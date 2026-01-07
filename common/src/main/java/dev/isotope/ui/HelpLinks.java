package dev.isotope.ui;

import dev.isotope.Isotope;
import net.minecraft.Util;

import java.net.URI;

/**
 * Utility class for opening documentation links.
 *
 * Provides easy access to help documentation for complex features.
 */
public final class HelpLinks {

    // Base URL for documentation
    private static final String DOCS_BASE = "https://github.com/halocore099/isotope/blob/main/USAGE.md";

    // Section anchors
    public static final String GETTING_STARTED = DOCS_BASE + "#getting-started";
    public static final String INTERFACE_OVERVIEW = DOCS_BASE + "#interface-overview";
    public static final String COMMAND_PALETTE = DOCS_BASE + "#command-palette";
    public static final String BROWSING_LOOT_TABLES = DOCS_BASE + "#browsing-loot-tables";
    public static final String EDITING_LOOT_TABLES = DOCS_BASE + "#editing-loot-tables";
    public static final String BATCH_OPERATIONS = DOCS_BASE + "#batch-operations";
    public static final String ANALYSIS_TOOLS = DOCS_BASE + "#analysis-tools";
    public static final String DROP_SIMULATOR = DOCS_BASE + "#drop-simulator";
    public static final String QUICK_FIX_WIZARDS = DOCS_BASE + "#quick-fix-wizards";
    public static final String BULK_OPERATIONS = DOCS_BASE + "#bulk-operations";
    public static final String VALIDATION = DOCS_BASE + "#validation";
    public static final String IN_WORLD_TESTING = DOCS_BASE + "#in-world-testing";
    public static final String LOCATE_TELEPORT = DOCS_BASE + "#locate--teleport";
    public static final String TEST_ARENA = DOCS_BASE + "#test-arena";
    public static final String EXPORTING_CHANGES = DOCS_BASE + "#exporting-changes";
    public static final String DATAPACK_EXPORT = DOCS_BASE + "#datapack-export";
    public static final String DATAPACK_IMPORT = DOCS_BASE + "#importing-from-datapacks";
    public static final String KUBEJS_EXPORT = DOCS_BASE + "#kubejs-export";
    public static final String SESSIONS = DOCS_BASE + "#sessions-and-workflow";
    public static final String KEYBOARD_SHORTCUTS = DOCS_BASE + "#keyboard-shortcuts";
    public static final String TEMPLATES = DOCS_BASE + "#using-templates";
    public static final String TEST_MODE = DOCS_BASE + "#test-mode";

    // External links
    public static final String GITHUB_REPO = "https://github.com/halocore099/isotope";
    public static final String GITHUB_ISSUES = "https://github.com/halocore099/isotope/issues";

    private HelpLinks() {}

    /**
     * Open a URL in the system browser.
     *
     * @param url The URL to open
     */
    public static void open(String url) {
        try {
            Util.getPlatform().openUri(URI.create(url));
            Isotope.LOGGER.info("Opened help link: {}", url);
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to open URL {}: {}", url, e.getMessage());
        }
    }

    /**
     * Open the main documentation page.
     */
    public static void openDocs() {
        open(DOCS_BASE);
    }

    /**
     * Open the GitHub issues page for bug reports/feature requests.
     */
    public static void openIssues() {
        open(GITHUB_ISSUES);
    }
}
