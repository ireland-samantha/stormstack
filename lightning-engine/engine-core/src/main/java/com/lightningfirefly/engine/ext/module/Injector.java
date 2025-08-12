package com.lightningfirefly.engine.ext.module;

import com.lightningfirefly.engine.core.store.EntityComponentStore;

/**
 * Legacy interface for module dependency injection.
 *
 * @deprecated Use {@link ModuleContext} instead. This interface is maintained
 * for backward compatibility and will be removed in a future release.
 *
 * <p>Migration guide:
 * <ul>
 *   <li>Replace {@code Injector context} with {@code ModuleContext context}</li>
 *   <li>Replace {@code context.getStoreRequired()} with {@code context.getEntityComponentStore()}</li>
 *   <li>Replace {@code context.getClass(SomeService.class)} with explicit methods like
 *       {@code context.getMatchService()}</li>
 * </ul>
 *
 * @see ModuleContext
 */
@Deprecated
public interface Injector extends ModuleContext {

    /**
     * Get the entity component store.
     *
     * @return the ECS store for managing entities and their components
     * @throws NullPointerException if no store is registered
     * @deprecated Use {@link ModuleContext#getEntityComponentStore()} instead
     */
    @Deprecated
    EntityComponentStore getStoreRequired();

    /**
     * {@inheritDoc}
     */
    @Override
    default EntityComponentStore getEntityComponentStore() {
        return getStoreRequired();
    }
}
