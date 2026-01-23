# Isotope Version Compatibility Reference

This document describes API differences between Minecraft versions and how Isotope handles them.

## Supported Versions

| MC Version | NeoForge | Fabric | API Group | Status |
|------------|----------|--------|-----------|--------|
| 1.21.0 | 21.0.x | 0.16.x | mc1210 | Supported |
| 1.21.1 | 21.1.x | 0.16.x | mc1210 | Supported |
| 1.21.2 | 21.2.x | 0.16.x | mc1210 | Supported |
| 1.21.3 | 21.3.x | 0.16.x | mc1210 | Supported |
| 1.21.4 | 21.4.x | 0.16.x | mc1210 | Supported |
| 1.21.5 | 21.5.x | 0.16.x | mc1210 | Supported |
| 1.21.6 | 21.6.x | 0.16.x | mc1210 | Supported |
| 1.21.7 | 21.7.x | 0.16.x | mc1210 | Supported |
| 1.21.8 | 21.8.x | 0.16.x | mc1210 | Supported |
| 1.21.9 | 21.9.x | 0.16.x | mc1210 | Supported |
| 1.21.10 | 21.10.x | 0.16.x | mc1210 | Supported |
| 1.21.11 | 21.11.x | 0.16.x | mc1211 | Supported |

## API Groups

Isotope uses two API compatibility groups:

### mc1210 (MC 1.21.0 - 1.21.10)

Uses the original Minecraft 1.21 API with:
- `net.minecraft.resources.ResourceLocation` for identifiers
- `ResourceKey.location()` to get the location from a key
- `CommandSourceStack.hasPermission(int level)` for permission checks
- `Button.renderWidget()` for custom button rendering

### mc1211 (MC 1.21.11+)

Uses the updated API with:
- `net.minecraft.resources.Identifier` for identifiers
- `ResourceKey.identifier()` to get the identifier from a key
- `CommandSourceStack.permissions().hasPermission(Permission)` for permission checks
- `Button.renderContents()` for custom button rendering

## Key API Differences

| Feature | mc1210 | mc1211 |
|---------|--------|--------|
| ID Class | `ResourceLocation` | `Identifier` |
| ID from Key | `key.location()` | `key.identifier()` |
| Permission Check | `source.hasPermission(2)` | `source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` |
| Button Render Override | `renderWidget()` | `renderContents()` |
| Util Package | `net.minecraft.Util` | `net.minecraft.util.Util` |
| GameRules Package | `net.minecraft.world.level.GameRules` | `net.minecraft.world.level.gamerules.GameRules` |
| FMLEnvironment.dist | `FMLEnvironment.dist` | `FMLEnvironment.getDist()` |

## Compatibility Layer Architecture

### The `compat` Module

The `compat/` module provides version-agnostic abstractions:

```
compat/
├── src/main/java/          # Shared interfaces
│   └── dev/isotope/compat/
│       ├── Id.java         # Version-agnostic identifier
│       ├── IdFactory.java  # Factory interface
│       ├── Ids.java        # Static utilities
│       └── McVersion.java  # Version detection & utilities
├── src/mc1210/java/        # MC 1.21.0-1.21.10 implementations
│   └── dev/isotope/compat/impl/
│       ├── IdImpl.java
│       ├── IdFactoryImpl.java
│       └── McVersionImpl.java
└── src/mc1211/java/        # MC 1.21.11+ implementations
    └── dev/isotope/compat/impl/
        ├── IdImpl.java
        ├── IdFactoryImpl.java
        └── McVersionImpl.java
```

### ServiceLoader Pattern

Implementations are loaded at runtime via `ServiceLoader`:
- `META-INF/services/dev.isotope.compat.IdFactory`
- `META-INF/services/dev.isotope.compat.McVersion`

### Using the Compatibility Layer

```java
// Create identifiers (version-agnostic)
Id id = Id.of("minecraft", "stone");
Id id = Id.parse("minecraft:diamond");

// Convert to Minecraft's native type
ResourceLocation/Identifier native = id.mc();

// Wrap native type
Id wrapped = Id.wrap(nativeId);

// Check version
if (McVersion.INSTANCE.is1211OrNewer()) {
    // 1.21.11+ specific code
}

// Permission check (version-agnostic)
boolean hasPermission = McVersion.INSTANCE.hasGamemasterPermission(source);
```

## Building for Specific Versions

```bash
# Build for MC 1.21.11 (default)
./gradlew build

# Build for MC 1.21.4
./gradlew build -PmcVersion=1.21.4

# Build NeoForge for MC 1.21.0
./gradlew :neoforge:build -PmcVersion=1.21.0

# Build Fabric for MC 1.21.11
./gradlew :fabric:build -PmcVersion=1.21.11

# Build all versions
./scripts/build-all-versions.sh
```

## Version Properties Files

Each supported version has a properties file in `versions/`:

```properties
# versions/1.21.11.properties
minecraft_version=1.21.11
api_version=mc1211
neoforge_version=21.11.34-beta
fabric_loader_version=0.16.9
fabric_api_version=0.115.0+1.21.11
architectury_version=19.0.1
architectury_min_version=19.0.0
minecraft_version_range_neoforge=[1.21.11,1.21.12)
```

## Adding Support for New Versions

1. **Create version properties file**: `versions/1.21.12.properties`
2. **Determine API group**: Check if any breaking API changes were introduced
3. **If new API group needed**:
   - Create `compat/src/mc1212/java/` directory
   - Implement `IdImpl`, `IdFactoryImpl`, `McVersionImpl`
   - Add ServiceLoader registration files
4. **Update build.gradle API version logic** if needed
5. **Test build**: `./gradlew build -PmcVersion=1.21.12`
6. **Add to CI matrix** in `.github/workflows/build-matrix.yml`

## Fabric Intermediary Mappings

Fabric uses intermediary mappings which may have different method names than the official mappings. The `LootTableRegistry` includes reflection fallbacks for:

- `method_36371` (Fabric intermediary for `getLootTable`)
- `method_36370` (Fabric intermediary for `getOptionalLootTable`)

These fallbacks are automatically used when the official method names fail.
