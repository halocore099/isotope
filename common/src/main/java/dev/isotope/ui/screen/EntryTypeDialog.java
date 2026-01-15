package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootEntry;
import dev.isotope.ui.IsotopeColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Dialog for selecting entry type (item, empty, loot_table, tag, etc.).
 */
public class EntryTypeDialog extends Screen {

    private static final int DIALOG_WIDTH = 220;
    private static final int DIALOG_HEIGHT = 200;

    @Nullable
    private final Screen parent;
    private final int poolIdx;
    private final int entryIdx;
    private final String currentType;
    private final BiConsumer<String, Optional<ResourceLocation>> onTypeSelected;

    // Type options
    private static final TypeOption[] TYPE_OPTIONS = {
        new TypeOption(LootEntry.TYPE_ITEM, "Item", "Specific item drop", true),
        new TypeOption(LootEntry.TYPE_EMPTY, "Empty", "No item (gap in drops)", false),
        new TypeOption(LootEntry.TYPE_LOOT_TABLE, "Loot Table", "Reference another table", true),
        new TypeOption(LootEntry.TYPE_TAG, "Item Tag", "All items from a tag", true),
        new TypeOption(LootEntry.TYPE_ALTERNATIVES, "Alternatives", "First matching child", false),
        new TypeOption(LootEntry.TYPE_GROUP, "Group", "All children", false),
        new TypeOption(LootEntry.TYPE_SEQUENCE, "Sequence", "Sequential children", false),
    };

    private record TypeOption(String type, String name, String description, boolean needsName) {}

    private int hoveredOption = -1;
    private int selectedOption = -1;
    private String nameInput = "";
    private boolean inputFocused = false;

    public EntryTypeDialog(@Nullable Screen parent, int poolIdx, int entryIdx, String currentType,
                           BiConsumer<String, Optional<ResourceLocation>> onTypeSelected) {
        super(Component.literal("Change Entry Type"));
        this.parent = parent;
        this.poolIdx = poolIdx;
        this.entryIdx = entryIdx;
        this.currentType = currentType;
        this.onTypeSelected = onTypeSelected;

        // Find current type index
        for (int i = 0; i < TYPE_OPTIONS.length; i++) {
            if (TYPE_OPTIONS[i].type.equals(currentType)) {
                selectedOption = i;
                break;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);
        graphics.renderOutline(dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF404040);

        // Title
        graphics.drawCenteredString(font, "Change Entry Type", dialogX + DIALOG_WIDTH / 2, dialogY + 8, IsotopeColors.ACCENT_GOLD);

        // Subtitle
        String subtitle = "Pool " + (poolIdx + 1) + ", Entry " + (entryIdx + 1);
        graphics.drawCenteredString(font, subtitle, dialogX + DIALOG_WIDTH / 2, dialogY + 22, IsotopeColors.TEXT_MUTED);

        // Type options
        int btnY = dialogY + 40;
        hoveredOption = -1;

        for (int i = 0; i < TYPE_OPTIONS.length; i++) {
            TypeOption opt = TYPE_OPTIONS[i];
            int btnX = dialogX + 10;
            int btnWidth = DIALOG_WIDTH - 20;
            int btnHeight = 18;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth &&
                mouseY >= btnY && mouseY < btnY + btnHeight;
            boolean isCurrent = opt.type.equals(currentType);
            boolean isSelected = i == selectedOption;

            if (hovered) {
                hoveredOption = i;
            }

            // Background
            int bgColor;
            if (isSelected) {
                bgColor = 0xFF3a5a3a;
            } else if (hovered) {
                bgColor = 0xFF3a4a3a;
            } else if (isCurrent) {
                bgColor = 0xFF2a3a4a;
            } else {
                bgColor = 0xFF2a2a2a;
            }
            graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, bgColor);

            // Text
            int textColor;
            if (isSelected) {
                textColor = 0xFF55FF55;
            } else if (hovered) {
                textColor = 0xFFAAFFAA;
            } else if (isCurrent) {
                textColor = 0xFF55AAFF;
            } else {
                textColor = IsotopeColors.TEXT_PRIMARY;
            }
            graphics.drawString(font, opt.name, btnX + 4, btnY + 5, textColor);

            // Description on the right
            int descWidth = font.width(opt.description);
            graphics.drawString(font, opt.description, btnX + btnWidth - descWidth - 4, btnY + 5, IsotopeColors.TEXT_MUTED);

            // Current indicator
            if (isCurrent) {
                graphics.drawString(font, "◄", btnX + btnWidth - descWidth - 14, btnY + 5, 0xFF55AAFF);
            }

            btnY += 20;
        }

        // Name input section (if needed)
        if (selectedOption >= 0 && TYPE_OPTIONS[selectedOption].needsName) {
            btnY += 5;
            graphics.drawString(font, "Name/ID:", dialogX + 10, btnY, IsotopeColors.TEXT_SECONDARY);
            btnY += 12;

            // Input box
            int inputX = dialogX + 10;
            int inputWidth = DIALOG_WIDTH - 20;
            int inputHeight = 16;
            int inputBgColor = inputFocused ? 0xFF3a3a3a : 0xFF2a2a2a;
            int inputBorderColor = inputFocused ? 0xFF55FF55 : 0xFF404040;
            graphics.fill(inputX, btnY, inputX + inputWidth, btnY + inputHeight, inputBgColor);
            graphics.renderOutline(inputX, btnY, inputWidth, inputHeight, inputBorderColor);

            // Input text with cursor
            String displayText = nameInput;
            if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                displayText += "_";
            }
            graphics.drawString(font, displayText, inputX + 3, btnY + 4, IsotopeColors.TEXT_PRIMARY);

            btnY += 20;

            // Hint
            String hint = switch (TYPE_OPTIONS[selectedOption].type) {
                case LootEntry.TYPE_ITEM -> "e.g., minecraft:diamond";
                case LootEntry.TYPE_LOOT_TABLE -> "e.g., minecraft:chests/simple_dungeon";
                case LootEntry.TYPE_TAG -> "e.g., minecraft:logs";
                default -> "";
            };
            graphics.drawString(font, hint, dialogX + 10, btnY, IsotopeColors.TEXT_MUTED);
        }

        // Buttons row
        int buttonY = dialogY + DIALOG_HEIGHT - 26;
        int buttonWidth = 60;
        int gap = 10;
        int totalWidth = buttonWidth * 2 + gap;
        int startX = dialogX + (DIALOG_WIDTH - totalWidth) / 2;

        // Apply button
        boolean canApply = selectedOption >= 0 && selectedOption != getTypeIndex(currentType);
        if (TYPE_OPTIONS[selectedOption].needsName && nameInput.isEmpty()) {
            canApply = false;
        }

        boolean applyHovered = mouseX >= startX && mouseX < startX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + 18;
        int applyBg = canApply ? (applyHovered ? 0xFF3a5a3a : 0xFF2a4a2a) : 0xFF2a2a2a;
        graphics.fill(startX, buttonY, startX + buttonWidth, buttonY + 18, applyBg);
        int applyColor = canApply ? (applyHovered ? 0xFF55FF55 : 0xFF88FF88) : IsotopeColors.TEXT_MUTED;
        graphics.drawCenteredString(font, "Apply", startX + buttonWidth / 2, buttonY + 5, applyColor);

        // Cancel button
        int cancelX = startX + buttonWidth + gap;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + 18;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 18, cancelHovered ? 0xFF4a3a3a : 0xFF2a2a2a);
        graphics.drawCenteredString(font, "Cancel", cancelX + buttonWidth / 2, buttonY + 5,
            cancelHovered ? 0xFFff6666 : IsotopeColors.TEXT_MUTED);
    }

    private int getTypeIndex(String type) {
        for (int i = 0; i < TYPE_OPTIONS.length; i++) {
            if (TYPE_OPTIONS[i].type.equals(type)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int dialogX = (width - DIALOG_WIDTH) / 2;
            int dialogY = (height - DIALOG_HEIGHT) / 2;

            // Check option clicks
            if (hoveredOption >= 0 && hoveredOption < TYPE_OPTIONS.length) {
                selectedOption = hoveredOption;
                // Clear name when changing type
                nameInput = "";
                inputFocused = TYPE_OPTIONS[selectedOption].needsName;
                return true;
            }

            // Check input box click
            if (selectedOption >= 0 && TYPE_OPTIONS[selectedOption].needsName) {
                int inputY = dialogY + 40 + TYPE_OPTIONS.length * 20 + 17;
                int inputX = dialogX + 10;
                int inputWidth = DIALOG_WIDTH - 20;
                if (mouseX >= inputX && mouseX < inputX + inputWidth &&
                    mouseY >= inputY && mouseY < inputY + 16) {
                    inputFocused = true;
                    return true;
                } else {
                    inputFocused = false;
                }
            }

            // Check Apply button
            int buttonY = dialogY + DIALOG_HEIGHT - 26;
            int buttonWidth = 60;
            int gap = 10;
            int totalWidth = buttonWidth * 2 + gap;
            int startX = dialogX + (DIALOG_WIDTH - totalWidth) / 2;

            boolean canApply = selectedOption >= 0 && selectedOption != getTypeIndex(currentType);
            if (selectedOption >= 0 && TYPE_OPTIONS[selectedOption].needsName && nameInput.isEmpty()) {
                canApply = false;
            }

            if (canApply && mouseX >= startX && mouseX < startX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + 18) {
                applySelection();
                return true;
            }

            // Check Cancel button
            int cancelX = startX + buttonWidth + gap;
            if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + 18) {
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            onClose();
            return true;
        }

        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
            if (selectedOption >= 0 && selectedOption != getTypeIndex(currentType)) {
                if (!TYPE_OPTIONS[selectedOption].needsName || !nameInput.isEmpty()) {
                    applySelection();
                    return true;
                }
            }
        }

        if (inputFocused) {
            if (keyCode == 259) { // Backspace
                if (!nameInput.isEmpty()) {
                    nameInput = nameInput.substring(0, nameInput.length() - 1);
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputFocused) {
            // Allow alphanumeric, underscore, colon, slash for ResourceLocation
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':' || chr == '/') {
                nameInput += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void applySelection() {
        if (selectedOption < 0) return;

        TypeOption opt = TYPE_OPTIONS[selectedOption];
        Optional<ResourceLocation> name = Optional.empty();

        if (opt.needsName && !nameInput.isEmpty()) {
            // Parse the name input
            String input = nameInput;
            if (!input.contains(":")) {
                input = "minecraft:" + input;
            }
            name = Optional.of(ResourceLocation.parse(input));
        }

        onTypeSelected.accept(opt.type, name);
        onClose();
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
