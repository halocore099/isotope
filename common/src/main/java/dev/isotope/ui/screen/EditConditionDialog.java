package dev.isotope.ui.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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

    // Raw JSON editing mode for unknown conditions
    private boolean rawJsonMode = false;
    private List<String> rawJsonLines = new ArrayList<>();
    private int rawJsonCursorLine = 0;
    private int rawJsonCursorCol = 0;
    private int rawJsonScrollOffset = 0;
    private String rawJsonError = null;
    private static final int MAX_JSON_LINES_VISIBLE = 8;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

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

        // No matching preset found - switch to raw JSON mode
        selectedPreset = -1;
        switchToRawJsonMode();
    }

    /**
     * Switch to raw JSON editing mode with the current condition.
     */
    private void switchToRawJsonMode() {
        rawJsonMode = true;
        rawJsonError = null;

        // Build the full JSON representation
        JsonObject fullJson = new JsonObject();
        fullJson.addProperty("condition", existingCondition.condition());
        for (var entry : existingCondition.parameters().entrySet()) {
            fullJson.add(entry.getKey(), entry.getValue());
        }

        // Convert to pretty-printed lines
        String prettyJson = PRETTY_GSON.toJson(fullJson);
        rawJsonLines.clear();
        for (String line : prettyJson.split("\n")) {
            rawJsonLines.add(line);
        }
        if (rawJsonLines.isEmpty()) {
            rawJsonLines.add("{}");
        }
        rawJsonCursorLine = 0;
        rawJsonCursorCol = 0;
        rawJsonScrollOffset = 0;
    }

    /**
     * Switch back to preset mode.
     */
    private void switchToPresetMode() {
        rawJsonMode = false;
        rawJsonError = null;
        if (selectedPreset < 0 && !presets.isEmpty()) {
            selectedPreset = 0;
            resetParamsToDefaults();
        }
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

        // Toggle Raw JSON button
        addRenderableWidget(Button.builder(
            Component.literal(rawJsonMode ? "Use Presets" : "Edit JSON"),
            b -> {
                if (rawJsonMode) {
                    switchToPresetMode();
                } else {
                    switchToRawJsonMode();
                }
                rebuildButtons();
            }
        ).pos(dialogX + 90, dialogY + DIALOG_HEIGHT - 30).size(80, 20).build());

        // Save button
        addRenderableWidget(Button.builder(
            Component.literal("Save Changes"),
            b -> saveCondition()
        ).pos(dialogX + DIALOG_WIDTH - 110, dialogY + DIALOG_HEIGHT - 30).size(100, 20).build());
    }

    private void rebuildButtons() {
        clearWidgets();
        init();
    }

    private void saveCondition() {
        if (rawJsonMode) {
            // Parse and save raw JSON
            try {
                String jsonStr = String.join("\n", rawJsonLines);
                JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

                // Extract condition type
                String condType = json.has("condition") ? json.get("condition").getAsString() : existingCondition.condition();

                // Extract parameters (everything except "condition")
                JsonObject params = new JsonObject();
                for (var entry : json.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("condition")) {
                        params.add(key, entry.getValue());
                    }
                }

                LootCondition newCondition = new LootCondition(condType, params);
                onConditionSaved.accept(newCondition);
                onClose();
            } catch (JsonSyntaxException e) {
                rawJsonError = "Invalid JSON: " + e.getMessage();
            } catch (Exception e) {
                rawJsonError = "Error: " + e.getMessage();
            }
        } else if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            LootCondition newCondition = preset.builder.build(param1, param2);
            onConditionSaved.accept(newCondition);
            onClose();
        }
    }

    private void resetParamsToDefaults() {
        if (selectedPreset >= 0 && selectedPreset < presets.size()) {
            ConditionPreset preset = presets.get(selectedPreset);
            param1 = preset.param1Default != null ? preset.param1Default : "";
            param2 = preset.param2Default != null ? preset.param2Default : "";
            activeParamField = preset.param1Label != null ? 1 : 0;
        }
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

        if (rawJsonMode) {
            renderRawJsonEditor(graphics, dialogX, dialogY, mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

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

    /**
     * Render the raw JSON editor interface.
     */
    private void renderRawJsonEditor(GuiGraphics graphics, int dialogX, int dialogY, int mouseX, int mouseY) {
        int editorX = dialogX + 10;
        int editorY = dialogY + 40;
        int editorWidth = DIALOG_WIDTH - 20;
        int editorHeight = DIALOG_HEIGHT - 120;
        int lineHeight = font.lineHeight + 2;

        // Editor background
        graphics.fill(editorX, editorY, editorX + editorWidth, editorY + editorHeight, IsotopeColors.INPUT_BACKGROUND);
        ScreenUtils.renderOutline(graphics, editorX, editorY, editorWidth, editorHeight, IsotopeColors.INPUT_BORDER);

        // Enable scissor for clipping
        graphics.enableScissor(editorX + 2, editorY + 2, editorX + editorWidth - 2, editorY + editorHeight - 2);

        // Render visible lines
        int visibleLines = editorHeight / lineHeight;
        for (int i = 0; i < visibleLines && i + rawJsonScrollOffset < rawJsonLines.size(); i++) {
            int lineIdx = i + rawJsonScrollOffset;
            String line = rawJsonLines.get(lineIdx);

            int lineY = editorY + 4 + i * lineHeight;

            // Line number
            String lineNum = String.format("%2d", lineIdx + 1);
            graphics.drawString(font, lineNum, editorX + 4, lineY, IsotopeColors.TEXT_MUTED, false);

            // Line content with syntax highlighting
            int textX = editorX + 25;
            renderJsonLine(graphics, line, textX, lineY);

            // Cursor on current line
            if (lineIdx == rawJsonCursorLine) {
                int cursorX = textX + font.width(line.substring(0, Math.min(rawJsonCursorCol, line.length())));
                if (System.currentTimeMillis() % 1000 < 500) {  // Blink cursor
                    graphics.fill(cursorX, lineY - 1, cursorX + 1, lineY + font.lineHeight, IsotopeColors.TEXT_PRIMARY);
                }
            }
        }

        graphics.disableScissor();

        // Scrollbar if needed
        if (rawJsonLines.size() > visibleLines) {
            int scrollbarX = editorX + editorWidth - 6;
            int scrollbarHeight = editorHeight - 4;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleLines / rawJsonLines.size());
            int thumbY = editorY + 2 + (scrollbarHeight - thumbHeight) * rawJsonScrollOffset / Math.max(1, rawJsonLines.size() - visibleLines);

            graphics.fill(scrollbarX, editorY + 2, scrollbarX + 4, editorY + editorHeight - 2, IsotopeColors.SCROLLBAR_TRACK);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, IsotopeColors.SCROLLBAR_THUMB);
        }

        // Error message
        if (rawJsonError != null) {
            int errorY = dialogY + DIALOG_HEIGHT - 75;
            String errorText = rawJsonError.length() > 45 ? rawJsonError.substring(0, 45) + "..." : rawJsonError;
            graphics.drawString(font, errorText, dialogX + 15, errorY, IsotopeColors.DESTRUCTIVE_TEXT, false);
        }

        // Help text
        int helpY = dialogY + DIALOG_HEIGHT - 55;
        graphics.drawString(font, "Edit JSON directly. Arrows/typing to edit.", dialogX + 15, helpY, IsotopeColors.TEXT_MUTED, false);
    }

    /**
     * Render a JSON line with basic syntax highlighting.
     */
    private void renderJsonLine(GuiGraphics graphics, String line, int x, int y) {
        StringBuilder current = new StringBuilder();
        int currentX = x;
        int color = IsotopeColors.TEXT_PRIMARY;
        boolean inString = false;
        boolean isKey = true;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (current.length() > 0) {
                    graphics.drawString(font, current.toString(), currentX, y, color, false);
                    currentX += font.width(current.toString());
                    current.setLength(0);
                }

                inString = !inString;
                if (inString) {
                    color = isKey ? IsotopeColors.ACCENT_AQUA : IsotopeColors.ACCENT_GREEN;
                } else {
                    color = IsotopeColors.TEXT_PRIMARY;
                    isKey = false;
                }
                current.append(c);
            } else if (!inString && (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',')) {
                if (current.length() > 0) {
                    graphics.drawString(font, current.toString(), currentX, y, color, false);
                    currentX += font.width(current.toString());
                    current.setLength(0);
                }

                graphics.drawString(font, String.valueOf(c), currentX, y, IsotopeColors.TEXT_MUTED, false);
                currentX += font.width(String.valueOf(c));

                if (c == ':') {
                    isKey = false;
                } else if (c == ',' || c == '{') {
                    isKey = true;
                }
                color = IsotopeColors.TEXT_PRIMARY;
            } else if (!inString && (Character.isDigit(c) || c == '.' || c == '-')) {
                if (current.length() > 0 && color != IsotopeColors.ACCENT_GOLD) {
                    graphics.drawString(font, current.toString(), currentX, y, color, false);
                    currentX += font.width(current.toString());
                    current.setLength(0);
                }
                color = IsotopeColors.ACCENT_GOLD;
                current.append(c);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            graphics.drawString(font, current.toString(), currentX, y, color, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (button != 0) return super.mouseClicked(event, focused);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Handle raw JSON editor clicks
        if (rawJsonMode) {
            int editorX = dialogX + 10;
            int editorY = dialogY + 40;
            int editorWidth = DIALOG_WIDTH - 20;
            int editorHeight = DIALOG_HEIGHT - 120;
            int lineHeight = font.lineHeight + 2;

            if (mouseX >= editorX && mouseX < editorX + editorWidth &&
                mouseY >= editorY && mouseY < editorY + editorHeight) {
                int clickedLine = (int) ((mouseY - editorY - 4) / lineHeight) + rawJsonScrollOffset;
                clickedLine = Math.max(0, Math.min(clickedLine, rawJsonLines.size() - 1));
                rawJsonCursorLine = clickedLine;

                String line = rawJsonLines.get(clickedLine);
                int textX = editorX + 25;
                int col = 0;
                for (int i = 0; i <= line.length(); i++) {
                    if (textX + font.width(line.substring(0, i)) >= mouseX) {
                        col = i;
                        break;
                    }
                    col = i;
                }
                rawJsonCursorCol = Math.min(col, line.length());
                return true;
            }
            return super.mouseClicked(event, focused);
        }

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

        // Escape to close (works in all modes)
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }

        // Handle raw JSON editing
        if (rawJsonMode) {
            return handleRawJsonKeyPress(keyCode, modifiers);
        }

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

    private boolean handleRawJsonKeyPress(int keyCode, int modifiers) {
        String currentLine = rawJsonLines.get(rawJsonCursorLine);
        rawJsonError = null;

        // Arrow keys for cursor movement
        if (keyCode == UIConstants.KEY_UP) {
            if (rawJsonCursorLine > 0) {
                rawJsonCursorLine--;
                rawJsonCursorCol = Math.min(rawJsonCursorCol, rawJsonLines.get(rawJsonCursorLine).length());
                ensureCursorVisible();
            }
            return true;
        }
        if (keyCode == UIConstants.KEY_DOWN) {
            if (rawJsonCursorLine < rawJsonLines.size() - 1) {
                rawJsonCursorLine++;
                rawJsonCursorCol = Math.min(rawJsonCursorCol, rawJsonLines.get(rawJsonCursorLine).length());
                ensureCursorVisible();
            }
            return true;
        }
        if (keyCode == UIConstants.KEY_LEFT) {
            if (rawJsonCursorCol > 0) {
                rawJsonCursorCol--;
            } else if (rawJsonCursorLine > 0) {
                rawJsonCursorLine--;
                rawJsonCursorCol = rawJsonLines.get(rawJsonCursorLine).length();
            }
            return true;
        }
        if (keyCode == UIConstants.KEY_RIGHT) {
            if (rawJsonCursorCol < currentLine.length()) {
                rawJsonCursorCol++;
            } else if (rawJsonCursorLine < rawJsonLines.size() - 1) {
                rawJsonCursorLine++;
                rawJsonCursorCol = 0;
            }
            return true;
        }

        // Home/End
        if (keyCode == UIConstants.KEY_HOME) {
            rawJsonCursorCol = 0;
            return true;
        }
        if (keyCode == UIConstants.KEY_END) {
            rawJsonCursorCol = currentLine.length();
            return true;
        }

        // Backspace
        if (keyCode == UIConstants.KEY_BACKSPACE) {
            if (rawJsonCursorCol > 0) {
                String newLine = currentLine.substring(0, rawJsonCursorCol - 1) + currentLine.substring(rawJsonCursorCol);
                rawJsonLines.set(rawJsonCursorLine, newLine);
                rawJsonCursorCol--;
            } else if (rawJsonCursorLine > 0) {
                String prevLine = rawJsonLines.get(rawJsonCursorLine - 1);
                rawJsonCursorCol = prevLine.length();
                rawJsonLines.set(rawJsonCursorLine - 1, prevLine + currentLine);
                rawJsonLines.remove(rawJsonCursorLine);
                rawJsonCursorLine--;
            }
            return true;
        }

        // Delete
        if (keyCode == UIConstants.KEY_DELETE) {
            if (rawJsonCursorCol < currentLine.length()) {
                String newLine = currentLine.substring(0, rawJsonCursorCol) + currentLine.substring(rawJsonCursorCol + 1);
                rawJsonLines.set(rawJsonCursorLine, newLine);
            } else if (rawJsonCursorLine < rawJsonLines.size() - 1) {
                rawJsonLines.set(rawJsonCursorLine, currentLine + rawJsonLines.get(rawJsonCursorLine + 1));
                rawJsonLines.remove(rawJsonCursorLine + 1);
            }
            return true;
        }

        // Enter - new line
        if (keyCode == UIConstants.KEY_ENTER || keyCode == UIConstants.KEY_NUMPAD_ENTER) {
            String before = currentLine.substring(0, rawJsonCursorCol);
            String after = currentLine.substring(rawJsonCursorCol);

            int indent = 0;
            for (char c : currentLine.toCharArray()) {
                if (c == ' ') indent++;
                else break;
            }
            if (before.trim().endsWith("{") || before.trim().endsWith("[")) {
                indent += 2;
            }
            String indentStr = " ".repeat(indent);

            rawJsonLines.set(rawJsonCursorLine, before);
            rawJsonLines.add(rawJsonCursorLine + 1, indentStr + after);
            rawJsonCursorLine++;
            rawJsonCursorCol = indent;
            ensureCursorVisible();
            return true;
        }

        return false;
    }

    private void ensureCursorVisible() {
        int lineHeight = font.lineHeight + 2;
        int editorHeight = DIALOG_HEIGHT - 120;
        int visibleLines = editorHeight / lineHeight;

        if (rawJsonCursorLine < rawJsonScrollOffset) {
            rawJsonScrollOffset = rawJsonCursorLine;
        } else if (rawJsonCursorLine >= rawJsonScrollOffset + visibleLines) {
            rawJsonScrollOffset = rawJsonCursorLine - visibleLines + 1;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char chr = (char) event.codepoint(); int modifiers = event.modifiers();

        // Handle raw JSON character input
        if (rawJsonMode) {
            if (chr >= 32) {
                rawJsonError = null;
                String currentLine = rawJsonLines.get(rawJsonCursorLine);
                String newLine = currentLine.substring(0, rawJsonCursorCol) + chr + currentLine.substring(rawJsonCursorCol);
                rawJsonLines.set(rawJsonCursorLine, newLine);
                rawJsonCursorCol++;
                return true;
            }
            return false;
        }

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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (rawJsonMode) {
            int dialogX = (width - DIALOG_WIDTH) / 2;
            int dialogY = (height - DIALOG_HEIGHT) / 2;
            int editorX = dialogX + 10;
            int editorY = dialogY + 40;
            int editorWidth = DIALOG_WIDTH - 20;
            int editorHeight = DIALOG_HEIGHT - 120;

            if (mouseX >= editorX && mouseX < editorX + editorWidth &&
                mouseY >= editorY && mouseY < editorY + editorHeight) {

                int lineHeight = font.lineHeight + 2;
                int visibleLines = editorHeight / lineHeight;
                int maxScroll = Math.max(0, rawJsonLines.size() - visibleLines);

                rawJsonScrollOffset -= (int) scrollY * 3;
                rawJsonScrollOffset = Math.max(0, Math.min(rawJsonScrollOffset, maxScroll));
                return true;
            }
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
        return true;
    }
}
