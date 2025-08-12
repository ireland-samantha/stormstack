package com.lightningfirefly.engine.acceptance.test;

import com.lightningfirefly.engine.acceptance.test.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that spawned entities appear in snapshots correctly.
 * Uses the domain layer for clean, readable test code.
 *
 * Note: These tests are disabled pending Docker image rebuild with updated EntityModule.
 * The current Docker image has stale code that doesn't include ENTITY_TYPE in snapshots.
 */
@Disabled("Docker image needs rebuild - ENTITY_TYPE not included in snapshot")
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Spawn Snapshot Tests")
@Testcontainers
class SpawnEntitiyVerifySnapshotIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private TestBackend backend;
    private Match match;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        backend = TestBackend.connectTo(backendUrl);
        log.info("Backend URL: {}", backendUrl);
    }

    @AfterEach
    void tearDown() {
        if (match != null) {
            match.delete();
            match = null;
        }
    }

    @Test
    @DisplayName("Given a match with EntityModule, when spawning an entity and ticking, then entity appears in snapshot")
    void givenMatchWithEntityModule_whenSpawningEntityAndTicking_thenEntityAppearsInSnapshot() {
        // Given a match with EntityModule
        match = backend.createMatch()
                .withModule("EntityModule")
                .start();
        log.info("Created match with ID: {}", match.id());

        // When spawning an entity
        match.spawnEntity().ofType(100).execute();
        log.info("Spawned entity with type 100");

        // And ticking the simulation
        match.tick().tick();
        log.info("Ticked simulation twice");

        // Debug: log raw snapshot
        SnapshotParser snapshot = match.fetchSnapshot();
        log.info("Raw snapshot: {}", snapshot);
        List<Float> entityIds = snapshot.getComponent("EntityModule", "ENTITY_ID");
        log.info("ENTITY_ID: {}", entityIds);
        List<Float> entityTypes = snapshot.getComponent("EntityModule", "ENTITY_TYPE");
        log.info("ENTITY_TYPE: {}", entityTypes);
        log.info("Available modules: {}", snapshot.getModuleNames());

        // Then the snapshot should contain entity data
        match.assertThatSnapshot()
                .hasModule("EntityModule")
                .withComponent("ENTITY_ID").isPresent()
                .withComponent("ENTITY_TYPE").containingValue(100f);

        log.info("Verified entity appears in snapshot");
    }

    @Test
    @DisplayName("Given a match with RenderModule, when creating without id field, then match is created successfully")
    void givenMatchWithRenderModule_whenCreatingWithoutIdField_thenMatchIsCreatedSuccessfully() {
        // When creating a match with EntityModule and RenderModule
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule")
                .start();

        // Then the match should be created with a positive ID
        assertThat(match.id())
                .as("Match should be created with positive ID")
                .isGreaterThan(0);

        log.info("Match created successfully with ID: {}", match.id());
    }

    @Test
    @DisplayName("Given multiple entities spawned, when ticking, then all entities appear in snapshot")
    void givenMultipleEntitiesSpawned_whenTicking_thenAllEntitiesAppearInSnapshot() {
        // Given a match with EntityModule
        match = backend.createMatch()
                .withModule("EntityModule")
                .start();

        // When spawning 3 entities
        match.spawnEntity().ofType(100).execute();
        match.spawnEntity().ofType(101).execute();
        match.spawnEntity().ofType(102).execute();

        // And ticking
        match.tick().tick();

        // Then snapshot should have 3 entities
        match.assertThatSnapshot()
                .hasModule("EntityModule")
                .withComponent("ENTITY_ID").withCount(3)
                .withComponent("ENTITY_TYPE").containingValues(100f, 101f, 102f);

        log.info("Verified all 3 entities appear in snapshot");
    }
}
