# ISOTOPE - Claude Memory

## Branch Info

**Branch**: `neo-1.21.4`
**Target**: Minecraft 1.21.4, NeoForge only
**Java**: 21 (via Homebrew on macOS: `/opt/homebrew/opt/openjdk@21`)

This is a single-version, single-loader branch. For other versions/loaders, see:
- `fab-1.21.4` - Fabric 1.21.4
- `main` - Documentation only

## Critical Rules

1. **UI style changes require user approval** - The current vanilla-styled UI with the 3-panel layout, tabs, and editor features should not be changed without explicit user authorization first.

2. **This branch is NeoForge 1.21.4 only** - No multi-version complexity. All code targets NeoForge 1.21.4 directly.

## Project Structure

```
isotope/
├── common/          # Shared code (Architectury common)
├── neoforge/        # NeoForge-specific code
├── build.gradle     # Root build config
├── gradle.properties # Version properties
└── settings.gradle.kts
```

## Build Commands

```bash
# Set Java 21 (macOS with Homebrew)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build
./gradlew build

# Run client
./gradlew :neoforge:runClient

# Clean
./gradlew clean
```

## Key Dependencies

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.4 |
| NeoForge | 21.4.50-beta |
| Architectury | 15.0.3 |
| Java | 21 |

## Mixin Architecture

Three mixins for runtime observation and test mode:

| Mixin | Target | Purpose |
|-------|--------|---------|
| `LootTableMixin` | `LootTable` | Intercept loot generation for test mode |
| `StructureStartMixin` | `StructureStart` | Observe structure placements |
| `ReloadableRegistriesMixin` | `ReloadableServerRegistries.Holder` | Track loot table lookups |

All mixins are always applied (no conditional loading needed for single-version branch).

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

## Structure-Loot Linking

Multi-layer linking system with confidence scoring:

1. **Registry Scan** - Structures from `Registries.STRUCTURE`
2. **Template Parsing** - Loot tables from `.nbt` files
3. **Template Pool Parsing** - From `worldgen/template_pool/*.json`
4. **Content Analysis** - Signature items hint at structure
5. **Runtime Observation** - Correlation from gameplay

### Confidence Levels

| Level | Score | Source |
|-------|-------|--------|
| MANUAL | 100 | User override |
| VERIFIED | 90 | Runtime observation |
| TEMPLATE | 80 | .nbt parsing |
| HIGH | 70 | Heuristics |
| MEDIUM | 50 | Partial match |
| LOW | 30 | Namespace only |

## Test Mode

Create temporary test worlds to verify loot table changes:

- **Structure Testing**: Locate, teleport, create arenas, generate loot
- **Mob Testing**: Spawn, kill with conditions (player kill, looting level)
- **Statistics**: Run multiple tests, view drop rates and averages
- **Compare Mode**: Side-by-side original vs edited comparison

## Console Commands

| Command | Description |
|---------|-------------|
| `/isotope status` | Show session state |
| `/isotope structures [namespace]` | List observed structures |
| `/isotope loottables` | List observed loot tables |
| `/isotope analyze <structure_id>` | Analyze specific structure |
| `/isotope session` | Show observation session status |
