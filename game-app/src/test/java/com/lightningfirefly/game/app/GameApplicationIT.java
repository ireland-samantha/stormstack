package com.lightningfirefly.game.app;

import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.game.engine.ControlSystem;
import com.lightningfirefly.game.engine.GameModule;
import com.lightningfirefly.game.engine.GameScene;
import com.lightningfirefly.game.engine.Sprite;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GameApplication with OpenGL and real backend.
 *
 * <p>These tests:
 * <ul>
 *   <li>Start a real backend via Docker</li>
 *   <li>Use real OpenGL windows</li>
 *   <li>Test the complete game lifecycle: load, install, play, stop</li>
 * </ul>
 */
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("GameApplication Integration Tests")
@Testcontainers
class GameApplicationIT {

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
    private GameApplication app;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            app = null;
        }
    }

    @Test
    @DisplayName("GameApplication initializes with OpenGL window")
    void gameApplication_initializesWithOpenGLWindow() {
        app = new GameApplication(backendUrl);
        app.initialize();

        assertThat(app.getWindow()).isNotNull();
        assertThat(app.getLoadedGameModule()).isNull();
        assertThat(app.isGameInstalled()).isFalse();
        assertThat(app.isGameRunning()).isFalse();

        // Run a few frames to verify window works
        app.getWindow().runFrames(10);

        System.out.println("GameApplication initialized successfully");
    }

    @Test
    @DisplayName("Can set game module programmatically")
    void canSetGameModuleProgrammatically() {
        app = new GameApplication(backendUrl);
        app.initialize();

        TestGameModule module = new TestGameModule();
        app.setGameModule(module);

        assertThat(app.getLoadedGameModule()).isSameAs(module);
        app.getWindow().runFrames(5);
    }

    @Test
    @DisplayName("Install game calls orchestrator.installGame")
    void installGame_callsOrchestratorInstallGame() {
        app = new GameApplication(backendUrl);
        app.initialize();

        TestGameModule module = new TestGameModule();
        app.setGameModule(module);

        app.getWindow().runFrames(5);

        app.clickInstall();

        assertThat(app.isGameInstalled()).isTrue();
        assertThat(app.getOrchestrator()).isNotNull();

        app.getWindow().runFrames(5);

        System.out.println("Game installed successfully via orchestrator");
    }

    @Test
    @DisplayName("Play game starts the game and updates isGameRunning")
    void playGame_startsTheGame() {
        app = new GameApplication(backendUrl);
        app.initialize();

        TestGameModule module = new TestGameModule();
        app.setGameModule(module);

        app.getWindow().runFrames(5);

        app.clickPlay();

        assertThat(app.isGameInstalled()).isTrue(); // Auto-installs
        assertThat(app.isGameRunning()).isTrue();
        assertThat(app.getOrchestrator()).isNotNull();

        app.getWindow().runFrames(10);

        System.out.println("Game started successfully");
    }

    @Test
    @DisplayName("Stop game stops the running game")
    void stopGame_stopsTheRunningGame() {
        app = new GameApplication(backendUrl);
        app.initialize();

        TestGameModule module = new TestGameModule();
        app.setGameModule(module);

        app.getWindow().runFrames(5);

        // Start the game
        app.clickPlay();
        assertThat(app.isGameRunning()).isTrue();

        app.getWindow().runFrames(10);

        // Stop the game
        app.clickStop();
        assertThat(app.isGameRunning()).isFalse();

        app.getWindow().runFrames(5);

        System.out.println("Game stopped successfully");
    }

    @Test
    @DisplayName("Full game lifecycle: install, play, stop")
    void fullGameLifecycle_installPlayStop() {
        app = new GameApplication(backendUrl);
        app.initialize();

        TestGameModule module = new TestGameModule();
        app.setGameModule(module);

        // Install
        app.clickInstall();
        assertThat(app.isGameInstalled()).isTrue();
        app.getWindow().runFrames(5);

        // Play
        app.clickPlay();
        assertThat(app.isGameRunning()).isTrue();
        app.getWindow().runFrames(30); // Play for a bit

        // Stop
        app.clickStop();
        assertThat(app.isGameRunning()).isFalse();
        app.getWindow().runFrames(5);

        // Play again
        app.clickPlay();
        assertThat(app.isGameRunning()).isTrue();
        app.getWindow().runFrames(10);

        // Stop again
        app.clickStop();
        assertThat(app.isGameRunning()).isFalse();

        System.out.println("Full game lifecycle test completed");
    }

    @Test
    @DisplayName("Test game module with sprite and controls")
    void testGameModule_withSpriteAndControls() {
        Window window = WindowBuilder.create()
                .size(800, 600)
                .title("Test Game Module")
                .build();

        MovableSquareTestModule module = new MovableSquareTestModule();

        // Attach module to a mock scene that captures sprites
        final List<Sprite>[] capturedSprites = new List[1];
        final ControlSystem[] capturedControls = new ControlSystem[1];

        module.attachScene(new GameScene() {
            @Override
            public void attachSprite(List<Sprite> sprites) {
                capturedSprites[0] = sprites;
                // Add sprites to window
                for (Sprite sprite : sprites) {
                    window.addSprite(convertToRenderingSprite(sprite));
                }
            }

            @Override
            public void attachControlSystem(ControlSystem controlSystem) {
                capturedControls[0] = controlSystem;
            }

            @Override
            public void attachGm(com.lightningfirefly.game.gm.GameMaster gameMaster) {
                // Not used
            }
        });

        assertThat(capturedSprites[0]).hasSize(1);
        assertThat(capturedControls[0]).isNotNull();

        Sprite playerSprite = capturedSprites[0].get(0);
        assertThat(playerSprite.getX()).isEqualTo(400);
        assertThat(playerSprite.getY()).isEqualTo(300);

        AtomicInteger frameCount = new AtomicInteger(0);

        // Set up update callback
        window.setOnUpdate(() -> {
            frameCount.incrementAndGet();

            // Simulate W key held for first 30 frames
            if (frameCount.get() <= 30) {
                capturedControls[0].onUpdate(key -> key == ControlSystem.KeyCodes.W);
            }
        });

        // Run frames
        window.runFrames(60);

        // Verify sprite moved up
        assertThat(playerSprite.getY()).isLessThan(300);

        window.stop();

        System.out.println("Test game module sprite moved to y=" + playerSprite.getY());
    }

    @Test
    @DisplayName("Sprite responds to mouse click")
    void sprite_respondsToMouseClick() {
        MovableSquareTestModule module = new MovableSquareTestModule();

        final Sprite[] playerSprite = new Sprite[1];
        final ControlSystem[] controls = new ControlSystem[1];

        module.attachScene(new GameScene() {
            @Override
            public void attachSprite(List<Sprite> sprites) {
                playerSprite[0] = sprites.get(0);
            }

            @Override
            public void attachControlSystem(ControlSystem controlSystem) {
                controls[0] = controlSystem;
            }

            @Override
            public void attachGm(com.lightningfirefly.game.gm.GameMaster gameMaster) {
            }
        });

        // Initial position
        float initialX = playerSprite[0].getX();
        float initialY = playerSprite[0].getY();

        // Click to move sprite
        controls[0].onMouseClicked(200, 100, ControlSystem.MouseButton.LEFT);

        // Sprite should move to clicked position (centered)
        assertThat(playerSprite[0].getX()).isNotEqualTo(initialX);
        assertThat(playerSprite[0].getY()).isNotEqualTo(initialY);

        // Should be centered on click position
        float expectedX = 200 - playerSprite[0].getWidth() / 2;
        float expectedY = 100 - playerSprite[0].getHeight() / 2;
        assertThat(playerSprite[0].getX()).isEqualTo(expectedX);
        assertThat(playerSprite[0].getY()).isEqualTo(expectedY);

        System.out.println("Sprite moved to click position: (" + playerSprite[0].getX() + ", " + playerSprite[0].getY() + ")");
    }

    // ========== Helper Methods ==========

    private com.lightningfirefly.engine.rendering.render2d.Sprite convertToRenderingSprite(Sprite gameSprite) {
        return com.lightningfirefly.engine.rendering.render2d.Sprite.builder()
                .id((int) gameSprite.getEntityId())
                .x((int) gameSprite.getX())
                .y((int) gameSprite.getY())
                .sizeX((int) gameSprite.getWidth())
                .sizeY((int) gameSprite.getHeight())
                .texturePath(gameSprite.getTexturePath())
                .build();
    }

    // ========== Test Game Module ==========

    /**
     * Simple test game module for testing.
     */
    static class TestGameModule implements GameModule {
        @Override
        public void attachScene(GameScene scene) {
            // Empty implementation
        }

        @Override
        public List<String> getRequiredModules() {
            return List.of();
        }
    }

    /**
     * Movable square test module with sprite and controls.
     */
    static class MovableSquareTestModule implements GameModule {
        private static final float MOVE_SPEED = 5.0f;
        private static final float SPRITE_SIZE = 64.0f;
        private static final float INITIAL_X = 400.0f;
        private static final float INITIAL_Y = 300.0f;

        private final Sprite playerSprite;
        private final ControlSystem controlSystem;

        public MovableSquareTestModule() {
            this.playerSprite = new Sprite(1);
            this.playerSprite.setPosition(INITIAL_X, INITIAL_Y);
            this.playerSprite.setSize(SPRITE_SIZE, SPRITE_SIZE);
            this.playerSprite.setTexturePath("textures/red-checker.png");
            this.controlSystem = new TestControlSystem(playerSprite, MOVE_SPEED);
        }

        @Override
        public void attachScene(GameScene scene) {
            scene.attachSprite(List.of(playerSprite));
            scene.attachControlSystem(controlSystem);
        }

        @Override
        public List<String> getRequiredModules() {
            return List.of();
        }

        static class TestControlSystem implements ControlSystem {
            private final Sprite sprite;
            private final float moveSpeed;

            TestControlSystem(Sprite sprite, float moveSpeed) {
                this.sprite = sprite;
                this.moveSpeed = moveSpeed;
            }

            @Override
            public void onUpdate(KeyStates keyStates) {
                if (keyStates.isPressed(KeyCodes.W) || keyStates.isPressed(KeyCodes.UP)) {
                    sprite.setY(sprite.getY() - moveSpeed);
                }
                if (keyStates.isPressed(KeyCodes.S) || keyStates.isPressed(KeyCodes.DOWN)) {
                    sprite.setY(sprite.getY() + moveSpeed);
                }
                if (keyStates.isPressed(KeyCodes.A) || keyStates.isPressed(KeyCodes.LEFT)) {
                    sprite.setX(sprite.getX() - moveSpeed);
                }
                if (keyStates.isPressed(KeyCodes.D) || keyStates.isPressed(KeyCodes.RIGHT)) {
                    sprite.setX(sprite.getX() + moveSpeed);
                }

                // Clamp to window bounds
                sprite.setX(Math.max(0, Math.min(800 - sprite.getWidth(), sprite.getX())));
                sprite.setY(Math.max(0, Math.min(600 - sprite.getHeight(), sprite.getY())));
            }

            @Override
            public void onMouseClicked(float x, float y, int button) {
                if (button == MouseButton.LEFT) {
                    float newX = x - sprite.getWidth() / 2;
                    float newY = y - sprite.getHeight() / 2;
                    sprite.setPosition(newX, newY);
                }
            }
        }
    }
}
