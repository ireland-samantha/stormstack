package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.SnapshotService.SnapshotData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SnapshotService JSON parsing.
 */
class SnapshotServiceTest {

    @Test
    void snapshotData_getModuleNames_returnsModuleNames() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of("ENTITY_TYPE", List.of(1.0f, 2.0f)),
            "MoveModule", Map.of("VELOCITY_X", List.of(10.0f, 20.0f))
        );
        SnapshotData snapshot = new SnapshotData(1L, 42L, data);

        Set<String> moduleNames = snapshot.getModuleNames();

        assertThat(moduleNames).containsExactlyInAnyOrder("SpawnModule", "MoveModule");
    }

    @Test
    void snapshotData_getModuleData_returnsDataForModule() {
        Map<String, List<Float>> spawnData = Map.of(
            "ENTITY_TYPE", List.of(1.0f, 2.0f),
            "OWNER_ID", List.of(100.0f, 200.0f)
        );
        Map<String, Map<String, List<Float>>> data = Map.of("SpawnModule", spawnData);
        SnapshotData snapshot = new SnapshotData(1L, 42L, data);

        Map<String, List<Float>> moduleData = snapshot.getModuleData("SpawnModule");

        assertThat(moduleData).containsEntry("ENTITY_TYPE", List.of(1.0f, 2.0f));
        assertThat(moduleData).containsEntry("OWNER_ID", List.of(100.0f, 200.0f));
    }

    @Test
    void snapshotData_getModuleData_returnsNullForUnknownModule() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of("ENTITY_TYPE", List.of(1.0f))
        );
        SnapshotData snapshot = new SnapshotData(1L, 42L, data);

        Map<String, List<Float>> moduleData = snapshot.getModuleData("UnknownModule");

        assertThat(moduleData).isNull();
    }

    @Test
    void snapshotData_getEntityCount_returnsNumberOfEntities() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "SpawnModule", Map.of(
                "ENTITY_TYPE", List.of(1.0f, 2.0f, 3.0f),
                "OWNER_ID", List.of(100.0f, 200.0f, 300.0f)
            )
        );
        SnapshotData snapshot = new SnapshotData(1L, 42L, data);

        int entityCount = snapshot.getEntityCount();

        assertThat(entityCount).isEqualTo(3);
    }

    @Test
    void snapshotData_getEntityCount_returnsZeroForEmptyData() {
        SnapshotData snapshot = new SnapshotData(1L, 42L, Map.of());

        int entityCount = snapshot.getEntityCount();

        assertThat(entityCount).isEqualTo(0);
    }

    @Test
    void snapshotData_getEntityCount_returnsZeroForNullData() {
        SnapshotData snapshot = new SnapshotData(1L, 42L, null);

        int entityCount = snapshot.getEntityCount();

        assertThat(entityCount).isEqualTo(0);
    }

    @Test
    void snapshotData_getModuleNames_returnsEmptySetForNullData() {
        SnapshotData snapshot = new SnapshotData(1L, 42L, null);

        Set<String> moduleNames = snapshot.getModuleNames();

        assertThat(moduleNames).isEmpty();
    }

    @Test
    void snapshotData_recordAccessors() {
        Map<String, Map<String, List<Float>>> data = Map.of(
            "Module1", Map.of("COMPONENT", List.of(1.0f))
        );
        SnapshotData snapshot = new SnapshotData(99L, 123L, data);

        assertThat(snapshot.matchId()).isEqualTo(99L);
        assertThat(snapshot.tick()).isEqualTo(123L);
        assertThat(snapshot.data()).isEqualTo(data);
    }

    @Test
    void snapshotService_canBeCreated() {
        SnapshotService service = new SnapshotService("http://localhost:8080");

        assertThat(service).isNotNull();

        service.shutdown();
    }

    @Test
    void snapshotService_shutdown_canBeCalledMultipleTimes() {
        SnapshotService service = new SnapshotService("http://localhost:8080");

        // Should not throw
        service.shutdown();
        service.shutdown();
    }
}
