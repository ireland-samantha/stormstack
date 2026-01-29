package ca.samanthaireland.engine.ext.modules.ecs.repository;

import ca.samanthaireland.engine.core.store.EntityComponentStore;
import ca.samanthaireland.engine.ext.module.ModuleContext;
import ca.samanthaireland.engine.ext.modules.domain.Position;
import ca.samanthaireland.engine.ext.modules.domain.repository.PositionRepository;

import java.util.Optional;

import static ca.samanthaireland.engine.ext.modules.GridMapModuleFactory.*;

/**
 * ECS-backed implementation of PositionRepository.
 *
 * <p>Stores position data as POSITION_X/Y/Z components in the EntityComponentStore.
 * These are continuous (float) positions used by physics and rendering.
 *
 * <p>Note: Gets the store dynamically from context to avoid stale reference issues
 * when the module's JWT token is updated.
 */
public class EcsPositionRepository implements PositionRepository {

    private final ModuleContext context;

    public EcsPositionRepository(ModuleContext context) {
        this.context = context;
    }

    private EntityComponentStore getStore() {
        return context.getEntityComponentStore();
    }

    @Override
    public void save(long entityId, Position position) {
        EntityComponentStore store = getStore();
        store.attachComponent(entityId, POSITION_X, position.x());
        store.attachComponent(entityId, POSITION_Y, position.y());
        store.attachComponent(entityId, POSITION_Z, position.z());
    }

    @Override
    public Optional<Position> findByEntityId(long entityId) {
        EntityComponentStore store = getStore();

        // O(1) direct lookup instead of O(n) full entity scan
        if (!store.hasComponent(entityId, POSITION_X) ||
            !store.hasComponent(entityId, POSITION_Y) ||
            !store.hasComponent(entityId, POSITION_Z)) {
            return Optional.empty();
        }

        float x = store.getComponent(entityId, POSITION_X);
        float y = store.getComponent(entityId, POSITION_Y);
        float z = store.getComponent(entityId, POSITION_Z);

        return Optional.of(new Position(x, y, z));
    }
}
