package dev.isotope.ui.screen;

import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Screen for picking an entry template.
 * Shows a grid of available templates organized by category.
 */
@Environment(EnvType.CLIENT)
public class TemplatePickerScreen extends Screen {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 320;
    private static final int TEMPLATE_HEIGHT = 50;
    private static final int PADDING = 10;

    @Nullable
    private final Screen parent;
    private final Consumer<EntryTemplate> onSelect;

    private int scrollOffset = 0;
    private int hoveredTemplate = -1;
    private List<EntryTemplate> templates = new ArrayList<>();

    public TemplatePickerScreen(@Nullable Screen parent, Consumer<EntryTemplate> onSelect) {
        super(Component.literal("Select Template"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    private void refreshTemplates() {
        templates = TemplateManager.getInstance().getAllTemplates();
    }

    @Override
    protected void init() {
        super.init();

        refreshTemplates();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Manage Templates button
        addRenderableWidget(Button.builder(Component.literal("Manage..."), btn -> openManageTemplates())
            .pos(dialogX + PADDING, dialogY + DIALOG_HEIGHT - 30)
            .size(80, 20)
            .build());

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
            .pos(dialogX + DIALOG_WIDTH - 80, dialogY + DIALOG_HEIGHT - 30)
            .size(70, 20)
            .build());
    }

    private void openManageTemplates() {
        minecraft.setScreen(new TemplateManagerScreen(this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF333333);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, "Select Entry Template", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Templates list area
        int listY = dialogY + 30;
        int listHeight = DIALOG_HEIGHT - 70;
        int listWidth = DIALOG_WIDTH - PADDING * 2;

        // Scissor for scrolling
        graphics.enableScissor(dialogX + PADDING, listY, dialogX + PADDING + listWidth, listY + listHeight);

        hoveredTemplate = -1;
        TemplateManager manager = TemplateManager.getInstance();

        String currentCategory = null;
        int y = listY - scrollOffset;

        for (int i = 0; i < templates.size(); i++) {
            EntryTemplate template = templates.get(i);
            boolean isCustom = !manager.isBuiltIn(template);

            // Category header
            if (!template.category().equals(currentCategory)) {
                currentCategory = template.category();
                if (y > listY - 20 && y < listY + listHeight) {
                    int catColor = isCustom ? 0xFF8a6a4a : IsotopeColors.TEXT_SECONDARY;
                    graphics.drawString(font, currentCategory, dialogX + PADDING, y + 4, catColor, false);
                }
                y += 16;
            }

            // Template entry
            if (y > listY - TEMPLATE_HEIGHT && y < listY + listHeight) {
                boolean hovered = mouseX >= dialogX + PADDING && mouseX < dialogX + PADDING + listWidth &&
                    mouseY >= y && mouseY < y + TEMPLATE_HEIGHT - 4;

                if (hovered) {
                    hoveredTemplate = i;
                    graphics.fill(dialogX + PADDING, y, dialogX + PADDING + listWidth, y + TEMPLATE_HEIGHT - 4, 0xFF2a3a4a);
                } else {
                    int bgColor = isCustom ? 0xFF2a2a25 : 0xFF252525;
                    graphics.fill(dialogX + PADDING, y, dialogX + PADDING + listWidth, y + TEMPLATE_HEIGHT - 4, bgColor);
                }
                int outlineColor = isCustom ? 0xFF4a4a3a : 0xFF3a3a3a;
                graphics.renderOutline(dialogX + PADDING, y, listWidth, TEMPLATE_HEIGHT - 4, outlineColor);

                int textX = dialogX + PADDING + 10;

                // Item icon (if has default item)
                if (template.defaultItem().isPresent()) {
                    var itemOpt = BuiltInRegistries.ITEM.get(template.defaultItem().get());
                    if (itemOpt.isPresent()) {
                        graphics.renderItem(new ItemStack(itemOpt.get().value()), dialogX + PADDING + 6, y + (TEMPLATE_HEIGHT - 4 - 16) / 2);
                        textX = dialogX + PADDING + 28;
                    }
                }

                // Template name
                graphics.drawString(font, template.name(), textX, y + 6,
                    IsotopeColors.TEXT_PRIMARY, false);

                // Custom badge
                if (isCustom) {
                    int badgeX = textX + font.width(template.name()) + 6;
                    graphics.fill(badgeX, y + 5, badgeX + 42, y + 16, 0xFF5a4a2a);
                    graphics.drawString(font, "CUSTOM", badgeX + 3, y + 7, 0xFFc9a656, false);
                }

                // Template description
                String desc = template.description();
                if (desc.length() > 45) {
                    desc = desc.substring(0, 42) + "...";
                }
                graphics.drawString(font, desc, textX, y + 18, IsotopeColors.TEXT_MUTED, false);

                // Count info
                String countText = template.defaultCount().toString();
                graphics.drawString(font, "Count: " + countText, textX, y + 30, IsotopeColors.TEXT_MUTED, false);

                // Weight badge
                String weightText = "W:" + template.defaultWeight();
                int weightWidth = font.width(weightText) + 8;
                int wBadgeX = dialogX + PADDING + listWidth - weightWidth - 10;
                graphics.fill(wBadgeX, y + 6, wBadgeX + weightWidth, y + 18, 0xFF3a3a3a);
                graphics.drawString(font, weightText, wBadgeX + 4, y + 8, IsotopeColors.TEXT_PRIMARY, false);
            }

            y += TEMPLATE_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar
        int contentHeight = templates.size() * TEMPLATE_HEIGHT + 16 * countCategories(templates);
        if (contentHeight > listHeight) {
            int scrollbarX = dialogX + DIALOG_WIDTH - PADDING - 4;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, (int)((float)listHeight / contentHeight * scrollbarHeight));
            int maxScroll = contentHeight - listHeight;
            int thumbY = listY + (int)((float)scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + listHeight, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }

        // Help text
        graphics.drawString(font, "Click a template to add it to the current pool", dialogX + PADDING,
            dialogY + DIALOG_HEIGHT - 25, IsotopeColors.TEXT_MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int countCategories(List<EntryTemplate> templates) {
        return (int) templates.stream()
            .map(EntryTemplate::category)
            .distinct()
            .count();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredTemplate >= 0 && hoveredTemplate < templates.size()) {
            EntryTemplate template = templates.get(hoveredTemplate);
            onSelect.accept(template);
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;
        int listY = dialogY + 30;
        int listHeight = DIALOG_HEIGHT - 70;

        if (mouseX >= dialogX && mouseX < dialogX + DIALOG_WIDTH &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int contentHeight = templates.size() * TEMPLATE_HEIGHT + 16 * countCategories(templates);
            int maxScroll = Math.max(0, contentHeight - listHeight);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));
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
