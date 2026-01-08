# Isotope IDE

**The missing loot table editor for modpack developers.**

Isotope IDE lets you browse, analyze, and rebalance all loot tables across vanilla and modded Minecraft - without writing scripts or editing JSON files.

---

## The Problem

Modern modpacks have a loot problem:

- Structures are scattered across dozens of mods
- Loot tables are hidden in JAR files and datapacks
- There's no way to see what drops where
- Balancing rewards means hours of manual JSON editing

**Isotope IDE makes loot observable and editable.**

---

## Features

### Browse Everything
- **1200+ loot tables** scanned automatically on startup
- Filter by mod, category (chest, entity, block), or search by item
- See which structures use which loot tables
- Bookmark frequently-edited tables

### Visual Editor
- **Point-and-click editing** - no JSON knowledge required
- Multi-tab interface with full undo/redo (Ctrl+Z/Y)
- Inline weight and count editing
- Add items, pools, and functions with one click
- Copy/paste entries between tables

### Analysis Tools
- **Drop rate calculator** - see actual probabilities
- **Diff view** - compare your changes to original
- **Global search** - find any item across all tables
- **Structure linking** - see which structures use each table

### Export Options
- **Datapack export** - vanilla-compatible, drag into your pack
- **KubeJS export** - for script-based modpacks
- **Session save/load** - pick up where you left off
- **Clipboard copy** - paste JSON anywhere

### Keyboard-First Design
| Shortcut | Action |
|----------|--------|
| Ctrl+Z / Y | Undo / Redo |
| Ctrl+S | Export datapack |
| Ctrl+Shift+F | Global search |
| Ctrl+C / V | Copy / Paste |
| Delete | Remove entry |
| F1 | Show all shortcuts |

---

## Quick Start

1. Install Isotope IDE alongside your modpack
2. Launch Minecraft and click **ISOTOPE** on the title screen
3. Browse loot tables in the left panel
4. Click any table to open it in the editor
5. Make changes, then export as a datapack

That's it. No commands, no configs, no setup.

---

## Use Cases

**Modpack Developers**
- Balance loot progression across 50+ mods
- Remove overpowered items from early-game structures
- Add custom rewards to underutilized structures

**Server Admins**
- Quickly nerf problematic loot without touching mod files
- Create custom loot for events or quests
- Export changes as datapacks for easy deployment

**Mod Developers**
- Visualize your loot tables during development
- Compare your tables to vanilla for balance reference
- Test changes without restarting the game

---

## Requirements

- Minecraft **1.21**, **1.21.1**, or **1.21.4**
- **Fabric Loader 0.16.0+** or **NeoForge 21.4+**
- **Architectury API 15.0.0+**

---

## Important Notes

**This is a developer tool.** It's designed for modpack creation, not survival gameplay.

**"Observed, not guaranteed."** Isotope shows loot tables as they exist at runtime. It cannot predict conditional loot or programmatic generation.

---

## Links

- [GitHub](https://github.com/halocore099/isotope) - Source code and issues
- [Usage Guide](https://github.com/halocore099/isotope/blob/main/USAGE.md) - Comprehensive documentation
- [Changelog](https://github.com/halocore099/isotope/blob/main/CHANGELOG.md) - Version history

---

Made with Architectury. MIT Licensed.
