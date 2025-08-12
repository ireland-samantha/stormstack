package com.lightningfirefly.engine.core.store;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Interface for entity component storage.
 *
 * <p>An entity component store manages entities identified by long IDs, with components
 * attached at specific indices. Components are stored as float values.
 *
 */
public interface EntityComponentStore {

    /**
     * Sentinel value indicating a null/missing component.
     * Using Float.NaN as the null sentinel since it's a special float value.
     */
    float NULL = Float.NaN;

    /**
     * Reset the store to its initial empty state.
     */
    void reset();

    /**
     * Create a new entity with the given ID.
     *
     * <p><b>Deprecated:</b> Use {@link com.lightningfirefly.engine.core.entity.EntityFactory#createEntity(long)}
     * instead to ensure proper MATCH_ID component attachment for match isolation.
     *
     * @param id the entity ID
     * @throws RuntimeException if the store is out of memory
     * @deprecated Use {@link com.lightningfirefly.engine.core.entity.EntityFactory#createEntity(long)} instead
     */
    @Deprecated
    void createEntity(long id);

    /**
     * Delete an entity by ID.
     *
     * @param id the entity ID to delete
     */
    void deleteEntity(long id);

    /**
     * Remove a component from an entity.
     *
     * @param id the entity ID
     * @param componentId the component index to remove
     */
    void removeComponent(long id, long componentId);

    /**
     * Remove a component from an entity.
     *
     * @param id the entity ID
     * @param component the component to remove
     */
    void removeComponent(long id, BaseComponent component);

    /**
     * Attach a component value to an entity.
     * Creates the entity if it doesn't exist.
     *
     * @param id the entity ID
     * @param componentId the component index
     * @param value the component value
     */
    void attachComponent(long id, long componentId, float value);

    /**
     * Attach a component value to an entity.
     * Creates the entity if it doesn't exist.
     *
     * @param id the entity ID
     * @param component the component
     * @param value the component value
     */
    void attachComponent(long id, BaseComponent component, float value);

    /**
     * Attach multiple components to an entity in a single operation.
     * Creates the entity if it doesn't exist.
     *
     * @param id the entity ID
     * @param componentIds array of component indices
     * @param values array of component values (must match componentIds length)
     * @throws IllegalArgumentException if arrays have different lengths
     */
    void attachComponents(long id, long[] componentIds, float[] values);

    /**
     * Attach multiple components to an entity in a single operation.
     * Creates the entity if it doesn't exist.
     *
     * @param id the entity ID
     * @param components list of components
     * @param values array of component values (must match components size)
     * @throws IllegalArgumentException if arrays have different lengths
     */
    void attachComponents(long id, List<BaseComponent> components, float[] values);

    /**
     * Query for all entities that have all specified components.
     *
     * @ai note that this is potentially expensive if we miss the cache and likely being called on a hot path, use with good judgement
     * @param componentIds the component indices to query for
     * @return set of entity IDs that have all specified components
     */
    Set<Long> getEntitiesWithComponents(long... componentIds);

    /**
     * Query for all entities that have all specified components.
     *
     * @ai note that this is potentially expensive if we miss the cache and likely being called on a hot path, use with good judgement
     * @param components the components to query for
     * @return set of entity IDs that have all specified components
     */
    Set<Long> getEntitiesWithComponents(BaseComponent... components);

    /**
     * Query for all entities that have all specified components.
     *
     * @ai note that this is potentially expensive if we miss the cache and likely being called on a hot path, use with good judgement
     * @param components the components to query for
     * @return set of entity IDs that have all specified components
     */
    Set<Long> getEntitiesWithComponents(Collection<BaseComponent> components);

    /**
     * Create a new buffer for reading component values.
     *
     * @return a new buffer array sized for this store's cardinality
     */
    float[] newBuffer();

    /**
     * Check if a value represents null/missing.
     *
     * @param value the value to check
     * @return true if the value is null (NaN)
     */
    boolean isNull(float value);

    /**
     * Check if an entity has a specific component.
     *
     * @param id the entity ID
     * @param componentId the component index
     * @return true if the entity has the component
     */
    boolean hasComponent(long id, long componentId);

    /**
     * Check if an entity has a specific component.
     *
     * @param id the entity ID
     * @param component the component
     * @return true if the entity has the component
     */
    boolean hasComponent(long id, BaseComponent component);

    /**
     * Get a single component value from an entity.
     *
     * @param id the entity ID
     * @param componentId the component index
     * @return the component value, or NULL (NaN) if not found
     */
    float getComponent(long id, long componentId);

    /**
     * Get a single component value from an entity.
     *
     * @param id the entity ID
     * @param component the component
     * @return the component value, or NULL (NaN) if not found
     */
    float getComponent(long id, BaseComponent component);

    /**
     * Get multiple component values from an entity.
     *
     * @param id the entity ID
     * @param componentIds array of component indices to get
     * @param buf buffer to receive values (must match componentIds length)
     * @throws IllegalArgumentException if buffer length doesn't match componentIds length
     */
    void getComponents(long id, long[] componentIds, float[] buf);

    /**
     * Get multiple component values from an entity.
     *
     * @param id the entity ID
     * @param components list of components to get
     * @param buf buffer to receive values (must match components size)
     * @throws IllegalArgumentException if buffer length doesn't match components size
     */
    void getComponents(long id, List<BaseComponent> components, float[] buf);
}
