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
| 1.21.9 | 21.9.x | 0.16.x | mc1219 | Supported |
| 1.21.10 | 21.10.x | 0.16.x | mc1219 | Supported |
| 1.21.11 | 21.11.x | 0.16.x | mc1211 | Supported |

## API Groups

Isotope uses three API compatibility groups:

### mc1210 (MC 1.21.0 - 1.21.8)

Uses the original Minecraft 1.21 API with:
- `net.minecraft.resources.ResourceLocation` for identifiers
- `ResourceKey.location()` to get the location from a key
- `CommandSourceStack.hasPermission(int level)` for permission checks
- `Button.renderWidget()` for custom button rendering
- Primitive-based input handling (`mouseClicked(double, double, int)`)

Note: The compat module uses reflection for APIs that vary within this range (NBT, rendering, version info).

### mc1219 (MC 1.21.9 - 1.21.10)

Transitional API with event-based input handling:
- `net.minecraft.resources.ResourceLocation` for identifiers (same as mc1210)
- `ResourceKey.location()` to get the location from a key
- Event-based input handling (`InputEvents.MouseClick`, etc.)
- `Button.renderWidget()` for custom button rendering

### mc1211 (MC 1.21.11+)

Uses the updated API with:
- `net.minecraft.resources.Identifier` for identifiers
- `ResourceKey.identifier()` to get the identifier from a key
- `CommandSourceStack.permissions().hasPermission(Permission)` for permission checks
- `Button.renderContents()` for custom button rendering
- Event-based input handling (same as mc1219)

## Key API Differences

| Feature | mc1210 | mc1219 | mc1211 |
|---------|--------|--------|--------|
| ID Class | `ResourceLocation` | `ResourceLocation` | `Identifier` |
| ID from Key | `key.location()` | `key.location()` | `key.identifier()` |
| Permission Check | `hasPermission(2)` | `hasPermission(2)` | `permissions().hasPermission(...)` |
| Button Render Override | `renderWidget()` | `renderWidget()` | `renderContents()` |
| Util Package | `net.minecraft.Util` | `net.minecraft.Util` | `net.minecraft.util.Util` |
| GameRules Package | `net.minecraft.world.level.GameRules` | `net.minecraft.world.level.GameRules` | `net.minecraft.world.level.gamerules.GameRules` |
| Input Handling | Primitive methods | Event-based | Event-based |
| FMLEnvironment.dist | `FMLEnvironment.dist` | `FMLEnvironment.dist` | `FMLEnvironment.getDist()` |

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
│       ├── McVersion.java  # Version detection & utilities
│       └── EnchantmentCompat.java  # Enchantment registry access
├── src/mc1210/java/        # MC 1.21.0-1.21.8 implementations
│   └── dev/isotope/compat/
│       ├── impl/
│       │   ├── IdImpl.java
│       │   ├── IdFactoryImpl.java
│       │   └── McVersionImpl.java
│       ├── EnchantmentCompatImpl.java
│       └── ui/
│           └── VersionedWidget.java (primitive input)
├── src/mc1219/java/        # MC 1.21.9-1.21.10 implementations
│   └── dev/isotope/compat/
│       ├── impl/
│       │   ├── IdImpl.java
│       │   ├── IdFactoryImpl.java
│       │   └── McVersionImpl.java
│       ├── EnchantmentCompatImpl.java
│       └── ui/
│           └── VersionedWidget.java (event-based input)
└── src/mc1211/java/        # MC 1.21.11+ implementations
    └── dev/isotope/compat/
        ├── impl/
        │   ├── IdImpl.java
        │   ├── IdFactoryImpl.java
        │   └── McVersionImpl.java
        ├── EnchantmentCompatImpl.java
        └── ui/
            └── VersionedWidget.java (event-based input)
```

### ServiceLoader Pattern

Implementations are loaded at runtime via `ServiceLoader`:
- `META-INF/services/dev.isotope.compat.IdFactory`
- `META-INF/services/dev.isotope.compat.McVersion`
- `META-INF/services/dev.isotope.compat.EnchantmentCompat`

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

// Enchantment data (registry-based, no hardcoding)
EnchantmentCompat enchants = EnchantmentCompat.INSTANCE;
List<Id> applicable = enchants.getApplicableEnchantments(itemId, includeTreasure, registryAccess);
int maxLevel = enchants.getMaxLevel(enchantmentId, registryAccess);
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
   - Implement `IdImpl`, `IdFactoryImpl`, `McVersionImpl`, `EnchantmentCompatImpl`
   - Add ServiceLoader registration files
4. **Update build.gradle API version logic** if needed
5. **Test build**: `./gradlew build -PmcVersion=1.21.12`
6. **Add to CI matrix** in `.github/workflows/build-matrix.yml`

## Fabric Intermediary Mappings

Fabric uses intermediary mappings which may have different method names than the official mappings. The `LootTableRegistry` includes reflection fallbacks for:

- `method_36371` (Fabric intermediary for `getLootTable`)
- `method_36370` (Fabric intermediary for `getOptionalLootTable`)

These fallbacks are automatically used when the official method names fail.

---

# Contributor Guide: Adding New Compat Classes

This section guides contributors on when and how to add version-specific compatibility code.

## Decision Tree: Do I Need a Compat Class?

```
Is the API you're using different between MC versions?
│
├─ NO → Use the API directly in common/
│
└─ YES → What kind of difference?
         │
         ├─ Class/package renamed (e.g., ResourceLocation → Identifier)
         │  └─ Create an abstraction interface + version implementations
         │
         ├─ Method signature changed (e.g., hasPermission(int) → permissions().hasPermission())
         │  └─ Add method to existing compat class or create new one
         │
         ├─ Method return type changed (e.g., String → Optional<String>)
         │  └─ Create compat utility class with unified return type
         │
         ├─ Class moved to different package
         │  └─ Create polyfill class in old location that delegates to new
         │
         └─ UI rendering method renamed (e.g., renderWidget → renderContents)
            └─ Create versioned base class with abstract method
```

## Existing Compat Patterns

### Pattern 1: Interface Abstraction (Id, McVersion, EnchantmentCompat)

**When to use**: Core class was renamed or has incompatible APIs.

```
compat/src/main/java/         → Interface definition
compat/src/mc1210/java/       → MC 1.21.0-1.21.8 implementation
compat/src/mc1219/java/       → MC 1.21.9-1.21.10 implementation
compat/src/mc1211/java/       → MC 1.21.11+ implementation
```

**Example**: `Id` interface wraps `ResourceLocation` (mc1210/mc1219) or `Identifier` (mc1211)

```java
// compat/src/main/java/dev/isotope/compat/Id.java
public interface Id {
    String getNamespace();
    String getPath();
    Object toMc();  // Returns native MC type

    static Id of(String namespace, String path) {
        return IdFactory.INSTANCE.create(namespace, path);
    }
}

// compat/src/mc1210/java/dev/isotope/compat/impl/IdImpl.java
public record IdImpl(ResourceLocation mc) implements Id {
    @Override public String getNamespace() { return mc.getNamespace(); }
    @Override public String getPath() { return mc.getPath(); }
    @Override public Object toMc() { return mc; }
}

// compat/src/mc1211/java/dev/isotope/compat/impl/IdImpl.java
public record IdImpl(Identifier mc) implements Id {
    @Override public String getNamespace() { return mc.getNamespace(); }
    @Override public String getPath() { return mc.getPath(); }
    @Override public Object toMc() { return mc; }
}
```

### Pattern 2: Static Utility Class (NbtCompat, EditBoxCompat)

**When to use**: Method signatures or return types changed.

```java
// compat/src/mc1210/java/dev/isotope/compat/NbtCompat.java
public final class NbtCompat {
    // MC 1.21.10: getString() returns String (empty if missing)
    public static Optional<String> getString(CompoundTag nbt, String key) {
        if (nbt.contains(key, Tag.TAG_STRING)) {
            return Optional.of(nbt.getString(key));
        }
        return Optional.empty();
    }
}

// compat/src/mc1211/java/dev/isotope/compat/NbtCompat.java
public final class NbtCompat {
    // MC 1.21.11: getString() already returns Optional<String>
    public static Optional<String> getString(CompoundTag nbt, String key) {
        return nbt.getString(key);
    }
}
```

### Pattern 3: Polyfill Class (Identifier, Util)

**When to use**: Class moved to different package, need same import path.

```java
// compat/src/mc1210/java/net/minecraft/resources/Identifier.java
// Polyfill so common code can import the 1.21.11 location
package net.minecraft.resources;

public final class Identifier {
    public static Identifier fromNamespaceAndPath(String ns, String path) {
        return new Identifier(ResourceLocation.fromNamespaceAndPath(ns, path));
    }
    // Wraps ResourceLocation
}

// compat/src/mc1210/java/net/minecraft/util/Util.java
// Shim for Util which moved from net.minecraft.Util
package net.minecraft.util;

public final class Util {
    public static net.minecraft.Util.OS getPlatform() {
        return net.minecraft.Util.getPlatform();
    }
}
```

### Pattern 4: Versioned Base Class (VersionedWidget, VersionedButton)

**When to use**: Override methods were renamed between versions.

```java
// compat/src/mc1210/java/dev/isotope/compat/ui/VersionedButton.java
public abstract class VersionedButton extends Button {
    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        renderButtonContents(g, mx, my, pt);  // Delegate to unified method
    }
    protected abstract void renderButtonContents(GuiGraphics g, int mx, int my, float pt);
}

// compat/src/mc1211/java/dev/isotope/compat/ui/VersionedButton.java
public abstract class VersionedButton extends Button {
    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        renderButtonContents(g, mx, my, pt);  // Delegate to unified method
    }
    protected abstract void renderButtonContents(GuiGraphics g, int mx, int my, float pt);
}
```

## Step-by-Step: Adding a New Compat Class

### Step 1: Identify the API Difference

Check multiple MC versions to understand the exact difference:

```bash
# Build for multiple versions to see compilation errors
./gradlew :common:compileJava -PmcVersion=1.21.4
./gradlew :common:compileJava -PmcVersion=1.21.9
./gradlew :common:compileJava -PmcVersion=1.21.11
```

### Step 2: Choose the Right Pattern

- **New core type?** → Pattern 1 (Interface Abstraction)
- **Method signature changed?** → Pattern 2 (Static Utility)
- **Package moved?** → Pattern 3 (Polyfill)
- **Override method renamed?** → Pattern 4 (Versioned Base Class)

### Step 3: Create the Interface/Class

For Pattern 1 or 2, create the shared interface or class:

```java
// compat/src/main/java/dev/isotope/compat/MyCompat.java
public interface MyCompat {
    // Define version-agnostic API
    void doSomething(Arg arg);

    // Singleton accessor if needed
    MyCompat INSTANCE = loadInstance();

    private static MyCompat loadInstance() {
        return ServiceLoader.load(MyCompat.class).findFirst()
            .orElseThrow(() -> new IllegalStateException("No MyCompat implementation"));
    }
}
```

### Step 4: Implement for Each API Version

```java
// compat/src/mc1210/java/dev/isotope/compat/MyCompatImpl.java
public class MyCompatImpl implements MyCompat {
    @Override
    public void doSomething(Arg arg) {
        // MC 1.21.0-1.21.8 implementation
    }
}

// compat/src/mc1219/java/dev/isotope/compat/MyCompatImpl.java
public class MyCompatImpl implements MyCompat {
    @Override
    public void doSomething(Arg arg) {
        // MC 1.21.9-1.21.10 implementation
    }
}

// compat/src/mc1211/java/dev/isotope/compat/MyCompatImpl.java
public class MyCompatImpl implements MyCompat {
    @Override
    public void doSomething(Arg arg) {
        // MC 1.21.11+ implementation
    }
}
```

### Step 5: Register ServiceLoader (if needed)

For interfaces that use ServiceLoader:

```
# compat/src/mc1210/resources/META-INF/services/dev.isotope.compat.MyCompat
dev.isotope.compat.MyCompatImpl

# compat/src/mc1219/resources/META-INF/services/dev.isotope.compat.MyCompat
dev.isotope.compat.MyCompatImpl

# compat/src/mc1211/resources/META-INF/services/dev.isotope.compat.MyCompat
dev.isotope.compat.MyCompatImpl
```

### Step 6: Add Tests

```java
// compat/src/test/java/dev/isotope/compat/MyCompatContractTest.java
@DisplayName("MyCompat Interface Contract")
class MyCompatContractTest {
    // Test interface behavior with mock implementation
    // See IdContractTest.java for example
}
```

### Step 7: Update Documentation

Add the new compat class to this file's "Current Compat Classes" table.

## Current Compat Classes

| Class | Pattern | Purpose |
|-------|---------|---------|
| `Id` | Interface | Version-agnostic resource identifier |
| `IdFactory` | Interface | Factory for creating Id instances |
| `IdImpl` | Implementation | Wraps ResourceLocation/Identifier |
| `Ids` | Utility | Static helper methods for Id |
| `McVersion` | Interface | Version detection and utilities |
| `McVersionImpl` | Implementation | Version-specific permission checks |
| `McVersionInfo` | Utility | Version string constants |
| `NbtCompat` | Static Utility | NBT access with unified Optional return |
| `GameRulesCompat` | Static Utility | GameRules creation across packages |
| `InputCompat` | Static Utility | Input event handling |
| `EnchantmentCompat` | Interface | Enchantment registry access (uses actual registries) |
| `VersionedWidget` | Base Class | Widget with unified input events |
| `VersionedButton` | Base Class | Button with unified render method |
| `VersionedScreen` | Base Class | Screen with unified input events |
| `VersionedListEntry` | Base Class | List entry compatibility |
| `EditBoxCompat` | Static Utility | EditBox input forwarding |
| `RenderCompat` | Static Utility | Rendering utilities |
| `Identifier` (polyfill) | Polyfill | mc1210/mc1219 shim for Identifier class |
| `Util` (polyfill) | Polyfill | mc1210/mc1219 shim for moved Util class |
| `IdentifierArgument` (polyfill) | Polyfill | mc1210/mc1219 shim for command argument |

## Common Pitfalls

### 1. Forgetting All Three Implementations

Always implement for **all three** API groups: mc1210, mc1219, and mc1211. The build will fail for one version if you only implement some.

### 2. Using Wrong Import in Common

```java
// WRONG - Uses version-specific class
import net.minecraft.resources.ResourceLocation;

// RIGHT - Uses compat abstraction
import dev.isotope.compat.Id;
```

### 3. Calling toMc() Without Cast

```java
// WRONG - Object type doesn't help
Object mc = id.toMc();

// RIGHT - Use generic helper
Identifier mc = id.mc();  // Typed cast via default method
```

### 4. Missing @Environment Annotation

Client-only compat classes need the annotation:

```java
@Environment(EnvType.CLIENT)
public class VersionedWidget extends AbstractWidget { ... }
```

### 5. Not Testing All API Groups

Always verify your changes compile for all API groups:

```bash
./gradlew :compat:compileJava -PmcVersion=1.21.0
./gradlew :compat:compileJava -PmcVersion=1.21.9
./gradlew :compat:compileJava -PmcVersion=1.21.11
```

### 6. Hardcoding Data Instead of Using Registries

Always prefer registry lookups over hardcoded data. Registries provide:
- Correct data for the running MC version
- Support for modded content
- Automatic updates when Minecraft changes

```java
// WRONG - Hardcoded data
private static final Map<String, Integer> MAX_LEVELS = Map.of("sharpness", 5, ...);

// RIGHT - Registry lookup with fallback
public int getMaxLevel(Id enchantmentId, RegistryAccess access) {
    try {
        Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> holder = registry.get(loc);
        if (holder.isPresent()) {
            return holder.get().value().getMaxLevel();
        }
    } catch (Exception e) {
        // Minimal fallback only
    }
    return 1;
}
```

## Quick Reference: Which Source Set?

| Code Type | Location |
|-----------|----------|
| Shared interface | `compat/src/main/java/` |
| MC 1.21.0-1.21.8 impl | `compat/src/mc1210/java/` |
| MC 1.21.9-1.21.10 impl | `compat/src/mc1219/java/` |
| MC 1.21.11+ impl | `compat/src/mc1211/java/` |
| Common mod code | `common/src/main/java/` |
| NeoForge-specific | `neoforge/src/main/java/` |
| Fabric-specific | `fabric/src/main/java/` |
| Unit tests | `compat/src/test/java/` or `common/src/test/java/` |
