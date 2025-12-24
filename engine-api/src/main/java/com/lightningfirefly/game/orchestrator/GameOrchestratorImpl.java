package com.lightningfirefly.game.orchestrator;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.engine.api.resource.adapter.SimulationAdapter;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.backend.installation.GameFactory;
import com.lightningfirefly.game.renderering.GameRenderer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
public class GameOrchestratorImpl implements GameOrchestrator {

    private static final String DEFAULT_CACHE_DIR = ".game-cache";
    private static final String RESOURCE_ID_COMPONENT = "RESOURCE_ID";

    private final MatchAdapter matchAdapter;
    private final ModuleAdapter moduleAdapter;
    private final ResourceAdapter resourceAdapter;
    private final GameMasterAdapter gameMasterAdapter;
    private final SimulationAdapter simulationAdapter;
    private final SnapshotSubscriber snapshotSubscriber;
    private final SnapshotObserver snapshotObserver;
    private final GameRenderer gameRenderer;

    private final Map<GameFactory, GameSession> activeSessions = new ConcurrentHashMap<>();
    private final ExecutorService tickExecutor;
    private final Map<GameFactory, InstallationInfo> installedGames = new ConcurrentHashMap<>();
    private final List<WatchedDomainPropertyUpdate> watches = new CopyOnWriteArrayList<>();

    // Resource caching
    private final Path cacheDirectory;
    private final Set<Long> downloadedResourceIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingDownloads = ConcurrentHashMap.newKeySet();
    private final Map<Long, Path> resourceIdToPath = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor;
    private Consumer<ResourceDownloadEvent> resourceDownloadListener;

    /**
     * Create a new orchestrator with all dependencies.
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
        this.matchAdapter = matchAdapter;
        this.moduleAdapter = moduleAdapter;
        this.resourceAdapter = resourceAdapter;
        this.gameMasterAdapter = gameMasterAdapter;
        this.simulationAdapter = simulationAdapter;
        this.snapshotSubscriber = snapshotSubscriber;
        this.snapshotObserver = snapshotObserver;
        this.gameRenderer = gameRenderer;
        this.cacheDirectory = cacheDirectory;
        this.downloadExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ResourceDownloader");
            t.setDaemon(true);
            return t;
        });
        this.tickExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TickAdvancer");
            t.setDaemon(true);
            return t;
        });
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
                long resourceId = resourceAdapter.uploadResource(
                        resource.name(),
                        resource.type(),
                        resource.data()
                );
                uploadedResourceIds.put(resource.texturePath(), resourceId);
            }

            // 3) Upload GameMaster JAR if bundled with the factory
            byte[] gameMasterJar = factory.getGameMasterJar();
            String gameMasterName = factory.getGameMasterName();
            if (gameMasterJar != null && gameMasterName != null) {
                // Only upload if not already installed
                if (!gameMasterAdapter.hasGameMaster(gameMasterName)) {
                    gameMasterAdapter.uploadGameMaster(gameMasterName + ".jar", gameMasterJar);
                    installedGameMaster = gameMasterName;
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
                resourceAdapter.deleteResource(resourceId);
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

    @Override
    public void startGame(GameFactory factory) {
        try {
            // 1) Create a match with the required modules and game master
            List<String> enabledModules = getRequiredModules(factory);

            String gameMasterName = factory.getGameMasterName();
            List<String> enabledGameMasters = gameMasterName != null ? List.of(gameMasterName) : List.of();

            // todo: what if the response fails?
            MatchAdapter.MatchResponse match = matchAdapter.createMatchWithGameMasters(enabledModules, enabledGameMasters);

            long matchId = match.id();

            // 2) Subscribe to snapshots and set up SnapshotObserver for domain objects
            Runnable unsubscribe = snapshotSubscriber.subscribe(matchId, this::onSnapshotReceived);

            // Note: GameRenderer is NOT started here - caller is responsible for
            // starting the renderer on the appropriate thread (main thread on macOS)

            // 3) Start auto-tick advancement in background (default on)

            // todo - no need to have a tick loop, please remove this logic
            var tickFuture = tickExecutor.submit(() -> runTickLoop(matchId));

            // Track the session
            GameSession session = new GameSession(matchId, unsubscribe, tickFuture);
            activeSessions.put(factory, session);

        } catch (IOException e) {
            throw new RuntimeException("Failed to start game", e);
        }
    }

    /**
     * Background loop that advances ticks at a fixed rate.
     * Runs until the thread is interrupted (when game is stopped).
     */
    private void runTickLoop(long matchId) {
        try {
            // Initial tick to set up the game
            simulationAdapter.advanceTick();

            // Continuous tick advancement at ~60Hz
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(16); // ~60 FPS
                try {
                    simulationAdapter.advanceTick();
                } catch (IOException e) {
                    // Log but continue - server may be temporarily unavailable
                    System.err.println("[TickLoop] Failed to advance tick: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("[TickLoop] Failed initial tick: " + e.getMessage());
        }
    }

    @Override
    public void stopGame(GameFactory factory) {
        GameSession session = activeSessions.remove(factory);
        if (session == null) {
            return;
        }

        // 1) Stop the tick loop
        if (session.tickFuture() != null) {
            session.tickFuture().cancel(true);
        }

        // 2) Unsubscribe from snapshots
        session.unsubscribe().run();

        // 3) Delete the match
        try {
            matchAdapter.deleteMatch(session.matchId());
        } catch (IOException e) {
            // Log but don't fail - match may already be deleted
        }

        // 4) Stop the renderer and free resources
        if (gameRenderer != null) {
            gameRenderer.stop();
        }
    }

    @Override
    public void registerWatch(WatchedDomainPropertyUpdate watch) {
        watches.add(watch);
    }

    /**
     * Unregister a previously registered watch.
     *
     * @param watch the watch to remove
     */
    public void unregisterWatch(WatchedDomainPropertyUpdate watch) {
        watches.remove(watch);
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
     * Called when a snapshot is received from the server.
     *
     * @param snapshotData the raw snapshot data
     */
    private void onSnapshotReceived(Map<String, Map<String, List<Float>>> snapshotData) {
        // Update domain objects via the observer
        snapshotObserver.onSnapshot(snapshotData);

        // Process individual watches
        processWatches(snapshotData);

        // Extract and cache resources asynchronously
        cacheResourcesFromSnapshot(snapshotData);
    }

    // todo: refactor resource downloading a caching to a helper class
    private void cacheResourcesFromSnapshot(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null) {
            return;
        }

        Set<Long> resourceIds = new HashSet<>();

        // Extract all RESOURCE_ID values from all modules
        for (Map<String, List<Float>> moduleData : snapshotData.values()) {
            List<Float> resourceIdList = moduleData.get(RESOURCE_ID_COMPONENT);
            if (resourceIdList != null) {
                for (Float resourceIdFloat : resourceIdList) {
                    if (resourceIdFloat != null && resourceIdFloat > 0) {
                        resourceIds.add(resourceIdFloat.longValue());
                    }
                }
            }
        }

        // Download any new resources asynchronously
        for (Long resourceId : resourceIds) {
            if (!downloadedResourceIds.contains(resourceId) && !pendingDownloads.contains(resourceId)) {
                downloadResourceAsync(resourceId);
            }
        }
    }

    /**
     * Download a resource asynchronously and save to disk.
     *
     * @param resourceId the resource ID to download
     */
    private void downloadResourceAsync(long resourceId) {
        if (!pendingDownloads.add(resourceId)) {
            return; // Already pending
        }

        CompletableFuture.runAsync(() -> {
            try {
                downloadAndSaveResource(resourceId);
            } catch (IOException e) {
                notifyDownloadListener(new ResourceDownloadEvent(
                        resourceId, null, ResourceDownloadEvent.Status.FAILED, e));
            } finally {
                pendingDownloads.remove(resourceId);
            }
        }, downloadExecutor);
    }

    /**
     * Download a resource and save it to the cache directory.
     *
     * @param resourceId the resource ID
     * @throws IOException if download or save fails
     */
    private void downloadAndSaveResource(long resourceId) throws IOException {
        // Download resource from server
        Optional<Resource> resourceOpt = resourceAdapter.downloadResource(resourceId);
        if (resourceOpt.isEmpty()) {
            throw new IOException("Resource not found: " + resourceId);
        }

        Resource resource = resourceOpt.get();

        // Ensure cache directory exists
        Files.createDirectories(cacheDirectory);

        // Determine file name and path
        String fileName = determineFileName(resource);
        Path filePath = cacheDirectory.resolve(fileName);

        // Write to disk
        Files.write(filePath, resource.blob());

        // Track the downloaded resource
        downloadedResourceIds.add(resourceId);
        resourceIdToPath.put(resourceId, filePath);

        // Notify listener
        notifyDownloadListener(new ResourceDownloadEvent(
                resourceId, filePath, ResourceDownloadEvent.Status.COMPLETED, null));
    }

    /**
     * Determine the file name for a resource.
     */
    private String determineFileName(Resource resource) {
        String name = resource.resourceName();
        if (name != null && !name.isBlank()) {
            return name;
        }

        // Use resource ID with appropriate extension based on type
        String extension = switch (resource.resourceType()) {
            case "TEXTURE" -> ".png";
            case "AUDIO" -> ".wav";
            default -> ".bin";
        };
        return "resource_" + resource.resourceId() + extension;
    }

    private void notifyDownloadListener(ResourceDownloadEvent event) {
        Consumer<ResourceDownloadEvent> listener = this.resourceDownloadListener;
        if (listener != null) {
            listener.accept(event);
        }
    }

    /**
     * Set a listener for resource download events.
     *
     * @param listener the listener to notify on download events
     */
    public void setResourceDownloadListener(Consumer<ResourceDownloadEvent> listener) {
        this.resourceDownloadListener = listener;
    }

    /**
     * Get the local file path for a cached resource.
     *
     * @param resourceId the resource ID
     * @return the path to the cached file, or null if not cached
     */
    public Path getCachedResourcePath(long resourceId) {
        return resourceIdToPath.get(resourceId);
    }

    /**
     * Check if a resource is cached locally.
     *
     * @param resourceId the resource ID
     * @return true if the resource is cached
     */
    public boolean isResourceCached(long resourceId) {
        return downloadedResourceIds.contains(resourceId);
    }

    //todo: clean up unused methods
    /**
     * Check if a resource download is pending.
     *
     * @param resourceId the resource ID
     * @return true if the resource is being downloaded
     */
    public boolean isResourceDownloadPending(long resourceId) {
        return pendingDownloads.contains(resourceId);
    }

    /**
     * Get all cached resource IDs.
     *
     * @return set of cached resource IDs
     */
    public Set<Long> getCachedResourceIds() {
        return Set.copyOf(downloadedResourceIds);
    }

    /**
     * Clear the resource cache.
     * This removes tracking of downloaded resources but does not delete files.
     */
    public void clearResourceCache() {
        downloadedResourceIds.clear();
        resourceIdToPath.clear();
    }

    /**
     * Get the cache directory path.
     *
     * @return the cache directory
     */
    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Shutdown the orchestrator and release resources.
     */
    // todo add to interface, maybe require try with resources
    public void shutdown() {
        downloadExecutor.shutdownNow();
        tickExecutor.shutdownNow();
        clearResourceCache();
    }

    /**
     * Process all registered watches against the snapshot.
     */
    // todo rename "watch" to "observable properties"
    // todo: define proper DTOs for snapshot data
    private void processWatches(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null || watches.isEmpty()) {
            return;
        }

        for (WatchedDomainPropertyUpdate watch : watches) {
            processWatch(watch, snapshotData);
        }
    }

    /**
     * Process a single watch against the snapshot.
     */
    private void processWatch(WatchedDomainPropertyUpdate watch, Map<String, Map<String, List<Float>>> snapshotData) {
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
    // todo: why protected? class should be final.
    protected List<String> getRequiredModules(GameFactory factory) {
        return factory.getRequiredModules();
    }

    /**
     *
     * Interface for subscribing to snapshot updates.
     */
    // todo: refactor out to a new file
    @FunctionalInterface
    public interface SnapshotSubscriber {
        /**
         * Subscribe to snapshot updates for a match.
         *
         * @param matchId  the match ID
         * @param callback called when a snapshot is received
         * @return a runnable that unsubscribes when called
         */
        Runnable subscribe(long matchId, Consumer<Map<String, Map<String, List<Float>>>> callback);
    }

    /**
     * Record representing an active game session.
     *
     * @param matchId     the match ID on the server
     * @param unsubscribe runnable to call to unsubscribe from snapshots
     * @param tickFuture  the future for the tick loop (null if not running)
     */
    // todo: refactor out to a new file

    public record GameSession(long matchId, Runnable unsubscribe, Future<?> tickFuture) {
    }

    /**
     * Record tracking resources installed for a game module.
     *
     * @param uploadedResourceIds maps texture paths to server resource IDs
     * @param installedGameMaster the game master name if we installed it, null otherwise
     */
    // todo: refactor out to a new file

    public record InstallationInfo(
            Map<String, Long> uploadedResourceIds,
            String installedGameMaster
    ) {
    }

    /**
     * Event fired when a resource download completes or fails.
     *
     * @param resourceId the resource ID
     * @param localPath  the local file path (null if failed)
     * @param status     the download status
     * @param error      the error if failed (null if completed)
     */
    // todo: refactor out to a new file

    public record ResourceDownloadEvent(
            long resourceId,
            Path localPath,
            Status status,
            Exception error
    ) {
        public enum Status {
            COMPLETED,
            FAILED
        }
    }
}
