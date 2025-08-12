package com.lightningfirefly.game.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnapshotObserver")
class SnapshotObserverTest {

    private SnapshotObserver observer;

    @BeforeEach
    void setUp() {
        DomainObjectRegistry.getInstance().clear();
        observer = new SnapshotObserver();
    }

    @AfterEach
    void tearDown() {
        DomainObjectRegistry.getInstance().clear();
    }

    @Nested
    @DisplayName("onSnapshot")
    class OnSnapshot {

        @Test
        @DisplayName("should update domain object fields from components")
        void shouldUpdateFieldsFromSnapshot() {
            TestPlayer player = new TestPlayer(1);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "POSITION_X", List.of(100.0f),
                    "POSITION_Y", List.of(200.0f),
                    "VELOCITY_X", List.of(5.0f)
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(player.positionX).isEqualTo(100.0f);
            assertThat(player.positionY).isEqualTo(200.0f);
            assertThat(player.velocityX).isEqualTo(5.0f);
        }

        @Test
        @DisplayName("should handle multiple entities in components")
        void shouldHandleMultipleEntities() {
            TestPlayer player1 = new TestPlayer(1);
            TestPlayer player2 = new TestPlayer(2);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f, 2.0f),
                    "POSITION_X", List.of(10.0f, 20.0f),
                    "POSITION_Y", List.of(11.0f, 21.0f)
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(player1.positionX).isEqualTo(10.0f);
            assertThat(player1.positionY).isEqualTo(11.0f);
            assertThat(player2.positionX).isEqualTo(20.0f);
            assertThat(player2.positionY).isEqualTo(21.0f);
        }

        @Test
        @DisplayName("should call onSnapshotUpdated after updates")
        void shouldCallOnSnapshotUpdated() {
            AtomicBoolean called = new AtomicBoolean(false);
            TestPlayer player = new TestPlayer(1) {
                @Override
                protected void onSnapshotUpdated() {
                    called.set(true);
                }
            };

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "POSITION_X", List.of(100.0f)
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(called.get()).isTrue();
        }

        @Test
        @DisplayName("should not update if entity not in components")
        void shouldNotUpdateIfEntityNotInSnapshot() {
            TestPlayer player = new TestPlayer(999);
            player.positionX = 50f;
            player.positionY = 60f;

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f, 2.0f),
                    "POSITION_X", List.of(10.0f, 20.0f),
                    "POSITION_Y", List.of(11.0f, 21.0f)
                )
            );

            observer.onSnapshot(snapshot);

            // Values should remain unchanged
            assertThat(player.positionX).isEqualTo(50f);
            assertThat(player.positionY).isEqualTo(60f);
        }

        @Test
        @DisplayName("should not update disposed domain objects")
        void shouldNotUpdateDisposedObjects() {
            TestPlayer player = new TestPlayer(1);
            player.positionX = 50f;
            player.dispose();

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "POSITION_X", List.of(100.0f)
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(player.positionX).isEqualTo(50f);
        }

        @Test
        @DisplayName("should handle null components")
        void shouldHandleNullSnapshot() {
            TestPlayer player = new TestPlayer(1);

            // Should not throw
            observer.onSnapshot(null);
        }

        @Test
        @DisplayName("should handle empty components")
        void shouldHandleEmptySnapshot() {
            TestPlayer player = new TestPlayer(1);
            player.positionX = 50f;

            observer.onSnapshot(Map.of());

            assertThat(player.positionX).isEqualTo(50f);
        }

        @Test
        @DisplayName("should handle missing module in components")
        void shouldHandleMissingModule() {
            TestPlayer player = new TestPlayer(1);
            player.positionX = 50f;

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "OtherModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "SOME_VALUE", List.of(999.0f)
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(player.positionX).isEqualTo(50f);
        }

        @Test
        @DisplayName("should handle missing component in components")
        void shouldHandleMissingComponent() {
            TestPlayer player = new TestPlayer(1);
            player.positionX = 50f;
            player.positionY = 60f;

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "POSITION_X", List.of(100.0f)
                    // POSITION_Y is missing
                )
            );

            observer.onSnapshot(snapshot);

            assertThat(player.positionX).isEqualTo(100f);
            assertThat(player.positionY).isEqualTo(60f);
        }
    }

    @Nested
    @DisplayName("Consumer interface")
    class ConsumerInterface {

        @Test
        @DisplayName("should work as Consumer<Map>")
        void shouldWorkAsConsumer() {
            TestPlayer player = new TestPlayer(1);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                "MoveModule",
                Map.of(
                    "ENTITY_ID", List.of(1.0f),
                    "POSITION_X", List.of(100.0f)
                )
            );

            // Use as Consumer
            observer.accept(snapshot);

            assertThat(player.positionX).isEqualTo(100.0f);
        }
    }

    private Map<String, Map<String, List<Float>>> createSnapshot(String moduleName, Map<String, List<Float>> moduleData) {
        Map<String, Map<String, List<Float>>> snapshot = new HashMap<>();
        snapshot.put(moduleName, new HashMap<>(moduleData));
        return snapshot;
    }

    // Test domain object
    static class TestPlayer extends DomainObject {
        @EcsEntityId
        long entityId;

        @EcsComponent(componentPath = "MoveModule.POSITION_X")
        float positionX;

        @EcsComponent(componentPath = "MoveModule.POSITION_Y")
        float positionY;

        @EcsComponent(componentPath = "MoveModule.VELOCITY_X")
        float velocityX;

        TestPlayer(long entityId) {
            super(entityId);
        }
    }
}
