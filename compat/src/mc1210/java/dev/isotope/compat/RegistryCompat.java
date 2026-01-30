package dev.isotope.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * MC 1.21.0-1.21.10 registry compatibility.
 *
 * Uses reflection to handle varying registry API:
 * - MC 1.21.1-1.21.3: registry.get(ResourceKey) returns T directly
 * - MC 1.21.4+: registry.get(ResourceKey) returns Optional<Holder.Reference<T>>
 * - lookupOrThrow returns different types across versions
 */
public final class RegistryCompat {

    private static final Method REGISTRY_GET_METHOD;
    private static final Method REGISTRY_GET_HOLDER_METHOD;
    private static final Method REGISTRY_GET_VALUE_METHOD;
    private static final boolean GET_RETURNS_OPTIONAL;

    static {
        Method getMethod = null;
        Method getHolderMethod = null;
        Method getValueMethod = null;
        boolean returnsOptional = false;

        try {
            // Find get(ResourceKey) method
            for (Method method : Registry.class.getMethods()) {
                if (method.getName().equals("get") && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (paramType == ResourceKey.class) {
                        getMethod = method;
                        returnsOptional = method.getReturnType() == Optional.class;
                        break;
                    }
                }
            }

            // Find getHolder(ResourceKey) method if it exists
            for (Method method : Registry.class.getMethods()) {
                if (method.getName().equals("getHolder") && method.getParameterCount() == 1) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (paramType == ResourceKey.class) {
                        getHolderMethod = method;
                        break;
                    }
                }
            }

            // Find getValue(ResourceKey) method if it exists
            try {
                getValueMethod = Registry.class.getMethod("getValue", ResourceKey.class);
            } catch (NoSuchMethodException e) {
                // Doesn't exist in older versions
            }

        } catch (Exception e) {
            // Fallback
        }

        REGISTRY_GET_METHOD = getMethod;
        REGISTRY_GET_HOLDER_METHOD = getHolderMethod;
        REGISTRY_GET_VALUE_METHOD = getValueMethod;
        GET_RETURNS_OPTIONAL = returnsOptional;
    }

    private RegistryCompat() {}

    /**
     * Get a holder from a registry by ResourceKey.
     * Returns Optional<Holder.Reference<T>> across all versions.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Holder.Reference<T>> getHolder(Registry<T> registry, ResourceKey<T> key) {
        if (REGISTRY_GET_METHOD == null) {
            return Optional.empty();
        }

        try {
            Object result = REGISTRY_GET_METHOD.invoke(registry, key);

            if (GET_RETURNS_OPTIONAL) {
                // MC 1.21.4+: Returns Optional<Holder.Reference<T>>
                return (Optional<Holder.Reference<T>>) result;
            } else {
                // MC 1.21.1-1.21.3: Returns T directly, need to wrap
                T value = (T) result;
                if (value == null) {
                    return Optional.empty();
                }
                // Try to get the holder using reflection
                if (REGISTRY_GET_HOLDER_METHOD != null) {
                    try {
                        return (Optional<Holder.Reference<T>>) REGISTRY_GET_HOLDER_METHOD.invoke(registry, key);
                    } catch (Exception e) {
                        // Fall through
                    }
                }
                // Can't get holder, return empty
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get a value from a registry by ResourceKey.
     * Returns T or null across all versions.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Registry<T> registry, ResourceKey<T> key) {
        try {
            if (REGISTRY_GET_VALUE_METHOD != null) {
                // MC 1.21.4+: Use getValue directly
                return (T) REGISTRY_GET_VALUE_METHOD.invoke(registry, key);
            } else if (REGISTRY_GET_METHOD != null) {
                // MC 1.21.1-1.21.3: get() returns T directly
                Object result = REGISTRY_GET_METHOD.invoke(registry, key);
                if (GET_RETURNS_OPTIONAL) {
                    Optional<Holder.Reference<T>> opt = (Optional<Holder.Reference<T>>) result;
                    return opt.map(Holder.Reference::value).orElse(null);
                } else {
                    return (T) result;
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }

    /**
     * Look up a registry from RegistryAccess, returning Registry<T>.
     * Handles the varying return types of lookupOrThrow across versions.
     */
    @SuppressWarnings("unchecked")
    public static <T> Registry<T> lookupRegistry(RegistryAccess access, ResourceKey<? extends Registry<T>> registryKey) {
        try {
            // Try lookupOrThrow
            Method lookupOrThrow = null;
            for (Method method : access.getClass().getMethods()) {
                if (method.getName().equals("lookupOrThrow") && method.getParameterCount() == 1) {
                    lookupOrThrow = method;
                    break;
                }
            }

            if (lookupOrThrow != null) {
                Object result = lookupOrThrow.invoke(access, registryKey);
                // Result could be Registry<T> or RegistryLookup<T>
                if (result instanceof Registry) {
                    return (Registry<T>) result;
                } else {
                    // It's a RegistryLookup - try to get the underlying registry
                    // In newer versions, we need to cast or use a different approach
                    // For compatibility, try registry() method if available
                    try {
                        Method registryMethod = result.getClass().getMethod("registry");
                        return (Registry<T>) registryMethod.invoke(result);
                    } catch (NoSuchMethodException e) {
                        // Cast directly - may work for some implementations
                        if (result instanceof Registry) {
                            return (Registry<T>) result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fall through
        }

        // Fallback: try direct casting
        try {
            return (Registry<T>) access.lookupOrThrow(registryKey);
        } catch (Exception e) {
            return null;
        }
    }
}
