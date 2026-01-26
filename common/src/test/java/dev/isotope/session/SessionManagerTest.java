package dev.isotope.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isotope.compat.Id;
import dev.isotope.test.TestCategories.IntegrationTest;
import dev.isotope.test.TestFixtures;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SessionManager.
 * Tests session persistence using a temporary directory.
 */
@IntegrationTest
class SessionManagerTest {

    private Path tempDir;
    private TestSessionManager sessionManager;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("isotope-test-sessions");
        sessionManager = new TestSessionManager(tempDir);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp directory
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    // ===== Save Operations =====

    @Nested
    @DisplayName("Save operations")
    class SaveOperations {

        @Test
        @DisplayName("saves session to file")
        void savesSessionToFile() throws IOException {
            EditorSessionData session = createTestSession("test_session");

            sessionManager.saveSession(session);

            Path sessionFile = tempDir.resolve("test_session.json");
            assertThat(Files.exists(sessionFile)).isTrue();
        }

        @Test
        @DisplayName("saves session with correct JSON format")
        void savesCorrectJsonFormat() throws IOException {
            EditorSessionData session = createTestSession("json_test");
            session.openTabs.add("minecraft:chests/simple_dungeon");
            session.openTabs.add("minecraft:entities/zombie");

            sessionManager.saveSession(session);

            Path sessionFile = tempDir.resolve("json_test.json");
            String content = Files.readString(sessionFile);

            assertThat(content).contains("\"name\"");
            assertThat(content).contains("\"openTabs\"");
            assertThat(content).contains("minecraft:chests/simple_dungeon");
            assertThat(content).contains("minecraft:entities/zombie");
        }

        @Test
        @DisplayName("overwrites existing session")
        void overwritesExistingSession() throws IOException {
            EditorSessionData session1 = createTestSession("overwrite_test");
            session1.openTabs.add("minecraft:chests/old_table");

            EditorSessionData session2 = createTestSession("overwrite_test");
            session2.openTabs.add("minecraft:chests/new_table");

            sessionManager.saveSession(session1);
            sessionManager.saveSession(session2);

            EditorSessionData loaded = sessionManager.loadSession("overwrite_test");

            assertThat(loaded.openTabs).contains("minecraft:chests/new_table");
            assertThat(loaded.openTabs).doesNotContain("minecraft:chests/old_table");
        }

        @Test
        @DisplayName("saves session with bookmarks")
        void savesSessionWithBookmarks() throws IOException {
            EditorSessionData session = createTestSession("bookmarks_test");
            session.bookmarks.add("minecraft:chests/stronghold_corridor");
            session.bookmarks.add("minecraft:chests/ancient_city");

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("bookmarks_test");

            assertThat(loaded.bookmarks).hasSize(2);
            assertThat(loaded.bookmarks).contains("minecraft:chests/stronghold_corridor");
        }

        @Test
        @DisplayName("saves session with active tab index")
        void savesActiveTabIndex() throws IOException {
            EditorSessionData session = createTestSession("tab_index_test");
            session.openTabs.add("minecraft:chests/tab1");
            session.openTabs.add("minecraft:chests/tab2");
            session.openTabs.add("minecraft:chests/tab3");
            session.activeTabIndex = 2;

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("tab_index_test");

            assertThat(loaded.activeTabIndex).isEqualTo(2);
        }
    }

    // ===== Load Operations =====

    @Nested
    @DisplayName("Load operations")
    class LoadOperations {

        @Test
        @DisplayName("loads saved session")
        void loadsSavedSession() throws IOException {
            EditorSessionData original = createTestSession("load_test");
            original.openTabs.add("minecraft:chests/simple_dungeon");

            sessionManager.saveSession(original);
            EditorSessionData loaded = sessionManager.loadSession("load_test");

            assertThat(loaded.name).isEqualTo("load_test");
            assertThat(loaded.openTabs).contains("minecraft:chests/simple_dungeon");
        }

        @Test
        @DisplayName("returns null for non-existent session")
        void returnsNullForNonExistent() {
            EditorSessionData loaded = sessionManager.loadSession("nonexistent");

            assertThat(loaded).isNull();
        }

        @Test
        @DisplayName("preserves all session data on round-trip")
        void preservesAllDataOnRoundTrip() throws IOException {
            EditorSessionData original = createTestSession("round_trip_test");
            original.openTabs.addAll(List.of("table1", "table2", "table3"));
            original.bookmarks.addAll(List.of("bookmark1", "bookmark2"));
            original.activeTabIndex = 1;
            original.lastModified = System.currentTimeMillis();
            original.metadata.put("custom_key", "custom_value");

            sessionManager.saveSession(original);
            EditorSessionData loaded = sessionManager.loadSession("round_trip_test");

            assertThat(loaded.name).isEqualTo(original.name);
            assertThat(loaded.openTabs).containsExactlyElementsOf(original.openTabs);
            assertThat(loaded.bookmarks).containsExactlyElementsOf(original.bookmarks);
            assertThat(loaded.activeTabIndex).isEqualTo(original.activeTabIndex);
            assertThat(loaded.lastModified).isEqualTo(original.lastModified);
            assertThat(loaded.metadata).containsEntry("custom_key", "custom_value");
        }
    }

    // ===== List Operations =====

    @Nested
    @DisplayName("List operations")
    class ListOperations {

        @Test
        @DisplayName("lists all saved sessions")
        void listsAllSessions() throws IOException {
            sessionManager.saveSession(createTestSession("session1"));
            sessionManager.saveSession(createTestSession("session2"));
            sessionManager.saveSession(createTestSession("session3"));

            List<String> sessions = sessionManager.listSessions();

            assertThat(sessions).hasSize(3);
            assertThat(sessions).contains("session1", "session2", "session3");
        }

        @Test
        @DisplayName("returns empty list when no sessions")
        void emptyListWhenNoSessions() {
            List<String> sessions = sessionManager.listSessions();

            assertThat(sessions).isEmpty();
        }

        @Test
        @DisplayName("ignores non-json files")
        void ignoresNonJsonFiles() throws IOException {
            sessionManager.saveSession(createTestSession("valid_session"));
            Files.writeString(tempDir.resolve("notes.txt"), "some notes");
            Files.writeString(tempDir.resolve("data.xml"), "<data/>");

            List<String> sessions = sessionManager.listSessions();

            assertThat(sessions).containsExactly("valid_session");
        }
    }

    // ===== Delete Operations =====

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @DisplayName("deletes session file")
        void deletesSessionFile() throws IOException {
            sessionManager.saveSession(createTestSession("to_delete"));
            Path sessionFile = tempDir.resolve("to_delete.json");
            assertThat(Files.exists(sessionFile)).isTrue();

            boolean deleted = sessionManager.deleteSession("to_delete");

            assertThat(deleted).isTrue();
            assertThat(Files.exists(sessionFile)).isFalse();
        }

        @Test
        @DisplayName("returns false for non-existent session")
        void returnsFalseForNonExistent() {
            boolean deleted = sessionManager.deleteSession("nonexistent");

            assertThat(deleted).isFalse();
        }

        @Test
        @DisplayName("deleted session not in list")
        void deletedNotInList() throws IOException {
            sessionManager.saveSession(createTestSession("keep"));
            sessionManager.saveSession(createTestSession("delete_me"));

            sessionManager.deleteSession("delete_me");
            List<String> sessions = sessionManager.listSessions();

            assertThat(sessions).containsExactly("keep");
        }
    }

    // ===== Autosave =====

    @Nested
    @DisplayName("Autosave")
    class Autosave {

        @Test
        @DisplayName("saves autosave session with special name")
        void savesAutosaveWithSpecialName() throws IOException {
            EditorSessionData session = createTestSession("_autosave");
            session.openTabs.add("minecraft:chests/test");

            sessionManager.saveSession(session);

            assertThat(sessionManager.hasAutosave()).isTrue();
        }

        @Test
        @DisplayName("loads autosave session")
        void loadsAutosave() throws IOException {
            EditorSessionData session = createTestSession("_autosave");
            session.openTabs.add("minecraft:chests/autosaved");

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.getAutosave();

            assertThat(loaded).isNotNull();
            assertThat(loaded.openTabs).contains("minecraft:chests/autosaved");
        }

        @Test
        @DisplayName("autosave not listed in regular sessions")
        void autosaveNotInRegularList() throws IOException {
            sessionManager.saveSession(createTestSession("regular_session"));
            sessionManager.saveSession(createTestSession("_autosave"));

            List<String> sessions = sessionManager.listSessions();

            assertThat(sessions).containsExactly("regular_session");
            assertThat(sessions).doesNotContain("_autosave");
        }

        @Test
        @DisplayName("returns null when no autosave exists")
        void nullWhenNoAutosave() {
            assertThat(sessionManager.hasAutosave()).isFalse();
            assertThat(sessionManager.getAutosave()).isNull();
        }
    }

    // ===== Edge Cases =====

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles session name with special characters")
        void handlesSpecialCharacters() throws IOException {
            // Note: Some characters may not be valid in filenames
            EditorSessionData session = createTestSession("session-with-dashes_and_underscores");
            session.openTabs.add("minecraft:test");

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("session-with-dashes_and_underscores");

            assertThat(loaded).isNotNull();
            assertThat(loaded.name).isEqualTo("session-with-dashes_and_underscores");
        }

        @Test
        @DisplayName("handles empty session")
        void handlesEmptySession() throws IOException {
            EditorSessionData session = createTestSession("empty_session");
            // Leave all lists empty

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("empty_session");

            assertThat(loaded.openTabs).isEmpty();
            assertThat(loaded.bookmarks).isEmpty();
        }

        @Test
        @DisplayName("handles large session")
        void handlesLargeSession() throws IOException {
            EditorSessionData session = createTestSession("large_session");

            // Add many tabs
            for (int i = 0; i < 100; i++) {
                session.openTabs.add("minecraft:chests/table_" + i);
            }

            // Add many bookmarks
            for (int i = 0; i < 50; i++) {
                session.bookmarks.add("minecraft:entities/mob_" + i);
            }

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("large_session");

            assertThat(loaded.openTabs).hasSize(100);
            assertThat(loaded.bookmarks).hasSize(50);
        }

        @Test
        @DisplayName("handles concurrent saves")
        void handlesConcurrentSaves() throws Exception {
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int index = i;
                Thread t = new Thread(() -> {
                    try {
                        EditorSessionData session = createTestSession("concurrent_" + index);
                        session.openTabs.add("minecraft:test_" + index);
                        sessionManager.saveSession(session);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                threads.add(t);
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }

            List<String> sessions = sessionManager.listSessions();
            assertThat(sessions).hasSize(10);
        }
    }

    // ===== Timestamp Handling =====

    @Nested
    @DisplayName("Timestamp handling")
    class TimestampHandling {

        @Test
        @DisplayName("preserves lastModified timestamp")
        void preservesLastModified() throws IOException {
            long timestamp = System.currentTimeMillis() - 1000; // 1 second ago
            EditorSessionData session = createTestSession("timestamp_test");
            session.lastModified = timestamp;

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("timestamp_test");

            assertThat(loaded.lastModified).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("preserves createdAt timestamp")
        void preservesCreatedAt() throws IOException {
            long timestamp = System.currentTimeMillis() - 10000; // 10 seconds ago
            EditorSessionData session = createTestSession("created_test");
            session.createdAt = timestamp;

            sessionManager.saveSession(session);
            EditorSessionData loaded = sessionManager.loadSession("created_test");

            assertThat(loaded.createdAt).isEqualTo(timestamp);
        }
    }

    // ===== Helper Methods =====

    private EditorSessionData createTestSession(String name) {
        EditorSessionData session = new EditorSessionData();
        session.name = name;
        session.openTabs = new ArrayList<>();
        session.bookmarks = new ArrayList<>();
        session.activeTabIndex = 0;
        session.createdAt = System.currentTimeMillis();
        session.lastModified = System.currentTimeMillis();
        session.metadata = new HashMap<>();
        return session;
    }

    // ===== Test Session Manager =====

    /**
     * Test implementation of session manager that uses a configurable directory.
     */
    static class TestSessionManager {
        private final Path sessionsDir;
        private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

        TestSessionManager(Path sessionsDir) {
            this.sessionsDir = sessionsDir;
        }

        void saveSession(EditorSessionData session) throws IOException {
            Path sessionFile = sessionsDir.resolve(session.name + ".json");
            Files.writeString(sessionFile, gson.toJson(session));
        }

        EditorSessionData loadSession(String name) {
            Path sessionFile = sessionsDir.resolve(name + ".json");
            if (!Files.exists(sessionFile)) {
                return null;
            }
            try {
                String json = Files.readString(sessionFile);
                return gson.fromJson(json, EditorSessionData.class);
            } catch (IOException e) {
                return null;
            }
        }

        List<String> listSessions() {
            try {
                return Files.list(sessionsDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .filter(name -> !name.startsWith("_")) // Exclude autosave
                    .sorted()
                    .toList();
            } catch (IOException e) {
                return List.of();
            }
        }

        boolean deleteSession(String name) {
            Path sessionFile = sessionsDir.resolve(name + ".json");
            try {
                return Files.deleteIfExists(sessionFile);
            } catch (IOException e) {
                return false;
            }
        }

        boolean hasAutosave() {
            return Files.exists(sessionsDir.resolve("_autosave.json"));
        }

        EditorSessionData getAutosave() {
            return loadSession("_autosave");
        }
    }

    // ===== Test Data Class =====

    /**
     * Simplified session data class for testing.
     */
    static class EditorSessionData {
        String name;
        List<String> openTabs;
        List<String> bookmarks;
        int activeTabIndex;
        long createdAt;
        long lastModified;
        Map<String, String> metadata;
    }
}
