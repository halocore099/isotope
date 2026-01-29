package dev.isotope.compat;

import dev.isotope.compat.impl.IdImpl;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MC 1.21.0-1.21.10 implementation of EnchantmentCompat.
 *
 * Uses hardcoded fallback data since the enchantment registry APIs changed
 * significantly between MC versions. This provides reliable functionality
 * for enchantment probability analysis without depending on version-specific
 * registry access patterns.
 */
public class EnchantmentCompatImpl implements EnchantmentCompat {

    // Treasure enchantments (not available from enchanting table)
    private static final Set<String> TREASURE_ENCHANTS = Set.of(
        "mending", "frost_walker", "binding_curse", "vanishing_curse",
        "soul_speed", "swift_sneak", "wind_burst"
    );

    // Very rare enchantments (weight ~1)
    private static final Set<String> VERY_RARE_ENCHANTS = Set.of(
        "infinity", "mending", "silk_touch", "fortune", "looting", "luck_of_the_sea"
    );

    // Rare enchantments (weight ~2)
    private static final Set<String> RARE_ENCHANTS = Set.of(
        "channeling", "multishot", "riptide", "thorns", "flame", "punch"
    );

    // Uncommon enchantments (weight ~5)
    private static final Set<String> UNCOMMON_ENCHANTS = Set.of(
        "fire_aspect", "knockback", "fire_protection", "feather_falling",
        "blast_protection", "projectile_protection", "aqua_affinity",
        "depth_strider", "respiration", "soul_speed", "swift_sneak"
    );

    // Enchantment max levels
    private static final Map<String, Integer> MAX_LEVELS = Map.ofEntries(
        Map.entry("sharpness", 5),
        Map.entry("smite", 5),
        Map.entry("bane_of_arthropods", 5),
        Map.entry("knockback", 2),
        Map.entry("fire_aspect", 2),
        Map.entry("looting", 3),
        Map.entry("sweeping_edge", 3),
        Map.entry("efficiency", 5),
        Map.entry("silk_touch", 1),
        Map.entry("unbreaking", 3),
        Map.entry("fortune", 3),
        Map.entry("power", 5),
        Map.entry("punch", 2),
        Map.entry("flame", 1),
        Map.entry("infinity", 1),
        Map.entry("luck_of_the_sea", 3),
        Map.entry("lure", 3),
        Map.entry("depth_strider", 3),
        Map.entry("frost_walker", 2),
        Map.entry("mending", 1),
        Map.entry("binding_curse", 1),
        Map.entry("vanishing_curse", 1),
        Map.entry("impaling", 5),
        Map.entry("riptide", 3),
        Map.entry("loyalty", 3),
        Map.entry("channeling", 1),
        Map.entry("multishot", 1),
        Map.entry("quick_charge", 3),
        Map.entry("piercing", 4),
        Map.entry("protection", 4),
        Map.entry("fire_protection", 4),
        Map.entry("feather_falling", 4),
        Map.entry("blast_protection", 4),
        Map.entry("projectile_protection", 4),
        Map.entry("respiration", 3),
        Map.entry("aqua_affinity", 1),
        Map.entry("thorns", 3),
        Map.entry("soul_speed", 3),
        Map.entry("swift_sneak", 3),
        Map.entry("breach", 4),
        Map.entry("density", 5),
        Map.entry("wind_burst", 3)
    );

    // Enchantments applicable to item categories
    private static final Set<String> SWORD_ENCHANTS = Set.of(
        "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
        "looting", "sweeping_edge", "unbreaking", "mending", "vanishing_curse"
    );

    private static final Set<String> PICKAXE_ENCHANTS = Set.of(
        "efficiency", "silk_touch", "fortune", "unbreaking", "mending", "vanishing_curse"
    );

    private static final Set<String> BOW_ENCHANTS = Set.of(
        "power", "punch", "flame", "infinity", "unbreaking", "mending", "vanishing_curse"
    );

    private static final Set<String> ARMOR_ENCHANTS = Set.of(
        "protection", "fire_protection", "blast_protection", "projectile_protection",
        "thorns", "unbreaking", "mending", "vanishing_curse", "binding_curse"
    );

    private static final Set<String> HELMET_ENCHANTS = Set.of(
        "protection", "fire_protection", "blast_protection", "projectile_protection",
        "respiration", "aqua_affinity", "thorns", "unbreaking", "mending",
        "vanishing_curse", "binding_curse"
    );

    private static final Set<String> BOOTS_ENCHANTS = Set.of(
        "protection", "fire_protection", "blast_protection", "projectile_protection",
        "feather_falling", "depth_strider", "frost_walker", "soul_speed",
        "thorns", "unbreaking", "mending", "vanishing_curse", "binding_curse"
    );

    private static final Set<String> TRIDENT_ENCHANTS = Set.of(
        "impaling", "riptide", "loyalty", "channeling", "unbreaking", "mending", "vanishing_curse"
    );

    private static final Set<String> CROSSBOW_ENCHANTS = Set.of(
        "multishot", "quick_charge", "piercing", "unbreaking", "mending", "vanishing_curse"
    );

    private static final Set<String> FISHING_ROD_ENCHANTS = Set.of(
        "luck_of_the_sea", "lure", "unbreaking", "mending", "vanishing_curse"
    );

    @Override
    public List<Id> getApplicableEnchantments(Id itemId, boolean includeTreasure, RegistryAccess access) {
        List<Id> result = new ArrayList<>();
        String itemPath = itemId.getPath();

        // Determine which enchantments apply based on item type
        Set<String> applicable = getEnchantmentsForItem(itemPath);

        for (String enchantPath : applicable) {
            if (!includeTreasure && TREASURE_ENCHANTS.contains(enchantPath)) {
                continue;
            }
            result.add(IdImpl.of("minecraft", enchantPath));
        }

        return result;
    }

    private Set<String> getEnchantmentsForItem(String itemPath) {
        if (itemPath.contains("sword") || itemPath.equals("mace")) {
            return SWORD_ENCHANTS;
        } else if (itemPath.contains("pickaxe") || itemPath.contains("axe") ||
                   itemPath.contains("shovel") || itemPath.contains("hoe")) {
            return PICKAXE_ENCHANTS;
        } else if (itemPath.equals("bow")) {
            return BOW_ENCHANTS;
        } else if (itemPath.equals("crossbow")) {
            return CROSSBOW_ENCHANTS;
        } else if (itemPath.equals("trident")) {
            return TRIDENT_ENCHANTS;
        } else if (itemPath.equals("fishing_rod")) {
            return FISHING_ROD_ENCHANTS;
        } else if (itemPath.contains("helmet") || itemPath.equals("turtle_helmet")) {
            return HELMET_ENCHANTS;
        } else if (itemPath.contains("boots")) {
            return BOOTS_ENCHANTS;
        } else if (itemPath.contains("chestplate") || itemPath.contains("leggings") ||
                   itemPath.equals("elytra")) {
            return ARMOR_ENCHANTS;
        } else if (itemPath.equals("book")) {
            // Books can have any enchantment
            return MAX_LEVELS.keySet();
        }
        // Default: basic tool enchantments
        return Set.of("unbreaking", "mending", "vanishing_curse");
    }

    @Override
    public int getMaxLevel(Id enchantmentId, RegistryAccess access) {
        return MAX_LEVELS.getOrDefault(enchantmentId.getPath(), 1);
    }

    @Override
    public int getMinLevel(Id enchantmentId, RegistryAccess access) {
        return 1;
    }

    @Override
    public String getDisplayName(Id enchantmentId, RegistryAccess access) {
        // Convert path to display name (e.g., "fire_aspect" -> "Fire Aspect")
        String path = enchantmentId.getPath();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : path.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    @Override
    public boolean isTreasure(Id enchantmentId, RegistryAccess access) {
        return TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }

    @Override
    public int getRarityWeight(Id enchantmentId, RegistryAccess access) {
        String path = enchantmentId.getPath();
        if (VERY_RARE_ENCHANTS.contains(path)) {
            return 1;
        } else if (RARE_ENCHANTS.contains(path)) {
            return 2;
        } else if (UNCOMMON_ENCHANTS.contains(path)) {
            return 5;
        }
        return 10; // Common
    }

    @Override
    public boolean isDiscoverable(Id enchantmentId, RegistryAccess access) {
        // All non-treasure enchantments are discoverable
        return !TREASURE_ENCHANTS.contains(enchantmentId.getPath());
    }
}
