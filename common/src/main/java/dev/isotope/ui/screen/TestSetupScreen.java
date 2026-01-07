package dev.isotope.ui.screen;

import dev.isotope.editing.LootEditManager;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.testing.TestModeState;
import dev.isotope.testing.TestWorldManager;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen for configuring test world settings before creation.
 *
 * Shows summary of edited loot tables and lets user choose world type.
 */
@Environment(EnvType.CLIENT)
public class TestSetupScreen extends Screen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 320;

    @Nullable
    private final Screen parent;

    private TestModeState.WorldType selectedWorldType = TestModeState.WorldType.VOID;
    private Button voidButton;
    private Button normalButton;
    private Button createButton;

    private final List<EditedTableInfo> editedTables = new ArrayList<>();
    private int scrollOffset = 0;

    private record EditedTableInfo(ResourceLocation tableId, Set<ResourceLocation> structures, int changeCount) {}

    public TestSetupScreen(@Nullable Screen parent) {
        super(Component.literal("Test Setup"));
        this.parent = parent;
        loadEditedTables();
    }

    private void loadEditedTables() {
        editedTables.clear();

        Set<ResourceLocation> edited = LootEditManager.getInstance().getEditedTables();
        StructureLootLinker linker = StructureLootLinker.getInstance();

        for (ResourceLocation tableId : edited) {
            // Find structures that use this loot table
            Set<ResourceLocation> structures = new HashSet<>();
            for (var link : linker.getAllLinks()) {
                if (link.lootTableId().equals(tableId)) {
                    structures.add(link.structureId());
                }
            }

            // Count changes
            var edit = LootEditManager.getInstance().getEdit(tableId);
            int changeCount = edit != null ? edit.getOperationCount() : 0;

            editedTables.add(new EditedTableInfo(tableId, structures, changeCount));
        }
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // World type buttons
        int buttonY = dialogY + 180;

        voidButton = addRenderableWidget(Button.builder(
            Component.literal("Superflat Void"),
            b -> selectWorldType(TestModeState.WorldType.VOID)
        ).pos(dialogX + 30, buttonY).size(160, 20).build());

        normalButton = addRenderableWidget(Button.builder(
            Component.literal("Normal Generation"),
            b -> selectWorldType(TestModeState.WorldType.NORMAL)
        ).pos(dialogX + 210, buttonY).size(160, 20).build());

        updateWorldTypeButtons();

        // Create button
        createButton = addRenderableWidget(Button.builder(
            Component.literal("Create Test World"),
            b -> onCreateWorld()
        ).pos(dialogX + 30, dialogY + DIALOG_HEIGHT - 40).size(160, 24).build());

        // Update create button state
        createButton.active = !editedTables.isEmpty();

        // Cancel button
        addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 100, dialogY + DIALOG_HEIGHT - 40).size(70, 24).build());

        // Help button
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.TEST_MODE)
        ).pos(dialogX + DIALOG_WIDTH - 25, dialogY + 5).size(20, 20).build());
    }

    private void selectWorldType(TestModeState.WorldType type) {
        selectedWorldType = type;
        updateWorldTypeButtons();
    }

    private void updateWorldTypeButtons() {
        // Visual indication of selected type
        if (voidButton != null && normalButton != null) {
            voidButton.active = selectedWorldType != TestModeState.WorldType.VOID;
            normalButton.active = selectedWorldType != TestModeState.WorldType.NORMAL;
        }
    }

    private void onCreateWorld() {
        if (editedTables.isEmpty()) {
            IsotopeToast.error("No Changes", "Edit some loot tables first");
            return;
        }

        // Enter test mode
        TestWorldManager.getInstance().enterTestMode(selectedWorldType);

        // Close this screen and go to title screen
        // User will create the world manually
        if (minecraft != null) {
            String worldName = TestModeState.getInstance().getTestWorldName();
            IsotopeToast.info("Test Mode Active",
                "Create a world named:\n" + worldName);

            // Go to title screen so user can create world
            minecraft.disconnect();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF000000);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title bar
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 28, 0xFF252525);
        graphics.drawString(font, "Test Your Changes", dialogX + 12, dialogY + 10, IsotopeColors.ACCENT_GOLD, false);

        // Edited tables section
        int listY = dialogY + 35;
        graphics.drawString(font, "Edited Loot Tables:", dialogX + 12, listY, IsotopeColors.TEXT_PRIMARY, false);

        if (editedTables.isEmpty()) {
            graphics.drawString(font, "No loot tables have been edited.",
                dialogX + 20, listY + 18, IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, "Make some changes first, then come back here.",
                dialogX + 20, listY + 30, IsotopeColors.TEXT_SECONDARY, false);
        } else {
            // List area
            int listHeight = 120;
            graphics.fill(dialogX + 10, listY + 14, dialogX + DIALOG_WIDTH - 10, listY + 14 + listHeight, 0xFF252525);

            // Scissor for scrolling
            graphics.enableScissor(dialogX + 10, listY + 14, dialogX + DIALOG_WIDTH - 10, listY + 14 + listHeight);

            int entryY = listY + 18 - scrollOffset;
            for (EditedTableInfo info : editedTables) {
                if (entryY > listY + 14 - 20 && entryY < listY + 14 + listHeight) {
                    // Table name
                    String tableName = info.tableId.getPath();
                    if (tableName.length() > 40) {
                        tableName = "..." + tableName.substring(tableName.length() - 37);
                    }
                    graphics.drawString(font, tableName, dialogX + 16, entryY, IsotopeColors.TEXT_PRIMARY, false);

                    // Structure count badge
                    if (!info.structures.isEmpty()) {
                        String structText = info.structures.size() + " structure" + (info.structures.size() != 1 ? "s" : "");
                        int badgeX = dialogX + DIALOG_WIDTH - 100;
                        graphics.fill(badgeX, entryY - 1, badgeX + 80, entryY + 10, 0xFF3a5a3a);
                        graphics.drawString(font, structText, badgeX + 4, entryY, 0xFF88cc88, false);
                    }
                }
                entryY += 16;
            }

            graphics.disableScissor();

            // Summary
            graphics.drawString(font, editedTables.size() + " table(s) will be tested",
                dialogX + 12, listY + 14 + listHeight + 8, IsotopeColors.TEXT_SECONDARY, false);
        }

        // World type section
        int typeY = dialogY + 160;
        graphics.drawString(font, "World Type:", dialogX + 12, typeY, IsotopeColors.TEXT_PRIMARY, false);

        // Description of selected type
        String typeDesc = selectedWorldType == TestModeState.WorldType.VOID
            ? "Flat void world - structures spawn on glass platforms"
            : "Normal terrain generation - find structures naturally";
        graphics.drawString(font, typeDesc, dialogX + 30, dialogY + 210, IsotopeColors.TEXT_SECONDARY, false);

        // Info text
        graphics.drawString(font, "A temporary creative world will be created.",
            dialogX + 12, dialogY + DIALOG_HEIGHT - 70, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, "It will be deleted when you exit test mode.",
            dialogX + 12, dialogY + DIALOG_HEIGHT - 58, IsotopeColors.TEXT_MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;
        int listY = dialogY + 35;
        int listHeight = 120;

        if (mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
            mouseY >= listY + 14 && mouseY < listY + 14 + listHeight) {

            int maxScroll = Math.max(0, editedTables.size() * 16 - listHeight + 10);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 16)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
