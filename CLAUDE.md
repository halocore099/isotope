# ISOTOPE - Claude Memory

## Branch Info

**Branch**: `1.21.x`
**Target**: Minecraft 1.21.0-1.21.11 with NeoForge and Fabric
**Java**: 21 (via Homebrew on macOS: `/opt/homebrew/opt/openjdk@21`)

This branch supports **multi-version builds** for all MC 1.21.x versions with both NeoForge and Fabric loaders.

## Multi-Version Support

### Supported Versions

| MC Version | API Group | NeoForge | Fabric | Status |
|------------|-----------|----------|--------|--------|
| 1.21.0 | mc1210 | 21.0.x | 0.16.x | Supported |
| 1.21.1 | mc1210 | 21.1.x | 0.16.x | Supported |
| 1.21.2 | mc1210 | 21.2.x | 0.16.x | Supported |
| 1.21.3 | mc1210 | 21.3.x | 0.16.x | Supported |
| 1.21.4 | mc1210 | 21.4.x | 0.16.x | Supported |
| 1.21.5 | mc1210 | 21.5.x | 0.16.x | Supported |
| 1.21.6 | mc1210 | 21.6.x | 0.16.x | Supported |
| 1.21.7 | mc1210 | 21.7.x | 0.16.x | Supported |
| 1.21.8 | mc1210 | 21.8.x | 0.16.x | Supported |
| 1.21.9 | mc1219 | 21.9.x | 0.16.x | Supported |
| 1.21.10 | mc1219 | 21.10.x | 0.16.x | Supported |
| 1.21.11 | mc1211 | 21.11.x | 0.16.x | Supported |

### API Groups

- **mc1210**: MC 1.21.0 - 1.21.8 (primitive input, ResourceLocation, renderWidget)
- **mc1219**: MC 1.21.9 - 1.21.10 (event-based input, ResourceLocation, renderWidget)
- **mc1211**: MC 1.21.11+ (event-based input, Identifier, renderContents)

## MC 1.21.11 API Changes

Key API changes in Minecraft 1.21.11:

| Old (mc1210) | New (mc1211) |
|--------------|--------------|
| `ResourceLocation` | `net.minecraft.resources.Identifier` |
| `ResourceKey.location()` | `ResourceKey.identifier()` |
| `net.minecraft.Util` | `net.minecraft.util.Util` |
| `net.minecraft.world.level.GameRules` | `net.minecraft.world.level.gamerules.GameRules` |
| `CommandSourceStack.hasPermission(int)` | `source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` |
| `FMLEnvironment.dist` | `FMLEnvironment.getDist()` |
| `Button.renderWidget()` (override) | `Button.renderContents()` (override) |

## Critical Rules

1. **UI style changes require user approval** - The current vanilla-styled UI with the 3-panel layout, tabs, and editor features should not be changed without explicit user authorization first. Always ask before redesigning or restyling.

## Project Structure

```
isotope/
├── compat/              # Version compatibility layer
│   ├── src/main/java/   # Shared interfaces (Id, McVersion)
│   ├── src/mc1210/java/ # MC 1.21.0-1.21.10 implementations
│   └── src/mc1211/java/ # MC 1.21.11+ implementations
├── common/              # Shared mod code (Architectury common)
├── neoforge/            # NeoForge-specific code
├── fabric/              # Fabric-specific code
├── versions/            # Version-specific properties files
├── scripts/             # Build scripts
├── docs/                # Documentation
├── build.gradle         # Root build config
├── gradle.properties    # Default version properties
└── settings.gradle.kts
```

## Build Commands

```bash
# Set Java 21 (macOS with Homebrew)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build default version (1.21.11)
./gradlew build

# Build specific version
./gradlew build -PmcVersion=1.21.4

# Build specific loader for specific version
./gradlew :neoforge:build -PmcVersion=1.21.11
./gradlew :fabric:build -PmcVersion=1.21.4

# Run NeoForge client
./gradlew :neoforge:runClient

# Run Fabric client
./gradlew :fabric:runClient

# Build all 24 version combinations
./scripts/build-all-versions.sh

# Quick build (latest only)
./scripts/build-all-versions.sh --quick

# Clean
./gradlew clean
```

## Key Dependencies

Default (MC 1.21.11):

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.11 |
| NeoForge | 21.11.34-beta |
| Fabric Loader | 0.16.9 |
| Fabric API | 0.115.0+1.21.11 |
| Architectury | 19.0.1 |
| Java | 21 |

Version-specific dependencies are defined in `versions/*.properties` files.

## Compatibility Layer

### Key Classes

- `dev.isotope.compat.Id` - Version-agnostic resource identifier
- `dev.isotope.compat.McVersion` - Version detection and utilities
- `dev.isotope.compat.ui.VersionedButton` - Version-specific button base class

### Usage

```java
// Create identifiers
Id id = Id.of("minecraft", "stone");
Id id = Id.parse("minecraft:diamond");

// Convert to native Minecraft type
ResourceLocation/Identifier native = id.mc();

// Check version
if (McVersion.INSTANCE.is1211OrNewer()) {
    // 1.21.11+ specific code
}

// Version-agnostic permission check
boolean hasPermission = McVersion.INSTANCE.hasGamemasterPermission(source);
```

## Structure-Loot Linking Architecture

The "No Silent Failure" model - multi-layer linking with confidence tracking.

### Loot Source Types

Minecraft has three categories of loot sources:

| Type | Color | Examples | Trigger | Our Handling |
|------|-------|----------|---------|--------------|
| **Structure** | Cyan | Villages, Ancient Cities | Container interaction | Scanned from `Registries.STRUCTURE` |
| **Feature** | Orange | Dungeons (monster_room) | Container interaction | Defined in `FeatureRegistry` |
| **Mob** | Purple | Zombie, Creeper, Ender Dragon | Entity death | Scanned from `entities/*.json` |

### Confidence Levels

| Level | Score | Source | Description |
|-------|-------|--------|-------------|
| MANUAL | 100 | User override | Author explicitly defined |
| MOD_DECLARED | 95 | Mod API | Mod explicitly declared link |
| VERIFIED | 90 | Runtime observation | Seen during gameplay |
| CONFIRMED | 88 | Multi-source | Multiple independent sources agree |
| RUNTIME_ASSIGNED | 85 | setLootTable() hook | Captured from container assignment |
| TEMPLATE | 80 | .nbt parsing | Found in structure template |
| LEARNED | 75 | Past sessions | Previously verified, loaded from disk |
| HIGH | 70 | Heuristics | Vanilla mapping / exact path match |
| MEDIUM | 50 | Heuristics | Partial path match |
| LOW | 30 | Heuristics | Namespace only |

## Mixin Architecture

Three mixins for runtime observation and test mode:

| Mixin | Target | Purpose |
|-------|--------|---------|
| `LootTableMixin` | `LootTable` | Intercept loot generation for test mode |
| `StructureStartMixin` | `StructureStart` | Observe structure placements |
| `ReloadableRegistriesMixin` | `ReloadableServerRegistries.Holder` | Track loot table lookups |

All mixins are always applied (no conditional loading needed).

## Test Mode

Create temporary test worlds to verify loot table changes:

- **Structure Testing**: Locate, teleport, create arenas, generate loot
- **Mob Testing**: Spawn, kill with conditions (player kill, looting level)
- **Statistics**: Run multiple tests, view drop rates and averages
- **Compare Mode**: Side-by-side original vs edited comparison

### Test Mode Flow

1. Edit loot tables in the editor
2. Click "Test Your Changes" → TestSetupScreen shows edited tables
3. Choose world type (Superflat or Normal)
4. Create test world → TestingScreen opens in-game

### Structure/Chest Loot Testing

| Button | Action |
|--------|--------|
| **Teleport** | Locate and teleport to nearest structure |
| **Arena** | Create grid of structure copies (4-36) |
| **Gen ×10** | Generate loot 10 times, spawn items on ground |
| **Stats** | Run 50 rolls and show statistics dialog |

### Mob Loot Testing

| Button | Action |
|--------|--------|
| **Spawn** | Spawn one mob near player (AI disabled) |
| **×5** | Spawn 5 mobs in a grid pattern |
| **Kill** | Remove all mobs of that type within 50 blocks |
| **Test ×10** | Spawn and kill 10 mobs, drops on ground |
| **Stats** | Run 50 kills and show statistics dialog |

## Console Commands

| Command | Description |
|---------|-------------|
| `/isotope status` | Show session state |
| `/isotope structures [namespace]` | List observed structures |
| `/isotope loottables` | List observed loot tables |
| `/isotope analyze <structure_id>` | Analyze specific structure |
| `/isotope session` | Show observation session status |

## Key Features

- 3-panel layout (namespace list, item list, detail panel)
- Tab bar (Structures, Loot Tables, Export)
- Loot table editor with pool/entry editing
- Multi-selection and batch editing
- Entry templates
- Undo/redo with history log
- Global search with item index
- Drop rate visualization
- Diff view (original vs edited)
- Bookmarks
- Session management
- Datapack import/export
- Compare mode
- Structure badges on loot tables
- Table/pool/entry-level function editing
- Random sequence editing
- Composite entry children editing
- KubeJS and CraftTweaker export
- JSON import from clipboard
- Loot flow visualization
- Theme support (light/dark)
- Test mode with structure/mob loot testing

## Keyboard Shortcuts

### Editing
| Shortcut | Action |
|----------|--------|
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Ctrl+S` | Save/Export |
| `Ctrl+N` | Add New |
| `Ctrl+D` | Duplicate |
| `Delete` / `Backspace` | Delete |

### Clipboard
| Shortcut | Action |
|----------|--------|
| `Ctrl+C` | Copy |
| `Ctrl+V` | Paste |
| `Ctrl+Shift+V` | Import JSON |

### Navigation
| Shortcut | Action |
|----------|--------|
| `Ctrl+F` | Focus Search |
| `Ctrl+Shift+F` | Global Search |
| `Alt+Up/Down` | Move entry up/down |
| `Escape` | Close/Clear |
| `F1` | Help |

## UI Theme System

ISOTOPE supports light and dark themes. Theme preference is persisted to `.minecraft/isotope/theme.json`.

### Key Classes

- `ColorTheme` - Record containing all themeable colors with DARK and LIGHT presets
- `ThemeManager` - Singleton managing current theme, persistence, and listeners
- `IsotopeColors` - Contains both static constants and theme-aware accessor methods
- `UIConstants` - Centralized UI layout constants (padding, heights, widths)
- `DialogScreen` - Base class for modal dialogs with common rendering

## Validation System

Validates loot table structures and detects potential issues before export.

| Issue | Severity | Description |
|-------|----------|-------------|
| `EMPTY_POOL` | Warning | Pool has no entries |
| `ZERO_WEIGHT` | Warning | Entry has weight of 0 |
| `ZERO_ROLLS` | Warning | Pool always rolls 0 times |
| `MISSING_ITEM` | Error | Item does not exist in registry |
| `DUPLICATE_ENTRY` | Info | Same item appears multiple times |
| `NEGATIVE_COUNT` | Error | Item count can be negative |
| `UNREACHABLE_ENTRY` | Error | All entries have 0 weight |
| `CONFLICTING_FUNCTIONS` | Warning | Multiple set_count or set_damage |

## Export Formats

### Datapack Export
Exports to `.minecraft/isotope-export/` as a standard datapack structure.

### KubeJS Export
Exports to `.minecraft/kubejs/server_scripts/isotope_loot_<timestamp>.js`

### CraftTweaker Export
Exports to `.minecraft/scripts/isotope_loot_<timestamp>.zs`

## CI/CD

GitHub Actions automatically builds all 24 version combinations on push. See:
- `.github/workflows/build-matrix.yml` - CI build matrix
- `.github/workflows/release.yml` - Release workflow

## Documentation

- `docs/TESTING.md` - Testing guide and checklists
- `docs/VERSION-COMPAT.md` - Version compatibility reference
