package dev.isotope.ui.widget;

import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vanilla-style tab bar using Minecraft's button textures.
 * Tabs render as button segments with proper 3D beveling.
 */
@Environment(EnvType.CLIENT)
public class VanillaTabBar extends AbstractWidget {

    private static final int TAB_HEIGHT = 20;
    private static final int TAB_PADDING = 10;
    private static final int TAB_SPACING = 2;

    private final List<Tab> tabs = new ArrayList<>();
    private int selectedIndex = 0;
    private Consumer<Integer> onTabChange;

    public VanillaTabBar(int x, int y, int width) {
        super(x, y, width, TAB_HEIGHT, Component.empty());
    }

    public VanillaTabBar addTab(String label) {
        tabs.add(new Tab(label));
        return this;
    }

    public VanillaTabBar onTabChange(Consumer<Integer> callback) {
        this.onTabChange = callback;
        return this;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < tabs.size()) {
            selectedIndex = index;
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int tabX = getX();

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tabWidth = mc.font.width(tab.label) + TAB_PADDING * 2;
            tabWidth = Math.max(tabWidth, 60); // Minimum width

            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth
                && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            renderVanillaTab(graphics, tabX, getY(), tabWidth, TAB_HEIGHT, tab.label, isSelected, isHovered);

            tab.x = tabX;
            tab.width = tabWidth;
            tabX += tabWidth + TAB_SPACING;
        }
    }

    private void renderVanillaTab(GuiGraphics graphics, int x, int y, int width, int height,
                                   String label, boolean selected, boolean hovered) {
        Minecraft mc = Minecraft.getInstance();

        // Vanilla-style button colors (matching Minecraft's button appearance)
        int bgColor;
        if (selected) {
            bgColor = 0xFF4A4A4A; // Pressed/active state
        } else if (hovered) {
            bgColor = 0xFF5A5A5A; // Hover state
        } else {
            bgColor = 0xFF3A3A3A; // Normal state
        }

        // Main button background
        graphics.fill(x, y, x + width, y + height, bgColor);

        // 3D border effect (vanilla button style)
        // Top highlight
        graphics.fill(x, y, x + width, y + 1, 0xFFAAAAAA);
        // Left highlight
        graphics.fill(x, y, x + 1, y + height, 0xFFAAAAAA);
        // Bottom shadow
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF2A2A2A);
        // Right shadow
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF2A2A2A);

        // Selected indicator (gold underline)
        if (selected) {
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height, IsotopeColors.ACCENT_GOLD);
        }

        // Text
        int textColor = selected ? 0xFFFFFF00 : (hovered ? 0xFFFFFFA0 : 0xFFE0E0E0);
        int textX = x + (width - mc.font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        graphics.drawString(mc.font, label, textX, textY, textColor, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                if (mouseX >= tab.x && mouseX < tab.x + tab.width) {
                    if (i != selectedIndex) {
                        selectedIndex = i;
                        if (onTabChange != null) {
                            onTabChange.accept(i);
                        }
                        playDownSound(Minecraft.getInstance().getSoundManager());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        if (selectedIndex >= 0 && selectedIndex < tabs.size()) {
            defaultButtonNarrationText(output);
        }
    }

    private static class Tab {
        final String label;
        int x;
        int width;

        Tab(String label) {
            this.label = label;
        }
    }
}
