package dev.isotope.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin for conditional mixin loading based on Minecraft version.
 *
 * This plugin detects the Minecraft version at runtime and excludes
 * mixins that are incompatible with the current version.
 */
public class IsotopeMixinPlugin implements IMixinConfigPlugin {

    private static Boolean is121Plus = null;

    @Override
    public void onLoad(String mixinPackage) {
        // Called when mixin config loads
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // ReloadableRegistriesMixin targets ReloadableServerRegistries.Holder which only exists in 1.21+
        // On 1.20.x we skip it (LootDataManagerMixin handles 1.20.x)
        if (mixinClassName.endsWith("ReloadableRegistriesMixin")) {
            if (!is121Plus()) {
                System.out.println("[ISOTOPE] Skipping ReloadableRegistriesMixin (1.20.x uses LootDataManagerMixin)");
                return false;
            }
            System.out.println("[ISOTOPE] Applying ReloadableRegistriesMixin for 1.21+ loot table tracking");
            return true;
        }

        // LootDataManagerMixin targets LootDataManager which is used in 1.20.x for loot table access
        // On 1.21+, ReloadableServerRegistries.Holder is used instead
        if (mixinClassName.endsWith("LootDataManagerMixin")) {
            if (is121Plus()) {
                System.out.println("[ISOTOPE] Skipping LootDataManagerMixin (1.21+ uses ReloadableRegistriesMixin)");
                return false;
            }
            System.out.println("[ISOTOPE] Applying LootDataManagerMixin for 1.20.x loot table tracking");
            return true;
        }

        return true;
    }

    /**
     * Detect if we're running on 1.21+ by checking the Minecraft version.
     * ReloadableServerRegistries.Holder only exists in 1.21+.
     */
    private static boolean is121Plus() {
        if (is121Plus == null) {
            is121Plus = detectIs121Plus();
            System.out.println("[ISOTOPE] Version detection: is121Plus=" + is121Plus);
        }
        return is121Plus;
    }

    private static boolean detectIs121Plus() {
        String version = getMinecraftVersion();
        if (version != null) {
            // 1.21+ starts with "1.21" or higher
            if (version.startsWith("1.21") || version.startsWith("1.22") ||
                version.startsWith("1.23") || version.startsWith("1.24") ||
                version.startsWith("1.25") || version.startsWith("2.")) {
                return true;
            }
            return false;
        }

        // Fallback: try to load the 1.21+ class
        try {
            Class.forName("net.minecraft.server.ReloadableServerRegistries$Holder", false,
                Thread.currentThread().getContextClassLoader());
            System.out.println("[ISOTOPE] Found ReloadableServerRegistries.Holder, assuming 1.21+");
            return true;
        } catch (ClassNotFoundException e) {
            System.out.println("[ISOTOPE] ReloadableServerRegistries.Holder not found, assuming 1.20.x");
            return false;
        }
    }

    /**
     * Get the Minecraft version string from various sources.
     */
    private static String getMinecraftVersion() {
        // Try Fabric Loader first (most reliable)
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoaderClass.getMethod("getInstance").invoke(null);
            Object modContainer = fabricLoaderClass.getMethod("getModContainer", String.class)
                .invoke(instance, "minecraft");

            @SuppressWarnings("unchecked")
            java.util.Optional<?> optional = (java.util.Optional<?>) modContainer;
            if (optional.isPresent()) {
                Object container = optional.get();
                Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
                Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
                String versionStr = version.toString();
                System.out.println("[ISOTOPE] Detected Minecraft version: " + versionStr);
                return versionStr;
            }
        } catch (Exception e) {
            System.out.println("[ISOTOPE] FabricLoader version detection failed: " + e.getMessage());
        }

        // Try system property
        String mcVersion = System.getProperty("minecraft.version");
        if (mcVersion != null) {
            System.out.println("[ISOTOPE] System property minecraft.version: " + mcVersion);
            return mcVersion;
        }

        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Called after shouldApplyMixin
    }

    @Override
    public List<String> getMixins() {
        // No additional mixins to add dynamically
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Called before mixin is applied
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Called after mixin is applied
    }
}
