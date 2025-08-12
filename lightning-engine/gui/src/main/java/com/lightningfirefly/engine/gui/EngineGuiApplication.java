package com.lightningfirefly.engine.gui;

import com.lightningfirefly.engine.gui.panel.*;
import com.lightningfirefly.engine.gui.service.SimulationService;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Main GUI application for the Lightning Engine.
 * Provides snapshot viewing, resource management, server modules, and match management.
 *
 * <p>This class uses only GUI abstractions and ComponentFactory for dependency injection,
 * keeping it decoupled from OpenGL or any specific rendering implementation.
 */
@Slf4j
public class EngineGuiApplication {

    private final GuiConfig config;
    private final String serverUrl;
    private final ComponentFactory componentFactory;
    private final SimulationService simulationService;

    private Window window;
    private Panel navPanel;
    private Label titleLabel;
    private Label serverLabel;

    // Navigation buttons
    private Button snapshotNavButton;
    private Button resourceNavButton;
    private Button serverNavButton;
    private Button matchNavButton;
    private Button commandNavButton;
    private Button renderingNavButton;

    // Tick controls (top right)
    private Button advanceTickButton;
    private Button playButton;
    private Button stopButton;
    private Label tickLabel;
    private volatile long currentTick = 0;
    private volatile boolean isPlaying = false;
    private volatile boolean tickRefreshPending = false;
    private long lastTickRefreshTime = 0;
    private static final long TICK_REFRESH_INTERVAL_MS = 50; // Refresh tick every 50ms during play

    // Content panels
    private SnapshotPanel snapshotPanel;
    private ResourcePanel resourcePanel;
    private ServerPanel serverPanel;
    private MatchPanel matchPanel;
    private CommandPanel commandPanel;
    private RenderingPanel renderingPanel;

    // Currently active panel
    private String activePanel = "matches";
    private long currentMatchId = 1;

    public EngineGuiApplication(String serverUrl) {
        this(serverUrl, GLComponentFactory.getInstance());
    }

    public EngineGuiApplication(String serverUrl, ComponentFactory componentFactory) {
        this.config = GuiConfig.load(serverUrl, 1);
        this.serverUrl = config.getServerUrl();
        this.componentFactory = componentFactory;
        this.simulationService = new SimulationService(this.serverUrl);
    }

    /**
     * Create with explicit config (for testing).
     */
    public EngineGuiApplication(GuiConfig config, ComponentFactory componentFactory) {
        this.config = config;
        this.serverUrl = config.getServerUrl();
        this.componentFactory = componentFactory;
        this.simulationService = new SimulationService(this.serverUrl);
    }

    /**
     * Initialize and run the application.
     * This is a blocking call that returns when the window is closed.
     */
    public void run() {
        initialize();
        start();
    }

    /**
     * Initialize the application without starting the event loop.
     * This creates the window and sets up the UI.
     * Call {@link #start()} to begin the event loop.
     *
     * <p>This method is useful for testing where you need access to the window
     * before starting the event loop.
     */
    public void initialize() {
        if (window != null) {
            throw new IllegalStateException("Application already initialized");
        }

        window = WindowBuilder.create()
            .size(config.getWindowWidth(), config.getWindowHeight())
            .title(config.getWindowTitle())
            .build();

        setupUI();
        window.setOnUpdate(this::update);

        log.debug("Application initialized");
    }

    /**
     * Start the event loop. Must call {@link #initialize()} first.
     * This is a blocking call that returns when the window is closed.
     */
    public void start() {
        if (window == null) {
            throw new IllegalStateException("Application not initialized. Call initialize() first.");
        }

        log.debug("Starting event loop");
        window.run();
        cleanup();
    }

    /**
     * Check if the application has been initialized.
     * @return true if initialized
     */
    public boolean isInitialized() {
        return window != null;
    }

    /**
     * Get the window. Only available after {@link #initialize()} is called.
     * @return the window, or null if not initialized
     */
    public Window getWindow() {
        return window;
    }

    private void setupUI() {
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        ComponentFactory.Colours colours = componentFactory.getColours();

        // Create title label
        titleLabel = componentFactory.createLabel(20, 15, "Lightning Engine GUI", 20.0f);
        titleLabel.setTextColor(colours.textPrimary());

        // Create server info label
        serverLabel = componentFactory.createLabel(20, 40, "Server: " + serverUrl, 12.0f);
        serverLabel.setTextColor(colours.textSecondary());

        // Create nav panel at the top
        int navY = 60;
        int navHeight = 40;
        navPanel = componentFactory.createPanel(10, navY, windowWidth - 20, navHeight);

        // Create navigation buttons
        int buttonWidth = 100;
        int buttonSpacing = 10;
        int buttonY = navY + 6;

        matchNavButton = componentFactory.createButton(20, buttonY, buttonWidth, 28, "Matches");
        matchNavButton.setOnClick(() -> switchPanel("matches"));

        snapshotNavButton = componentFactory.createButton(20 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28, "Snapshot");
        snapshotNavButton.setOnClick(() -> switchPanel("snapshot"));

        resourceNavButton = componentFactory.createButton(20 + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 28, "Resources");
        resourceNavButton.setOnClick(() -> switchPanel("resources"));

        serverNavButton = componentFactory.createButton(20 + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, 28, "Modules");
        serverNavButton.setOnClick(() -> switchPanel("server"));

        commandNavButton = componentFactory.createButton(20 + (buttonWidth + buttonSpacing) * 4, buttonY, buttonWidth, 28, "Commands");
        commandNavButton.setOnClick(() -> switchPanel("commands"));

        renderingNavButton = componentFactory.createButton(20 + (buttonWidth + buttonSpacing) * 5, buttonY, buttonWidth, 28, "Rendering");
        renderingNavButton.setOnClick(() -> switchPanel("rendering"));

        navPanel.addChild((WindowComponent) matchNavButton);
        navPanel.addChild((WindowComponent) snapshotNavButton);
        navPanel.addChild((WindowComponent) resourceNavButton);
        navPanel.addChild((WindowComponent) serverNavButton);
        navPanel.addChild((WindowComponent) commandNavButton);
        navPanel.addChild((WindowComponent) renderingNavButton);

        // Create tick controls on the right side of the nav bar
        int tickButtonWidth = 80;
        int tickLabelWidth = 80;
        int playButtonWidth = 60;
        int stopButtonWidth = 60;
        int tickControlsX = windowWidth - tickButtonWidth - playButtonWidth - stopButtonWidth - tickLabelWidth - 60;

        tickLabel = componentFactory.createLabel(tickControlsX, buttonY + 6, "Tick: 0", 14.0f);
        tickLabel.setTextColor(colours.textPrimary());

        advanceTickButton = componentFactory.createButton(
            tickControlsX + tickLabelWidth + 10,
            buttonY,
            tickButtonWidth,
            28,
            "Advance"
        );
        advanceTickButton.setBackgroundColor(colours.success());
        advanceTickButton.setOnClick(this::advanceTick);

        playButton = componentFactory.createButton(
            tickControlsX + tickLabelWidth + tickButtonWidth + 20,
            buttonY,
            playButtonWidth,
            28,
            "Play"
        );
        playButton.setBackgroundColor(colours.accent());
        playButton.setOnClick(this::startPlay);

        stopButton = componentFactory.createButton(
            tickControlsX + tickLabelWidth + tickButtonWidth + playButtonWidth + 30,
            buttonY,
            stopButtonWidth,
            28,
            "Stop"
        );
        stopButton.setBackgroundColor(colours.buttonBg());
        stopButton.setOnClick(this::stopPlay);

        navPanel.addChild((WindowComponent) tickLabel);
        navPanel.addChild((WindowComponent) advanceTickButton);
        navPanel.addChild((WindowComponent) playButton);
        navPanel.addChild((WindowComponent) stopButton);

        // Fetch initial tick value
        refreshCurrentTick();

        // Create content panels
        int panelMargin = 10;
        int panelY = navY + navHeight + panelMargin;
        int panelHeight = windowHeight - panelY - panelMargin;
        int panelWidth = windowWidth - 2 * panelMargin;

        // Match panel
        matchPanel = new MatchPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl
        );
        matchPanel.setOnViewSnapshot(this::viewMatchSnapshot);

        // Snapshot panel (initially hidden)
        snapshotPanel = new SnapshotPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl,
            currentMatchId
        );
        snapshotPanel.setVisible(false);

        // Resource panel (initially hidden)
        resourcePanel = new ResourcePanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl
        );
        resourcePanel.setVisible(false);

        // Server panel (initially hidden)
        serverPanel = new ServerPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl
        );
        serverPanel.setVisible(false);

        // Command panel (initially hidden)
        commandPanel = new CommandPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl
        );
        commandPanel.setVisible(false);

        // Rendering panel (initially hidden)
        renderingPanel = new RenderingPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl
        );
        renderingPanel.setVisible(false);

        // Add components to window
        window.addComponent((WindowComponent) titleLabel);
        window.addComponent((WindowComponent) serverLabel);
        window.addComponent(navPanel);
        window.addComponent(matchPanel);
        window.addComponent(snapshotPanel);
        window.addComponent(resourcePanel);
        window.addComponent(serverPanel);
        window.addComponent(commandPanel);
        window.addComponent(renderingPanel);

        // Set initial active panel
        updateNavButtonStyles();
    }

    /**
     * Switch to a different panel.
     */
    private void switchPanel(String panelName) {
        activePanel = panelName;

        // Hide all content panels
        matchPanel.setVisible(false);
        snapshotPanel.setVisible(false);
        resourcePanel.setVisible(false);
        serverPanel.setVisible(false);
        commandPanel.setVisible(false);
        renderingPanel.setVisible(false);

        // Show the selected panel
        switch (panelName) {
            case "matches" -> matchPanel.setVisible(true);
            case "snapshot" -> snapshotPanel.setVisible(true);
            case "resources" -> resourcePanel.setVisible(true);
            case "server" -> serverPanel.setVisible(true);
            case "commands" -> commandPanel.setVisible(true);
            case "rendering" -> renderingPanel.setVisible(true);
        }

        updateNavButtonStyles();
        log.debug("Switched to panel: {}", panelName);
    }

    /**
     * View the snapshot for a specific match.
     */
    private void viewMatchSnapshot(long matchId) {
        currentMatchId = matchId;

        // Update the snapshot panel with the new match ID
        // For now, we recreate it. A better approach would be to add a setMatchId method.
        int panelMargin = 10;
        int panelY = 60 + 40 + panelMargin; // navY + navHeight + margin
        int panelHeight = window.getHeight() - panelY - panelMargin;
        int panelWidth = window.getWidth() - 2 * panelMargin;

        // Remove old panel
        window.removeComponent(snapshotPanel);
        snapshotPanel.dispose();

        // Create new panel with updated match ID
        snapshotPanel = new SnapshotPanel(
            componentFactory,
            panelMargin,
            panelY,
            panelWidth,
            panelHeight,
            serverUrl,
            matchId
        );

        window.addComponent(snapshotPanel);
        snapshotPanel.setVisible(true);

        // Update server label
        serverLabel.setText("Server: " + serverUrl + " | Match: " + matchId);

        // Switch to snapshot panel
        switchPanel("snapshot");

        log.info("Viewing snapshot for match {}", matchId);
    }

    /**
     * Update nav button styles to show active state.
     */
    private void updateNavButtonStyles() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        // Reset all buttons to default style
        matchNavButton.setBackgroundColor(colours.buttonBg());
        snapshotNavButton.setBackgroundColor(colours.buttonBg());
        resourceNavButton.setBackgroundColor(colours.buttonBg());
        serverNavButton.setBackgroundColor(colours.buttonBg());
        commandNavButton.setBackgroundColor(colours.buttonBg());
        renderingNavButton.setBackgroundColor(colours.buttonBg());

        // Highlight active button
        switch (activePanel) {
            case "matches" -> matchNavButton.setBackgroundColor(colours.accent());
            case "snapshot" -> snapshotNavButton.setBackgroundColor(colours.accent());
            case "resources" -> resourceNavButton.setBackgroundColor(colours.accent());
            case "server" -> serverNavButton.setBackgroundColor(colours.accent());
            case "commands" -> commandNavButton.setBackgroundColor(colours.accent());
            case "rendering" -> renderingNavButton.setBackgroundColor(colours.accent());
        }
    }

    /**
     * Advance the simulation by one tick.
     */
    private void advanceTick() {
        simulationService.advanceTick().thenAccept(newTick -> {
            if (newTick >= 0) {
                currentTick = newTick;
                tickLabel.setText("Tick: " + newTick);
                log.info("Advanced to tick {}", newTick);
            }
        });
    }

    /**
     * Start auto-advancing ticks (play mode).
     */
    private void startPlay() {
        ComponentFactory.Colours colours = componentFactory.getColours();
        simulationService.play(10).thenAccept(success -> {
            if (success) {
                isPlaying = true;
                playButton.setBackgroundColor(colours.success());
                stopButton.setBackgroundColor(colours.red());
                log.info("Started play mode");
            }
        });
    }

    /**
     * Stop auto-advancing ticks.
     */
    private void stopPlay() {
        ComponentFactory.Colours colours = componentFactory.getColours();
        simulationService.stop().thenAccept(success -> {
            if (success) {
                isPlaying = false;
                playButton.setBackgroundColor(colours.accent());
                stopButton.setBackgroundColor(colours.buttonBg());
                log.info("Stopped play mode");
            }
        });
    }

    /**
     * Check if currently playing.
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Refresh the current tick from the server.
     */
    private void refreshCurrentTick() {
        simulationService.getCurrentTick().thenAccept(tick -> {
            if (tick >= 0) {
                currentTick = tick;
                tickLabel.setText("Tick: " + tick);
            }
        });
    }

    /**
     * Get the current tick value.
     */
    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Get the simulation service for testing.
     */
    public SimulationService getSimulationService() {
        return simulationService;
    }

    private void update() {
        // Auto-refresh tick label while playing
        if (isPlaying && !tickRefreshPending) {
            long now = System.currentTimeMillis();
            if (now - lastTickRefreshTime >= TICK_REFRESH_INTERVAL_MS) {
                lastTickRefreshTime = now;
                tickRefreshPending = true;
                simulationService.getCurrentTick().thenAccept(tick -> {
                    tickRefreshPending = false;
                    if (tick >= 0 && tick != currentTick) {
                        currentTick = tick;
                        tickLabel.setText("Tick: " + tick);
                    }
                });
            }
        }

        // Update all panels
        if (matchPanel != null && matchPanel.isVisible()) {
            matchPanel.update();
        }
        if (snapshotPanel != null && snapshotPanel.isVisible()) {
            snapshotPanel.update();
        }
        if (resourcePanel != null && resourcePanel.isVisible()) {
            resourcePanel.update();
        }
        if (serverPanel != null && serverPanel.isVisible()) {
            serverPanel.update();
        }
        if (commandPanel != null && commandPanel.isVisible()) {
            commandPanel.update();
        }
        if (renderingPanel != null && renderingPanel.isVisible()) {
            renderingPanel.update();
        }
    }

    private void cleanup() {
        if (snapshotPanel != null) {
            snapshotPanel.dispose();
        }
        if (resourcePanel != null) {
            resourcePanel.dispose();
        }
        if (serverPanel != null) {
            serverPanel.dispose();
        }
        if (matchPanel != null) {
            matchPanel.dispose();
        }
        if (commandPanel != null) {
            commandPanel.dispose();
        }
        if (renderingPanel != null) {
            renderingPanel.dispose();
        }
        if (simulationService != null) {
            simulationService.shutdown();
        }
    }

    /**
     * Get the snapshot panel.
     */
    public SnapshotPanel getSnapshotPanel() {
        return snapshotPanel;
    }

    /**
     * Get the resource panel.
     */
    public ResourcePanel getResourcePanel() {
        return resourcePanel;
    }

    /**
     * Get the server panel.
     */
    public ServerPanel getServerPanel() {
        return serverPanel;
    }

    /**
     * Get the match panel.
     */
    public MatchPanel getMatchPanel() {
        return matchPanel;
    }

    /**
     * Get the command panel.
     */
    public CommandPanel getCommandPanel() {
        return commandPanel;
    }

    /**
     * Get the rendering panel.
     */
    public RenderingPanel getRenderingPanel() {
        return renderingPanel;
    }

    /**
     * Switch to a panel by name. Available panels: matches, snapshot, resources, server, commands, rendering.
     */
    public void switchToPanel(String panelName) {
        switchPanel(panelName);
    }

    /**
     * Advance the simulation tick and return the new tick value.
     * This is a blocking call for testing convenience.
     */
    public long advanceTickSync() {
        try {
            long newTick = simulationService.advanceTick().get();
            if (newTick >= 0) {
                currentTick = newTick;
                tickLabel.setText("Tick: " + newTick);
            }
            return newTick;
        } catch (Exception e) {
            log.error("Failed to advance tick", e);
            return -1;
        }
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
        String serverUrl = null;
        String rendererType = null;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server", "-s" -> {
                    if (i + 1 < args.length) {
                        serverUrl = args[++i];
                    }
                }
                case "--renderer", "-r" -> {
                    if (i + 1 < args.length) {
                        rendererType = args[++i];
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
            }
        }

        // If no server URL from command line, try to load from auto-config file
        if (serverUrl == null) {
            serverUrl = loadServerUrlFromConfig();
        }

        EngineGuiApplication app = new EngineGuiApplication(serverUrl);
        log.info("Starting Lightning Engine GUI");
        log.info("Server URL: {}", app.serverUrl);
        if (rendererType != null) {
            log.info("Renderer: {}", rendererType);
        }
        log.debug("Full config: {}", app.config);

        app.run();
    }

    /**
     * Load server URL from server.properties file in the same directory as the JAR.
     * This enables auto-configuration when downloaded from a server.
     */
    private static String loadServerUrlFromConfig() {
        // Try multiple locations for the config file
        String[] configPaths = {
            "server.properties",           // Current directory
            getJarDirectory() + "/server.properties"  // Same directory as JAR
        };

        for (String path : configPaths) {
            if (path == null) continue;
            try {
                java.nio.file.Path configPath = java.nio.file.Paths.get(path);
                if (java.nio.file.Files.exists(configPath)) {
                    java.util.Properties props = new java.util.Properties();
                    try (var reader = java.nio.file.Files.newBufferedReader(configPath)) {
                        props.load(reader);
                    }
                    String url = props.getProperty("server.url");
                    if (url != null && !url.isBlank()) {
                        log.info("Loaded server URL from config file: {}", path);
                        return url;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not load config from {}: {}", path, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get the directory containing the JAR file (or class files in dev mode).
     */
    private static String getJarDirectory() {
        try {
            java.net.URL jarUrl = EngineGuiApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            java.nio.file.Path jarPath = java.nio.file.Paths.get(jarUrl.toURI());
            if (java.nio.file.Files.isRegularFile(jarPath)) {
                return jarPath.getParent().toString();
            }
            return jarPath.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void printUsage() {
        String usage = """
            Lightning Engine GUI

            Usage: java -jar engine-gui.jar [options]

            Options:
              -s, --server <url>      Server URL (default: http://localhost:8080)
              -r, --renderer <type>   Renderer backend: nanovg (default) or opengl
              -h, --help              Show this help message

            Renderer Types:
              nanovg   - NanoVG-based renderer with vector graphics (default)
              opengl   - Pure OpenGL renderer with custom shaders

            Configuration:
              Settings can also be configured via:
              - System properties: -Dgui.window.width=1200
              - Environment variables: GUI_SERVER_URL=http://localhost:8080
              - gui.properties file in current directory

            Examples:
              java -jar engine-gui.jar
              java -jar engine-gui.jar -s http://192.168.1.100:8080
              java -jar engine-gui.jar -r opengl
            """;
        System.out.println(usage);
    }
}
