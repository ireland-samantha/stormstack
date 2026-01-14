/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.acceptance;

import ca.samanthaireland.engine.api.resource.adapter.ContainerAdapter;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient.ContainerClient;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient.ContainerMatch;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient.ContainerSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API acceptance test for multi-match isolation using container-scoped endpoints.
 *
 * <p>This test verifies the business use case: entities created in one match
 * should not appear in another match's snapshot. Tests via the EngineClient
 * using container-scoped REST API endpoints.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker must be running</li>
 *   <li>The backend image must be built: {@code docker build -t lightning-backend .}</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * ./mvnw verify -pl lightning-engine/api-acceptance-test -Pacceptance-tests
 * </pre>
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Multi-Match Isolation API Acceptance Test")
@Testcontainers
class MultiMatchIsolationApiIT {

    private static final String ENTITY_MODULE_NAME = "EntityModule";
    private static final String MOVE_MODULE_NAME = "RigidBodyModule";
    private static final String GRID_MODULE_NAME = "GridMapModule";
    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forLogMessage(".*started in.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineClient client;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private long containerId = -1;
    private long createdMatch1Id = -1;
    private long createdMatch2Id = -1;

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        baseUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL from testcontainers: " + baseUrl);

        objectMapper = new ObjectMapper();

        // Authenticate to get JWT token
        String token = authenticate();

        // Create client with authentication
        client = EngineClient.builder()
                .baseUrl(baseUrl)
                .withBearerToken(token)
                .build();
    }

    private String authenticate() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String loginJson = "{\"username\":\"admin\",\"password\":\"admin\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Authentication failed: " + response.statusCode() + " - " + response.body());
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        return tokenResponse.path("token").asText();
    }

    @AfterEach
    void tearDown() {
        // Clean up container (which also cleans up matches)
        if (containerId > 0) {
            try {
                client.containers().stopContainer(containerId);
                client.containers().deleteContainer(containerId);
            } catch (Exception e) {
                log.warn("Failed to clean up container {}: {}", containerId, e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Entities created in separate matches are isolated in snapshots")
    void entitiesInSeparateMatchesAreIsolated() throws Exception {
        log.info("=== Starting multi-match isolation API acceptance test ===" );

        // ===== STEP 1: Create container and start it =====
        log.info("=== STEP 1: Create and start container ===");

        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("isolation-test")
                .withModules(ENTITY_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        log.info("Created container: {} (already started since modules were specified)", containerId);

        ContainerClient container = client.container(containerId);

        // ===== STEP 2: Create two matches with EntityModule =====
        log.info("=== STEP 2: Create two matches with EntityModule ===");

        ContainerMatch match1 = container.createMatch(List.of(ENTITY_MODULE_NAME));
        createdMatch1Id = match1.id();
        log.info("Created match 1: " + createdMatch1Id);

        ContainerMatch match2 = container.createMatch(List.of(ENTITY_MODULE_NAME));
        createdMatch2Id = match2.id();
        log.info("Created match 2: " + createdMatch2Id);

        assertThat(createdMatch1Id).isGreaterThan(0);
        assertThat(createdMatch2Id).isGreaterThan(0);
        assertThat(createdMatch1Id).isNotEqualTo(createdMatch2Id);

        // ===== STEP 3: Spawn entities in each match =====
        log.info("=== STEP 3: Spawn entities in each match ===");

        // Spawn 2 entities in match 1
        container.forMatch(createdMatch1Id).spawn().forPlayer(1).ofType(100).execute();
        container.forMatch(createdMatch1Id).spawn().forPlayer(1).ofType(100).execute();
        log.info("Spawned 2 entities in match 1");

        // Spawn 3 entities in match 2
        container.forMatch(createdMatch2Id).spawn().forPlayer(1).ofType(100).execute();
        container.forMatch(createdMatch2Id).spawn().forPlayer(1).ofType(100).execute();
        container.forMatch(createdMatch2Id).spawn().forPlayer(1).ofType(100).execute();
        log.info("Spawned 3 entities in match 2");

        // ===== STEP 4: Advance tick to process spawn commands =====
        log.info("=== STEP 4: Advance tick to process spawn commands ===");
        container.tick();
        container.tick();

        // ===== STEP 5: Verify snapshot isolation via REST API =====
        log.info("=== STEP 5: Verify snapshot isolation via REST API ===");

        Optional<ContainerSnapshot> match1SnapshotOpt = container.getSnapshot(createdMatch1Id);
        Optional<ContainerSnapshot> match2SnapshotOpt = container.getSnapshot(createdMatch2Id);

        assertThat(match1SnapshotOpt).isPresent();
        assertThat(match2SnapshotOpt).isPresent();

        JsonNode match1Snapshot = objectMapper.readTree(match1SnapshotOpt.get().data());
        JsonNode match2Snapshot = objectMapper.readTree(match2SnapshotOpt.get().data());

        // Verify match 1 has exactly 2 entities (using EntityModule)
        List<Long> match1EntityIds = getEntityIds(match1Snapshot);
        log.info("Match 1 entity IDs: " + match1EntityIds);
        assertThat(match1EntityIds)
                .as("Match 1 should have exactly 2 entities")
                .hasSize(2);

        // Verify match 2 has exactly 3 entities (using EntityModule)
        List<Long> match2EntityIds = getEntityIds(match2Snapshot);
        log.info("Match 2 entity IDs: " + match2EntityIds);
        assertThat(match2EntityIds)
                .as("Match 2 should have exactly 3 entities")
                .hasSize(3);

        // Verify entity IDs are disjoint (no overlap between matches)
        for (Long id : match1EntityIds) {
            assertThat(match2EntityIds)
                    .as("Entity ID %d should not appear in match 2", id)
                    .doesNotContain(id);
        }

        log.info("=== API ACCEPTANCE TEST PASSED: Multi-match isolation verified ===");
    }

    @Test
    @DisplayName("Physics commands can be submitted via container-scoped API")
    void physicsCommandsCanBeSubmitted() throws Exception {
        log.info("=== Starting physics commands test ===");

        // Create container with physics modules (RigidBodyModule requires GridMapModule for positions)
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("physics-test")
                .withModules(ENTITY_MODULE_NAME, GRID_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();

        ContainerClient container = client.container(containerId);

        // Create match with physics modules (RigidBodyModule depends on GridMapModule)
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, GRID_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatch1Id = match.id();
        log.info("Created match with physics: {}", createdMatch1Id);

        // Spawn an entity
        container.forMatch(createdMatch1Id).spawn().forPlayer(1).ofType(100).execute();
        container.tick();
        container.tick();

        // Get the entity ID
        Optional<ContainerSnapshot> snap = container.getSnapshot(createdMatch1Id);
        assertThat(snap).isPresent();

        List<Long> entityIds = getEntityIds(objectMapper.readTree(snap.get().data()));
        assertThat(entityIds).hasSize(1);
        long entityId = entityIds.get(0);
        log.info("Spawned entity: {}", entityId);

        // Submit attachRigidBody command - should not throw exception
        container.forMatch(createdMatch1Id).send("attachRigidBody", Map.of(
                "entityId", entityId,
                "positionX", 100.0f,
                "positionY", 200.0f,
                "positionZ", 0.0f,
                "velocityX", 50.0f,
                "velocityY", 25.0f,
                "velocityZ", 0.0f,
                "mass", 1.0f,
                "linearDrag", 0.1f
        ));
        container.tick();
        log.info("attachRigidBody command submitted successfully");

        // Verify entity still exists in snapshot after physics command
        snap = container.getSnapshot(createdMatch1Id);
        assertThat(snap).isPresent();
        JsonNode snapshot = objectMapper.readTree(snap.get().data());
        log.info("Snapshot after attachRigidBody: {}", snap.get().data());

        // Verify RigidBodyModule has the rigidBody flag component
        JsonNode rigidBodyModule = snapshot.path(MOVE_MODULE_NAME);
        assertThat(rigidBodyModule.isMissingNode())
                .as("RigidBodyModule should be present in snapshot")
                .isFalse();
        assertThat(rigidBodyModule.has("rigidBody") || rigidBodyModule.has("ENTITY_ID"))
                .as("RigidBodyModule should have data")
                .isTrue();

        // Submit setVelocity command - should not throw exception
        container.forMatch(createdMatch1Id).send("setVelocity", Map.of(
                "entityId", entityId,
                "velocityX", -100.0f,
                "velocityY", 0.0f,
                "velocityZ", 0.0f
        ));
        container.tick();
        log.info("setVelocity command submitted successfully");

        // Run several more ticks to confirm physics system processes commands
        for (int i = 0; i < 5; i++) {
            container.tick();
        }

        // Final verification - entity should still exist
        snap = container.getSnapshot(createdMatch1Id);
        assertThat(snap).isPresent();
        List<Long> finalEntityIds = getEntityIds(objectMapper.readTree(snap.get().data()));
        assertThat(finalEntityIds)
                .as("Entity should still exist after physics commands")
                .containsExactly(entityId);

        log.info("=== PHYSICS COMMANDS TEST PASSED ===");
    }

    // ========== Helper Methods ==========

    private List<Long> getEntityIds(JsonNode snapshot) {
        List<Long> entityIds = new ArrayList<>();

        // Look for ENTITY_ID in EntityModule
        JsonNode entityModule = snapshot.path("EntityModule");
        if (!entityModule.isMissingNode()) {
            JsonNode entityIdArray = entityModule.path("ENTITY_ID");
            if (entityIdArray.isArray()) {
                for (JsonNode id : entityIdArray) {
                    entityIds.add(id.asLong());
                }
            }
        }
        return entityIds;
    }

    private int countEntitiesInSnapshot(JsonNode snapshot, String moduleName) {
        if (snapshot.isMissingNode()) {
            return 0;
        }

        JsonNode moduleData = snapshot.path(moduleName);
        if (moduleData.isMissingNode()) {
            return 0;
        }

        // The entity count is the length of any component array
        // Try POSITION_X as the canonical component
        JsonNode positionX = moduleData.path("POSITION_X");
        if (positionX.isArray()) {
            return positionX.size();
        }

        // Fallback: try first field
        var fields = moduleData.fields();
        if (fields.hasNext()) {
            JsonNode firstComponent = fields.next().getValue();
            if (firstComponent.isArray()) {
                return firstComponent.size();
            }
        }

        return 0;
    }

    private List<Long> getPositionXValues(JsonNode snapshot, String moduleName) {
        List<Long> positions = new ArrayList<>();

        if (snapshot.isMissingNode()) {
            return positions;
        }

        JsonNode moduleData = snapshot.path(moduleName);
        JsonNode positionX = moduleData.path("POSITION_X");

        if (positionX.isArray()) {
            for (JsonNode pos : positionX) {
                positions.add(pos.asLong());
            }
        }

        return positions;
    }
}
