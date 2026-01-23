package dev.isotope.compat.impl;

import dev.isotope.compat.Id;
import dev.isotope.compat.IdFactory;
import net.minecraft.resources.ResourceKey;

/**
 * MC 1.21.11+ implementation of IdFactory.
 */
public class IdFactoryImpl implements IdFactory {

    @Override
    public Id create(String namespace, String path) {
        return IdImpl.of(namespace, path);
    }

    @Override
    public Id parse(String id) {
        return IdImpl.parse(id);
    }

    @Override
    public Id fromKey(ResourceKey<?> key) {
        return IdImpl.fromKey(key);
    }

    @Override
    public Id wrap(Object mc) {
        return IdImpl.wrap(mc);
    }

    @Override
    public Id tryParse(String id) {
        return IdImpl.tryParse(id);
    }
}
