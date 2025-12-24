package dev.isotope.ui.screen;

import dev.isotope.data.loot.LootFunction;
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
 * Screen for picking a loot function to add to an entry.
 * Provides common function templates.
 */
@Environment(EnvType.CLIENT)
public class FunctionPickerScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("Add Function");

    private final Consumer<LootFunction> onSelect;
    private IsotopeWindow window;
    private ScrollableListWidget<FunctionTemplate> functionList;

    @Nullable
    private FunctionTemplate selectedTemplate;

    public FunctionPickerScreen(Screen parent, Consumer<LootFunction> onSelect) {
        super(TITLE, parent);
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();

        int windowWidth = 300;
        int windowHeight = 250;
        int windowX = (width - windowWidth) / 2;
        int windowY = (height - windowHeight) / 2;

        window = new IsotopeWindow(windowX, windowY, windowWidth, windowHeight, TITLE);

        // Function list
        int listX = windowX + 10;
        int listY = windowY + 35;
        int listWidth = windowWidth - 20;
        int listHeight = windowHeight - 90;

        functionList = new ScrollableListWidget<>(
            listX, listY, listWidth, listHeight, 28,
            this::renderFunctionTemplate,
            this::onTemplateSelected
        );
        functionList.setItems(getTemplates());
        this.addRenderableWidget(functionList);

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

    private List<FunctionTemplate> getTemplates() {
        List<FunctionTemplate> templates = new ArrayList<>();

        templates.add(new FunctionTemplate(
            "Set Count (1-3)",
            "Set item count to 1-3",
            () -> LootFunction.setCount(1, 3)
        ));

        templates.add(new FunctionTemplate(
            "Set Count (2-5)",
            "Set item count to 2-5",
            () -> LootFunction.setCount(2, 5)
        ));

        templates.add(new FunctionTemplate(
            "Set Count (4-8)",
            "Set item count to 4-8",
            () -> LootFunction.setCount(4, 8)
        ));

        templates.add(new FunctionTemplate(
            "Enchant Randomly",
            "Apply random enchantment",
            LootFunction::enchantRandomly
        ));

        templates.add(new FunctionTemplate(
            "Enchant (Lvl 5-15)",
            "Enchant with levels 5-15",
            () -> LootFunction.enchantWithLevels(5, 15, false)
        ));

        templates.add(new FunctionTemplate(
            "Enchant (Lvl 15-30)",
            "Enchant with levels 15-30",
            () -> LootFunction.enchantWithLevels(15, 30, false)
        ));

        templates.add(new FunctionTemplate(
            "Enchant Treasure",
            "Enchant with treasure enchants",
            () -> LootFunction.enchantWithLevels(20, 39, true)
        ));

        templates.add(new FunctionTemplate(
            "Set Damage (50-100%)",
            "Set durability 50-100%",
            () -> LootFunction.setDamage(0.5f, 1.0f)
        ));

        templates.add(new FunctionTemplate(
            "Set Damage (25-75%)",
            "Set durability 25-75%",
            () -> LootFunction.setDamage(0.25f, 0.75f)
        ));

        return templates;
    }

    private void renderFunctionTemplate(GuiGraphics graphics, FunctionTemplate template,
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

    private void onTemplateSelected(FunctionTemplate template) {
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

    private record FunctionTemplate(
        String name,
        String description,
        java.util.function.Supplier<LootFunction> factory
    ) {
        LootFunction create() {
            return factory.get();
        }
    }
}
