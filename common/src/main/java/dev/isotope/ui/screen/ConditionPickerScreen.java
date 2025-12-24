package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootCondition;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeWindow;
import dev.isotope.ui.widget.ScrollableListWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for picking a loot condition to add to an entry.
 * Provides common condition templates.
 */
@Environment(EnvType.CLIENT)
public class ConditionPickerScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("Add Condition");

    private final Consumer<LootCondition> onSelect;
    private IsotopeWindow window;
    private ScrollableListWidget<ConditionTemplate> conditionList;

    @Nullable
    private ConditionTemplate selectedTemplate;

    public ConditionPickerScreen(Screen parent, Consumer<LootCondition> onSelect) {
        super(TITLE, parent);
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();

        int windowWidth = 300;
        int windowHeight = 220;
        int windowX = (width - windowWidth) / 2;
        int windowY = (height - windowHeight) / 2;

        window = new IsotopeWindow(windowX, windowY, windowWidth, windowHeight, TITLE);

        // Condition list
        int listX = windowX + 10;
        int listY = windowY + 35;
        int listWidth = windowWidth - 20;
        int listHeight = windowHeight - 90;

        conditionList = new ScrollableListWidget<>(
            listX, listY, listWidth, listHeight, 28,
            this::renderConditionTemplate,
            this::onTemplateSelected
        );
        conditionList.setItems(getTemplates());
        this.addRenderableWidget(conditionList);

        // Buttons
        int buttonY = windowY + windowHeight - 35;
        int buttonWidth = 80;

        this.addRenderableWidget(
            Button.builder(Component.literal("Add"), button -> addSelected())
                .pos(windowX + windowWidth / 2 - buttonWidth - 5, buttonY)
                .size(buttonWidth, 20)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), button -> onClose())
                .pos(windowX + windowWidth / 2 + 5, buttonY)
                .size(buttonWidth, 20)
                .build()
        );
    }

    private List<ConditionTemplate> getTemplates() {
        List<ConditionTemplate> templates = new ArrayList<>();

        templates.add(new ConditionTemplate(
            "Random Chance 10%",
            "10% chance to apply",
            () -> LootCondition.randomChance(0.1f)
        ));

        templates.add(new ConditionTemplate(
            "Random Chance 25%",
            "25% chance to apply",
            () -> LootCondition.randomChance(0.25f)
        ));

        templates.add(new ConditionTemplate(
            "Random Chance 50%",
            "50% chance to apply",
            () -> LootCondition.randomChance(0.5f)
        ));

        templates.add(new ConditionTemplate(
            "Random Chance 75%",
            "75% chance to apply",
            () -> LootCondition.randomChance(0.75f)
        ));

        templates.add(new ConditionTemplate(
            "Killed By Player",
            "Only when killed by player",
            LootCondition::killedByPlayer
        ));

        templates.add(new ConditionTemplate(
            "Survives Explosion",
            "Survives explosion damage",
            LootCondition::survivesExplosion
        ));

        return templates;
    }

    private void renderConditionTemplate(GuiGraphics graphics, ConditionTemplate template,
                                         int x, int y, int width, int height,
                                         boolean selected, boolean hovered) {
        var font = Minecraft.getInstance().font;

        // Name
        graphics.drawString(font, template.name, x + 4, y + 4,
            IsotopeColors.TEXT_PRIMARY, false);

        // Description
        graphics.drawString(font, template.description, x + 4, y + 15,
            IsotopeColors.TEXT_MUTED, false);
    }

    private void onTemplateSelected(ConditionTemplate template) {
        this.selectedTemplate = template;
    }

    private void addSelected() {
        if (selectedTemplate != null) {
            onSelect.accept(selectedTemplate.create());
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        window.render(graphics, mouseX, mouseY, partialTick);
    }

    private record ConditionTemplate(
        String name,
        String description,
        java.util.function.Supplier<LootCondition> factory
    ) {
        LootCondition create() {
            return factory.get();
        }
    }
}
