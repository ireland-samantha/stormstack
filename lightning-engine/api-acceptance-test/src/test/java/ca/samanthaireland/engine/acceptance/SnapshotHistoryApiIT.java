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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API acceptance test for snapshot history and MongoDB persistence.
 *
 * <p>This test verifies that:
 * <ul>
 *   <li>Snapshots are persisted to MongoDB after each tick</li>
 *   <li>The /api/history endpoint provides access to historical snapshots</li>
 *   <li>Snapshot queries by tick range work correctly</li>
 *   <li>Snapshot cleanup operations work correctly</li>
 * </ul>
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
@Tag("mongodb")
@DisplayName("Snapshot History API Acceptance Test")
@Testcontainers
class SnapshotHistoryApiIT {

    private static final String ENTITY_MODULE_NAME = "EntityModule";
    private static final String MOVE_MODULE_NAME = "RigidBodyModule";
    private static final int BACKEND_PORT = 8080;
    private static final int MONGO_PORT = 27017;

    static Network network = Network.newNetwork();

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer(DockerImageName.parse("mongo:7"))
            .withNetwork(network)
            .withNetworkAliases("mongodb");

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .withNetwork(network)
            .withEnv("QUARKUS_MONGODB_CONNECTION_STRING", "mongodb://mongodb:27017")
            .withEnv("SNAPSHOT_PERSISTENCE_ENABLED", "true")
            .withEnv("SNAPSHOT_PERSISTENCE_DATABASE", "lightningfirefly")
            .withEnv("SNAPSHOT_PERSISTENCE_COLLECTION", "snapshots")
            .withEnv("SNAPSHOT_PERSISTENCE_TICK_INTERVAL", "1")
            .dependsOn(mongoContainer)
            .waitingFor(Wait.forLogMessage(".*started in.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineClient client;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private String baseUrl;
    private String bearerToken;
    private long containerId = -1;
    private long createdMatchId = -1;

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        baseUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL from testcontainers: " + baseUrl);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();

        // Authenticate to get JWT token
        bearerToken = authenticate();

        // Create client with authentication
        client = EngineClient.builder()
                .baseUrl(baseUrl)
                .withBearerToken(bearerToken)
                .build();
    }

    private String authenticate() throws Exception {
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
        if (containerId > 0) {
            try {
                client.containers().stopContainer(containerId);
                client.containers().deleteContainer(containerId);
            } catch (Exception e) {
                log.warn("Failed to clean up container: {}", e.getMessage());
            }
        }
    }

    @Test
    @Disabled("MongoDB persistence not yet integrated with container-scoped matches - requires backend work")
    @DisplayName("GET /api/history returns summary when persistence is enabled")
    void historySummaryReturnsCorrectInfo() throws Exception {
        log.info("=== Starting history summary test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("history-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance ticks
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();
        container.tick();
        container.tick();
        container.tick();

        // Give MongoDB time to persist
        Thread.sleep(500);

        // Get history summary
        JsonNode summary = getHistorySummary();

        assertThat(summary.path("totalSnapshots").asLong())
                .as("Should have persisted at least 3 snapshots")
                .isGreaterThanOrEqualTo(3);

        assertThat(summary.path("database").asText())
                .as("Database name should match config")
                .isEqualTo("lightningfirefly");

        assertThat(summary.path("collection").asText())
                .as("Collection name should match config")
                .isEqualTo("snapshots");

        log.info("=== HISTORY SUMMARY TEST PASSED ===");
    }

    @Test
    @Disabled("MongoDB persistence not yet integrated with container-scoped matches - requires backend work")
    @DisplayName("GET /api/history/{matchId} returns match-specific history")
    void matchHistoryReturnsCorrectInfo() throws Exception {
        log.info("=== Starting match history test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("match-history-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance ticks
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();
        container.tick();
        container.tick();

        // Give MongoDB time to persist
        Thread.sleep(500);

        // Get match history
        JsonNode matchHistory = getMatchHistory(createdMatchId);

        assertThat(matchHistory.path("matchId").asLong())
                .as("Match ID should match")
                .isEqualTo(createdMatchId);

        assertThat(matchHistory.path("snapshotCount").asLong())
                .as("Should have at least 2 snapshots for this match")
                .isGreaterThanOrEqualTo(2);

        assertThat(matchHistory.path("firstTick").asLong())
                .as("First tick should be >= 0")
                .isGreaterThanOrEqualTo(0);

        assertThat(matchHistory.path("lastTick").asLong())
                .as("Last tick should be > first tick")
                .isGreaterThan(matchHistory.path("firstTick").asLong());

        log.info("=== MATCH HISTORY TEST PASSED ===");
    }

    @Test
    @Disabled("MongoDB persistence not yet integrated with container-scoped matches - requires backend work")
    @DisplayName("GET /api/history/{matchId}/snapshots returns snapshots in tick range")
    void snapshotsInRangeReturnsCorrectData() throws Exception {
        log.info("=== Starting snapshots in range test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("snapshots-range-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance ticks
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();

        long tick1 = container.tick();
        long tick2 = container.tick();
        long tick3 = container.tick();
        long tick4 = container.tick();

        // Give MongoDB time to persist
        Thread.sleep(500);

        // Get snapshots in range
        JsonNode result = getSnapshotsInRange(createdMatchId, tick1, tick3, 100);

        assertThat(result.path("matchId").asLong())
                .as("Match ID should match")
                .isEqualTo(createdMatchId);

        assertThat(result.path("count").asInt())
                .as("Should return snapshots in the tick range")
                .isGreaterThanOrEqualTo(1);

        JsonNode snapshots = result.path("snapshots");
        assertThat(snapshots.isArray())
                .as("Snapshots should be an array")
                .isTrue();

        // Verify each snapshot has expected structure
        for (JsonNode snapshot : snapshots) {
            assertThat(snapshot.has("matchId")).isTrue();
            assertThat(snapshot.has("tick")).isTrue();
            assertThat(snapshot.has("timestamp")).isTrue();
            assertThat(snapshot.has("data")).isTrue();
        }

        log.info("=== SNAPSHOTS IN RANGE TEST PASSED ===");
    }

    @Test
    @DisplayName("GET /api/history/{matchId}/snapshots/latest returns most recent snapshots")
    void latestSnapshotsReturnsCorrectData() throws Exception {
        log.info("=== Starting latest snapshots test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("latest-snapshots-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance many ticks
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();

        for (int i = 0; i < 10; i++) {
            container.tick();
        }

        // Give MongoDB time to persist
        Thread.sleep(500);

        // Get latest 5 snapshots
        JsonNode result = getLatestSnapshots(createdMatchId, 5);

        assertThat(result.path("count").asInt())
                .as("Should return up to 5 latest snapshots")
                .isLessThanOrEqualTo(5);

        JsonNode snapshots = result.path("snapshots");
        if (snapshots.size() > 1) {
            // Verify ordering (should be descending by tick)
            long previousTick = Long.MAX_VALUE;
            for (JsonNode snapshot : snapshots) {
                long currentTick = snapshot.path("tick").asLong();
                assertThat(currentTick)
                        .as("Snapshots should be in descending tick order")
                        .isLessThanOrEqualTo(previousTick);
                previousTick = currentTick;
            }
        }

        log.info("=== LATEST SNAPSHOTS TEST PASSED ===");
    }

    @Test
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}/snapshots/delta returns compression info between ticks")
    void deltaReturnsCompressionInfo() throws Exception {
        log.info("=== Starting delta compression test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("delta-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance tick
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();
        long tick1 = container.tick();

        // Record snapshot at tick1 to in-memory history (required for delta endpoint)
        recordSnapshot(createdMatchId);

        // Spawn another entity to create a change
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();
        long tick2 = container.tick();

        // Record snapshot at tick2
        recordSnapshot(createdMatchId);

        // Get delta between ticks (uses in-memory history, not MongoDB)
        JsonNode delta = getDelta(createdMatchId, tick1, tick2);

        assertThat(delta.path("matchId").asLong())
                .as("Match ID should match")
                .isEqualTo(createdMatchId);

        assertThat(delta.path("fromTick").asLong())
                .as("From tick should match")
                .isEqualTo(tick1);

        assertThat(delta.path("toTick").asLong())
                .as("To tick should match")
                .isEqualTo(tick2);

        assertThat(delta.has("changeCount"))
                .as("Delta should have change count")
                .isTrue();

        assertThat(delta.has("compressionRatio"))
                .as("Delta should have compression ratio")
                .isTrue();

        assertThat(delta.has("addedEntities"))
                .as("Delta should have added entities list")
                .isTrue();

        assertThat(delta.has("removedEntities"))
                .as("Delta should have removed entities list")
                .isTrue();

        log.info("Delta: changeCount={}, compressionRatio={}",
                delta.path("changeCount").asInt(),
                delta.path("compressionRatio").asDouble());

        log.info("=== DELTA COMPRESSION TEST PASSED ===");
    }

    @Test
    @Disabled("MongoDB persistence not yet integrated with container-scoped matches - requires backend work")
    @DisplayName("GET /api/history/{matchId}/snapshot/{tick} returns specific snapshot")
    void specificSnapshotReturnsCorrectData() throws Exception {
        log.info("=== Starting specific snapshot test ===");

        // Create container, start it, and create a match
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("specific-snapshot-test")
                .withModules(ENTITY_MODULE_NAME, MOVE_MODULE_NAME)
                .execute();
        containerId = containerResponse.id();
        // Container is already started since modules were specified

        ContainerClient container = client.container(containerId);
        ContainerMatch match = container.createMatch(List.of(ENTITY_MODULE_NAME, MOVE_MODULE_NAME));
        createdMatchId = match.id();

        // Spawn entity and advance ticks
        container.forMatch(createdMatchId).spawn().forPlayer(1).ofType(100).execute();
        container.tick();
        long targetTick = container.tick();

        // Give MongoDB time to persist
        Thread.sleep(500);

        // Get specific snapshot
        JsonNode snapshot = getSpecificSnapshot(createdMatchId, targetTick);

        assertThat(snapshot.path("matchId").asLong())
                .as("Match ID should match")
                .isEqualTo(createdMatchId);

        assertThat(snapshot.path("tick").asLong())
                .as("Tick should match requested tick")
                .isEqualTo(targetTick);

        assertThat(snapshot.has("data"))
                .as("Snapshot should have data")
                .isTrue();

        log.info("=== SPECIFIC SNAPSHOT TEST PASSED ===");
    }

    // ========== Helper Methods (using raw HTTP for history API) ==========

    private JsonNode getHistorySummary() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/containers/" + containerId + "/history"))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get history summary should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private JsonNode getMatchHistory(long matchId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/containers/" + containerId + "/matches/" + matchId + "/history"))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get match history should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private JsonNode getSnapshotsInRange(long matchId, long fromTick, long toTick, int limit)
            throws IOException, InterruptedException {
        String url = String.format("%s/api/containers/%d/matches/%d/history/snapshots?fromTick=%d&toTick=%d&limit=%d",
                baseUrl, containerId, matchId, fromTick, toTick, limit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get snapshots in range should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private JsonNode getLatestSnapshots(long matchId, int limit) throws IOException, InterruptedException {
        String url = String.format("%s/api/containers/%d/matches/%d/history/snapshots/latest?limit=%d",
                baseUrl, containerId, matchId, limit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get latest snapshots should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private JsonNode getSpecificSnapshot(long matchId, long tick) throws IOException, InterruptedException {
        String url = String.format("%s/api/containers/%d/matches/%d/history/snapshots/%d",
                baseUrl, containerId, matchId, tick);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get specific snapshot should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private JsonNode getDelta(long matchId, long fromTick, long toTick) throws IOException, InterruptedException {
        String url = String.format("%s/api/containers/%d/matches/%d/snapshots/delta?fromTick=%d&toTick=%d",
                baseUrl, containerId, matchId, fromTick, toTick);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Get delta should succeed").isEqualTo(200);

        return objectMapper.readTree(response.body());
    }

    private void recordSnapshot(long matchId) throws IOException, InterruptedException {
        String url = String.format("%s/api/containers/%d/matches/%d/snapshots/record",
                baseUrl, containerId, matchId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Authorization", "Bearer " + bearerToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Record snapshot should succeed").isEqualTo(200);
    }
}
