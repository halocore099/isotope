package dev.isotope.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
 * Stonecutter conditionals handle the API differences at build time.
 */
public final class RegistryHelper {

    private RegistryHelper() {}

    // ========== Structure Registry ==========

    /**
     * Get the structure registry from a RegistryAccess.
     * Works across all supported MC versions.
     */
    public static Registry<Structure> getStructureRegistry(RegistryAccess registryAccess) {
        //? if >=1.21 {
        return registryAccess.lookupOrThrow(Registries.STRUCTURE);
        //?} else {
        /*return registryAccess.registryOrThrow(Registries.STRUCTURE);*/
        //?}
    }

    // ========== Item Registry ==========

    /**
     * Get an Item from the registry by ResourceLocation.
     * Returns Optional.empty() if the item doesn't exist.
     */
    public static Optional<Item> getItem(ResourceLocation id) {
        //? if >=1.21 {
        var itemOpt = BuiltInRegistries.ITEM.get(id);
        if (itemOpt.isEmpty()) {
            return Optional.empty();
        }
        Item item = itemOpt.get().value();
        if (item == Items.AIR) {
            return Optional.empty();
        }
        return Optional.of(item);
        //?} else {
        /*
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(id);
        if (itemOpt.isEmpty() || itemOpt.get() == Items.AIR) {
            return Optional.empty();
        }
        return itemOpt;
        */
        //?}
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
        //? if >=1.21 {
        return BuiltInRegistries.ITEM.get(id).isPresent();
        //?} else {
        /*return BuiltInRegistries.ITEM.containsKey(id);*/
        //?}
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
        //? if >=1.21 {
        var entityOpt = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entityOpt.get().value());
        //?} else {
        /*return BuiltInRegistries.ENTITY_TYPE.getOptional(id);*/
        //?}
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
        //? if >=1.21 {
        return BuiltInRegistries.ENTITY_TYPE.get(id).isPresent();
        //?} else {
        /*return BuiltInRegistries.ENTITY_TYPE.containsKey(id);*/
        //?}
    }

    // ========== Generic Registry Access ==========

    /**
     * Get a registry by its ResourceKey.
     */
    public static <T> Registry<T> getRegistry(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> key) {
        return registryAccess.lookupOrThrow(key);
    }

    /**
     * Find the ResourceLocation for a value in a registry.
     * Returns null if not found.
     */
    @Nullable
    public static <T> ResourceLocation findKey(Registry<T> registry, T value) {
        return registry.listElements()
            .filter(holder -> holder.value() == value)
            .findFirst()
            .map(holder -> holder.key().location())
            .orElse(null);
    }

    /**
     * Get a value from a registry by ResourceLocation.
     * Returns Optional.empty() if not found.
     */
    public static <T> Optional<T> getValue(Registry<T> registry, ResourceLocation id) {
        //? if >=1.21 {
        return registry.get(id).map(ref -> ref.value());
        //?} else {
        /*return registry.getOptional(id);*/
        //?}
    }

    // ========== Loot Table Registry ==========

    /**
     * Get a loot table from the server by ResourceKey.
     * Works across all supported MC versions.
     *
     * In 1.21+: Uses reloadableRegistries().getLootTable()
     * In 1.20.x: Uses getLootData().getLootTable()
     */
    public static LootTable getLootTable(MinecraftServer server, ResourceKey<LootTable> key) {
        //? if >=1.21 {
        return server.reloadableRegistries().getLootTable(key);
        //?} else {
        /*return server.getLootData().getLootTable(key.location());*/
        //?}
    }

    /**
     * Get a loot table from the server by ResourceLocation.
     * Convenience method that creates the ResourceKey internally.
     */
    public static LootTable getLootTable(MinecraftServer server, ResourceLocation id) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        return getLootTable(server, key);
    }
}
