# Isotope IDE Usage Guide

A comprehensive guide to using Isotope IDE for modpack loot table editing and analysis.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Interface Overview](#interface-overview)
3. [Command Palette](#command-palette)
4. [Browsing Loot Tables](#browsing-loot-tables)
5. [Editing Loot Tables](#editing-loot-tables)
6. [Batch Operations](#batch-operations)
7. [Analysis Tools](#analysis-tools)
8. [Advanced Tools](#advanced-tools)
   - [Drop Simulator](#drop-simulator)
   - [Quick Fix Wizards](#quick-fix-wizards)
   - [Bulk Operations](#bulk-operations)
   - [Validation](#validation)
9. [Exporting Changes](#exporting-changes)
10. [Sessions and Workflow](#sessions-and-workflow)
11. [Keyboard Shortcuts](#keyboard-shortcuts)
12. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Installation

1. Install Minecraft 1.21, 1.21.1, or 1.21.4
2. Install Fabric Loader 0.16.0+ or NeoForge 21.4+
3. Install Architectury API 15.0.0+
4. Download Isotope IDE from Modrinth or CurseForge
5. Place the jar in your mods folder

### First Launch

1. Launch Minecraft with Isotope IDE installed
2. On the title screen, click the **ISOTOPE** button at the bottom
3. Read and confirm the developer warning dialog
4. Wait for the registry scan to complete (approximately 2-3 seconds)
5. The main editor interface will open

The registry scan happens once per game launch and indexes all loot tables from vanilla and installed mods.

### Accessing Isotope IDE

- **From Title Screen**: Click the ISOTOPE button below the main menu buttons
- **From Pause Menu**: While in a world, press Escape and click the ISOTOPE button

---

## Interface Overview

The main interface uses a VS Code-inspired layout with several panels:

### Activity Bar (Left Edge)

The vertical bar on the far left provides quick access to different panels:

| Icon | Panel | Shortcut |
|------|-------|----------|
| ☰ | Browser | 1 |
| ⌕ | Global Search | 2 |
| ≡ | Analysis | 3 |
| ⎘ | History | 4 |
| ✔ | Validation | 5 |

### Left Panel

Changes based on Activity Bar selection:
- **Browser**: Loot table browser with namespace filter and search
- **Search**: Global item search across all loot tables
- **Analysis**: Drop rate visualization and diff view
- **History**: Edit history log with timestamps
- **Validation**: Issue detection with auto-fix for current table

### Tab Bar

- Open multiple loot tables simultaneously in tabs
- Click a tab to switch between tables
- Click X on a tab to close it
- Modified tables show an indicator

### Edit Panel (Right Side)

- View and edit the currently selected loot table
- Shows pools, entries, weights, and counts
- Inline editing for all values
- Right-click for context menu

### Header Toolbar

| Button | Function |
|--------|----------|
| ↶ | Undo (Ctrl+Z) |
| ↷ | Redo (Ctrl+Y) |
| Test ○/● | Toggle live testing of edits in-game |
| Export | Open export options dialog |

### Status Bar

Shows current table name and unsaved edit count.

---

## Command Palette

The Command Palette provides quick access to all Isotope features. Press **Ctrl+P** to open it.

### Using the Command Palette

1. Press **Ctrl+P** anywhere in the editor
2. Type to filter commands (fuzzy search supported)
3. Use arrow keys to navigate
4. Press Enter to execute the selected command
5. Press Escape to close

### Available Commands

| Command | Description |
|---------|-------------|
| **File Operations** | |
| Export as Datapack | Export edited tables as a Minecraft datapack |
| Copy JSON to Clipboard | Copy current table's JSON |
| Import from Datapack... | Import tables from an existing datapack |
| **Edit Operations** | |
| Undo / Redo | Undo or redo changes |
| Copy / Paste Entry | Copy and paste loot entries |
| Delete Selected | Remove selected entries |
| Duplicate Entry | Duplicate the selected entry |
| **View Operations** | |
| Toggle Test Mode | Enable/disable live loot testing |
| Show Browser/Search/Analysis/History/Validation Panel | Switch left panel view |
| Global Item Search | Search for items across all tables |
| **Tools** | |
| Simulate Drops... | Run drop simulation on current table |
| Quick Fix Wizards... | Apply one-click balancing fixes |
| Bulk Operations... | Apply changes across multiple tables |
| Validate Loot Table | Check for issues in current table |
| Validate All Edited Tables | Check all edited tables for issues |
| Export as KubeJS Script | Export edits as KubeJS server script |
| Compare Two Tables... | Side-by-side table comparison |
| Manage Sessions... | Save and load work sessions |
| **Help** | |
| Keyboard Shortcuts | Show all keyboard shortcuts (F1) |

---

## Browsing Loot Tables

### Filtering by Namespace

1. Click the namespace dropdown in the left panel
2. Select a mod name to show only that mod's tables
3. Select "All" to show all namespaces

### Searching

1. Click the search bar in the left panel
2. Type part of a loot table name
3. Results filter in real-time
4. Press Escape or clear the search to reset

### Global Search (Ctrl+Shift+F)

Search for specific items across all loot tables:

1. Press Ctrl+Shift+F to open global search
2. Type an item name (e.g., "diamond", "iron_ingot")
3. Results show which loot tables contain that item
4. Click a result to open that table

### Bookmarks

Mark frequently used tables for quick access:

1. Click the star icon next to a table name
2. Bookmarked tables appear in a separate section
3. Click the star again to remove the bookmark

---

## Editing Loot Tables

### Opening a Table

1. Click a loot table in the browser
2. The table opens in a new tab
3. You can have multiple tables open simultaneously

### Understanding the Display

Each loot table shows:

- **Pools**: Groups of entries that roll together
- **Entries**: Individual items or references within a pool
- **Weight (W:)**: Relative chance of this entry being selected
- **Quantity (Qty:)**: Number of items dropped (min-max range)

### Editing Weight

1. Click on the weight value box (next to W:)
2. The box highlights with a gold border
3. Type the new weight value (integers only, 1-9999)
4. Press Enter to confirm, or Escape to cancel

### Editing Count/Quantity

1. Click on the min or max count box (next to Qty:)
2. Type the new value (integers only)
3. Press Enter to confirm
4. If min exceeds max, they auto-adjust

### Adding Items

1. Click "+ Add Item" at the bottom of a pool
2. Select an item from the picker
3. The item is added with default weight 10 and count 1

### Using Templates

Templates are pre-configured entry settings that make adding common item types faster.

#### Built-in Templates

1. Click "Template" next to "+ Add Item"
2. Choose a preset configuration:
   - Common Item (weight 10, count 1-3)
   - Uncommon Item (weight 5, count 1-2)
   - Rare Item (weight 1, count 1)
   - Food Stack (weight 10, count 2-5)
   - Enchanted Gear (random enchantments)
   - Treasure, Emerald Stack, Arrow Stack, and more
3. If the template has no default item, select an item from the picker
4. The entry is added with all template settings applied

#### Custom Templates

Create your own templates for items and configurations you use frequently.

**Creating from an Existing Entry:**

1. Right-click any entry in the edit panel
2. The Template Editor opens pre-filled with that entry's settings
3. Enter a name, description, and category
4. Adjust weight, count, or functions as needed
5. Click "Save" to save the template

**Creating from Scratch:**

1. Click "Template" next to "+ Add Item"
2. Click "Manage..." at the bottom of the template picker
3. Click "New Template"
4. Fill in the template details:
   - **Name**: Display name for the template
   - **Description**: Short description of what it's for
   - **Category**: Grouping for organization (e.g., "Custom", "Resources")
   - **Item**: (Optional) Default item to use
   - **Weight**: Default weight value
   - **Count**: Constant or range (min-max)
   - **Functions**: Add functions like set_count, enchant, etc.
5. Click "Save"

**Managing Custom Templates:**

1. Open the template picker (click "Template")
2. Click "Manage..." to open the Template Manager
3. View all your custom templates
4. Click "E" (Edit) to modify a template
5. Click "X" (Delete) to remove a template
6. Use keyboard shortcuts: Enter to edit, Delete to remove

Custom templates are saved to `.minecraft/isotope/templates.json` and persist across game sessions.

#### Visual Distinction

- Built-in templates appear with standard styling
- Custom templates show a gold "CUSTOM" badge
- Custom template categories are highlighted in gold

### Removing Entries

1. Click the X button on an entry row
2. Or select entries and press Delete

### Undo/Redo

- Press Ctrl+Z to undo the last change
- Press Ctrl+Y to redo
- Full history is preserved for the session

### Context Menus

Right-click on entries and pools for quick access to common actions.

**Entry Context Menu** (right-click on an item entry):

| Action | Description |
|--------|-------------|
| Copy | Copy entry to clipboard (Ctrl+C) |
| Paste | Paste entry from clipboard (Ctrl+V) |
| Move Up | Move entry up in the pool |
| Move Down | Move entry down in the pool |
| Duplicate | Create a copy of this entry (Ctrl+D) |
| Delete | Remove this entry (Delete) |
| Save as Template... | Save entry settings as a reusable template |

**Pool Context Menu** (right-click on pool header):

| Action | Description |
|--------|-------------|
| Add Item... | Open item picker to add a new entry |
| Add from Template... | Add entry using a template |
| Duplicate Pool | Create a copy of the entire pool |
| Clear Pool | Remove all entries from the pool |
| Delete Pool | Remove the pool entirely |
| Add New Pool | Create a new empty pool |

---

## Batch Operations

### Multi-Selection

Select multiple entries for batch operations:

- **Ctrl+Click**: Toggle selection on an entry
- **Shift+Click**: Select a range from last clicked entry

Selected entries are highlighted in purple.

### Batch Actions

When multiple entries are selected, a batch action bar appears:

- **Set Weight**: Apply the same weight to all selected entries
- **Delete All**: Remove all selected entries (with confirmation)

---

## Analysis Tools

### Drop Rate Panel

Visualize the probability of each item dropping:

1. Click "Rates" in the toolbar
2. The panel shows calculated drop percentages
3. Bar charts display relative probabilities
4. Hover for exact percentages

### Diff View

Compare your edits against the original table:

1. Click "Diff" in the toolbar
2. See additions (green), removals (red), and modifications (yellow)
3. Useful for reviewing changes before export

### History Log

Track all changes made during the session:

1. Click "History" in the toolbar
2. See timestamped list of all operations
3. Useful for understanding what was changed

---

## Advanced Tools

Access these tools via the Command Palette (Ctrl+P) or their respective shortcuts.

### Drop Simulator

Simulate loot drops to verify your changes produce expected results.

**Opening the Simulator:**
1. Select a loot table in the editor
2. Press Ctrl+P and type "Simulate" or select "Simulate Drops..."

**Using the Simulator:**
1. Choose the number of rolls: 100, 1,000, 5,000, or 10,000
2. Click "Run Simulation"
3. Wait for the simulation to complete

**Understanding Results:**
- **Simulated %**: Actual drop rate from the simulation
- **Theoretical %**: Calculated probability based on weights
- **Bar Charts**: Visual comparison of simulated vs theoretical
- Large differences may indicate issues with your configuration

**Tips:**
- More rolls = more accurate results (10,000 recommended for rare items)
- Compare original vs edited tables to see the impact of changes
- Use for balancing: aim for specific drop rates

### Quick Fix Wizards

One-click operations for common loot table balancing tasks.

**Opening Quick Fix:**
1. Select a loot table in the editor
2. Press Ctrl+P and type "Quick Fix" or select "Quick Fix Wizards..."

**Available Fixes:**

| Fix | Description |
|-----|-------------|
| Balance Weights | Normalize all entry weights to sum to 100 |
| Nerf Rare Items | Halve weight of items with <5% drop rate |
| Buff Rare Items | Double weight of items with <5% drop rate |
| Nerf Common Items | Halve weight of items with >50% drop rate |
| Buff Common Items | Double weight of items with >50% drop rate |
| Cap Quantity | Set max item count to 3 for all entries |
| Double Quantity | Double all item counts |
| Halve Quantity | Halve all item counts |
| Remove Enchanted | Remove enchantment functions from all entries |
| Remove Duplicates | Remove duplicate item entries per pool |

**Using Quick Fix:**
1. Select a fix from the left panel
2. Preview shows the changes that will be made
3. Click "Apply Fix" to apply the changes
4. Changes can be undone with Ctrl+Z

### Bulk Operations

Apply changes across ALL loot tables at once - powerful for modpack-wide adjustments.

**Opening Bulk Operations:**
1. Press Ctrl+P and type "Bulk" or select "Bulk Operations..."
2. No table needs to be selected

**Available Operations:**

| Operation | Description |
|-----------|-------------|
| Remove Item | Remove a specific item from all loot tables |
| Replace Item | Replace one item with another across all tables |
| Scale Weights | Multiply all entry weights by a factor |
| Scale Counts | Multiply all item counts by a factor |
| Remove Empty | Remove all empty pools from all tables (cleanup) |
| Normalize | Set all weights to sum to 100 per pool |

**Using Bulk Operations:**
1. Select an operation type (three rows of operation buttons)
2. For item operations: Enter the item ID (e.g., `minecraft:diamond`)
3. For Replace: also enter the replacement item ID
4. For Scale operations: Enter a scale factor (e.g., 2.0 = double, 0.5 = halve)
5. For cleanup operations (Remove Empty, Normalize): No parameters needed
5. Click "Preview" to see affected tables
6. Review the list of changes
7. Click "Apply" to execute

**Use Cases:**
- Remove overpowered mod items from all loot tables
- Replace one resource with another across your modpack
- Scale weights to make all items more/less common
- Scale counts to increase/decrease drop quantities globally
- Clean up empty pools left over from editing
- Normalize weights for easier probability understanding
- Standardize loot across different structures

**Warning:** Bulk operations affect many tables. Always preview before applying and use Test Mode to verify changes in-game.

### Validation

Check loot tables for potential issues and errors with automatic fix suggestions.

#### Validation Panel

Access the dedicated validation panel for the current table:

1. Press **5** or click the checkmark icon in the Activity Bar
2. The panel shows all issues for the currently selected table
3. Issues update automatically when you make edits
4. Click an issue to navigate to the affected pool/entry

#### Running One-Time Validation

For quick validation without switching panels:

1. Select a loot table in the editor
2. Press Ctrl+P and type "Validate" or select "Validate Loot Table"
3. A toast notification shows the summary

#### Validate All Edited Tables

Check all tables you've edited at once:

1. Press Ctrl+P and type "Validate All" or select "Validate All Edited Tables"
2. All edited tables are validated
3. Summary toast shows total errors and warnings across all tables
4. Detailed issues logged to game console (F3+T to view)

**Issue Types Detected:**

| Issue | Severity | Description |
|-------|----------|-------------|
| Empty Pool | Warning | Pool has no entries |
| Zero Weight | Warning | Entry has weight of 0 |
| Zero Rolls | Warning | Pool always rolls 0 times |
| Missing Item | Error | Item does not exist in registry |
| Duplicate Entry | Info | Same item appears multiple times |
| Negative Count | Error | Item count can be negative |
| Unreachable Entry | Error | Entry can never be selected |
| Conflicting Functions | Warning | Multiple functions conflict |

**Severity Levels:**
- **Error** (Red): Will cause problems, should be fixed
- **Warning** (Orange): Might cause problems, review recommended
- **Info** (Blue): Informational, may be intentional

#### Auto-Fix

Some issues can be automatically fixed with one click:

| Issue | Auto-Fix Action |
|-------|-----------------|
| Zero Weight | Set weight to 1 |
| Zero Rolls | Set rolls to 1 |
| Empty Pool | Remove the empty pool |
| Unreachable Entry | Set first entry weight to 1 |

**Using Auto-Fix:**
1. Open the Validation panel (press 5)
2. Look for issues with a green "Fix" button
3. Click "Fix" to apply the automatic correction
4. The table is updated and validation refreshes
5. A toast confirms the fix was applied

**Note:** Not all issues can be auto-fixed. Issues like Missing Item or Duplicate Entry require manual review since the correct fix depends on your intent.

#### Validation Badge

The Activity Bar shows a red badge on the Validation icon when the current table has issues:
- The badge shows the count of errors + warnings
- Badge updates automatically when you make edits or switch tables
- A quick glance tells you if there are problems to address

#### Export Validation Report

Generate a comprehensive report of all validation issues:

1. Press Ctrl+P and type "Export Validation Report"
2. A Markdown report is generated in `<game>/isotope-reports/`
3. The report includes:
   - Summary statistics (tables validated, errors, warnings)
   - Issues grouped by table
   - Severity, type, message, and location for each issue

**Report Formats:**
- Markdown (.md) - Default, best for reading and sharing
- JSON (.json) - Machine-readable for scripts
- Plain text (.txt) - Simple format for logs

---

## Exporting Changes

### Export Dialog

1. Click "Export" in the toolbar
2. Choose export options:
   - Export Structures (observed structure data)
   - Export Loot Tables (analyzed loot table data)
   - Export Structure-Loot Links
   - Export Sample Data
   - Timestamped Folder (creates dated subfolder)
3. Set custom export path if desired
4. Click "Export to JSON" or "Export Datapack"

### Datapack Export

Creates a valid Minecraft datapack with your edited loot tables:

1. Click "Export Datapack"
2. The datapack is created in `<game>/isotope-datapacks/`
3. Copy the folder to your world's `datapacks/` directory
4. Run `/reload` in-game to apply changes

### KubeJS Export

Export your edits as KubeJS server scripts for modpack distribution.

**What is KubeJS?**
KubeJS is a popular modding tool that lets you modify game content via JavaScript without creating full mods. Many modpack creators prefer KubeJS for easier maintenance and distribution.

**Exporting to KubeJS:**
1. Make your edits to loot tables
2. Press Ctrl+P and select "Export as KubeJS Script"
3. The script is saved to `<game>/kubejs/server_scripts/`

**Output Format:**
```javascript
ServerEvents.lootTables(event => {
    event.modify('minecraft:chests/simple_dungeon', loot => {
        loot.clearPools();
        loot.addPool(pool => {
            pool.rolls = [1, 3];
            pool.addItem('minecraft:diamond', 10);
            pool.addItem('minecraft:emerald', 5).count([1, 3]);
        });
    });
});
```

**Requirements:**
- KubeJS mod must be installed to use the exported scripts
- Scripts work on both client and dedicated servers

**Advantages over Datapacks:**
- Easier to read and modify manually
- Can be version-controlled more easily
- Integrates with other KubeJS scripts
- Familiar syntax for JavaScript developers

### Testing Changes

Use Test Mode to preview changes without exporting:

1. Click "Test Mode" to enable (indicator turns green)
2. Open chests and kill mobs to see modified loot
3. Changes only affect loot generation, not saved tables
4. Disable Test Mode when done testing

---

## Sessions and Workflow

### Saving Sessions

Preserve your work across game restarts:

1. Open the Session menu
2. Click "Save Session"
3. Enter a name for the session
4. Your open tabs, edits, and bookmarks are saved

### Loading Sessions

1. Open the Session menu
2. Select a saved session
3. Click "Load"
4. All previous state is restored

### Recommended Workflow

1. **Analyze**: Browse tables, use global search to find items of interest
2. **Plan**: Identify which tables need changes
3. **Edit**: Make changes with inline editing
4. **Test**: Enable Test Mode, verify in-game
5. **Export**: Create datapack when satisfied
6. **Save Session**: Preserve work for future iterations

---

## Keyboard Shortcuts

### General

| Shortcut | Action |
|----------|--------|
| Ctrl+P | Open Command Palette |
| F1 | Show keyboard shortcuts overlay |
| F5 | Toggle Test Mode |
| Escape | Close overlay / Cancel edit / Clear selection |

### Panel Switching

| Shortcut | Action |
|----------|--------|
| 1 | Show Browser panel |
| 2 | Show Global Search panel |
| 3 | Show Analysis panel |
| 4 | Show History panel |
| 5 | Show Validation panel |

### Editing

| Shortcut | Action |
|----------|--------|
| Ctrl+Z | Undo last change |
| Ctrl+Y | Redo |
| Ctrl+S | Open export dialog |
| Ctrl+C | Copy selected entry |
| Ctrl+V | Paste entry |
| Ctrl+D | Duplicate selected entry |
| Delete | Remove selected entry(s) |
| Right-click | Open context menu / Save as template |

### Selection

| Shortcut | Action |
|----------|--------|
| Ctrl+Click | Toggle entry selection |
| Shift+Click | Select range of entries |

### Navigation

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+F | Open global search |
| Up/Down | Navigate entries |
| Alt+Up | Move selected entry up |
| Alt+Down | Move selected entry down |
| Enter | Select/confirm |

### Inline Editing

| Key | Action |
|-----|--------|
| Click value | Start editing |
| Enter | Confirm edit |
| Escape | Cancel edit |
| Backspace | Delete character before cursor |
| 0-9 | Type digits (integers only) |
| Left/Right | Move cursor |
| Home/End | Jump to start/end |

---

## Troubleshooting

### Registry Scan Takes Too Long

- This is normal on first launch with many mods
- Subsequent launches use cached data
- Large modpacks (200+ mods) may take 5-10 seconds

### Changes Not Appearing In-Game

1. Ensure Test Mode is enabled
2. Changes only affect newly generated loot
3. Already-filled chests are not affected
4. Try generating new chunks or respawning mobs

### Datapack Not Working

1. Verify the datapack is in `world/datapacks/`
2. Run `/reload` command
3. Check `/datapack list` to verify it's enabled
4. Check game log for JSON parsing errors

### Loot Table Not Found

Some loot tables are generated at runtime and may not appear:

- Procedurally generated tables
- Tables only created when specific conditions are met
- Tables from mods that register late

### Values Not Saving

- Ensure you press Enter after editing
- Escape cancels the edit without saving
- Check that the value is within valid range (1-9999)

---

## Tips and Best Practices

### For Modpack Balancing

1. Start with vanilla tables to understand baseline
2. Use global search to find all sources of valuable items
3. Reduce weight of overpowered drops rather than removing them
4. Test changes in a creative world first
5. Document your changes using session saves

### For Performance

1. Close unused tabs to reduce memory usage
2. Use namespace filter to narrow down large table lists
3. Avoid editing while chunks are generating

### For Organization

1. Use bookmarks for tables you edit frequently
2. Save sessions with descriptive names
3. Create separate sessions for different balancing goals
4. Export datapacks with timestamped folders for versioning

---

## Getting Help

- Report bugs: https://github.com/halocore099/isotope/issues
- Feature requests: https://github.com/halocore099/isotope/issues
- Source code: https://github.com/halocore099/isotope
