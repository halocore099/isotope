package dev.isotope.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.isotope.Isotope;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages bookmarked loot tables for quick access.
 * Persists bookmarks to .minecraft/isotope/bookmarks.json
 */
public final class BookmarkManager {

    private static final BookmarkManager INSTANCE = new BookmarkManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<ResourceLocation> bookmarks = new LinkedHashSet<>();
    private final List<BookmarkListener> listeners = new CopyOnWriteArrayList<>();
    private boolean loaded = false;

    private BookmarkManager() {}

    public static BookmarkManager getInstance() {
        return INSTANCE;
    }

    /**
     * Add a table to bookmarks.
     */
    public void add(ResourceLocation tableId) {
        if (bookmarks.add(tableId)) {
            save();
            notifyListeners();
        }
    }

    /**
     * Remove a table from bookmarks.
     */
    public void remove(ResourceLocation tableId) {
        if (bookmarks.remove(tableId)) {
            save();
            notifyListeners();
        }
    }

    /**
     * Toggle bookmark status.
     */
    public boolean toggle(ResourceLocation tableId) {
        if (bookmarks.contains(tableId)) {
            remove(tableId);
            return false;
        } else {
            add(tableId);
            return true;
        }
    }

    /**
     * Check if a table is bookmarked.
     */
    public boolean isBookmarked(ResourceLocation tableId) {
        ensureLoaded();
        return bookmarks.contains(tableId);
    }

    /**
     * Get all bookmarked tables.
     */
    public List<ResourceLocation> getAll() {
        ensureLoaded();
        return new ArrayList<>(bookmarks);
    }

    /**
     * Get bookmark count.
     */
    public int getCount() {
        ensureLoaded();
        return bookmarks.size();
    }

    /**
     * Clear all bookmarks.
     */
    public void clear() {
        bookmarks.clear();
        save();
        notifyListeners();
    }

    /**
     * Add a listener for bookmark changes.
     */
    public void addListener(BookmarkListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(BookmarkListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (BookmarkListener listener : listeners) {
            listener.onBookmarksChanged();
        }
    }

    /**
     * Ensure bookmarks are loaded from disk.
     */
    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    /**
     * Get the bookmarks file path.
     */
    private Path getBookmarksPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("bookmarks.json");
    }

    /**
     * Save bookmarks to disk.
     */
    public void save() {
        try {
            Path path = getBookmarksPath();
            Files.createDirectories(path.getParent());

            // Convert to string list for JSON
            List<String> bookmarkStrings = new ArrayList<>();
            for (ResourceLocation id : bookmarks) {
                bookmarkStrings.add(id.toString());
            }

            String json = GSON.toJson(bookmarkStrings);
            Files.writeString(path, json);

            Isotope.LOGGER.debug("Saved {} bookmarks", bookmarks.size());
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to save bookmarks", e);
        }
    }

    /**
     * Load bookmarks from disk.
     */
    public void load() {
        Path path = getBookmarksPath();
        if (!Files.exists(path)) {
            Isotope.LOGGER.debug("No bookmarks file found");
            return;
        }

        try {
            String json = Files.readString(path);
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> bookmarkStrings = GSON.fromJson(json, listType);

            bookmarks.clear();
            if (bookmarkStrings != null) {
                for (String str : bookmarkStrings) {
                    ResourceLocation id = ResourceLocation.tryParse(str);
                    if (id != null) {
                        bookmarks.add(id);
                    }
                }
            }

            Isotope.LOGGER.info("Loaded {} bookmarks", bookmarks.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to load bookmarks", e);
        }
    }

    /**
     * Listener for bookmark changes.
     */
    @FunctionalInterface
    public interface BookmarkListener {
        void onBookmarksChanged();
    }
}
