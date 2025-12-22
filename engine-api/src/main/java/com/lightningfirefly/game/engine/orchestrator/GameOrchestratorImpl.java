package com.lightningfirefly.game.engine.orchestrator;

import com.lightningfirefly.engine.api.resource.Resource;
import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.engine.GameModule;
import com.lightningfirefly.game.engine.renderer.GameRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class GameOrchestratorImpl implements GameOrchestrator {

    private static final String DEFAULT_CACHE_DIR = ".game-cache";
    private static final String RESOURCE_ID_COMPONENT = "RESOURCE_ID";

    private final MatchAdapter matchAdapter;
    private final ModuleAdapter moduleAdapter;
    private final ResourceAdapter resourceAdapter;
    private final GameMasterAdapter gameMasterAdapter;
    private final SnapshotSubscriber snapshotSubscriber;
    private final SnapshotObserver snapshotObserver;
    private final GameRenderer gameRenderer;

    private final Map<GameModule, GameSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<GameModule, InstallationInfo> installedGames = new ConcurrentHashMap<>();
    private final List<WatchedPropertyUpdate> watches = new CopyOnWriteArrayList<>();

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
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param gameRenderer       renderer for displaying the game
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceAdapter resourceAdapter,
            GameMasterAdapter gameMasterAdapter,
            SnapshotSubscriber snapshotSubscriber,
            GameRenderer gameRenderer) {
        this(matchAdapter, moduleAdapter, resourceAdapter, gameMasterAdapter,
                snapshotSubscriber, new SnapshotObserver(), gameRenderer,
                Path.of(System.getProperty("user.home"), DEFAULT_CACHE_DIR));
    }

    /**
     * Create a new orchestrator with a custom SnapshotObserver.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceAdapter    adapter for resource operations
     * @param gameMasterAdapter  adapter for game master operations
     * @param snapshotSubscriber subscriber for real-time snapshots
     * @param snapshotObserver   observer for domain object updates
     * @param gameRenderer       renderer for displaying the game
     */
    public GameOrchestratorImpl(
            MatchAdapter matchAdapter,
            ModuleAdapter moduleAdapter,
            ResourceAdapter resourceAdapter,
            GameMasterAdapter gameMasterAdapter,
            SnapshotSubscriber snapshotSubscriber,
            SnapshotObserver snapshotObserver,
            GameRenderer gameRenderer) {
        this(matchAdapter, moduleAdapter, resourceAdapter, gameMasterAdapter,
                snapshotSubscriber, snapshotObserver, gameRenderer,
                Path.of(System.getProperty("user.home"), DEFAULT_CACHE_DIR));
    }

    /**
     * Create a new orchestrator with all dependencies and custom cache directory.
     *
     * @param matchAdapter       adapter for match operations
     * @param moduleAdapter      adapter for module operations
     * @param resourceAdapter    adapter for resource operations
     * @param gameMasterAdapter  adapter for game master operations
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
            SnapshotSubscriber snapshotSubscriber,
            SnapshotObserver snapshotObserver,
            GameRenderer gameRenderer,
            Path cacheDirectory) {
        this.matchAdapter = matchAdapter;
        this.moduleAdapter = moduleAdapter;
        this.resourceAdapter = resourceAdapter;
        this.gameMasterAdapter = gameMasterAdapter;
        this.snapshotSubscriber = snapshotSubscriber;
        this.snapshotObserver = snapshotObserver;
        this.gameRenderer = gameRenderer;
        this.cacheDirectory = cacheDirectory;
        this.downloadExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ResourceDownloader");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void installGame(GameModule module) {
        try {
            Map<String, Long> uploadedResourceIds = new HashMap<>();
            String installedGameMaster = null;

            // 1) Upload resources (sprites/textures) to the server
            List<GameModule.GameResource> resources = module.getResources();
            for (GameModule.GameResource resource : resources) {
                long resourceId = resourceAdapter.uploadResource(
                        resource.name(),
                        resource.type(),
                        resource.data()
                );
                uploadedResourceIds.put(resource.texturePath(), resourceId);
            }

            // 2) Upload GameMaster JAR if bundled with the module
            byte[] gameMasterJar = module.getGameMasterJar();
            String gameMasterName = module.getGameMasterName();
            if (gameMasterJar != null && gameMasterName != null) {
                // Only upload if not already installed
                if (!gameMasterAdapter.hasGameMaster(gameMasterName)) {
                    gameMasterAdapter.uploadGameMaster(gameMasterName + ".jar", gameMasterJar);
                    installedGameMaster = gameMasterName;
                }
            }

            // 3) Track installation for potential cleanup
            InstallationInfo info = new InstallationInfo(
                    uploadedResourceIds,
                    installedGameMaster
            );
            installedGames.put(module, info);

        } catch (IOException e) {
            throw new RuntimeException("Failed to install game", e);
        }
    }

    /**
     * Uninstall a previously installed game.
     * This removes uploaded resources and game masters from the server.
     *
     * @param module the game module to uninstall
     */
    public void uninstallGame(GameModule module) {
        InstallationInfo info = installedGames.remove(module);
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
     * Check if a game module is installed.
     *
     * @param module the game module
     * @return true if installed
     */
    public boolean isGameInstalled(GameModule module) {
        return installedGames.containsKey(module);
    }

    /**
     * Get the resource ID mapping for an installed game.
     * Maps texture paths to server resource IDs.
     *
     * @param module the game module
     * @return the resource ID mapping, or null if not installed
     */
    public Map<String, Long> getResourceIdMapping(GameModule module) {
        InstallationInfo info = installedGames.get(module);
        return info != null ? info.uploadedResourceIds() : null;
    }

    @Override
    public void startGame(GameModule module) {
        try {
            // 1) Create a match with the required modules
            List<String> enabledModules = getRequiredModules(module);
            MatchAdapter.MatchResponse match = matchAdapter.createMatch(enabledModules);
            long matchId = match.id();

            // 2) Subscribe to snapshots and set up SnapshotObserver for domain objects
            Runnable unsubscribe = snapshotSubscriber.subscribe(matchId, this::onSnapshotReceived);

            // 3) Set up the GameRenderer with Snapshots and call to render the game
            if (gameRenderer != null) {
                gameRenderer.startAsync(() -> {
                    // Frame update callback - rendering handled by snapshot subscription
                });
            }

            // Track the session
            GameSession session = new GameSession(matchId, unsubscribe);
            activeSessions.put(module, session);

        } catch (IOException e) {
            throw new RuntimeException("Failed to start game", e);
        }
    }

    @Override
    public void stopGame(GameModule module) {
        GameSession session = activeSessions.remove(module);
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
    public void registerWatch(WatchedPropertyUpdate watch) {
        watches.add(watch);
    }

    /**
     * Unregister a previously registered watch.
     *
     * @param watch the watch to remove
     */
    public void unregisterWatch(WatchedPropertyUpdate watch) {
        watches.remove(watch);
    }

    /**
     * Get the current active session for a module.
     *
     * @param module the game module
     * @return the session, or null if not active
     */
    public GameSession getSession(GameModule module) {
        return activeSessions.get(module);
    }

    /**
     * Check if a game is currently running.
     *
     * @param module the game module
     * @return true if the game is active
     */
    public boolean isGameRunning(GameModule module) {
        return activeSessions.containsKey(module);
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

    /**
     * Extract RESOURCE_ID values from snapshot and trigger async downloads.
     */
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
    public void shutdown() {
        downloadExecutor.shutdownNow();
        clearResourceCache();
    }

    /**
     * Process all registered watches against the snapshot.
     */
    private void processWatches(Map<String, Map<String, List<Float>>> snapshotData) {
        if (snapshotData == null || watches.isEmpty()) {
            return;
        }

        for (WatchedPropertyUpdate watch : watches) {
            processWatch(watch, snapshotData);
        }
    }

    /**
     * Process a single watch against the snapshot.
     */
    private void processWatch(WatchedPropertyUpdate watch, Map<String, Map<String, List<Float>>> snapshotData) {
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
     * Delegates to the module's getRequiredModules() method.
     */
    protected List<String> getRequiredModules(GameModule module) {
        return module.getRequiredModules();
    }

    /**
     * Interface for subscribing to snapshot updates.
     */
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
     */
    public record GameSession(long matchId, Runnable unsubscribe) {
    }

    /**
     * Record tracking resources installed for a game module.
     *
     * @param uploadedResourceIds maps texture paths to server resource IDs
     * @param installedGameMaster the game master name if we installed it, null otherwise
     */
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
