package com.lightningfirefly.game.app;

import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.engine.GameModule;
import com.lightningfirefly.game.engine.orchestrator.GameOrchestratorImpl;
import com.lightningfirefly.game.engine.renderer.GameRenderer;
import com.lightningfirefly.game.renderer.GameRendererBuilder;
import com.lightningfirefly.game.renderer.SnapshotSpriteMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * GameApplication allows users to browse for a game module JAR,
 * install it, and play/stop the game using the orchestrator.
 *
 * <p>Usage:
 * <pre>{@code
 * GameApplication app = new GameApplication("http://localhost:8080");
 * app.run();
 * }</pre>
 */
@Slf4j
public class GameApplication {

    private final String serverUrl;
    private final ComponentFactory componentFactory;
    private final Path cacheDirectory;

    private Window window;
    private Panel mainPanel;
    private Label titleLabel;
    private Label statusLabel;
    private Label jarPathLabel;
    private Button browseButton;
    private Button installButton;
    private Button playButton;
    private Button stopButton;

    // State
    private Path selectedJarPath;
    private GameModule loadedGameModule;
    private GameOrchestratorImpl orchestrator;
    private GameRenderer gameRenderer;
    private boolean isGameInstalled;
    private boolean isGameRunning;

    public GameApplication(String serverUrl) {
        this(serverUrl, GLComponentFactory.getInstance());
    }

    public GameApplication(String serverUrl, ComponentFactory componentFactory) {
        this.serverUrl = serverUrl != null ? serverUrl : "http://localhost:8080";
        this.componentFactory = componentFactory;
        this.cacheDirectory = getCacheDirectory();
    }

    private Path getCacheDirectory() {
        try {
            Path cacheDir = Path.of(System.getProperty("user.home"), ".lightning-engine", "cache");
            Files.createDirectories(cacheDir);
            return cacheDir;
        } catch (IOException e) {
            log.warn("Failed to create cache directory, using temp", e);
            try {
                return Files.createTempDirectory("lightning-cache");
            } catch (IOException e2) {
                throw new RuntimeException("Cannot create cache directory", e2);
            }
        }
    }

    /**
     * Initialize and run the application.
     */
    public void run() {
        initialize();
        start();
    }

    /**
     * Initialize the application without starting the event loop.
     */
    public void initialize() {
        if (window != null) {
            throw new IllegalStateException("Already initialized");
        }

        window = WindowBuilder.create()
                .size(600, 200)
                .title("Game Launcher")
                .build();

        setupUI();
        window.setOnUpdate(this::update);

        log.info("GameApplication initialized");
    }

    /**
     * Start the event loop.
     */
    public void start() {
        if (window == null) {
            throw new IllegalStateException("Not initialized");
        }
        window.run();
        cleanup();
    }

    private void setupUI() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        // Main panel
        mainPanel = componentFactory.createPanel(10, 10, window.getWidth() - 20, window.getHeight() - 20);
        mainPanel.setTitle("Game Launcher");

        // Title
        titleLabel = componentFactory.createLabel(20, 50, "Select a Game Module JAR", 16.0f);
        titleLabel.setTextColor(colours.textPrimary());

        // JAR path display
        jarPathLabel = componentFactory.createLabel(20, 80, "No JAR selected", 12.0f);
        jarPathLabel.setTextColor(colours.textSecondary());

        // Status label
        statusLabel = componentFactory.createLabel(20, 170, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Browse button
        browseButton = componentFactory.createButton(20, 110, 100, 32, "Browse...");
        browseButton.setOnClick(this::onBrowseClicked);

        // Install button
        installButton = componentFactory.createButton(130, 110, 100, 32, "Install");
        installButton.setBackgroundColor(colours.buttonBg());
        installButton.setOnClick(this::onInstallClicked);

        // Play button
        playButton = componentFactory.createButton(240, 110, 100, 32, "Play");
        playButton.setBackgroundColor(colours.success());
        playButton.setOnClick(this::onPlayClicked);

        // Stop button
        stopButton = componentFactory.createButton(350, 110, 100, 32, "Stop");
        stopButton.setBackgroundColor(colours.error());
        stopButton.setOnClick(this::onStopClicked);

        // Add to main panel
        mainPanel.addChild((WindowComponent) titleLabel);
        mainPanel.addChild((WindowComponent) jarPathLabel);
        mainPanel.addChild((WindowComponent) statusLabel);
        mainPanel.addChild((WindowComponent) browseButton);
        mainPanel.addChild((WindowComponent) installButton);
        mainPanel.addChild((WindowComponent) playButton);
        mainPanel.addChild((WindowComponent) stopButton);

        window.addComponent(mainPanel);

        updateButtonStates();
    }

    private void onBrowseClicked() {
        Optional<Path> selected = componentFactory.openFileDialog(
                "Select Game Module JAR",
                System.getProperty("user.home"),
                "*.jar",
                "JAR Files"
        );

        if (selected.isPresent()) {
            selectedJarPath = selected.get();
            jarPathLabel.setText(selectedJarPath.getFileName().toString());

            // Try to load the game module from the JAR
            try {
                loadedGameModule = loadGameModuleFromJar(selectedJarPath);
                setStatus("Loaded: " + loadedGameModule.getClass().getSimpleName());
                isGameInstalled = false;
                isGameRunning = false;
            } catch (Exception e) {
                log.error("Failed to load game module from JAR", e);
                setStatus("Error: " + e.getMessage());
                loadedGameModule = null;
            }
        }

        updateButtonStates();
    }

    private void onInstallClicked() {
        if (loadedGameModule == null) {
            setStatus("No game module loaded");
            return;
        }

        try {
            createOrchestrator();
            orchestrator.installGame(loadedGameModule);
            isGameInstalled = true;
            setStatus("Game installed successfully");
            log.info("Game installed: {}", loadedGameModule.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to install game", e);
            setStatus("Install failed: " + e.getMessage());
        }

        updateButtonStates();
    }

    private void onPlayClicked() {
        if (loadedGameModule == null) {
            setStatus("No game module loaded");
            return;
        }

        try {
            // Auto-install if not installed
            if (!isGameInstalled) {
                createOrchestrator();
                orchestrator.installGame(loadedGameModule);
                isGameInstalled = true;
            }

            orchestrator.startGame(loadedGameModule);
            isGameRunning = true;
            setStatus("Game running");
            log.info("Game started: {}", loadedGameModule.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to start game", e);
            setStatus("Start failed: " + e.getMessage());
        }

        updateButtonStates();
    }

    private void onStopClicked() {
        if (loadedGameModule == null || !isGameRunning) {
            return;
        }

        try {
            orchestrator.stopGame(loadedGameModule);
            isGameRunning = false;
            setStatus("Game stopped");
            log.info("Game stopped: {}", loadedGameModule.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to stop game", e);
            setStatus("Stop failed: " + e.getMessage());
        }

        updateButtonStates();
    }

    private void createOrchestrator() {
        if (orchestrator != null) {
            return;
        }

        // Create adapters
        MatchAdapter matchAdapter = new MatchAdapter.HttpMatchAdapter(serverUrl);
        ModuleAdapter moduleAdapter = new ModuleAdapter.HttpModuleAdapter(serverUrl);
        ResourceAdapter resourceAdapter = new ResourceAdapter.HttpResourceAdapter(serverUrl);
        GameMasterAdapter gameMasterAdapter = new GameMasterAdapter.HttpGameMasterAdapter(serverUrl);

        // Create game renderer
        gameRenderer = GameRendererBuilder.create()
                .windowSize(800, 600)
                .title("Game")
                .spriteMapper(new SnapshotSpriteMapper()
                        .defaultSize(64, 64)
                        .textureResolver(resourceId -> null))
                .build();

        // Create snapshot subscriber (simple polling implementation)
        GameOrchestratorImpl.SnapshotSubscriber snapshotSubscriber = (matchId, callback) -> {
            // For now, return empty unsubscribe - actual implementation would poll server
            return () -> {};
        };

        // Create orchestrator
        orchestrator = new GameOrchestratorImpl(
                matchAdapter,
                moduleAdapter,
                resourceAdapter,
                gameMasterAdapter,
                snapshotSubscriber,
                new SnapshotObserver(),
                gameRenderer,
                cacheDirectory
        );
    }

    private void updateButtonStates() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        boolean hasModule = loadedGameModule != null;

        // Install button: enabled when module loaded and not installed
        if (hasModule && !isGameInstalled) {
            installButton.setBackgroundColor(colours.accent());
        } else {
            installButton.setBackgroundColor(colours.buttonBg());
        }

        // Play button: enabled when module loaded and not running
        if (hasModule && !isGameRunning) {
            playButton.setBackgroundColor(colours.success());
        } else {
            playButton.setBackgroundColor(colours.buttonBg());
        }

        // Stop button: enabled when game is running
        if (isGameRunning) {
            stopButton.setBackgroundColor(colours.error());
        } else {
            stopButton.setBackgroundColor(colours.buttonBg());
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        log.debug("Status: {}", message);
    }

    private void update() {
        // Frame update callback - can be used for animations or state refresh
    }

    private void cleanup() {
        if (orchestrator != null) {
            if (isGameRunning && loadedGameModule != null) {
                try {
                    orchestrator.stopGame(loadedGameModule);
                } catch (Exception e) {
                    log.warn("Error stopping game during cleanup", e);
                }
            }
            orchestrator.shutdown();
        }
        if (gameRenderer != null) {
            gameRenderer.dispose();
        }
    }

    /**
     * Load a GameModule from a JAR file.
     */
    private GameModule loadGameModuleFromJar(Path jarPath) throws Exception {
        // Read manifest to get the Game-Module-Class
        String moduleClassName = null;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                moduleClassName = manifest.getMainAttributes().getValue("Game-Module-Class");
            }
        }

        // Create class loader for the JAR
        URL jarUrl = jarPath.toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                GameApplication.class.getClassLoader()
        );

        // Try manifest-specified class first
        if (moduleClassName != null) {
            Class<?> clazz = classLoader.loadClass(moduleClassName);
            if (GameModule.class.isAssignableFrom(clazz)) {
                return (GameModule) clazz.getDeclaredConstructor().newInstance();
            }
        }

        // Try ServiceLoader as fallback
        ServiceLoader<GameModule> loader = ServiceLoader.load(GameModule.class, classLoader);
        for (GameModule module : loader) {
            return module;
        }

        throw new IllegalArgumentException("No GameModule found in JAR: " + jarPath);
    }

    /**
     * Get the window (for testing).
     */
    public Window getWindow() {
        return window;
    }

    /**
     * Get the loaded game module (for testing).
     */
    public GameModule getLoadedGameModule() {
        return loadedGameModule;
    }

    /**
     * Check if game is installed (for testing).
     */
    public boolean isGameInstalled() {
        return isGameInstalled;
    }

    /**
     * Check if game is running (for testing).
     */
    public boolean isGameRunning() {
        return isGameRunning;
    }

    /**
     * Set a game module directly (for testing without JAR loading).
     */
    public void setGameModule(GameModule module) {
        this.loadedGameModule = module;
        if (jarPathLabel != null) {
            jarPathLabel.setText(module.getClass().getSimpleName());
        }
        updateButtonStates();
    }

    /**
     * Click the install button programmatically (for testing).
     */
    public void clickInstall() {
        onInstallClicked();
    }

    /**
     * Click the play button programmatically (for testing).
     */
    public void clickPlay() {
        onPlayClicked();
    }

    /**
     * Click the stop button programmatically (for testing).
     */
    public void clickStop() {
        onStopClicked();
    }

    /**
     * Get the orchestrator (for testing).
     */
    public GameOrchestratorImpl getOrchestrator() {
        return orchestrator;
    }

    /**
     * Stop the application.
     */
    public void stop() {
        if (window != null) {
            window.stop();
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        String serverUrl = "http://localhost:8080";

        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("-s") || args[i].equals("--server")) && i + 1 < args.length) {
                serverUrl = args[++i];
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                System.out.println("""
                    Game Application Launcher

                    Usage: java -jar game-application.jar [options]

                    Options:
                      -s, --server <url>   Server URL (default: http://localhost:8080)
                      -h, --help           Show this help
                    """);
                return;
            }
        }

        GameApplication app = new GameApplication(serverUrl);
        log.info("Starting Game Application");
        log.info("Server URL: {}", serverUrl);
        app.run();
    }
}
