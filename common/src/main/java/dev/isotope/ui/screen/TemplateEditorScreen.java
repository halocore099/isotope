package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
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
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        int fieldWidth = PANEL_WIDTH - 100;
        int y = panelY + 35;

        // Name
        nameBox = new EditBox(font, panelX + 80, y, fieldWidth, 16, Component.literal("Name"));
        nameBox.setMaxLength(50);
        if (existingTemplate != null && !existingTemplate.name().isEmpty()) {
            nameBox.setValue(existingTemplate.name());
        }
        addRenderableWidget(nameBox);
        y += 24;

        // Description
        descriptionBox = new EditBox(font, panelX + 80, y, fieldWidth, 16, Component.literal("Description"));
        descriptionBox.setMaxLength(100);
        if (existingTemplate != null && !existingTemplate.description().isEmpty()) {
            descriptionBox.setValue(existingTemplate.description());
        }
        addRenderableWidget(descriptionBox);
        y += 24;

        // Category
        categoryBox = new EditBox(font, panelX + 80, y, fieldWidth, 16, Component.literal("Category"));
        categoryBox.setMaxLength(30);
        categoryBox.setValue(existingTemplate != null ? existingTemplate.category() : "Custom");
        addRenderableWidget(categoryBox);
        y += 32;

        // Item (button to open picker) - rendered manually
        y += 24;

        // Weight
        weightBox = new EditBox(font, panelX + 80, y, 60, 16, Component.literal("Weight"));
        weightBox.setMaxLength(4);
        weightBox.setValue(existingTemplate != null ? String.valueOf(existingTemplate.defaultWeight()) : "10");
        weightBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        addRenderableWidget(weightBox);
        y += 24;

        // Count range toggle and fields
        int countFieldWidth = 50;
        countMinBox = new EditBox(font, panelX + 80, y, countFieldWidth, 16, Component.literal("Min"));
        countMinBox.setMaxLength(4);
        countMinBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));

        countMaxBox = new EditBox(font, panelX + 80 + countFieldWidth + 30, y, countFieldWidth, 16, Component.literal("Max"));
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
        y += 32;

        // Functions area
        functionsY = y + 14;
        functionsHeight = PANEL_HEIGHT - (y - panelY) - 80;

        // Buttons
        int buttonY = panelY + PANEL_HEIGHT - 30;
        int buttonWidth = 80;
        int buttonSpacing = 10;
        int totalButtonWidth = buttonWidth * 2 + buttonSpacing;
        int buttonStartX = panelX + (PANEL_WIDTH - totalButtonWidth) / 2;

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveTemplate())
            .bounds(buttonStartX, buttonY, buttonWidth, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
            .bounds(buttonStartX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
            .build());

        setInitialFocus(nameBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF404040);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 10, IsotopeColors.ACCENT_GOLD);

        int y = panelY + 35;

        // Labels
        graphics.drawString(font, "Name:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);
        y += 24;
        graphics.drawString(font, "Desc:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);
        y += 24;
        graphics.drawString(font, "Category:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);
        y += 32;

        // Item selector
        graphics.drawString(font, "Item:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);
        int itemBtnX = panelX + 80;
        int itemBtnWidth = PANEL_WIDTH - 100;
        boolean itemHovered = mouseX >= itemBtnX && mouseX < itemBtnX + itemBtnWidth &&
            mouseY >= y && mouseY < y + 20;

        graphics.fill(itemBtnX, y, itemBtnX + itemBtnWidth, y + 20,
            itemHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(itemBtnX, y, itemBtnWidth, 20, 0xFF505050);

        if (selectedItem != null) {
            // Render item icon
            var itemOpt = BuiltInRegistries.ITEM.get(selectedItem);
            if (itemOpt.isPresent()) {
                graphics.renderItem(new ItemStack(itemOpt.get().value()), itemBtnX + 2, y + 2);
            }
            graphics.drawString(font, selectedItem.toString(), itemBtnX + 22, y + 6, IsotopeColors.TEXT_PRIMARY, false);
        } else {
            graphics.drawString(font, "(Optional) Click to select...", itemBtnX + 4, y + 6, IsotopeColors.TEXT_MUTED, false);
        }

        // Clear button if item selected
        if (selectedItem != null) {
            int clearX = itemBtnX + itemBtnWidth - 16;
            boolean clearHovered = mouseX >= clearX && mouseX < clearX + 14 && mouseY >= y + 3 && mouseY < y + 17;
            graphics.drawString(font, "X", clearX + 4, y + 6, clearHovered ? 0xFFFF6666 : IsotopeColors.TEXT_MUTED, false);
        }

        y += 24;

        // Weight label
        graphics.drawString(font, "Weight:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);
        y += 24;

        // Count label and toggle
        graphics.drawString(font, "Count:", panelX + 10, y + 4, IsotopeColors.TEXT_PRIMARY, false);

        // Range toggle button
        int toggleX = panelX + 80 + 50 + 10;
        graphics.drawString(font, "-", toggleX + 7, y + 4, IsotopeColors.TEXT_MUTED, false);

        // Toggle button
        int toggleBtnX = panelX + PANEL_WIDTH - 80;
        boolean toggleHovered = mouseX >= toggleBtnX && mouseX < toggleBtnX + 70 && mouseY >= y && mouseY < y + 16;
        graphics.fill(toggleBtnX, y, toggleBtnX + 70, y + 16, toggleHovered ? 0xFF404040 : 0xFF303030);
        graphics.renderOutline(toggleBtnX, y, 70, 16, 0xFF505050);
        graphics.drawString(font, useRange ? "Range" : "Constant", toggleBtnX + 4, y + 4,
            IsotopeColors.TEXT_PRIMARY, false);

        countMaxBox.visible = useRange;

        y += 32;

        // Functions section
        graphics.drawString(font, "Functions:", panelX + 10, y, IsotopeColors.TEXT_PRIMARY, false);

        // Add function button
        int addFuncBtnX = panelX + PANEL_WIDTH - 90;
        boolean addFuncHovered = mouseX >= addFuncBtnX && mouseX < addFuncBtnX + 80 &&
            mouseY >= y - 2 && mouseY < y + 12;
        graphics.fill(addFuncBtnX, y - 2, addFuncBtnX + 80, y + 12,
            addFuncHovered ? 0xFF3a5a3a : 0xFF2a3a2a);
        graphics.renderOutline(addFuncBtnX, y - 2, 80, 14, 0xFF4a6a4a);
        graphics.drawString(font, "+ Add Function", addFuncBtnX + 4, y, IsotopeColors.TEXT_PRIMARY, false);

        // Functions list
        int listX = panelX + 10;
        int listWidth = PANEL_WIDTH - 20;
        int listY = functionsY;
        int listHeight = functionsHeight;

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF252525);
        graphics.renderOutline(listX, listY, listWidth, listHeight, 0xFF3a3a3a);

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        hoveredFunctionIdx = -1;
        int funcY = listY + 4 - functionsScrollOffset;
        for (int i = 0; i < functions.size(); i++) {
            LootFunction func = functions.get(i);

            if (funcY + 20 > listY && funcY < listY + listHeight) {
                boolean funcHovered = mouseX >= listX && mouseX < listX + listWidth &&
                    mouseY >= funcY && mouseY < funcY + 20 &&
                    mouseY >= listY && mouseY < listY + listHeight;

                if (funcHovered) {
                    hoveredFunctionIdx = i;
                    graphics.fill(listX + 2, funcY, listX + listWidth - 2, funcY + 20, 0xFF3a3a3a);
                }

                // Function name and params
                String display = func.getDisplayName();
                String params = func.getParameterSummary();
                if (!params.isEmpty()) {
                    display += " (" + params + ")";
                }
                graphics.drawString(font, display, listX + 6, funcY + 6, IsotopeColors.TEXT_PRIMARY, false);

                // Remove button
                int removeX = listX + listWidth - 18;
                boolean removeHovered = mouseX >= removeX && mouseX < removeX + 14 &&
                    mouseY >= funcY + 3 && mouseY < funcY + 17 &&
                    mouseY >= listY && mouseY < listY + listHeight;
                graphics.drawString(font, "X", removeX + 4, funcY + 6,
                    removeHovered ? 0xFFFF6666 : IsotopeColors.TEXT_MUTED, false);
            }

            funcY += 22;
        }

        if (functions.isEmpty()) {
            graphics.drawString(font, "No functions added", listX + 6, listY + 6, IsotopeColors.TEXT_MUTED, false);
        }

        graphics.disableScissor();

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = panelY + 35 + 24 + 24 + 32; // Item row Y

        // Item selector click
        int itemBtnX = panelX + 80;
        int itemBtnWidth = PANEL_WIDTH - 100;
        if (mouseX >= itemBtnX && mouseX < itemBtnX + itemBtnWidth && mouseY >= y && mouseY < y + 20) {
            // Check clear button first
            if (selectedItem != null) {
                int clearX = itemBtnX + itemBtnWidth - 16;
                if (mouseX >= clearX && mouseX < clearX + 14 && mouseY >= y + 3 && mouseY < y + 17) {
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

        y += 24 + 24; // Count row Y

        // Count toggle click
        int toggleBtnX = panelX + PANEL_WIDTH - 80;
        if (mouseX >= toggleBtnX && mouseX < toggleBtnX + 70 && mouseY >= y && mouseY < y + 16) {
            useRange = !useRange;
            if (!useRange) {
                // Copy min to max for constant
                countMaxBox.setValue(countMinBox.getValue());
            }
            return true;
        }

        y += 32;

        // Add function button
        int addFuncBtnX = panelX + PANEL_WIDTH - 90;
        if (mouseX >= addFuncBtnX && mouseX < addFuncBtnX + 80 && mouseY >= y - 2 && mouseY < y + 12) {
            minecraft.setScreen(new FunctionPickerScreen(this, func -> {
                functions.add(func);
            }));
            return true;
        }

        // Function remove buttons
        if (mouseX >= panelX + 10 && mouseX < panelX + PANEL_WIDTH - 10 &&
            mouseY >= functionsY && mouseY < functionsY + functionsHeight) {

            int funcY = functionsY + 4 - functionsScrollOffset;
            for (int i = 0; i < functions.size(); i++) {
                if (funcY + 20 > functionsY && funcY < functionsY + functionsHeight) {
                    int removeX = panelX + PANEL_WIDTH - 10 - 18;
                    if (mouseX >= removeX && mouseX < removeX + 14 &&
                        mouseY >= funcY + 3 && mouseY < funcY + 17) {
                        functions.remove(i);
                        return true;
                    }
                }
                funcY += 22;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Functions list scroll
        if (mouseX >= panelX + 10 && mouseX < panelX + PANEL_WIDTH - 10 &&
            mouseY >= functionsY && mouseY < functionsY + functionsHeight) {

            int contentHeight = functions.size() * 22 + 8;
            int maxScroll = Math.max(0, contentHeight - functionsHeight);
            functionsScrollOffset = Math.max(0, Math.min(maxScroll, functionsScrollOffset - (int)(scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
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

        int weight;
        try {
            weight = Integer.parseInt(weightBox.getValue().trim());
            if (weight < 1) weight = 1;
            if (weight > 9999) weight = 9999;
        } catch (NumberFormatException e) {
            weight = 10;
        }

        int countMin, countMax;
        try {
            countMin = Integer.parseInt(countMinBox.getValue().trim());
            if (countMin < 1) countMin = 1;
        } catch (NumberFormatException e) {
            countMin = 1;
        }

        if (useRange) {
            try {
                countMax = Integer.parseInt(countMaxBox.getValue().trim());
                if (countMax < countMin) countMax = countMin;
            } catch (NumberFormatException e) {
                countMax = countMin;
            }
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
