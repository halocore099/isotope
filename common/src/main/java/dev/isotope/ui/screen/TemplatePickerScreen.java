package dev.isotope.ui.screen;

import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.ui.HelpLinks;
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
import dev.isotope.util.Regs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for picking an entry template.
 * Shows a grid of available templates organized by category.
 */
@Environment(EnvType.CLIENT)
public class TemplatePickerScreen extends VersionedScreen {

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
        addRenderableWidget(Button.builder(Component.literal(UIConstants.LABEL_CANCEL), btn -> onClose())
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
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DIM);

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Dialog background with vanilla-style border
        // Outer highlight (top-left light)
        graphics.fill(dialogX - 3, dialogY - 3, dialogX + DIALOG_WIDTH + 3, dialogY + DIALOG_HEIGHT + 3, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + DIALOG_WIDTH + 2, dialogY + DIALOG_HEIGHT + 2, IsotopeColors.BUTTON_BACKGROUND);
        graphics.fill(dialogX - 1, dialogY - 1, dialogX + DIALOG_WIDTH + 1, dialogY + DIALOG_HEIGHT + 1, IsotopeColors.BACKGROUND_DARKEST);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Title
        graphics.drawCenteredString(font, "Select Entry Template", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Templates list area
        int listY = dialogY + 30;
        int listHeight = DIALOG_HEIGHT - 70;
        int listWidth = DIALOG_WIDTH - PADDING * 2;

        // Draw list background with inset border
        graphics.fill(dialogX + PADDING - 1, listY - 1, dialogX + PADDING + listWidth + 1, listY + listHeight + 1, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(dialogX + PADDING, listY, dialogX + PADDING + listWidth, listY + listHeight, IsotopeColors.BACKGROUND_SOLID);

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
                        graphics.fill(dialogX + PADDING + 8, y - 2, dialogX + PADDING + listWidth - 8, y - 1, IsotopeColors.BORDER_DEFAULT);
                    }
                    // Category label
                    int catColor = isCustom ? IsotopeColors.CATEGORY_CUSTOM : IsotopeColors.CATEGORY_BUILTIN;
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
                    graphics.fill(entryX, y, entryX + entryWidth, y + entryHeight, IsotopeColors.SYNTAX_BLUE_DARK);
                    ScreenUtils.renderOutline(graphics, entryX, y, entryWidth, entryHeight, IsotopeColors.SYNTAX_BLUE);
                } else {
                    // Normal background
                    int bgColor = isCustom ? IsotopeColors.CUSTOM_TEMPLATE_BG : IsotopeColors.ENTRY_BACKGROUND;
                    graphics.fill(entryX, y, entryX + entryWidth, y + entryHeight, bgColor);
                    int outlineColor = isCustom ? IsotopeColors.CUSTOM_TEMPLATE_BORDER : IsotopeColors.POOL_HEADER_HOVER;
                    ScreenUtils.renderOutline(graphics, entryX, y, entryWidth, entryHeight, outlineColor);
                }

                int textX = entryX + 8;

                // Item icon (if has default item)
                if (template.defaultItem().isPresent()) {
                    var itemOpt = Regs.getOptional(BuiltInRegistries.ITEM, Registries.ITEM, template.defaultItem().get());
                    if (itemOpt.isPresent()) {
                        graphics.renderItem(new ItemStack(itemOpt.get()), entryX + 4, y + (entryHeight - 16) / 2);
                        textX = entryX + 26;
                    }
                }

                // Template name (white for better readability)
                graphics.drawString(font, template.name(), textX, y + 4, IsotopeColors.TEXT_PRIMARY, false);

                // Custom badge
                if (isCustom) {
                    int badgeX = textX + font.width(template.name()) + 6;
                    graphics.fill(badgeX, y + 3, badgeX + 44, y + 14, IsotopeColors.CUSTOM_BADGE_BG);
                    ScreenUtils.renderOutline(graphics, badgeX, y + 3, 44, 11, IsotopeColors.CUSTOM_BADGE_BORDER);
                    graphics.drawString(font, "CUSTOM", badgeX + 4, y + 5, IsotopeColors.ACCENT_GOLD, false);
                }

                // Template description
                String desc = template.description();
                if (desc.length() > 50) {
                    desc = desc.substring(0, 47) + "...";
                }
                graphics.drawString(font, desc, textX, y + 16, IsotopeColors.TEXT_SECONDARY, false);

                // Count info
                String countText = "Count: " + template.defaultCount().toString();
                graphics.drawString(font, countText, textX, y + 28, IsotopeColors.SCROLLBAR_THUMB, false);

                // Weight badge (right side)
                String weightText = "Weight: " + template.defaultWeight();
                int weightWidth = font.width(weightText) + 10;
                int wBadgeX = entryX + entryWidth - weightWidth - 6;
                graphics.fill(wBadgeX, y + 4, wBadgeX + weightWidth, y + 16, IsotopeColors.BORDER_INNER);
                ScreenUtils.renderOutline(graphics, wBadgeX, y + 4, weightWidth, 12, IsotopeColors.WEIGHT_BADGE_OUTLINE);
                graphics.drawString(font, weightText, wBadgeX + 5, y + 6, IsotopeColors.TEXT_SECONDARY, false);
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
            graphics.fill(scrollbarX, listY, scrollbarX + 6, listY + listHeight, IsotopeColors.BACKGROUND_MEDIUM);
            // Scrollbar thumb
            graphics.fill(scrollbarX + 1, thumbY + 1, scrollbarX + 5, thumbY + thumbHeight - 1, IsotopeColors.BORDER_HIGHLIGHT);
        }

        // Help text (centered)
        String helpText = "Click a template to add it to the current pool";
        graphics.drawCenteredString(font, helpText, width / 2, dialogY + DIALOG_HEIGHT - 44, IsotopeColors.TEXT_MUTED);

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
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        double mouseX = event.x(); double mouseY = event.y(); int button = event.button();
        if (hoveredTemplate >= 0 && hoveredTemplate < templates.size()) {
            EntryTemplate template = templates.get(hoveredTemplate);
            onSelect.accept(template);
            onClose();
            return true;
        }
        return super.mouseClicked(event, focused);
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key(); int scanCode = event.scancode(); int modifiers = event.modifiers();
        if (keyCode == UIConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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
