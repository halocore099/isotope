package dev.isotope.ui.screen;

import dev.isotope.Isotope;
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
 * Displays edited loot tables with testing actions.
 */
@Environment(EnvType.CLIENT)
public class TestingScreen extends Screen {

    private static final int PANEL_WIDTH = 380;
    private static final int HEADER_HEIGHT = 50;
    private static final int ENTRY_HEIGHT = 80;
    private static final int FOOTER_HEIGHT = 50;

    private final List<TestedTableEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedArenaCount = 16;

    // Arena count presets
    private static final int[] ARENA_PRESETS = {4, 9, 16, 25, 36};

    private record TestedTableEntry(
        ResourceLocation tableId,
        Set<ResourceLocation> structures,
        boolean expanded
    ) {
        public TestedTableEntry withExpanded(boolean expanded) {
            return new TestedTableEntry(tableId, structures, expanded);
        }
    }

    public TestingScreen() {
        super(Component.literal("ISOTOPE Test Mode"));
        loadTestedTables();
    }

    private void loadTestedTables() {
        entries.clear();

        Set<ResourceLocation> tested = TestModeState.getInstance().getTestedTables();
        StructureLootLinker linker = StructureLootLinker.getInstance();

        for (ResourceLocation tableId : tested) {
            Set<ResourceLocation> structures = new HashSet<>();
            for (var link : linker.getAllLinks()) {
                if (link.lootTableId().equals(tableId)) {
                    structures.add(link.structureId());
                }
            }
            entries.add(new TestedTableEntry(tableId, structures, false));
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = 20;

        // Exit Test Mode button (top right of panel)
        addRenderableWidget(Button.builder(
            Component.literal("Exit Test Mode"),
            b -> onExitTestMode()
        ).pos(panelX + PANEL_WIDTH - 110, panelY + 8).size(100, 18).build());

        // Help button
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.TEST_MODE)
        ).pos(panelX + PANEL_WIDTH - 135, panelY + 8).size(20, 18).build());

        // Arena count preset buttons at bottom
        int presetY = height - 35;
        int presetStartX = panelX + 80;
        for (int i = 0; i < ARENA_PRESETS.length; i++) {
            final int count = ARENA_PRESETS[i];
            Button btn = addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> {
                    selectedArenaCount = count;
                    TestArenaManager.getInstance().setStructureCount(count);
                }
            ).pos(presetStartX + i * 50, presetY).size(45, 20).build());
        }

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            b -> onClose()
        ).pos(panelX + PANEL_WIDTH - 70, height - 35).size(60, 20).build());
    }

    private void onExitTestMode() {
        if (minecraft == null) return;

        minecraft.setScreen(new ConfirmDialog(
            this,
            "Exit Test Mode",
            "This will delete the test world\nand return to the main menu.",
            "Exit",
            () -> TestWorldManager.getInstance().exitTestWorld(),
            true
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent background
        graphics.fill(0, 0, width, height, 0xA0000000);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = 20;
        int panelHeight = height - 60;

        // Main panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, 0xFF000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xFF1a1a1a);

        // Header
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, 0xFF252525);

        // Test mode indicator
        graphics.fill(panelX + 8, panelY + 8, panelX + 12, panelY + 28, 0xFF44aa44); // Green bar
        graphics.drawString(font, "ISOTOPE TEST MODE", panelX + 18, panelY + 12, IsotopeColors.ACCENT_GOLD, false);

        // World info
        String worldType = TestModeState.getInstance().getWorldType().displayName;
        graphics.drawString(font, "World: " + worldType, panelX + 18, panelY + 28, IsotopeColors.TEXT_SECONDARY, false);

        // Table count
        String countText = entries.size() + " edited table" + (entries.size() != 1 ? "s" : "");
        int countWidth = font.width(countText);
        graphics.drawString(font, countText, panelX + PANEL_WIDTH - countWidth - 120, panelY + 28, IsotopeColors.TEXT_MUTED, false);

        // Content area
        int contentY = panelY + HEADER_HEIGHT;
        int contentHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Scissor for scrolling
        graphics.enableScissor(panelX, contentY, panelX + PANEL_WIDTH, contentY + contentHeight);

        int entryY = contentY + 8 - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            TestedTableEntry entry = entries.get(i);

            if (entryY > contentY - ENTRY_HEIGHT && entryY < contentY + contentHeight) {
                renderEntry(graphics, panelX + 8, entryY, PANEL_WIDTH - 16, entry, mouseX, mouseY, i);
            }

            entryY += ENTRY_HEIGHT + 4;
        }

        graphics.disableScissor();

        // Footer
        int footerY = panelY + panelHeight - FOOTER_HEIGHT;
        graphics.fill(panelX, footerY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xFF202020);

        // Arena count label
        graphics.drawString(font, "Arena size:", panelX + 10, footerY + 13, IsotopeColors.TEXT_PRIMARY, false);

        // Render buttons
        super.render(graphics, mouseX, mouseY, partialTick);

        // Highlight selected arena count
        int presetStartX = panelX + 80;
        int presetY = height - 35;
        for (int i = 0; i < ARENA_PRESETS.length; i++) {
            if (ARENA_PRESETS[i] == selectedArenaCount) {
                graphics.renderOutline(presetStartX + i * 50 - 1, presetY - 1, 47, 22, IsotopeColors.ACCENT_GOLD);
            }
        }
    }

    private void renderEntry(GuiGraphics graphics, int x, int y, int width, TestedTableEntry entry,
                             int mouseX, int mouseY, int index) {
        // Entry background
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT, 0xFF282828);
        graphics.renderOutline(x, y, width, ENTRY_HEIGHT, 0xFF383838);

        // Table name
        String tableName = entry.tableId.toString();
        if (tableName.length() > 45) {
            tableName = "..." + tableName.substring(tableName.length() - 42);
        }
        graphics.drawString(font, tableName, x + 8, y + 6, IsotopeColors.TEXT_PRIMARY, false);

        // Structures info
        if (entry.structures.isEmpty()) {
            graphics.drawString(font, "No linked structures", x + 8, y + 20, IsotopeColors.TEXT_MUTED, false);
        } else {
            String structInfo = entry.structures.size() + " structure" + (entry.structures.size() != 1 ? "s" : "");
            graphics.drawString(font, structInfo, x + 8, y + 20, 0xFF88aa88, false);

            // Show first few structure names
            int structY = y + 32;
            int count = 0;
            for (ResourceLocation structId : entry.structures) {
                if (count >= 2) {
                    graphics.drawString(font, "  +" + (entry.structures.size() - 2) + " more...",
                        x + 8, structY, IsotopeColors.TEXT_MUTED, false);
                    break;
                }
                graphics.drawString(font, "  " + structId.getPath(), x + 8, structY, IsotopeColors.TEXT_SECONDARY, false);
                structY += 10;
                count++;
            }
        }

        // Action buttons
        int buttonY = y + ENTRY_HEIGHT - 24;

        // Teleport button
        if (!entry.structures.isEmpty()) {
            int teleportX = x + 8;
            boolean teleportHovered = mouseX >= teleportX && mouseX < teleportX + 100 &&
                                      mouseY >= buttonY && mouseY < buttonY + 18;
            graphics.fill(teleportX, buttonY, teleportX + 100, buttonY + 18,
                teleportHovered ? 0xFF4a6a4a : 0xFF3a5a3a);
            graphics.drawCenteredString(font, "Teleport", teleportX + 50, buttonY + 5, 0xFFaaccaa);

            // Spawn arena button
            int arenaX = x + 115;
            boolean arenaHovered = mouseX >= arenaX && mouseX < arenaX + 120 &&
                                   mouseY >= buttonY && mouseY < buttonY + 18;
            graphics.fill(arenaX, buttonY, arenaX + 120, buttonY + 18,
                arenaHovered ? 0xFF5a5a6a : 0xFF4a4a5a);
            graphics.drawCenteredString(font, "Spawn " + selectedArenaCount + "x Arena",
                arenaX + 60, buttonY + 5, 0xFFaaaacc);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelX = (width - PANEL_WIDTH) / 2;
            int panelY = 20;
            int contentY = panelY + HEADER_HEIGHT;
            int contentHeight = height - 60 - HEADER_HEIGHT - FOOTER_HEIGHT;

            // Check entry clicks
            int entryY = contentY + 8 - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                TestedTableEntry entry = entries.get(i);
                int x = panelX + 8;
                int width = PANEL_WIDTH - 16;

                if (mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT &&
                    mouseX >= x && mouseX < x + width) {

                    int buttonY = entryY + ENTRY_HEIGHT - 24;

                    // Teleport button
                    if (!entry.structures.isEmpty() &&
                        mouseX >= x + 8 && mouseX < x + 108 &&
                        mouseY >= buttonY && mouseY < buttonY + 18) {
                        onTeleportToStructure(entry.structures.iterator().next());
                        return true;
                    }

                    // Arena button
                    if (!entry.structures.isEmpty() &&
                        mouseX >= x + 115 && mouseX < x + 235 &&
                        mouseY >= buttonY && mouseY < buttonY + 18) {
                        onSpawnArena(entry.structures.iterator().next());
                        return true;
                    }
                }

                entryY += ENTRY_HEIGHT + 4;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onTeleportToStructure(ResourceLocation structureId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose(); // Close screen first

        minecraft.execute(() -> {
            var result = TestingTools.locateStructure(
                minecraft.getSingleplayerServer(),
                structureId,
                100 // Search radius in chunks
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose(); // Close screen first

        IsotopeToast.info("Creating Arena", "Spawning " + selectedArenaCount + " structures...");

        TestArenaManager.getInstance().createArena(structureId, msg -> {
            minecraft.execute(() -> IsotopeToast.info("Arena", msg));
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = 20;
        int contentY = panelY + HEADER_HEIGHT;
        int contentHeight = height - 60 - HEADER_HEIGHT - FOOTER_HEIGHT;

        if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH &&
            mouseY >= contentY && mouseY < contentY + contentHeight) {

            int totalHeight = entries.size() * (ENTRY_HEIGHT + 4);
            int maxScroll = Math.max(0, totalHeight - contentHeight + 16);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 30)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
}
