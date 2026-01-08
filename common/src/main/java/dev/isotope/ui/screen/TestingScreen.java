package dev.isotope.ui.screen;

import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.testing.TestArenaManager;
import dev.isotope.testing.TestModeState;
import dev.isotope.testing.TestWorldManager;
import dev.isotope.testing.TestingTools;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game testing UI shown when in test mode.
 *
 * Simplified design with actual Button widgets for all interactions.
 */
@Environment(EnvType.CLIENT)
public class TestingScreen extends Screen {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 350;

    private final List<TableEntry> entries = new ArrayList<>();
    private int selectedArenaCount = 16;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Entry buttons (rebuilt on scroll)
    private final List<Button> entryButtons = new ArrayList<>();

    private record TableEntry(
        ResourceLocation tableId,
        Set<ResourceLocation> structures
    ) {}

    public TestingScreen() {
        super(Component.literal("ISOTOPE Test Mode"));
        loadEntries();
    }

    private void loadEntries() {
        entries.clear();
        Set<ResourceLocation> tested = TestModeState.getInstance().getTestedTables();
        StructureLootLinker linker = StructureLootLinker.getInstance();

        for (ResourceLocation tableId : tested) {
            // Use the proper method to get links for this loot table
            List<StructureLootLink> links = linker.getLinksForLootTable(tableId);
            Set<ResourceLocation> structures = new HashSet<>();
            for (var link : links) {
                structures.add(link.structureId());
            }
            entries.add(new TableEntry(tableId, structures));
        }

        // Calculate max scroll
        int contentHeight = entries.size() * 70;
        int viewHeight = PANEL_HEIGHT - 120; // header + footer
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Header buttons
        addRenderableWidget(Button.builder(
            Component.literal("Exit Test Mode"),
            b -> onExitTestMode()
        ).pos(panelX + PANEL_WIDTH - 115, panelY + 8).size(105, 20).build());

        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.TEST_MODE)
        ).pos(panelX + PANEL_WIDTH - 140, panelY + 8).size(20, 20).build());

        // Footer - Arena size buttons
        int footerY = panelY + PANEL_HEIGHT - 35;
        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 100;
        for (int preset : presets) {
            final int count = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> selectedArenaCount = count
            ).pos(presetX, footerY).size(40, 20).build());
            presetX += 45;
        }

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            b -> onClose()
        ).pos(panelX + PANEL_WIDTH - 70, footerY).size(60, 20).build());

        // Build entry buttons
        rebuildEntryButtons();
    }

    private void rebuildEntryButtons() {
        // Remove old entry buttons
        for (Button btn : entryButtons) {
            removeWidget(btn);
        }
        entryButtons.clear();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int contentY = panelY + 45;
        int contentHeight = PANEL_HEIGHT - 120;

        int entryY = contentY - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            TableEntry entry = entries.get(i);

            // Only create buttons for visible entries
            if (entryY + 60 > contentY && entryY < contentY + contentHeight) {
                final ResourceLocation structureId = entry.structures.isEmpty()
                    ? null
                    : entry.structures.iterator().next();

                // Teleport button
                if (structureId != null) {
                    Button teleportBtn = Button.builder(
                        Component.literal("Teleport"),
                        b -> onTeleport(structureId)
                    ).pos(panelX + 15, entryY + 35).size(80, 18).build();
                    entryButtons.add(addRenderableWidget(teleportBtn));

                    // Arena button
                    Button arenaBtn = Button.builder(
                        Component.literal("Spawn Arena"),
                        b -> onSpawnArena(structureId)
                    ).pos(panelX + 100, entryY + 35).size(90, 18).build();
                    entryButtons.add(addRenderableWidget(arenaBtn));
                }
            }

            entryY += 70;
        }
    }

    private void onExitTestMode() {
        if (minecraft == null) return;
        // skipCloseAfterConfirm=true because exitTestWorld() handles the screen transition
        minecraft.setScreen(new ConfirmDialog(
            this,
            "Exit Test Mode",
            "This will delete the test world\nand return to the main menu.",
            "Exit",
            () -> TestWorldManager.getInstance().exitTestWorld(),
            true,
            true  // Don't call onClose() after confirm - exitTestWorld handles screen
        ));
    }

    private void onTeleport(ResourceLocation structureId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose();
        minecraft.execute(() -> {
            var result = TestingTools.locateStructure(
                minecraft.getSingleplayerServer(),
                structureId,
                100
            );

            if (result.found()) {
                boolean success = TestingTools.teleportPlayer(
                    minecraft.getSingleplayerServer(),
                    result.position()
                );
                if (success) {
                    IsotopeToast.success("Teleported", "Found " + structureId.getPath());
                } else {
                    IsotopeToast.error("Failed", "Could not teleport");
                }
            } else {
                IsotopeToast.error("Not Found", "Structure not found within range");
            }
        });
    }

    private void onSpawnArena(ResourceLocation structureId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose();
        IsotopeToast.info("Creating Arena", "Spawning " + selectedArenaCount + " structures...");
        TestArenaManager.getInstance().createArena(structureId, msg -> {
            minecraft.execute(() -> IsotopeToast.info("Arena", msg));
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render widgets first (this calls renderBackground internally)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Now draw our panel content ON TOP of everything
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Header
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 40, IsotopeColors.BACKGROUND_SOLID);
        graphics.fill(panelX + 8, panelY + 10, panelX + 12, panelY + 30, 0xFF44aa44); // Green indicator
        graphics.drawString(font, "ISOTOPE TEST MODE", panelX + 18, panelY + 14, IsotopeColors.ACCENT_GOLD, false);

        // World type
        String worldType = TestModeState.getInstance().getWorldType().displayName;
        graphics.drawString(font, worldType, panelX + 150, panelY + 14, IsotopeColors.TEXT_MUTED, false);

        // Content area
        int contentY = panelY + 45;
        int contentHeight = PANEL_HEIGHT - 120;

        // Scissor for scrolling
        graphics.enableScissor(panelX, contentY, panelX + PANEL_WIDTH, contentY + contentHeight);

        // Render entries
        int entryY = contentY - scrollOffset;
        for (TableEntry entry : entries) {
            if (entryY + 65 > contentY && entryY < contentY + contentHeight) {
                renderEntry(graphics, panelX + 10, entryY, PANEL_WIDTH - 20, entry);
            }
            entryY += 70;
        }

        graphics.disableScissor();

        // Footer
        int footerY = panelY + PANEL_HEIGHT - 45;
        graphics.fill(panelX, footerY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_SOLID);
        graphics.drawString(font, "Arena size:", panelX + 15, footerY + 12, IsotopeColors.TEXT_PRIMARY, false);

        // Re-render buttons on top of panel
        for (var widget : this.children()) {
            if (widget instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        // Selected arena highlight
        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 100;
        int presetButtonY = panelY + PANEL_HEIGHT - 35;
        for (int preset : presets) {
            if (preset == selectedArenaCount) {
                graphics.renderOutline(presetX - 1, presetButtonY - 1, 42, 22, IsotopeColors.ACCENT_GOLD);
            }
            presetX += 45;
        }
    }

    private void renderEntry(GuiGraphics graphics, int x, int y, int entryWidth, TableEntry entry) {
        // Entry background
        graphics.fill(x, y, x + entryWidth, y + 60, IsotopeColors.BACKGROUND_DARK);
        graphics.renderOutline(x, y, entryWidth, 60, 0xFF383838);

        // Table name
        String name = entry.tableId.toString();
        if (name.length() > 50) {
            name = "..." + name.substring(name.length() - 47);
        }
        graphics.drawString(font, name, x + 8, y + 6, IsotopeColors.TEXT_PRIMARY, false);

        // Structure info
        if (entry.structures.isEmpty()) {
            graphics.drawString(font, "No linked structures", x + 8, y + 20, IsotopeColors.TEXT_MUTED, false);
        } else {
            String info = entry.structures.size() + " structure" + (entry.structures.size() != 1 ? "s" : "");
            graphics.drawString(font, info, x + 8, y + 20, 0xFF88aa88, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int oldOffset = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));

        if (oldOffset != scrollOffset) {
            rebuildEntryButtons();
        }

        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Don't call super - just fill with solid color to prevent blur
        graphics.fill(0, 0, this.width, this.height, 0xFF1a1a1a);
    }
}
