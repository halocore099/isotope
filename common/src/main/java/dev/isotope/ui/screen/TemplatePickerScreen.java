package dev.isotope.ui.screen;

import dev.isotope.data.EntryTemplate;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    public TemplatePickerScreen(@Nullable Screen parent, Consumer<EntryTemplate> onSelect) {
        super(Component.literal("Select Template"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
            .pos(dialogX + DIALOG_WIDTH - 80, dialogY + DIALOG_HEIGHT - 30)
            .size(70, 20)
            .build());
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

        List<EntryTemplate> templates = EntryTemplate.BUILTIN_TEMPLATES;
        hoveredTemplate = -1;

        String currentCategory = null;
        int y = listY - scrollOffset;

        for (int i = 0; i < templates.size(); i++) {
            EntryTemplate template = templates.get(i);

            // Category header
            if (!template.category().equals(currentCategory)) {
                currentCategory = template.category();
                if (y > listY - 20 && y < listY + listHeight) {
                    graphics.drawString(font, currentCategory, dialogX + PADDING, y + 4,
                        IsotopeColors.TEXT_SECONDARY, false);
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
                    graphics.fill(dialogX + PADDING, y, dialogX + PADDING + listWidth, y + TEMPLATE_HEIGHT - 4, 0xFF252525);
                }
                graphics.renderOutline(dialogX + PADDING, y, listWidth, TEMPLATE_HEIGHT - 4, 0xFF3a3a3a);

                // Template name
                graphics.drawString(font, template.name(), dialogX + PADDING + 10, y + 6,
                    IsotopeColors.TEXT_PRIMARY, false);

                // Template description
                graphics.drawString(font, template.description(), dialogX + PADDING + 10, y + 18,
                    IsotopeColors.TEXT_MUTED, false);

                // Default item (if any)
                if (template.defaultItem().isPresent()) {
                    String itemText = "Default: " + template.defaultItem().get().getPath();
                    graphics.drawString(font, itemText, dialogX + PADDING + 10, y + 30,
                        IsotopeColors.TEXT_MUTED, false);
                } else {
                    graphics.drawString(font, "Pick an item after selecting", dialogX + PADDING + 10, y + 30,
                        IsotopeColors.TEXT_MUTED, false);
                }

                // Weight badge
                String weightText = "W:" + template.defaultWeight();
                int weightWidth = font.width(weightText) + 8;
                int badgeX = dialogX + PADDING + listWidth - weightWidth - 10;
                graphics.fill(badgeX, y + 6, badgeX + weightWidth, y + 18, 0xFF3a3a3a);
                graphics.drawString(font, weightText, badgeX + 4, y + 8, IsotopeColors.TEXT_PRIMARY, false);
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
        if (hoveredTemplate >= 0 && hoveredTemplate < EntryTemplate.BUILTIN_TEMPLATES.size()) {
            EntryTemplate template = EntryTemplate.BUILTIN_TEMPLATES.get(hoveredTemplate);
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

            List<EntryTemplate> templates = EntryTemplate.BUILTIN_TEMPLATES;
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
