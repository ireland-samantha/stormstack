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
     * Fluent builder for attaching sprites.
     * Designed to read like English.
     */
    public static class SpriteBuilder {
        private final Entity entity;
        private long resourceId = 0;
        private float x = 0;
        private float y = 0;
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
         * Position the sprite at the given coordinates.
         */
        public SpriteBuilder at(float x, float y) {
            this.x = x;
            this.y = y;
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
            payload.put("x", x);
            payload.put("y", y);
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
        public float x() { return x; }
        public float y() { return y; }
        public float width() { return width; }
        public float height() { return height; }
        public float rotation() { return rotation; }
        public float zIndex() { return zIndex; }
        public long resourceId() { return resourceId; }
    }
}
