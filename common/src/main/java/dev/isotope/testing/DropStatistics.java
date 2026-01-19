package dev.isotope.testing;

import dev.isotope.compat.RegistryHelper;
import dev.isotope.ui.ScreenUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Tracks drop statistics from loot table testing.
 *
 * Collects item drops and provides statistical analysis including
 * totals, averages, and percentages per item type.
 */
public class DropStatistics {

    private final ResourceLocation sourceId;  // Loot table or entity ID
    private final String sourceName;
    private final String testCondition;  // e.g., "Player Kill", "Looting III"
    private int testCount = 0;
    private int totalDrops = 0;

    // Item ID -> list of drop counts per test
    private final Map<ResourceLocation, List<Integer>> dropsByItem = new LinkedHashMap<>();

    // Track total count per item across all tests
    private final Map<ResourceLocation, Integer> totalByItem = new LinkedHashMap<>();

    public DropStatistics(ResourceLocation sourceId, String sourceName, String testCondition) {
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.testCondition = testCondition;
    }

    /**
     * Start a new test iteration.
     */
    public void startTest() {
        testCount++;
        // Initialize drop counts for this test
        for (ResourceLocation itemId : dropsByItem.keySet()) {
            dropsByItem.get(itemId).add(0);
        }
    }

    /**
     * Record a dropped item.
     */
    public void recordDrop(ItemStack stack) {
        if (stack.isEmpty()) return;

        ResourceLocation itemId = RegistryHelper.getItemId(stack.getItem());
        int count = stack.getCount();

        // Initialize if first time seeing this item
        if (!dropsByItem.containsKey(itemId)) {
            List<Integer> counts = new ArrayList<>();
            // Backfill with zeros for previous tests
            for (int i = 0; i < testCount - 1; i++) {
                counts.add(0);
            }
            counts.add(count);
            dropsByItem.put(itemId, counts);
            totalByItem.put(itemId, count);
        } else {
            // Add to current test's count
            List<Integer> counts = dropsByItem.get(itemId);
            if (counts.size() < testCount) {
                counts.add(count);
            } else {
                counts.set(testCount - 1, counts.get(testCount - 1) + count);
            }
            totalByItem.merge(itemId, count, Integer::sum);
        }

        totalDrops += count;
    }

    /**
     * Record multiple drops at once.
     */
    public void recordDrops(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            recordDrop(stack);
        }
    }

    /**
     * Get source ID (loot table or entity).
     */
    public ResourceLocation getSourceId() {
        return sourceId;
    }

    /**
     * Get source display name.
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Get test condition description.
     */
    public String getTestCondition() {
        return testCondition;
    }

    /**
     * Get number of tests run.
     */
    public int getTestCount() {
        return testCount;
    }

    /**
     * Get total items dropped across all tests.
     */
    public int getTotalDrops() {
        return totalDrops;
    }

    /**
     * Get all unique items that dropped.
     */
    public Set<ResourceLocation> getDroppedItems() {
        return Collections.unmodifiableSet(dropsByItem.keySet());
    }

    /**
     * Get total count for a specific item.
     */
    public int getTotalForItem(ResourceLocation itemId) {
        return totalByItem.getOrDefault(itemId, 0);
    }

    /**
     * Get average drops per test for a specific item.
     */
    public float getAverageForItem(ResourceLocation itemId) {
        if (testCount == 0) return 0;
        return (float) getTotalForItem(itemId) / testCount;
    }

    /**
     * Get percentage of tests that dropped this item at least once.
     */
    public float getDropRateForItem(ResourceLocation itemId) {
        if (testCount == 0) return 0;
        List<Integer> counts = dropsByItem.get(itemId);
        if (counts == null) return 0;

        int testsWithDrop = 0;
        for (int count : counts) {
            if (count > 0) testsWithDrop++;
        }
        return (float) testsWithDrop / testCount * 100;
    }

    /**
     * Get min/max drops for a specific item.
     */
    public int[] getMinMaxForItem(ResourceLocation itemId) {
        List<Integer> counts = dropsByItem.get(itemId);
        if (counts == null || counts.isEmpty()) return new int[]{0, 0};

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int count : counts) {
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        return new int[]{min, max};
    }

    /**
     * Get a formatted summary of the statistics.
     */
    public List<String> getSummaryLines() {
        List<String> lines = new ArrayList<>();

        lines.add("§6" + sourceName + " §7(" + testCondition + ")");
        lines.add("§7Tests: §f" + testCount + " §7| Total drops: §f" + totalDrops);
        lines.add("");

        if (dropsByItem.isEmpty()) {
            lines.add("§8No items dropped");
        } else {
            // Sort by total count descending
            List<Map.Entry<ResourceLocation, Integer>> sorted = new ArrayList<>(totalByItem.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            for (var entry : sorted) {
                ResourceLocation itemId = entry.getKey();
                int total = entry.getValue();
                float avg = getAverageForItem(itemId);
                float rate = getDropRateForItem(itemId);
                int[] minMax = getMinMaxForItem(itemId);

                String itemName = ScreenUtils.formatItemName(itemId);
                lines.add(String.format("§f%s§7: %d (%.1f avg, %.0f%% rate, %d-%d)",
                    itemName, total, avg, rate, minMax[0], minMax[1]));
            }
        }

        return lines;
    }

    /**
     * Create a compact single-line summary.
     */
    public String getCompactSummary() {
        if (dropsByItem.isEmpty()) {
            return "No drops from " + testCount + " tests";
        }
        return totalDrops + " items from " + testCount + " tests (" + dropsByItem.size() + " unique)";
    }
}
