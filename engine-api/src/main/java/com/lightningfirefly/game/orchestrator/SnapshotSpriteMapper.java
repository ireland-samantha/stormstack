package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.game.domain.Sprite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Converts ECS snapshots to game sprites.
 *
 * <p>This mapper extracts entity data from snapshots and creates Sprite objects
 * that can be rendered. It provides sensible defaults and is configurable for
 * different component naming conventions.
 *
 * <p>Example usage:
 * <pre>{@code
 * SnapshotSpriteMapper mapper = new SnapshotSpriteMapper()
 *     .positionComponents("POSITION_X", "POSITION_Y")
 *     .sizeComponents("SIZE_X", "SIZE_Y")
 *     .resourceIdComponent("RESOURCE_ID")
 *     .textureResolver(resourceId -> "/textures/sprite_" + resourceId + ".png");
 *
 * gameRenderer.setSpriteMapper(mapper);
 * }</pre>
 */
public class SnapshotSpriteMapper implements SpriteMapper {

    // Component name configuration
    private String entityIdComponent = "ENTITY_ID";
    private String positionXComponent = "POSITION_X";
    private String positionYComponent = "POSITION_Y";
    private String sizeXComponent = "SIZE_X";
    private String sizeYComponent = "SIZE_Y";
    private String rotationComponent = "ROTATION";
    private String zIndexComponent = "Z_INDEX";
    private String resourceIdComponent = "RESOURCE_ID";
    private String visibleComponent = "VISIBLE";

    // Default values
    private float defaultWidth = 32;
    private float defaultHeight = 32;
    private int defaultZIndex = 0;

    // Texture resolution
    private Function<Long, String> textureResolver = resourceId -> null;

    // Cache for entity sprites (keyed by entity ID)
    private final Map<Long, Sprite> spriteCache = new HashMap<>();

    /**
     * Create a new SnapshotSpriteMapper with default configuration.
     */
    public SnapshotSpriteMapper() {
    }

    /**
     * Configure the entity ID component name.
     */
    public SnapshotSpriteMapper entityIdComponent(String componentName) {
        this.entityIdComponent = componentName;
        return this;
    }

    /**
     * Configure position component names.
     */
    public SnapshotSpriteMapper positionComponents(String xComponent, String yComponent) {
        this.positionXComponent = xComponent;
        this.positionYComponent = yComponent;
        return this;
    }

    /**
     * Configure size component names.
     */
    public SnapshotSpriteMapper sizeComponents(String xComponent, String yComponent) {
        this.sizeXComponent = xComponent;
        this.sizeYComponent = yComponent;
        return this;
    }

    /**
     * Configure the rotation component name.
     */
    public SnapshotSpriteMapper rotationComponent(String componentName) {
        this.rotationComponent = componentName;
        return this;
    }

    /**
     * Configure the z-index component name.
     */
    public SnapshotSpriteMapper zIndexComponent(String componentName) {
        this.zIndexComponent = componentName;
        return this;
    }

    /**
     * Configure the resource ID component name (for texture lookup).
     */
    public SnapshotSpriteMapper resourceIdComponent(String componentName) {
        this.resourceIdComponent = componentName;
        return this;
    }

    /**
     * Configure the visibility component name.
     */
    public SnapshotSpriteMapper visibleComponent(String componentName) {
        this.visibleComponent = componentName;
        return this;
    }

    /**
     * Set default sprite dimensions.
     */
    public SnapshotSpriteMapper defaultSize(float width, float height) {
        this.defaultWidth = width;
        this.defaultHeight = height;
        return this;
    }

    /**
     * Set default z-index.
     */
    public SnapshotSpriteMapper defaultZIndex(int zIndex) {
        this.defaultZIndex = zIndex;
        return this;
    }

    /**
     * Set the texture resolver function.
     * This function converts resource IDs to texture file paths.
     */
    public SnapshotSpriteMapper textureResolver(Function<Long, String> resolver) {
        this.textureResolver = resolver;
        return this;
    }

    @Override
    public List<Sprite> map(Object snapshotObj) {
        List<Sprite> sprites = new ArrayList<>();

        // todo: add more robust snapshot DTO
        if (!(snapshotObj instanceof Snapshot snapshot)) {
            return sprites;
        }

        if (snapshot.snapshot() == null) {
            return sprites;
        }

        // Iterate through all modules in the snapshot
        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : snapshot.snapshot().entrySet()) {
            Map<String, List<Float>> moduleData = moduleEntry.getValue();

            // Get entity IDs from this module
            List<Float> entityIds = moduleData.get(entityIdComponent);
            if (entityIds == null || entityIds.isEmpty()) {
                continue;
            }

            // Extract component data
            List<Float> posX = moduleData.getOrDefault(positionXComponent, List.of());
            List<Float> posY = moduleData.getOrDefault(positionYComponent, List.of());
            List<Float> sizeX = moduleData.getOrDefault(sizeXComponent, List.of());
            List<Float> sizeY = moduleData.getOrDefault(sizeYComponent, List.of());
            List<Float> rotations = moduleData.getOrDefault(rotationComponent, List.of());
            List<Float> zIndexes = moduleData.getOrDefault(zIndexComponent, List.of());
            List<Float> resourceIds = moduleData.getOrDefault(resourceIdComponent, List.of());
            List<Float> visibles = moduleData.getOrDefault(visibleComponent, List.of());

            // Create sprites for each entity
            for (int i = 0; i < entityIds.size(); i++) {
                long entityId = entityIds.get(i).longValue();

                // Get or create sprite
                Sprite sprite = spriteCache.computeIfAbsent(entityId, Sprite::new);

                // Update position
                if (i < posX.size()) sprite.setX(posX.get(i));
                if (i < posY.size()) sprite.setY(posY.get(i));

                // Update size
                if (i < sizeX.size()) {
                    sprite.setWidth(sizeX.get(i));
                } else {
                    sprite.setWidth(defaultWidth);
                }
                if (i < sizeY.size()) {
                    sprite.setHeight(sizeY.get(i));
                } else {
                    sprite.setHeight(defaultHeight);
                }

                // Update rotation
                if (i < rotations.size()) {
                    sprite.setRotation(rotations.get(i));
                }

                // Update z-index
                if (i < zIndexes.size()) {
                    sprite.setZIndex(zIndexes.get(i).intValue());
                } else {
                    sprite.setZIndex(defaultZIndex);
                }

                // Update visibility
                if (i < visibles.size()) {
                    sprite.setVisible(visibles.get(i) != 0);
                } else {
                    sprite.setVisible(true);
                }

                // Update texture from resource ID
                if (i < resourceIds.size()) {
                    long resourceId = resourceIds.get(i).longValue();
                    if (resourceId > 0) {
                        String texturePath = textureResolver.apply(resourceId);
                        sprite.setTexturePath(texturePath);
                    }
                }

                sprites.add(sprite);
            }
        }

        return sprites;
    }

    /**
     * Clear the sprite cache.
     * Call this when starting a new game or match.
     */
    public void clearCache() {
        spriteCache.clear();
    }
}
