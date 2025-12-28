# Isotope IDE

**Visual loot table editor and worldgen analysis toolkit for modpack developers**

[![Build](https://github.com/halocore099/isotope/actions/workflows/build.yml/badge.svg)](https://github.com/halocore099/isotope/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)](https://minecraft.net)
[![Modrinth](https://img.shields.io/modrinth/dt/isotope-ide?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/isotope-ide)
[![CurseForge](https://img.shields.io/curseforge/dt/isotope-ide?logo=curseforge&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/isotope-ide)

Isotope IDE allows pack developers to analyze, visualize, and rebalance all structures and their loot - across vanilla and modded Minecraft - without modifying mods or writing scripts.

> **Warning:** This is a developer tool. Not intended for survival gameplay.

## What Problem Isotope IDE Solves

Modern Minecraft modpacks suffer from a fundamental tooling gap:

- Structures are distributed across many mods
- Worldgen is procedural, jigsaw-based, and runtime-driven
- Loot tables are hidden, reused, or generated programmatically
- There is no global view of what structures exist or what rewards they give

This makes it extremely difficult to:
- Balance exploration rewards
- Establish clear progression
- Prevent loot redundancy
- Make structures feel meaningful

**Isotope IDE exists to make worldgen observable.**

## Features

### Loot Table Browser
- Browse all registered loot tables (vanilla + modded)
- Filter by namespace/mod or category (chest, entity, block, etc.)
- Bookmarks for frequently accessed tables
- Global search across all tables by item name

### Visual Loot Table Editor
- Multi-tab editing with full undo/redo support
- Inline weight and count editing
- Add/remove items and pools with one click
- Copy/paste entries between tables
- Live drop rate visualization
- Diff view showing changes vs original

### Analysis Engine
- Pre-parses all 1200+ loot tables on startup
- Structure-to-loot-table linking (34 vanilla structures)
- Search index for finding items across all tables
- Links structures to their associated loot tables

### Export & Import
- Export edits as vanilla-compatible Minecraft datapack
- Import edits from existing datapacks
- Copy loot table JSON to clipboard
- Session save/load for persistent workflows

### Keyboard Shortcuts
| Shortcut | Action |
|----------|--------|
| F1 | Show keyboard shortcuts help |
| Ctrl+Z / Ctrl+Y | Undo / Redo |
| Ctrl+S | Export as datapack |
| Ctrl+Shift+F | Global search |
| Ctrl+C / Ctrl+V | Copy / Paste entry |
| Delete | Remove selected entry(s) |
| Ctrl+Click | Toggle entry selection |
| Shift+Click | Select range of entries |

### Entry Templates
Quickly add pre-configured loot entries with the Template button:
- **Common Item** - Weight 10, count 1-3
- **Uncommon Item** - Weight 5, count 1-2
- **Rare Item** - Weight 1, count 1
- **Food Stack** - Weight 10, count 2-5
- **Enchanted Gear** - Random enchantments
- **Treasure** - Diamond with weight 1
- And more...

## Installation

### Requirements
- Minecraft 1.21, 1.21.1, or 1.21.4
- Fabric Loader 0.16.0+ **or** NeoForge 21.4+
- Architectury API 15.0.0+

### Download
- [Modrinth](https://modrinth.com/mod/isotope-ide)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/isotope-ide)
- [GitHub Releases](https://github.com/halocore099/isotope/releases)

## Usage

For a comprehensive guide, see [USAGE.md](USAGE.md).

### Getting Started
1. Launch Minecraft with Isotope IDE installed
2. Click **"ISOTOPE"** button at bottom of title screen
3. Confirm the developer warning
4. Wait for registry scan (happens once, ~2 seconds)
5. Browse loot tables in the main interface

### Editing Workflow
1. Click a loot table to open it in a tab
2. Click weight/count values to edit inline
3. Use "+ Add Item" to add entries to pools
4. Use "Rates" panel to visualize drop probabilities
5. Enable "Test Mode" to try changes in-game
6. Click "Export" to save as datapack

### Accessing Isotope IDE
- **Title Screen**: Click the "ISOTOPE" button (bottom of screen)
- **In-Game**: Press Escape, click "ISOTOPE" button

## Important Disclaimer

**Data shown by Isotope IDE is "Observed, not guaranteed."**

Isotope IDE captures loot tables as they are used during structure generation. It cannot predict:
- All possible random rolls
- Conditional loot (locked behind game state)
- Programmatic loot not triggered during analysis

Use Isotope IDE as a high-confidence starting point, not an absolute truth.

## Building from Source

```bash
git clone https://github.com/halocore099/isotope.git
cd isotope
./gradlew build
```

Artifacts will be in:
- `fabric/build/libs/` - Fabric mod
- `neoforge/build/libs/` - NeoForge mod

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Built with [Architectury](https://github.com/architectury/architectury-api)
- Inspired by the need for better modpack tooling
