package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootFunction;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for adding functions to loot entries.
 * Shows common function presets with easy configuration.
 */
@Environment(EnvType.CLIENT)
public class AddFunctionDialog extends Screen {

    private static final int DIALOG_WIDTH = 320;
    private static final int DIALOG_HEIGHT = 340;

    @Nullable
    private final Screen parent;
    private final Consumer<LootFunction> onFunctionAdded;

    // Function presets
    private final List<FunctionPreset> presets = new ArrayList<>();
    private int selectedPreset = -1;

    // Parameter input fields for selected preset
    private String param1 = "";
    private String param2 = "";
    private String param3 = "";

    private record FunctionPreset(
        String name,
        String description,
        String icon,
        int iconColor,
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

    public AddFunctionDialog(@Nullable Screen parent, Consumer<LootFunction> onFunctionAdded) {
        super(Component.literal("Add Function"));
        this.parent = parent;
        this.onFunctionAdded = onFunctionAdded;
        buildPresets();
    }

    private void buildPresets() {
        presets.clear();

        // Enchant with levels (like skeleton bow, dungeon gear)
        presets.add(new FunctionPreset(
            "Enchant with Levels",
            "Enchant as if using enchanting table",
            "✦", IsotopeColors.ACCENT_GOLD,
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
            "✦", IsotopeColors.ACCENT_GOLD,
            null, null,
            null, null,
            null, null,
            (p1, p2, p3) -> LootFunction.enchantRandomly()
        ));

        // Set count
        presets.add(new FunctionPreset(
            "Set Count",
            "Set item stack size",
            "×", IsotopeColors.ACCENT_AQUA,
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
            "⚒", IsotopeColors.TEXT_SECONDARY,
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
            "⚗", IsotopeColors.ACCENT_AQUA,
            "Min/level:", "0",
            "Max/level:", "1",
            null, null,
            (p1, p2, p3) -> createLootingEnchant(ScreenUtils.parseIntSafe(p1, 0), ScreenUtils.parseIntSafe(p2, 1))
        ));

        // Furnace smelt
        presets.add(new FunctionPreset(
            "Furnace Smelt",
            "Auto-smelt drops (like Fire Aspect)",
            "🔥", IsotopeColors.SOURCE_FEATURE,
            null, null,
            null, null,
            null, null,
            (p1, p2, p3) -> createSimpleFunction("minecraft:furnace_smelt")
        ));

        // Exploration map
        presets.add(new FunctionPreset(
            "Exploration Map",
            "Create map to structure",
            "🗺", IsotopeColors.ACCENT_GOLD,
            "Destination:", "buried_treasure",
            null, null,
            null, null,
            (p1, p2, p3) -> createExplorationMap(p1)
        ));

        // Set potion
        presets.add(new FunctionPreset(
            "Set Potion",
            "Set potion effect type",
            "⚗", IsotopeColors.SOURCE_FEATURE,
            "Potion ID:", "healing",
            null, null,
            null, null,
            (p1, p2, p3) -> createSetPotion(p1)
        ));
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

        // Add button
        addRenderableWidget(Button.builder(
            Component.literal("Add Function"),
            b -> addSelectedFunction()
        ).pos(dialogX + DIALOG_WIDTH - 100, dialogY + DIALOG_HEIGHT - 30).size(90, 20).build());
    }

    private void addSelectedFunction() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            LootFunction function = preset.builder.build(param1, param2, param3);
            onFunctionAdded.accept(function);
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
        graphics.drawString(font, "Add Function", dialogX + 12, dialogY + 10, IsotopeColors.ACCENT_GOLD, false);

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
                graphics.renderOutline(dialogX + 10, itemY, DIALOG_WIDTH - 20, itemHeight, IsotopeColors.ACCENT_GOLD);
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
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, false,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.INPUT_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.INPUT_BORDER);
                graphics.drawString(font, param1.isEmpty() ? preset.param1Default : param1, inputX + 3, paramY + 8,
                    param1.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
                paramX = inputX + 50;
            }

            if (preset.param2Label != null) {
                graphics.drawString(font, preset.param2Label, paramX, paramY + 8, IsotopeColors.TEXT_MUTED, false);
                int inputX = paramX + font.width(preset.param2Label) + 5;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, false,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.INPUT_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.INPUT_BORDER);
                graphics.drawString(font, param2.isEmpty() ? preset.param2Default : param2, inputX + 3, paramY + 8,
                    param2.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
                paramX = inputX + 50;
            }

            if (preset.param3Label != null) {
                graphics.drawString(font, preset.param3Label, paramX, paramY + 8, IsotopeColors.TEXT_MUTED, false);
                int inputX = paramX + font.width(preset.param3Label) + 5;
                ScreenUtils.renderInputBox(graphics, inputX, paramY + 5, 40, 14, false,
                    IsotopeColors.INPUT_BACKGROUND, IsotopeColors.INPUT_BACKGROUND,
                    IsotopeColors.INPUT_BORDER, IsotopeColors.INPUT_BORDER);
                graphics.drawString(font, param3.isEmpty() ? preset.param3Default : param3, inputX + 3, paramY + 8,
                    param3.isEmpty() ? IsotopeColors.TEXT_MUTED : IsotopeColors.TEXT_PRIMARY, false);
            }

            // Instruction
            graphics.drawString(font, "Click values to edit, then click Add Function",
                dialogX + 15, paramY + 28, IsotopeColors.TEXT_MUTED, false);
        } else {
            int hintY = dialogY + DIALOG_HEIGHT - 70;
            graphics.drawString(font, "Select a function type above",
                dialogX + 15, hintY, IsotopeColors.TEXT_MUTED, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

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
                    // Reset params to defaults
                    param1 = preset.param1Default != null ? preset.param1Default : "";
                    param2 = preset.param2Default != null ? preset.param2Default : "";
                    param3 = preset.param3Default != null ? preset.param3Default : "";
                }
                return true;
            }
        }

        // Check parameter input clicks (for now, simple click to edit)
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            FunctionPreset preset = presets.get(selectedPreset);
            int paramY = dialogY + DIALOG_HEIGHT - 80;
            int paramX = dialogX + 15;

            if (preset.param1Label != null) {
                int inputX = paramX + font.width(preset.param1Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    // Edit param1 - for simplicity, cycle through common values
                    param1 = cycleNumber(param1, preset.param1Default);
                    return true;
                }
                paramX = inputX + 50;
            }

            if (preset.param2Label != null) {
                int inputX = paramX + font.width(preset.param2Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    param2 = cycleNumber(param2, preset.param2Default);
                    return true;
                }
                paramX = inputX + 50;
            }

            if (preset.param3Label != null) {
                int inputX = paramX + font.width(preset.param3Label) + 5;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, inputX, paramY + 5, 40, 14)) {
                    // Toggle boolean or cycle text
                    if (param3.equals("true") || param3.equals("false")) {
                        param3 = param3.equals("true") ? "false" : "true";
                    } else {
                        param3 = cycleNumber(param3, preset.param3Default);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private String cycleNumber(String current, String defaultVal) {
        int val = ScreenUtils.parseIntSafe(current.isEmpty() ? defaultVal : current, 1);
        // Cycle: +1, or +5 if shift, or +10 if ctrl
        if (hasShiftDown()) {
            val += 5;
        } else if (hasControlDown()) {
            val += 10;
        } else {
            val += 1;
        }
        if (val > 100) val = 1;
        return String.valueOf(val);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Number keys to directly set values when a preset is selected
        if (selectedPreset >= 0 && keyCode >= 48 && keyCode <= 57) {
            // 0-9 keys
            char digit = (char) keyCode;
            if (hasShiftDown()) {
                param2 = param2 + digit;
                if (param2.length() > 4) param2 = String.valueOf(digit);
            } else {
                param1 = param1 + digit;
                if (param1.length() > 4) param1 = String.valueOf(digit);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
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
