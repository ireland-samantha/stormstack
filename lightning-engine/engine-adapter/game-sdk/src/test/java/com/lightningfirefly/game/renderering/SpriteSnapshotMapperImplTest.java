package com.lightningfirefly.game.renderering;

import com.lightningfirefly.game.domain.Sprite;
import com.lightningfirefly.game.orchestrator.Snapshot;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SnapshotSpriteMapper.
 */
class SpriteSnapshotMapperImplTest {

    private SpriteSnapshotMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new SpriteSnapshotMapperImpl();
    }

    @Nested
    @DisplayName("Basic Mapping")
    class BasicMapping {

        @Test
        @DisplayName("maps entity ID and position from components using default SPRITE_ prefix")
        void mapsEntityIdAndPosition() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_X", List.of(100.0f),
                            "SPRITE_Y", List.of(200.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites).hasSize(1);
            assertThat(sprites.get(0).getEntityId()).isEqualTo(1);
            assertThat(sprites.get(0).getX()).isEqualTo(100.0f);
            assertThat(sprites.get(0).getY()).isEqualTo(200.0f);
        }

        @Test
        @DisplayName("maps multiple entities")
        void mapsMultipleEntities() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f, 2.0f, 3.0f),
                            "SPRITE_X", List.of(100.0f, 200.0f, 300.0f),
                            "SPRITE_Y", List.of(50.0f, 60.0f, 70.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites).hasSize(3);
            assertThat(sprites.get(0).getEntityId()).isEqualTo(1);
            assertThat(sprites.get(1).getEntityId()).isEqualTo(2);
            assertThat(sprites.get(2).getEntityId()).isEqualTo(3);
        }

        @Test
        @DisplayName("handles null components")
        void handlesNullSnapshot() {
            List<Sprite> sprites = mapper.spritesFromSnapshot(null);

            assertThat(sprites).isEmpty();
        }

        @Test
        @DisplayName("handles empty components")
        void handlesEmptySnapshot() {
            Snapshot snapshot = createSnapshot(Map.of());

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites).isEmpty();
        }
    }

    @Nested
    @DisplayName("Size and Rotation")
    class SizeAndRotation {

        @Test
        @DisplayName("maps size using default SPRITE_WIDTH/HEIGHT components")
        void mapsCustomSize() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_WIDTH", List.of(64.0f),
                            "SPRITE_HEIGHT", List.of(32.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getWidth()).isEqualTo(64.0f);
            assertThat(sprites.get(0).getHeight()).isEqualTo(32.0f);
        }

        @Test
        @DisplayName("uses default size when not provided")
        void usesDefaultSize() {
            mapper.defaultSize(48, 48);

            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getWidth()).isEqualTo(48.0f);
            assertThat(sprites.get(0).getHeight()).isEqualTo(48.0f);
        }

        @Test
        @DisplayName("maps rotation using default SPRITE_ROTATION component")
        void mapsRotation() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_ROTATION", List.of(45.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getRotation()).isEqualTo(45.0f);
        }
    }

    @Nested
    @DisplayName("Z-Index and Visibility")
    class ZIndexAndVisibility {

        @Test
        @DisplayName("maps z-index using default SPRITE_Z_INDEX component")
        void mapsZIndex() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_Z_INDEX", List.of(5.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getZIndex()).isEqualTo(5);
        }

        @Test
        @DisplayName("maps visibility using default SPRITE_VISIBLE component")
        void mapsVisibility() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f, 2.0f),
                            "SPRITE_VISIBLE", List.of(1.0f, 0.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).isVisible()).isTrue();
            assertThat(sprites.get(1).isVisible()).isFalse();
        }
    }

    @Nested
    @DisplayName("Texture Resolution")
    class TextureResolution {

        @Test
        @DisplayName("resolves texture from resource ID")
        void resolvesTextureFromResourceId() {
            mapper.textureResolver(resourceId -> "/textures/sprite_" + resourceId + ".png");

            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "RESOURCE_ID", List.of(42.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getTexturePath()).isEqualTo("/textures/sprite_42.png");
        }

        @Test
        @DisplayName("handles zero resource ID")
        void handlesZeroResourceId() {
            mapper.textureResolver(resourceId -> "/textures/" + resourceId + ".png");

            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "RESOURCE_ID", List.of(0.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            // Zero resource ID should not set texture
            assertThat(sprites.get(0).getTexturePath()).isNull();
        }
    }

    @Nested
    @DisplayName("Custom Component Names")
    class CustomComponentNames {

        @Test
        @DisplayName("supports custom position component names")
        void supportsCustomPositionNames() {
            mapper.positionComponents("X", "Y");

            Snapshot snapshot = createSnapshot(Map.of(
                    "TestModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "X", List.of(100.0f),
                            "Y", List.of(200.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites.get(0).getX()).isEqualTo(100.0f);
            assertThat(sprites.get(0).getY()).isEqualTo(200.0f);
        }

        @Test
        @DisplayName("supports custom entity ID component name")
        void supportsCustomEntityIdName() {
            mapper.entityIdComponent("ID");

            Snapshot snapshot = createSnapshot(Map.of(
                    "TestModule", Map.of(
                            "ID", List.of(1.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites).hasSize(1);
            assertThat(sprites.get(0).getEntityId()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Multiple Modules")
    class MultipleModules {

        @Test
        @DisplayName("combines entities from multiple modules")
        void combinesMultipleModules() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "ModuleA", Map.of(
                            "ENTITY_ID", List.of(1.0f, 2.0f),
                            "SPRITE_X", List.of(100.0f, 200.0f)
                    ),
                    "ModuleB", Map.of(
                            "ENTITY_ID", List.of(3.0f),
                            "SPRITE_X", List.of(300.0f)
                    )
            ));

            List<Sprite> sprites = mapper.spritesFromSnapshot(snapshot);

            assertThat(sprites).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        @DisplayName("reuses sprite instances across calls")
        void reusesSpriteInstances() {
            Snapshot snapshot1 = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_X", List.of(100.0f)
                    )
            ));

            List<Sprite> sprites1 = mapper.spritesFromSnapshot(snapshot1);
            Sprite sprite1 = sprites1.get(0);

            Snapshot snapshot2 = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "SPRITE_X", List.of(200.0f)
                    )
            ));

            List<Sprite> sprites2 = mapper.spritesFromSnapshot(snapshot2);
            Sprite sprite2 = sprites2.get(0);

            // Same instance should be returned
            assertThat(sprite1).isSameAs(sprite2);
            // But position should be updated
            assertThat(sprite2.getX()).isEqualTo(200.0f);
        }

        @Test
        @DisplayName("clearCache removes cached sprites")
        void clearCacheRemovesCachedSprites() {
            Snapshot snapshot = createSnapshot(Map.of(
                    "RenderModule", Map.of(
                            "ENTITY_ID", List.of(1.0f)
                    )
            ));

            Sprite sprite1 = mapper.spritesFromSnapshot(snapshot).get(0);

            mapper.clearCache();

            Sprite sprite2 = mapper.spritesFromSnapshot(snapshot).get(0);

            // Should be a new instance after cache clear
            assertThat(sprite1).isNotSameAs(sprite2);
        }
    }

    private Snapshot createSnapshot(Map<String, Map<String, List<Float>>> data) {
        return new Snapshot(data);
    }
}
