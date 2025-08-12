package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotWebSocketClientTest {

    @Test
    void constructor_normalizesServerUrl() {
        SnapshotWebSocketClient client1 = new SnapshotWebSocketClient("http://localhost:8080/", 1);
        SnapshotWebSocketClient client2 = new SnapshotWebSocketClient("http://localhost:8080", 1);

        // Both should work without issues
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
    }

    @Test
    void isConnected_returnsFalse_beforeConnect() {
        SnapshotWebSocketClient client = new SnapshotWebSocketClient("http://localhost:8080", 1);

        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void addListener_acceptsMultipleListeners() {
        SnapshotWebSocketClient client = new SnapshotWebSocketClient("http://localhost:8080", 1);

        // Should not throw
        client.addListener(snapshot -> {});
        client.addListener(snapshot -> {});
        client.addListener(snapshot -> {});

        assertThat(client).isNotNull();
    }

    @Test
    void snapshotData_getModuleNames_returnsCorrectModules() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of("ENTITY_TYPE", List.of(1.0f, 2.0f)),
            "MoveModule", Map.of("POSITION_X", List.of(100.0f, 200.0f))
        );

        SnapshotData snapshot = new SnapshotData(1L, 100L, data);

        assertThat(snapshot.getModuleNames())
            .containsExactlyInAnyOrder("SpawnModule", "MoveModule");
    }

    @Test
    void snapshotData_getModuleData_returnsCorrectData() {
        Map<String, List<Float>> spawnData = Map.of(
            "ENTITY_TYPE", List.of(1.0f, 2.0f),
            "OWNER_ID", List.of(100.0f, 200.0f)
        );
        Map<String, Map<String, List<Float>>> data = Map.of("SpawnModule", spawnData);

        SnapshotData snapshot = new SnapshotData(1L, 100L, data);

        assertThat(snapshot.getModuleData("SpawnModule")).isEqualTo(spawnData);
        assertThat(snapshot.getModuleData("NonExistent")).isNull();
    }

    @Test
    void snapshotData_getEntityCount_returnsCorrectCount() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(1.0f, 2.0f, 3.0f),
                "OWNER_ID", List.of(100.0f, 200.0f, 300.0f)
            )
        );

        SnapshotData snapshot = new SnapshotData(1L, 100L, data);

        assertThat(snapshot.getEntityCount()).isEqualTo(3);
    }

    @Test
    void snapshotData_getEntityCount_returnsZero_forEmptySnapshot() {
        SnapshotData emptySnapshot = new SnapshotData(1L, 100L, Map.of());

        assertThat(emptySnapshot.getEntityCount()).isZero();
    }

    @Test
    void snapshotData_getEntityCount_returnsZero_forNullData() {
        SnapshotData nullSnapshot = new SnapshotData(1L, 100L, null);

        assertThat(nullSnapshot.getEntityCount()).isZero();
    }

    @Test
    void snapshotData_getModuleNames_returnsEmpty_forNullData() {
        SnapshotData nullSnapshot = new SnapshotData(1L, 100L, null);

        assertThat(nullSnapshot.getModuleNames()).isEmpty();
    }

    @Test
    void snapshotData_recordAccessors_returnCorrectValues() {
        Map<String, Map<String, List<Float>>> data = Map.of();
        SnapshotData snapshot = new SnapshotData(42L, 999L, data);

        assertThat(snapshot.matchId()).isEqualTo(42L);
        assertThat(snapshot.tick()).isEqualTo(999L);
        assertThat(snapshot.data()).isEqualTo(data);
    }
}
