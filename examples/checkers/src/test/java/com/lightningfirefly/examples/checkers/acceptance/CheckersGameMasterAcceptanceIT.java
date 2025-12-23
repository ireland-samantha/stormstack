package com.lightningfirefly.examples.checkers.acceptance;

import com.lightningfirefly.examples.checkers.engine.CheckersGameFactory;
import com.lightningfirefly.game.app.GameApplication;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI Acceptance test for the Checkers GameMaster using GameApplication.
 *
 * <p>This test verifies the complete checkers game flow:
 * <ol>
 *   <li>Start a backend server via Docker</li>
 *   <li>Create a GameApplication pointing to the backend</li>
 *   <li>Load the CheckersGameFactory</li>
 *   <li>Click Install to upload the module and gamemaster JARs</li>
 *   <li>Click Play to start the game and render</li>
 * </ol>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker must be running</li>
 *   <li>The backend image must be built: {@code docker build -t lightning-backend .}</li>
 *   <li>The checkers module must be built: {@code ./mvnw package -pl examples/checkers}</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * ./mvnw test -pl examples/checkers -Dtest=CheckersGameMasterAcceptanceIT
 * </pre>
 */
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Checkers GameMaster GUI Acceptance Test")
@Testcontainers
class CheckersGameMasterAcceptanceIT {

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
    private GameApplication gameApplication;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
        System.out.println("Backend URL: " + backendUrl);
    }

    @AfterEach
    void tearDown() {
        if (gameApplication != null) {
            try {
                gameApplication.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            gameApplication = null;
        }
    }

    @Test
    @DisplayName("Load CheckersGameFactory, click Install, click Play, render game with sprites")
    void loadGameFactory_clickInstall_clickPlay_renderGame() throws Exception {
        System.out.println("=== Checkers GameMaster GUI Acceptance Test ===");

        // Step 1: Create GameApplication with backend URL
        System.out.println("\n[Step 1] Creating GameApplication...");
        gameApplication = new GameApplication(backendUrl);
        gameApplication.initialize();
        assertThat(gameApplication.getWindow())
                .as("Window should be created")
                .isNotNull();

        // Run a few frames to verify window works
        gameApplication.getWindow().runFrames(5);
        System.out.println("Window initialized and rendered frames successfully!");

        // Step 2: Load CheckersGameFactory directly (simulating JAR load)
        System.out.println("\n[Step 2] Loading CheckersGameFactory...");
        CheckersGameFactory checkersFactory = new CheckersGameFactory();
        gameApplication.setGameFactory(checkersFactory);
        assertThat(gameApplication.getLoadedGameFactory())
                .as("Factory should be loaded")
                .isEqualTo(checkersFactory);
        System.out.println("Factory loaded successfully!");

        gameApplication.getWindow().runFrames(5);

        // Step 3: Verify factory provides all required resources
        System.out.println("\n[Step 3] Verifying factory resources...");
        assertThat(checkersFactory.getGameMasterJar())
                .as("GameMaster JAR should be bundled")
                .isNotNull()
                .hasSizeGreaterThan(1000);
        System.out.println("  GameMaster JAR: " + checkersFactory.getGameMasterJar().length + " bytes");

        var moduleJars = checkersFactory.getModuleJars();
        assertThat(moduleJars)
                .as("Module JARs should be bundled")
                .containsKey("CheckersModule");
        System.out.println("  Module JARs: " + moduleJars.keySet());
        System.out.println("  CheckersModule JAR: " + moduleJars.get("CheckersModule").length + " bytes");

        // Step 4: Click Install button to upload module and gamemaster JARs
        System.out.println("\n[Step 4] Clicking Install button...");
        gameApplication.clickInstall();
        gameApplication.getWindow().runFrames(10);

        assertThat(gameApplication.isGameInstalled())
                .as("Game should be installed after clicking Install")
                .isTrue();
        System.out.println("Game installed successfully!");

        // Step 5: Start the game (using test method that doesn't close window)
        System.out.println("\n[Step 5] Starting game for testing...");
        gameApplication.startGameForTesting();
        gameApplication.getWindow().runFrames(30);

        assertThat(gameApplication.isGameRunning())
                .as("Game should be running after starting")
                .isTrue();
        System.out.println("Game running! Match ID: " + gameApplication.getActiveMatchId());

        // Step 6: Poll for snapshot and render sprites
        System.out.println("\n[Step 6] Polling for snapshot...");

        // Give the server time to process game start and generate entities
        Thread.sleep(500);

        // Poll multiple times to ensure we get data
        boolean hasSprites = false;
        for (int attempt = 0; attempt < 10 && !hasSprites; attempt++) {
            hasSprites = gameApplication.pollAndRenderSnapshot();
            if (!hasSprites) {
                System.out.println("  Attempt " + (attempt + 1) + ": no sprites yet");
                Thread.sleep(200);
                gameApplication.getWindow().runFrames(10);
            }
        }

        var sprites = gameApplication.getWindow().getSprites();
        System.out.println("  Sprites in window: " + sprites.size());

        assertThat(sprites)
                .as("Window should have checkers pieces rendered as sprites")
                .isNotEmpty();

        // Checkers has 24 pieces (12 per player) at game start
        assertThat(sprites.size())
                .as("Should have checkers pieces rendered")
                .isGreaterThanOrEqualTo(12);

        // Print sprite details
        for (var sprite : sprites) {
            System.out.println("  Sprite id=" + sprite.getId() +
                    " pos=(" + sprite.getX() + ", " + sprite.getY() + ")" +
                    " size=(" + sprite.getSizeX() + ", " + sprite.getSizeY() + ")");
        }

        // Step 7: Verify sprite positions are on the board
        System.out.println("\n[Step 7] Verifying sprite positions...");
        for (var sprite : sprites) {
            assertThat(sprite.getX())
                    .as("Sprite X should be on board")
                    .isGreaterThanOrEqualTo(0);
            assertThat(sprite.getY())
                    .as("Sprite Y should be on board")
                    .isGreaterThanOrEqualTo(0);
        }

        // Step 8: Run more frames to verify stable rendering
        System.out.println("\n[Step 8] Running additional frames...");
        gameApplication.getWindow().runFrames(30);

        // Verify sprites still present after additional frames
        var spritesAfter = gameApplication.getWindow().getSprites();
        assertThat(spritesAfter.size())
                .as("Sprites should persist after additional frames")
                .isEqualTo(sprites.size());

        System.out.println("\n=== Checkers GameMaster GUI Acceptance Test PASSED ===");
    }

    @Test
    @DisplayName("Verify CheckersGameFactory provides required resources")
    void checkersGameFactory_providesRequiredResources() {
        CheckersGameFactory factory = new CheckersGameFactory();

        // Verify required modules
        assertThat(factory.getRequiredModules())
                .as("CheckersGameFactory should require CheckersModule")
                .contains("CheckersModule");

        // Verify game master name
        assertThat(factory.getGameMasterName())
                .as("CheckersGameFactory should specify CheckersGameMaster")
                .isEqualTo("CheckersGameMaster");

        // Verify gamemaster JAR is bundled
        byte[] gameMasterJar = factory.getGameMasterJar();
        assertThat(gameMasterJar)
                .as("GameMaster JAR should be bundled")
                .isNotNull()
                .hasSizeGreaterThan(1000); // Should be a valid JAR

        // Verify module JARs are bundled
        var moduleJars = factory.getModuleJars();
        assertThat(moduleJars)
                .as("CheckersModule JAR should be bundled")
                .containsKey("CheckersModule");

        byte[] checkersModuleJar = moduleJars.get("CheckersModule");
        assertThat(checkersModuleJar)
                .as("CheckersModule JAR should be valid")
                .isNotNull()
                .hasSizeGreaterThan(1000);

        System.out.println("CheckersGameFactory provides:");
        System.out.println("  - Required modules: " + factory.getRequiredModules());
        System.out.println("  - GameMaster name: " + factory.getGameMasterName());
        System.out.println("  - GameMaster JAR size: " + gameMasterJar.length + " bytes");
        System.out.println("  - Module JARs: " + moduleJars.keySet());
        System.out.println("  - CheckersModule JAR size: " + checkersModuleJar.length + " bytes");
    }
}
