package dev.isotope.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.isotope.Isotope;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the current color theme for ISOTOPE UI.
 * Supports switching between light and dark themes at runtime.
 */
@Environment(EnvType.CLIENT)
public final class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ColorTheme currentTheme = ColorTheme.DARK;
    private final List<Runnable> listeners = new ArrayList<>();
    private Path configPath;

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get the current theme.
     */
    public ColorTheme getTheme() {
        return currentTheme;
    }

    /**
     * Check if dark theme is active.
     */
    public boolean isDarkTheme() {
        return currentTheme == ColorTheme.DARK;
    }

    /**
     * Check if light theme is active.
     */
    public boolean isLightTheme() {
        return currentTheme == ColorTheme.LIGHT;
    }

    /**
     * Set the current theme.
     */
    public void setTheme(ColorTheme theme) {
        if (theme == null) {
            theme = ColorTheme.DARK;
        }
        if (currentTheme != theme) {
            currentTheme = theme;
            notifyListeners();
            save();
            Isotope.LOGGER.info("Theme changed to: {}", theme.name());
        }
    }

    /**
     * Toggle between dark and light themes.
     */
    public void toggleTheme() {
        setTheme(isDarkTheme() ? ColorTheme.LIGHT : ColorTheme.DARK);
    }

    /**
     * Add a listener to be notified when the theme changes.
     */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Remove a theme change listener.
     */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                Isotope.LOGGER.error("Error notifying theme listener", e);
            }
        }
    }

    /**
     * Initialize the theme manager and load saved preferences.
     */
    public void initialize(Path gameDir) {
        configPath = gameDir.resolve("isotope").resolve("theme.json");
        load();
    }

    /**
     * Load theme preference from disk.
     */
    private void load() {
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }

        try {
            String json = Files.readString(configPath);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);

            if (obj.has("theme")) {
                String themeName = obj.get("theme").getAsString();
                if ("light".equalsIgnoreCase(themeName)) {
                    currentTheme = ColorTheme.LIGHT;
                } else {
                    currentTheme = ColorTheme.DARK;
                }
                Isotope.LOGGER.info("Loaded theme preference: {}", currentTheme.name());
            }
        } catch (Exception e) {
            Isotope.LOGGER.warn("Failed to load theme preference", e);
        }
    }

    /**
     * Save theme preference to disk.
     */
    private void save() {
        if (configPath == null) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("theme", currentTheme.name().toLowerCase());

            Files.writeString(configPath, GSON.toJson(obj));
        } catch (IOException e) {
            Isotope.LOGGER.warn("Failed to save theme preference", e);
        }
    }

    // === Theme-aware color accessors ===
    // These methods return colors based on the current theme.

    public int backgroundDark() {
        return currentTheme.backgroundDark();
    }

    public int backgroundMedium() {
        return currentTheme.backgroundMedium();
    }

    public int backgroundSolid() {
        return currentTheme.backgroundSolid();
    }

    public int backgroundPanel() {
        return currentTheme.backgroundPanel();
    }

    public int textPrimary() {
        return currentTheme.textPrimary();
    }

    public int textSecondary() {
        return currentTheme.textSecondary();
    }

    public int textMuted() {
        return currentTheme.textMuted();
    }

    public int borderDefault() {
        return currentTheme.borderDefault();
    }

    public int borderHighlight() {
        return currentTheme.borderHighlight();
    }

    public int entryBackground() {
        return currentTheme.entryBackground();
    }

    public int entryBackgroundHover() {
        return currentTheme.entryBackgroundHover();
    }

    public int buttonBackground() {
        return currentTheme.buttonBackground();
    }

    public int buttonHover() {
        return currentTheme.buttonHover();
    }

    public int inputBackground() {
        return currentTheme.inputBackground();
    }

    public int scrollbarTrack() {
        return currentTheme.scrollbarTrack();
    }

    public int scrollbarThumb() {
        return currentTheme.scrollbarThumb();
    }

    public int tooltipBackground() {
        return currentTheme.tooltipBackground();
    }

    public int overlayDim() {
        return currentTheme.overlayDim();
    }

    public int overlayDark() {
        return currentTheme.overlayDark();
    }
}
