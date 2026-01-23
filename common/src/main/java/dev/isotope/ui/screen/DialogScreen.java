package dev.isotope.ui.screen;

import dev.isotope.compat.ui.VersionedScreen;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for modal dialog screens in ISOTOPE.
 *
 * Provides common dialog functionality:
 * - Centered dialog box
 * - Background dimming
 * - Border rendering
 * - Title rendering
 * - Parent screen navigation
 * - Non-pause behavior
 *
 * Subclasses should:
 * - Override {@link #getDialogWidth()} and {@link #getDialogHeight()} for custom sizes
 * - Override {@link #initDialog(int, int)} to add widgets (dialogX, dialogY provided)
 * - Override {@link #renderDialogContent(GuiGraphics, int, int, int, int, float)} to render custom content
 */
@Environment(EnvType.CLIENT)
public abstract class DialogScreen extends VersionedScreen {

    /** Default dialog width. */
    protected static final int DEFAULT_WIDTH = 320;

    /** Default dialog height. */
    protected static final int DEFAULT_HEIGHT = 200;

    @Nullable
    protected final Screen parent;
    protected final String dialogTitle;

    /**
     * Create a dialog screen.
     *
     * @param parent Parent screen to return to on close
     * @param title  Dialog title
     */
    protected DialogScreen(@Nullable Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
        this.dialogTitle = title;
    }

    /**
     * Get the dialog width. Override to customize.
     */
    protected int getDialogWidth() {
        return DEFAULT_WIDTH;
    }

    /**
     * Get the dialog height. Override to customize.
     */
    protected int getDialogHeight() {
        return DEFAULT_HEIGHT;
    }

    /**
     * Get the dialog X position (centered by default).
     */
    protected int getDialogX() {
        return (width - getDialogWidth()) / 2;
    }

    /**
     * Get the dialog Y position (centered by default).
     */
    protected int getDialogY() {
        return (height - getDialogHeight()) / 2;
    }

    /**
     * Get the title color. Override to customize.
     */
    protected int getTitleColor() {
        return IsotopeColors.ACCENT_GOLD;
    }

    /**
     * Get the background color. Override to customize.
     */
    protected int getBackgroundColor() {
        return IsotopeColors.BACKGROUND_MEDIUM;
    }

    /**
     * Get the border color. Override to customize.
     */
    protected int getBorderColor() {
        return IsotopeColors.BORDER_DEFAULT;
    }

    /**
     * Get the overlay/dimmer color. Override to customize.
     */
    protected int getOverlayColor() {
        return IsotopeColors.OVERLAY_DARK;
    }

    @Override
    protected void init() {
        super.init();
        initDialog(getDialogX(), getDialogY());
    }

    /**
     * Initialize dialog content. Called with the dialog's top-left position.
     *
     * @param dialogX Dialog X position
     * @param dialogY Dialog Y position
     */
    protected void initDialog(int dialogX, int dialogY) {
        // Subclasses override to add widgets
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, width, height, getOverlayColor());

        int dialogX = getDialogX();
        int dialogY = getDialogY();
        int dialogWidth = getDialogWidth();
        int dialogHeight = getDialogHeight();

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, getBackgroundColor());

        // Border
        ScreenUtils.renderOutline(graphics, dialogX, dialogY, dialogWidth, dialogHeight, getBorderColor());

        // Title
        graphics.drawCenteredString(font, dialogTitle, dialogX + dialogWidth / 2, dialogY + 8, getTitleColor());

        // Render custom content
        renderDialogContent(graphics, dialogX, dialogY, mouseX, mouseY, partialTick);

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Render custom dialog content. Called after background but before widgets.
     *
     * @param graphics   Graphics context
     * @param dialogX    Dialog X position
     * @param dialogY    Dialog Y position
     * @param mouseX     Mouse X
     * @param mouseY     Mouse Y
     * @param partialTick Partial tick
     */
    protected void renderDialogContent(GuiGraphics graphics, int dialogX, int dialogY, int mouseX, int mouseY, float partialTick) {
        // Subclasses override to render custom content
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
