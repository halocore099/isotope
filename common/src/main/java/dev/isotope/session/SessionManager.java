package dev.isotope.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.Isotope;
import dev.isotope.data.BookmarkManager;
import dev.isotope.ui.TabManager;
import dev.isotope.ui.EditorTab;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages editor sessions: save, load, list, delete.
 *
 * Sessions are stored in .minecraft/isotope/sessions/ as JSON files.
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String AUTOSAVE_NAME = "_autosave";

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get the sessions directory path.
     */
    private Path getSessionsDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("sessions");
    }

    /**
     * Save the current editor state as a session.
     *
     * @param name Session name
     * @param tabManager Current tab manager
     * @return The saved session
     */
    public EditorSession saveSession(String name, TabManager tabManager) {
        return saveSession(name, tabManager, null);
    }

    /**
     * Save the current editor state as a session with UI state.
     *
     * @param name Session name
     * @param tabManager Current tab manager
     * @param uiState UI panel visibility state
     * @return The saved session
     */
    public EditorSession saveSession(String name, TabManager tabManager, EditorSession.UIState uiState) {
        try {
            // Gather current state
            List<ResourceLocation> openTabs = tabManager.getTabs().stream()
                .map(EditorTab::tableId)
                .toList();
            int activeTabIndex = tabManager.getActiveTabIndex();
            List<ResourceLocation> bookmarks = BookmarkManager.getInstance().getAll();

            // Create session
            EditorSession session = EditorSession.create(name, openTabs, activeTabIndex, bookmarks, uiState);

            // Save to file
            Path sessionsDir = getSessionsDir();
            Files.createDirectories(sessionsDir);

            String fileName = sanitizeFileName(name) + ".json";
            Path sessionFile = sessionsDir.resolve(fileName);

            String json = GSON.toJson(session);
            Files.writeString(sessionFile, json);

            Isotope.LOGGER.info("Saved session '{}' with {} tabs", name, openTabs.size());
            return session;

        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to save session", e);
            throw new RuntimeException("Failed to save session: " + e.getMessage(), e);
        }
    }

    /**
     * Auto-save the current state.
     */
    public void autoSave(TabManager tabManager) {
        autoSave(tabManager, null);
    }

    /**
     * Auto-save the current state with UI state.
     */
    public void autoSave(TabManager tabManager, EditorSession.UIState uiState) {
        if (tabManager.getTabCount() == 0) {
            return; // Nothing to save
        }
        try {
            saveSession(AUTOSAVE_NAME, tabManager, uiState);
        } catch (Exception e) {
            Isotope.LOGGER.warn("Auto-save failed", e);
        }
    }

    /**
     * Load a session by name.
     *
     * @param name Session name
     * @return The loaded session, or empty if not found
     */
    public Optional<EditorSession> loadSession(String name) {
        try {
            String fileName = sanitizeFileName(name) + ".json";
            Path sessionFile = getSessionsDir().resolve(fileName);

            if (!Files.exists(sessionFile)) {
                return Optional.empty();
            }

            String json = Files.readString(sessionFile);
            EditorSession session = GSON.fromJson(json, EditorSession.class);

            Isotope.LOGGER.info("Loaded session '{}' with {} tabs", name, session.openTabs().size());
            return Optional.of(session);

        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to load session", e);
            return Optional.empty();
        }
    }

    /**
     * Apply a loaded session to the tab manager.
     */
    public void applySession(EditorSession session, TabManager tabManager) {
        // Close all existing tabs
        tabManager.closeAll();

        // Open tabs from session
        for (ResourceLocation tableId : session.getOpenTabIds()) {
            tabManager.openTab(tableId);
        }

        // Switch to active tab
        if (session.activeTabIndex() >= 0 && session.activeTabIndex() < session.openTabs().size()) {
            tabManager.switchTab(session.activeTabIndex());
        }

        // Restore bookmarks
        BookmarkManager bookmarkMgr = BookmarkManager.getInstance();
        bookmarkMgr.clear();
        for (ResourceLocation id : session.getBookmarkIds()) {
            bookmarkMgr.add(id);
        }

        Isotope.LOGGER.info("Applied session '{}' - {} tabs, {} bookmarks",
            session.name(), session.openTabs().size(), session.bookmarks().size());
    }

    /**
     * List all saved sessions.
     *
     * @return List of session info (name, timestamp, tab count)
     */
    public List<SessionInfo> listSessions() {
        Path sessionsDir = getSessionsDir();
        if (!Files.exists(sessionsDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(sessionsDir)) {
            return files
                .filter(p -> p.toString().endsWith(".json"))
                .map(this::loadSessionInfo)
                .filter(Objects::nonNull)
                .filter(info -> !info.name().equals(AUTOSAVE_NAME)) // Hide autosave
                .sorted(Comparator.comparing(SessionInfo::lastModified).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to list sessions", e);
            return List.of();
        }
    }

    /**
     * Check if an autosave exists.
     */
    public boolean hasAutosave() {
        return loadSession(AUTOSAVE_NAME).isPresent();
    }

    /**
     * Load the autosave session.
     */
    public Optional<EditorSession> loadAutosave() {
        return loadSession(AUTOSAVE_NAME);
    }

    private SessionInfo loadSessionInfo(Path file) {
        try {
            String json = Files.readString(file);
            EditorSession session = GSON.fromJson(json, EditorSession.class);

            String dateStr = DATE_FORMAT.format(
                Instant.ofEpochMilli(session.lastModified())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            );

            return new SessionInfo(
                session.name(),
                session.lastModified(),
                dateStr,
                session.openTabs().size(),
                session.bookmarks().size()
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Delete a session by name.
     */
    public boolean deleteSession(String name) {
        try {
            String fileName = sanitizeFileName(name) + ".json";
            Path sessionFile = getSessionsDir().resolve(fileName);
            return Files.deleteIfExists(sessionFile);
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to delete session", e);
            return false;
        }
    }

    /**
     * Rename a session.
     */
    public boolean renameSession(String oldName, String newName) {
        Optional<EditorSession> session = loadSession(oldName);
        if (session.isEmpty()) {
            return false;
        }

        try {
            // Create new session with new name
            EditorSession updated = new EditorSession(
                session.get().id(),
                newName,
                session.get().createdAt(),
                System.currentTimeMillis(),
                session.get().openTabs(),
                session.get().activeTabIndex(),
                session.get().bookmarks(),
                session.get().metadata(),
                session.get().getUIState()
            );

            // Save with new name
            String newFileName = sanitizeFileName(newName) + ".json";
            Path newFile = getSessionsDir().resolve(newFileName);
            Files.writeString(newFile, GSON.toJson(updated));

            // Delete old file
            deleteSession(oldName);

            return true;
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to rename session", e);
            return false;
        }
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    /**
     * Summary info for a session (for list display).
     */
    public record SessionInfo(
        String name,
        long lastModified,
        String formattedDate,
        int tabCount,
        int bookmarkCount
    ) {}
}
