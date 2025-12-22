# ISOTOPE

**Worldgen & loot introspection toolkit for Minecraft modpacks**

[![Build](https://github.com/halocore099/isotope/actions/workflows/build.yml/badge.svg)](https://github.com/halocore099/isotope/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://minecraft.net)

ISOTOPE allows pack developers to analyze, visualize, and rebalance all structures and their loot - across vanilla and modded Minecraft - without modifying mods or writing scripts.

> **Warning:** This is a developer tool. Not intended for survival gameplay.

## What Problem ISOTOPE Solves

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

**ISOTOPE exists to make worldgen observable.**

## Features

### Discovery
- Enumerate all registered structures (vanilla + modded)
- Detect containers placed by structures
- Capture loot table assignments and runtime calls

### Visualization
- Structure gallery world for spatial inspection
- Advancement-style UI for structure/loot tree navigation
- Clear labeling of all observed data

### Editing & Export
- Remove/add items from loot tables
- Enable/disable structures
- Export changes as vanilla-compatible datapacks

## Installation

### Requirements
- Minecraft 1.21.4
- Fabric Loader 0.16.9+ **or** NeoForge 21.4+
- Architectury API 15.0.3+

### Download
- [Modrinth](https://modrinth.com/mod/isotope) (coming soon)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/isotope) (coming soon)
- [GitHub Releases](https://github.com/halocore099/isotope/releases)

## Usage

1. Launch Minecraft with ISOTOPE installed
2. From the main menu, click **"Run Worldgen Analysis"**
3. Wait for analysis to complete
4. Browse structures and loot in the ISOTOPE UI
5. Make adjustments as needed
6. Export your changes as a datapack

## Important Disclaimer

**Data shown by ISOTOPE is "Observed, not guaranteed."**

ISOTOPE captures loot tables as they are used during structure generation. It cannot predict:
- All possible random rolls
- Conditional loot (locked behind game state)
- Programmatic loot not triggered during analysis

Use ISOTOPE as a high-confidence starting point, not an absolute truth.

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
