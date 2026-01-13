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
 * Manages recently viewed loot tables for quick access.
 * Persists to .minecraft/isotope/recent.json
 *
 * Most recently viewed tables appear first.
 * Limited to MAX_RECENT entries.
 */
public final class RecentTablesManager {

    private static final RecentTablesManager INSTANCE = new RecentTablesManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_RECENT = 15;

    private final LinkedList<ResourceLocation> recentTables = new LinkedList<>();
    private final List<RecentListener> listeners = new CopyOnWriteArrayList<>();
    private boolean loaded = false;

    private RecentTablesManager() {}

    public static RecentTablesManager getInstance() {
        return INSTANCE;
    }

    /**
     * Record a table as recently viewed.
     * Moves to front if already in list, otherwise adds to front.
     * Trims oldest entries if over MAX_RECENT.
     */
    public void recordView(ResourceLocation tableId) {
        ensureLoaded();

        // Remove if exists (will re-add at front)
        recentTables.remove(tableId);

        // Add to front
        recentTables.addFirst(tableId);

        // Trim if too many
        while (recentTables.size() > MAX_RECENT) {
            recentTables.removeLast();
        }

        save();
        notifyListeners();
    }

    /**
     * Remove a table from recent history.
     */
    public void remove(ResourceLocation tableId) {
        ensureLoaded();
        if (recentTables.remove(tableId)) {
            save();
            notifyListeners();
        }
    }

    /**
     * Check if a table is in recent history.
     */
    public boolean isRecent(ResourceLocation tableId) {
        ensureLoaded();
        return recentTables.contains(tableId);
    }

    /**
     * Get all recent tables (most recent first).
     */
    public List<ResourceLocation> getAll() {
        ensureLoaded();
        return new ArrayList<>(recentTables);
    }

    /**
     * Get recent count.
     */
    public int getCount() {
        ensureLoaded();
        return recentTables.size();
    }

    /**
     * Clear all recent history.
     */
    public void clear() {
        recentTables.clear();
        save();
        notifyListeners();
    }

    /**
     * Add a listener for recent changes.
     */
    public void addListener(RecentListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(RecentListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (RecentListener listener : listeners) {
            listener.onRecentChanged();
        }
    }

    /**
     * Ensure recent tables are loaded from disk.
     */
    private void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    /**
     * Get the recent file path.
     */
    private Path getRecentPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("recent.json");
    }

    /**
     * Save recent tables to disk.
     */
    public void save() {
        try {
            Path path = getRecentPath();
            Files.createDirectories(path.getParent());

            // Convert to string list for JSON
            List<String> recentStrings = new ArrayList<>();
            for (ResourceLocation id : recentTables) {
                recentStrings.add(id.toString());
            }

            String json = GSON.toJson(recentStrings);
            Files.writeString(path, json);

            Isotope.LOGGER.debug("Saved {} recent tables", recentTables.size());
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to save recent tables", e);
        }
    }

    /**
     * Load recent tables from disk.
     */
    public void load() {
        Path path = getRecentPath();
        if (!Files.exists(path)) {
            Isotope.LOGGER.debug("No recent tables file found");
            return;
        }

        try {
            String json = Files.readString(path);
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> recentStrings = GSON.fromJson(json, listType);

            recentTables.clear();
            if (recentStrings != null) {
                for (String str : recentStrings) {
                    ResourceLocation id = ResourceLocation.tryParse(str);
                    if (id != null) {
                        recentTables.add(id);
                    }
                }
            }

            Isotope.LOGGER.info("Loaded {} recent tables", recentTables.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to load recent tables", e);
        }
    }

    /**
     * Listener for recent changes.
     */
    @FunctionalInterface
    public interface RecentListener {
        void onRecentChanged();
    }
}
