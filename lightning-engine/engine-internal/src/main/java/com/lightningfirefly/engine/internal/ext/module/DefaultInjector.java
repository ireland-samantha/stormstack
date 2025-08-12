package com.lightningfirefly.engine.internal.ext.module;

import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import com.lightningfirefly.engine.ext.module.Injector;
import com.lightningfirefly.engine.ext.module.ModuleResolver;
import com.lightningfirefly.engine.internal.core.entity.DefaultEntityFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link Injector} and {@link com.lightningfirefly.engine.ext.module.ModuleContext}.
 *
 * <p>Provides modules with access to engine services through both explicit typed
 * methods and a general-purpose lookup. The explicit methods are preferred as they
 * provide compile-time type safety and better documentation.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>DIP: Implements ModuleContext abstraction</li>
 *   <li>SRP: Only responsible for dependency resolution</li>
 * </ul>
 *
 * <p>Thread-safe: All operations use ConcurrentHashMap for thread safety.
 *
 * @see Injector
 * @see com.lightningfirefly.engine.ext.module.ModuleContext
 */
@Slf4j
public class DefaultInjector implements Injector {

    private final ConcurrentHashMap<Class<?>, Object> beans = new ConcurrentHashMap<>();

    // Cached references for explicit getters
    private volatile EntityComponentStore entityComponentStore;
    private volatile EntityFactory entityFactory;
    private volatile MatchService matchService;
    private volatile ModuleResolver moduleResolver;
    private volatile Object moduleManager;

    @Override
    @Deprecated
    public EntityComponentStore getStoreRequired() {
        EntityComponentStore store = getEntityComponentStore();
        return Objects.requireNonNull(store, "EntityComponentStore not registered in ModuleContext");
    }

    @Override
    public EntityComponentStore getEntityComponentStore() {
        if (entityComponentStore == null) {
            entityComponentStore = getClass(EntityComponentStore.class);
        }
        return entityComponentStore;
    }

    @Override
    public EntityFactory getEntityFactory() {
        if (entityFactory == null) {
            // Try to get from registered beans first
            EntityFactory registered = getClass(EntityFactory.class);
            if (registered != null) {
                entityFactory = registered;
            } else {
                // Create a default factory using the entity component store
                EntityComponentStore store = getEntityComponentStore();
                if (store == null) {
                    throw new IllegalStateException("EntityComponentStore not available - cannot create EntityFactory");
                }
                entityFactory = new DefaultEntityFactory(store);
                // Register it for future lookups
                beans.put(EntityFactory.class, entityFactory);
            }
        }
        return entityFactory;
    }

    @Override
    public MatchService getMatchService() {
        if (matchService == null) {
            matchService = getClass(MatchService.class);
        }
        return matchService;
    }

    @Override
    public ModuleResolver getModuleResolver() {
        if (moduleResolver == null) {
            moduleResolver = getClass(ModuleResolver.class);
        }
        return moduleResolver;
    }

    @Override
    public Object getModuleManager() {
        if (moduleManager == null) {
            // Use string lookup to avoid circular dependency with ModuleManager
            moduleManager = beans.get(loadModuleManagerClass());
        }
        return moduleManager;
    }

    private Class<?> loadModuleManagerClass() {
        try {
            return Class.forName("com.lightningfirefly.engine.internal.ext.module.ModuleManager");
        } catch (ClassNotFoundException e) {
            log.debug("ModuleManager class not available");
            return null;
        }
    }

    @Override
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T getClass(Class<? extends T> bean) {
        log.debug("Get bean {}", bean);
        return (T) beans.get(bean);
    }

    @Override
    public void addClass(Class<?> clazz, Object impl) {
        log.debug("Add bean {}", clazz);
        beans.put(clazz, impl);
        // Invalidate cached references when dependencies change
        invalidateCaches();
    }

    private void invalidateCaches() {
        entityComponentStore = null;
        entityFactory = null;
        matchService = null;
        moduleResolver = null;
        moduleManager = null;
    }

    /**
     * Clear all registered beans.
     * For testing purposes.
     */
    public void clear() {
        beans.clear();
        invalidateCaches();
    }

    /**
     * Get the number of registered beans.
     * For testing purposes.
     *
     * @return the number of registered beans
     */
    public int size() {
        return beans.size();
    }
}
