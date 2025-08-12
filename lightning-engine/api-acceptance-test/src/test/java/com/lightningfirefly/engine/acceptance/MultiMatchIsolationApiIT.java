package com.lightningfirefly.engine.acceptance;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.websocket.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API acceptance test for multi-match isolation.
 *
 * <p>This test verifies the business use case: entities created in one match
 * should not appear in another match's snapshot. Tests purely via REST API
 * and WebSockets - no GUI or OpenGL required.
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
    private static final String MOVE_MODULE_NAME = "MoveModule";
    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private long createdMatch1Id = -1;
    private long createdMatch2Id = -1;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        baseUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL from testcontainers: " + baseUrl);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        // Clean up created matches
        if (createdMatch1Id > 0) {
            try {
                deleteMatch(createdMatch1Id);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        if (createdMatch2Id > 0) {
            try {
                deleteMatch(createdMatch2Id);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("Entities created in separate matches are isolated in snapshots")
    void entitiesInSeparateMatchesAreIsolated() throws Exception {
        log.info("=== Starting multi-match isolation API acceptance test ===" );

        // ===== STEP 1: Create two matches with EntityModule and MoveModule =====
        log.info("=== STEP 1: Create two matches with EntityModule and MoveModule ===");

        createdMatch1Id = createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        log.info("Created match 1: " + createdMatch1Id);

        createdMatch2Id = createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        log.info("Created match 2: " + createdMatch2Id);

        assertThat(createdMatch1Id).isGreaterThan(0);
        assertThat(createdMatch2Id).isGreaterThan(0);
        assertThat(createdMatch1Id).isNotEqualTo(createdMatch2Id);

        // ===== STEP 2: Spawn entities in each match =====
        log.info("=== STEP 2: Spawn entities in each match ===");

        // Spawn 2 entities in match 1
        spawnEntity(createdMatch1Id);
        spawnEntity(createdMatch1Id);
        log.info("Spawned 2 entities in match 1");

        // Spawn 3 entities in match 2
        spawnEntity(createdMatch2Id);
        spawnEntity(createdMatch2Id);
        spawnEntity(createdMatch2Id);
        log.info("Spawned 3 entities in match 2");

        // ===== STEP 3: Advance tick to process spawn commands =====
        log.info("=== STEP 3: Advance tick to process spawn commands ===");
        advanceTick();
        advanceTick();

        // ===== STEP 4: Get entity IDs and attach movement =====
        log.info("=== STEP 4: Get entity IDs and attach movement ===");

        // Get entity IDs from match 1 snapshot
        JsonNode snapshot1 = getSnapshot(createdMatch1Id);
        List<Long> match1EntityIds = getEntityIds(snapshot1);
        log.info("Match 1 entity IDs: " + match1EntityIds);

        // Attach movement to match 1 entities
        int posX = 100;
        for (Long entityId : match1EntityIds) {
            attachMovement(entityId, posX, 0, 0);
            posX += 100;
        }

        // Get entity IDs from match 2 snapshot
        JsonNode snapshot2 = getSnapshot(createdMatch2Id);
        List<Long> match2EntityIds = getEntityIds(snapshot2);
        log.info("Match 2 entity IDs: " + match2EntityIds);

        // Attach movement to match 2 entities
        posX = 1000;
        for (Long entityId : match2EntityIds) {
            attachMovement(entityId, posX, 0, 0);
            posX += 1000;
        }

        // ===== STEP 5: Advance tick to process attachMovement commands =====
        log.info("=== STEP 5: Advance tick to process attachMovement commands ===");
        long newTick = advanceTick();
        log.info("Advanced to tick: " + newTick);

        // ===== STEP 6: Verify snapshot isolation via REST API =====
        log.info("=== STEP 6: Verify snapshot isolation via REST API ===");

        JsonNode match1Snapshot = getSnapshot(createdMatch1Id);
        JsonNode match2Snapshot = getSnapshot(createdMatch2Id);

        // Verify match 1 has exactly 2 entities
        int match1EntityCount = countEntitiesInSnapshot(match1Snapshot, MOVE_MODULE_NAME);
        log.info("Match 1 entity count: " + match1EntityCount);
        assertThat(match1EntityCount)
                .as("Match 1 should have exactly 2 entities")
                .isEqualTo(2);

        // Verify match 2 has exactly 3 entities
        int match2EntityCount = countEntitiesInSnapshot(match2Snapshot, MOVE_MODULE_NAME);
        log.info("Match 2 entity count: " + match2EntityCount);
        assertThat(match2EntityCount)
                .as("Match 2 should have exactly 3 entities")
                .isEqualTo(3);

        // Verify the positions are distinct (match 1 entities have x < 500, match 2 have x >= 1000)
        List<Long> match1Positions = getPositionXValues(match1Snapshot, MOVE_MODULE_NAME);
        List<Long> match2Positions = getPositionXValues(match2Snapshot, MOVE_MODULE_NAME);

        log.info("Match 1 positions: " + match1Positions);
        log.info("Match 2 positions: " + match2Positions);

        assertThat(match1Positions).containsExactlyInAnyOrder(100L, 200L);
        assertThat(match2Positions).containsExactlyInAnyOrder(1000L, 2000L, 3000L);

        log.info("=== API ACCEPTANCE TEST PASSED: Multi-match isolation verified ===");
    }

    @Test
    @DisplayName("WebSocket snapshot updates respect match isolation")
    void webSocketSnapshotUpdatesRespectMatchIsolation() throws Exception {
        log.info("=== Starting WebSocket isolation test ===");

        // Create two matches with EntityModule and MoveModule
        createdMatch1Id = createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatch2Id = createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));

        // Spawn entities
        spawnEntity(createdMatch1Id);
        spawnEntity(createdMatch2Id);
        spawnEntity(createdMatch2Id);

        // Advance tick to process spawns
        advanceTick();
        advanceTick();

        // Get entity IDs and attach movement
        List<Long> match1EntityIds = getEntityIds(getSnapshot(createdMatch1Id));
        List<Long> match2EntityIds = getEntityIds(getSnapshot(createdMatch2Id));

        for (Long entityId : match1EntityIds) {
            attachMovement(entityId, 100, 0, 0);
        }
        int posX = 1000;
        for (Long entityId : match2EntityIds) {
            attachMovement(entityId, posX, 0, 0);
            posX += 1000;
        }

        // Advance tick to process attachMovement
        advanceTick();

        // Connect WebSocket and request snapshot for match 1 only
        String wsUrl = baseUrl.replace("http://", "ws://") + "/ws/snapshots";
        AtomicReference<JsonNode> receivedSnapshot = new AtomicReference<>();
        CountDownLatch snapshotReceived = new CountDownLatch(1);

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session wsSession = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(String.class, message -> {
                    try {
                        JsonNode snapshot = objectMapper.readTree(message);
                        receivedSnapshot.set(snapshot);
                        snapshotReceived.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }, ClientEndpointConfig.Builder.create().build(), URI.create(wsUrl));

        try {
            // Request snapshot for match 1
            wsSession.getBasicRemote().sendText("{\"type\":\"subscribe\",\"matchId\":" + createdMatch1Id + "}");
            wsSession.getBasicRemote().sendText("{\"type\":\"requestSnapshot\",\"matchId\":" + createdMatch1Id + "}");

            // Wait for snapshot
            boolean received = snapshotReceived.await(10, TimeUnit.SECONDS);
            assertThat(received).as("Should receive WebSocket snapshot").isTrue();

            // Verify we only got match 1 data
            JsonNode snapshot = receivedSnapshot.get();
            assertThat(snapshot).isNotNull();

            long snapshotMatchId = snapshot.path("matchId").asLong(-1);
            assertThat(snapshotMatchId)
                    .as("WebSocket snapshot should be for match 1")
                    .isEqualTo(createdMatch1Id);

            int entityCount = countEntitiesInSnapshot(snapshot, MOVE_MODULE_NAME);
            assertThat(entityCount)
                    .as("WebSocket snapshot should show only match 1's entity")
                    .isEqualTo(1);

            log.info("=== WEBSOCKET ISOLATION TEST PASSED ===");
        } finally {
            wsSession.close();
        }
    }

    // ========== Helper Methods ==========

    private long createMatch(List<String> modules) throws IOException, InterruptedException {
        String modulesJson = "[" + String.join(",", modules.stream().map(m -> "\"" + m + "\"").toList()) + "]";
        String json = "{\"enabledModuleNames\":" + modulesJson + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/matches"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Create match should succeed").isEqualTo(201);

        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.path("id").asLong();
    }

    private void deleteMatch(long matchId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/matches/" + matchId))
                .DELETE()
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void spawnEntity(long matchId) throws IOException, InterruptedException {
        String json = String.format(
                "{\"commandName\":\"spawn\",\"payload\":{\"matchId\":%d,\"playerId\":1,\"entityType\":100}}",
                matchId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("Spawn command should succeed")
                .isIn(200, 201, 202);
    }

    private void attachMovement(long entityId, int posX, int posY, int posZ) throws IOException, InterruptedException {
        String json = String.format(
                "{\"commandName\":\"attachMovement\",\"payload\":{" +
                        "\"entityId\":%d,\"positionX\":%d,\"positionY\":%d,\"positionZ\":%d," +
                        "\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}}",
                entityId, posX, posY, posZ);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/commands"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("Attach movement command should succeed")
                .isIn(200, 201, 202);
    }

    private List<Long> getEntityIds(JsonNode snapshot) {
        List<Long> entityIds = new ArrayList<>();
        JsonNode data = snapshot.path("data");
        if (data.isMissingNode()) {
            return entityIds;
        }

        // Look for ENTITY_ID in EntityModule
        JsonNode entityModule = data.path("EntityModule");
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

    private long advanceTick() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/simulation/tick"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Advance tick should succeed").isEqualTo(200);

        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.path("tick").asLong();
    }

    private JsonNode getSnapshot(long matchId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/snapshots/match/" + matchId))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get snapshot should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private int countEntitiesInSnapshot(JsonNode snapshot, String moduleName) {
        JsonNode snapshotData = snapshot.path("snapshot");
        if (snapshotData.isMissingNode()) {
            snapshotData = snapshot.path("data");
        }
        if (snapshotData.isMissingNode()) {
            return 0;
        }

        JsonNode moduleData = snapshotData.path(moduleName);
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

        JsonNode snapshotData = snapshot.path("snapshot");
        if (snapshotData.isMissingNode()) {
            snapshotData = snapshot.path("data");
        }
        if (snapshotData.isMissingNode()) {
            return positions;
        }

        JsonNode moduleData = snapshotData.path(moduleName);
        JsonNode positionX = moduleData.path("POSITION_X");

        if (positionX.isArray()) {
            for (JsonNode pos : positionX) {
                positions.add(pos.asLong());
            }
        }

        return positions;
    }
}
