package dev.isotope.ui.screen;

import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Confirmation modal screen with developer warning.
 * Displays before opening the main ISOTOPE interface.
 */
@Environment(EnvType.CLIENT)
public class ConfirmationScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("ISOTOPE - Developer Tool");

    private static final List<Component> WARNING_LINES = List.of(
        Component.literal("ISOTOPE will analyze structures and loot tables"),
        Component.literal("from the current world's registry data."),
        Component.literal(""),
        Component.literal("This tool is intended for modpack developers."),
        Component.literal(""),
        Component.literal("Features:"),
        Component.literal("  - Browse all structures by mod"),
        Component.literal("  - View loot table contents and probabilities"),
        Component.literal("  - Export analysis results as JSON"),
        Component.literal(""),
        Component.literal("No existing worlds will be modified.")
    );

    private final Consumer<Boolean> callback;

    // Modal dimensions
    private static final int MODAL_WIDTH = 350;
    private static final int MODAL_HEIGHT = 260;

    public ConfirmationScreen(@Nullable Screen parent, Consumer<Boolean> callback) {
        super(TITLE, parent);
        this.callback = callback;
    }

    @Override
    protected void init() {
        super.init();

        int modalX = (this.width - MODAL_WIDTH) / 2;
        int modalY = (this.height - MODAL_HEIGHT) / 2;

        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonY = modalY + MODAL_HEIGHT - 35;

        // Run Analysis button (primary)
        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Run Analysis"),
                button -> {
                    callback.accept(true);
                    onClose();
                }
            )
            .pos(modalX + MODAL_WIDTH / 2 - buttonWidth - 10, buttonY)
            .size(buttonWidth, buttonHeight)
            .style(IsotopeButton.ButtonStyle.PRIMARY)
            .build()
        );

        // Cancel button
        this.addRenderableWidget(
            IsotopeButton.isotopeBuilder(
                Component.literal("Cancel"),
                button -> {
                    callback.accept(false);
                    onClose();
                }
            )
            .pos(modalX + MODAL_WIDTH / 2 + 10, buttonY)
            .size(buttonWidth, buttonHeight)
            .style(IsotopeButton.ButtonStyle.DEFAULT)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render parent screen dimmed (if present)
        if (parent != null) {
            parent.render(graphics, -1, -1, partialTick);
        }

        // Dim overlay
        graphics.fill(0, 0, this.width, this.height, IsotopeColors.withAlpha(0x000000, 180));

        // Modal background
        int modalX = (this.width - MODAL_WIDTH) / 2;
        int modalY = (this.height - MODAL_HEIGHT) / 2;

        renderModal(graphics, modalX, modalY, MODAL_WIDTH, MODAL_HEIGHT);

        // Title
        graphics.drawCenteredString(
            this.font,
            this.title,
            this.width / 2,
            modalY + 15,
            IsotopeColors.ACCENT_CYAN
        );

        // Warning icon/banner
        int iconY = modalY + 35;
        graphics.fill(
            modalX + 20,
            iconY,
            modalX + MODAL_WIDTH - 20,
            iconY + 2,
            IsotopeColors.STATUS_WARNING
        );

        // Warning text
        int textY = modalY + 50;
        for (Component line : WARNING_LINES) {
            if (line.getString().isEmpty()) {
                textY += 6; // Smaller gap for empty lines
            } else {
                graphics.drawString(
                    this.font,
                    line,
                    modalX + 25,
                    textY,
                    IsotopeColors.TEXT_SECONDARY
                );
                textY += 12;
            }
        }

        // Render buttons (from super)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderModal(GuiGraphics graphics, int x, int y, int width, int height) {
        // Background
        graphics.fill(x, y, x + width, y + height, IsotopeColors.BACKGROUND_MEDIUM);

        // Border
        int borderColor = IsotopeColors.ACCENT_CYAN_DARK;

        // Top
        graphics.fill(x, y, x + width, y + 2, borderColor);
        // Bottom
        graphics.fill(x, y + height - 2, x + width, y + height, borderColor);
        // Left
        graphics.fill(x, y, x + 2, y + height, borderColor);
        // Right
        graphics.fill(x + width - 2, y, x + width, y + height, borderColor);
    }

    @Override
    protected void renderIsotopeBackground(GuiGraphics graphics) {
        // Don't render the standard background - we render the parent instead
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
