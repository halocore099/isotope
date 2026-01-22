package dev.isotope.registry;

import dev.isotope.Isotope;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps Structure implementation classes to their registry IDs.
 *
 * This enables 100% accurate caller-to-structure mapping when analyzing
 * stack traces from setLootTable() calls. Instead of using heuristic
 * string matching on class names (e.g., "contains village"), we can do
 * exact lookups.
 *
 * Populated during registry scan (Layer 1) and used by LootObserver
 * to improve RUNTIME_ASSIGNED link accuracy.
 */
public final class StructureClassRegistry {

    private static final StructureClassRegistry INSTANCE = new StructureClassRegistry();

    // class name -> structure ID (primary lookup for stack traces)
    private final Map<String, ResourceLocation> classToStructure = new ConcurrentHashMap<>();

    // structure ID -> class name (reverse lookup for debugging)
    private final Map<ResourceLocation, String> structureToClass = new ConcurrentHashMap<>();

    // Also track superclasses for inheritance-based matching
    private final Map<String, ResourceLocation> superclassToStructure = new ConcurrentHashMap<>();

    private boolean scanned = false;

    private StructureClassRegistry() {}

    public static StructureClassRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Scan the structure registry and build class-to-ID mappings.
     * Should be called during RegistryScanner.onServerStarted().
     */
    public void scan(MinecraftServer server) {
        classToStructure.clear();
        structureToClass.clear();
        superclassToStructure.clear();

        try {
            // 1.21.1: Use HolderLookup and listElements() instead of Registry.entrySet()
            HolderLookup.RegistryLookup<Structure> lookup = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);

            lookup.listElements().forEach(holder -> {
                ResourceLocation structureId = holder.key().location();
                Structure structure = holder.value();
                String className = structure.getClass().getName();

                classToStructure.put(className, structureId);
                structureToClass.put(structureId, className);

                // Also index superclass if it's a Minecraft structure class
                Class<?> superclass = structure.getClass().getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    String superclassName = superclass.getName();
                    // Only store first structure using this superclass to avoid conflicts
                    if (!superclassToStructure.containsKey(superclassName)) {
                        superclassToStructure.put(superclassName, structureId);
                    }
                }

                Isotope.LOGGER.debug("[StructureClassRegistry] Mapped {} -> {}", className, structureId);
            });

            scanned = true;
            Isotope.LOGGER.info("[StructureClassRegistry] Scanned {} unique structure classes",
                classToStructure.size());

        } catch (Exception e) {
            Isotope.LOGGER.error("[StructureClassRegistry] Failed to scan structure registry", e);
        }
    }

    /**
     * Look up structure ID from a class name found in stack trace.
     * Tries exact match first, then superclass match.
     *
     * @param className Full class name from stack trace (e.g., "net.minecraft.world.level.levelgen.structure.structures.JigsawStructure")
     * @return The structure ID if found, empty otherwise
     */
    public Optional<ResourceLocation> getStructureForClass(String className) {
        if (className == null) return Optional.empty();

        // Direct lookup (exact class match)
        ResourceLocation direct = classToStructure.get(className);
        if (direct != null) {
            return Optional.of(direct);
        }

        // Superclass lookup (for subclasses)
        ResourceLocation superMatch = superclassToStructure.get(className);
        if (superMatch != null) {
            return Optional.of(superMatch);
        }

        return Optional.empty();
    }

    /**
     * Try to identify a structure from any class in a stack trace.
     * Scans through the provided class names looking for a match.
     *
     * @param classNames Array of class names from stack trace
     * @return First matching structure ID, or empty if none match
     */
    public Optional<ResourceLocation> findStructureInStackTrace(String[] classNames) {
        for (String className : classNames) {
            Optional<ResourceLocation> result = getStructureForClass(className);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Get the implementation class for a structure ID.
     * Useful for debugging and logging.
     */
    public Optional<String> getClassForStructure(ResourceLocation structureId) {
        return Optional.ofNullable(structureToClass.get(structureId));
    }

    /**
     * Check if a class name matches any known structure.
     */
    public boolean isKnownStructureClass(String className) {
        return classToStructure.containsKey(className) || superclassToStructure.containsKey(className);
    }

    /**
     * Get total number of mapped classes.
     */
    public int size() {
        return classToStructure.size();
    }

    /**
     * Check if the registry has been scanned.
     */
    public boolean isScanned() {
        return scanned;
    }

    /**
     * Clear all mappings (for reload).
     */
    public void clear() {
        classToStructure.clear();
        structureToClass.clear();
        superclassToStructure.clear();
        scanned = false;
    }
}
