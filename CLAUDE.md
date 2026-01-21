# ISOTOPE - Claude Memory

## Branch Structure

**This is the documentation branch (`main`).** It contains no code, only documentation.

### Code Branches

For actual mod code, checkout the appropriate version branch:

| Branch | Loader | MC Version | Java |
|--------|--------|------------|------|
| `fab-1.21.4` | Fabric | 1.21.4 | 21 |
| `neo-1.21.4` | NeoForge | 1.21.4 | 21 |

Each branch is self-contained with its own build configuration and CLAUDE.md documentation.

## Branch Workflow

1. **New features**: Develop on a version branch (usually `fab-1.21.4` first)
2. **Port to other loaders**: Cherry-pick or merge to other branches
3. **Documentation updates**: Update on `main`, merge to version branches as needed

## What's on Main

- `README.md` - Project overview
- `CLAUDE.md` - This file (branch structure info)
- `USAGE.md` - User documentation
- `CHANGELOG.md` - Release history
- `CONTRIBUTING.md` - Contribution guidelines
- `MODRINTH.md` - Modrinth page content
- `LICENSE` - MIT license
- `docs/` - Additional documentation
- `.github/` - GitHub workflows and templates

## Project Overview

**Isotope IDE** is a visual loot table editor and worldgen analysis toolkit for Minecraft modpack developers.

### Key Features

- 3-panel layout (namespace list, item list, detail panel)
- Tab bar (Structures, Loot Tables, Export)
- Loot table editor with pool/entry editing
- Multi-selection and batch editing
- Entry templates
- Undo/redo with history log
- Global search with item index
- Drop rate visualization
- Diff view (original vs edited)
- Bookmarks and session management
- Datapack import/export
- Compare mode (original vs edited statistics)
- KubeJS and CraftTweaker export
- Test mode with structure/mob loot testing
- Loot flow visualization
- Theme support (light/dark)

### Architecture

Uses **Architectury** for cross-loader support with a common + platform pattern:
- `common/` - Shared code
- `fabric/` or `neoforge/` - Loader-specific code

### Mixin Architecture

Three core mixins enable runtime observation and test mode:

| Mixin | Target | Purpose |
|-------|--------|---------|
| `LootTableMixin` | `LootTable` | Intercept loot generation |
| `StructureStartMixin` | `StructureStart` | Observe structure placements |
| `ReloadableRegistriesMixin` | `ReloadableServerRegistries.Holder` | Track loot table lookups |

## Critical Rules

1. **UI style changes require user approval** - The vanilla-styled UI should not be changed without explicit authorization.

2. **Each branch is independent** - Don't try to share code between branches. Each branch has its own complete codebase.

## Build Instructions

See the CLAUDE.md in each version branch for specific build commands. General pattern:

```bash
# Set Java 21 (macOS with Homebrew)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build
./gradlew build

# Run client
./gradlew :fabric:runClient  # or :neoforge:runClient
```
