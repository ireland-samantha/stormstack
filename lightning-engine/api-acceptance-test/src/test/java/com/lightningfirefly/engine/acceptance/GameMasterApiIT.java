package com.lightningfirefly.engine.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API acceptance test for game master functionality.
 *
 * <p>This test verifies:
 * <ul>
 *   <li>Game master REST endpoints work correctly</li>
 *   <li>Game masters can be listed, reloaded, and uninstalled</li>
 *   <li>Game masters execute per-match when enabled</li>
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
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Game Master API Acceptance Test")
@Testcontainers
class GameMasterApiIT {

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
    private long createdMatchId = -1;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        baseUrl = String.format("http://%s:%d", host, port);
        System.out.println("Backend URL from testcontainers: " + baseUrl);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (createdMatchId > 0) {
            try {
                deleteMatch(createdMatchId);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("GET /api/gamemasters returns list with TickCounter game master")
    void getGameMasters_returnsTickCounterGameMaster() throws Exception {
        System.out.println("=== Testing GET /api/gamemasters ===");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/gamemasters"))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode gameMasters = objectMapper.readTree(response.body());
        assertThat(gameMasters.isArray()).isTrue();
        assertThat(gameMasters.size()).as("Should have at least TickCounter game master").isGreaterThanOrEqualTo(1);

        // Verify TickCounter is in the list
        boolean hasTickCounter = false;
        for (JsonNode gm : gameMasters) {
            if ("TickCounter".equals(gm.path("name").asText())) {
                hasTickCounter = true;
                break;
            }
        }
        assertThat(hasTickCounter).as("TickCounter game master should be installed").isTrue();

        System.out.println("=== GET /api/gamemasters test passed ===");
    }

    @Test
    @DisplayName("POST /api/gamemasters/reload reloads game masters")
    void reloadGameMasters_succeeds() throws Exception {
        System.out.println("=== Testing POST /api/gamemasters/reload ===");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/gamemasters/reload"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode gameMasters = objectMapper.readTree(response.body());
        assertThat(gameMasters.isArray()).isTrue();

        System.out.println("=== POST /api/gamemasters/reload test passed ===");
    }

    @Test
    @DisplayName("Match with TickCounter game master runs on tick")
    void matchWithTickCounter_executesOnTick() throws Exception {
        System.out.println("=== Testing TickCounter game master execution per match ===");

        // Create a match with the TickCounter game master enabled
        createdMatchId = createMatch(List.of("SpawnModule"), List.of("TickCounter"));
        System.out.println("Created match with TickCounter: " + createdMatchId);

        assertThat(createdMatchId).isGreaterThan(0);

        // Advance several ticks to let game master execute
        // TickCounter logs every 100 ticks, but we just verify it doesn't crash
        for (int i = 0; i < 5; i++) {
            long tick = advanceTick();
            System.out.println("Advanced to tick: " + tick);
        }

        // Verify the match still exists and is functional (game master didn't crash)
        HttpRequest matchRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/matches/" + createdMatchId))
                .GET()
                .build();

        HttpResponse<String> matchResponse = httpClient.send(matchRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(matchResponse.statusCode()).isEqualTo(200);

        // Verify the match has the TickCounter enabled
        JsonNode matchJson = objectMapper.readTree(matchResponse.body());
        JsonNode enabledGameMasters = matchJson.path("enabledGameMasterNames");
        assertThat(enabledGameMasters.isArray()).isTrue();
        assertThat(enabledGameMasters.size()).isEqualTo(1);
        assertThat(enabledGameMasters.get(0).asText()).isEqualTo("TickCounter");

        System.out.println("Match response: " + matchResponse.body());
        System.out.println("=== TickCounter game master execution test passed ===");
    }

    @Test
    @DisplayName("DELETE /api/gamemasters/{name} returns 404 for non-existent game master")
    void deleteNonExistentGameMaster_returns404() throws Exception {
        System.out.println("=== Testing DELETE /api/gamemasters/NonExistent ===");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/gamemasters/NonExistentGameMaster"))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        assertThat(response.statusCode()).isEqualTo(404);

        System.out.println("=== DELETE non-existent game master test passed ===");
    }

    @Test
    @DisplayName("GET /api/gamemasters/{name} returns 404 for non-existent game master")
    void getNonExistentGameMaster_returns404() throws Exception {
        System.out.println("=== Testing GET /api/gamemasters/NonExistent ===");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/gamemasters/NonExistentGameMaster"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        assertThat(response.statusCode()).isEqualTo(404);

        System.out.println("=== GET non-existent game master test passed ===");
    }

    // ========== Helper Methods ==========

    private long createMatch(List<String> modules, List<String> gameMasters) throws IOException, InterruptedException {
        String modulesJson = "[" + String.join(",", modules.stream().map(m -> "\"" + m + "\"").toList()) + "]";
        String gameMastersJson = "[" + String.join(",", gameMasters.stream().map(gm -> "\"" + gm + "\"").toList()) + "]";
        String json = "{\"enabledModuleNames\":" + modulesJson + ",\"enabledGameMasterNames\":" + gameMastersJson + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/matches"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Create match response: status=" + response.statusCode() + ", body=" + response.body());
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
}
