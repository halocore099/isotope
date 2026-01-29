package dev.isotope.ui.screen;

import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.data.loot.LootCondition;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for editing existing conditions on loot entries.
 * Pre-populates with existing condition data and allows modification.
 */
@Environment(EnvType.CLIENT)
public class EditConditionDialog extends VersionedScreen {

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 380;

    @Nullable
    private final Screen parent;
    private final LootCondition existingCondition;
    private final Consumer<LootCondition> onConditionSaved;

    // Condition presets
    private final List<ConditionPreset> presets = new ArrayList<>();
    private int selectedPreset = -1;

    // Parameter input values for selected preset
    private String param1 = "";
    private String param2 = "";

    // Track which parameter field is selected for keyboard input
    private int activeParamField = 0; // 0 = none, 1 = param1, 2 = param2

    private record ConditionPreset(
        String name,
        String description,
        String icon,
        int iconColor,
        String conditionType,  // e.g., "minecraft:random_chance"
        String param1Label,  // null if no param
        String param1Default,
        String param2Label,  // null if no param
        String param2Default,
        ConditionBuilder builder
    ) {}

    @FunctionalInterface
    private interface ConditionBuilder {
        LootCondition build(String p1, String p2);
    }

    public EditConditionDialog(@Nullable Screen parent, LootCondition existingCondition, Consumer<LootCondition> onConditionSaved) {
        super(Component.literal("Edit Condition"));
        this.parent = parent;
        this.existingCondition = existingCondition;
        this.onConditionSaved = onConditionSaved;
        buildPresets();
        populateFromExisting();
    }

    private void buildPresets() {
        presets.clear();

        // Random Chance - configurable percentage
        presets.add(new ConditionPreset(
            "Random Chance",
            "Probability-based drop (0-100%)",
            "\u2680", IsotopeColors.ACCENT_AQUA,
            "minecraft:random_chance",
            "Chance %:", "25",
            null, null,
            (p1, p2) -> LootCondition.randomChance(ScreenUtils.parsePercentSafe(p1, 25) / 100f)
        ));

        // Random Chance with Looting
        presets.add(new ConditionPreset(
            "Random Chance + Looting",
            "Base chance increased by looting level",
            "\u2697", IsotopeColors.ACCENT_AQUA,
            "minecraft:random_chance_with_looting",
            "Base %:", "10",
            "Per Level %:", "2",
            (p1, p2) -> LootCondition.randomChanceWithLooting(
                ScreenUtils.parsePercentSafe(p1, 10) / 100f,
                ScreenUtils.parsePercentSafe(p2, 2) / 100f
            )
        ));

        // Killed by Player
        presets.add(new ConditionPreset(
            "Killed by Player",
            "Only drops when killed by a player",
            "\u2694", IsotopeColors.SOURCE_MOB,
            "minecraft:killed_by_player",
            null, null,
            null, null,
            (p1, p2) -> LootCondition.killedByPlayer()
        ));

        // Survives Explosion
        presets.add(new ConditionPreset(
            "Survives Explosion",
            "Drop survives explosion damage",
            "\uD83D\uDCA5", IsotopeColors.SOURCE_FEATURE,
            "minecraft:survives_explosion",
            null, null,
            null, null,
            (p1, p2) -> LootCondition.survivesExplosion()
        ));

        // Weather Check - Raining
        presets.add(new ConditionPreset(
            "Weather: Raining",
            "Only when it's raining",
            "\uD83C\uDF27", IsotopeColors.ACCENT_AQUA,
            "minecraft:weather_check",
            null, null,
            null, null,
            (p1, p2) -> LootCondition.weatherCheck(true, null)
        ));

        // Weather Check - Thundering
        presets.add(new ConditionPreset(
            "Weather: Thunderstorm",
            "Only during thunderstorms",
            "\u26A1", IsotopeColors.ACCENT_GOLD,
            "minecraft:weather_check",
            null, null,
            null, null,
            (p1, p2) -> LootCondition.weatherCheck(null, true)
        ));

        // Time Check
        presets.add(new ConditionPreset(
            "Time Check",
            "Only during specific time of day",
            "\uD83D\uDD50", IsotopeColors.TEXT_SECONDARY,
            "minecraft:time_check",
            "Min (0-24000):", "13000",
            "Max (0-24000):", "23000",
            (p1, p2) -> LootCondition.timeCheck(
                ScreenUtils.parseIntSafe(p1, 13000),
                ScreenUtils.parseIntSafe(p2, 23000)
            )
        ));

        // Inverted (wraps another condition)
        presets.add(new ConditionPreset(
            "Inverted: Not Player Kill",
            "Drops when NOT killed by player",
            "\u2297", IsotopeColors.STATUS_WARNING,
            "minecraft:inverted",
            null, null,
            null, null,
            (p1, p2) -> LootCondition.inverted(LootCondition.killedByPlayer())
        ));
    }

    /**
     * Find and select the preset matching the existing condition, and populate parameters.
     */
    private void populateFromExisting() {
        String condType = existingCondition.condition();

        // Find matching preset
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).conditionType.equals(condType)) {
                // For weather_check, need to distinguish raining vs thundering
                if (condType.equals("minecraft:weather_check")) {
                    var params = existingCondition.parameters();
                    boolean isThundering = params.has("thundering") && params.get("thundering").getAsBoolean();
                    boolean isRaining = params.has("raining") && params.get("raining").getAsBoolean();

                    // Find the right weather preset
                    if (isThundering && presets.get(i).name.contains("Thunderstorm")) {
                        selectedPreset = i;
                        extractParameters(existingCondition, presets.get(i));
                        activeParamField = presets.get(i).param1Label != null ? 1 : 0;
                        return;
                    } else if (isRaining && !isThundering && presets.get(i).name.contains("Raining")) {
                        selectedPreset = i;
                        extractParameters(existingCondition, presets.get(i));
                        activeParamField = presets.get(i).param1Label != null ? 1 : 0;
                        return;
                    }
                    continue;
                }

                selectedPreset = i;
                extractParameters(existingCondition, presets.get(i));
                activeParamField = presets.get(i).param1Label != null ? 1 : 0;
                return;
            }
        }

        // No matching preset found - leave unselected
        selectedPreset = -1;
    }

    /**
     * Extract parameters from existing condition into param fields.
     */
    private void extractParameters(LootCondition cond, ConditionPreset preset) {
        var params = cond.parameters();

        switch (cond.condition()) {
            case "minecraft:random_chance" -> {
                if (params.has("chance")) {
                    float chance = params.get("chance").getAsFloat();
                    param1 = String.valueOf((int)(chance * 100));
                } else {
                    param1 = preset.param1Default != null ? preset.param1Default : "25";
                }
            }
            case "minecraft:random_chance_with_looting" -> {
                if (params.has("chance")) {
                    float chance = params.get("chance").getAsFloat();
                    param1 = String.valueOf((int)(chance * 100));
                } else {
                    param1 = "10";
                }
                if (params.has("looting_multiplier")) {
                    float mult = params.get("looting_multiplier").getAsFloat();
                    param2 = String.valueOf((int)(mult * 100));
                } else {
                    param2 = "2";
                }
            }
            case "minecraft:time_check" -> {
                if (params.has("value")) {
                    var value = params.getAsJsonObject("value");
                    param1 = value.has("min") ? String.valueOf(value.get("min").getAsInt()) : "13000";
                    param2 = value.has("max") ? String.valueOf(value.get("max").getAsInt()) : "23000";
                } else {
                    param1 = "13000";
                    param2 = "23000";
                }
            }
            default -> {
                // Use defaults for conditions without parameters
                param1 = preset.param1Default != null ? preset.param1Default : "";
                param2 = preset.param2Default != null ? preset.param2Default : "";
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Cancel button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CANCEL),
            b -> onClose()
        ).pos(dialogX + 10, dialogY + DIALOG_HEIGHT - 30).size(70, 20).build());

        // Save button
        addRenderableWidget(Button.builder(
            Component.literal("Save Changes"),
            b -> saveCondition()
        ).pos(dialogX + DIALOG_WIDTH - 110, dialogY + DIALOG_HEIGHT - 30).size(100, 20).build());
    }

    private void saveCondition() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            LootCondition newCondition = preset.builder.build(param1, param2);
            onConditionSaved.accept(newCondition);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DARK);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        ScreenUtils.drawDialogBackground(graphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT);

        // Header
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 30, IsotopeColors.POOL_HEADER_BACKGROUND);
        graphics.drawString(font, "Edit Condition", dialogX + 12, dialogY + 10, IsotopeColors.ACCENT_GOLD, false);

        // Current condition info
        String currentType = existingCondition.getDisplayName();
        graphics.drawString(font, "Current: " + currentType, dialogX + 150, dialogY + 10, IsotopeColors.TEXT_MUTED, false);

        // Preset list
        int listY = dialogY + 40;
        int itemHeight = 36;

        for (int i = 0; i < presets.size(); i++) {
            ConditionPreset preset = presets.get(i);
            int itemY = listY + i * itemHeight;

            // Check if visible
            if (itemY + itemHeight < dialogY + 35 || itemY > dialogY + DIALOG_HEIGHT - 100) continue;

            // Selection/hover highlight
            boolean selected = i == selectedPreset;
            boolean hovered = mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
                mouseY >= itemY && mouseY < itemY + itemHeight;

            if (selected) {
                graphics.fill(dialogX + 10, itemY, dialogX + DIALOG_WIDTH - 10, itemY + itemHeight, IsotopeColors.SINGLE_SELECT_BACKGROUND);
                ScreenUtils.renderOutline(graphics, dialogX + 10, itemY, DIALOG_WIDTH - 20, itemHeight, IsotopeColors.ACCENT_GOLD);
            } else if (hovered) {
                graphics.fill(dialogX + 10, itemY, dialogX + DIALOG_WIDTH - 10, itemY + itemHeight, IsotopeColors.INPUT_BACKGROUND);
            }

            // Icon
            graphics.drawString(font, preset.icon, dialogX + 18, itemY + 10, preset.iconColor, false);

            // Name
            graphics.drawString(font, preset.name, dialogX + 35, itemY + 6, IsotopeColors.TEXT_PRIMARY, false);

            // Description
            graphics.drawString(font, preset.description, dialogX + 35, itemY + 20, IsotopeColors.TEXT_MUTED, false);
        }

        // Parameter inputs for selected preset
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            int paramY = dialogY + DIALOG_HEIGHT - 95;

            graphics.fill(dialogX, paramY, dialogX + DIALOG_WIDTH, paramY + 60, IsotopeColors.POOL_HEADER_BACKGROUND);

            int paramX = dialogX + 15;

            if (preset.param1Label != null) {
                graphics.drawString(font, preset.param1Label, paramX, paramY + 10, IsotopeColors.TEXT_MUTED, false);
                // Input box
                int inputX = paramX + font.width(preset.param1Label) + 5;
                int inputWidth = 60;
                boolean field1Selected = activeParamField == 1;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 7, inputWidth, 16, field1Selected,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.FUNC_COND_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.ACCENT_GOLD);
                String displayText = param1.isEmpty() ? preset.param1Default : param1;
                int textColor = param1.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY;
                graphics.drawString(font, displayText, inputX + 4, paramY + 11, textColor, false);

                // Cursor for active field
                if (field1Selected) {
                    int cursorX = inputX + 4 + font.width(param1);
                    ScreenUtils.renderCursor(graphics, cursorX, paramY + 9, 12);
                }

                paramX = inputX + inputWidth + 20;
            }

            if (preset.param2Label != null) {
                graphics.drawString(font, preset.param2Label, paramX, paramY + 10, IsotopeColors.TEXT_MUTED, false);
                int inputX = paramX + font.width(preset.param2Label) + 5;
                int inputWidth = 60;
                boolean field2Selected = activeParamField == 2;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 7, inputWidth, 16, field2Selected,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.FUNC_COND_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.ACCENT_GOLD);
                String displayText = param2.isEmpty() ? preset.param2Default : param2;
                int textColor = param2.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY;
                graphics.drawString(font, displayText, inputX + 4, paramY + 11, textColor, false);

                // Cursor for active field
                if (field2Selected) {
                    int cursorX = inputX + 4 + font.width(param2);
                    ScreenUtils.renderCursor(graphics, cursorX, paramY + 9, 12);
                }
            }

            // Live preview
            try {
                LootCondition previewCond = preset.builder.build(
                    param1.isEmpty() ? preset.param1Default : param1,
                    param2.isEmpty() ? preset.param2Default : param2
                );
                String preview = "Preview: " + previewCond.getDisplayName();
                String paramSummary = previewCond.getParameterSummary();
                if (!paramSummary.isEmpty()) {
                    preview += " " + paramSummary;
                }
                graphics.drawString(font, preview, dialogX + 15, paramY + 40, IsotopeColors.ACCENT_GREEN, false);
            } catch (Exception e) {
                graphics.drawString(font, "Preview: Invalid parameters", dialogX + 15, paramY + 40, IsotopeColors.DESTRUCTIVE_TEXT, false);
            }
        } else {
            int hintY = dialogY + DIALOG_HEIGHT - 85;
            graphics.drawString(font, "Select a condition type above", dialogX + 15, hintY, IsotopeColors.TEXT_MUTED, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button != 0) return super.mouseClicked(event, focused);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Check preset list clicks
        int listY = dialogY + 40;
        int itemHeight = 36;

        for (int i = 0; i < presets.size(); i++) {
            int itemY = listY + i * itemHeight;

            if (mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
                mouseY >= itemY && mouseY < itemY + itemHeight) {

                if (selectedPreset != i) {
                    selectedPreset = i;
                    ConditionPreset preset = presets.get(i);
                    // Reset params to defaults
                    param1 = preset.param1Default != null ? preset.param1Default : "";
                    param2 = preset.param2Default != null ? preset.param2Default : "";
                    activeParamField = preset.param1Label != null ? 1 : 0;
                }
                return true;
            }
        }

        // Check parameter field clicks
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            int paramY = dialogY + DIALOG_HEIGHT - 95;
            int paramX = dialogX + 15;

            if (preset.param1Label != null) {
                int inputX = paramX + font.width(preset.param1Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 7, 60, 16)) {
                    activeParamField = 1;
                    return true;
                }
                paramX = inputX + 60 + 20;
            }

            if (preset.param2Label != null) {
                int inputX = paramX + font.width(preset.param2Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 7, 60, 16)) {
                    activeParamField = 2;
                    return true;
                }
            }

            // Click elsewhere deselects fields
            activeParamField = 0;
        }

        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();
        // Tab to switch between fields
        if (keyCode == UIConstants.KEY_TAB && selectedPreset >= 0) {
            ConditionPreset preset = presets.get(selectedPreset);
            if (preset.param1Label != null && preset.param2Label != null) {
                activeParamField = activeParamField == 1 ? 2 : 1;
                return true;
            }
        }

        // Enter to save condition
        if (keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) {
            if (selectedPreset >= 0) {
                saveCondition();
                return true;
            }
        }

        // Escape to close
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }

        // Backspace to delete character
        if (keyCode == UIConstants.KEY_BACKSPACE) {
            if (activeParamField == 1 && !param1.isEmpty()) {
                param1 = param1.substring(0, param1.length() - 1);
                return true;
            } else if (activeParamField == 2 && !param2.isEmpty()) {
                param2 = param2.substring(0, param2.length() - 1);
                return true;
            }
        }

        // Arrow keys to navigate presets
        if (keyCode == UIConstants.KEY_DOWN && selectedPreset < presets.size() - 1) {
            selectedPreset++;
            resetParams();
            return true;
        }
        if (keyCode == UIConstants.KEY_UP && selectedPreset > 0) {
            selectedPreset--;
            resetParams();
            return true;
        }

        return super.keyPressed(event);
    }

    private void resetParams() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            param1 = preset.param1Default != null ? preset.param1Default : "";
            param2 = preset.param2Default != null ? preset.param2Default : "";
            activeParamField = preset.param1Label != null ? 1 : 0;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char chr = (char) event.codepoint(); int modifiers = event.modifiers();
        if (activeParamField > 0 && selectedPreset >= 0) {
            // Allow digits, decimal point, and minus for numeric input
            if (Character.isDigit(chr) || chr == '.' || chr == '-') {
                if (activeParamField == 1 && param1.length() < 10) {
                    param1 = param1 + chr;
                    return true;
                } else if (activeParamField == 2 && param2.length() < 10) {
                    param2 = param2 + chr;
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
