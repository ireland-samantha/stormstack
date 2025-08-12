package com.lightningfirefly.engine.gui.acceptance;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test to isolate the empty snapshot issue.
 * This test uses only REST API calls without GUI.
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Spawn Snapshot Debug Tests")
@Testcontainers
class SpawnSnapshotDebugIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private String backendUrl;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
        httpClient = HttpClient.newHttpClient();
        System.out.println("Backend URL: " + backendUrl);
    }

    @Test
    @DisplayName("Spawn entity should appear in snapshot")
    void spawnEntity_shouldAppearInSnapshot() throws Exception {
        // Step 1: Create a match with SpawnModule
        System.out.println("=== Step 1: Create match ===");
        String matchBody = "{\"id\":0,\"enabledModuleNames\":[\"SpawnModule\"]}";
        HttpResponse<String> matchResponse = post("/api/matches", matchBody);
        System.out.println("Create match response: " + matchResponse.statusCode() + " " + matchResponse.body());
        assertThat(matchResponse.statusCode()).isIn(200, 201);

        // Extract match ID from response (format: {"id":N,"enabledModules":[...]})
        String matchResponseBody = matchResponse.body();
        // Extract id field
        int idStart = matchResponseBody.indexOf("\"id\":") + 5;
        int idEnd = matchResponseBody.indexOf(",", idStart);
        if (idEnd == -1) idEnd = matchResponseBody.indexOf("}", idStart);
        long matchId = Long.parseLong(matchResponseBody.substring(idStart, idEnd).trim());
        System.out.println("Created match ID: " + matchId);

        // Step 2: Check initial snapshot (should be empty or have no entities)
        System.out.println("\n=== Step 2: Check initial snapshot ===");
        HttpResponse<String> snapshotResponse1 = get("/api/snapshots");
        System.out.println("Initial snapshot: " + snapshotResponse1.body());

        // Step 3: Send spawn command
        System.out.println("\n=== Step 3: Send spawn command ===");
        String spawnBody = String.format(
            "{\"commandName\":\"spawn\",\"payload\":{\"matchId\":%d,\"playerId\":1,\"entityType\":100}}",
            matchId);
        HttpResponse<String> spawnResponse = post("/api/commands", spawnBody);
        System.out.println("Spawn command response: " + spawnResponse.statusCode() + " " + spawnResponse.body());

        // Step 4: Tick the simulation
        System.out.println("\n=== Step 4: Tick simulation ===");
        HttpResponse<String> tickResponse = post("/api/simulation/tick", "");
        System.out.println("Tick response: " + tickResponse.statusCode() + " " + tickResponse.body());

        // Step 5: Tick again to ensure command is processed
        System.out.println("\n=== Step 5: Tick again ===");
        HttpResponse<String> tickResponse2 = post("/api/simulation/tick", "");
        System.out.println("Tick 2 response: " + tickResponse2.statusCode() + " " + tickResponse2.body());

        // Step 6: Check snapshot after spawn
        System.out.println("\n=== Step 6: Check snapshot after spawn ===");
        HttpResponse<String> snapshotResponse2 = get("/api/snapshots");
        System.out.println("Snapshot after spawn: " + snapshotResponse2.body());

        // Step 7: Check match-specific snapshot
        System.out.println("\n=== Step 7: Check match-specific snapshot ===");
        HttpResponse<String> matchSnapshotResponse = get("/api/snapshots/match/" + matchId);
        System.out.println("Match " + matchId + " snapshot: " + matchSnapshotResponse.body());

        // Parse and verify the snapshot has entity data
        String snapshotData = snapshotResponse2.body();
        assertThat(snapshotData).as("Snapshot should not have empty modules array").doesNotContain("\"data\":{}");

        System.out.println("\n=== Test completed ===");
    }

    @Test
    @DisplayName("Check what modules are available")
    void checkAvailableModules() throws Exception {
        HttpResponse<String> response = get("/api/modules");
        System.out.println("Available modules: " + response.body());
    }

    @Test
    @DisplayName("Check commands available")
    void checkAvailableCommands() throws Exception {
        HttpResponse<String> response = get("/api/commands");
        System.out.println("Available commands: " + response.body());
    }

    @Test
    @DisplayName("Test match creation without id field")
    void testMatchCreationWithoutId() throws Exception {
        // This mimics what MatchService does - sends enabledModuleNames without id
        String matchBody = "{\"enabledModuleNames\":[\"SpawnModule\",\"RenderModule\"]}";
        HttpResponse<String> matchResponse = post("/api/matches", matchBody);
        System.out.println("Create match response: " + matchResponse.statusCode() + " " + matchResponse.body());

        if (matchResponse.statusCode() == 201) {
            // Try to extract match ID
            String body = matchResponse.body();
            System.out.println("Match created successfully");

            // Get the match to verify it has correct modules
            HttpResponse<String> matches = get("/api/matches");
            System.out.println("All matches: " + matches.body());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .GET()
                .header("Accept", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if (body != null && !body.isEmpty()) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
