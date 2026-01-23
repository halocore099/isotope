# Isotope Testing Guide

This document describes how to test Isotope across all supported Minecraft versions and mod loaders.

## Build Verification

### Local Build Testing

```bash
# Build for default version (1.21.11)
./gradlew build

# Build for specific version
./gradlew build -PmcVersion=1.21.4

# Build specific loader
./gradlew :neoforge:build -PmcVersion=1.21.11
./gradlew :fabric:build -PmcVersion=1.21.11

# Build all versions (24 combinations)
./scripts/build-all-versions.sh

# Quick build (latest version only)
./scripts/build-all-versions.sh --quick
```

### Expected Build Artifacts

After a successful build, you should find:
```
neoforge/build/libs/isotope-neoforge-{version}+mc{mc_version}.jar
fabric/build/libs/isotope-fabric-{version}+mc{mc_version}.jar
```

## In-Game Test Checklist

### Basic Functionality

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Mod Loads** | Start client, open mod list | Isotope visible, no crash | [ ] |
| **Commands Register** | Run `/isotope status` | Shows session info | [ ] |
| **Keybind Works** | Press keybind (default: I) | Editor UI opens | [ ] |

### UI Tests

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **3-Panel Layout** | Open editor | Namespace list, item list, detail panel visible | [ ] |
| **Tab Bar** | Click tabs | Structures, Loot Tables, Export tabs switch | [ ] |
| **Theme Toggle** | Toggle theme | Light/dark theme switches correctly | [ ] |
| **Keyboard Shortcuts** | Press Ctrl+F | Search field focuses | [ ] |

### Loot Table Editing

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Browse Tables** | Click namespace, select table | Table details shown | [ ] |
| **Edit Entry** | Modify item count | Change reflected in UI | [ ] |
| **Add Pool** | Click "Add Pool" | New pool appears | [ ] |
| **Add Entry** | Click "Add Entry" in pool | Entry type dialog opens | [ ] |
| **Delete Entry** | Select entry, press Delete | Entry removed | [ ] |
| **Undo/Redo** | Edit, press Ctrl+Z, Ctrl+Y | Changes undo and redo | [ ] |
| **Copy/Paste** | Copy entry, paste | Entry duplicated | [ ] |

### Export Tests

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Datapack Export** | Click Export, select Datapack | Valid JSON in `isotope-export/` | [ ] |
| **KubeJS Export** | Click Export, select KubeJS | Valid .js in `kubejs/server_scripts/` | [ ] |
| **CraftTweaker** | Click Export, select CT | Valid .zs in `scripts/` | [ ] |

### Test Mode

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Create Test World** | Click "Test Your Changes" | Test world creation dialog | [ ] |
| **Structure Teleport** | Click Teleport on structure | Teleports to nearest structure | [ ] |
| **Arena Creation** | Click Arena | Grid of structures generated | [ ] |
| **Loot Generation** | Click "Gen ×10" | Loot items spawned | [ ] |
| **Statistics** | Click Stats | Statistics dialog shows | [ ] |
| **Mob Spawn** | Click Spawn on mob | Mob spawns near player | [ ] |
| **Mob Kill Test** | Click "Test ×10" | 10 mobs killed, drops on ground | [ ] |

### Validation

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Empty Pool Warning** | Create empty pool | Warning shown | [ ] |
| **Missing Item Error** | Add invalid item ID | Error shown | [ ] |
| **Zero Weight Warning** | Set weight to 0 | Warning shown | [ ] |

## Version-Specific Tests

### MC 1.21.11+ (API: mc1211)

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **Identifier Class** | Check logs for class errors | No `ResourceLocation` errors | [ ] |
| **Permission Check** | Run `/isotope` as non-op | Permission denied message | [ ] |
| **Button Rendering** | Open any dialog | Buttons render correctly | [ ] |

### MC 1.21.0-1.21.10 (API: mc1210)

| Test | Steps | Expected Result | Pass |
|------|-------|-----------------|------|
| **ResourceLocation** | Check logs for class errors | No `Identifier` errors | [ ] |
| **Permission Check** | Run `/isotope` as non-op | Permission denied message | [ ] |
| **Button Rendering** | Open any dialog | Buttons render correctly | [ ] |

## Automated CI Tests

GitHub Actions runs the full build matrix on every push:
- 12 Minecraft versions (1.21.0 - 1.21.11)
- 2 loaders (NeoForge, Fabric)
- Total: 24 build configurations

Check the Actions tab for build status.

## Reporting Issues

When reporting a bug, please include:
1. Minecraft version
2. Mod loader (NeoForge/Fabric)
3. Isotope version (from JAR filename)
4. Steps to reproduce
5. Expected vs actual behavior
6. Crash log (if applicable)

Create issues at: https://github.com/halocore099/isotope/issues
