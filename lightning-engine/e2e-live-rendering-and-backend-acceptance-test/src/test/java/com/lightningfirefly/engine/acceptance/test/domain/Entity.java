package com.lightningfirefly.engine.acceptance.test.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Domain object representing a game entity.
 *
 * <p>Provides a fluent, English-like API for entity operations.
 *
 * <p>Example usage:
 * <pre>{@code
 * entity.attachSprite()
 *     .using(resourceId)
 *     .at(100, 200)
 *     .sized(48, 48)
 *     .rotatedBy(45)
 *     .onLayer(10)
 *     .visible();
 * }</pre>
 */
public class Entity {

    private final Match match;
    private final long entityId;

    Entity(Match match, long entityId) {
        this.match = match;
        this.entityId = entityId;
    }

    /**
     * Get the entity ID.
     */
    public long id() {
        return entityId;
    }

    /**
     * Attach a sprite to this entity.
     *
     * @return a fluent builder for configuring the sprite
     */
    public SpriteBuilder attachSprite() {
        return new SpriteBuilder(this);
    }

    Match match() { return match; }

    /**
     * Attach a rigid body to this entity.
     *
     * @return a fluent builder for configuring the rigid body
     */
    public RigidBodyBuilder attachRigidBody() {
        return new RigidBodyBuilder(this);
    }

    /**
     * Set the velocity of this entity's rigid body.
     *
     * @return a fluent builder for setting velocity
     */
    public VelocityBuilder setVelocity() {
        return new VelocityBuilder(this);
    }

    /**
     * Apply a force to this entity's rigid body.
     *
     * @return a fluent builder for applying force
     */
    public ForceBuilder applyForce() {
        return new ForceBuilder(this);
    }

    /**
     * Attach a box collider to this entity.
     *
     * @return a fluent builder for configuring the collider
     */
    public BoxColliderBuilder attachBoxCollider() {
        return new BoxColliderBuilder(this);
    }

    /**
     * Attach a collision handler to this entity.
     * The entity must have a box collider attached first.
     *
     * @return a fluent builder for configuring the collision handler
     */
    public CollisionHandlerBuilder attachCollisionHandler() {
        return new CollisionHandlerBuilder(this);
    }

    /**
     * Attach health to this entity.
     *
     * @return a fluent builder for configuring health
     */
    public HealthBuilder attachHealth() {
        return new HealthBuilder(this);
    }

    /**
     * Deal damage to this entity.
     *
     * @param amount damage amount
     * @return this entity for chaining
     */
    public Entity damage(float amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityId", entityId);
        payload.put("amount", amount);

        try {
            match().backend().commandAdapter().submitCommand(
                    match().matchId(), "damage", entityId, payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deal damage", e);
        }

        return this;
    }

    /**
     * Heal this entity.
     *
     * @param amount heal amount
     * @return this entity for chaining
     */
    public Entity heal(float amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityId", entityId);
        payload.put("amount", amount);

        try {
            match().backend().commandAdapter().submitCommand(
                    match().matchId(), "heal", entityId, payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to heal", e);
        }

        return this;
    }

    /**
     * Fluent builder for attaching rigid bodies.
     */
    public static class RigidBodyBuilder {
        private final Entity entity;
        private float positionX = 0;
        private float positionY = 0;
        private float positionZ = 0;
        private float velocityX = 0;
        private float velocityY = 0;
        private float velocityZ = 0;
        private float mass = 1.0f;
        private float linearDrag = 0;
        private float angularDrag = 0;
        private float inertia = 1.0f;

        private RigidBodyBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the initial position.
         */
        public RigidBodyBuilder at(float x, float y) {
            this.positionX = x;
            this.positionY = y;
            return this;
        }

        /**
         * Set the initial position (3D).
         */
        public RigidBodyBuilder at(float x, float y, float z) {
            this.positionX = x;
            this.positionY = y;
            this.positionZ = z;
            return this;
        }

        /**
         * Set the initial velocity.
         */
        public RigidBodyBuilder withVelocity(float vx, float vy) {
            this.velocityX = vx;
            this.velocityY = vy;
            return this;
        }

        /**
         * Set the initial velocity (3D).
         */
        public RigidBodyBuilder withVelocity(float vx, float vy, float vz) {
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
            return this;
        }

        /**
         * Set the mass.
         */
        public RigidBodyBuilder withMass(float mass) {
            this.mass = mass;
            return this;
        }

        /**
         * Set the linear drag coefficient.
         */
        public RigidBodyBuilder withLinearDrag(float drag) {
            this.linearDrag = drag;
            return this;
        }

        /**
         * Set the angular drag coefficient.
         */
        public RigidBodyBuilder withAngularDrag(float drag) {
            this.angularDrag = drag;
            return this;
        }

        /**
         * Set the inertia.
         */
        public RigidBodyBuilder withInertia(float inertia) {
            this.inertia = inertia;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("positionX", positionX);
            payload.put("positionY", positionY);
            payload.put("positionZ", positionZ);
            payload.put("velocityX", velocityX);
            payload.put("velocityY", velocityY);
            payload.put("velocityZ", velocityZ);
            payload.put("mass", mass);
            payload.put("linearDrag", linearDrag);
            payload.put("angularDrag", angularDrag);
            payload.put("inertia", inertia);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "attachRigidBody", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to attach rigid body", e);
            }

            return entity;
        }
    }

    /**
     * Fluent builder for setting velocity.
     */
    public static class VelocityBuilder {
        private final Entity entity;
        private float velocityX = 0;
        private float velocityY = 0;
        private float velocityZ = 0;

        private VelocityBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the velocity values.
         */
        public VelocityBuilder to(float vx, float vy) {
            this.velocityX = vx;
            this.velocityY = vy;
            return this;
        }

        /**
         * Set the velocity values (3D).
         */
        public VelocityBuilder to(float vx, float vy, float vz) {
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("velocityX", velocityX);
            payload.put("velocityY", velocityY);
            payload.put("velocityZ", velocityZ);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "setVelocity", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to set velocity", e);
            }

            return entity;
        }
    }

    /**
     * Fluent builder for applying force.
     */
    public static class ForceBuilder {
        private final Entity entity;
        private float forceX = 0;
        private float forceY = 0;
        private float forceZ = 0;

        private ForceBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the force values.
         */
        public ForceBuilder of(float fx, float fy) {
            this.forceX = fx;
            this.forceY = fy;
            return this;
        }

        /**
         * Set the force values (3D).
         */
        public ForceBuilder of(float fx, float fy, float fz) {
            this.forceX = fx;
            this.forceY = fy;
            this.forceZ = fz;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("forceX", forceX);
            payload.put("forceY", forceY);
            payload.put("forceZ", forceZ);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "applyForce", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to apply force", e);
            }

            return entity;
        }
    }

    /**
     * Fluent builder for attaching box colliders.
     */
    public static class BoxColliderBuilder {
        private final Entity entity;
        private float width = 32;
        private float height = 32;
        private float depth = 1;
        private float offsetX = 0;
        private float offsetY = 0;
        private float offsetZ = 0;
        private int layer = 1;
        private int mask = -1; // All layers by default
        private boolean isTrigger = false;

        private BoxColliderBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the collider dimensions.
         */
        public BoxColliderBuilder sized(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Set the collider dimensions (3D).
         */
        public BoxColliderBuilder sized(float width, float height, float depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            return this;
        }

        /**
         * Set the offset from the entity's position.
         */
        public BoxColliderBuilder offset(float x, float y) {
            this.offsetX = x;
            this.offsetY = y;
            return this;
        }

        /**
         * Set the collision layer.
         */
        public BoxColliderBuilder onLayer(int layer) {
            this.layer = layer;
            return this;
        }

        /**
         * Set the collision mask.
         */
        public BoxColliderBuilder withMask(int mask) {
            this.mask = mask;
            return this;
        }

        /**
         * Make this a trigger collider.
         */
        public BoxColliderBuilder asTrigger() {
            this.isTrigger = true;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("width", width);
            payload.put("height", height);
            payload.put("depth", depth);
            payload.put("offsetX", offsetX);
            payload.put("offsetY", offsetY);
            payload.put("offsetZ", offsetZ);
            payload.put("layer", layer);
            payload.put("mask", mask);
            payload.put("isTrigger", isTrigger);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "attachBoxCollider", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to attach box collider", e);
            }

            return entity;
        }
    }

    /**
     * Fluent builder for attaching collision handlers.
     *
     * <p>Handlers must be registered on the server using BoxColliderModule.registerCollisionHandler().
     * Use ofType() to specify which handler type to invoke on collision.
     */
    public static class CollisionHandlerBuilder {
        private final Entity entity;
        private int handlerType = 0;
        private float param1 = 0;
        private float param2 = 0;

        private CollisionHandlerBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the handler type (must match a registered handler on the server).
         */
        public CollisionHandlerBuilder ofType(int handlerType) {
            this.handlerType = handlerType;
            return this;
        }

        /**
         * Set the first handler parameter.
         */
        public CollisionHandlerBuilder withParam1(float value) {
            this.param1 = value;
            return this;
        }

        /**
         * Set the second handler parameter.
         */
        public CollisionHandlerBuilder withParam2(float value) {
            this.param2 = value;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("handlerType", handlerType);
            payload.put("param1", param1);
            payload.put("param2", param2);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "attachCollisionHandler", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to attach collision handler", e);
            }

            return entity;
        }
    }

    /**
     * Fluent builder for attaching sprite rendering components.
     *
     * <p>Note: Sprite position comes from EntityModule's POSITION_X/POSITION_Y components,
     * which are shared with physics. Use attachRigidBody().at(x, y) to set position.
     */
    public static class SpriteBuilder {
        private final Entity entity;
        private long resourceId = 0;
        private float width = 32;
        private float height = 32;
        private float rotation = 0;
        private float zIndex = 0;
        private boolean isVisible = true;

        private SpriteBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Use the specified resource for the sprite texture.
         */
        public SpriteBuilder using(long resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        /**
         * Set the sprite dimensions.
         */
        public SpriteBuilder sized(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Rotate the sprite by the given degrees.
         */
        public SpriteBuilder rotatedBy(float degrees) {
            this.rotation = degrees;
            return this;
        }

        /**
         * Place the sprite on the given render layer.
         */
        public SpriteBuilder onLayer(float zIndex) {
            this.zIndex = zIndex;
            return this;
        }

        /**
         * Make the sprite visible (default).
         */
        public SpriteBuilder visible() {
            this.isVisible = true;
            return this;
        }

        /**
         * Make the sprite hidden.
         */
        public SpriteBuilder hidden() {
            this.isVisible = false;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            return execute();
        }

        Entity execute() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("resourceId", resourceId);
            // Note: Position comes from EntityModule's POSITION_X/POSITION_Y
            payload.put("width", width);
            payload.put("height", height);
            payload.put("rotation", rotation);
            payload.put("zIndex", zIndex);
            payload.put("visible", isVisible ? 1.0 : 0.0);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "attachSprite", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to attach sprite", e);
            }

            return entity;
        }

        // Test verification accessors
        public float width() { return width; }
        public float height() { return height; }
        public float rotation() { return rotation; }
        public float zIndex() { return zIndex; }
        public long resourceId() { return resourceId; }
    }

    /**
     * Fluent builder for attaching health.
     */
    public static class HealthBuilder {
        private final Entity entity;
        private float maxHP = 100;
        private float currentHP = -1; // -1 means use maxHP

        private HealthBuilder(Entity entity) {
            this.entity = entity;
        }

        /**
         * Set the maximum HP.
         */
        public HealthBuilder withMaxHP(float maxHP) {
            this.maxHP = maxHP;
            return this;
        }

        /**
         * Set the current HP.
         */
        public HealthBuilder withCurrentHP(float currentHP) {
            this.currentHP = currentHP;
            return this;
        }

        /**
         * Execute the command and return to the entity.
         */
        public Entity andApply() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entity.entityId);
            payload.put("maxHP", maxHP);
            payload.put("currentHP", currentHP < 0 ? maxHP : currentHP);

            try {
                entity.match().backend().commandAdapter().submitCommand(
                        entity.match().matchId(), "attachHealth", entity.entityId, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to attach health", e);
            }

            return entity;
        }
    }
}
