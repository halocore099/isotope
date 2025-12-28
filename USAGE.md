# Isotope IDE Usage Guide

A comprehensive guide to using Isotope IDE for modpack loot table editing and analysis.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Interface Overview](#interface-overview)
3. [Browsing Loot Tables](#browsing-loot-tables)
4. [Editing Loot Tables](#editing-loot-tables)
5. [Batch Operations](#batch-operations)
6. [Analysis Tools](#analysis-tools)
7. [Exporting Changes](#exporting-changes)
8. [Sessions and Workflow](#sessions-and-workflow)
9. [Keyboard Shortcuts](#keyboard-shortcuts)
10. [Troubleshooting](#troubleshooting)

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

The main interface is divided into several panels:

### Left Panel: Loot Table Browser

- **Namespace Filter**: Filter tables by mod (minecraft, modname, etc.)
- **Search Bar**: Search for tables by name
- **Category Badges**: Shows table type (CHEST, ENTITY, BLOCK, etc.)
- **Table List**: All loot tables matching current filters

### Center Panel: Tab Bar

- Open multiple loot tables simultaneously in tabs
- Click a tab to switch between tables
- Click X on a tab to close it
- Modified tables show an indicator

### Right Panel: Edit Panel

- View and edit the currently selected loot table
- Shows pools, entries, weights, and counts
- Inline editing for all values

### Toolbar Buttons

| Button | Function |
|--------|----------|
| Test Mode | Toggle live testing of your edits in-game |
| Rates | Show/hide drop rate visualization panel |
| Diff | Show/hide comparison with original table |
| History | Show/hide edit history log |
| Export | Open export options dialog |

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

1. Click "Template" next to "+ Add Item"
2. Choose a preset configuration:
   - Common Item (weight 10, count 1-3)
   - Uncommon Item (weight 5, count 1-2)
   - Rare Item (weight 1, count 1)
   - Food Stack (weight 10, count 2-5)
   - Enchanted Gear (random enchantments)
   - And more
3. Select the item to add

### Removing Entries

1. Click the X button on an entry row
2. Or select entries and press Delete

### Undo/Redo

- Press Ctrl+Z to undo the last change
- Press Ctrl+Y to redo
- Full history is preserved for the session

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
| F1 | Show keyboard shortcuts overlay |
| Escape | Close current overlay/cancel edit |

### Editing

| Shortcut | Action |
|----------|--------|
| Ctrl+Z | Undo last change |
| Ctrl+Y | Redo |
| Ctrl+S | Export as datapack |
| Ctrl+C | Copy selected entry |
| Ctrl+V | Paste entry |
| Delete | Remove selected entry(s) |

### Selection

| Shortcut | Action |
|----------|--------|
| Ctrl+Click | Toggle entry selection |
| Shift+Click | Select range of entries |

### Navigation

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+F | Open global search |

### Inline Editing

| Key | Action |
|-----|--------|
| Enter | Confirm edit |
| Escape | Cancel edit |
| Backspace | Delete character before cursor |
| Delete | Delete character after cursor |
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
