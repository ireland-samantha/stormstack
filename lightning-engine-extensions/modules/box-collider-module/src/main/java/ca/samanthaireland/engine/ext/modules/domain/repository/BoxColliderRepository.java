package ca.samanthaireland.engine.ext.modules.domain.repository;

import ca.samanthaireland.engine.ext.modules.domain.BoxCollider;
import ca.samanthaireland.engine.ext.modules.domain.CollisionHandlerConfig;

import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for BoxCollider entities.
 */
public interface BoxColliderRepository {

    /**
     * Save a box collider to an entity.
     *
     * @param entityId the entity ID
     * @param collider the box collider to save
     */
    void save(long entityId, BoxCollider collider);

    /**
     * Find a box collider by entity ID.
     *
     * @param entityId the entity ID
     * @return the box collider if found
     */
    Optional<BoxCollider> findByEntityId(long entityId);

    /**
     * Check if an entity has a box collider.
     *
     * @param entityId the entity ID
     * @return true if the entity has a box collider
     */
    boolean exists(long entityId);

    /**
     * Update the size of a box collider.
     *
     * @param entityId the entity ID
     * @param width the new width
     * @param height the new height
     */
    void updateSize(long entityId, float width, float height);

    /**
     * Update the collision layer and mask.
     *
     * @param entityId the entity ID
     * @param layer the new layer
     * @param mask the new mask
     */
    void updateLayerMask(long entityId, int layer, int mask);

    /**
     * Save collision handler configuration.
     *
     * @param entityId the entity ID
     * @param config the handler configuration
     */
    void saveHandlerConfig(long entityId, CollisionHandlerConfig config);

    /**
     * Get all entities with box colliders.
     *
     * @return set of entity IDs with box colliders
     */
    Set<Long> findAllColliderEntities();

    /**
     * Delete a box collider from an entity.
     *
     * @param entityId the entity ID
     */
    void delete(long entityId);

    /**
     * Get entity position X component value.
     *
     * @param entityId the entity ID
     * @return the position X value
     */
    float getPositionX(long entityId);

    /**
     * Get entity position Y component value.
     *
     * @param entityId the entity ID
     * @return the position Y value
     */
    float getPositionY(long entityId);

    /**
     * Get the collision handler type for an entity.
     *
     * @param entityId the entity ID
     * @return the handler type
     */
    int getHandlerType(long entityId);

    /**
     * Get collision handler param1.
     *
     * @param entityId the entity ID
     * @return the param1 value
     */
    float getHandlerParam1(long entityId);

    /**
     * Get collision handler param2.
     *
     * @param entityId the entity ID
     * @return the param2 value
     */
    float getHandlerParam2(long entityId);

    /**
     * Get the last tick when collision was handled.
     *
     * @param entityId the entity ID
     * @return the last handled tick
     */
    long getLastHandledTick(long entityId);

    /**
     * Update collision handled tick.
     *
     * @param entityId the entity ID
     * @param tick the tick value
     */
    void updateHandledTick(long entityId, long tick);

    /**
     * Update collision state for an entity.
     *
     * @param entityId the entity to update
     * @param isColliding whether the entity is colliding
     * @param collisionCount the number of collisions
     * @param lastCollisionEntity the last entity collided with
     * @param normalX collision normal X
     * @param normalY collision normal Y
     * @param penetrationDepth penetration depth
     */
    void updateCollisionState(long entityId, boolean isColliding, int collisionCount,
                              long lastCollisionEntity, float normalX, float normalY,
                              float penetrationDepth);

    /**
     * Reset collision state for an entity.
     *
     * @param entityId the entity to reset
     */
    void resetCollisionState(long entityId);
}
