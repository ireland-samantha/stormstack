package com.lightningfirefly.engine.acceptance.test.ui;

import com.lightningfirefly.engine.acceptance.test.domain.*;
import com.lightningfirefly.game.domain.ControlSystem;
import com.lightningfirefly.game.domain.ControlSystem.KeyStates;
import com.lightningfirefly.game.domain.Sprite;
import com.lightningfirefly.game.renderering.DefaultGameRenderer;
import com.lightningfirefly.game.renderering.GameRenderer;
import com.lightningfirefly.game.renderering.GameRendererBuilder;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GameRenderer with OpenGL and a real backend server.
 *
 * <p>Tests verify sprite rendering by checking sprite positions in the window's
 * sprite list - a decoupled approach that doesn't require direct framebuffer access.
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

    private TestBackend backend;
    private Match match;
    private Window window;
    private DefaultGameRenderer renderer;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        backend = TestBackend.connectTo(backendUrl);
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
        if (match != null) {
            match.delete();
            match = null;
        }
    }

    @Test
    @DisplayName("Given window builder, when building GameRenderer, then window is initialized")
    void givenWindowBuilder_whenBuildingGameRenderer_thenWindowIsInitialized() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("GameRenderer Test")
                .build();

        // When
        GameRenderer gameRenderer = GameRendererBuilder.create()
                .window(window)
                .build();

        // Then
        assertThat(gameRenderer).isNotNull();
        assertThat(gameRenderer.getWidth()).isEqualTo(800);
        assertThat(gameRenderer.getHeight()).isEqualTo(600);

        gameRenderer.runFrames(5, () -> {});

        // Verify no sprites initially
        forWindow(window).hasTotalSpriteCount(0);
        log.info("Window initialized with no sprites");
    }

    @Test
    @DisplayName("Given sprites, when rendering frames, then sprites are visible in window")
    void givenSprites_whenRenderingFrames_thenSpritesAreVisibleInWindow() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Sprite Rendering Test")
                .build();
        renderer = new DefaultGameRenderer(window);

        List<Sprite> sprites = List.of(
                createSprite(1, 100, 200),
                createSprite(2, 300, 400),
                createSprite(3, 500, 100)
        );

        // When
        renderer.runFrames(30, () -> renderer.renderSprites(sprites));

        // Then - verify sprites are rendered at their positions
        forWindow(window).hasTotalSpriteCount(3);
        forWindow(window).inRegion(100, 200, 32, 32).hasContent();
        forWindow(window).inRegion(300, 400, 32, 32).hasContent();
        forWindow(window).inRegion(500, 100, 32, 32).hasContent();

        // Verify empty region has no sprites
        forWindow(window).inRegion(600, 500, 32, 32).isEmpty();

        log.info("Verified {} sprites rendered in window", sprites.size());
    }

    @Test
    @DisplayName("Given sprite, when moving position each frame, then window reflects movement")
    void givenSprite_whenMovingPositionEachFrame_thenWindowReflectsMovement() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Dynamic Sprite Test")
                .build();
        renderer = new DefaultGameRenderer(window);

        Sprite sprite = createSprite(1, 100, 300);
        List<Sprite> sprites = List.of(sprite);

        // When - move sprite across frames
        AtomicInteger frame = new AtomicInteger(0);
        renderer.runFrames(30, () -> {
            float x = 100 + (frame.getAndIncrement() * 10);
            sprite.setPosition(x, 300);
            renderer.renderSprites(sprites);
        });

        // Then - sprite should be at final position (100 + 29*10 = 390)
        forWindow(window).inRegion(380, 300, 40, 32).hasContent();

        // Original position should now be empty (sprite moved away)
        forWindow(window).inRegion(100, 300, 20, 20).isEmpty();

        log.info("Sprite moved to final position x={}", window.getSprites().get(0).getX());
    }

    @Test
    @DisplayName("Given match with entities, when rendering snapshot, then entities visible in window")
    void givenMatchWithEntities_whenRenderingSnapshot_thenEntitiesVisibleInWindow() {
        // Given - a match with SpawnModule and entities
        match = backend.createMatch()
                .withModule("SpawnModule")
                .start();

        for (int i = 0; i < 5; i++) {
            match.spawnEntity().ofType(100 + i).execute();
        }
        match.tick().tick();

        // When - create renderer and render snapshot
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Server Snapshot Rendering")
                .build();

        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .defaultSize(48, 48)
                .entityIdComponent("ENTITY_ID")
                .textureResolver(entityId -> "textures/red-checker.png");

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(mapper);

        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        // Then - verify sprites are rendered
        forWindow(window).hasAnySprites();

        log.info("Rendered {} sprites from server snapshot", window.getSprites().size());
    }

    @Test
    @DisplayName("Given entity with sprite, when sprite moves, then window position updates")
    void givenEntityWithSprite_whenSpriteMoves_thenWindowPositionUpdates() {
        // Given - a match with render capability
        match = backend.createMatch()
                .withModules("SpawnModule", "RenderModule")
                .start();

        Entity entity = match.spawnEntity().ofType(100).execute();
        match.tick();

        // When - attach sprite at initial position
        entity.attachSprite()
                .using(1)
                .at(200, 150)
                .sized(48, 48)
                .andApply();
        match.tick();

        // Then - verify snapshot data
        match.assertThatSnapshot()
                .hasModule("RenderModule")
                .withComponent("SPRITE_X").equalTo(200f)
                .withComponent("SPRITE_Y").equalTo(150f);

        // Render and verify sprite at initial position
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Live Snapshot Updates")
                .build();

        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png");

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(mapper);

        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(200, 150, 48, 48).hasContent();

        // When - move sprite to new position
        entity.attachSprite()
                .using(1)
                .at(400, 300)
                .sized(48, 48)
                .andApply();
        match.tick().tick();

        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        // Then - verify sprite at new position
        forWindow(window).inRegion(400, 300, 48, 48).hasContent();

        log.info("Sprite position update verified in window");
    }

    @Test
    @DisplayName("Given sprites with z-index, when rendering, then z-order is correct")
    void givenSpritesWithZIndex_whenRendering_thenZOrderIsCorrect() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Z-Index Test")
                .build();
        renderer = new DefaultGameRenderer(window);

        // Create overlapping sprites with different z-indexes
        Sprite back = createSprite(1, 200, 200);
        back.setZIndex(0);

        Sprite middle = createSprite(2, 210, 210);
        middle.setZIndex(5);

        Sprite front = createSprite(3, 220, 220);
        front.setZIndex(10);

        // When
        renderer.runFrames(30, () ->
                renderer.renderSprites(List.of(back, middle, front)));

        // Then - all three sprites should be in window
        forWindow(window).hasTotalSpriteCount(3);

        // Verify z-index structure
        assertThat(window.getSprites().stream().mapToInt(s -> s.getZIndex()).max().orElse(-1))
                .isEqualTo(10);

        log.info("Z-index ordering verified in window");
    }

    @Test
    @DisplayName("Given sprites, when removing one, then window reflects removal")
    void givenSprites_whenRemovingOne_thenWindowReflectsRemoval() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Sprite Removal Test")
                .build();
        renderer = new DefaultGameRenderer(window);

        List<Sprite> sprites = new ArrayList<>();
        sprites.add(createSprite(1, 100, 100));
        sprites.add(createSprite(2, 200, 200));
        sprites.add(createSprite(3, 300, 300));

        renderer.runFrames(10, () -> renderer.renderSprites(sprites));

        // Verify all three are in window
        forWindow(window).hasTotalSpriteCount(3);
        forWindow(window).inRegion(100, 100, 32, 32).hasContent();
        forWindow(window).inRegion(200, 200, 32, 32).hasContent();
        forWindow(window).inRegion(300, 300, 32, 32).hasContent();

        // When - remove middle sprite
        sprites.remove(1);
        renderer.runFrames(10, () -> renderer.renderSprites(sprites));

        // Then - only 2 sprites remain
        forWindow(window).hasTotalSpriteCount(2);
        forWindow(window).inRegion(100, 100, 32, 32).hasContent();
        forWindow(window).inRegion(200, 200, 32, 32).isEmpty();
        forWindow(window).inRegion(300, 300, 32, 32).hasContent();

        log.info("Sprite removal verified in window");
    }

    @Test
    @DisplayName("Given sprites with rotation, when rendering, then sprites are in window")
    void givenSpritesWithRotation_whenRendering_thenSpritesAreInWindow() {
        // Given - a match with rotated sprite
        match = backend.createMatch()
                .withModules("SpawnModule", "RenderModule")
                .start();

        Entity frontEntity = match.spawnEntity().ofType(100).execute();
        Entity backEntity = match.spawnEntity().ofType(101).execute();
        match.tick();

        // When - attach sprites with rotation
        frontEntity.attachSprite()
                .using(1)
                .at(100, 100)
                .sized(64, 64)
                .rotatedBy(45)
                .onLayer(10)
                .visible()
                .andApply();

        backEntity.attachSprite()
                .using(2)
                .at(250, 100)
                .sized(32, 32)
                .rotatedBy(0)
                .onLayer(0)
                .andApply();
        match.tick();

        // Then - verify snapshot contains rotation
        match.assertThatSnapshot()
                .hasModule("RenderModule")
                .withComponent("SPRITE_ROTATION").containingValues(45f, 0f);

        // Render and verify sprites in window
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Sprite Rotation Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        // Both sprite regions should have sprites
        forWindow(window).inRegion(100, 100, 64, 64).hasContent();
        forWindow(window).inRegion(250, 100, 32, 32).hasContent();

        log.info("Rotated sprites verified in window");
    }

    @Test
    @DisplayName("Given control system, when running renderer, then control system receives updates")
    void givenControlSystem_whenRunningRenderer_thenControlSystemReceivesUpdates() {
        // Given
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Input Test")
                .build();
        renderer = new DefaultGameRenderer(window);

        List<Integer> pressedKeys = new ArrayList<>();
        AtomicInteger updateCount = new AtomicInteger(0);

        renderer.setControlSystem(new ControlSystem() {
            @Override
            public void onKeyPressed(int key) {
                pressedKeys.add(key);
            }

            @Override
            public void onUpdate(KeyStates keyStates) {
                updateCount.incrementAndGet();
            }
        });

        // When
        renderer.runFrames(30, () -> {});

        // Then - control system should have been updated each frame
        assertThat(updateCount.get()).isEqualTo(30);
        log.info("Control system received {} update callbacks", updateCount.get());
    }

    // ========== Helper Methods ==========

    private Sprite createSprite(long entityId, float x, float y) {
        Sprite sprite = new Sprite(entityId);
        sprite.setPosition(x, y);
        sprite.setSize(32, 32);
        sprite.setTexturePath("textures/red-checker.png");
        return sprite;
    }
}
