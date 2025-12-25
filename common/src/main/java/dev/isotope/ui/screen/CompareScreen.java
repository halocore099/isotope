package dev.isotope.ui.screen;

import dev.isotope.analysis.DropRateCalculator;
import dev.isotope.analysis.DropRateCalculator.DropRate;
import dev.isotope.data.loot.LootEntry;
import dev.isotope.data.loot.LootPool;
import dev.isotope.data.loot.LootTableStructure;
import dev.isotope.editing.LootEditManager;
import dev.isotope.registry.LootTableRegistry;
import dev.isotope.ui.IsotopeColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Side-by-side comparison of two loot tables.
 */
@Environment(EnvType.CLIENT)
public class CompareScreen extends Screen {

    private static final int HEADER_HEIGHT = 60;
    private static final int POOL_HEADER_HEIGHT = 20;
    private static final int ENTRY_HEIGHT = 18;
    private static final int PADDING = 10;

    private final Screen parent;

    // Selected tables
    @Nullable
    private ResourceLocation leftTableId;
    @Nullable
    private ResourceLocation rightTableId;
    @Nullable
    private LootTableStructure leftTable;
    @Nullable
    private LootTableStructure rightTable;

    // UI state
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean syncScroll = true;

    // Table selection
    private EditBox leftSearchBox;
    private EditBox rightSearchBox;
    private boolean leftDropdownOpen = false;
    private boolean rightDropdownOpen = false;
    private List<ResourceLocation> filteredTablesLeft = new ArrayList<>();
    private List<ResourceLocation> filteredTablesRight = new ArrayList<>();

    // Widgets
    private Button syncButton;
    private Button swapButton;
    private Button closeButton;

    public CompareScreen(Screen parent) {
        super(Component.literal("Compare Loot Tables"));
        this.parent = parent;
    }

    public CompareScreen(Screen parent, ResourceLocation leftTable, ResourceLocation rightTable) {
        this(parent);
        this.leftTableId = leftTable;
        this.rightTableId = rightTable;
    }

    @Override
    protected void init() {
        super.init();

        int halfWidth = width / 2;

        // Left search box
        leftSearchBox = new EditBox(font, PADDING, 30, halfWidth - PADDING * 2, 18,
            Component.literal("Left table"));
        leftSearchBox.setHint(Component.literal("Search left table..."));
        leftSearchBox.setResponder(this::onLeftSearchChanged);
        if (leftTableId != null) {
            leftSearchBox.setValue(leftTableId.toString());
        }
        addRenderableWidget(leftSearchBox);

        // Right search box
        rightSearchBox = new EditBox(font, halfWidth + PADDING, 30, halfWidth - PADDING * 2, 18,
            Component.literal("Right table"));
        rightSearchBox.setHint(Component.literal("Search right table..."));
        rightSearchBox.setResponder(this::onRightSearchChanged);
        if (rightTableId != null) {
            rightSearchBox.setValue(rightTableId.toString());
        }
        addRenderableWidget(rightSearchBox);

        // Sync scroll toggle
        syncButton = Button.builder(Component.literal(syncScroll ? "Sync ✓" : "Sync"), this::onToggleSync)
            .pos(halfWidth - 30, 5)
            .size(60, 16)
            .build();
        addRenderableWidget(syncButton);

        // Swap button
        swapButton = Button.builder(Component.literal("⇄"), this::onSwap)
            .pos(halfWidth - 10, 32)
            .size(20, 16)
            .build();
        addRenderableWidget(swapButton);

        // Close button
        closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .pos(width - 70, 5)
            .size(60, 16)
            .build();
        addRenderableWidget(closeButton);

        // Load tables if IDs are set
        loadTables();
        calculateMaxScroll();
    }

    private void onLeftSearchChanged(String text) {
        filterTables(text, true);
        leftDropdownOpen = !text.isEmpty() && !filteredTablesLeft.isEmpty();

        // Check for exact match
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id != null && LootTableRegistry.getInstance().get(id).isPresent()) {
            leftTableId = id;
            loadTables();
            leftDropdownOpen = false;
        }
    }

    private void onRightSearchChanged(String text) {
        filterTables(text, false);
        rightDropdownOpen = !text.isEmpty() && !filteredTablesRight.isEmpty();

        // Check for exact match
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id != null && LootTableRegistry.getInstance().get(id).isPresent()) {
            rightTableId = id;
            loadTables();
            rightDropdownOpen = false;
        }
    }

    private void filterTables(String query, boolean isLeft) {
        List<ResourceLocation> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (var info : LootTableRegistry.getInstance().getAll()) {
            if (info.id().toString().toLowerCase().contains(lowerQuery)) {
                results.add(info.id());
                if (results.size() >= 10) break;
            }
        }

        if (isLeft) {
            filteredTablesLeft = results;
        } else {
            filteredTablesRight = results;
        }
    }

    private void loadTables() {
        LootEditManager manager = LootEditManager.getInstance();

        if (leftTableId != null) {
            leftTable = manager.getEditedStructure(leftTableId)
                .or(() -> manager.getCachedOriginalStructure(leftTableId))
                .orElse(null);
        } else {
            leftTable = null;
        }

        if (rightTableId != null) {
            rightTable = manager.getEditedStructure(rightTableId)
                .or(() -> manager.getCachedOriginalStructure(rightTableId))
                .orElse(null);
        } else {
            rightTable = null;
        }

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int leftHeight = calculateTableHeight(leftTable);
        int rightHeight = calculateTableHeight(rightTable);
        int contentHeight = Math.max(leftHeight, rightHeight);
        int viewHeight = height - HEADER_HEIGHT - PADDING;
        maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int calculateTableHeight(LootTableStructure table) {
        if (table == null) return 100;
        int height = 0;
        for (LootPool pool : table.pools()) {
            height += POOL_HEADER_HEIGHT;
            height += pool.entries().size() * ENTRY_HEIGHT;
        }
        return height + 50;
    }

    private void onToggleSync(Button button) {
        syncScroll = !syncScroll;
        syncButton.setMessage(Component.literal(syncScroll ? "Sync ✓" : "Sync"));
    }

    private void onSwap(Button button) {
        // Swap tables
        ResourceLocation tempId = leftTableId;
        leftTableId = rightTableId;
        rightTableId = tempId;

        String tempText = leftSearchBox.getValue();
        leftSearchBox.setValue(rightSearchBox.getValue());
        rightSearchBox.setValue(tempText);

        loadTables();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int halfWidth = width / 2;

        // Header background
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xFF1a1a1a);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF333333);

        // Title
        graphics.drawCenteredString(font, "Compare Loot Tables", width / 2, 8, IsotopeColors.ACCENT_GOLD);

        // Divider line
        graphics.fill(halfWidth - 1, HEADER_HEIGHT, halfWidth + 1, height, 0xFF333333);

        // Left panel header
        if (leftTableId != null) {
            String leftName = leftTableId.getPath();
            if (font.width(leftName) > halfWidth - 20) {
                leftName = font.plainSubstrByWidth(leftName, halfWidth - 30) + "...";
            }
            graphics.drawString(font, leftName, PADDING, 55, IsotopeColors.TEXT_PRIMARY, false);
        }

        // Right panel header
        if (rightTableId != null) {
            String rightName = rightTableId.getPath();
            if (font.width(rightName) > halfWidth - 20) {
                rightName = font.plainSubstrByWidth(rightName, halfWidth - 30) + "...";
            }
            graphics.drawString(font, rightName, halfWidth + PADDING, 55, IsotopeColors.TEXT_PRIMARY, false);
        }

        // Render table contents
        graphics.enableScissor(0, HEADER_HEIGHT, width, height);

        int contentY = HEADER_HEIGHT + 5 - scrollOffset;

        // Left table
        if (leftTable != null) {
            renderTable(graphics, leftTable, PADDING, contentY, halfWidth - PADDING * 2, mouseX, mouseY, true);
        } else {
            graphics.drawCenteredString(font, "Select a table", halfWidth / 2, HEADER_HEIGHT + 50,
                IsotopeColors.TEXT_MUTED);
        }

        // Right table
        if (rightTable != null) {
            renderTable(graphics, rightTable, halfWidth + PADDING, contentY, halfWidth - PADDING * 2, mouseX, mouseY, false);
        } else {
            graphics.drawCenteredString(font, "Select a table", halfWidth + halfWidth / 2, HEADER_HEIGHT + 50,
                IsotopeColors.TEXT_MUTED);
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = width - 5;
            int viewHeight = height - HEADER_HEIGHT;
            int thumbHeight = Math.max(20, (int) ((float) viewHeight / (viewHeight + maxScroll) * viewHeight));
            int thumbY = HEADER_HEIGHT + (int) ((float) scrollOffset / maxScroll * (viewHeight - thumbHeight));

            graphics.fill(scrollbarX, HEADER_HEIGHT, scrollbarX + 4, height, 0xFF2a2a2a);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF555555);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render dropdowns on top
        renderDropdowns(graphics, mouseX, mouseY);
    }

    private void renderTable(GuiGraphics graphics, LootTableStructure table, int x, int y, int panelWidth,
                             int mouseX, int mouseY, boolean isLeft) {
        // Calculate drop rates for comparison
        List<DropRate> rates = DropRateCalculator.calculate(table);
        Map<ResourceLocation, DropRate> rateMap = new HashMap<>();
        for (DropRate rate : rates) {
            rateMap.put(rate.item(), rate);
        }

        int currentY = y;

        for (int poolIdx = 0; poolIdx < table.pools().size(); poolIdx++) {
            LootPool pool = table.pools().get(poolIdx);

            // Pool header
            if (currentY + POOL_HEADER_HEIGHT > HEADER_HEIGHT && currentY < height) {
                graphics.fill(x, currentY, x + panelWidth, currentY + POOL_HEADER_HEIGHT - 2, 0xFF252525);

                String poolLabel = "Pool " + (poolIdx + 1) + " (" + pool.entries().size() + " entries)";
                graphics.drawString(font, poolLabel, x + 4, currentY + 5, IsotopeColors.TEXT_PRIMARY, false);

                // Rolls info
                String rollsInfo = "Rolls: " + pool.rolls();
                int rollsWidth = font.width(rollsInfo);
                graphics.drawString(font, rollsInfo, x + panelWidth - rollsWidth - 4, currentY + 5,
                    IsotopeColors.TEXT_MUTED, false);
            }
            currentY += POOL_HEADER_HEIGHT;

            // Entries
            for (int entryIdx = 0; entryIdx < pool.entries().size(); entryIdx++) {
                LootEntry entry = pool.entries().get(entryIdx);

                if (currentY + ENTRY_HEIGHT > HEADER_HEIGHT && currentY < height) {
                    // Check if this entry differs from other table
                    boolean differs = checkEntryDiffers(entry, poolIdx, entryIdx, isLeft);

                    // Background for difference highlighting
                    if (differs) {
                        graphics.fill(x, currentY, x + panelWidth, currentY + ENTRY_HEIGHT, 0xFF3a2a1a);
                    }

                    // Item icon
                    final int itemY = currentY;
                    entry.name().ifPresent(itemId -> {
                        var itemOpt = BuiltInRegistries.ITEM.get(itemId);
                        if (itemOpt.isPresent()) {
                            ItemStack stack = new ItemStack(itemOpt.get().value());
                            graphics.renderItem(stack, x + 2, itemY + 1);
                        }
                    });

                    // Entry name
                    String entryName = entry.name().map(ResourceLocation::getPath).orElse("???");
                    if (font.width(entryName) > panelWidth - 100) {
                        entryName = font.plainSubstrByWidth(entryName, panelWidth - 110) + "...";
                    }
                    int nameColor = differs ? 0xFFFFAA00 : IsotopeColors.TEXT_SECONDARY;
                    graphics.drawString(font, entryName, x + 20, currentY + 5, nameColor, false);

                    // Weight
                    String weightStr = "W:" + entry.weight();
                    graphics.drawString(font, weightStr, x + panelWidth - 60, currentY + 5,
                        IsotopeColors.TEXT_MUTED, false);

                    // Drop rate percentage
                    DropRate rate = entry.name().map(rateMap::get).orElse(null);
                    if (rate != null) {
                        String pctStr = String.format("%.1f%%", rate.percentChance());
                        int pctWidth = font.width(pctStr);
                        int rateColor = DropRateCalculator.getRarityColor(rate.probability());
                        graphics.drawString(font, pctStr, x + panelWidth - pctWidth - 4, currentY + 5,
                            rateColor, false);
                    }
                }
                currentY += ENTRY_HEIGHT;
            }
        }
    }

    private boolean checkEntryDiffers(LootEntry entry, int poolIdx, int entryIdx, boolean isLeft) {
        LootTableStructure otherTable = isLeft ? rightTable : leftTable;
        if (otherTable == null) return false;

        // Check if pool exists
        if (poolIdx >= otherTable.pools().size()) return true;
        LootPool otherPool = otherTable.pools().get(poolIdx);

        // Check if entry exists
        if (entryIdx >= otherPool.entries().size()) return true;
        LootEntry otherEntry = otherPool.entries().get(entryIdx);

        // Compare key attributes
        if (!Objects.equals(entry.name(), otherEntry.name())) return true;
        if (entry.weight() != otherEntry.weight()) return true;

        return false;
    }

    private void renderDropdowns(GuiGraphics graphics, int mouseX, int mouseY) {
        int halfWidth = width / 2;

        // Left dropdown
        if (leftDropdownOpen && !filteredTablesLeft.isEmpty()) {
            int dropY = 50;
            int dropHeight = Math.min(filteredTablesLeft.size() * 14 + 4, 150);

            graphics.fill(PADDING - 1, dropY - 1, halfWidth - PADDING + 1, dropY + dropHeight + 1, 0xFF606060);
            graphics.fill(PADDING, dropY, halfWidth - PADDING, dropY + dropHeight, 0xFF2a2a2a);

            int itemY = dropY + 2;
            for (ResourceLocation id : filteredTablesLeft) {
                boolean hovered = mouseX >= PADDING && mouseX < halfWidth - PADDING &&
                    mouseY >= itemY && mouseY < itemY + 14;

                if (hovered) {
                    graphics.fill(PADDING, itemY, halfWidth - PADDING, itemY + 14, 0xFF404040);
                }

                String display = id.toString();
                if (font.width(display) > halfWidth - PADDING * 2 - 8) {
                    display = font.plainSubstrByWidth(display, halfWidth - PADDING * 2 - 16) + "...";
                }
                graphics.drawString(font, display, PADDING + 4, itemY + 2, IsotopeColors.TEXT_PRIMARY, false);
                itemY += 14;
            }
        }

        // Right dropdown
        if (rightDropdownOpen && !filteredTablesRight.isEmpty()) {
            int dropY = 50;
            int dropHeight = Math.min(filteredTablesRight.size() * 14 + 4, 150);

            graphics.fill(halfWidth + PADDING - 1, dropY - 1, width - PADDING + 1, dropY + dropHeight + 1, 0xFF606060);
            graphics.fill(halfWidth + PADDING, dropY, width - PADDING, dropY + dropHeight, 0xFF2a2a2a);

            int itemY = dropY + 2;
            for (ResourceLocation id : filteredTablesRight) {
                boolean hovered = mouseX >= halfWidth + PADDING && mouseX < width - PADDING &&
                    mouseY >= itemY && mouseY < itemY + 14;

                if (hovered) {
                    graphics.fill(halfWidth + PADDING, itemY, width - PADDING, itemY + 14, 0xFF404040);
                }

                String display = id.toString();
                if (font.width(display) > halfWidth - PADDING * 2 - 8) {
                    display = font.plainSubstrByWidth(display, halfWidth - PADDING * 2 - 16) + "...";
                }
                graphics.drawString(font, display, halfWidth + PADDING + 4, itemY + 2, IsotopeColors.TEXT_PRIMARY, false);
                itemY += 14;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int halfWidth = width / 2;

        // Check left dropdown clicks
        if (leftDropdownOpen) {
            int dropY = 50;
            int itemY = dropY + 2;
            for (ResourceLocation id : filteredTablesLeft) {
                if (mouseX >= PADDING && mouseX < halfWidth - PADDING &&
                    mouseY >= itemY && mouseY < itemY + 14) {
                    leftTableId = id;
                    leftSearchBox.setValue(id.toString());
                    leftDropdownOpen = false;
                    loadTables();
                    return true;
                }
                itemY += 14;
            }
            leftDropdownOpen = false;
        }

        // Check right dropdown clicks
        if (rightDropdownOpen) {
            int dropY = 50;
            int itemY = dropY + 2;
            for (ResourceLocation id : filteredTablesRight) {
                if (mouseX >= halfWidth + PADDING && mouseX < width - PADDING &&
                    mouseY >= itemY && mouseY < itemY + 14) {
                    rightTableId = id;
                    rightSearchBox.setValue(id.toString());
                    rightDropdownOpen = false;
                    loadTables();
                    return true;
                }
                itemY += 14;
            }
            rightDropdownOpen = false;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
        return true;
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
