package ca.samanthaireland.engine.ext.modules.domain.repository;

import ca.samanthaireland.engine.ext.modules.domain.RigidBody;
import ca.samanthaireland.engine.ext.modules.domain.Vector3;

import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for RigidBody entities.
 */
public interface RigidBodyRepository {

    /**
     * Save a rigid body's state to the ECS store.
     *
     * @param rigidBody the rigid body to save
     */
    void save(RigidBody rigidBody);

    /**
     * Find a rigid body by its entity ID.
     *
     * @param entityId the entity ID
     * @return the rigid body if found
     */
    Optional<RigidBody> findById(long entityId);

    /**
     * Get all entity IDs that have rigid body components.
     *
     * @return set of entity IDs
     */
    Set<Long> findAllIds();

    /**
     * Check if an entity has rigid body components.
     *
     * @param entityId the entity ID
     * @return true if the entity has rigid body components
     */
    boolean exists(long entityId);

    /**
     * Delete rigid body components from an entity.
     *
     * @param entityId the entity ID
     */
    void delete(long entityId);

    /**
     * Update the position of a rigid body.
     *
     * @param entityId the entity ID
     * @param position the new position
     */
    void updatePosition(long entityId, Vector3 position);

    /**
     * Update the velocity of a rigid body.
     *
     * @param entityId the entity ID
     * @param velocity the new velocity
     */
    void updateVelocity(long entityId, Vector3 velocity);

    /**
     * Update the force accumulator of a rigid body.
     *
     * @param entityId the entity ID
     * @param force the new force
     */
    void updateForce(long entityId, Vector3 force);

    /**
     * Update the acceleration of a rigid body.
     *
     * @param entityId the entity ID
     * @param acceleration the new acceleration
     */
    void updateAcceleration(long entityId, Vector3 acceleration);

    /**
     * Update angular properties of a rigid body.
     *
     * @param entityId the entity ID
     * @param angularVelocity the new angular velocity
     * @param rotation the new rotation
     * @param torque the new torque
     */
    void updateAngular(long entityId, float angularVelocity, float rotation, float torque);
}
