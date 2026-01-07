package dev.isotope.ui.screen;

import dev.isotope.testing.TestArenaManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Screen for configuring and launching a test arena.
 */
@Environment(EnvType.CLIENT)
public class TestArenaScreen extends Screen {

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 180;

    private final Screen parent;
    private final ResourceLocation structureId;

    private EditBox countInput;
    private Button createButton;

    public TestArenaScreen(Screen parent, ResourceLocation structureId) {
        super(Component.literal("Test Arena"));
        this.parent = parent;
        this.structureId = structureId;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Count input
        countInput = new EditBox(font, dialogX + 120, dialogY + 70, 60, 18, Component.literal("Count"));
        countInput.setValue(String.valueOf(TestArenaManager.getInstance().getStructureCount()));
        countInput.setFilter(s -> s.isEmpty() || s.matches("\\d{0,2}"));
        addRenderableWidget(countInput);

        // Preset buttons
        int presetY = dialogY + 95;
        int presetX = dialogX + 20;
        for (int count : new int[]{4, 9, 16, 25, 36}) {
            final int c = count;
            Button presetBtn = addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> {
                    countInput.setValue(String.valueOf(c));
                    TestArenaManager.getInstance().setStructureCount(c);
                }
            ).pos(presetX, presetY).size(45, 18).build());
            presetX += 50;
        }

        // Create button
        createButton = addRenderableWidget(Button.builder(
            Component.literal("Create Arena"),
            b -> onCreateArena()
        ).pos(dialogX + 20, dialogY + DIALOG_HEIGHT - 35).size(120, 20).build());

        // Cancel button
        addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            b -> onClose()
        ).pos(dialogX + DIALOG_WIDTH - 90, dialogY + DIALOG_HEIGHT - 35).size(70, 20).build());
    }

    private void onCreateArena() {
        // Parse count
        try {
            int count = Integer.parseInt(countInput.getValue().trim());
            if (count < 1 || count > 64) {
                IsotopeToast.error("Invalid Count", "Enter a number between 1 and 64");
                return;
            }
            TestArenaManager.getInstance().setStructureCount(count);
        } catch (NumberFormatException e) {
            IsotopeToast.error("Invalid Count", "Enter a valid number");
            return;
        }

        // Close screens and create arena
        if (minecraft != null) {
            minecraft.setScreen(null);
        }

        IsotopeToast.info("Creating Arena", "Spawning " + TestArenaManager.getInstance().getStructureCount() + " structures...");

        TestArenaManager.getInstance().createArena(structureId, msg -> {
            if (minecraft != null) {
                minecraft.execute(() -> IsotopeToast.info("Arena", msg));
            }
        });
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
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 24, 0xFF252525);
        graphics.drawString(font, "Test Arena Setup", dialogX + 10, dialogY + 8, IsotopeColors.ACCENT_GOLD, false);

        // Structure name
        graphics.drawString(font, "Structure:", dialogX + 20, dialogY + 35, IsotopeColors.TEXT_MUTED, false);
        String structureName = structureId.getPath();
        if (font.width(structureName) > 200) {
            structureName = "..." + structureName.substring(structureName.length() - 25);
        }
        graphics.drawString(font, structureName, dialogX + 80, dialogY + 35, IsotopeColors.TEXT_PRIMARY, false);

        // Description
        graphics.drawString(font, "This will spawn multiple copies of the", dialogX + 20, dialogY + 52, IsotopeColors.TEXT_SECONDARY, false);
        graphics.drawString(font, "structure in your current world.", dialogX + 20, dialogY + 62, IsotopeColors.TEXT_SECONDARY, false);

        // Count label
        graphics.drawString(font, "Count:", dialogX + 20, dialogY + 73, IsotopeColors.TEXT_MUTED, false);

        // Preset label
        graphics.drawString(font, "Presets:", dialogX + 20, dialogY + 115, IsotopeColors.TEXT_MUTED, false);

        // Grid preview
        int count = 16;
        try {
            count = Integer.parseInt(countInput.getValue().trim());
        } catch (NumberFormatException ignored) {}
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        String gridInfo = gridSize + "x" + gridSize + " grid";
        graphics.drawString(font, gridInfo, dialogX + 190, dialogY + 73, IsotopeColors.TEXT_SECONDARY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
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
