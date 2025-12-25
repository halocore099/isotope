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
- Currently targets MC 1.21.4
- Multi-project: `common/`, `fabric/`, `neoforge/`

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
