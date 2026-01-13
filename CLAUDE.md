# ISOTOPE - Claude Memory

## Critical Rules

1. **UI style changes require user approval** - The current vanilla-styled UI with the 3-panel layout, tabs, and editor features should not be changed without explicit user authorization first. Always ask before redesigning or restyling.

2. **Features should be DISABLED, never REMOVED** - When adding multi-version support or making compatibility changes:
   - Use conditional checks or reflection to disable features on incompatible versions
   - Never delete or gut feature code
   - Keep all IDE features intact for versions that support them (1.21+)
   - Only gracefully degrade on older versions (1.20.x)

## Project Structure

- Uses Architectury for cross-loader (Fabric/NeoForge) support
- Multi-project: `common/`, `fabric/`, `neoforge/`

## Version Support

**Supported**: Minecraft 1.21.x (all patch versions from 1.21 to 1.21.4+)

Single branch (`main`) supports the entire 1.21.x line. No per-patch branches needed.

**Why this works**:
- Architectury insulates from loader quirks
- Data models use `String` types + raw `JsonObject` (tolerates unknown loot features)
- Mixins use safe patterns (`@Inject` at `HEAD`/`TAIL` only, no bytecode manipulation)
- MC 1.21.x has no breaking API changes between patches

**Releases**: Tag format `vX.X.X`. Published to all 1.21.x game versions on Modrinth/CurseForge.

## Structure-Loot Linking Architecture

The "No Silent Failure" model - multi-layer linking with confidence tracking.

### Confidence Levels (never downgrade, only promote)

| Level | Score | Source | Description |
|-------|-------|--------|-------------|
| MANUAL | 100 | User override | Author explicitly defined |
| VERIFIED | 90 | Runtime observation | Seen during gameplay |
| TEMPLATE | 80 | .nbt parsing | Found in structure template |
| HIGH | 70 | Heuristics | Vanilla mapping / exact path match |
| MEDIUM | 50 | Heuristics | Partial path match |
| LOW | 30 | Heuristics | Namespace only |

### Layer Flow

```
Server Start
    ↓
Layer 1: Registry Scan (StructureRegistry, LootTableRegistry)
    ↓
Layer 2: Template Parsing (StructureTemplateParser)
         - Parses .nbt files for loot table references
         - Handles jigsaw structures recursively
    ↓
Layer 3: Multi-Layer Linking (StructureLootLinker)
         - Heuristics → Templates → Runtime → Author
         - Confidence only goes UP
    ↓
Layer 4: Orphan Detection (OrphanDetector)
         - Flags unlinked loot tables
         - Flags structures without loot
         - Tracks runtime-only discoveries
    ↓
Ready
```

### Key Classes

- `StructureTemplateParser` - Extracts loot tables from .nbt structure templates
- `StructureLootLinker` - Multi-layer linking with confidence promotion
- `ObservationCorrelator` - Runtime structure↔loot correlation
- `OrphanDetector` - Surfaces gaps and missing links
- `StructureLootLink` - Link record with confidence and source

### Design Principles

1. **No silent failures** - Every link has a confidence level
2. **Confidence only promotes** - Never downgrade existing evidence
3. **Surface gaps intentionally** - Orphans are flagged, not hidden
4. **Runtime upgrades heuristics** - Observation confirms guesses

## Key Features (DO NOT REMOVE)

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

## Build Commands

```bash
./gradlew :fabric:runClient    # Run Fabric client
./gradlew :neoforge:runClient  # Run NeoForge client
./gradlew build                # Build all
```
