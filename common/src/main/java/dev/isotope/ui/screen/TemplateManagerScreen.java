package dev.isotope.ui.screen;

import dev.isotope.Isotope;
import dev.isotope.data.EntryTemplate;
import dev.isotope.data.TemplateManager;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for managing custom templates (list, edit, delete).
 */
@Environment(EnvType.CLIENT)
public class TemplateManagerScreen extends Screen {

    private static final int PANEL_WIDTH = 450;
    private static final int PANEL_HEIGHT = 350;
    private static final int TEMPLATE_HEIGHT = 50;

    private final Screen parent;

    private List<EntryTemplate> templates = new ArrayList<>();
    private int scrollOffset = 0;
    private int hoveredTemplateIdx = -1;
    private int selectedTemplateIdx = -1;

    // Layout
    private int panelX, panelY;
    private int listX, listY, listWidth, listHeight;

    // Confirmation state
    private boolean confirmingDelete = false;
    private int deleteTargetIdx = -1;

    public TemplateManagerScreen(Screen parent) {
        super(Component.literal("Manage Templates"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init(); // Required for proper screen initialization

        Isotope.LOGGER.info("TemplateManagerScreen: init() called");

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        listX = panelX + 10;
        listY = panelY + 35;
        listWidth = PANEL_WIDTH - 20;
        listHeight = PANEL_HEIGHT - 100;

        refreshTemplates();
        Isotope.LOGGER.info("TemplateManagerScreen: {} custom templates loaded", templates.size());

        // Buttons at bottom
        int buttonY = panelY + PANEL_HEIGHT - 35;
        int buttonWidth = 90;
        int spacing = 10;

        addRenderableWidget(Button.builder(Component.literal("New Template"), btn -> openNewTemplate())
            .bounds(panelX + 10, buttonY, buttonWidth + 20, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(panelX + PANEL_WIDTH - buttonWidth - 10, buttonY, buttonWidth, 20)
            .build());
    }

    private void refreshTemplates() {
        templates = TemplateManager.getInstance().getCustomTemplates();
        selectedTemplateIdx = -1;
        scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, IsotopeColors.OVERLAY_DARK);

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, IsotopeColors.BORDER_DEFAULT);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Title
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 10, IsotopeColors.ACCENT_GOLD);

        // Template count
        String countText = templates.size() + " custom template" + (templates.size() != 1 ? "s" : "");
        graphics.drawString(font, countText, panelX + 10, panelY + 24, IsotopeColors.TEXT_MUTED, false);

        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, IsotopeColors.POOL_HEADER_BACKGROUND);
        ScreenUtils.renderOutline(graphics, listX, listY, listWidth, listHeight, IsotopeColors.ENTRY_BACKGROUND_HOVER);

        // Template list
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        hoveredTemplateIdx = -1;

        if (templates.isEmpty()) {
            graphics.drawCenteredString(font, "No custom templates yet",
                listX + listWidth / 2, listY + listHeight / 2 - 4, IsotopeColors.TEXT_MUTED);
            graphics.drawCenteredString(font, "Click 'New Template' to create one",
                listX + listWidth / 2, listY + listHeight / 2 + 8, IsotopeColors.TEXT_MUTED);
        } else {
            int y = listY + 4 - scrollOffset;
            for (int i = 0; i < templates.size(); i++) {
                EntryTemplate template = templates.get(i);

                if (y + TEMPLATE_HEIGHT > listY && y < listY + listHeight) {
                    boolean hovered = mouseX >= listX && mouseX < listX + listWidth - 8 &&
                        mouseY >= y && mouseY < y + TEMPLATE_HEIGHT - 4 &&
                        mouseY >= listY && mouseY < listY + listHeight;
                    boolean selected = i == selectedTemplateIdx;

                    if (hovered) {
                        hoveredTemplateIdx = i;
                    }

                    // Background
                    int bgColor = selected ? IsotopeColors.SINGLE_SELECT_BACKGROUND : (hovered ? IsotopeColors.ENTRY_BACKGROUND_HOVER : IsotopeColors.ENTRY_BACKGROUND);
                    graphics.fill(listX + 2, y, listX + listWidth - 10, y + TEMPLATE_HEIGHT - 4, bgColor);
                    ScreenUtils.renderOutline(graphics, listX + 2, y, listWidth - 12, TEMPLATE_HEIGHT - 4,
                        selected ? IsotopeColors.SELECTED_BORDER : IsotopeColors.BORDER_DEFAULT);

                    // Item icon (if has default item)
                    int iconX = listX + 8;
                    if (template.defaultItem().isPresent()) {
                        var itemOpt = BuiltInRegistries.ITEM.get(template.defaultItem().get());
                        if (itemOpt.isPresent()) {
                            graphics.renderItem(new ItemStack(itemOpt.get().value()), iconX, y + (TEMPLATE_HEIGHT - 4 - 16) / 2);
                        }
                    }

                    // Template info
                    int textX = template.defaultItem().isPresent() ? iconX + 22 : iconX + 4;

                    // Name
                    graphics.drawString(font, template.name(), textX, y + 6, IsotopeColors.TEXT_PRIMARY, false);

                    // Description
                    String desc = template.description();
                    if (desc.length() > 50) {
                        desc = desc.substring(0, 47) + "...";
                    }
                    graphics.drawString(font, desc, textX, y + 18, IsotopeColors.TEXT_MUTED, false);

                    // Stats
                    String stats = "W:" + template.defaultWeight() + " | " + template.defaultCount().toString();
                    if (!template.functions().isEmpty()) {
                        stats += " | " + template.functions().size() + " func";
                    }
                    graphics.drawString(font, stats, textX, y + 30, IsotopeColors.TEXT_SECONDARY, false);

                    // Category badge
                    String category = template.category();
                    int catWidth = font.width(category) + 8;
                    int catX = listX + listWidth - 12 - catWidth - 50;
                    graphics.fill(catX, y + 6, catX + catWidth, y + 18, IsotopeColors.ENTRY_BACKGROUND_HOVER);
                    graphics.drawString(font, category, catX + 4, y + 8, IsotopeColors.TEXT_SECONDARY, false);

                    // Action buttons
                    int btnY = y + (TEMPLATE_HEIGHT - 4 - 14) / 2;
                    int editX = listX + listWidth - 12 - 40;
                    int deleteX = editX + 22;

                    // Edit button
                    boolean editHovered = mouseX >= editX && mouseX < editX + 18 &&
                        mouseY >= btnY && mouseY < btnY + 14 &&
                        mouseY >= listY && mouseY < listY + listHeight;
                    graphics.fill(editX, btnY, editX + 18, btnY + 14,
                        editHovered ? IsotopeColors.EDIT_BUTTON_HOVER : IsotopeColors.SINGLE_SELECT_BACKGROUND);
                    graphics.drawCenteredString(font, "E", editX + 9, btnY + 3,
                        editHovered ? IsotopeColors.TEXT_PRIMARY : IsotopeColors.TEXT_PRIMARY);

                    // Delete button
                    boolean deleteHovered = mouseX >= deleteX && mouseX < deleteX + 18 &&
                        mouseY >= btnY && mouseY < btnY + 14 &&
                        mouseY >= listY && mouseY < listY + listHeight;
                    graphics.fill(deleteX, btnY, deleteX + 18, btnY + 14,
                        deleteHovered ? IsotopeColors.DELETE_BUTTON_HOVER : IsotopeColors.DELETE_BUTTON);
                    graphics.drawCenteredString(font, "X", deleteX + 9, btnY + 3,
                        deleteHovered ? IsotopeColors.DESTRUCTIVE_TEXT : IsotopeColors.TEXT_PRIMARY);
                }

                y += TEMPLATE_HEIGHT;
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (!templates.isEmpty()) {
            int contentHeight = templates.size() * TEMPLATE_HEIGHT + 8;
            if (contentHeight > listHeight) {
                int scrollbarX = listX + listWidth - 6;
                int thumbHeight = Math.max(20, (int)((float)listHeight / contentHeight * listHeight));
                int maxScroll = contentHeight - listHeight;
                int thumbY = listY + (int)((float)scrollOffset / maxScroll * (listHeight - thumbHeight));

                graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + listHeight, IsotopeColors.ENTRY_BACKGROUND);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, IsotopeColors.BUTTON_BACKGROUND);
            }
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Delete confirmation overlay
        if (confirmingDelete && deleteTargetIdx >= 0 && deleteTargetIdx < templates.size()) {
            graphics.fill(0, 0, width, height, 0xC0000000);

            int dialogWidth = 280;
            int dialogHeight = 100;
            int dialogX = (width - dialogWidth) / 2;
            int dialogY = (height - dialogHeight) / 2;

            graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, IsotopeColors.BORDER_HIGHLIGHT);
            graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, IsotopeColors.ENTRY_BACKGROUND);

            EntryTemplate target = templates.get(deleteTargetIdx);
            graphics.drawCenteredString(font, "Delete Template?", dialogX + dialogWidth / 2, dialogY + 15, IsotopeColors.DESTRUCTIVE_TEXT);
            graphics.drawCenteredString(font, "\"" + target.name() + "\"", dialogX + dialogWidth / 2, dialogY + 35, IsotopeColors.TEXT_PRIMARY);
            graphics.drawCenteredString(font, "This cannot be undone.", dialogX + dialogWidth / 2, dialogY + 50, IsotopeColors.TEXT_MUTED);

            // Confirm/Cancel buttons
            int confirmX = dialogX + dialogWidth / 2 - 75;
            int cancelX = dialogX + dialogWidth / 2 + 5;
            int btnY = dialogY + dialogHeight - 30;

            boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + 70 &&
                mouseY >= btnY && mouseY < btnY + 20;
            boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + 70 &&
                mouseY >= btnY && mouseY < btnY + 20;

            graphics.fill(confirmX, btnY, confirmX + 70, btnY + 20,
                confirmHovered ? IsotopeColors.CONFIRM_DELETE_HOVER : IsotopeColors.CONFIRM_DELETE);
            graphics.drawCenteredString(font, "Delete", confirmX + 35, btnY + 6, IsotopeColors.DESTRUCTIVE_TEXT);

            graphics.fill(cancelX, btnY, cancelX + 70, btnY + 20,
                cancelHovered ? IsotopeColors.TEXT_DISABLED : IsotopeColors.ENTRY_BACKGROUND_HOVER);
            graphics.drawCenteredString(font, "Cancel", cancelX + 35, btnY + 6, IsotopeColors.TEXT_PRIMARY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle delete confirmation dialog
        if (confirmingDelete) {
            int dialogWidth = 280;
            int dialogHeight = 100;
            int dialogX = (width - dialogWidth) / 2;
            int dialogY = (height - dialogHeight) / 2;

            int confirmX = dialogX + dialogWidth / 2 - 75;
            int cancelX = dialogX + dialogWidth / 2 + 5;
            int btnY = dialogY + dialogHeight - 30;

            if (ScreenUtils.isMouseOver(mouseX, mouseY, confirmX, btnY, 70, 20)) {
                // Confirm delete
                if (deleteTargetIdx >= 0 && deleteTargetIdx < templates.size()) {
                    EntryTemplate target = templates.get(deleteTargetIdx);
                    TemplateManager.getInstance().delete(target.id());
                    IsotopeToast.success("Deleted", "Template '" + target.name() + "' deleted");
                    refreshTemplates();
                }
                confirmingDelete = false;
                deleteTargetIdx = -1;
                return true;
            }

            if (ScreenUtils.isMouseOver(mouseX, mouseY, cancelX, btnY, 70, 20)) {
                confirmingDelete = false;
                deleteTargetIdx = -1;
                return true;
            }

            // Click anywhere else cancels
            confirmingDelete = false;
            deleteTargetIdx = -1;
            return true;
        }

        // Template list interactions
        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int y = listY + 4 - scrollOffset;
            for (int i = 0; i < templates.size(); i++) {
                if (y + TEMPLATE_HEIGHT > listY && y < listY + listHeight) {
                    if (mouseY >= y && mouseY < y + TEMPLATE_HEIGHT - 4) {
                        int btnY = y + (TEMPLATE_HEIGHT - 4 - 14) / 2;
                        int editX = listX + listWidth - 12 - 40;
                        int deleteX = editX + 22;

                        // Edit button
                        if (ScreenUtils.isMouseOver(mouseX, mouseY, editX, btnY, 18, 14)) {
                            openEditTemplate(i);
                            return true;
                        }

                        // Delete button
                        if (ScreenUtils.isMouseOver(mouseX, mouseY, deleteX, btnY, 18, 14)) {
                            confirmingDelete = true;
                            deleteTargetIdx = i;
                            return true;
                        }

                        // Select template
                        selectedTemplateIdx = i;
                        return true;
                    }
                }
                y += TEMPLATE_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirmingDelete) return true;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int contentHeight = templates.size() * TEMPLATE_HEIGHT + 8;
            int maxScroll = Math.max(0, contentHeight - listHeight);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 30)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == UIConstants.KEY_ESCAPE) {
            if (confirmingDelete) {
                confirmingDelete = false;
                deleteTargetIdx = -1;
                return true;
            }
            onClose();
            return true;
        }

        // Delete key
        if (keyCode == UIConstants.KEY_DELETE && selectedTemplateIdx >= 0) {
            confirmingDelete = true;
            deleteTargetIdx = selectedTemplateIdx;
            return true;
        }

        // Enter to edit
        if (keyCode == UIConstants.KEY_ENTER && selectedTemplateIdx >= 0) {
            openEditTemplate(selectedTemplateIdx);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openNewTemplate() {
        minecraft.setScreen(new TemplateEditorScreen(this, this::refreshTemplates));
    }

    private void openEditTemplate(int idx) {
        if (idx >= 0 && idx < templates.size()) {
            EntryTemplate template = templates.get(idx);
            minecraft.setScreen(new TemplateEditorScreen(this, template, this::refreshTemplates));
        }
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
