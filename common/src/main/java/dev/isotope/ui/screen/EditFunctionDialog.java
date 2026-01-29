package dev.isotope.ui.screen;

import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.data.loot.LootFunction;
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
 * Dialog for editing existing functions on loot entries.
 * Pre-populates with existing function data and allows modification.
 */
@Environment(EnvType.CLIENT)
public class EditFunctionDialog extends VersionedScreen {

    private static final int DIALOG_WIDTH = 320;
    private static final int DIALOG_HEIGHT = 340;

    @Nullable
    private final Screen parent;
    private final LootFunction existingFunction;
    private final Consumer<LootFunction> onFunctionSaved;

    // Function presets
    private final List<FunctionPreset> presets = new ArrayList<>();
    private int selectedPreset = -1;

    // Parameter input fields for selected preset
    private String param1 = "";
    private String param2 = "";
    private String param3 = "";

    // Track which parameter field is active for keyboard input
    private int activeParamField = 0; // 0 = none, 1 = param1, 2 = param2, 3 = param3

    private record FunctionPreset(
        String name,
        String description,
        String icon,
        int iconColor,
        String functionType,  // e.g., "minecraft:set_count"
        String param1Label,  // null if no param
        String param1Default,
        String param2Label,  // null if no param
        String param2Default,
        String param3Label,  // null if no param (for things like treasure boolean)
        String param3Default,
        FunctionBuilder builder
    ) {}

    @FunctionalInterface
    private interface FunctionBuilder {
        LootFunction build(String p1, String p2, String p3);
    }

    public EditFunctionDialog(@Nullable Screen parent, LootFunction existingFunction, Consumer<LootFunction> onFunctionSaved) {
        super(Component.literal("Edit Function"));
        this.parent = parent;
        this.existingFunction = existingFunction;
        this.onFunctionSaved = onFunctionSaved;
        buildPresets();
        populateFromExisting();
    }

    private void buildPresets() {
        presets.clear();

        // Enchant with levels (like skeleton bow, dungeon gear)
        presets.add(new FunctionPreset(
            "Enchant with Levels",
            "Enchant as if using enchanting table",
            "\u2726", IsotopeColors.ACCENT_GOLD,
            "minecraft:enchant_with_levels",
            "Min Level:", "5",
            "Max Level:", "15",
            "Include treasure:", "false",
            (p1, p2, p3) -> LootFunction.enchantWithLevels(
                ScreenUtils.parseIntSafe(p1, 5),
                ScreenUtils.parseIntSafe(p2, 15),
                Boolean.parseBoolean(p3)
            )
        ));

        // Enchant randomly
        presets.add(new FunctionPreset(
            "Enchant Randomly",
            "Apply a random enchantment",
            "\u2726", IsotopeColors.ACCENT_GOLD,
            "minecraft:enchant_randomly",
            null, null,
            null, null,
            null, null,
            (p1, p2, p3) -> LootFunction.enchantRandomly()
        ));

        // Set count
        presets.add(new FunctionPreset(
            "Set Count",
            "Set item stack size",
            "\u00D7", IsotopeColors.ACCENT_AQUA,
            "minecraft:set_count",
            "Min:", "1",
            "Max:", "3",
            null, null,
            (p1, p2, p3) -> {
                int min = ScreenUtils.parseIntSafe(p1, 1);
                int max = ScreenUtils.parseIntSafe(p2, 1);
                return min == max ? LootFunction.setCount(min) : LootFunction.setCount(min, max);
            }
        ));

        // Set damage (durability)
        presets.add(new FunctionPreset(
            "Set Damage",
            "Set tool/armor durability (0-1)",
            "\u2692", IsotopeColors.TEXT_SECONDARY,
            "minecraft:set_damage",
            "Min %:", "10",
            "Max %:", "50",
            null, null,
            (p1, p2, p3) -> LootFunction.setDamage(
                ScreenUtils.parseIntSafe(p1, 10) / 100f,
                ScreenUtils.parseIntSafe(p2, 50) / 100f
            )
        ));

        // Looting enchant bonus
        presets.add(new FunctionPreset(
            "Looting Enchant",
            "Add bonus drops per Looting level",
            "\u2697", IsotopeColors.ACCENT_AQUA,
            "minecraft:looting_enchant",
            "Min/level:", "0",
            "Max/level:", "1",
            null, null,
            (p1, p2, p3) -> createLootingEnchant(ScreenUtils.parseIntSafe(p1, 0), ScreenUtils.parseIntSafe(p2, 1))
        ));

        // Furnace smelt
        presets.add(new FunctionPreset(
            "Furnace Smelt",
            "Auto-smelt drops (like Fire Aspect)",
            "\uD83D\uDD25", IsotopeColors.SOURCE_FEATURE,
            "minecraft:furnace_smelt",
            null, null,
            null, null,
            null, null,
            (p1, p2, p3) -> createSimpleFunction("minecraft:furnace_smelt")
        ));

        // Exploration map
        presets.add(new FunctionPreset(
            "Exploration Map",
            "Create map to structure",
            "\uD83D\uDDFA", IsotopeColors.ACCENT_GOLD,
            "minecraft:exploration_map",
            "Destination:", "buried_treasure",
            null, null,
            null, null,
            (p1, p2, p3) -> createExplorationMap(p1)
        ));

        // Set potion
        presets.add(new FunctionPreset(
            "Set Potion",
            "Set potion effect type",
            "\u2697", IsotopeColors.SOURCE_FEATURE,
            "minecraft:set_potion",
            "Potion ID:", "healing",
            null, null,
            null, null,
            (p1, p2, p3) -> createSetPotion(p1)
        ));
    }

    /**
     * Find and select the preset matching the existing function, and populate parameters.
     */
    private void populateFromExisting() {
        String funcType = existingFunction.function();

        // Find matching preset
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).functionType.equals(funcType)) {
                selectedPreset = i;
                extractParameters(existingFunction, presets.get(i));
                activeParamField = presets.get(i).param1Label != null ? 1 : 0;
                return;
            }
        }

        // No matching preset found - leave unselected
        selectedPreset = -1;
    }

    /**
     * Extract parameters from existing function into param fields.
     */
    private void extractParameters(LootFunction func, FunctionPreset preset) {
        var params = func.parameters();

        switch (func.function()) {
            case "minecraft:set_count" -> {
                if (params.has("count")) {
                    var count = params.get("count");
                    if (count.isJsonPrimitive()) {
                        param1 = String.valueOf(count.getAsInt());
                        param2 = param1;
                    } else if (count.isJsonObject()) {
                        var countObj = count.getAsJsonObject();
                        param1 = countObj.has("min") ? String.valueOf(countObj.get("min").getAsInt()) : "1";
                        param2 = countObj.has("max") ? String.valueOf(countObj.get("max").getAsInt()) : param1;
                    }
                } else {
                    param1 = preset.param1Default != null ? preset.param1Default : "";
                    param2 = preset.param2Default != null ? preset.param2Default : "";
                }
            }
            case "minecraft:enchant_with_levels" -> {
                if (params.has("levels")) {
                    var levels = params.get("levels");
                    if (levels.isJsonPrimitive()) {
                        param1 = String.valueOf(levels.getAsInt());
                        param2 = param1;
                    } else if (levels.isJsonObject()) {
                        var levelsObj = levels.getAsJsonObject();
                        param1 = levelsObj.has("min") ? String.valueOf(levelsObj.get("min").getAsInt()) : "5";
                        param2 = levelsObj.has("max") ? String.valueOf(levelsObj.get("max").getAsInt()) : "15";
                    }
                } else {
                    param1 = "5";
                    param2 = "15";
                }
                param3 = params.has("treasure") ? String.valueOf(params.get("treasure").getAsBoolean()) : "false";
            }
            case "minecraft:set_damage" -> {
                if (params.has("damage")) {
                    var damage = params.get("damage");
                    if (damage.isJsonPrimitive()) {
                        int pct = (int) (damage.getAsFloat() * 100);
                        param1 = String.valueOf(pct);
                        param2 = param1;
                    } else if (damage.isJsonObject()) {
                        var damageObj = damage.getAsJsonObject();
                        param1 = damageObj.has("min") ? String.valueOf((int)(damageObj.get("min").getAsFloat() * 100)) : "10";
                        param2 = damageObj.has("max") ? String.valueOf((int)(damageObj.get("max").getAsFloat() * 100)) : "50";
                    }
                } else {
                    param1 = preset.param1Default != null ? preset.param1Default : "";
                    param2 = preset.param2Default != null ? preset.param2Default : "";
                }
            }
            case "minecraft:looting_enchant" -> {
                if (params.has("count")) {
                    var count = params.get("count");
                    if (count.isJsonPrimitive()) {
                        param1 = String.valueOf(count.getAsInt());
                        param2 = param1;
                    } else if (count.isJsonObject()) {
                        var countObj = count.getAsJsonObject();
                        param1 = countObj.has("min") ? String.valueOf(countObj.get("min").getAsInt()) : "0";
                        param2 = countObj.has("max") ? String.valueOf(countObj.get("max").getAsInt()) : "1";
                    }
                } else {
                    param1 = "0";
                    param2 = "1";
                }
            }
            case "minecraft:exploration_map" -> {
                param1 = params.has("destination") ? params.get("destination").getAsString().replace("minecraft:", "") : "buried_treasure";
            }
            case "minecraft:set_potion" -> {
                param1 = params.has("id") ? params.get("id").getAsString().replace("minecraft:", "") : "healing";
            }
            default -> {
                // Use defaults for unknown functions
                param1 = preset.param1Default != null ? preset.param1Default : "";
                param2 = preset.param2Default != null ? preset.param2Default : "";
                param3 = preset.param3Default != null ? preset.param3Default : "";
            }
        }
    }

    private static LootFunction createLootingEnchant(int min, int max) {
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        com.google.gson.JsonObject count = new com.google.gson.JsonObject();
        count.addProperty("type", "minecraft:uniform");
        count.addProperty("min", min);
        count.addProperty("max", max);
        params.add("count", count);
        return new LootFunction("minecraft:looting_enchant", params, List.of());
    }

    private static LootFunction createSimpleFunction(String functionId) {
        return new LootFunction(functionId, new com.google.gson.JsonObject(), List.of());
    }

    private static LootFunction createExplorationMap(String destination) {
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        params.addProperty("destination", ScreenUtils.ensureNamespace(destination.trim()));
        params.addProperty("decoration", "target_x");
        params.addProperty("zoom", 2);
        params.addProperty("skip_existing_chunks", true);
        return new LootFunction("minecraft:exploration_map", params, List.of());
    }

    private static LootFunction createSetPotion(String potionId) {
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        params.addProperty("id", ScreenUtils.ensureNamespace(potionId.trim()));
        return new LootFunction("minecraft:set_potion", params, List.of());
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
            b -> saveFunction()
        ).pos(dialogX + DIALOG_WIDTH - 110, dialogY + DIALOG_HEIGHT - 30).size(100, 20).build());
    }

    private void saveFunction() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            // Build new function with edited parameters, preserving existing conditions
            LootFunction baseFunction = preset.builder.build(param1, param2, param3);
            // Preserve conditions from existing function
            LootFunction newFunction = new LootFunction(
                baseFunction.function(),
                baseFunction.parameters(),
                existingFunction.conditions()
            );
            onFunctionSaved.accept(newFunction);
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
        graphics.drawString(font, "Edit Function", dialogX + 12, dialogY + 10, IsotopeColors.ACCENT_GOLD, false);

        // Current function info
        String currentType = existingFunction.getDisplayName();
        graphics.drawString(font, "Current: " + currentType, dialogX + 150, dialogY + 10, IsotopeColors.TEXT_MUTED, false);

        // Preset list
        int listY = dialogY + 40;
        int itemHeight = 32;

        for (int i = 0; i < presets.size(); i++) {
            FunctionPreset preset = presets.get(i);
            int itemY = listY + i * itemHeight;

            // Check if visible
            if (itemY + itemHeight < dialogY + 35 || itemY > dialogY + DIALOG_HEIGHT - 80) continue;

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
            graphics.drawString(font, preset.icon, dialogX + 18, itemY + 8, preset.iconColor, false);

            // Name
            graphics.drawString(font, preset.name, dialogX + 35, itemY + 6, IsotopeColors.TEXT_PRIMARY, false);

            // Description
            graphics.drawString(font, preset.description, dialogX + 35, itemY + 18, IsotopeColors.TEXT_MUTED, false);
        }

        // Parameter inputs for selected preset
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            int paramY = dialogY + DIALOG_HEIGHT - 80;

            graphics.fill(dialogX, paramY, dialogX + DIALOG_WIDTH, paramY + 45, IsotopeColors.POOL_HEADER_BACKGROUND);

            int paramX = dialogX + 15;

            if (preset.param1Label != null) {
                graphics.drawString(font, preset.param1Label, paramX, paramY + 8, IsotopeColors.TEXT_MUTED, false);
                // Input box
                int inputX = paramX + font.width(preset.param1Label) + 5;
                boolean field1Selected = activeParamField == 1;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, field1Selected,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.FUNC_COND_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.ACCENT_GOLD);
                graphics.drawString(font, param1.isEmpty() ? preset.param1Default : param1, inputX + 3, paramY + 8,
                    param1.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
                if (field1Selected) {
                    int cursorX = inputX + 3 + font.width(param1);
                    ScreenUtils.renderCursor(graphics, cursorX, paramY + 6, 12);
                }
                paramX = inputX + 50;
            }

            if (preset.param2Label != null) {
                graphics.drawString(font, preset.param2Label, paramX, paramY + 8, IsotopeColors.TEXT_MUTED, false);
                int inputX = paramX + font.width(preset.param2Label) + 5;
                boolean field2Selected = activeParamField == 2;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, field2Selected,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.FUNC_COND_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.ACCENT_GOLD);
                graphics.drawString(font, param2.isEmpty() ? preset.param2Default : param2, inputX + 3, paramY + 8,
                    param2.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
                if (field2Selected) {
                    int cursorX = inputX + 3 + font.width(param2);
                    ScreenUtils.renderCursor(graphics, cursorX, paramY + 6, 12);
                }
                paramX = inputX + 50;
            }

            if (preset.param3Label != null) {
                graphics.drawString(font, preset.param3Label, paramX, paramY + 8, IsotopeColors.TEXT_MUTED, false);
                int inputX = paramX + font.width(preset.param3Label) + 5;
                boolean field3Selected = activeParamField == 3;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, field3Selected,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.FUNC_COND_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.ACCENT_GOLD);
                graphics.drawString(font, param3.isEmpty() ? preset.param3Default : param3, inputX + 3, paramY + 8,
                    param3.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
                if (field3Selected) {
                    int cursorX = inputX + 3 + font.width(param3);
                    ScreenUtils.renderCursor(graphics, cursorX, paramY + 6, 12);
                }
            }

            // Live preview
            try {
                LootFunction previewFunc = preset.builder.build(
                    param1.isEmpty() ? preset.param1Default : param1,
                    param2.isEmpty() ? preset.param2Default : param2,
                    param3.isEmpty() ? preset.param3Default : param3
                );
                String preview = "Preview: " + previewFunc.getDisplayName();
                String paramSummary = previewFunc.getParameterSummary();
                if (!paramSummary.isEmpty()) {
                    preview += " (" + paramSummary + ")";
                }
                graphics.drawString(font, preview, dialogX + 15, paramY + 28, IsotopeColors.ACCENT_GREEN, false);
            } catch (Exception e) {
                graphics.drawString(font, "Preview: Invalid parameters", dialogX + 15, paramY + 28, IsotopeColors.DESTRUCTIVE_TEXT, false);
            }
        } else {
            int hintY = dialogY + DIALOG_HEIGHT - 70;
            graphics.drawString(font, "Select a function type above", dialogX + 15, hintY, IsotopeColors.TEXT_MUTED, false);
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
        int itemHeight = 32;

        for (int i = 0; i < presets.size(); i++) {
            int itemY = listY + i * itemHeight;

            if (mouseX >= dialogX + 10 && mouseX < dialogX + DIALOG_WIDTH - 10 &&
                mouseY >= itemY && mouseY < itemY + itemHeight) {

                if (selectedPreset != i) {
                    selectedPreset = i;
                    FunctionPreset preset = presets.get(i);
                    // Reset params to defaults when changing type
                    param1 = preset.param1Default != null ? preset.param1Default : "";
                    param2 = preset.param2Default != null ? preset.param2Default : "";
                    param3 = preset.param3Default != null ? preset.param3Default : "";
                    activeParamField = preset.param1Label != null ? 1 : 0;
                }
                return true;
            }
        }

        // Check parameter field clicks
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            int paramY = dialogY + DIALOG_HEIGHT - 80;
            int paramX = dialogX + 15;

            if (preset.param1Label != null) {
                int inputX = paramX + font.width(preset.param1Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    activeParamField = 1;
                    return true;
                }
                paramX = inputX + 50;
            }

            if (preset.param2Label != null) {
                int inputX = paramX + font.width(preset.param2Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    activeParamField = 2;
                    return true;
                }
                paramX = inputX + 50;
            }

            if (preset.param3Label != null) {
                int inputX = paramX + font.width(preset.param3Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    activeParamField = 3;
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
            FunctionPreset preset = presets.get(selectedPreset);
            int maxField = preset.param3Label != null ? 3 : (preset.param2Label != null ? 2 : (preset.param1Label != null ? 1 : 0));
            if (maxField > 0) {
                activeParamField = activeParamField >= maxField ? 1 : activeParamField + 1;
                return true;
            }
        }

        // Enter to save
        if (keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) {
            if (selectedPreset >= 0) {
                saveFunction();
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
            } else if (activeParamField == 3 && !param3.isEmpty()) {
                param3 = param3.substring(0, param3.length() - 1);
                return true;
            }
        }

        // Arrow keys to navigate presets
        if (keyCode == UIConstants.KEY_DOWN && selectedPreset < presets.size() - 1) {
            selectedPreset++;
            resetParamsToDefaults();
            return true;
        }
        if (keyCode == UIConstants.KEY_UP && selectedPreset > 0) {
            selectedPreset--;
            resetParamsToDefaults();
            return true;
        }

        return super.keyPressed(event);
    }

    private void resetParamsToDefaults() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            param1 = preset.param1Default != null ? preset.param1Default : "";
            param2 = preset.param2Default != null ? preset.param2Default : "";
            param3 = preset.param3Default != null ? preset.param3Default : "";
            activeParamField = preset.param1Label != null ? 1 : 0;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char chr = (char) event.codepoint(); int modifiers = event.modifiers();
        if (activeParamField > 0 && selectedPreset >= 0) {
            FunctionPreset preset = presets.get(selectedPreset);

            // For boolean fields (param3 with "true"/"false"), toggle on any key
            if (activeParamField == 3 && preset.param3Label != null &&
                (param3.equals("true") || param3.equals("false"))) {
                param3 = param3.equals("true") ? "false" : "true";
                return true;
            }

            // Allow digits, decimal point, minus, and letters for text fields
            if (Character.isDigit(chr) || chr == '.' || chr == '-' ||
                Character.isLetter(chr) || chr == '_') {
                if (activeParamField == 1 && param1.length() < 20) {
                    param1 = param1 + chr;
                    return true;
                } else if (activeParamField == 2 && param2.length() < 20) {
                    param2 = param2 + chr;
                    return true;
                } else if (activeParamField == 3 && param3.length() < 20) {
                    param3 = param3 + chr;
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
