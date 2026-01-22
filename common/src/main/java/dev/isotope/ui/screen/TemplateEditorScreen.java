package dev.isotope.ui.screen;

import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screen for creating and editing custom templates.
 */
@Environment(EnvType.CLIENT)
public class TemplateEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 400;

    private final Screen parent;
    @Nullable
    private final EntryTemplate existingTemplate;
    @Nullable
    private final Runnable onSaved;

    // Form fields
    private EditBox nameBox;
    private EditBox descriptionBox;
    private EditBox categoryBox;
    private EditBox weightBox;
    private EditBox countMinBox;
    private EditBox countMaxBox;

    // State
    @Nullable
    private ResourceLocation selectedItem;
    private boolean useRange = true;
    private final List<LootFunction> functions = new ArrayList<>();
    private int functionsScrollOffset = 0;
    private int hoveredFunctionIdx = -1;

    // Layout
    private int panelX, panelY;
    private int functionsY, functionsHeight;

    /**
     * Create a new template from scratch.
     */
    public TemplateEditorScreen(Screen parent, @Nullable Runnable onSaved) {
        this(parent, null, onSaved);
    }

    /**
     * Edit an existing template or create from entry.
     */
    public TemplateEditorScreen(Screen parent, @Nullable EntryTemplate existing, @Nullable Runnable onSaved) {
        super(Component.literal(existing != null ? "Edit Template" : "New Template"));
        this.parent = parent;
        this.existingTemplate = existing;
        this.onSaved = onSaved;

        // Pre-fill from existing template
        if (existing != null) {
            this.selectedItem = existing.defaultItem().orElse(null);
            this.functions.addAll(existing.functions());

            NumberProvider count = existing.defaultCount();
            if (count instanceof NumberProvider.Constant) {
                this.useRange = false;
            }
        }
    }

    /**
     * Create a template from a loot entry.
     */
    public static TemplateEditorScreen fromEntry(Screen parent, LootEntry entry, @Nullable Runnable onSaved) {
        // Build a template from the entry
        ResourceLocation itemId = entry.name().orElse(null);

        // Extract count from set_count function
        NumberProvider count = NumberProvider.constant(1);
        List<LootFunction> funcs = new ArrayList<>();
        for (LootFunction func : entry.functions()) {
            if (func.isSetCount()) {
                count = func.getCountAsNumberProvider();
            }
            funcs.add(func);
        }

        EntryTemplate template = new EntryTemplate(
            "",
            "",
            "",
            "Custom",
            Optional.ofNullable(itemId),
            entry.weight(),
            count,
            funcs
        );

        return new TemplateEditorScreen(parent, template, onSaved);
    }

    @Override
    protected void init() {
        super.init(); // Required for proper screen initialization

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        int fieldWidth = PANEL_WIDTH - 100;
        int y = panelY + 40;  // Start after title

        // Section 1: Basic Info (Name, Description, Category)
        int fieldX = panelX + 80;

        // Name - at y position in section
        nameBox = new EditBox(font, fieldX, y + 12, fieldWidth - 14, 16, Component.literal("Name"));
        nameBox.setMaxLength(50);
        nameBox.setHint(Component.literal("Template name..."));
        if (existingTemplate != null && !existingTemplate.name().isEmpty()) {
            nameBox.setValue(existingTemplate.name());
        }
        addRenderableWidget(nameBox);

        // Description
        descriptionBox = new EditBox(font, fieldX, y + 36, fieldWidth - 14, 16, Component.literal("Description"));
        descriptionBox.setMaxLength(100);
        descriptionBox.setHint(Component.literal("Short description..."));
        if (existingTemplate != null && !existingTemplate.description().isEmpty()) {
            descriptionBox.setValue(existingTemplate.description());
        }
        addRenderableWidget(descriptionBox);

        // Category
        categoryBox = new EditBox(font, fieldX, y + 60, 100, 16, Component.literal("Category"));
        categoryBox.setMaxLength(30);
        categoryBox.setValue(existingTemplate != null ? existingTemplate.category() : "Custom");
        addRenderableWidget(categoryBox);

        y += 76;  // Move past section 1

        // Section 2: Item & Values (skip 6 for padding)
        y += 6;

        // Item row - rendered manually, skip 26
        y += 26;

        // Weight
        weightBox = new EditBox(font, fieldX, y, 60, 16, Component.literal("Weight"));
        weightBox.setMaxLength(4);
        weightBox.setValue(existingTemplate != null ? String.valueOf(existingTemplate.defaultWeight()) : "10");
        weightBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        addRenderableWidget(weightBox);
        y += 26;

        // Count fields
        int countFieldWidth = 50;
        countMinBox = new EditBox(font, fieldX, y, countFieldWidth, 16, Component.literal("Min"));
        countMinBox.setMaxLength(4);
        countMinBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));

        countMaxBox = new EditBox(font, fieldX + countFieldWidth + 25, y, countFieldWidth, 16, Component.literal("Max"));
        countMaxBox.setMaxLength(4);
        countMaxBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));

        if (existingTemplate != null) {
            NumberProvider count = existingTemplate.defaultCount();
            if (count instanceof NumberProvider.Constant c) {
                countMinBox.setValue(String.valueOf((int) c.value()));
                countMaxBox.setValue(String.valueOf((int) c.value()));
                useRange = false;
            } else if (count instanceof NumberProvider.Uniform u) {
                countMinBox.setValue(String.valueOf((int) u.min()));
                countMaxBox.setValue(String.valueOf((int) u.max()));
                useRange = true;
            }
        } else {
            countMinBox.setValue("1");
            countMaxBox.setValue("3");
        }

        addRenderableWidget(countMinBox);
        addRenderableWidget(countMaxBox);
        y += 28;

        // Section 3: Functions area
        functionsY = y + 22;  // After section header
        functionsHeight = PANEL_HEIGHT - (y - panelY) - 70;

        // Buttons
        int buttonY = panelY + PANEL_HEIGHT - 30;
        int buttonWidth = 80;
        int buttonSpacing = 10;
        int totalButtonWidth = buttonWidth * 2 + buttonSpacing;
        int buttonStartX = panelX + (PANEL_WIDTH - totalButtonWidth) / 2;

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveTemplate())
            .bounds(buttonStartX, buttonY, buttonWidth, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal(UIConstants.LABEL_CANCEL), btn -> onClose())
            .bounds(buttonStartX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
            .build());

        setInitialFocus(nameBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // MUST call super.render() first for content to be visible in MC 1.21
        super.render(graphics, mouseX, mouseY, partialTick);

        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DIM);

        // Panel background with vanilla-style border
        graphics.fill(panelX - 3, panelY - 3, panelX + PANEL_WIDTH + 3, panelY + PANEL_HEIGHT + 3, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, IsotopeColors.BUTTON_BACKGROUND);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, IsotopeColors.BACKGROUND_DARKEST);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Title
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 10, IsotopeColors.ACCENT_GOLD);

        // Subtitle hint
        graphics.drawCenteredString(font, "Create a reusable entry preset", panelX + PANEL_WIDTH / 2, panelY + 22, IsotopeColors.TEXT_MUTED);

        int y = panelY + 40;

        // Section: Basic Info
        graphics.fill(panelX + 8, y - 4, panelX + PANEL_WIDTH - 8, y + 68, IsotopeColors.BACKGROUND_DARKER);
        ScreenUtils.renderOutline(graphics, panelX + 8, y - 4, PANEL_WIDTH - 16, 72, IsotopeColors.BORDER_DEFAULT);

        // Name field
        graphics.drawString(font, "Name", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);
        y += 24;

        // Description field
        graphics.drawString(font, "Description", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);
        y += 24;

        // Category field
        graphics.drawString(font, "Category", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);
        y += 28;

        // Section: Item & Values
        graphics.fill(panelX + 8, y, panelX + PANEL_WIDTH - 8, y + 92, IsotopeColors.BACKGROUND_DARKER);
        ScreenUtils.renderOutline(graphics, panelX + 8, y, PANEL_WIDTH - 16, 92, IsotopeColors.BORDER_DEFAULT);

        y += 6;

        // Item selector
        graphics.drawString(font, "Item", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);

        int itemBtnX = panelX + 80;
        int itemBtnWidth = PANEL_WIDTH - 96;
        boolean itemHovered = mouseX >= itemBtnX && mouseX < itemBtnX + itemBtnWidth &&
            mouseY >= y - 2 && mouseY < y + 18;

        graphics.fill(itemBtnX, y - 2, itemBtnX + itemBtnWidth, y + 18,
            itemHovered ? IsotopeColors.SYNTAX_BLUE_DARK : IsotopeColors.ENTRY_BACKGROUND);
        ScreenUtils.renderOutline(graphics, itemBtnX, y - 2, itemBtnWidth, 20, itemHovered ? IsotopeColors.SYNTAX_BLUE : IsotopeColors.BORDER_DEFAULT);

        if (selectedItem != null) {
            // Render item icon
            var itemOpt = BuiltInRegistries.ITEM.get(selectedItem);
            if (itemOpt.isPresent()) {
                graphics.renderItem(new ItemStack(itemOpt.get().value()), itemBtnX + 2, y);
            }
            String itemName = selectedItem.getPath();
            if (itemName.length() > 18) itemName = itemName.substring(0, 15) + "...";
            graphics.drawString(font, itemName, itemBtnX + 22, y + 4, IsotopeColors.TEXT_PRIMARY, false);

            // Clear button
            int clearX = itemBtnX + itemBtnWidth - 18;
            boolean clearHovered = ScreenUtils.isMouseOver(mouseX, mouseY, clearX, y, 16, 16);
            graphics.fill(clearX, y, clearX + 16, y + 16, clearHovered ? IsotopeColors.DESTRUCTIVE_BACKGROUND : IsotopeColors.DESTRUCTIVE_BACKGROUND);
            graphics.drawCenteredString(font, "X", clearX + 8, y + 4, clearHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.DESTRUCTIVE_TEXT);
        } else {
            graphics.drawString(font, "Click to select...", itemBtnX + 4, y + 4, IsotopeColors.SCROLLBAR_THUMB, false);
        }

        y += 26;

        // Weight
        graphics.drawString(font, "Weight", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);
        y += 26;

        // Count
        graphics.drawString(font, "Count", panelX + 14, y + 2, IsotopeColors.TEXT_PRIMARY, false);

        // "to" label between min and max
        if (useRange) {
            graphics.drawString(font, "to", panelX + 80 + 55, y + 2, IsotopeColors.SCROLLBAR_THUMB, false);
        }

        // Toggle button for Range/Constant
        int toggleBtnX = panelX + PANEL_WIDTH - 80;
        boolean toggleHovered = ScreenUtils.isMouseOver(mouseX, mouseY, toggleBtnX, y - 2, 66, 16);
        int toggleBg = useRange ? IsotopeColors.SUCCESS_TINT : IsotopeColors.TOGGLE_OFF_BG;
        graphics.fill(toggleBtnX, y - 2, toggleBtnX + 66, y + 14, toggleHovered ? IsotopeColors.BORDER_DEFAULT : toggleBg);
        ScreenUtils.renderOutline(graphics, toggleBtnX, y - 2, 66, 16, toggleHovered ? IsotopeColors.BORDER_HIGHLIGHT : IsotopeColors.INPUT_BORDER);
        graphics.drawCenteredString(font, useRange ? "Range" : "Fixed", toggleBtnX + 33, y + 1,
            useRange ? IsotopeColors.TOGGLE_ON_TEXT : IsotopeColors.TOGGLE_OFF_TEXT);

        countMaxBox.visible = useRange;

        y += 28;

        // Section: Functions
        graphics.fill(panelX + 8, y, panelX + PANEL_WIDTH - 8, y + functionsHeight + 24, IsotopeColors.BACKGROUND_DARKER);
        ScreenUtils.renderOutline(graphics, panelX + 8, y, PANEL_WIDTH - 16, functionsHeight + 24, IsotopeColors.BORDER_DEFAULT);

        y += 6;

        graphics.drawString(font, "Functions", panelX + 14, y, IsotopeColors.TEXT_PRIMARY, false);

        // Add function button
        int addFuncBtnX = panelX + PANEL_WIDTH - 100;
        boolean addFuncHovered = mouseX >= addFuncBtnX && mouseX < addFuncBtnX + 86 &&
            mouseY >= y - 4 && mouseY < y + 12;
        graphics.fill(addFuncBtnX, y - 4, addFuncBtnX + 86, y + 12,
            addFuncHovered ? IsotopeColors.FUNC_ADD_HOVER : IsotopeColors.SUCCESS_TINT);
        ScreenUtils.renderOutline(graphics, addFuncBtnX, y - 4, 86, 16, addFuncHovered ? IsotopeColors.FUNC_ADD_BORDER : IsotopeColors.FUNC_ADD_BORDER_DEFAULT);
        graphics.drawCenteredString(font, "+ Add Function", addFuncBtnX + 43, y - 1, addFuncHovered ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.SUCCESS_MUTED);

        y += 16;

        // Functions list
        int listX = panelX + 14;
        int listWidth = PANEL_WIDTH - 36;
        int listY = y;
        int listHeight = functionsHeight - 4;

        graphics.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, IsotopeColors.BACKGROUND_MEDIUM);

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        hoveredFunctionIdx = -1;
        int funcY = listY + 4 - functionsScrollOffset;
        for (int i = 0; i < functions.size(); i++) {
            LootFunction func = functions.get(i);

            if (funcY + 24 > listY && funcY < listY + listHeight) {
                boolean funcHovered = mouseX >= listX && mouseX < listX + listWidth &&
                    mouseY >= funcY && mouseY < funcY + 24 &&
                    mouseY >= listY && mouseY < listY + listHeight;

                if (funcHovered) {
                    hoveredFunctionIdx = i;
                    graphics.fill(listX + 2, funcY, listX + listWidth - 2, funcY + 22, IsotopeColors.SYNTAX_BLUE_DARK);
                    ScreenUtils.renderOutline(graphics, listX + 2, funcY, listWidth - 4, 22, IsotopeColors.SYNTAX_BLUE);
                } else {
                    graphics.fill(listX + 2, funcY, listX + listWidth - 2, funcY + 22, IsotopeColors.ENTRY_BACKGROUND);
                    ScreenUtils.renderOutline(graphics, listX + 2, funcY, listWidth - 4, 22, IsotopeColors.POOL_HEADER_HOVER);
                }

                // Function icon placeholder
                graphics.fill(listX + 6, funcY + 4, listX + 18, funcY + 18, IsotopeColors.FUNC_ICON_BG);
                graphics.drawCenteredString(font, "f", listX + 12, funcY + 7, IsotopeColors.SUCCESS_MUTED);

                // Function name
                String display = func.getDisplayName();
                graphics.drawString(font, display, listX + 24, funcY + 4, IsotopeColors.TEXT_PRIMARY, false);

                // Parameters
                String params = func.getParameterSummary();
                if (!params.isEmpty()) {
                    if (params.length() > 30) params = params.substring(0, 27) + "...";
                    graphics.drawString(font, params, listX + 24, funcY + 13, IsotopeColors.SCROLLBAR_THUMB, false);
                }

                // Remove button
                int removeX = listX + listWidth - 22;
                boolean removeHovered = mouseX >= removeX && mouseX < removeX + 18 &&
                    mouseY >= funcY + 2 && mouseY < funcY + 20 &&
                    mouseY >= listY && mouseY < listY + listHeight;
                graphics.fill(removeX, funcY + 3, removeX + 18, funcY + 19, removeHovered ? IsotopeColors.DESTRUCTIVE_BACKGROUND : IsotopeColors.DESTRUCTIVE_BACKGROUND);
                graphics.drawCenteredString(font, "X", removeX + 9, funcY + 7, removeHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.DESTRUCTIVE_TEXT);
            }

            funcY += 26;
        }

        if (functions.isEmpty()) {
            graphics.drawCenteredString(font, "No functions added", listX + listWidth / 2, listY + listHeight / 2 - 4, IsotopeColors.BORDER_HIGHLIGHT);
            graphics.drawCenteredString(font, "Click '+ Add Function' above", listX + listWidth / 2, listY + listHeight / 2 + 8, IsotopeColors.INPUT_BORDER);
        }

        graphics.disableScissor();

        // Re-render widgets on top
        for (var widget : this.children()) {
            if (widget instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof EditBox box) {
                box.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Calculate positions matching render()
        int y = panelY + 40;  // Start after title
        y += 76;  // Past section 1 (basic info)
        y += 6;   // Section 2 padding

        // Item selector row
        int itemBtnX = panelX + 80;
        int itemBtnWidth = PANEL_WIDTH - 96;
        int itemY = y;

        if (ScreenUtils.isMouseOver(mouseX, mouseY, itemBtnX, itemY - 2, itemBtnWidth, 20)) {
            // Check clear button first
            if (selectedItem != null) {
                int clearX = itemBtnX + itemBtnWidth - 18;
                if (ScreenUtils.isMouseOver(mouseX, mouseY, clearX, itemY, 16, 16)) {
                    selectedItem = null;
                    return true;
                }
            }

            // Open item picker
            minecraft.setScreen(new ItemPickerScreen(this, itemId -> {
                selectedItem = itemId;
            }));
            return true;
        }

        y += 26;  // Weight row
        y += 26;  // Count row

        // Count toggle click
        int toggleBtnX = panelX + PANEL_WIDTH - 80;
        if (ScreenUtils.isMouseOver(mouseX, mouseY, toggleBtnX, y - 2, 66, 16)) {
            useRange = !useRange;
            if (!useRange) {
                // Copy min to max for constant
                countMaxBox.setValue(countMinBox.getValue());
            }
            return true;
        }

        y += 28;  // Functions section
        y += 6;   // Padding

        // Add function button
        int addFuncBtnX = panelX + PANEL_WIDTH - 100;
        if (ScreenUtils.isMouseOver(mouseX, mouseY, addFuncBtnX, y - 4, 86, 16)) {
            minecraft.setScreen(new FunctionPickerScreen(this, func -> {
                functions.add(func);
            }));
            return true;
        }

        // Function remove buttons
        int listX = panelX + 14;
        int listWidth = PANEL_WIDTH - 36;
        int listY = functionsY;
        int listHeight = functionsHeight - 4;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int funcY = listY + 4 - functionsScrollOffset;
            for (int i = 0; i < functions.size(); i++) {
                if (funcY + 24 > listY && funcY < listY + listHeight) {
                    int removeX = listX + listWidth - 22;
                    if (mouseX >= removeX && mouseX < removeX + 18 &&
                        mouseY >= funcY + 2 && mouseY < funcY + 20) {
                        functions.remove(i);
                        return true;
                    }
                }
                funcY += 26;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Functions list scroll
        int listX = panelX + 14;
        int listWidth = PANEL_WIDTH - 36;
        int listY = functionsY;
        int listHeight = functionsHeight - 4;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int contentHeight = functions.size() * 26 + 8;
            int maxScroll = Math.max(0, contentHeight - listHeight);
            functionsScrollOffset = Math.max(0, Math.min(maxScroll, functionsScrollOffset - (int)(scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveTemplate() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            IsotopeToast.error("Error", "Template name is required");
            return;
        }

        String description = descriptionBox.getValue().trim();
        String category = categoryBox.getValue().trim();
        if (category.isEmpty()) {
            category = "Custom";
        }

        int weight = ScreenUtils.parseIntSafe(weightBox.getValue(), 10);
        weight = Math.max(1, Math.min(9999, weight));

        int countMin = ScreenUtils.parseIntSafe(countMinBox.getValue(), 1);
        countMin = Math.max(1, countMin);

        int countMax;
        if (useRange) {
            countMax = ScreenUtils.parseIntSafe(countMaxBox.getValue(), countMin);
            if (countMax < countMin) countMax = countMin;
        } else {
            countMax = countMin;
        }

        NumberProvider count;
        if (countMin == countMax) {
            count = NumberProvider.constant(countMin);
        } else {
            count = NumberProvider.uniform(countMin, countMax);
        }

        // Generate ID
        String id;
        if (existingTemplate != null && !existingTemplate.id().isEmpty()) {
            id = existingTemplate.id();
        } else {
            id = TemplateManager.getInstance().generateId(name);
        }

        // Create and save
        EntryTemplate template = new EntryTemplate(
            id, name, description, category,
            Optional.ofNullable(selectedItem),
            weight, count,
            new ArrayList<>(functions)
        );

        TemplateManager.getInstance().save(template);
        IsotopeToast.success("Saved", "Template '" + name + "' saved");

        if (onSaved != null) {
            onSaved.run();
        }

        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
