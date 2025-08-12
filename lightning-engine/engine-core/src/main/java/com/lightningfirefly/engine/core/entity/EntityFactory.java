package com.lightningfirefly.engine.core.entity;

import com.lightningfirefly.engine.core.store.BaseComponent;

import java.util.List;

/**
 * Factory for creating entities with guaranteed match isolation.
 *
 * <p>All entities must be created through this factory to ensure
 * proper MATCH_ID component attachment. This provides:
 * <ul>
 *   <li>Match isolation - entities are bound to a specific match</li>
 *   <li>Clean shutdown - entities can be queried/deleted by match</li>
 *   <li>Consistent entity creation patterns across modules</li>
 * </ul>
 *
 * <p>Modules should obtain this factory from {@link com.lightningfirefly.engine.ext.module.ModuleContext#getEntityFactory()}.
 *
 * @see com.lightningfirefly.engine.ext.module.ModuleContext
 */
public interface EntityFactory {

    /**
     * Create an entity bound to a specific match.
     *
     * <p>The entity will automatically have the MATCH_ID component attached
     * with the provided match ID value.
     *
     * @param matchId the match this entity belongs to
     * @return the new entity ID
     */
    long createEntity(long matchId);

    /**
     * Create an entity with initial components.
     *
     * <p>The entity will automatically have the MATCH_ID component attached
     * in addition to the provided components.
     *
     * @param matchId the match this entity belongs to
     * @param components initial components to attach
     * @param values component values (must match components size)
     * @return the new entity ID
     * @throws IllegalArgumentException if components and values have different sizes
     */
    long createEntity(long matchId, List<BaseComponent> components, float[] values);

    /**
     * Delete an entity and all its components.
     *
     * @param entityId the entity to delete
     */
    void deleteEntity(long entityId);

    /**
     * Get the MATCH_ID component used by this factory.
     *
     * <p>This can be used to query entities by match ID.
     *
     * @return the MATCH_ID component
     */
    BaseComponent getMatchIdComponent();
}
