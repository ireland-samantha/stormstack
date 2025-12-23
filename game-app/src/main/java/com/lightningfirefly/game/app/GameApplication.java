package com.lightningfirefly.game.app;

import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.api.resource.adapter.SimulationAdapter;
import com.lightningfirefly.engine.api.resource.adapter.SnapshotAdapter;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.engine.GameFactory;
import com.lightningfirefly.game.engine.orchestrator.GameOrchestratorImpl;
import com.lightningfirefly.game.engine.renderer.GameRenderer;
import com.lightningfirefly.game.renderer.GameRendererBuilder;
import com.lightningfirefly.game.renderer.SnapshotSpriteMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    private GameFactory loadedGameFactory;
    private GameOrchestratorImpl orchestrator;
    private GameRenderer gameRenderer;
    private boolean isGameInstalled;
    private boolean isGameRunning;

    // Snapshot polling
    private SnapshotAdapter snapshotAdapter;
    private ScheduledExecutorService snapshotPoller;
    private long activeMatchId = -1;
    private final Map<Long, Sprite> windowSpriteMap = new HashMap<>();

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
                .size(800, 800)
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

        // If game was started, run the game renderer on the main thread
        if (isGameRunning && gameRenderer != null) {
            log.info("Running game renderer on main thread");
            gameRenderer.start(() -> {
                // Frame update callback - rendering driven by snapshot subscription
            });

            // Game window closed - cleanup and potentially restart launcher
            isGameRunning = false;
            log.info("Game window closed");
        }

        cleanup();
    }

    private void setupUI() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        // Control panel at bottom of window
        int controlPanelHeight = 100;
        int controlPanelY = window.getHeight() - controlPanelHeight - 10;
        mainPanel = componentFactory.createPanel(10, controlPanelY, window.getWidth() - 20, controlPanelHeight);
        mainPanel.setTitle("Game Controls");

        // Positions relative to control panel
        int labelY = controlPanelY + 35;
        int buttonY = controlPanelY + 55;

        // Title / JAR path display
        jarPathLabel = componentFactory.createLabel(20, labelY, "No game loaded", 12.0f);
        jarPathLabel.setTextColor(colours.textPrimary());

        // Status label
        statusLabel = componentFactory.createLabel(400, labelY, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Browse button
        browseButton = componentFactory.createButton(20, buttonY, 100, 32, "Browse...");
        browseButton.setOnClick(this::onBrowseClicked);

        // Install button
        installButton = componentFactory.createButton(130, buttonY, 100, 32, "Install");
        installButton.setBackgroundColor(colours.buttonBg());
        installButton.setOnClick(this::onInstallClicked);

        // Play button
        playButton = componentFactory.createButton(240, buttonY, 100, 32, "Play");
        playButton.setBackgroundColor(colours.success());
        playButton.setOnClick(this::onPlayClicked);

        // Stop button
        stopButton = componentFactory.createButton(350, buttonY, 100, 32, "Stop");
        stopButton.setBackgroundColor(colours.error());
        stopButton.setOnClick(this::onStopClicked);

        // Add to main panel
        mainPanel.addChild((WindowComponent) jarPathLabel);
        mainPanel.addChild((WindowComponent) statusLabel);
        mainPanel.addChild((WindowComponent) browseButton);
        mainPanel.addChild((WindowComponent) installButton);
        mainPanel.addChild((WindowComponent) playButton);
        mainPanel.addChild((WindowComponent) stopButton);

        window.addComponent(mainPanel);

        // Remove titleLabel reference since we removed it
        titleLabel = jarPathLabel;

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
                loadedGameFactory = loadGameFactoryFromJar(selectedJarPath);
                setStatus("Loaded: " + loadedGameFactory.getClass().getSimpleName());
                isGameInstalled = false;
                isGameRunning = false;
            } catch (Exception e) {
                log.error("Failed to load game module from JAR", e);
                setStatus("Error: " + e.getMessage());
                loadedGameFactory = null;
            }
        }

        updateButtonStates();
    }

    private void onInstallClicked() {
        if (loadedGameFactory == null) {
            setStatus("No game module loaded");
            return;
        }

        try {
            createOrchestrator();
            orchestrator.installGame(loadedGameFactory);
            isGameInstalled = true;
            setStatus("Game installed successfully");
            log.info("Game installed: {}", loadedGameFactory.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to install game", e);
            setStatus("Install failed: " + e.getMessage());
        }

        updateButtonStates();
    }

    private void onPlayClicked() {
        if (loadedGameFactory == null) {
            setStatus("No game module loaded");
            return;
        }

        try {
            // Auto-install if not installed
            if (!isGameInstalled) {
                createOrchestrator();
                orchestrator.installGame(loadedGameFactory);
                isGameInstalled = true;
            }

            isGameRunning = true;
            setStatus("Starting game...");
            log.info("Game starting: {}", loadedGameFactory.getClass().getSimpleName());

            // Start the game (creates match, subscribes to snapshots)
            // Note: The renderer will be started after the launcher window closes
            orchestrator.startGame(loadedGameFactory);

            // Close the launcher window - game renderer will run on main thread after
            // This is required on macOS where GLFW needs the main thread
            window.stop();

        } catch (Exception e) {
            log.error("Failed to start game", e);
            setStatus("Start failed: " + e.getMessage());
            isGameRunning = false;
        }

        updateButtonStates();
    }

    private void onStopClicked() {
        if (loadedGameFactory == null || !isGameRunning) {
            return;
        }

        try {
            orchestrator.stopGame(loadedGameFactory);
            isGameRunning = false;
            setStatus("Game stopped");
            log.info("Game stopped: {}", loadedGameFactory.getClass().getSimpleName());
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
        SimulationAdapter simulationAdapter = new SimulationAdapter.HttpSimulationAdapter(serverUrl);
        snapshotAdapter = new SnapshotAdapter.HttpSnapshotAdapter(serverUrl);

        // Create game renderer
        gameRenderer = GameRendererBuilder.create()
                .windowSize(800, 600)
                .title("Game")
                .spriteMapper(new SnapshotSpriteMapper()
                        .defaultSize(64, 64)
                        .textureResolver(resourceId -> null))
                .build();

        // Create snapshot subscriber that polls the server
        GameOrchestratorImpl.SnapshotSubscriber snapshotSubscriber = (matchId, callback) -> {
            activeMatchId = matchId;

            // Create a polling thread
            snapshotPoller = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SnapshotPoller");
                t.setDaemon(true);
                return t;
            });

            snapshotPoller.scheduleAtFixedRate(() -> {
                try {
                    pollAndProcessSnapshot(matchId, callback);
                } catch (Exception e) {
                    log.warn("Error polling snapshot: {}", e.getMessage());
                }
            }, 0, 100, TimeUnit.MILLISECONDS);

            // Return unsubscribe function
            return () -> {
                if (snapshotPoller != null) {
                    snapshotPoller.shutdownNow();
                    snapshotPoller = null;
                }
                activeMatchId = -1;
            };
        };

        // Create orchestrator
        orchestrator = new GameOrchestratorImpl(
                matchAdapter,
                moduleAdapter,
                resourceAdapter,
                gameMasterAdapter,
                simulationAdapter,
                snapshotSubscriber,
                new SnapshotObserver(),
                gameRenderer,
                cacheDirectory
        );
    }

    /**
     * Poll the server for a snapshot and process it.
     */
    private void pollAndProcessSnapshot(long matchId, Consumer<Map<String, Map<String, List<Float>>>> callback) {
        try {
            Optional<SnapshotAdapter.SnapshotResponse> response = snapshotAdapter.getMatchSnapshot(matchId);
            if (response.isPresent()) {
                Map<String, Map<String, List<Float>>> snapshotData = parseSnapshotData(response.get().snapshotData());
                if (snapshotData != null && !snapshotData.isEmpty()) {
                    callback.accept(snapshotData);
                    // Also render sprites to the launcher window for testing/debugging
                    renderSpritesToWindow(snapshotData);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to poll snapshot: {}", e.getMessage());
        }
    }

    /**
     * Parse snapshot JSON data into the expected format.
     */
    private Map<String, Map<String, List<Float>>> parseSnapshotData(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }

        Map<String, Map<String, List<Float>>> result = new HashMap<>();

        try {
            // Simple JSON parsing - look for module entries
            // Format: {"ModuleName":{"COMPONENT":[val1,val2,...],...},...}
            int pos = 0;
            while (pos < snapshotJson.length()) {
                // Find module name
                int keyStart = snapshotJson.indexOf('"', pos);
                if (keyStart == -1) break;
                int keyEnd = snapshotJson.indexOf('"', keyStart + 1);
                if (keyEnd == -1) break;
                String moduleName = snapshotJson.substring(keyStart + 1, keyEnd);

                // Skip to module data (after the colon and opening brace)
                int colonPos = snapshotJson.indexOf(':', keyEnd);
                if (colonPos == -1) break;
                int braceStart = snapshotJson.indexOf('{', colonPos);
                if (braceStart == -1) break;

                // Find matching closing brace for module data
                int braceCount = 1;
                int braceEnd = braceStart + 1;
                while (braceEnd < snapshotJson.length() && braceCount > 0) {
                    char ch = snapshotJson.charAt(braceEnd);
                    if (ch == '{') braceCount++;
                    else if (ch == '}') braceCount--;
                    braceEnd++;
                }

                String moduleJson = snapshotJson.substring(braceStart, braceEnd);
                Map<String, List<Float>> moduleData = parseModuleData(moduleJson);
                if (!moduleData.isEmpty()) {
                    result.put(moduleName, moduleData);
                }

                pos = braceEnd;
            }
        } catch (Exception e) {
            log.warn("Failed to parse snapshot JSON: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Parse a module's component data.
     */
    private Map<String, List<Float>> parseModuleData(String moduleJson) {
        Map<String, List<Float>> result = new HashMap<>();

        int pos = 0;
        while (pos < moduleJson.length()) {
            // Find component name
            int keyStart = moduleJson.indexOf('"', pos);
            if (keyStart == -1) break;
            int keyEnd = moduleJson.indexOf('"', keyStart + 1);
            if (keyEnd == -1) break;
            String componentName = moduleJson.substring(keyStart + 1, keyEnd);

            // Find the array
            int arrayStart = moduleJson.indexOf('[', keyEnd);
            if (arrayStart == -1) break;
            int arrayEnd = moduleJson.indexOf(']', arrayStart);
            if (arrayEnd == -1) break;

            String arrayContent = moduleJson.substring(arrayStart + 1, arrayEnd);
            List<Float> values = parseFloatArray(arrayContent);
            result.put(componentName, values);

            pos = arrayEnd + 1;
        }

        return result;
    }

    /**
     * Parse a comma-separated list of numbers into a Float list.
     */
    private List<Float> parseFloatArray(String content) {
        List<Float> result = new ArrayList<>();
        if (content.isBlank()) {
            return result;
        }

        String[] parts = content.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(Float.parseFloat(trimmed));
                } catch (NumberFormatException e) {
                    result.add(0.0f);
                }
            }
        }
        return result;
    }

    // Game board rendering constants - board is at top of window
    private static final int BOARD_OFFSET_X = 50;
    private static final int BOARD_OFFSET_Y = 50;
    private static final int CELL_SIZE = 75;
    private static final int PIECE_SIZE = 65;
    private static final int BOARD_SIZE = 8; // 8x8 checkers board

    /**
     * Render sprites from snapshot data to the launcher window.
     * This is useful for testing and debugging.
     */
    private void renderSpritesToWindow(Map<String, Map<String, List<Float>>> snapshotData) {
        if (window == null || snapshotData == null) {
            return;
        }

        // First pass: collect all entity data including resource IDs
        Map<Long, EntityRenderData> entityData = new HashMap<>();

        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : snapshotData.entrySet()) {
            Map<String, List<Float>> moduleData = moduleEntry.getValue();

            List<Float> entityIds = moduleData.get("ENTITY_ID");
            List<Float> posX = moduleData.get("POSITION_X");
            List<Float> posY = moduleData.get("POSITION_Y");
            List<Float> resourceIds = moduleData.get("RESOURCE_ID");

            if (entityIds == null) continue;

            for (int i = 0; i < entityIds.size(); i++) {
                long entityId = entityIds.get(i).longValue();
                EntityRenderData data = entityData.computeIfAbsent(entityId, EntityRenderData::new);

                if (posX != null && i < posX.size()) {
                    data.posX = posX.get(i);
                }
                if (posY != null && i < posY.size()) {
                    data.posY = posY.get(i);
                }
                if (resourceIds != null && i < resourceIds.size()) {
                    Float resId = resourceIds.get(i);
                    if (resId != null && !Float.isNaN(resId) && resId > 0) {
                        data.resourceId = resId.longValue();
                    }
                }
            }
        }

        // Second pass: create/update sprites
        for (EntityRenderData data : entityData.values()) {
            if (data.posX == null || data.posY == null) continue;

            float rawX = data.posX;
            float rawY = data.posY;

            // Convert to screen coordinates
            int screenX, screenY;
            if (rawX <= BOARD_SIZE && rawY <= BOARD_SIZE) {
                screenX = BOARD_OFFSET_X + (int)(rawX * CELL_SIZE) + (CELL_SIZE - PIECE_SIZE) / 2;
                screenY = BOARD_OFFSET_Y + (int)(rawY * CELL_SIZE) + (CELL_SIZE - PIECE_SIZE) / 2;
            } else {
                float scaleX = (float)(BOARD_SIZE * CELL_SIZE) / 640.0f;
                float scaleY = (float)(BOARD_SIZE * CELL_SIZE) / 640.0f;
                screenX = BOARD_OFFSET_X + (int)(rawX * scaleX);
                screenY = BOARD_OFFSET_Y + (int)(rawY * scaleY);
                screenX = Math.max(BOARD_OFFSET_X, Math.min(screenX, BOARD_OFFSET_X + BOARD_SIZE * CELL_SIZE - PIECE_SIZE));
                screenY = Math.max(BOARD_OFFSET_Y, Math.min(screenY, BOARD_OFFSET_Y + BOARD_SIZE * CELL_SIZE - PIECE_SIZE));
            }

            // Get texture path from orchestrator's cache
            String texturePath = getTexturePath(data.resourceId);

            Sprite existingSprite = windowSpriteMap.get(data.entityId);
            if (existingSprite == null) {
                Sprite sprite = Sprite.builder()
                        .id((int) data.entityId)
                        .x(screenX)
                        .y(screenY)
                        .sizeX(PIECE_SIZE)
                        .sizeY(PIECE_SIZE)
                        .texturePath(texturePath)
                        .build();
                windowSpriteMap.put(data.entityId, sprite);
                window.addSprite(sprite);
                System.out.println("[Render] Sprite " + data.entityId + " at (" + screenX + "," + screenY +
                        ") resource=" + data.resourceId + " texture=" + texturePath);
            } else {
                existingSprite.setX(screenX);
                existingSprite.setY(screenY);
                if (texturePath != null && !texturePath.equals(existingSprite.getTexturePath())) {
                    existingSprite.setTexturePath(texturePath);
                }
            }
        }
    }

    /**
     * Get texture path for a resource ID using the orchestrator's cache.
     * Falls back to checking disk if not tracked in memory.
     */
    private String getTexturePath(Long resourceId) {
        if (resourceId == null) return null;

        // First try the orchestrator's tracked cache
        if (orchestrator != null) {
            java.nio.file.Path cachedPath = orchestrator.getCachedResourcePath(resourceId);
            if (cachedPath != null) {
                return cachedPath.toString();
            }
        }

        // Fall back to checking disk - the file might have been downloaded but not yet tracked
        // or might exist from a previous session
        java.nio.file.Path fallbackPath = cacheDirectory.resolve("Resource-" + resourceId);
        if (java.nio.file.Files.exists(fallbackPath)) {
            return fallbackPath.toString();
        }

        return null;
    }

    private static class EntityRenderData {
        final long entityId;
        Float posX;
        Float posY;
        Long resourceId;

        EntityRenderData(long entityId) {
            this.entityId = entityId;
        }
    }

    private void updateButtonStates() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        boolean hasModule = loadedGameFactory != null;

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
            if (isGameRunning && loadedGameFactory != null) {
                try {
                    orchestrator.stopGame(loadedGameFactory);
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
     * Load a GameFactory from a JAR file.
     */
    private GameFactory loadGameFactoryFromJar(Path jarPath) throws Exception {
        return new GameFactoryJarLoader().loadFromJar(jarPath);
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
    public GameFactory getLoadedGameFactory() {
        return loadedGameFactory;
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
    public void setGameFactory(GameFactory module) {
        this.loadedGameFactory = module;
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
     * Start the game without stopping the launcher window.
     * This is useful for testing where we want to keep rendering frames.
     */
    public void startGameForTesting() {
        if (loadedGameFactory == null) {
            throw new IllegalStateException("No game module loaded");
        }

        try {
            // Auto-install if not installed
            if (!isGameInstalled) {
                createOrchestrator();
                orchestrator.installGame(loadedGameFactory);
                isGameInstalled = true;
            }

            isGameRunning = true;
            setStatus("Starting game...");
            log.info("Game starting (test mode): {}", loadedGameFactory.getClass().getSimpleName());

            // Start the game (creates match, subscribes to snapshots)
            orchestrator.startGame(loadedGameFactory);

            // Don't close the window - keep it open for testing

        } catch (Exception e) {
            log.error("Failed to start game", e);
            setStatus("Start failed: " + e.getMessage());
            isGameRunning = false;
            throw new RuntimeException("Failed to start game", e);
        }

        updateButtonStates();
    }

    /**
     * Manually poll the server for a snapshot and render sprites.
     * This is useful for testing to ensure sprites are rendered synchronously.
     *
     * @return true if sprites were rendered
     */
    public boolean pollAndRenderSnapshot() {
        if (!isGameRunning || activeMatchId <= 0) {
            System.out.println("[pollAndRenderSnapshot] Cannot poll: game not running or no match ID");
            return false;
        }

        try {
            Optional<SnapshotAdapter.SnapshotResponse> response = snapshotAdapter.getMatchSnapshot(activeMatchId);
            if (response.isPresent()) {
                String snapshotData = response.get().snapshotData();
                System.out.println("[pollAndRenderSnapshot] Received snapshot data length: " + (snapshotData != null ? snapshotData.length() : 0));
                System.out.println("[pollAndRenderSnapshot] Snapshot data preview: " + (snapshotData != null ? snapshotData.substring(0, Math.min(200, snapshotData.length())) : "null"));

                Map<String, Map<String, List<Float>>> parsed = parseSnapshotData(snapshotData);
                if (parsed != null && !parsed.isEmpty()) {
                    System.out.println("[pollAndRenderSnapshot] Parsed snapshot with " + parsed.size() + " modules: " + parsed.keySet());
                    renderSpritesToWindow(parsed);
                    System.out.println("[pollAndRenderSnapshot] Sprites in map: " + windowSpriteMap.size());
                    return !windowSpriteMap.isEmpty();
                } else {
                    System.out.println("[pollAndRenderSnapshot] Parsed snapshot is empty or null");
                }
            } else {
                System.out.println("[pollAndRenderSnapshot] No snapshot response from server");
            }
        } catch (IOException e) {
            System.out.println("[pollAndRenderSnapshot] Failed to poll: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get the number of sprites currently in the launcher window.
     */
    public int getSpriteCount() {
        return windowSpriteMap.size();
    }

    /**
     * Get all rendered sprites (for testing).
     */
    public java.util.Collection<Sprite> getRenderedSprites() {
        return java.util.Collections.unmodifiableCollection(windowSpriteMap.values());
    }

    /**
     * Get the active match ID (for debugging).
     */
    public long getActiveMatchId() {
        return activeMatchId;
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
