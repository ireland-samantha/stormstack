package com.lightningfirefly.engine.ext.module;

import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.store.EntityComponentStore;

import java.util.Optional;

/**
 * Context provided to modules for dependency injection.
 *
 * <p>This interface provides explicit access to common dependencies that modules
 * may need. It follows the Dependency Inversion Principle (DIP) by:
 * <ul>
 *   <li>Making dependencies explicit and documented</li>
 *   <li>Allowing compile-time type safety</li>
 *   <li>Enabling proper testing with mock implementations</li>
 * </ul>
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>ISP: Clients only depend on methods they use</li>
 *   <li>DIP: High-level modules depend on abstractions</li>
 * </ul>
 *
 * <p>Available dependencies:
 * <ul>
 *   <li>{@link EntityFactory} - for creating entities with match isolation</li>
 *   <li>{@link EntityComponentStore} - for reading/updating entities and components</li>
 *   <li>{@link MatchService} - for match operations</li>
 *   <li>{@link ModuleResolver} - for resolving other modules</li>
 *   <li>{@link ModuleManager} - for module management operations</li>
 * </ul>
 *
 * @see ModuleFactory
 */
public interface ModuleContext {

    /**
     * Get the entity component store.
     *
     * <p>The store is the central registry for all entities and their components.
     * Most modules will need this dependency.
     *
     * @return the entity component store
     * @throws IllegalStateException if the store is not available
     */
    EntityComponentStore getEntityComponentStore();

    /**
     * Get the entity component store, or empty if not available.
     *
     * @return Optional containing the store, or empty
     */
    default Optional<EntityComponentStore> findEntityComponentStore() {
        try {
            return Optional.ofNullable(getEntityComponentStore());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Get the entity factory for creating match-bound entities.
     *
     * <p>All entity creation should go through this factory to ensure
     * proper MATCH_ID component attachment. This provides match isolation
     * and enables proper cleanup when matches end.
     *
     * <p>Example usage:
     * <pre>{@code
     * EntityFactory factory = context.getEntityFactory();
     * long entityId = factory.createEntity(matchId);
     * // Entity automatically has MATCH_ID component attached
     * }</pre>
     *
     * @return the entity factory
     * @throws IllegalStateException if the factory is not available
     */
    EntityFactory getEntityFactory();

    /**
     * Get the match service for match-related operations.
     *
     * @return the match service, or null if not available
     */
    MatchService getMatchService();

    /**
     * Get the module resolver for looking up other modules.
     *
     * @return the module resolver, or null if not available
     */
    ModuleResolver getModuleResolver();

    /**
     * Get the module manager for module management operations.
     *
     * <p>Note: This is typically only available within the engine-internal module.
     *
     * @return the module manager, or null if not available
     */
    Object getModuleManager();

    /**
     * Look up an arbitrary dependency by type.
     *
     * <p><b>Deprecated:</b> Prefer using the explicit getter methods
     * ({@link #getEntityComponentStore()}, {@link #getMatchService()}, etc.)
     * for better compile-time safety and documentation.
     *
     * <p>This method is provided for backward compatibility and edge cases
     * where a module needs a dependency not covered by the explicit methods.
     *
     * @param <T> the type of dependency
     * @param type the class of the dependency to look up
     * @return the dependency instance, or null if not found
     * @deprecated Use explicit getter methods instead
     */
    @Deprecated
    <T> T getClass(Class<? extends T> type);

    /**
     * Register a dependency for lookup.
     *
     * <p><b>Note:</b> This method is typically only used during initialization.
     * Modules should not need to call this method.
     *
     * @param type the class type to register
     * @param instance the instance to associate with the type
     */
    void addClass(Class<?> type, Object instance);
}
