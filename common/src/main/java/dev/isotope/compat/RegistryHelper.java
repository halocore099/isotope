package dev.isotope.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abstraction layer for registry access differences between MC versions.
 *
 * For 1.21.x: Uses the new registry API with Optional<Holder.Reference<T>>
 * For 1.20.x: Uses the old API with getOptional() returning Optional<T>
 *
 * Uses reflection for version-specific methods to allow a single codebase
 * to work across multiple MC versions.
 */
public final class RegistryHelper {

    private RegistryHelper() {}

    // Cache for reflection methods
    private static Method resourceLocationParse;
    private static Method resourceLocationTryParse;
    private static Method resourceLocationFromNsPath;
    private static Method reloadableRegistriesMethod;
    private static Method getLootDataMethod;
    private static boolean reflectionInitialized = false;

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            // Try 1.21+ methods
            resourceLocationParse = ResourceLocation.class.getMethod("parse", String.class);
        } catch (NoSuchMethodException e) {
            // 1.20.x - use constructor
        }

        try {
            resourceLocationTryParse = ResourceLocation.class.getMethod("tryParse", String.class);
        } catch (NoSuchMethodException e) {
            // 1.20.x - use constructor with try-catch
        }

        try {
            resourceLocationFromNsPath = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class);
        } catch (NoSuchMethodException e) {
            // 1.20.x - use constructor
        }

        try {
            // 1.21+: server.reloadableRegistries()
            reloadableRegistriesMethod = MinecraftServer.class.getMethod("reloadableRegistries");
        } catch (NoSuchMethodException e) {
            // 1.20.x - use getLootData()
        }

        try {
            // 1.20.x: server.getLootData()
            getLootDataMethod = MinecraftServer.class.getMethod("getLootData");
        } catch (NoSuchMethodException e) {
            // 1.21+ - use reloadableRegistries()
        }
    }

    // ========== ResourceLocation Parsing ==========

    /**
     * Parse a ResourceLocation from a string.
     * Uses ResourceLocation.parse() on 1.21+, constructor on 1.20.x.
     */
    @SuppressWarnings("deprecation")
    public static ResourceLocation parseLocation(String id) {
        initReflection();
        // Try 1.21+ method first: ResourceLocation.parse(String)
        if (resourceLocationParse != null) {
            try {
                return (ResourceLocation) resourceLocationParse.invoke(null, id);
            } catch (Exception e) {
                // Continue to fallback
            }
        }
        // 1.20.x fallback: use constructor via reflection (private in 1.21+)
        try {
            java.lang.reflect.Constructor<ResourceLocation> ctor =
                ResourceLocation.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ResourceLocation from: " + id, e);
        }
    }

    /**
     * Try to parse a ResourceLocation from a string.
     * Returns null if the string is invalid.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static ResourceLocation tryParseLocation(String id) {
        initReflection();
        // Try 1.21+ method first: ResourceLocation.tryParse(String)
        if (resourceLocationTryParse != null) {
            try {
                return (ResourceLocation) resourceLocationTryParse.invoke(null, id);
            } catch (Exception e) {
                // Continue to fallback
            }
        }
        // 1.20.x fallback: use constructor via reflection
        try {
            java.lang.reflect.Constructor<ResourceLocation> ctor =
                ResourceLocation.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a ResourceLocation from namespace and path.
     */
    @SuppressWarnings("deprecation")
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        initReflection();
        // Try 1.21+ method first: ResourceLocation.fromNamespaceAndPath(String, String)
        if (resourceLocationFromNsPath != null) {
            try {
                return (ResourceLocation) resourceLocationFromNsPath.invoke(null, namespace, path);
            } catch (Exception e) {
                // Continue to fallback
            }
        }
        // 1.20.x fallback: use two-argument constructor via reflection
        try {
            java.lang.reflect.Constructor<ResourceLocation> ctor =
                ResourceLocation.class.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(namespace, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ResourceLocation from: " + namespace + ":" + path, e);
        }
    }

    /**
     * Create a ResourceLocation with the minecraft namespace.
     */
    @SuppressWarnings("deprecation")
    public static ResourceLocation withDefaultNamespace(String path) {
        return fromNamespaceAndPath("minecraft", path);
    }

    // ========== Structure Registry ==========

    /**
     * Get the structure registry from a RegistryAccess.
     * Works across all supported MC versions.
     */
    @SuppressWarnings("unchecked")
    public static Registry<Structure> getStructureRegistry(RegistryAccess registryAccess) {
        try {
            // Get Registries.STRUCTURE via reflection
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Object structureKey = registriesClass.getField("STRUCTURE").get(null);

            // Try 1.20.x method first: registryOrThrow (returns Registry directly)
            try {
                Method registry = RegistryAccess.class.getMethod("registryOrThrow", ResourceKey.class);
                Object result = registry.invoke(registryAccess, structureKey);
                if (result instanceof Registry<?>) {
                    return (Registry<Structure>) result;
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, continue to 1.21+ method
            }

            // Try 1.21+ method: lookupOrThrow (returns HolderLookup.RegistryLookup which IS-A Registry)
            try {
                Method lookup = RegistryAccess.class.getMethod("lookupOrThrow", ResourceKey.class);
                Object result = lookup.invoke(registryAccess, structureKey);
                if (result instanceof Registry<?>) {
                    return (Registry<Structure>) result;
                }
                // In 1.21+, the result might be a RegistryLookup - try to cast it
                // RegistryLookup extends HolderLookup which can iterate entries
                throw new RuntimeException("lookupOrThrow returned unexpected type: " + result.getClass().getName());
            } catch (NoSuchMethodException e) {
                // Neither method exists
                throw new RuntimeException("Neither registryOrThrow nor lookupOrThrow found");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get structure registry", e);
        }
    }

    // ========== Item Registry ==========

    /**
     * Get an Item from the registry by ResourceLocation.
     * Returns Optional.empty() if the item doesn't exist.
     */
    public static Optional<Item> getItem(ResourceLocation id) {
        try {
            // Try to use the registry.get() method and check if it returns Optional
            Object result = BuiltInRegistries.ITEM.get(id);
            if (result instanceof Optional<?>) {
                Optional<?> opt = (Optional<?>) result;
                if (opt.isEmpty()) return Optional.empty();
                Object holder = opt.get();
                // 1.21+ returns Holder.Reference<Item>
                Method valueMethod = holder.getClass().getMethod("value");
                Item item = (Item) valueMethod.invoke(holder);
                if (item == Items.AIR) return Optional.empty();
                return Optional.of(item);
            }
        } catch (Exception e) {
            // Fallback to 1.20.x behavior
        }

        // 1.20.x: getOptional() returns Optional<Item> directly
        try {
            Method getOptional = BuiltInRegistries.ITEM.getClass().getMethod("getOptional", ResourceLocation.class);
            Optional<Item> itemOpt = (Optional<Item>) getOptional.invoke(BuiltInRegistries.ITEM, id);
            if (itemOpt.isEmpty() || itemOpt.get() == Items.AIR) {
                return Optional.empty();
            }
            return itemOpt;
        } catch (Exception e) {
            // Last resort - use containsKey (works in all versions) but return empty
            // since we can't safely call get() without knowing the return type
            return Optional.empty();
        }
    }

    /**
     * Get an ItemStack from the registry by ResourceLocation.
     * Returns ItemStack.EMPTY if the item doesn't exist.
     */
    public static ItemStack getItemStack(ResourceLocation id) {
        return getItemStack(id, 1);
    }

    /**
     * Get an ItemStack with count from the registry by ResourceLocation.
     * Returns ItemStack.EMPTY if the item doesn't exist.
     */
    public static ItemStack getItemStack(ResourceLocation id, int count) {
        return getItem(id)
            .map(item -> new ItemStack(item, count))
            .orElse(ItemStack.EMPTY);
    }

    /**
     * Check if an item exists in the registry.
     */
    public static boolean itemExists(ResourceLocation id) {
        // containsKey works in all versions
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    /**
     * Get the ResourceLocation of an Item.
     */
    public static ResourceLocation getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * Iterate over all items in the registry.
     * Returns a map of ResourceLocation to Item.
     */
    public static Map<ResourceLocation, Item> getAllItems() {
        Map<ResourceLocation, Item> items = new LinkedHashMap<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            items.put(entry.getKey().location(), entry.getValue());
        }
        return items;
    }

    /**
     * Get all item namespaces registered.
     */
    public static Set<String> getAllItemNamespaces() {
        Set<String> namespaces = new TreeSet<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            namespaces.add(entry.getKey().location().getNamespace());
        }
        return namespaces;
    }

    // ========== Entity Type Registry ==========

    /**
     * Get an EntityType from the registry by ResourceLocation.
     * Returns Optional.empty() if the entity type doesn't exist.
     */
    public static Optional<EntityType<?>> getEntityType(ResourceLocation id) {
        try {
            // Try to use the registry.get() method and check if it returns Optional
            Object result = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (result instanceof Optional<?>) {
                Optional<?> opt = (Optional<?>) result;
                if (opt.isEmpty()) return Optional.empty();
                Object holder = opt.get();
                // 1.21+ returns Holder.Reference<EntityType>
                Method valueMethod = holder.getClass().getMethod("value");
                return Optional.of((EntityType<?>) valueMethod.invoke(holder));
            }
        } catch (Exception e) {
            // Fallback to 1.20.x behavior
        }

        // 1.20.x: getOptional
        try {
            Method getOptional = BuiltInRegistries.ENTITY_TYPE.getClass().getMethod("getOptional", ResourceLocation.class);
            return (Optional<EntityType<?>>) getOptional.invoke(BuiltInRegistries.ENTITY_TYPE, id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get the ResourceLocation of an EntityType.
     */
    public static ResourceLocation getEntityTypeId(EntityType<?> entityType) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    }

    /**
     * Check if an entity type exists in the registry.
     */
    public static boolean entityTypeExists(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    // ========== Generic Registry Access ==========

    /**
     * Get a registry by its ResourceKey.
     */
    public static <T> Registry<T> getRegistry(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> key) {
        try {
            Method lookup = RegistryAccess.class.getMethod("lookupOrThrow", ResourceKey.class);
            return (Registry<T>) lookup.invoke(registryAccess, key);
        } catch (Exception e) {
            try {
                Method registry = RegistryAccess.class.getMethod("registryOrThrow", ResourceKey.class);
                return (Registry<T>) registry.invoke(registryAccess, key);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to get registry for " + key, ex);
            }
        }
    }

    /**
     * Find the ResourceLocation for a value in a registry.
     * Returns null if not found.
     */
    @Nullable
    public static <T> ResourceLocation findKey(Registry<T> registry, T value) {
        // getKey works in all versions
        return registry.getKey(value);
    }

    /**
     * Get a value from a registry by ResourceLocation.
     * Returns Optional.empty() if not found.
     */
    public static <T> Optional<T> getValue(Registry<T> registry, ResourceLocation id) {
        try {
            // Try 1.21+ API
            Object result = registry.get(id);
            if (result instanceof Optional<?>) {
                Optional<?> opt = (Optional<?>) result;
                if (opt.isEmpty()) return Optional.empty();
                Object holder = opt.get();
                Method valueMethod = holder.getClass().getMethod("value");
                return Optional.of((T) valueMethod.invoke(holder));
            }
        } catch (Exception e) {
            // Fallback
        }

        // 1.20.x
        try {
            Method getOptional = registry.getClass().getMethod("getOptional", ResourceLocation.class);
            return (Optional<T>) getOptional.invoke(registry, id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ========== Loot Table Registry ==========

    /**
     * Get a loot table from the server by ResourceKey.
     * Works across all supported MC versions.
     */
    public static LootTable getLootTable(MinecraftServer server, ResourceKey<LootTable> key) {
        initReflection();

        // Try 1.21+ method first: server.reloadableRegistries().getLootTable(key)
        if (reloadableRegistriesMethod != null) {
            try {
                Object holder = reloadableRegistriesMethod.invoke(server);
                Method getLootTable = holder.getClass().getMethod("getLootTable", ResourceKey.class);
                return (LootTable) getLootTable.invoke(holder, key);
            } catch (Exception e) {
                // Fallback to 1.20.x
            }
        }

        // Try 1.20.x method: server.getLootData().getLootTable(location)
        if (getLootDataMethod != null) {
            try {
                Object lootData = getLootDataMethod.invoke(server);
                Method getLootTable = lootData.getClass().getMethod("getLootTable", ResourceLocation.class);
                return (LootTable) getLootTable.invoke(lootData, key.location());
            } catch (Exception e) {
                throw new RuntimeException("Failed to get loot table: " + key, e);
            }
        }

        throw new RuntimeException("No loot table access method available for MC version");
    }

    /**
     * Get a loot table from the server by ResourceLocation.
     * Convenience method that creates the ResourceKey internally.
     */
    public static LootTable getLootTable(MinecraftServer server, ResourceLocation id) {
        // Create ResourceKey via reflection since Registries.LOOT_TABLE doesn't exist in 1.20.x
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Object lootTableRegistryKey = registriesClass.getField("LOOT_TABLE").get(null);
            ResourceKey<LootTable> key = ResourceKey.create((ResourceKey) lootTableRegistryKey, id);
            return getLootTable(server, key);
        } catch (Exception e) {
            // In 1.20.x, we can't easily create the ResourceKey, so call getLootTable directly via reflection
            initReflection();
            if (getLootDataMethod != null) {
                try {
                    Object lootData = getLootDataMethod.invoke(server);
                    Method getLootTable = lootData.getClass().getMethod("getLootTable", ResourceLocation.class);
                    return (LootTable) getLootTable.invoke(lootData, id);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to get loot table: " + id, ex);
                }
            }
            throw new RuntimeException("No loot table access method available for MC version");
        }
    }
}
