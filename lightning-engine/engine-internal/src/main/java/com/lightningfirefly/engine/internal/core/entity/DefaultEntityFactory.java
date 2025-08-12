package com.lightningfirefly.engine.internal.core.entity;

import com.lightningfirefly.engine.core.entity.CoreComponents;
import com.lightningfirefly.engine.core.entity.EntityFactory;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link EntityFactory}.
 *
 * <p>This factory ensures all entities are created with a MATCH_ID component
 * attached, providing match isolation for the ECS.
 */
@Slf4j
public class DefaultEntityFactory implements EntityFactory {

    private final EntityComponentStore store;
    private final AtomicLong nextEntityId;

    /**
     * Create a new entity factory.
     *
     * @param store the entity component store to create entities in
     */
    public DefaultEntityFactory(EntityComponentStore store) {
        this(store, new AtomicLong(1));
    }

    /**
     * Create a new entity factory with a custom ID generator.
     *
     * @param store the entity component store to create entities in
     * @param nextEntityId the atomic counter for generating entity IDs
     */
    public DefaultEntityFactory(EntityComponentStore store, AtomicLong nextEntityId) {
        this.store = store;
        this.nextEntityId = nextEntityId;
    }

    @Override
    public long createEntity(long matchId) {
        long entityId = nextEntityId.getAndIncrement();
        store.createEntity(entityId);
        store.attachComponent(entityId, CoreComponents.MATCH_ID, (float) matchId);
        store.attachComponent(entityId, CoreComponents.ENTITY_ID, (float) entityId);
        log.trace("Created entity {} for match {}", entityId, matchId);
        return entityId;
    }

    @Override
    public long createEntity(long matchId, List<BaseComponent> components, float[] values) {
        if (components.size() != values.length) {
            throw new IllegalArgumentException(
                    "Components size (" + components.size() + ") must match values length (" + values.length + ")");
        }

        long entityId = nextEntityId.getAndIncrement();
        store.createEntity(entityId);

        // Attach MATCH_ID first
        store.attachComponent(entityId, CoreComponents.MATCH_ID, (float) matchId);
        store.attachComponent(entityId, CoreComponents.ENTITY_ID, (float) entityId);

        // Attach provided components
        if (!components.isEmpty()) {
            store.attachComponents(entityId, components, values);
        }

        log.trace("Created entity {} for match {} with {} components", entityId, matchId, components.size());
        return entityId;
    }

    @Override
    public void deleteEntity(long entityId) {
        store.deleteEntity(entityId);
        log.trace("Deleted entity {}", entityId);
    }

    @Override
    public BaseComponent getMatchIdComponent() {
        return CoreComponents.MATCH_ID;
    }
}
