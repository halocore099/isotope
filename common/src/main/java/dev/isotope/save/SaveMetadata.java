package dev.isotope.save;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Lightweight metadata for displaying saves in a list without loading full data.
 */
public record SaveMetadata(
    String id,
    String name,
    long timestamp,
    int structureCount,
    int lootTableCount,
    int linkedCount
) {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy HH:mm");

    /**
     * Get formatted date string for display.
     */
    public String getFormattedDate() {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    /**
     * Get a summary string like "34 structures, 1237 tables, 32 linked"
     */
    public String getSummary() {
        return String.format("%d structures, %d tables, %d linked",
            structureCount, lootTableCount, linkedCount);
    }

    /**
     * Get display name (use filename if name is empty).
     */
    public String getDisplayName() {
        return name != null && !name.isEmpty() ? name : id;
    }
}
