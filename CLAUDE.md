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

### Loot Source Types

Minecraft has three categories of loot sources:

| Type | Color | Examples | Trigger | Our Handling |
|------|-------|----------|---------|--------------|
| **Structure** | Cyan | Villages, Ancient Cities | Container interaction | Scanned from `Registries.STRUCTURE` |
| **Feature** | Orange | Dungeons (monster_room) | Container interaction | Defined in `FeatureRegistry` |
| **Mob** | Purple | Zombie, Creeper, Ender Dragon | Entity death | Scanned from `entities/*.json` |

```java
LootSourceType.STRUCTURE  // Cyan - real structures with persistent tracking
LootSourceType.FEATURE    // Orange - fire-and-forget decorations
LootSourceType.MOB        // Purple - entity drops on death
```

### Chest vs Mob Loot (Key Differences)

| Aspect | Chest/Structure Loot | Mob/Entity Loot |
|--------|---------------------|-----------------|
| **Trigger** | Container open | Entity death |
| **Looting Enchant** | Rarely applies | Critical (0-3+ levels) |
| **Player Kill** | Rarely required | Often required for rares |
| **Conditions** | Simple | `killed_by_player`, `random_chance_with_looting` |

**Why this matters**: Mob loot requires understanding killer context. A zombie drops different items depending on whether a player killed it and what Looting level they had.

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
Layer 1: Registry Scan
         - StructureRegistry (real structures from Registries.STRUCTURE)
         - LootTableRegistry (all loot tables)
         - FeatureRegistry (known features with loot - monster_room, etc.)
         - EntityLootRegistry (mob loot from entities/*.json)
    ↓
Layer 2: Template Parsing (StructureTemplateParser)
         - Parses .nbt files for loot table references
         - Handles jigsaw structures recursively
    ↓
Layer 3: Multi-Layer Linking (StructureLootLinker)
         - Heuristics → Templates → Runtime → Author
         - Features linked via FeatureRegistry mappings
         - Confidence only goes UP
    ↓
Layer 4: Orphan Detection (OrphanDetector)
         - Flags unlinked loot tables
         - Flags structures without loot
         - Tracks runtime-only discoveries
    ↓
Layer 5: Compile Unified Registry (LootSourceRegistry)
         - Combines structures + features + mobs into single view
         - Used by UI for consistent display
    ↓
Ready
```

### Key Classes

- `StructureTemplateParser` - Extracts loot tables from .nbt structure templates
- `StructureLootLinker` - Multi-layer linking with confidence promotion
- `ObservationCorrelator` - Runtime structure↔loot correlation
- `OrphanDetector` - Surfaces gaps and missing links
- `StructureLootLink` - Link record with confidence and source
- `FeatureRegistry` - Known features with loot (monster_room → simple_dungeon)
- `EntityLootRegistry` - Scans entity loot tables, maps to entity IDs
- `LootSourceRegistry` - Unified view of structures + features + mobs
- `LootSource` - Abstraction representing any loot source
- `LootSourceType` - Enum: STRUCTURE / FEATURE / MOB

### Design Principles

1. **No silent failures** - Every link has a confidence level
2. **Confidence only promotes** - Never downgrade existing evidence
3. **Surface gaps intentionally** - Orphans are flagged, not hidden
4. **Runtime upgrades heuristics** - Observation confirms guesses

### UI Indicators for Loot Sources

Visual indicators help users understand loot source types and special conditions:

**Browser Widget (LootTableBrowserWidget)**
| Element | Color | Description |
|---------|-------|-------------|
| Entity category header | Purple | Distinguishes mob drops from other loot |
| ⚔ sword icon | Purple | Player kill required for rare drops |
| ⚠ warning icon | Yellow | Orphan loot table (no linked source) |

**Edit Panel (LootTableEditPanel)**
| Element | Color | Location | Description |
|---------|-------|----------|-------------|
| "Mob: EntityName ⚔" | Purple | Header | Shows linked entity, sword if player kill required |
| "⚗ Looting: X drops" | Aqua | Header | Count of looting-affected entries |
| ⚔ sword icon | Purple | Entry row | Entry has `killed_by_player` condition |
| ⚗ potion icon | Aqua | Entry row | Entry has `looting_enchant` or `random_chance_with_looting` |
| ✦ sparkle icon | Gold | Entry row | Entry has enchantment function |
| ◆ diamond icon | Green | Entry row | Entry has attribute modifiers |
| ⚗ potion icon | Orange | Entry row | Entry has potion effect |
| ⚒ tool icon | Gray | Entry row | Entry has damage/durability set |

**Color Constants (IsotopeColors)**
```java
SOURCE_STRUCTURE = 0xFF55FFFF  // Cyan - real structures
SOURCE_FEATURE   = 0xFFFFAA00  // Orange - features (dungeons)
SOURCE_MOB       = 0xFFAA55FF  // Purple - entity drops
ACCENT_AQUA      = 0xFF55FFFF  // Aqua - looting indicators
```

### Loot Function Viewing & Editing

Entries can have functions that modify dropped items (enchantments, attributes, potions, etc.). These are now visible and editable.

**Function Indicators**: Icons appear on entry rows showing what functions are applied (see Edit Panel table above).

**Parameter Summaries**: Functions show readable summaries in the detail panel:
| Function | Example Summary |
|----------|-----------------|
| `enchant_with_levels` | "Lvl 5-15" |
| `enchant_randomly` | "any" or "3 options" |
| `set_enchantments` | "Sharpness" or "3 enchants" |
| `set_damage` | "10-50%" |
| `set_attributes` | "2 modifiers" |
| `set_potion` | "Healing" |
| `looting_enchant` | "+0-1/lvl" |
| `exploration_map` | "Buried treasure" |

**Add Function Dialog**: Right-click an entry → "Add Function..." opens a dialog with presets:
- Enchant with Levels (configurable min/max levels, treasure option)
- Enchant Randomly
- Set Count (min/max)
- Set Damage (durability percentage)
- Looting Enchant (bonus per level)
- Furnace Smelt
- Exploration Map (destination)
- Set Potion (potion type)

**Key Classes**:
- `LootFunction` - Function data with parameter parsing and type detection
- `AddFunctionDialog` - Preset-based function creation UI

## Test Mode

Test mode creates a temporary creative world to test edited loot tables in-game.

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

Entity loot tables are detected and displayed with purple styling.

| Button | Action |
|--------|--------|
| **Spawn** | Spawn one mob near player (AI disabled) |
| **×5** | Spawn 5 mobs in a grid pattern |
| **Kill** | Remove all mobs of that type within 50 blocks |
| **Test ×10** | Spawn and kill 10 mobs, drops on ground |
| **Stats** | Run 50 kills and show statistics dialog |

**Kill Conditions** (cycle with ◀/▶):
| Condition | Effect |
|-----------|--------|
| Player Kill | Triggers `killed_by_player` conditions |
| Non-Player | Only guaranteed drops (no rare items) |
| Looting I/II/III | Player kill with looting enchant bonus |

### Auto-Collect Option

Toggle button in header: `📦 Ground` ↔ `📦 Inventory`

| Mode | Behavior |
|------|----------|
| **📦 Ground** | Items spawn on ground near player (default) |
| **📦 Inventory** | Items go directly to player inventory |

Affects the **Gen ×10** button. If inventory is full, items drop on ground as fallback.

### Clear Items Button

**Clear Items** button in the footer removes all dropped items within 100 blocks of the player. Shows toast with count of removed items. Useful for cleaning up after loot testing.

### Adjustable Test Count

Test count selector in the header allows choosing how many tests to run for Stats and Compare:

| Preset | Use Case |
|--------|----------|
| **10** | Quick check, rough estimate |
| **50** | Default, good balance (default) |
| **100** | More accurate statistics |
| **500** | High precision, slower |

Selected preset highlighted with aqua outline.

### Drop Statistics

The **Stats** button runs multiple loot generations and displays a statistics dialog:

| Column | Description |
|--------|-------------|
| **Item** | Dropped item name |
| **Total** | Total count across all tests |
| **Avg** | Average drops per test |
| **Rate** | % of tests that dropped this item (color-coded) |
| **Range** | Min-max drops per test |

**Rate colors**: Green (≥75%), Yellow (≥25%), Red (<25%)

**Copy button**: Exports formatted statistics to clipboard for sharing/documentation.

### Compare Mode

The **Compare** button (appears when edits exist) runs loot tests on both the original and edited versions, then displays a side-by-side comparison dialog.

| Column | Description |
|--------|-------------|
| **Item** | Item name |
| **Original** | Avg drops and rate for vanilla table |
| **Edited** | Avg drops and rate for edited table |
| **Diff** | Change in average (+X.X or -X.X) and rate change |

**Row highlighting**:
| Color | Meaning |
|-------|---------|
| Green tint | NEW - Item added by edits |
| Red tint | GONE - Item removed by edits |
| Yellow tint | CHANGED - Item rates modified |

Items are sorted by change magnitude (new/removed first, then largest changes).

**Copy button**: Exports comparison table to clipboard with Original, Edited, and Diff columns.

**How it works**: Temporarily disables test mode to generate original loot, then re-enables to generate edited loot. Both results displayed in `CompareStatisticsDialog`.

### Key Classes

- `TestModeState` - Singleton tracking test mode state
- `TestWorldManager` - Creates/deletes test worlds
- `TestingTools` - Structure locate/teleport utilities
- `TestMobTools` - Mob spawn/kill utilities with condition support
- `TestArenaManager` - Structure arena creation
- `LootTestRunner` - Runs loot tests and collects statistics
- `DropStatistics` - Tracks drops across multiple test runs
- `TestSetupScreen` - Pre-test configuration UI
- `TestingScreen` - In-game test controls
- `DropStatisticsDialog` - Statistics display with scrollable table
- `CompareStatisticsDialog` - Side-by-side original vs edited comparison

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
