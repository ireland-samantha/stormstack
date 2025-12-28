package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.api.resource.adapter.SimulationAdapter;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.backend.installation.GameFactory;
import com.lightningfirefly.game.orchestrator.resource.CachingResourceProvider;
import com.lightningfirefly.game.orchestrator.resource.ResourceProvider;
import com.lightningfirefly.game.orchestrator.resource.ResourceProviderAdapter;
import com.lightningfirefly.game.renderering.GameRenderer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Implementation of GameOrchestrator that coordinates between
 * the game engine, backend server, and domain objects.
 *
 * <p>This orchestrator:
 * <ul>
 *   <li>Installs games by uploading resources and modules to the server</li>
 *   <li>Starts games by creating matches and subscribing to snapshots</li>
 *   <li>Updates domain objects via {@link SnapshotObserver}</li>
 *   <li>Processes individual property watches</li>
 *   <li>Caches resources (textures) locally for rendering</li>
 * </ul>
 */
@Slf4j
public final class GameOrchestratorImpl implements GameOrchestrator {

    private static final String DEFAULT_CACHE_DIR = ".game-cache";
    private static final String RESOURCE_ID_COMPONENT = "RESOURCE_ID";

    private final MatchAdapter matchAdapter;
    private final ModuleAdapter moduleAdapter;
    private final ResourceProvider resourceProvider;
    private final GameMasterAdapter gameMasterAdapter;
    private final SimulationAdapter simulationAdapter;
    private final SnapshotSubscriber snapshotSubscriber;
    private final SnapshotObserver snapshotObserver;
    private final GameRenderer gameRenderer;

    private final Map<GameFactory, GameSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<GameFactory, InstallationInfo> installedGames = new ConcurrentHashMap<>();
    private final List<DomainPropertyUpdate> watches = new CopyOnWriteArrayList<>();

    /**
     * Create a new orchestrator with all dependencies.
     * <p>Creates a caching resource provider wrapping the given resource adapter.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceAdapter    adapter for resource operations
     * @param gameMasterAdapter  adapter for game master operations
     * @param simulationAdapter  adapter for simulation tick operations
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param gameRenderer       renderer for displaying the game
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceAdapter resourceAdapter,
            GameMasterAdapter gameMasterAdapter,
            SimulationAdapter simulationAdapter,
            SnapshotSubscriber snapshotSubscriber,
            GameRenderer gameRenderer) {
        this(matchAdapter, moduleAdapter, resourceAdapter, gameMasterAdapter,
                simulationAdapter, snapshotSubscriber, new SnapshotObserver(), gameRenderer,
                Path.of(System.getProperty("user.home"), DEFAULT_CACHE_DIR));
    }

    /**
     * Create a new orchestrator with a custom SnapshotObserver.
     * <p>Creates a caching resource provider wrapping the given resource adapter.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceAdapter    adapter for resource operations
     * @param gameMasterAdapter  adapter for game master operations
     * @param simulationAdapter  adapter for simulation tick operations
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param snapshotObserver   observer for domain object updates
     * @param gameRenderer       renderer for displaying the game
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceAdapter resourceAdapter,
            GameMasterAdapter gameMasterAdapter,
            SimulationAdapter simulationAdapter,
            SnapshotSubscriber snapshotSubscriber,
            SnapshotObserver snapshotObserver,
            GameRenderer gameRenderer) {
        this(matchAdapter, moduleAdapter, resourceAdapter, gameMasterAdapter,
                simulationAdapter, snapshotSubscriber, snapshotObserver, gameRenderer,
                Path.of(System.getProperty("user.home"), DEFAULT_CACHE_DIR));
    }

    /**
     * Create a new orchestrator with all dependencies and custom cache directory.
     * <p>Creates a caching resource provider wrapping the given resource adapter.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceAdapter    adapter for resource operations
     * @param gameMasterAdapter  adapter for game master operations
     * @param simulationAdapter  adapter for simulation tick operations
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param snapshotObserver   observer for domain object updates
     * @param gameRenderer       renderer for displaying the game
     * @param cacheDirectory     directory for caching downloaded resources
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceAdapter resourceAdapter,
            GameMasterAdapter gameMasterAdapter,
            SimulationAdapter simulationAdapter,
            SnapshotSubscriber snapshotSubscriber,
            SnapshotObserver snapshotObserver,
            GameRenderer gameRenderer,
            Path cacheDirectory) {
        this(matchAdapter, moduleAdapter,
                new CachingResourceProvider(new ResourceProviderAdapter(resourceAdapter), cacheDirectory),
                gameMasterAdapter, simulationAdapter, snapshotSubscriber, snapshotObserver, gameRenderer);
    }

    /**
     * Create a new orchestrator with a custom ResourceProvider.
     * <p>Use this constructor for full control over resource management.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceProvider   provider for resource operations (may include caching)
     * @param gameMasterAdapter  adapter for game master operations
     * @param simulationAdapter  adapter for simulation tick operations
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param snapshotObserver   observer for domain object updates
     * @param gameRenderer       renderer for displaying the game
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceProvider resourceProvider,
            GameMasterAdapter gameMasterAdapter,
            SimulationAdapter simulationAdapter,
            SnapshotSubscriber snapshotSubscriber,
            SnapshotObserver snapshotObserver,
            GameRenderer gameRenderer) {
        this.matchAdapter = matchAdapter;
        this.moduleAdapter = moduleAdapter;
        this.resourceProvider = resourceProvider;
        this.gameMasterAdapter = gameMasterAdapter;
        this.simulationAdapter = simulationAdapter;
        this.snapshotSubscriber = snapshotSubscriber;
        this.snapshotObserver = snapshotObserver;
        this.gameRenderer = gameRenderer;
    }

    @Override
    public void installGame(GameFactory factory) {
        try {
            Map<String, Long> uploadedResourceIds = new HashMap<>();
            String installedGameMaster = null;
            List<String> uploadedModules = new ArrayList<>();

            // 1) Upload module JARs if provided
            Map<String, byte[]> moduleJars = factory.getModuleJars();
            for (Map.Entry<String, byte[]> entry : moduleJars.entrySet()) {
                String moduleName = entry.getKey();
                byte[] jarData = entry.getValue();
                log.info("Uploading module JAR: {} ({} bytes)", moduleName, jarData.length);
                var response = moduleAdapter.uploadModule(moduleName + ".jar", jarData);
                log.debug("Module upload response: {}", response);
                uploadedModules.add(moduleName);
            }

            // 2) Upload resources (sprites/textures) to the server
            List<GameFactory.GameResource> resources = factory.getResources();
            for (GameFactory.GameResource resource : resources) {
                long resourceId = resourceProvider.uploadResource(
                        resource.name(),
                        resource.type(),
                        resource.data()
                );
                uploadedResourceIds.put(resource.texturePath(), resourceId);
            }

            // 3) Upload GameMaster JAR if bundled with the factory
            Optional<byte[]> gameMasterJar = factory.getGameMasterJar();
            Optional<String> gameMasterName = factory.getGameMasterName();
            if (gameMasterJar.isPresent() && gameMasterName.isPresent()) {
                String name = gameMasterName.get();
                // Only upload if not already installed
                if (!gameMasterAdapter.hasGameMaster(name)) {
                    gameMasterAdapter.uploadGameMaster(name + ".jar", gameMasterJar.get());
                    installedGameMaster = name;
                }
            }

            // 4) Track installation for potential cleanup
            InstallationInfo info = new InstallationInfo(
                    uploadedResourceIds,
                    installedGameMaster
            );
            installedGames.put(factory, info);

        } catch (IOException e) {
            throw new RuntimeException("Failed to install game", e);
        }
    }

    /**
     * Uninstall a previously installed game.
     * This removes uploaded resources and game masters from the server.
     *
     * @param factory the game factory to uninstall
     */
    public void uninstallGame(GameFactory factory) {
        InstallationInfo info = installedGames.remove(factory);
        if (info == null) {
            return;
        }

        // Delete uploaded resources
        for (Long resourceId : info.uploadedResourceIds().values()) {
            try {
                resourceProvider.deleteResource(resourceId);
            } catch (IOException e) {
                // Log but continue - resource may already be deleted
            }
        }

        // Uninstall game master if we installed it
        if (info.installedGameMaster() != null) {
            try {
                gameMasterAdapter.uninstallGameMaster(info.installedGameMaster());
            } catch (IOException e) {
                // Log but continue
            }
        }
    }

    /**
     * Check if a game factory is installed.
     *
     * @param factory the game factory
     * @return true if installed
     */
    public boolean isGameInstalled(GameFactory factory) {
        return installedGames.containsKey(factory);
    }

    /**
     * Get the resource ID mapping for an installed game.
     * Maps texture paths to server resource IDs.
     *
     * @param factory the game factory
     * @return the resource ID mapping, or null if not installed
     */
    public Map<String, Long> getResourceIdMapping(GameFactory factory) {
        InstallationInfo info = installedGames.get(factory);
        return info != null ? info.uploadedResourceIds() : null;
    }


    // Note: GameRenderer is NOT started here - caller is responsible for
    // starting the renderer on the appropriate thread.
    @Override
    public void startGame(GameFactory factory) {
        try {
            // 1) Create a match with the required modules and game master
            List<String> enabledModules = getRequiredModules(factory);

            List<String> enabledGameMasters = factory.getGameMasterName()
                    .map(List::of)
                    .orElse(List.of());

            MatchAdapter.MatchResponse match = matchAdapter.createMatchWithGameMasters(enabledModules, enabledGameMasters);
            if (match == null) {
                throw new IllegalStateException("Failed to create match: server returned null response");
            }

            long matchId = match.id();

            // 2) Subscribe to snapshots and set up SnapshotObserver for domain objects
            Runnable unsubscribe = snapshotSubscriber.subscribe(matchId, this::onSnapshotReceived);

            // Track the session
            GameSession session = new GameSession(matchId, unsubscribe);
            activeSessions.put(factory, session);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start game", e);
        }
    }

    @Override
    public void stopGame(GameFactory factory) {
        GameSession session = activeSessions.remove(factory);
        if (session == null) {
            return;
        }

        // 1) Unsubscribe from snapshots
        session.unsubscribe().run();

        // 2) Delete the match
        try {
            matchAdapter.deleteMatch(session.matchId());
        } catch (IOException e) {
            // Log but don't fail - match may already be deleted
        }

        // 3) Stop the renderer and free resources
        if (gameRenderer != null) {
            gameRenderer.stop();
        }
    }

    @Override
    public void registerDomainPropertyUpdate(DomainPropertyUpdate update) {
        watches.add(update);
    }

    @Override
    public void unregisterDomainPropertyUpdate(DomainPropertyUpdate update) {
        watches.remove(update);
    }

    /**
     * Get the current active session for a factory.
     *
     * @param factory the game factory
     * @return the session, or null if not active
     */
    public GameSession getSession(GameFactory factory) {
        return activeSessions.get(factory);
    }

    /**
     * Check if a game is currently running.
     *
     * @param factory the game factory
     * @return true if the game is active
     */
    public boolean isGameRunning(GameFactory factory) {
        return activeSessions.containsKey(factory);
    }

    /**
     * Called when a components is received from the server.
     *
     * @param snapshotData the raw components data
     */
    private void onSnapshotReceived(Map<String, Map<String, List<Float>>> snapshotData) {
        // Update domain objects via the observer
        snapshotObserver.onSnapshot(snapshotData);

        // Process individual watches
        processWatches(snapshotData);

        // Extract and cache resources asynchronously
        cacheResourcesFromSnapshot(snapshotData);
    }

    /**
     * Extract and ensure all resources from the snapshot are cached.
     * Delegates to the ResourceProvider for download and caching.
     */
    private void cacheResourcesFromSnapshot(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null) {
            return;
        }

        // Extract all RESOURCE_ID values from all modules
        for (Map<String, List<Float>> moduleData : snapshotData.values()) {
            List<Float> resourceIdList = moduleData.get(RESOURCE_ID_COMPONENT);
            if (resourceIdList != null) {
                for (Float resourceIdFloat : resourceIdList) {
                    if (resourceIdFloat != null && resourceIdFloat > 0) {
                        resourceProvider.ensureResource(resourceIdFloat.longValue());
                    }
                }
            }
        }
    }

    /**
     * Set a listener for resource download events.
     *
     * @param listener the listener to notify on download events
     */
    public void setResourceDownloadListener(Consumer<ResourceDownloadEvent> listener) {
        resourceProvider.setDownloadListener(listener);
    }

    /**
     * Get the local file path for a cached resource.
     *
     * @param resourceId the resource ID
     * @return the path to the cached file, or null if not cached
     */
    public Path getCachedResourcePath(long resourceId) {
        return resourceProvider.getLocalPath(resourceId).orElse(null);
    }

    /**
     * Check if a resource is cached locally.
     *
     * @param resourceId the resource ID
     * @return true if the resource is cached
     */
    public boolean isResourceCached(long resourceId) {
        return resourceProvider.isAvailableLocally(resourceId);
    }

    /**
     * Clear the resource cache.
     */
    public void clearResourceCache() {
        resourceProvider.clearCache();
    }

    /**
     * Get the cache directory path.
     *
     * @return the cache directory, or null if caching is not supported
     */
    public Path getCacheDirectory() {
        return resourceProvider.getCacheDirectory().orElse(null);
    }

    /**
     * Get the underlying resource provider.
     *
     * @return the resource provider
     */
    public ResourceProvider getResourceProvider() {
        return resourceProvider;
    }

    /**
     * Shutdown the orchestrator and release resources.
     */
    @Override
    public void shutdown() {
        resourceProvider.close();
    }

    /**
     * Process all registered property observers against the components.
     * <p>Observers (watches) are notified when their tracked ECS component values change.
     */
    private void processWatches(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null || watches.isEmpty()) {
            return;
        }

        for (DomainPropertyUpdate watch : watches) {
            processWatch(watch, snapshotData);
        }
    }

    /**
     * Process a single watch against the components.
     */
    private void processWatch(DomainPropertyUpdate watch, Map<String, Map<String, List<Float>>> snapshotData) {
        String ecsPath = watch.ecsPath();
        String[] parts = ecsPath.split("\\.", 2);
        if (parts.length != 2) {
            return;
        }

        String moduleName = parts[0];
        String componentName = parts[1];

        Map<String, List<Float>> moduleData = snapshotData.get(moduleName);
        if (moduleData == null) {
            return;
        }

        // Find entity index
        List<Float> entityIds = moduleData.get("ENTITY_ID");
        if (entityIds == null) {
            return;
        }

        int entityIndex = -1;
        float targetId = (float) watch.entityId();
        for (int i = 0; i < entityIds.size(); i++) {
            Float id = entityIds.get(i);
            if (id != null && id == targetId) {
                entityIndex = i;
                break;
            }
        }

        if (entityIndex < 0) {
            return;
        }

        // Get component value
        List<Float> values = moduleData.get(componentName);
        if (values == null || entityIndex >= values.size()) {
            return;
        }

        Float value = values.get(entityIndex);
        if (value != null) {
            watch.callback().accept(value);
        }
    }

    /**
     * Get the list of required module names for a game.
     * Delegates to the factory's getRequiredModules() method.
     */
    private List<String> getRequiredModules(GameFactory factory) {
        return factory.getRequiredModules();
    }
}
