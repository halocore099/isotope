package dev.isotope.ui.screen;

import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.ui.HelpLinks;
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

import dev.isotope.Isotope;

import java.util.ArrayList;
import java.util.Comparator;
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
        // Sort by category, then by name for proper grouping
        templates.sort(Comparator.comparing(EntryTemplate::category).thenComparing(EntryTemplate::name));
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

        // Help button (top right of dialog)
        addRenderableWidget(Button.builder(Component.literal("?"), btn -> HelpLinks.open(HelpLinks.TEMPLATES))
            .pos(dialogX + DIALOG_WIDTH - 25, dialogY + 5)
            .size(20, 20)
            .build());
    }

    private void openManageTemplates() {
        minecraft.setScreen(new TemplateManagerScreen(this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // MUST call super.render() first for content to be visible in MC 1.21
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render background dim
        graphics.fill(0, 0, width, height, 0x90000000);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background with vanilla-style border
        // Outer highlight (top-left light)
        graphics.fill(dialogX - 3, dialogY - 3, dialogX + DIALOG_WIDTH + 3, dialogY + DIALOG_HEIGHT + 3, 0xFF000000);
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, 0xFF555555);
        graphics.fill(dialogX - 1, dialogY - 1, dialogX + DIALOG_WIDTH + 1, dialogY + DIALOG_HEIGHT + 1, 0xFF2d2d2d);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF1a1a1a);

        // Title
        graphics.drawCenteredString(font, "Select Entry Template", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Templates list area
        int listY = dialogY + 30;
        int listHeight = DIALOG_HEIGHT - 70;
        int listWidth = DIALOG_WIDTH - PADDING * 2;

        // Draw list background with inset border
        graphics.fill(dialogX + PADDING - 1, listY - 1, dialogX + PADDING + listWidth + 1, listY + listHeight + 1, 0xFF000000);
        graphics.fill(dialogX + PADDING, listY, dialogX + PADDING + listWidth, listY + listHeight, 0xFF202020);

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
                    // Category separator line
                    if (y > listY + 4) {
                        graphics.fill(dialogX + PADDING + 8, y - 2, dialogX + PADDING + listWidth - 8, y - 1, 0xFF333333);
                    }
                    // Category label
                    int catColor = isCustom ? 0xFFc9a656 : 0xFF8899aa;
                    graphics.drawString(font, "- " + currentCategory + " -", dialogX + PADDING + 8, y + 2, catColor, false);
                }
                y += 18;
            }

            // Template entry
            int entryX = dialogX + PADDING + 4;
            int entryWidth = listWidth - 8;
            int entryHeight = TEMPLATE_HEIGHT - 6;

            if (y > listY - TEMPLATE_HEIGHT && y < listY + listHeight) {
                // Hover detection must match visual bounds exactly
                boolean hovered = mouseX >= entryX && mouseX < entryX + entryWidth &&
                    mouseY >= y && mouseY < y + entryHeight;

                if (hovered) {
                    hoveredTemplate = i;
                    // Highlighted background
                    graphics.fill(entryX, y, entryX + entryWidth, y + entryHeight, 0xFF3d5c7a);
                    graphics.renderOutline(entryX, y, entryWidth, entryHeight, 0xFF5a8ab8);
                } else {
                    // Normal background
                    int bgColor = isCustom ? 0xFF2e2a24 : 0xFF2a2a2a;
                    graphics.fill(entryX, y, entryX + entryWidth, y + entryHeight, bgColor);
                    int outlineColor = isCustom ? 0xFF3d3a30 : 0xFF353535;
                    graphics.renderOutline(entryX, y, entryWidth, entryHeight, outlineColor);
                }

                int textX = entryX + 8;

                // Item icon (if has default item)
                if (template.defaultItem().isPresent()) {
                    var itemOpt = BuiltInRegistries.ITEM.get(template.defaultItem().get());
                    if (itemOpt.isPresent()) {
                        graphics.renderItem(new ItemStack(itemOpt.get().value()), entryX + 4, y + (entryHeight - 16) / 2);
                        textX = entryX + 26;
                    }
                }

                // Template name (white for better readability)
                graphics.drawString(font, template.name(), textX, y + 4, 0xFFFFFFFF, false);

                // Custom badge
                if (isCustom) {
                    int badgeX = textX + font.width(template.name()) + 6;
                    graphics.fill(badgeX, y + 3, badgeX + 44, y + 14, 0xFF4a3d20);
                    graphics.renderOutline(badgeX, y + 3, 44, 11, 0xFF6a5a30);
                    graphics.drawString(font, "CUSTOM", badgeX + 4, y + 5, 0xFFdaa520, false);
                }

                // Template description
                String desc = template.description();
                if (desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }
                graphics.drawString(font, desc, textX, y + 16, 0xFFaaaaaa, false);

                // Count info
                String countText = "Count: " + template.defaultCount().toString();
                graphics.drawString(font, countText, textX, y + 28, 0xFF888888, false);

                // Weight badge (right side)
                String weightText = "Weight: " + template.defaultWeight();
                int weightWidth = font.width(weightText) + 10;
                int wBadgeX = entryX + entryWidth - weightWidth - 6;
                graphics.fill(wBadgeX, y + 4, wBadgeX + weightWidth, y + 16, 0xFF383838);
                graphics.renderOutline(wBadgeX, y + 4, weightWidth, 12, 0xFF484848);
                graphics.drawString(font, weightText, wBadgeX + 5, y + 6, 0xFFcccccc, false);
            }

            y += TEMPLATE_HEIGHT;
        }

        graphics.disableScissor();

        // Scrollbar
        int contentHeight = templates.size() * TEMPLATE_HEIGHT + 18 * countCategories(templates);
        if (contentHeight > listHeight) {
            int scrollbarX = dialogX + DIALOG_WIDTH - PADDING - 6;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, (int)((float)listHeight / contentHeight * scrollbarHeight));
            int maxScroll = contentHeight - listHeight;
            int thumbY = listY + (int)((float)scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));

            // Scrollbar track
            graphics.fill(scrollbarX, listY, scrollbarX + 6, listY + listHeight, 0xFF1a1a1a);
            // Scrollbar thumb
            graphics.fill(scrollbarX + 1, thumbY + 1, scrollbarX + 5, thumbY + thumbHeight - 1, 0xFF606060);
        }

        // Help text (centered)
        String helpText = "Click a template to add it to the current pool";
        graphics.drawCenteredString(font, helpText, width / 2, dialogY + DIALOG_HEIGHT - 44, 0xFF707070);

        // Re-render buttons on top (super.render() rendered them first, but we drew over them)
        for (var widget : this.children()) {
            if (widget instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
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

            int contentHeight = templates.size() * TEMPLATE_HEIGHT + 18 * countCategories(templates);
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
