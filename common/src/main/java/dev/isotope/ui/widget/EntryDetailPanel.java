package dev.isotope.ui.widget;

import dev.isotope.data.loot.LootCondition;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.NumberProvider;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Panel displaying details of a selected loot entry.
 * Shows item info, weight, count, functions, and conditions.
 * Includes editing controls for weight and count.
 */
@Environment(EnvType.CLIENT)
public class EntryDetailPanel extends AbstractWidget {

    @Nullable
    private LootEntry entry;
    private int poolIndex = -1;
    private int entryIndex = -1;

    private int scrollOffset = 0;
    private int contentHeight = 0;

    // Edit callbacks
    @Nullable
    private BiConsumer<Integer, Integer> onWeightChanged;  // (entryIndex, newWeight)
    @Nullable
    private BiConsumer<Integer, NumberProvider> onCountChanged;  // (entryIndex, newCount)
    @Nullable
    private Runnable onDeleteEntry;
    @Nullable
    private Runnable onAddFunction;
    @Nullable
    private java.util.function.Consumer<Integer> onRemoveFunction;  // functionIndex
    @Nullable
    private Runnable onAddCondition;
    @Nullable
    private java.util.function.Consumer<Integer> onRemoveCondition;  // conditionIndex

    // Button bounds for click detection
    private int weightMinusBtnX, weightMinusBtnY;
    private int weightPlusBtnX, weightPlusBtnY;
    private int countMinusBtnX, countMinusBtnY;
    private int countPlusBtnX, countPlusBtnY;
    private int deleteBtnX, deleteBtnY, deleteBtnWidth;
    private int addFunctionBtnX, addFunctionBtnY, addFunctionBtnWidth;
    private int addConditionBtnX, addConditionBtnY, addConditionBtnWidth;
    private final java.util.List<int[]> removeFunctionBtns = new java.util.ArrayList<>();
    private final java.util.List<int[]> removeConditionBtns = new java.util.ArrayList<>();
    private static final int BTN_SIZE = 14;

    public EntryDetailPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public void setOnWeightChanged(@Nullable BiConsumer<Integer, Integer> callback) {
        this.onWeightChanged = callback;
    }

    public void setOnCountChanged(@Nullable BiConsumer<Integer, NumberProvider> callback) {
        this.onCountChanged = callback;
    }

    public void setOnDeleteEntry(@Nullable Runnable callback) {
        this.onDeleteEntry = callback;
    }

    public void setOnAddFunction(@Nullable Runnable callback) {
        this.onAddFunction = callback;
    }

    public void setOnRemoveFunction(@Nullable java.util.function.Consumer<Integer> callback) {
        this.onRemoveFunction = callback;
    }

    public void setOnAddCondition(@Nullable Runnable callback) {
        this.onAddCondition = callback;
    }

    public void setOnRemoveCondition(@Nullable java.util.function.Consumer<Integer> callback) {
        this.onRemoveCondition = callback;
    }

    public void setEntry(@Nullable LootEntry entry, int poolIndex, int entryIndex) {
        this.entry = entry;
        this.poolIndex = poolIndex;
        this.entryIndex = entryIndex;
        this.scrollOffset = 0;
        calculateContentHeight();
    }

    public void clearEntry() {
        setEntry(null, -1, -1);
    }

    @Nullable
    public LootEntry getEntry() {
        return entry;
    }

    public int getPoolIndex() {
        return poolIndex;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    private void calculateContentHeight() {
        if (entry == null) {
            contentHeight = 0;
            return;
        }

        var font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 2;

        // Base info: type, name, weight, count
        contentHeight = lineHeight * 5;

        // Functions
        contentHeight += lineHeight; // "Functions:" header
        contentHeight += entry.functions().size() * lineHeight;

        // Conditions
        contentHeight += lineHeight; // "Conditions:" header
        contentHeight += entry.conditions().size() * lineHeight;

        // Children (if composite)
        if (!entry.children().isEmpty()) {
            contentHeight += lineHeight; // "Children:" header
            contentHeight += entry.children().size() * lineHeight;
        }

        contentHeight += 20; // Padding
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Panel background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, IsotopeColors.BACKGROUND_SLOT_DARK);

        // Border
        renderBorder(graphics);

        if (entry == null) {
            renderEmptyState(graphics);
            return;
        }

        // Enable scissor for scrolling
        graphics.enableScissor(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2);

        renderEntryDetails(graphics);

        graphics.disableScissor();

        // Scrollbar if needed
        if (contentHeight > height - 4) {
            renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderBorder(GuiGraphics graphics) {
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + 1, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(x, y, x + 1, y + height, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(x, y + height - 1, x + width, y + height, IsotopeColors.BORDER_OUTER_LIGHT);
        graphics.fill(x + width - 1, y, x + width, y + height, IsotopeColors.BORDER_OUTER_LIGHT);
    }

    private void renderEmptyState(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        String text = "Select an entry";
        int textWidth = font.width(text);
        graphics.drawString(font, text,
            getX() + (width - textWidth) / 2,
            getY() + height / 2 - font.lineHeight / 2,
            IsotopeColors.TEXT_MUTED, false);
    }

    private void renderEntryDetails(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 2;
        int x = getX() + 6;
        int y = getY() + 6 - scrollOffset;

        // Header with item icon
        if (entry.isItem() && entry.name().isPresent()) {
            ItemStack stack = getItemStack(entry.name().get());
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y);
            }
        }

        // Entry type
        graphics.drawString(font, "Type:", x, y, IsotopeColors.TEXT_MUTED, false);
        String typeStr = entry.type().replace("minecraft:", "");
        graphics.drawString(font, typeStr, x + 40, y, IsotopeColors.TEXT_SECONDARY, false);
        y += lineHeight;

        // Name/ID
        if (entry.name().isPresent()) {
            graphics.drawString(font, "ID:", x, y, IsotopeColors.TEXT_MUTED, false);
            String nameStr = entry.name().get().toString();
            if (nameStr.length() > 25) {
                nameStr = "..." + nameStr.substring(nameStr.length() - 22);
            }
            graphics.drawString(font, nameStr, x + 40, y, IsotopeColors.TEXT_PRIMARY, false);
        }
        y += lineHeight;

        // Weight with +/- buttons
        graphics.drawString(font, "Weight:", x, y, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, String.valueOf(entry.weight()), x + 50, y,
            IsotopeColors.ACCENT_GREEN, false);

        // Weight buttons (only if editing enabled)
        if (onWeightChanged != null) {
            int btnY = y - 2;
            weightMinusBtnX = x + 75;
            weightMinusBtnY = btnY;
            renderSmallButton(graphics, weightMinusBtnX, weightMinusBtnY, "-");

            weightPlusBtnX = x + 92;
            weightPlusBtnY = btnY;
            renderSmallButton(graphics, weightPlusBtnX, weightPlusBtnY, "+");
        }
        y += lineHeight;

        // Count with +/- buttons
        graphics.drawString(font, "Count:", x, y, IsotopeColors.TEXT_MUTED, false);
        graphics.drawString(font, entry.getCountString(), x + 50, y,
            IsotopeColors.ACCENT_AQUA, false);

        // Count buttons (only if editing enabled and is item entry)
        if (onCountChanged != null && entry.isItem()) {
            int btnY = y - 2;
            countMinusBtnX = x + 85;
            countMinusBtnY = btnY;
            renderSmallButton(graphics, countMinusBtnX, countMinusBtnY, "-");

            countPlusBtnX = x + 102;
            countPlusBtnY = btnY;
            renderSmallButton(graphics, countPlusBtnX, countPlusBtnY, "+");
        }
        y += lineHeight;

        // Quality
        if (entry.quality() != 0) {
            graphics.drawString(font, "Quality:", x, y, IsotopeColors.TEXT_MUTED, false);
            graphics.drawString(font, String.valueOf(entry.quality()), x + 50, y,
                IsotopeColors.TEXT_SECONDARY, false);
            y += lineHeight;
        }

        y += 4; // Spacer

        // Clear button lists
        removeFunctionBtns.clear();
        removeConditionBtns.clear();

        // Functions section
        graphics.drawString(font, "Functions:", x, y, IsotopeColors.ACCENT_GOLD, false);
        y += lineHeight;

        if (entry.functions().isEmpty()) {
            graphics.drawString(font, "  (none)", x, y, IsotopeColors.TEXT_MUTED, false);
            y += lineHeight;
        } else {
            int funcIndex = 0;
            for (LootFunction func : entry.functions()) {
                String funcName = "  " + func.getDisplayName();
                String params = func.getParameterSummary();
                graphics.drawString(font, funcName, x, y, IsotopeColors.TEXT_SECONDARY, false);
                if (!params.isEmpty()) {
                    int funcWidth = font.width(funcName);
                    graphics.drawString(font, " (" + params + ")", x + funcWidth, y,
                        IsotopeColors.TEXT_MUTED, false);
                }

                // [X] remove button
                if (onRemoveFunction != null) {
                    int btnX = x + width - 30;
                    int btnY = y - 1;
                    renderMiniButton(graphics, btnX, btnY, "X", IsotopeColors.DESTRUCTIVE_BACKGROUND);
                    removeFunctionBtns.add(new int[]{btnX, btnY, funcIndex});
                }

                y += lineHeight;
                funcIndex++;
            }
        }

        // Add Function button
        if (onAddFunction != null) {
            addFunctionBtnX = x + 4;
            addFunctionBtnY = y;
            addFunctionBtnWidth = 60;
            renderAddButton(graphics, addFunctionBtnX, addFunctionBtnY, addFunctionBtnWidth, "+ Add");
            y += 18;
        }

        y += 4; // Spacer

        // Conditions section
        graphics.drawString(font, "Conditions:", x, y, IsotopeColors.ACCENT_GOLD, false);
        y += lineHeight;

        if (entry.conditions().isEmpty()) {
            graphics.drawString(font, "  (none)", x, y, IsotopeColors.TEXT_MUTED, false);
            y += lineHeight;
        } else {
            int condIndex = 0;
            for (LootCondition cond : entry.conditions()) {
                String condName = "  " + cond.getDisplayName();
                String params = cond.getParameterSummary();
                graphics.drawString(font, condName, x, y, IsotopeColors.TEXT_SECONDARY, false);
                if (!params.isEmpty()) {
                    int condWidth = font.width(condName);
                    graphics.drawString(font, " " + params, x + condWidth, y,
                        IsotopeColors.TEXT_MUTED, false);
                }

                // [X] remove button
                if (onRemoveCondition != null) {
                    int btnX = x + width - 30;
                    int btnY = y - 1;
                    renderMiniButton(graphics, btnX, btnY, "X", IsotopeColors.DESTRUCTIVE_BACKGROUND);
                    removeConditionBtns.add(new int[]{btnX, btnY, condIndex});
                }

                y += lineHeight;
                condIndex++;
            }
        }

        // Add Condition button
        if (onAddCondition != null) {
            addConditionBtnX = x + 4;
            addConditionBtnY = y;
            addConditionBtnWidth = 60;
            renderAddButton(graphics, addConditionBtnX, addConditionBtnY, addConditionBtnWidth, "+ Add");
            y += 18;
        }

        // Children section (for composite entries)
        if (!entry.children().isEmpty()) {
            y += 4;
            graphics.drawString(font, "Children: " + entry.children().size(), x, y,
                IsotopeColors.ACCENT_GOLD, false);
            y += lineHeight;

            for (LootEntry child : entry.children()) {
                String childName = "  " + child.getDisplayName();
                if (childName.length() > 28) {
                    childName = childName.substring(0, 25) + "...";
                }
                graphics.drawString(font, childName, x, y, IsotopeColors.TEXT_SECONDARY, false);
                y += lineHeight;
            }
        }

        // Delete button at the bottom
        if (onDeleteEntry != null) {
            y += 10;
            deleteBtnX = x;
            deleteBtnY = y;
            deleteBtnWidth = 70;
            renderDeleteButton(graphics, deleteBtnX, deleteBtnY, deleteBtnWidth, "Remove");
        }
    }

    private void renderSmallButton(GuiGraphics graphics, int x, int y, String label) {
        var font = Minecraft.getInstance().font;

        // Button background
        graphics.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, IsotopeColors.BUTTON_BACKGROUND);

        // 3D border
        graphics.fill(x, y, x + BTN_SIZE, y + 1, 0xFFC6C6C6);
        graphics.fill(x, y, x + 1, y + BTN_SIZE, 0xFFC6C6C6);
        graphics.fill(x, y + BTN_SIZE - 1, x + BTN_SIZE, y + BTN_SIZE, 0xFF2A2A2A);
        graphics.fill(x + BTN_SIZE - 1, y, x + BTN_SIZE, y + BTN_SIZE, 0xFF2A2A2A);

        // Label centered
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (BTN_SIZE - textWidth) / 2, y + 3,
            IsotopeColors.TEXT_PRIMARY, false);
    }

    private void renderDeleteButton(GuiGraphics graphics, int x, int y, int width, String label) {
        var font = Minecraft.getInstance().font;
        int btnHeight = 16;

        // Red background for destructive action
        graphics.fill(x, y, x + width, y + btnHeight, IsotopeColors.DESTRUCTIVE_BACKGROUND);

        // Border
        graphics.fill(x, y, x + width, y + 1, 0xFF8A4040);
        graphics.fill(x, y, x + 1, y + btnHeight, 0xFF8A4040);
        graphics.fill(x, y + btnHeight - 1, x + width, y + btnHeight, 0xFF3A1010);
        graphics.fill(x + width - 1, y, x + width, y + btnHeight, 0xFF3A1010);

        // Label centered
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (width - textWidth) / 2, y + 4,
            IsotopeColors.DESTRUCTIVE_TEXT, false);
    }

    private void renderMiniButton(GuiGraphics graphics, int x, int y, String label, int bgColor) {
        var font = Minecraft.getInstance().font;
        int size = 12;

        // Button background
        graphics.fill(x, y, x + size, y + size, bgColor);

        // Label centered
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (size - textWidth) / 2, y + 2,
            IsotopeColors.TEXT_PRIMARY, false);
    }

    private void renderAddButton(GuiGraphics graphics, int x, int y, int width, String label) {
        var font = Minecraft.getInstance().font;
        int btnHeight = 14;

        // Green-ish background
        graphics.fill(x, y, x + width, y + btnHeight, 0xFF3A5A3A);

        // Border
        graphics.fill(x, y, x + width, y + 1, 0xFF5A7A5A);
        graphics.fill(x, y, x + 1, y + btnHeight, 0xFF5A7A5A);
        graphics.fill(x, y + btnHeight - 1, x + width, y + btnHeight, 0xFF1A3A1A);
        graphics.fill(x + width - 1, y, x + width, y + btnHeight, 0xFF1A3A1A);

        // Label centered
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (width - textWidth) / 2, y + 3,
            IsotopeColors.ACCENT_GREEN, false);
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int scrollbarWidth = 4;
        int scrollbarX = getX() + width - scrollbarWidth - 2;
        int trackY = getY() + 2;
        int trackHeight = height - 4;

        // Track
        graphics.fill(scrollbarX, trackY, scrollbarX + scrollbarWidth, trackY + trackHeight,
            IsotopeColors.SCROLLBAR_TRACK);

        // Thumb
        int maxScroll = Math.max(1, contentHeight - height + 4);
        int thumbHeight = Math.max(20, (height - 4) * (height - 4) / contentHeight);
        int thumbY = trackY + (int) ((float) scrollOffset / maxScroll * (trackHeight - thumbHeight));

        graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight,
            IsotopeColors.SCROLLBAR_THUMB);
    }

    private ItemStack getItemStack(ResourceLocation itemId) {
        try {
            var itemOpt = BuiltInRegistries.ITEM.get(itemId);
            if (itemOpt.isPresent()) {
                return new ItemStack(itemOpt.get().value());
            }
        } catch (Exception e) {
            // Ignore
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0 || entry == null) return false;

        // Check weight buttons
        if (onWeightChanged != null) {
            if (isInButton(mouseX, mouseY, weightMinusBtnX, weightMinusBtnY)) {
                int newWeight = Math.max(1, entry.weight() - 1);
                onWeightChanged.accept(entryIndex, newWeight);
                return true;
            }
            if (isInButton(mouseX, mouseY, weightPlusBtnX, weightPlusBtnY)) {
                int newWeight = entry.weight() + 1;
                onWeightChanged.accept(entryIndex, newWeight);
                return true;
            }
        }

        // Check count buttons (for item entries)
        if (onCountChanged != null && entry.isItem()) {
            if (isInButton(mouseX, mouseY, countMinusBtnX, countMinusBtnY)) {
                // Decrease min count
                LootFunction setCount = entry.getSetCountFunction();
                NumberProvider current = setCount != null ? setCount.getCountAsNumberProvider() : NumberProvider.constant(1);
                NumberProvider newCount = decreaseCount(current);
                onCountChanged.accept(entryIndex, newCount);
                return true;
            }
            if (isInButton(mouseX, mouseY, countPlusBtnX, countPlusBtnY)) {
                // Increase max count
                LootFunction setCount = entry.getSetCountFunction();
                NumberProvider current = setCount != null ? setCount.getCountAsNumberProvider() : NumberProvider.constant(1);
                NumberProvider newCount = increaseCount(current);
                onCountChanged.accept(entryIndex, newCount);
                return true;
            }
        }

        // Check delete button
        if (onDeleteEntry != null && deleteBtnWidth > 0) {
            if (mouseX >= deleteBtnX && mouseX < deleteBtnX + deleteBtnWidth
                && mouseY >= deleteBtnY && mouseY < deleteBtnY + 16) {
                onDeleteEntry.run();
                return true;
            }
        }

        // Check Add Function button
        if (onAddFunction != null && addFunctionBtnWidth > 0) {
            if (mouseX >= addFunctionBtnX && mouseX < addFunctionBtnX + addFunctionBtnWidth
                && mouseY >= addFunctionBtnY && mouseY < addFunctionBtnY + 14) {
                onAddFunction.run();
                return true;
            }
        }

        // Check Remove Function buttons
        if (onRemoveFunction != null) {
            for (int[] btn : removeFunctionBtns) {
                if (mouseX >= btn[0] && mouseX < btn[0] + 12
                    && mouseY >= btn[1] && mouseY < btn[1] + 12) {
                    onRemoveFunction.accept(btn[2]);
                    return true;
                }
            }
        }

        // Check Add Condition button
        if (onAddCondition != null && addConditionBtnWidth > 0) {
            if (mouseX >= addConditionBtnX && mouseX < addConditionBtnX + addConditionBtnWidth
                && mouseY >= addConditionBtnY && mouseY < addConditionBtnY + 14) {
                onAddCondition.run();
                return true;
            }
        }

        // Check Remove Condition buttons
        if (onRemoveCondition != null) {
            for (int[] btn : removeConditionBtns) {
                if (mouseX >= btn[0] && mouseX < btn[0] + 12
                    && mouseY >= btn[1] && mouseY < btn[1] + 12) {
                    onRemoveCondition.accept(btn[2]);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isInButton(double mouseX, double mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX < btnX + BTN_SIZE
            && mouseY >= btnY && mouseY < btnY + BTN_SIZE;
    }

    private NumberProvider decreaseCount(NumberProvider current) {
        return switch (current) {
            case NumberProvider.Constant c -> NumberProvider.constant(Math.max(1, c.value() - 1));
            case NumberProvider.Uniform u -> {
                float newMin = Math.max(1, u.min() - 1);
                float newMax = Math.max(newMin, u.max() - 1);
                if (newMin == newMax) {
                    yield NumberProvider.constant(newMin);
                }
                yield NumberProvider.uniform(newMin, newMax);
            }
            case NumberProvider.Binomial b -> NumberProvider.binomial(Math.max(1, b.n() - 1), b.p());
        };
    }

    private NumberProvider increaseCount(NumberProvider current) {
        return switch (current) {
            case NumberProvider.Constant c -> NumberProvider.constant(c.value() + 1);
            case NumberProvider.Uniform u -> NumberProvider.uniform(u.min(), u.max() + 1);
            case NumberProvider.Binomial b -> NumberProvider.binomial(b.n() + 1, b.p());
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        int maxScroll = Math.max(0, contentHeight - height + 4);
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 10), 0, maxScroll);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Accessibility narration
    }
}
