# ISOTOPE - Claude Memory

## Branch Info

**Branch**: `fab-1.21.2-.3`
**Target**: Minecraft 1.21.2 and 1.21.3, Fabric only
**Java**: 21 (via Homebrew on macOS: `/opt/homebrew/opt/openjdk@21`)

This is a multi-version, single-loader branch. For other versions/loaders, see:
- `neo-1.21.2-.3` - NeoForge 1.21.2/1.21.3
- `neo-1.21.4` / `fab-1.21.4` - Minecraft 1.21.4
- `main` - Documentation only

## Critical Rules

1. **UI style changes require user approval** - The current vanilla-styled UI with the 3-panel layout, tabs, and editor features should not be changed without explicit user authorization first. Always ask before redesigning or restyling.

## Project Structure

```
isotope/
├── common/          # Shared code (Architectury common)
├── fabric/          # Fabric-specific code
├── build.gradle     # Root build config
├── gradle.properties # Version properties
└── settings.gradle.kts
```

## Build Commands

```bash
# Set Java 21 (macOS with Homebrew)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build
./gradlew :fabric:build

# Run client
./gradlew :fabric:runClient

# Clean
./gradlew clean
```

## Key Dependencies

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.2 (supports 1.21.2, 1.21.3) |
| Fabric Loader | 0.16.9 |
| Fabric API | 0.106.1+1.21.2 |
| Architectury | 14.0.4 |
| Java | 21 |

## Mixin Architecture

Three mixins for runtime observation and test mode:

| Mixin | Target | Purpose |
|-------|--------|---------|
| `LootTableMixin` | `LootTable` | Intercept loot generation for test mode |
| `StructureStartMixin` | `StructureStart` | Observe structure placements |
| `ReloadableRegistriesMixin` | `ReloadableServerRegistries.Holder` | Track loot table lookups |

Note: In 1.21.2, `LootTable.getLootTableId()` doesn't exist, so we use ThreadLocal tracking via `LootTableTracker`.

## Test Mode

Create temporary test worlds to verify loot table changes:

- **Structure Testing**: Locate, teleport, create arenas, generate loot
- **Mob Testing**: Spawn, kill with conditions (player kill, looting level)
- **Statistics**: Run multiple tests, view drop rates and averages
- **Compare Mode**: Side-by-side original vs edited comparison

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
