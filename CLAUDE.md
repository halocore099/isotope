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
| ⚙ gear icon | Orange | Linked to a feature (dungeon, bonus chest, etc.) |
| ⚠ warning icon | Yellow | Orphan loot table (no linked source) |

**Feature Tooltip**: Hovering over ⚙ shows feature name, description, and "Not a tracked structure" note. Features are fire-and-forget decorations without persistent tracking like real structures.

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

### Loot Condition Editing

Entries can have conditions that control when they drop (player kill, random chance, weather, etc.).

**Condition Indicators**: Icons appear on entry rows:
- ⚔ sword icon (purple) - Entry has `killed_by_player` condition
- ⚗ potion icon (aqua) - Entry has `random_chance_with_looting` condition

**Parameter Summaries**: Conditions show readable summaries in the detail panel:
| Condition | Example Summary |
|-----------|-----------------|
| `random_chance` | "25%" |
| `random_chance_with_looting` | "10% +2%/lvl" |
| `weather_check` | "rain" or "thunder" |
| `time_check` | "13000-23000" |
| `inverted` | "NOT killed by player" |

**Add Condition Dialog**: Right-click an entry → "Add Condition..." opens a dialog with presets:
- Random Chance (configurable percentage 0-100%)
- Random Chance + Looting (base chance + per-level bonus)
- Killed by Player (no parameters)
- Survives Explosion (no parameters)
- Weather: Raining (only when raining)
- Weather: Thunderstorm (only during thunderstorms)
- Time Check (configurable min/max day time 0-24000)
- Inverted: Not Player Kill (drops when NOT killed by player)

**Key Classes**:
- `LootCondition` - Condition data with parameter parsing and factory methods
- `AddConditionDialog` - Preset-based condition creation UI

### Pool-Level Functions & Conditions

Pools can have their own functions and conditions that apply to all entries in the pool.

**Adding Pool Functions/Conditions**: Right-click on a pool header to access:
- "Add Pool Function..." - Opens the same AddFunctionDialog, applies function to entire pool
- "Add Pool Condition..." - Opens the same AddConditionDialog, applies condition to entire pool

**Visual Display**: When a pool has functions or conditions, they appear below the pool header:
```
Pool 1          Rolls: 1-3    Luck: +1        [x]
  Functions: set_count (1-3)              [X]
  Conditions: random_chance (25%)         [X]
  ├─ diamond           W: 5   Qty: 1-2
  └─ gold_ingot        W: 10  Qty: 2-4
```

**Remove Buttons**: Each pool function/condition has an [X] button to remove it.

**Key Operations**:
- `AddPoolFunction(poolIndex, function)` - Add function to pool
- `RemovePoolFunction(poolIndex, functionIndex)` - Remove function from pool
- `AddPoolCondition(poolIndex, condition)` - Add condition to pool
- `RemovePoolCondition(poolIndex, conditionIndex)` - Remove condition from pool

### Bonus Rolls (Luck-Based)

Pools have bonus rolls that add extra rolls based on the player's luck attribute. This affects quality-weighted loot when players have Luck potion effects or Luck of the Sea enchantment.

**Visual Display**: Bonus rolls appear in the pool header:
- `Luck: +1` (green) - Pool has bonus rolls configured
- `[+Luck]` (muted, clickable) - Pool has no bonus rolls, click to add

**Editing Bonus Rolls**: Right-click pool header → "Set Bonus Rolls..." opens dialog with presets:

| Preset | Effect |
|--------|--------|
| None (0) | No bonus rolls |
| +1 per luck | 1 extra roll per luck point |
| +2 per luck | 2 extra rolls per luck point |
| +0-1 per luck | 0-1 extra rolls per luck point |
| +0-2 per luck | 0-2 extra rolls per luck point |
| +1-2 per luck | 1-2 extra rolls per luck point |

**Key Classes**:
- `BonusRollsDialog` - Preset-based bonus rolls selection
- `ModifyBonusRolls` operation - Changes pool bonus rolls

### Entry Quality Editing

Quality is a luck-based weight modifier that affects drop chances based on the player's luck attribute.

**How Quality Works**:
- Positive quality: Item becomes more likely with higher luck
- Negative quality: Item becomes less likely with higher luck
- Zero: Luck has no effect on this entry (default)

**Editing Quality**: Right-click an entry → "Set Quality..." opens a dialog with presets:

| Preset | Effect |
|--------|--------|
| 0 | No luck effect (default) |
| +1 | Slightly favored with luck |
| +2 | Favored with luck |
| +5 | Rare, strongly favored |
| +10 | Very rare, heavily favored |
| -1 | Slightly disfavored with luck |
| -2 | Disfavored with luck |

**Display**: Quality is shown in the entry detail panel when non-zero.

**Key Classes**:
- `QualityDialog` - Preset-based quality selection UI
- `LootEditOperation.ModifyEntryQuality` - Operation for changing quality

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

Looting level (0-3) is now a separate selector in the header row. See [Looting Selector](#looting-selector).

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

### Luck Parameter

Luck selector in the second header row affects quality-weighted loot table entries for chest loot:

| Value | Effect |
|-------|--------|
| **0** | Default, no luck bonus |
| **1-3** | Moderate luck increase |
| **5** | Maximum luck (like Luck of the Sea III) |

Higher luck values increase chances of getting rarer items from quality-weighted pools. This simulates the effect of the Luck potion or Luck of the Sea enchantment on fishing.

Selected preset highlighted with green outline. Luck value shown in toast messages and statistics condition.

**Note**: Luck only affects chest/container loot (Stats/Compare). Mob loot uses looting enchant instead.

### Looting Selector

Looting selector in the second header row (next to luck) affects mob loot drops:

| Value | Effect |
|-------|--------|
| **0** | No looting bonus |
| **1** | Looting I enchantment |
| **2** | Looting II enchantment |
| **3** | Looting III enchantment |

Looting increases drop quantities and rare drop chances for mobs. Only applies when kill condition is "Player Kill".

Selected preset highlighted with purple outline. Looting value shown in toast messages and statistics condition.

**Kill Conditions** (simplified):
| Condition | Description |
|-----------|-------------|
| **Player Kill** | Triggers `killed_by_player` conditions, looting applies |
| **Non-Player** | Only guaranteed drops, looting ignored |

### Drop Statistics

The **Stats** button runs multiple loot generations and displays a statistics dialog:

| Column | Description |
|--------|-------------|
| **Icon** | 16x16 item icon for visual identification |
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
| **Icon** | 16x16 item icon for visual identification |
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

## Console Commands

Debug commands for inspecting observation data. Requires permission level 2 (op/creative).

| Command | Description |
|---------|-------------|
| `/isotope status` | Show session state, observed structures count, structures with loot, unique loot tables |
| `/isotope structures [namespace]` | List observed structures, optionally filtered by namespace (max 20 shown) |
| `/isotope loottables` | List all observed loot tables (max 20 shown) |
| `/isotope analyze <structure_id>` | Analyze a specific structure - shows loot tables, invocation counts, observed items |
| `/isotope session` | Show observation session status and last result details |

**Analyze output includes:**
- Loot tables found in structure with invocation counts
- Observed items dropped from those tables
- Source marked as "OBSERVED (ground truth)"

**Session result details:**
- Structures placed/failed counts
- Loot invocations total
- Structures with loot vs without
- List of failed structures (if any)

**Key Class:** `IsotopeCommands` - Registers commands via Architectury's `CommandRegistrationEvent`

## Validation System

Validates loot table structures and detects potential issues before export.

### Severity Levels

| Severity | Color | Description |
|----------|-------|-------------|
| **ERROR** | Red | Will cause problems (broken loot tables) |
| **WARNING** | Orange | Might cause problems (suboptimal configuration) |
| **INFO** | Blue | Informational (suggestions for improvement) |

### Issue Types

| Issue | Severity | Description | Suggestion |
|-------|----------|-------------|------------|
| `EMPTY_POOL` | Warning | Pool has no entries | Add entries or remove the pool |
| `ZERO_WEIGHT` | Warning | Entry has weight of 0 | Set weight to at least 1 |
| `ZERO_ROLLS` | Warning | Pool always rolls 0 times | Set rolls to at least 1 |
| `MISSING_ITEM` | Error | Item does not exist in registry | Check item ID spelling |
| `DUPLICATE_ENTRY` | Info | Same item appears multiple times in pool | Consider merging duplicate entries |
| `NEGATIVE_COUNT` | Error | Item count can be negative | Set minimum count to 0 or higher |
| `UNREACHABLE_ENTRY` | Error | All entries have 0 weight, nothing can drop | Set at least one entry's weight > 0 |
| `CONFLICTING_FUNCTIONS` | Warning | Multiple set_count or set_damage functions | Only the last function will apply |

### Validation Result

Each validation returns a `ValidationResult` containing:
- List of `ValidationIssue` records (type, severity, message, pool/entry index, suggestion)
- Counts: `errorCount`, `warningCount`, `infoCount`
- Helper methods: `hasIssues()`, `hasErrors()`

### Key Class

`LootTableValidator` - Static `validate(tableId, structure)` method that checks:
1. Empty pools
2. Zero rolls on pools
3. Zero/invalid weights on entries
4. Missing items (minecraft namespace only, to avoid false positives with mods)
5. Duplicate entries per pool
6. Negative item counts
7. Conflicting functions (multiple set_count or set_damage)

## Entry Templates

Pre-configured templates for quickly creating common loot entries with standard weights, counts, and functions.

### Built-in Templates

| Template | Category | Weight | Count | Default Item | Functions |
|----------|----------|--------|-------|--------------|-----------|
| **Common Item** | Basic | 10 | 1-3 | - | set_count |
| **Uncommon Item** | Basic | 5 | 1-2 | - | set_count |
| **Rare Item** | Basic | 1 | 1 | - | set_count |
| **Food Stack** | Food | 10 | 2-5 | - | set_count |
| **Enchanted Gear** | Equipment | 3 | 1 | - | enchant_randomly |
| **Treasure** | Valuables | 1 | 1 | Diamond | set_count |
| **Emerald Stack** | Valuables | 8 | 1-4 | Emerald | set_count |
| **Arrow Stack** | Combat | 10 | 4-12 | Arrow | set_count |
| **Iron Ingots** | Resources | 10 | 1-4 | Iron Ingot | set_count |
| **Gold Ingots** | Resources | 5 | 1-3 | Gold Ingot | set_count |

### Usage

Templates can be used to create entries:
- **With custom item**: `template.createEntry(itemId)` - uses template settings with specified item
- **With default item**: `template.createEntry()` - uses template's default item (if set)

### Template Structure

```java
EntryTemplate(
    id,           // Unique identifier
    name,         // Display name
    description,  // Description text
    category,     // Category for grouping (Basic, Food, Equipment, etc.)
    defaultItem,  // Optional default item ResourceLocation
    defaultWeight,// Weight for drop chance
    defaultCount, // NumberProvider (Constant or Uniform range)
    functions     // List of LootFunctions to apply
)
```

**Key Class:** `EntryTemplate` - Record with `BUILTIN_TEMPLATES` list containing all 10 default templates

## Quick Fixes

Wizard-style operations for common loot table balancing tasks. All fixes can be previewed before applying.

### Available Fix Types

| Fix Type | Description | Effect |
|----------|-------------|--------|
| **Balance Weights** | Normalize all entry weights to sum to 100 | Scales weights proportionally |
| **Nerf Rare Items** | Halve weight of items with <5% drop rate | Reduces rare item chances |
| **Buff Rare Items** | Double weight of items with <5% drop rate | Increases rare item chances |
| **Nerf Common Items** | Halve weight of items with >50% drop rate | Reduces common item dominance |
| **Buff Common Items** | Double weight of items with >50% drop rate | Increases common item chances |
| **Cap Quantity** | Set max item count to 3 for all entries | Limits stack sizes |
| **Double Quantity** | Double all item counts | Increases all drop amounts |
| **Halve Quantity** | Halve all item counts | Decreases all drop amounts |
| **Remove Enchanted** | Remove enchantment functions from all entries | Strips enchant_randomly, enchant_with_levels, etc. |
| **Remove Duplicates** | Remove duplicate item entries per pool | Keeps first occurrence only |

### Workflow

1. **Preview**: `QuickFix.preview(type, tableId, structure)` returns `FixResult` with:
   - Description of what will change
   - Count of changes to be made
   - List of `LootEditOperation` objects

2. **Apply**: `QuickFix.apply(tableId, result)` executes all operations through `LootEditManager`

### Implementation Details

- **Weight adjustments**: Minimum weight of 1 enforced to prevent 0-weight entries
- **Quantity scaling**: Uses `NumberProvider` (Constant, Uniform, Binomial) - all values capped at minimum 1
- **Duplicate removal**: Processes entries in reverse order to maintain valid indices during removal
- **Rare threshold**: <5% drop rate (weight/totalWeight < 0.05)
- **Common threshold**: >50% drop rate (weight/totalWeight > 0.5)

**Key Class:** `QuickFix` - Static methods `preview()` and `apply()` with `FixType` enum

## Bulk Operations

Cross-table operations that apply changes to **all loot tables** at once. All operations support preview before applying.

### Available Operations

| Operation | Description | Parameters |
|-----------|-------------|------------|
| **Remove Item** | Remove a specific item from all loot tables | `itemToRemove` |
| **Replace Item** | Replace one item with another across all tables | `oldItem`, `newItem` |
| **Scale Weights** | Multiply all weights by a factor | `scaleFactor` (e.g., 0.5, 2.0) |
| **Scale Counts** | Multiply all item counts by a factor | `scaleFactor` |
| **Remove Empty** | Remove all empty pools from all tables | - |
| **Normalize** | Set all weights to sum to 100 per pool | - |

### Workflow

1. **Preview**: Each operation has a `preview*()` method returning `BulkResult`:
   ```java
   BulkResult result = BulkOperation.previewRemoveItem(server, itemId);
   // result.tablesAffected() - number of tables that will be modified
   // result.totalChanges() - total number of individual changes
   // result.changesByTable() - Map of tableId → list of change descriptions
   ```

2. **Apply**: Each operation has an `apply*()` method:
   ```java
   BulkOperation.applyRemoveItem(server, itemId);
   ```

### Operation Details

| Operation | Min Weight | Min Count | Processing Order |
|-----------|------------|-----------|------------------|
| Scale Weights | 1 | - | Forward |
| Scale Counts | - | 1 | Forward |
| Remove Item | - | - | Reverse (preserves indices) |
| Remove Empty | - | - | Reverse (preserves indices) |
| Normalize | 1 | - | Forward |

### Use Cases

- **Remove Item**: Remove a specific item that shouldn't drop anywhere (e.g., debug item)
- **Replace Item**: Swap one resource for another across all structures
- **Scale Weights**: Make all items more/less rare globally
- **Scale Counts**: Increase/decrease all drop quantities
- **Remove Empty**: Clean up tables after bulk entry removal
- **Normalize**: Standardize weights for easier percentage calculations

**Key Class:** `BulkOperation` - Static preview/apply method pairs for each `Type`

## Keyboard Shortcuts

Centralized keyboard shortcuts for the loot editor. Screens implement `ShortcutContext` interface to handle supported actions.

### Editing Shortcuts

| Shortcut | Action | Description |
|----------|--------|-------------|
| `Ctrl+Z` | Undo | Undo the last operation |
| `Ctrl+Y` | Redo | Redo the last undone operation |
| `Ctrl+Shift+Z` | Redo | Alternative redo shortcut |
| `Ctrl+S` | Save/Export | Export current work |
| `Ctrl+N` | Add New | Add new item to selected pool |
| `Ctrl+D` | Duplicate | Duplicate selected entry |
| `Delete` | Delete | Remove selected item |
| `Backspace` | Delete | Alternative delete shortcut |

### Clipboard Shortcuts

| Shortcut | Action | Description |
|----------|--------|-------------|
| `Ctrl+C` | Copy | Copy selected entry to clipboard |
| `Ctrl+V` | Paste | Paste entry from clipboard |

### Navigation Shortcuts

| Shortcut | Action | Description |
|----------|--------|-------------|
| `Ctrl+F` | Focus Search | Focus the search box |
| `Ctrl+Shift+F` | Global Search | Open global search across all tables |
| `Alt+Up` | Move Up | Move selected entry up in pool |
| `Alt+Down` | Move Down | Move selected entry down in pool |
| `Escape` | Close/Clear | Close picker or clear selection |
| `F1` | Help | Show keyboard shortcuts help |

### Implementation

Screens implement the `ShortcutContext` interface with default no-op methods:

```java
public interface ShortcutContext {
    default void undo() {}
    default void redo() {}
    default void save() {}
    default void focusSearch() {}
    default void globalSearch() {}
    default void addItem() {}
    default void delete() {}
    default void duplicate() {}
    default void copy() {}
    default void paste() {}
    default void moveUp() {}
    default void moveDown() {}
    default void escape() {}
    default void showHelp() {}
}
```

**Key Class:** `KeyboardShortcuts` - Static `handle(keyCode, modifiers, context)` dispatcher

## Drag-and-Drop Reordering

Entries and pools can be reordered by dragging within the edit panel.

### Entry Drag-and-Drop

Click and drag an entry row to reorder it within the same pool or move it to a different pool.

| Visual | Description |
|--------|-------------|
| Green line | Drop indicator showing where entry will be inserted |
| Green arrow | Points to insertion position |
| Ghost preview | Semi-transparent entry with icon and name follows cursor |

**Behavior:**
- 5px drag threshold prevents accidental drags
- Can drop before any entry or after the last entry in a pool
- Can move entries between different pools
- Uses `RemoveEntry` + `AddEntry` operations (supports undo/redo)

### Pool Drag-and-Drop

Click and drag a pool header to reorder pools within the loot table.

| Visual | Description |
|--------|-------------|
| Cyan line | Drop indicator showing where pool will be inserted |
| Cyan arrows | Arrows on both sides of the line |
| Ghost preview | Shows "Pool N" and entry count |

**Behavior:**
- Click anywhere on pool header (except remove button) to start drag
- Can drop before any pool or after the last pool
- Uses `RemovePool` + `AddPool` operations (supports undo/redo)

### Visual Distinction

| Drag Type | Indicator Color | Ghost Color |
|-----------|-----------------|-------------|
| Entry | Green (0xFF55FF55) | Green tint |
| Pool | Cyan (0xFF55FFFF) | Cyan tint |

**Key Implementation:** `LootTableEditPanel.java` - `mouseDragged()`, `mouseReleased()`, `renderEntryDragVisualization()`, `renderPoolDragVisualization()`

## Bookmark Manager

Persistent bookmarking system for quick access to frequently-used loot tables.

### Storage

Bookmarks are saved to `.minecraft/isotope/bookmarks.json` as a JSON array of ResourceLocation strings:

```json
[
  "minecraft:chests/simple_dungeon",
  "minecraft:chests/stronghold_corridor",
  "minecraft:entities/zombie"
]
```

### API

| Method | Description |
|--------|-------------|
| `add(tableId)` | Add a table to bookmarks |
| `remove(tableId)` | Remove a table from bookmarks |
| `toggle(tableId)` | Toggle bookmark status, returns new state |
| `isBookmarked(tableId)` | Check if a table is bookmarked |
| `getAll()` | Get list of all bookmarked tables |
| `getCount()` | Get total bookmark count |
| `clear()` | Remove all bookmarks |

### Listeners

Register for bookmark change notifications:

```java
BookmarkManager.getInstance().addListener(() -> {
    // Bookmarks changed - refresh UI
});
```

### Features

- **Lazy loading**: Bookmarks loaded from disk on first access
- **Auto-save**: Changes automatically persisted to disk
- **Thread-safe**: Uses `CopyOnWriteArrayList` for listeners
- **Order preserved**: Uses `LinkedHashSet` to maintain insertion order

**Key Class:** `BookmarkManager` - Singleton with `getInstance()`, persists to `isotope/bookmarks.json`

## Recent Tables

Automatically tracks recently viewed loot tables for quick access.

### Storage

Recent tables are saved to `.minecraft/isotope/recent.json` as a JSON array of ResourceLocation strings (most recent first):

```json
[
  "minecraft:chests/simple_dungeon",
  "minecraft:entities/zombie",
  "minecraft:chests/village/village_weaponsmith"
]
```

### API

| Method | Description |
|--------|-------------|
| `recordView(tableId)` | Record a table as recently viewed (moves to front) |
| `remove(tableId)` | Remove a table from recent history |
| `isRecent(tableId)` | Check if a table is in recent history |
| `getAll()` | Get all recent tables (most recent first) |
| `getCount()` | Get total recent count |
| `clear()` | Remove all recent history |

### Features

- **Max entries**: Limited to 15 most recent tables
- **Auto-reorder**: Viewing an existing table moves it to front
- **Bookmark exclusion**: Tables in Bookmarks are hidden from Recent (no duplicates)
- **Lazy loading**: Loaded from disk on first access
- **Auto-save**: Changes automatically persisted to disk

### UI Display

The Recent section appears in the browser widget after Bookmarks:
- Aqua colored header with clock icon (⏱)
- Collapsible like other sections
- Shows table path with clock icon per entry

**Key Class:** `RecentTablesManager` - Singleton with `getInstance()`, persists to `isotope/recent.json`

## History Log

Session-wide chronological log of all edit operations with timestamps and table context.

### Log Entry Structure

Each entry contains:
- `timestamp` - Unix timestamp in milliseconds
- `tableId` - The loot table that was modified
- `operationType` - Type code (e.g., `ADD_ENTRY`, `MODIFY_WEIGHT`)
- `description` - Human-readable description from the operation
- `formattedTime` - Time formatted as `HH:mm:ss`

### Operation Types

| Type | Description |
|------|-------------|
| `ADD_POOL` | Added a new pool |
| `REMOVE_POOL` | Removed a pool |
| `MODIFY_ROLLS` | Changed pool roll count |
| `ADD_ENTRY` | Added entry to pool |
| `REMOVE_ENTRY` | Removed entry from pool |
| `MODIFY_WEIGHT` | Changed entry weight |
| `MODIFY_ITEM` | Changed entry item |
| `SET_COUNT` | Set item count |
| `ADD_FUNCTION` | Added function to entry |
| `REMOVE_FUNCTION` | Removed function from entry |
| `ADD_CONDITION` | Added condition to entry |
| `REMOVE_CONDITION` | Removed condition from entry |
| `ADD_POOL_FUNC` | Added function to pool |
| `REMOVE_POOL_FUNC` | Removed function from pool |
| `ADD_POOL_COND` | Added condition to pool |
| `REMOVE_POOL_COND` | Removed condition from pool |
| `UNDO` | Undo operation |
| `REDO` | Redo operation |
| `BATCH` | Multiple operations at once |

### API

| Method | Description |
|--------|-------------|
| `log(tableId, operation)` | Log a single operation |
| `logUndo(tableId)` | Log an undo action |
| `logRedo(tableId)` | Log a redo action |
| `logBatch(tableId, count, desc)` | Log a batch operation |
| `getAll()` | Get all entries (oldest first) |
| `getRecent(count)` | Get the most recent N entries |
| `getForTable(tableId)` | Get entries for a specific table |
| `getCount()` | Get total entry count |
| `clear()` | Clear all entries |

### Features

- **Max entries**: 500 (oldest trimmed automatically)
- **Time format**: `HH:mm:ss` local time
- **Thread-safe**: Uses `CopyOnWriteArrayList`
- **Listener support**: Register for change notifications

**Key Class:** `HistoryLog` - Singleton with `getInstance()`, in-memory only (not persisted)

## Session Management

Save and restore editor state including open tabs, bookmarks, and UI panel visibility.

### Storage

Sessions are saved to `.minecraft/isotope/sessions/` as JSON files:

```json
{
  "id": "uuid-string",
  "name": "My Session",
  "createdAt": 1704067200000,
  "lastModified": 1704153600000,
  "openTabs": ["minecraft:chests/simple_dungeon", "minecraft:entities/zombie"],
  "activeTabIndex": 0,
  "bookmarks": ["minecraft:chests/buried_treasure"],
  "metadata": {
    "minecraftVersion": "1.21.4",
    "isotopeVersion": "1.0.0"
  },
  "uiState": {
    "dropRatesVisible": true,
    "diffVisible": false,
    "historyVisible": false
  }
}
```

### SessionManager API

| Method | Description |
|--------|-------------|
| `saveSession(name, tabManager)` | Save current state with given name |
| `saveSession(name, tabManager, uiState)` | Save with UI visibility state |
| `loadSession(name)` | Load session by name, returns `Optional<EditorSession>` |
| `applySession(session, tabManager)` | Restore tabs and bookmarks from session |
| `listSessions()` | Get list of all saved sessions (sorted by date) |
| `deleteSession(name)` | Delete a session file |
| `renameSession(oldName, newName)` | Rename a session |
| `autoSave(tabManager)` | Save to hidden `_autosave` session |
| `hasAutosave()` | Check if autosave exists |
| `loadAutosave()` | Load the autosave session |

### SessionInfo (for listing)

| Field | Description |
|-------|-------------|
| `name` | Session name |
| `lastModified` | Timestamp of last save |
| `formattedDate` | Date formatted as `yyyy-MM-dd HH:mm` |
| `tabCount` | Number of open tabs |
| `bookmarkCount` | Number of bookmarks |

### UIState

Captures panel visibility:
- `dropRatesVisible` - Drop rate visualization panel
- `diffVisible` - Diff panel (original vs edited)
- `historyVisible` - History log panel

### Features

- **Autosave**: Hidden `_autosave` session for crash recovery
- **Filename sanitization**: Names converted to safe filenames (`[^a-zA-Z0-9_-]` → `_`)
- **Sorted listing**: Sessions sorted by last modified (newest first)
- **Full restore**: Tabs, active tab index, bookmarks, and UI state all restored

**Key Classes:**
- `SessionManager` - Singleton handling save/load/list/delete
- `EditorSession` - Record containing session data

## Datapack Importer

Import loot tables from existing datapacks for viewing, comparison, and editing.

### Scan Locations

The importer automatically scans these locations for datapacks:
- `.minecraft/datapacks/` - Global datapacks folder
- `.minecraft/isotope-export/` - Isotope's own exports
- `.minecraft/saves/*/datapacks/` - Per-world datapacks

### DatapackInfo

For each discovered datapack:

| Field | Description |
|-------|-------------|
| `name` | Folder name |
| `path` | Full path to datapack |
| `lootTableCount` | Number of loot table JSON files |
| `description` | From `pack.mcmeta` (if present) |

### Import API

| Method | Description |
|--------|-------------|
| `findAvailableDatapacks()` | List all datapacks with loot tables |
| `importFromDatapack(path, callback)` | Import from a standard datapack folder |
| `importFromPath(pathString, callback)` | Import from any path (auto-detects format) |
| `applyImportedTables(tables)` | Cache imported tables for viewing |

### ImportResult

| Field | Description |
|-------|-------------|
| `success` | Whether import succeeded |
| `tablesFound` | Total JSON files found |
| `tablesImported` | Successfully parsed tables |
| `tablesSkipped` | Failed to parse |
| `errors` | List of error messages |
| `importedTables` | List of `ImportedTable` records |

### ImportedTable

| Field | Description |
|-------|-------------|
| `tableId` | ResourceLocation derived from path |
| `sourcePath` | Original file path |
| `structure` | Parsed `LootTableStructure` |

### Path Detection

The importer auto-detects input type:
1. **Standard datapack**: Has `pack.mcmeta` file
2. **Data folder**: Has `data/` subdirectory
3. **Loot table folder**: Path contains `loot_table`

### Table ID Generation

Table IDs are built from the file path:
```
data/minecraft/loot_table/chests/simple_dungeon.json
  → minecraft:chests/simple_dungeon
```

**Key Class:** `DatapackImporter` - Singleton with `getInstance()`

## KubeJS Export

Exports edited loot tables as KubeJS server scripts for modpack distribution.

### Output Location

Scripts are exported to `.minecraft/kubejs/server_scripts/isotope_loot_<timestamp>.js`

### Supported Entry Functions

These functions are converted to chained KubeJS method calls on item entries:

| Function | KubeJS Output | Example |
|----------|---------------|---------|
| `set_count` | `.count(n)` or `.count([min, max])` | `.count([1, 3])` |
| `set_damage` | `.damage(n)` | `.damage(0.5)` |
| `enchant_randomly` | `.enchantRandomly()` | - |
| `enchant_with_levels` | `.enchantWithLevels(n)` | `.enchantWithLevels([5, 15])` |
| `looting_enchant` | `.lootingEnchant(min, max)` | `.lootingEnchant(0, 1)` |
| `furnace_smelt` | `.furnaceSmelt()` | - |
| `limit_count` | `.limitCount(min, max)` | `.limitCount(0, 64)` |
| `set_nbt` | `.nbt({...})` | `.nbt({Damage: 0})` |
| `set_name` | `.customName(Component.literal(...))` | - |
| `set_lore` | `.lore([Component.literal(...)])` | - |
| `set_potion` | `.potion('id')` | `.potion('minecraft:healing')` |
| `exploration_map` | `.explorationMap('dest')` | `.explorationMap('buried_treasure')` |
| `copy_nbt` | `.copyNBT('source')` | `.copyNBT('block_entity')` |

### Supported Pool Functions

Same functions available at pool level via `generateFunctionCode()`:

| Function | KubeJS Output |
|----------|---------------|
| `copy_name` | `pool.copyName('source')` |
| All entry functions | `pool.<function>()` |

### Supported Conditions

| Condition | KubeJS Output |
|-----------|---------------|
| `random_chance` | `pool.randomChance(0.5)` |
| `random_chance_with_looting` | `pool.randomChanceWithLooting(0.1, 0.02)` |
| `killed_by_player` | `pool.killedByPlayer()` |
| `survives_explosion` | `pool.survivesExplosion()` |

### Functions as Comments

Complex functions that can't be directly converted are added as comments:

| Function | Comment Output |
|----------|----------------|
| `apply_bonus` | `// apply_bonus (formula: ...)` |
| `set_contents` | `// set_contents (container contents)` |
| `set_banner_pattern` | `// set_banner_pattern` |
| Other conditions | `// Condition: <type>` |

### Generated Script Structure

```javascript
ServerEvents.lootTables(event => {
    // minecraft:chests/simple_dungeon
    event.modify('minecraft:chests/simple_dungeon', loot => {
        loot.clearPools();
        loot.addPool(pool => {
            pool.rolls = [1, 3];
            pool.addItem('minecraft:diamond', 5).count([1, 2]);
            pool.addItem('minecraft:gold_ingot', 10).count([2, 4]);
            pool.addEmpty(20);
        });
    });
});
```

**Key Class:** `KubeJSExporter` - Singleton with `getInstance()`

## Mixin Architecture

Three critical mixins enable runtime observation and test mode without modifying core game behavior.

### LootTableMixin

**Target:** `net.minecraft.world.level.storage.loot.LootTable`

**Purpose:** Intercept loot generation for observation and test mode replacement.

**Injected Methods:**

| Method | Injection | Purpose |
|--------|-----------|---------|
| `getRandomItems(LootParams, long, Consumer)` | HEAD, cancellable | Main consumer-based generation |
| `getRandomItems(LootParams, RandomSource)` | HEAD, cancellable | Random source variant |
| `getRandomItems(LootParams)` | HEAD, cancellable | Simple variant (uses nanoTime) |
| `getRandomItems(LootParams, long)` | HEAD, cancellable | Seed-based returning list |
| `fill(Container, LootParams, long)` | HEAD, cancellable | **Critical**: Fills chests with loot |

**Behavior:**
1. Gets current table ID from `LootTableTracker`
2. If test mode active AND table has edits:
   - Generates loot from edited `LootTableStructure` via `LootGenerator`
   - Cancels vanilla generation
3. If recording active:
   - Records invocation to `LootObserver`

### StructureStartMixin

**Target:** `net.minecraft.world.level.levelgen.structure.StructureStart`

**Purpose:** Observe structure placements during world generation.

**Injected Methods:**

| Method | Injection | Purpose |
|--------|-----------|---------|
| `placeInChunk(...)` | TAIL | Record structure after placement |

**Behavior:**
1. Only records if `StructureObserver.isRecording()` is true
2. Looks up structure's `ResourceLocation` from registry
3. Creates `StructurePlacement` with:
   - Structure ID
   - Origin position (min corner of bounding box)
   - Full bounding box
4. Reports to `StructureObserver.onStructurePlaced()`

**Shadowed Fields:**
- `structure` - The Structure being placed
- `getBoundingBox()` - Structure bounds
- `getChunkPos()` - Chunk position

### ReloadableRegistriesMixin

**Target:** `net.minecraft.server.ReloadableServerRegistries.Holder`

**Purpose:** Track which loot table is being looked up (bridges registry to LootTableMixin).

**Injected Methods:**

| Method | Injection | Purpose |
|--------|-----------|---------|
| `getLootTable(ResourceKey)` | HEAD | Set current table ID in tracker |

**Behavior:**
1. Only tracks if recording OR test mode is active
2. Sets `LootTableTracker.setCurrentTableId(key.location())`
3. LootTableMixin reads this value to know which table is generating

### Data Flow

```
Registry Lookup → ReloadableRegistriesMixin → LootTableTracker
                                                    ↓
LootTable.fill() → LootTableMixin ← reads table ID
                         ↓
              [Test Mode?] → LootGenerator (edited structure)
              [Recording?] → LootObserver (invocation log)

Structure Generation → StructureStartMixin → StructureObserver
```

**Key Classes:**
- `LootTableMixin` - Loot interception and replacement
- `StructureStartMixin` - Structure placement observation
- `ReloadableRegistriesMixin` - Table ID tracking bridge

## Search Index

Inverted index for finding items across all loot tables.

### Index Structure

Two indexes maintained:
- **Inverted index** (`itemIndex`): Item ID → List of `SearchHit` (where item appears)
- **Forward index** (`tableItems`): Table ID → Set of item IDs (what items are in table)

### SearchHit Record

```java
record SearchHit(
    ResourceLocation table,  // Loot table containing the item
    int pool,                // Pool index (0-based)
    int entry,               // Entry index within pool (0-based)
    String context           // Display string: "Pool 1, Entry 2: diamond (W:5)"
)
```

### API

| Method | Description |
|--------|-------------|
| `indexTable(structure)` | Add a loot table to the index |
| `search(query)` | Find items/tables matching query string |
| `findTablesWithItem(itemId)` | Get all tables containing a specific item |
| `getItemsInTable(tableId)` | Get all items in a specific table |
| `isIndexed(tableId)` | Check if table is in index |
| `getStats()` | Get index statistics string |
| `rebuild()` | Clear and rebuild entire index |
| `clear()` | Clear all index data |

### Search Behavior

1. Query is case-insensitive
2. Matches against both full item ID (`minecraft:diamond`) and path (`diamond`)
3. Results deduplicated by table+pool+entry
4. Sorted alphabetically by table path
5. Returns empty list for blank queries

### Statistics

`getStats()` returns: `"{tables} tables, {uniqueItems} unique items, {totalHits} total hits"`

### Context String Format

```
Pool {N}, Entry {M}: {item_path} (W:{weight})
```

Example: `"Pool 1, Entry 3: diamond_sword (W:5)"`

**Key Classes:**
- `SearchIndex` - Singleton with `getInstance()`
- `SearchHit` - Record representing a search result location

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
