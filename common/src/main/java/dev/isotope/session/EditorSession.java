package dev.isotope.session;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Represents a saved editing session.
 *
 * Captures the state of the editor including open tabs, bookmarks,
 * and selection state for later restoration.
 */
public record EditorSession(
    String id,
    String name,
    long createdAt,
    long lastModified,
    List<String> openTabs,      // Table IDs as strings
    int activeTabIndex,
    List<String> bookmarks,     // Bookmark IDs as strings
    SessionMetadata metadata
) {
    /**
     * Create a new session with generated ID and current timestamp.
     */
    public static EditorSession create(
            String name,
            List<ResourceLocation> openTabs,
            int activeTabIndex,
            List<ResourceLocation> bookmarks
    ) {
        long now = System.currentTimeMillis();
        return new EditorSession(
            UUID.randomUUID().toString(),
            name,
            now,
            now,
            openTabs.stream().map(ResourceLocation::toString).toList(),
            activeTabIndex,
            bookmarks.stream().map(ResourceLocation::toString).toList(),
            SessionMetadata.current()
        );
    }

    /**
     * Create a copy with updated modification time.
     */
    public EditorSession withUpdatedTime() {
        return new EditorSession(
            id,
            name,
            createdAt,
            System.currentTimeMillis(),
            openTabs,
            activeTabIndex,
            bookmarks,
            metadata
        );
    }

    /**
     * Get open tabs as ResourceLocations.
     */
    public List<ResourceLocation> getOpenTabIds() {
        return openTabs.stream()
            .map(ResourceLocation::tryParse)
            .filter(id -> id != null)
            .toList();
    }

    /**
     * Get bookmarks as ResourceLocations.
     */
    public List<ResourceLocation> getBookmarkIds() {
        return bookmarks.stream()
            .map(ResourceLocation::tryParse)
            .filter(id -> id != null)
            .toList();
    }

    /**
     * Metadata about the session environment.
     */
    public record SessionMetadata(
        String minecraftVersion,
        String isotopeVersion
    ) {
        public static SessionMetadata current() {
            return new SessionMetadata(
                "1.21.4",
                "1.0.0"
            );
        }
    }
}
