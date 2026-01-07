# Changelog

All notable changes to Isotope IDE will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2025-01-07

### Added

#### Advanced Tools
- Command Palette (Ctrl+P) for quick access to all commands
- Activity Bar with VS Code-style panel switching (keys 1-5)
- Context menus on right-click for entries and pools
- Drop Simulator for simulating 100-10,000 loot rolls
- Quick Fix Wizards (10 one-click balancing operations)
- Validation Panel with issue detection and auto-fix
- Export Validation Report (Markdown, JSON, Text formats)
- KubeJS Export for modpack distribution

#### Bulk Operations
- Scale Weights (multiply all weights by factor)
- Scale Counts (multiply all counts by factor)
- Remove Empty Pools (cleanup)
- Normalize Weights (sum to 100 per pool)

#### Keyboard Shortcuts
- Alt+Up/Down to move entries up/down
- Panel switching with keys 1-5

#### Validation
- Activity Bar badge showing issue count
- Auto-fix for Unreachable Entry (all entries have 0 weight)

### Improved
- Enhanced context menus with Move Up/Down, Duplicate, Clear Pool
- Better inline editing with text input for weights and counts
- Custom template support (create, save, edit, delete templates)

### Fixed
- Screen rendering order for MC 1.21+ compatibility
- Search bar focus management
- Keyboard shortcuts not intercepting backspace in text fields

---

## [0.1.0] - 2024-12-25

### Added

#### Core Features
- Registry discovery for structures and loot tables
- Automatic structure-to-loot-table linking (34 vanilla structures, 108 links)
- Pre-parsing of 1200+ loot tables on startup
- Search index for finding items across all tables

#### Loot Table Browser
- 3-panel layout (namespaces, tables, details)
- Filter by namespace/mod or category
- Category badges (CHEST, ENTITY, BLOCK, GAMEPLAY, etc.)
- Bookmark system for frequently accessed tables

#### Visual Loot Table Editor
- Multi-tab editing interface
- Pool and entry visualization
- Inline weight and count editing
- Add/remove items, pools, functions, and conditions
- Live drop rate visualization with bar charts
- Diff view showing changes vs original

#### Edit Operations
- Full undo/redo support with history log
- Copy/paste entries between tables
- Multi-selection with Ctrl+Click and Shift+Click
- Batch operations on selected entries
- Entry templates for quick item addition

#### Session Management
- Save/load editing sessions
- Persistent bookmarks
- UI state preservation

#### Export & Import
- Export as vanilla-compatible Minecraft datapack
- Import edits from existing datapacks
- Copy loot table JSON to clipboard
- Compare mode for side-by-side table comparison

#### UI/UX
- Vanilla-styled UI matching Minecraft aesthetics
- Keyboard shortcuts (F1 for help overlay)
- Tooltips on all toolbar buttons
- Confirmation dialogs for destructive actions

### Technical
- Architectury multi-loader setup (Fabric + NeoForge)
- Supports Minecraft 1.21, 1.21.1, 1.21.4
- Invisible temp world for registry scanning from main menu

---

[0.1.1]: https://github.com/halocore099/isotope/releases/tag/v0.1.1
[0.1.0]: https://github.com/halocore099/isotope/releases/tag/v0.1.0
