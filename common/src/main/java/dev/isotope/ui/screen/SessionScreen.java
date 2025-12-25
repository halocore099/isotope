package dev.isotope.ui.screen;

import dev.isotope.session.EditorSession;
import dev.isotope.session.SessionManager;
import dev.isotope.session.SessionManager.SessionInfo;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.TabManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Screen for managing editor sessions.
 *
 * Allows saving, loading, and deleting sessions.
 */
@Environment(EnvType.CLIENT)
public class SessionScreen extends Screen {

    private static final int DIALOG_WIDTH = 350;
    private static final int DIALOG_HEIGHT = 280;
    private static final int LIST_ITEM_HEIGHT = 24;
    private static final int PADDING = 10;

    private final Screen parent;
    private final TabManager tabManager;

    // UI state
    private List<SessionInfo> sessions;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    @Nullable
    private String selectedSession = null;

    // Widgets
    private EditBox nameField;
    private Button saveButton;
    private Button loadButton;
    private Button deleteButton;
    private Button closeButton;

    public SessionScreen(Screen parent, TabManager tabManager) {
        super(Component.literal("Sessions"));
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    protected void init() {
        super.init();

        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        // Name input field
        nameField = new EditBox(font, dialogX + PADDING, dialogY + 30, DIALOG_WIDTH - PADDING * 2 - 80, 20,
            Component.literal("Session name"));
        nameField.setHint(Component.literal("Enter session name..."));
        nameField.setMaxLength(50);
        addRenderableWidget(nameField);

        // Save button (next to name field)
        saveButton = Button.builder(Component.literal("Save"), this::onSave)
            .pos(dialogX + DIALOG_WIDTH - PADDING - 75, dialogY + 30)
            .size(65, 20)
            .build();
        addRenderableWidget(saveButton);

        // Bottom buttons
        int buttonWidth = 80;
        int buttonY = dialogY + DIALOG_HEIGHT - PADDING - 20;

        loadButton = Button.builder(Component.literal("Load"), this::onLoad)
            .pos(dialogX + PADDING, buttonY)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(loadButton);

        deleteButton = Button.builder(Component.literal("Delete"), this::onDelete)
            .pos(dialogX + PADDING + buttonWidth + 10, buttonY)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(deleteButton);

        closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .pos(dialogX + DIALOG_WIDTH - PADDING - buttonWidth, buttonY)
            .size(buttonWidth, 20)
            .build();
        addRenderableWidget(closeButton);

        // Load session list (after buttons are created)
        refreshSessionList();
    }

    private void refreshSessionList() {
        sessions = SessionManager.getInstance().listSessions();
        selectedSession = null;

        // Calculate max scroll
        int listHeight = DIALOG_HEIGHT - 120; // Space for header, buttons, name field
        int contentHeight = sessions.size() * LIST_ITEM_HEIGHT;
        maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedSession != null;
        loadButton.active = hasSelection;
        deleteButton.active = hasSelection;
        saveButton.active = nameField != null && !nameField.getValue().trim().isEmpty();
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
        graphics.drawCenteredString(font, "Sessions", width / 2, dialogY + 10, IsotopeColors.ACCENT_GOLD);

        // Separator line after name field
        int separatorY = dialogY + 55;
        graphics.fill(dialogX + PADDING, separatorY, dialogX + DIALOG_WIDTH - PADDING, separatorY + 1, 0xFF333333);

        // Session list
        int listX = dialogX + PADDING;
        int listY = separatorY + 5;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 125;

        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF252525);

        if (sessions.isEmpty()) {
            graphics.drawCenteredString(font, "No saved sessions", dialogX + DIALOG_WIDTH / 2,
                listY + listHeight / 2 - 4, IsotopeColors.TEXT_MUTED);
        } else {
            // Clip region for scrolling
            graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

            int renderY = listY - scrollOffset;
            for (SessionInfo info : sessions) {
                if (renderY + LIST_ITEM_HEIGHT > listY && renderY < listY + listHeight) {
                    boolean isSelected = info.name().equals(selectedSession);
                    boolean isHovered = mouseX >= listX && mouseX < listX + listWidth &&
                        mouseY >= renderY && mouseY < renderY + LIST_ITEM_HEIGHT;

                    // Background
                    if (isSelected) {
                        graphics.fill(listX, renderY, listX + listWidth, renderY + LIST_ITEM_HEIGHT, 0xFF3a5a8a);
                    } else if (isHovered) {
                        graphics.fill(listX, renderY, listX + listWidth, renderY + LIST_ITEM_HEIGHT, 0xFF353535);
                    }

                    // Session name
                    int textColor = isSelected ? 0xFFFFFFFF : IsotopeColors.TEXT_PRIMARY;
                    graphics.drawString(font, info.name(), listX + 4, renderY + 4, textColor, false);

                    // Stats: tabs and bookmarks
                    String stats = info.tabCount() + " tabs, " + info.bookmarkCount() + " bookmarks";
                    graphics.drawString(font, stats, listX + 4, renderY + 14, IsotopeColors.TEXT_MUTED, false);

                    // Date
                    int dateWidth = font.width(info.formattedDate());
                    graphics.drawString(font, info.formattedDate(), listX + listWidth - dateWidth - 4,
                        renderY + 8, IsotopeColors.TEXT_SECONDARY, false);
                }
                renderY += LIST_ITEM_HEIGHT;
            }

            graphics.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int scrollbarX = listX + listWidth - 4;
                int thumbHeight = Math.max(20, (int) ((float) listHeight / (listHeight + maxScroll) * listHeight));
                int thumbY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - thumbHeight));

                graphics.fill(scrollbarX, listY, scrollbarX + 3, listY + listHeight, 0xFF2a2a2a);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF555555);
            }
        }

        // Render widgets (buttons, text field)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int listX = dialogX + PADDING;
        int listY = dialogY + 60;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 125;

        // Check list clicks
        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int renderY = listY - scrollOffset;
            for (SessionInfo info : sessions) {
                if (mouseY >= renderY && mouseY < renderY + LIST_ITEM_HEIGHT) {
                    if (info.name().equals(selectedSession)) {
                        // Double-click to load
                        if (System.currentTimeMillis() - lastClickTime < 500) {
                            onLoad(loadButton);
                        }
                    }
                    selectedSession = info.name();
                    lastClickTime = System.currentTimeMillis();
                    updateButtonStates();
                    return true;
                }
                renderY += LIST_ITEM_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private long lastClickTime = 0;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dialogX = (width - DIALOG_WIDTH) / 2;
        int dialogY = (height - DIALOG_HEIGHT) / 2;

        int listX = dialogX + PADDING;
        int listY = dialogY + 60;
        int listWidth = DIALOG_WIDTH - PADDING * 2;
        int listHeight = DIALOG_HEIGHT - 125;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        boolean result = super.charTyped(chr, modifiers);
        updateButtonStates();
        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean result = super.keyPressed(keyCode, scanCode, modifiers);
        updateButtonStates();
        return result;
    }

    private void onSave(Button button) {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            IsotopeToast.error("Save Failed", "Please enter a session name");
            return;
        }

        try {
            SessionManager.getInstance().saveSession(name, tabManager);
            IsotopeToast.success("Session Saved", "Saved '" + name + "'");
            nameField.setValue("");
            refreshSessionList();
        } catch (Exception e) {
            IsotopeToast.error("Save Failed", e.getMessage());
        }
    }

    private void onLoad(Button button) {
        if (selectedSession == null) return;

        Optional<EditorSession> session = SessionManager.getInstance().loadSession(selectedSession);
        if (session.isPresent()) {
            SessionManager.getInstance().applySession(session.get(), tabManager);
            IsotopeToast.success("Session Loaded", "Loaded '" + selectedSession + "'");
            onClose();
        } else {
            IsotopeToast.error("Load Failed", "Could not load session");
        }
    }

    private void onDelete(Button button) {
        if (selectedSession == null) return;

        if (SessionManager.getInstance().deleteSession(selectedSession)) {
            IsotopeToast.info("Session Deleted", "Deleted '" + selectedSession + "'");
            refreshSessionList();
        } else {
            IsotopeToast.error("Delete Failed", "Could not delete session");
        }
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
