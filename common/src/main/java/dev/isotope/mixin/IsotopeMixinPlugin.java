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
        // ReloadableRegistriesMixin targets ReloadableServerRegistries.Holder which only exists in 1.20.2+
        // On 1.20.1 we need to skip it (use LootDataManagerMixin instead)
        if (mixinClassName.endsWith("ReloadableRegistriesMixin")) {
            // Check if target class exists by trying to find it via class loader
            try {
                Class.forName("net.minecraft.server.ReloadableServerRegistries$Holder", false,
                    Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                System.out.println("[ISOTOPE] Skipping ReloadableRegistriesMixin - target class not found (1.20.1)");
                return false;
            }
        }

        // LootDataManagerMixin targets LootDataManager which is used differently in 1.20.1 vs 1.20.2+
        // On 1.20.2+, ReloadableServerRegistries.Holder is used instead, so skip this mixin
        if (mixinClassName.endsWith("LootDataManagerMixin")) {
            // Check if ReloadableServerRegistries.Holder exists - if so, we're on 1.20.2+
            try {
                Class.forName("net.minecraft.server.ReloadableServerRegistries$Holder", false,
                    Thread.currentThread().getContextClassLoader());
                System.out.println("[ISOTOPE] Skipping LootDataManagerMixin - using ReloadableRegistriesMixin instead (1.20.2+)");
                return false;
            } catch (ClassNotFoundException e) {
                // On 1.20.1, use LootDataManagerMixin
                System.out.println("[ISOTOPE] Applying LootDataManagerMixin for 1.20.1 loot table tracking");
            }
        }

        return true;
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

    /**
     * Detect if we're running on 1.21+ by checking for ResourceKey-based methods.
     */
    private static boolean is121Plus() {
        if (is121Plus == null) {
            try {
                // Check if RenderType.guiTextured exists (1.21+ method)
                Class.forName("net.minecraft.client.renderer.RenderType")
                    .getMethod("guiTextured", Class.forName("net.minecraft.resources.ResourceLocation"));
                is121Plus = true;
            } catch (Exception e) {
                is121Plus = false;
            }
        }
        return is121Plus;
    }
}
