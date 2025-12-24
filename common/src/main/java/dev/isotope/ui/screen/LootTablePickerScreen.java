package dev.isotope.ui.screen;

import dev.isotope.data.LootTableInfo;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.widget.IsotopeWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Picker dialog for selecting a loot table to link to a structure.
 */
@Environment(EnvType.CLIENT)
public class LootTablePickerScreen extends IsotopeScreen {

    private static final Component TITLE = Component.literal("Select Loot Table");
    private static final int WINDOW_WIDTH = 350;
    private static final int WINDOW_HEIGHT = 300;

    private final ResourceLocation structureId;
    private final Consumer<ResourceLocation> onSelect;

    private IsotopeWindow window;
    private EditBox searchBox;
    private LootTableList lootTableList;
    private Button selectButton;
    private Button cancelButton;

    @Nullable
    private LootTableInfo selectedTable = null;
    private String currentFilter = "";

    public LootTablePickerScreen(Screen parent, ResourceLocation structureId, Consumer<ResourceLocation> onSelect) {
        super(TITLE, parent);
        this.structureId = structureId;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();

        // Create centered window
        window = IsotopeWindow.centered(this, WINDOW_WIDTH, WINDOW_HEIGHT, TITLE);

        // Search box at top
        int searchX = window.getContentX() + 5;
        int searchY = window.getContentY() + 5;
        searchBox = new EditBox(
            this.font,
            searchX,
            searchY,
            window.getContentWidth() - 10,
            18,
            Component.literal("Search...")
        );
        searchBox.setHint(Component.literal("Type to filter..."));
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);

        // Loot table list
        int listX = window.getContentX() + 5;
        int listY = window.getContentY() + 30;
        int listWidth = window.getContentWidth() - 10;
        int listHeight = window.getContentHeight() - 70;

        lootTableList = new LootTableList(this.minecraft, listWidth, listHeight, listY, 24);
        lootTableList.setX(listX);
        this.addRenderableWidget(lootTableList);

        // Buttons at bottom
        int buttonY = window.getContentY() + window.getContentHeight() - 30;
        int buttonWidth = 80;

        selectButton = Button.builder(Component.literal("Select"), btn -> selectAndClose())
            .pos(window.getX() + window.getWidth() / 2 - buttonWidth - 5, buttonY)
            .size(buttonWidth, 20)
            .build();
        selectButton.active = false;
        this.addRenderableWidget(selectButton);

        cancelButton = Button.builder(Component.literal("Cancel"), btn -> onClose())
            .pos(window.getX() + window.getWidth() / 2 + 5, buttonY)
            .size(buttonWidth, 20)
            .build();
        this.addRenderableWidget(cancelButton);

        // Populate list
        refreshList();
    }

    private void onSearchChanged(String text) {
        currentFilter = text.toLowerCase();
        refreshList();
    }

    private void refreshList() {
        lootTableList.children().clear();

        // Get all loot tables filtered by search
        List<LootTableInfo> tables = LootTableRegistry.getInstance().getAll().stream()
            .filter(lt -> {
                if (currentFilter.isEmpty()) return true;
                return lt.fullId().toLowerCase().contains(currentFilter) ||
                       lt.path().toLowerCase().contains(currentFilter);
            })
            .sorted((a, b) -> a.path().compareToIgnoreCase(b.path()))
            .limit(100) // Limit for performance
            .collect(Collectors.toList());

        for (LootTableInfo table : tables) {
            lootTableList.children().add(new LootTableEntry(table));
        }
    }

    private void onTableSelected(@Nullable LootTableInfo table) {
        this.selectedTable = table;
        selectButton.active = table != null;
    }

    private void selectAndClose() {
        if (selectedTable != null) {
            onSelect.accept(selectedTable.id());
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        // Render window frame
        if (window != null) {
            window.render(graphics, mouseX, mouseY, partialTick);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Info text
        if (lootTableList.children().isEmpty()) {
            graphics.drawCenteredString(
                this.font,
                "No matching loot tables found",
                window.getX() + window.getWidth() / 2,
                window.getY() + window.getHeight() / 2,
                IsotopeColors.TEXT_MUTED
            );
        }
    }

    // --- List widget ---

    @Environment(EnvType.CLIENT)
    class LootTableList extends ObjectSelectionList<LootTableEntry> {

        public LootTableList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        @Override
        public void setSelected(@Nullable LootTableEntry entry) {
            super.setSelected(entry);
            LootTablePickerScreen.this.onTableSelected(entry != null ? entry.table : null);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), IsotopeColors.BACKGROUND_DARK);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Environment(EnvType.CLIENT)
    class LootTableEntry extends ObjectSelectionList.Entry<LootTableEntry> {
        private final LootTableInfo table;

        public LootTableEntry(LootTableInfo table) {
            this.table = table;
        }

        @Override
        public Component getNarration() {
            return Component.literal(table.fullId());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            int bgColor = hovering ? IsotopeColors.LIST_ITEM_HOVER : IsotopeColors.LIST_ITEM_BG;
            if (lootTableList.getSelected() == this) {
                bgColor = IsotopeColors.LIST_ITEM_SELECTED;
            }
            graphics.fill(left, top, left + width, top + height - 2, bgColor);

            // Path (main text)
            graphics.drawString(
                LootTablePickerScreen.this.font,
                table.path(),
                left + 5,
                top + 3,
                IsotopeColors.TEXT_PRIMARY,
                false
            );

            // Namespace and category
            String info = table.namespace() + " • " + table.category().name();
            graphics.drawString(
                LootTablePickerScreen.this.font,
                info,
                left + 5,
                top + 13,
                IsotopeColors.TEXT_MUTED,
                false
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                lootTableList.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
