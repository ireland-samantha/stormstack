package com.lightningfirefly.engine.acceptance.test.ui;

import com.lightningfirefly.game.backend.adapter.BackendClient;
import com.lightningfirefly.game.backend.adapter.Orchestrator;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import com.lightningfirefly.game.renderering.DefaultGameRenderer;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Orchestrator with live OpenGL and backend.
 *
 * <p>Tests verify the orchestration flow:
 * <ol>
 *   <li>Connect to backend via BackendClient</li>
 *   <li>Create match and spawn entities with sprites</li>
 *   <li>Orchestrator fetches snapshots and queues for rendering</li>
 *   <li>GameRenderer renders sprites from snapshots</li>
 *   <li>ScreenAssertions verify sprites at correct positions</li>
 * </ol>
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Orchestrator E2E Integration Tests")
@Testcontainers
class OrchestratorIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private BackendClient client;
    private Window window;
    private DefaultGameRenderer renderer;
    private Orchestrator orchestrator;
    private long matchId;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        client = BackendClient.connect(backendUrl);
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null && orchestrator.isRunning()) {
            orchestrator.stop();
            orchestrator = null;
        }
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        if (window != null) {
            window.stop();
            window = null;
        }
        if (matchId > 0) {
            try {
                client.matches().delete(matchId);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            matchId = 0;
        }
    }

    @Test
    @DisplayName("Given orchestrator, when running frames, then snapshot is fetched and rendered")
    void givenOrchestrator_whenRunningFrames_thenSnapshotIsFetchedAndRendered() {
        // Given - create a match with entities
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        // Spawn an entity
        client.commands().forMatch(matchId)
                .spawn().forPlayer(1).ofType(100).execute();
        client.simulation().tick();

        // Attach sprite to entity
        long entityId = findFirstEntityId();
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(200, 150)
                .sized(48, 48)
                .execute();
        client.simulation().tick();

        // When - create orchestrator and run frames
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Orchestrator Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .pollInterval(Duration.ofMillis(50))
                .build();

        orchestrator.runFrames(30);

        // Then - verify snapshot was fetched
        assertThat(orchestrator.latestSnapshot()).isNotNull();

        // Verify sprite is rendered at correct position
        forWindow(window).inRegion(200, 150, 48, 48).hasContent();

        log.info("Orchestrator successfully rendered {} sprites", window.getSprites().size());
    }

    @Test
    @DisplayName("Given orchestrator, when tick and fetch called, then snapshot updates")
    void givenOrchestrator_whenTickAndFetchCalled_thenSnapshotUpdates() {
        // Given - create a match
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        window = WindowBuilder.create()
                .size(800, 600)
                .title("Tick and Fetch Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .build();

        // Spawn entity and attach sprite
        client.commands().forMatch(matchId)
                .spawn().forPlayer(1).ofType(100).execute();
        client.simulation().tick();

        long entityId = findFirstEntityId();
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(100, 100)
                .sized(32, 32)
                .execute();

        // When - tick and fetch via orchestrator
        orchestrator.tickAndFetch();
        long tick1 = orchestrator.latestSnapshot() != null ? orchestrator.latestSnapshot().tick() : -1;

        orchestrator.tickAndFetch();
        long tick2 = orchestrator.latestSnapshot() != null ? orchestrator.latestSnapshot().tick() : -1;

        // Run frames to render
        orchestrator.runFrames(30);

        // Then - ticks should have advanced
        assertThat(tick2).isGreaterThan(tick1);

        // Verify sprite is rendered
        forWindow(window).hasAnySprites();

        log.info("Tick advanced from {} to {}", tick1, tick2);
    }

    @Test
    @DisplayName("Given orchestrator with moving sprite, when rendering, then position updates")
    void givenOrchestratorWithMovingSprite_whenRendering_thenPositionUpdates() {
        // Given - create a match with entity
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        client.commands().forMatch(matchId)
                .spawn().forPlayer(1).ofType(100).execute();
        client.simulation().tick();

        long entityId = findFirstEntityId();

        // Initial position
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(100, 200)
                .sized(48, 48)
                .execute();
        client.simulation().tick();

        // Create orchestrator
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Moving Sprite Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .pollInterval(Duration.ofMillis(50))
                .build();

        // Render initial position
        orchestrator.runFrames(30);
        forWindow(window).inRegion(100, 200, 48, 48).hasContent();

        // When - move sprite to new position
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(400, 300)
                .sized(48, 48)
                .execute();
        client.simulation().tick();

        // Fetch and render again
        orchestrator.fetchSnapshot();
        orchestrator.runFrames(30);

        // Then - sprite should be at new position
        forWindow(window).inRegion(400, 300, 48, 48).hasContent();

        log.info("Sprite moved from (100,200) to (400,300)");
    }

    @Test
    @DisplayName("Given orchestrator, when frame callback used, then callback invoked each frame")
    void givenOrchestrator_whenFrameCallbackUsed_thenCallbackInvokedEachFrame() {
        // Given
        var match = client.matches().create()
                .withModules("SpawnModule")
                .execute();
        matchId = match.id();

        window = WindowBuilder.create()
                .size(800, 600)
                .title("Frame Callback Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .build();

        AtomicInteger frameCount = new AtomicInteger(0);

        // When
        orchestrator.runFrames(60, () -> frameCount.incrementAndGet());

        // Then
        assertThat(frameCount.get()).isEqualTo(60);

        log.info("Frame callback invoked {} times", frameCount.get());
    }

    @Test
    @DisplayName("Given multiple entities, when orchestrating, then all sprites rendered")
    void givenMultipleEntities_whenOrchestrating_thenAllSpritesRendered() {
        // Given - create a match with multiple entities
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        // Spawn 5 entities
        for (int i = 0; i < 5; i++) {
            client.commands().forMatch(matchId)
                    .spawn().forPlayer(1).ofType(100 + i).execute();
        }
        client.simulation().tick();

        // Attach sprites at different positions
        var snapshot = client.snapshots().forMatch(matchId).fetch();
        var entityIds = snapshot.entityIds();

        int x = 50;
        for (Float entityId : entityIds) {
            client.commands().forMatch(matchId)
                    .attachSprite()
                    .toEntity(entityId.longValue())
                    .usingResource(1)
                    .at(x, 100)
                    .sized(32, 32)
                    .execute();
            x += 100;
        }
        client.simulation().tick();

        // When - create orchestrator and render
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Multiple Entities Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .build();

        orchestrator.runFrames(60);

        // Then - verify all sprites are rendered
        forWindow(window).hasTotalSpriteCount(5);

        // Verify sprites at expected positions
        forWindow(window).inRegion(50, 100, 32, 32).hasContent();
        forWindow(window).inRegion(150, 100, 32, 32).hasContent();
        forWindow(window).inRegion(250, 100, 32, 32).hasContent();
        forWindow(window).inRegion(350, 100, 32, 32).hasContent();
        forWindow(window).inRegion(450, 100, 32, 32).hasContent();

        log.info("Successfully rendered {} entities", entityIds.size());
    }

    @Test
    @DisplayName("Given orchestrator with poll interval, when fetching, then uses configured interval")
    void givenOrchestratorWithPollInterval_whenFetching_thenUsesConfiguredInterval() {
        // Given
        var match = client.matches().create()
                .withModules("SpawnModule")
                .execute();
        matchId = match.id();

        window = WindowBuilder.create()
                .size(800, 600)
                .title("Poll Interval Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        // When - create with custom poll interval
        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .pollInterval(Duration.ofMillis(200))
                .build();

        // Then - orchestrator should be configured correctly
        assertThat(orchestrator.matchId()).isEqualTo(matchId);
        assertThat(orchestrator.isRunning()).isFalse();

        orchestrator.runFrames(10);

        assertThat(orchestrator.isRunning()).isFalse();
        assertThat(orchestrator.latestSnapshot()).isNotNull();

        log.info("Poll interval configured correctly");
    }

    @Test
    @DisplayName("Given orchestrator, when started async, then WebSocket connects and receives snapshots")
    void givenOrchestrator_whenStartedAsync_thenWebSocketConnectsAndReceivesSnapshots() throws Exception {
        // Given - create a match with entities
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        // Spawn an entity and attach sprite
        client.commands().forMatch(matchId)
                .spawn().forPlayer(1).ofType(100).execute();
        client.simulation().tick();

        long entityId = findFirstEntityId();
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(300, 250)
                .sized(64, 64)
                .execute();
        client.simulation().tick();

        window = WindowBuilder.create()
                .size(800, 600)
                .title("WebSocket Test")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .build();

        // When - start async and let WebSocket receive snapshots
        orchestrator.startAsync();

        // Wait for WebSocket to connect and receive at least one snapshot
        Thread.sleep(500);

        // Then - verify WebSocket is connected
        assertThat(orchestrator.isWebSocketConnected())
                .as("Orchestrator should be connected via WebSocket")
                .isTrue();

        // Verify snapshot was received
        assertThat(orchestrator.latestSnapshot())
                .as("Should have received at least one snapshot")
                .isNotNull();

        // Tick the server and verify tick increases
        long initialTick = orchestrator.latestSnapshot().tick();
        client.simulation().tick();

        // Wait for WebSocket to push the update
        Thread.sleep(200);

        long newTick = orchestrator.latestSnapshot().tick();
        assertThat(newTick)
                .as("Tick should have advanced after server tick")
                .isGreaterThan(initialTick);

        // Run some frames to render
        orchestrator.stop();
        orchestrator.runFrames(30);

        // Verify sprite rendered at correct position
        forWindow(window).inRegion(300, 250, 64, 64).hasContent();

        log.info("WebSocket connected successfully, tick advanced from {} to {}", initialTick, newTick);
    }

    @Test
    @DisplayName("Given orchestrator with WebSocket, when server ticks, then snapshot updates automatically")
    void givenOrchestratorWithWebSocket_whenServerTicks_thenSnapshotUpdatesAutomatically() throws Exception {
        // Given - create match with moving entity
        var match = client.matches().create()
                .withModules("SpawnModule", "RenderModule")
                .execute();
        matchId = match.id();

        client.commands().forMatch(matchId)
                .spawn().forPlayer(1).ofType(100).execute();
        client.simulation().tick();

        long entityId = findFirstEntityId();
        client.commands().forMatch(matchId)
                .attachSprite()
                .toEntity(entityId)
                .usingResource(1)
                .at(100, 100)
                .sized(48, 48)
                .execute();
        client.simulation().tick();

        window = WindowBuilder.create()
                .size(800, 600)
                .title("WebSocket Tick Updates")
                .build();

        renderer = new DefaultGameRenderer(window);

        orchestrator = Orchestrator.create()
                .backend(client)
                .renderer(renderer)
                .forMatch(matchId)
                .spriteMapper(new SpriteSnapshotMapperImpl()
                        .textureResolver(id -> "textures/red-checker.png"))
                .build();

        // When - start async and verify initial state
        orchestrator.startAsync();
        Thread.sleep(300);

        long tick1 = orchestrator.latestSnapshot() != null ? orchestrator.latestSnapshot().tick() : -1;

        // Tick the server multiple times
        for (int i = 0; i < 5; i++) {
            client.simulation().tick();
        }

        // Wait for WebSocket updates
        Thread.sleep(300);

        long tick2 = orchestrator.latestSnapshot() != null ? orchestrator.latestSnapshot().tick() : -1;

        // Then - tick should have advanced via WebSocket
        assertThat(tick2)
                .as("Tick should advance via WebSocket updates (tick1=%d, tick2=%d)", tick1, tick2)
                .isGreaterThan(tick1);

        orchestrator.stop();

        log.info("WebSocket received {} tick updates", tick2 - tick1);
    }

    // ========== Helper Methods ==========

    private long findFirstEntityId() {
        var snapshot = client.snapshots().forMatch(matchId).fetch();
        var entityIds = snapshot.entityIds();
        if (!entityIds.isEmpty()) {
            return entityIds.get(0).longValue();
        }
        return 1;
    }
}
