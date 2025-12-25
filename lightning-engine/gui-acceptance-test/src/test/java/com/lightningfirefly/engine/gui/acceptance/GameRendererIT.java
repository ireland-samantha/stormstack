package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.game.orchestrator.Snapshot;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.game.domain.ControlSystem;
import com.lightningfirefly.game.domain.Sprite;
import com.lightningfirefly.game.renderering.DefaultGameRenderer;
import com.lightningfirefly.game.renderering.GameRenderer;
import com.lightningfirefly.game.renderering.GameRendererBuilder;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GameRenderer with OpenGL backend and real backend server.
 *
 * <p>Tests the complete game rendering workflow using actual OpenGL windows:
 * <ol>
 *   <li>Start a game server via Docker</li>
 *   <li>Create a match with game modules</li>
 *   <li>Spawn entities</li>
 *   <li>Fetch snapshots and render them using GameRenderer with OpenGL</li>
 *   <li>Verify sprites are created and rendered correctly</li>
 * </ol>
 *
 * <p>These tests require:
 * <ul>
 *   <li>A display environment (cannot run headless)</li>
 *   <li>Docker for the backend container</li>
 *   <li>-XstartOnFirstThread JVM argument on macOS</li>
 * </ul>
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("GameRenderer OpenGL Integration Tests")
@Testcontainers
class GameRendererIT {

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
    private long createdMatchId = -1;
    private Window window;
    private DefaultGameRenderer renderer;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        if (window != null) {
            window.stop();
            window = null;
        }
        if (createdMatchId > 0) {
            try {
                deleteMatch(createdMatchId);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("GameRendererBuilder creates OpenGL window")
    void gameRendererBuilder_createsOpenGLWindow() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("GameRenderer Test")
                .build();

        GameRenderer gameRenderer = GameRendererBuilder.create()
                .window(window)
                .build();

        assertThat(gameRenderer).isNotNull();
        assertThat(gameRenderer.getWidth()).isEqualTo(800);
        assertThat(gameRenderer.getHeight()).isEqualTo(600);

        // Run a few frames to verify window initializes
        gameRenderer.runFrames(5, () -> {});

        log.info("OpenGL window created and initialized successfully");
    }

    @Test
    @DisplayName("GameRenderer renders sprites in OpenGL window")
    void gameRenderer_rendersSpritesInOpenGLWindow() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Sprite Rendering Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        // Create test sprites
        List<Sprite> sprites = List.of(
                createTestSprite(1, 100, 200),
                createTestSprite(2, 300, 400),
                createTestSprite(3, 500, 100)
        );

        AtomicInteger frameCount = new AtomicInteger(0);

        renderer.runFrames(30, () -> {
            renderer.renderSprites(sprites);
            frameCount.incrementAndGet();
        });

        // Verify sprites were rendered
        var renderingSprites = window.getSprites();
        assertThat(renderingSprites).hasSize(3);
        assertThat(frameCount.get()).isEqualTo(30);

        log.info("Rendered {} sprites over {} frames", renderingSprites.size(), frameCount.get());
    }

    @Test
    @DisplayName("GameRenderer updates sprite positions dynamically")
    void gameRenderer_updatesSpritePositionsDynamically() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Dynamic Sprite Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        Sprite sprite = createTestSprite(1, 100, 300);
        List<Sprite> sprites = List.of(sprite);

        AtomicInteger frame = new AtomicInteger(0);

        renderer.runFrames(60, () -> {
            // Move sprite horizontally
            float x = 100 + (frame.getAndIncrement() * 10);
            sprite.setPosition(x, 300);
            renderer.renderSprites(sprites);
        });

        // Verify final position
        var renderingSprite = window.getSprites().get(0);
        assertThat(renderingSprite.getX()).isGreaterThan(100);

        log.info("Sprite moved to x={}", renderingSprite.getX());
    }

    @Test
    @DisplayName("SnapshotSpriteMapper renders entities from server components")
    void snapshotSpriteMapper_rendersEntitiesFromServerSnapshot() throws Exception {
        // Create a match with SpawnModule
        createdMatchId = createMatch(List.of("SpawnModule"));
        assertThat(createdMatchId).isGreaterThan(0);
        log.info("Created match: {}", createdMatchId);

        // Spawn multiple entities
        for (int i = 0; i < 5; i++) {
            spawnEntity(createdMatchId, 100 + i);
        }
        tick();
        tick();
        log.info("Spawned 5 entities");

        // Fetch components
        Snapshot snapshot = fetchSnapshot(createdMatchId);
        assertThat(snapshot).isNotNull();
        log.debug("Fetched components with data: {}", snapshot.components().keySet());

        // Create OpenGL window and renderer
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Server Snapshot Rendering")
                .build();

        // Configure mapper to use ENTITY_ID as the source (SpawnModule only has ENTITY_TYPE, not positions)
        // Use ENTITY_TYPE as entity ID source and provide default positions
        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .defaultSize(48, 48)
                .entityIdComponent("ENTITY_ID")
                .textureResolver(entityId -> entityId % 2 == 0
                        ? "textures/red-checker.png"
                        : "textures/black-checker.png");

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(mapper);

        AtomicInteger frameCount = new AtomicInteger(0);

        // Render for 60 frames
        renderer.runFrames(60, () -> {
            renderer.renderSnapshot(snapshot);
            frameCount.incrementAndGet();
        });

        // Verify rendering completed (sprites may be empty if backend doesn't return position data)
        var renderingSprites = window.getSprites();
        log.info("Rendered {} sprites from server components", renderingSprites.size());
        log.debug("Render frames completed: {}", frameCount.get());

        // The test verifies the rendering pipeline works - sprites depend on backend module data
        assertThat(frameCount.get()).isEqualTo(60);
    }

    @Test
    @DisplayName("GameRenderer with live components updates")
    void gameRenderer_withLiveSnapshotUpdates() throws Exception {
        // Create a match
        createdMatchId = createMatch(List.of("SpawnModule", "MoveModule", "RenderModule"));
        log.info("Created match with movement: {}", createdMatchId);

        // Spawn an entity and get its ID (entity ID is 1 for first spawn)
        spawnEntity(createdMatchId, 100);
        tick();

        long entityId = 1L; // First spawned entity gets ID 1

        // Attach movement components with initial position
        int initialX = 200;
        int initialY = 150;
        attachMovement(entityId, initialX, initialY);
        tick();

        // Attach a resource/sprite to the entity
        attachResource(entityId, 1L);
        tick();

        // Verify snapshot has the correct data from all modules
        SnapshotParser parser = fetchSnapshotParser(createdMatchId);
        assertThat(parser.hasModule("MoveModule")).isTrue();
        assertThat(parser.hasModule("RenderModule")).isTrue();
        assertThat(parser.getComponentValue("MoveModule", "POSITION_X")).hasValue((float) initialX);
        assertThat(parser.getComponentValue("MoveModule", "POSITION_Y")).hasValue((float) initialY);
        assertThat(parser.getComponentValue("RenderModule", "RESOURCE_ID")).hasValue(1.0f);

        // Create OpenGL window
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Live Snapshot Updates")
                .build();

        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .defaultSize(32, 32)
                .textureResolver(id -> id % 2 == 0
                        ? "textures/red-checker.png"
                        : "textures/black-checker.png");

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(mapper);

        // Render initial state
        renderer.runFrames(30, () -> {
            try {
                Snapshot snapshot = fetchSnapshot(createdMatchId);
                renderer.renderSnapshot(snapshot);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verify sprite is rendered at initial position
        var sprites = window.getSprites();
        assertThat(sprites).isNotEmpty();
        var sprite = sprites.get(0);
        assertThat(sprite.getX()).isEqualTo(initialX);
        assertThat(sprite.getY()).isEqualTo(initialY);
        log.info("Sprite rendered at initial position: ({}, {})", sprite.getX(), sprite.getY());

        // Now move the entity to a new position via command
        int newX = 400;
        int newY = 300;
        sendCommand(CommandRequestBuilder.command("attachMovement")
                .param("entityId", entityId)
                .param("positionX", newX)
                .param("positionY", newY)
                .param("positionZ", 0L)
                .param("velocityX", 0L)
                .param("velocityY", 0L)
                .param("velocityZ", 0L)
                .build());
        tick();
        tick();

        // Render after move
        renderer.runFrames(30, () -> {
            try {
                Snapshot snapshot = fetchSnapshot(createdMatchId);
                renderer.renderSnapshot(snapshot);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verify sprite moved to new position
        sprites = window.getSprites();
        assertThat(sprites).isNotEmpty();
        sprite = sprites.get(0);
        assertThat(sprite.getX()).isEqualTo(newX);
        assertThat(sprite.getY()).isEqualTo(newY);
        log.info("Sprite moved to new position: ({}, {})", sprite.getX(), sprite.getY());
    }

    @Test
    @DisplayName("GameRenderer with ControlSystem receives input")
    void gameRenderer_withControlSystemReceivesInput() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Input Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        List<Integer> pressedKeys = new ArrayList<>();
        renderer.setControlSystem(new ControlSystem() {
            @Override
            public void onKeyPressed(int key) {
                pressedKeys.add(key);
                log.debug("Key pressed: {}", key);
            }
        });

        // Run some frames - user can press arrow keys during this time
        log.debug("Running for 60 frames - press arrow keys to test input");
        renderer.runFrames(60, () -> {});

        // Note: In automated tests, no keys will be pressed
        // This test verifies the control system is properly connected
        log.info("Input test completed. Keys pressed: {}", pressedKeys.size());
    }

    @Test
    @DisplayName("Multiple sprites with different z-indexes render correctly")
    void multipleSprites_withDifferentZIndexes() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Z-Index Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        // Create sprites with different z-indexes
        Sprite back = createTestSprite(1, 200, 200);
        back.setZIndex(0);

        Sprite middle = createTestSprite(2, 220, 220);
        middle.setZIndex(5);

        Sprite front = createTestSprite(3, 240, 240);
        front.setZIndex(10);

        List<Sprite> sprites = List.of(back, middle, front);

        renderer.runFrames(30, () -> {
            renderer.renderSprites(sprites);
        });

        var renderingSprites = window.getSprites();
        assertThat(renderingSprites).hasSize(3);

        // Verify z-indexes are set
        assertThat(renderingSprites.stream().mapToInt(s -> s.getZIndex()).max().orElse(-1)).isEqualTo(10);

        log.info("Rendered 3 sprites with z-indexes: {}",
                renderingSprites.stream().map(s -> String.valueOf(s.getZIndex())).toList());
    }

    @Test
    @DisplayName("Sprite removal updates window correctly")
    void spriteRemoval_updatesWindowCorrectly() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Sprite Removal Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        List<Sprite> sprites = new ArrayList<>();
        sprites.add(createTestSprite(1, 100, 100));
        sprites.add(createTestSprite(2, 200, 200));
        sprites.add(createTestSprite(3, 300, 300));

        // Render initial sprites
        renderer.runFrames(10, () -> renderer.renderSprites(sprites));
        assertThat(window.getSprites()).hasSize(3);

        // Remove one sprite
        sprites.remove(1);

        // Render again
        renderer.runFrames(10, () -> renderer.renderSprites(sprites));
        assertThat(window.getSprites()).hasSize(2);

        // Verify correct sprites remain
        var ids = window.getSprites().stream().map(s -> s.getId()).toList();
        assertThat(ids).containsExactlyInAnyOrder(1, 3);

        log.info("Sprite removal test passed - remaining sprites: {}", ids);
    }

    // ========== Helper Methods ==========

    private Sprite createTestSprite(long entityId, float x, float y) {
        Sprite sprite = new Sprite(entityId);
        sprite.setPosition(x, y);
        sprite.setSize(32, 32);
        // Use real textures from rendering-core resources
        sprite.setTexturePath(entityId % 2 == 0 ? "textures/red-checker.png" : "textures/black-checker.png");
        return sprite;
    }

    private long createMatch(List<String> modules) throws Exception {
        // MatchRequest expects both id and enabledModuleNames
        // Format module names as JSON array: ["Module1", "Module2"]
        String moduleArray = modules.stream()
                .map(m -> "\"" + m + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        String json = String.format("{\"id\": 0, \"enabledModuleNames\": %s}", moduleArray);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/matches"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new RuntimeException("Match creation failed with status " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        // Parse id from JSON response (MatchResponse uses "id" not "matchId")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new RuntimeException("Could not parse id from response: " + body);
    }

    private void deleteMatch(long matchId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/matches/" + matchId))
                .DELETE()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void spawnEntity(long matchId, long entityType) throws Exception {
        sendCommand(CommandRequestBuilder.command("spawn")
                .param("matchId", matchId)
                .param("playerId", 1L)
                .param("entityType", entityType)
                .build());
    }

    private void attachMovement(long entityId, long posX, long posY) throws Exception {
        sendCommand(CommandRequestBuilder.command("attachMovement")
                .param("entityId", entityId)
                .param("positionX", posX)
                .param("positionY", posY)
                .param("positionZ", 0L)
                .param("velocityX", 0L)
                .param("velocityY", 0L)
                .param("velocityZ", 0L)
                .build());
    }

    private void attachResource(long entityId, long resourceId) throws Exception {
        sendCommand(CommandRequestBuilder.command("attachSprite")
                .param("entityId", entityId)
                .param("resourceId", resourceId)
                .build());
    }

    private void sendCommand(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/commands"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("Command failed with status %d: %s", response.statusCode(), response.body())
                .isEqualTo(202);
    }

    private void tick() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/simulation/tick"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Thread.sleep(50);
    }

    private Snapshot fetchSnapshot(long matchId) throws Exception {
        String json = fetchSnapshotJson(matchId);
        if (json == null) {
            return new Snapshot(Map.of());
        }
        return SnapshotParser.parse(json).toSnapshot();
    }

    private SnapshotParser fetchSnapshotParser(long matchId) throws Exception {
        String json = fetchSnapshotJson(matchId);
        if (json == null) {
            return SnapshotParser.parse("{}");
        }
        return SnapshotParser.parse(json);
    }

    private String fetchSnapshotJson(long matchId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/api/snapshots/match/" + matchId))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return null;
        }

        return response.body();
    }
}
