package com.lightningfirefly.game.engine.internal.orchestrator;

import com.lightningfirefly.engine.api.resource.adapter.GameMasterAdapter;
import com.lightningfirefly.engine.api.resource.adapter.MatchAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ModuleAdapter;
import com.lightningfirefly.engine.api.resource.adapter.ResourceAdapter;
import com.lightningfirefly.game.domain.DomainObject;
import com.lightningfirefly.game.domain.DomainObjectRegistry;
import com.lightningfirefly.game.domain.EcsComponent;
import com.lightningfirefly.game.domain.EcsEntityId;
import com.lightningfirefly.game.domain.SnapshotObserver;
import com.lightningfirefly.game.engine.GameFactory;
import com.lightningfirefly.game.engine.orchestrator.GameOrchestratorImpl;
import com.lightningfirefly.game.engine.GameScene;
import com.lightningfirefly.game.engine.orchestrator.WatchedPropertyUpdate;
import com.lightningfirefly.game.engine.renderer.GameRenderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GameOrchestratorImpl")
class GameOrchestratorImplTest {

    private MockMatchAdapter matchAdapter;
    private MockModuleAdapter moduleAdapter;
    private MockResourceAdapter resourceAdapter;
    private MockGameMasterAdapter gameMasterAdapter;
    private MockSimulationAdapter simulationAdapter;
    private MockSnapshotSubscriber snapshotSubscriber;
    private MockGameRenderer gameRenderer;
    private GameOrchestratorImpl orchestrator;
    private Path tempCacheDir;

    @BeforeEach
    void setUp() throws IOException {
        DomainObjectRegistry.getInstance().clear();
        matchAdapter = new MockMatchAdapter();
        moduleAdapter = new MockModuleAdapter();
        resourceAdapter = new MockResourceAdapter();
        gameMasterAdapter = new MockGameMasterAdapter();
        simulationAdapter = new MockSimulationAdapter();
        snapshotSubscriber = new MockSnapshotSubscriber();
        gameRenderer = new MockGameRenderer();
        tempCacheDir = Files.createTempDirectory("game-cache-test");
        orchestrator = new GameOrchestratorImpl(
                matchAdapter,
                moduleAdapter,
                resourceAdapter,
                gameMasterAdapter,
                simulationAdapter,
                snapshotSubscriber,
                new SnapshotObserver(),
                gameRenderer,
                tempCacheDir
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        DomainObjectRegistry.getInstance().clear();
        orchestrator.shutdown();
        // Clean up temp directory
        if (tempCacheDir != null && Files.exists(tempCacheDir)) {
            Files.walk(tempCacheDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Nested
    @DisplayName("startGame")
    class StartGame {

        @Test
        @DisplayName("should create match via adapter")
        void shouldCreateMatch() {
            GameFactory module = new TestGameFactory();

            orchestrator.startGame(module);

            assertThat(matchAdapter.createMatchCalled).isTrue();
            assertThat(matchAdapter.lastEnabledModules).containsExactly("MoveModule", "SpawnModule", "RenderModule");
        }

        @Test
        @DisplayName("should subscribe to snapshots with match ID")
        void shouldSubscribeToSnapshots() {
            GameFactory module = new TestGameFactory();

            orchestrator.startGame(module);

            assertThat(snapshotSubscriber.subscribeCalled).isTrue();
            assertThat(snapshotSubscriber.lastMatchId).isEqualTo(1L); // Mock returns matchId 1
        }

        @Test
        @DisplayName("should not auto-start renderer (caller is responsible)")
        void shouldNotAutoStartRenderer() {
            GameFactory module = new TestGameFactory();

            orchestrator.startGame(module);

            // Renderer is NOT auto-started - caller (e.g., GameApplication) is responsible
            // for starting the renderer on the appropriate thread (main thread on macOS)
            assertThat(gameRenderer.startAsyncCalled).isFalse();
        }

        @Test
        @DisplayName("should track active session")
        void shouldTrackSession() {
            GameFactory module = new TestGameFactory();

            orchestrator.startGame(module);

            assertThat(orchestrator.isGameRunning(module)).isTrue();
            GameOrchestratorImpl.GameSession session = orchestrator.getSession(module);
            assertThat(session).isNotNull();
            assertThat(session.matchId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw on adapter failure")
        void shouldThrowOnAdapterFailure() {
            matchAdapter.shouldFail = true;
            GameFactory module = new TestGameFactory();

            assertThatThrownBy(() -> orchestrator.startGame(module))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to start game");
        }

        @Test
        @DisplayName("should work without renderer")
        void shouldWorkWithoutRenderer() {
            orchestrator = new GameOrchestratorImpl(
                    matchAdapter,
                    moduleAdapter,
                    resourceAdapter,
                    gameMasterAdapter,
                    simulationAdapter,
                    snapshotSubscriber,
                    null // no renderer
            );
            GameFactory module = new TestGameFactory();

            orchestrator.startGame(module);

            assertThat(orchestrator.isGameRunning(module)).isTrue();
        }
    }

    @Nested
    @DisplayName("installGame")
    class InstallGame {

        @Test
        @DisplayName("should upload resources to server")
        void shouldUploadResources() {
            GameFactory module = new TestGameFactoryWithResources();

            orchestrator.installGame(module);

            assertThat(resourceAdapter.uploadCalled).isTrue();
            assertThat(resourceAdapter.uploadedResources).hasSize(2);
            assertThat(resourceAdapter.uploadedResources.get("player.png")).isNotNull();
            assertThat(resourceAdapter.uploadedResources.get("enemy.png")).isNotNull();
        }

        @Test
        @DisplayName("should upload game master JAR if bundled")
        void shouldUploadGameMasterJar() {
            GameFactory module = new TestGameFactoryWithGameMaster();

            orchestrator.installGame(module);

            assertThat(gameMasterAdapter.uploadCalled).isTrue();
            assertThat(gameMasterAdapter.lastUploadedFileName).isEqualTo("TestGameMaster.jar");
        }

        @Test
        @DisplayName("should not upload game master if already installed")
        void shouldNotUploadIfAlreadyInstalled() {
            gameMasterAdapter.installedGameMasters.add("TestGameMaster");
            GameFactory module = new TestGameFactoryWithGameMaster();

            orchestrator.installGame(module);

            assertThat(gameMasterAdapter.uploadCalled).isFalse();
        }

        @Test
        @DisplayName("should track installation info")
        void shouldTrackInstallationInfo() {
            GameFactory module = new TestGameFactoryWithResources();

            orchestrator.installGame(module);

            assertThat(orchestrator.isGameInstalled(module)).isTrue();
            Map<String, Long> resourceMapping = orchestrator.getResourceIdMapping(module);
            assertThat(resourceMapping).containsKey("player.png");
            assertThat(resourceMapping).containsKey("enemy.png");
        }

        @Test
        @DisplayName("should throw on upload failure")
        void shouldThrowOnUploadFailure() {
            resourceAdapter.shouldFail = true;
            GameFactory module = new TestGameFactoryWithResources();

            assertThatThrownBy(() -> orchestrator.installGame(module))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to install game");
        }
    }

    @Nested
    @DisplayName("uninstallGame")
    class UninstallGame {

        @Test
        @DisplayName("should delete uploaded resources")
        void shouldDeleteResources() {
            GameFactory module = new TestGameFactoryWithResources();
            orchestrator.installGame(module);

            orchestrator.uninstallGame(module);

            assertThat(resourceAdapter.deletedResourceIds).hasSize(2);
        }

        @Test
        @DisplayName("should uninstall game master if we installed it")
        void shouldUninstallGameMaster() {
            GameFactory module = new TestGameFactoryWithGameMaster();
            orchestrator.installGame(module);

            orchestrator.uninstallGame(module);

            assertThat(gameMasterAdapter.uninstallCalled).isTrue();
            assertThat(gameMasterAdapter.lastUninstalledName).isEqualTo("TestGameMaster");
        }

        @Test
        @DisplayName("should remove installation tracking")
        void shouldRemoveInstallationTracking() {
            GameFactory module = new TestGameFactoryWithResources();
            orchestrator.installGame(module);

            orchestrator.uninstallGame(module);

            assertThat(orchestrator.isGameInstalled(module)).isFalse();
        }
    }

    @Nested
    @DisplayName("Resource caching")
    class ResourceCaching {

        @Test
        @DisplayName("should download resource when RESOURCE_ID found in snapshot")
        void shouldDownloadResourceFromSnapshot() throws Exception {
            // Add a downloadable resource to the mock
            resourceAdapter.addDownloadableResource(42L, "texture.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch downloadLatch = new CountDownLatch(1);
            AtomicReference<GameOrchestratorImpl.ResourceDownloadEvent> eventRef = new AtomicReference<>();

            orchestrator.setResourceDownloadListener(event -> {
                eventRef.set(event);
                downloadLatch.countDown();
            });

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            // Simulate snapshot with RESOURCE_ID
            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "RESOURCE_ID", List.of(42.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            // Wait for async download
            boolean completed = downloadLatch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // Verify download completed
            GameOrchestratorImpl.ResourceDownloadEvent event = eventRef.get();
            assertThat(event.status()).isEqualTo(GameOrchestratorImpl.ResourceDownloadEvent.Status.COMPLETED);
            assertThat(event.resourceId()).isEqualTo(42L);
            assertThat(event.localPath()).isNotNull();
            assertThat(Files.exists(event.localPath())).isTrue();
        }

        @Test
        @DisplayName("should not re-download already cached resources")
        void shouldNotReDownloadCachedResources() throws Exception {
            resourceAdapter.addDownloadableResource(42L, "texture.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch firstLatch = new CountDownLatch(1);
            orchestrator.setResourceDownloadListener(event -> firstLatch.countDown());

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            // First snapshot triggers download
            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(42.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);
            firstLatch.await(5, TimeUnit.SECONDS);

            // Reset download count
            int downloadCountBefore = resourceAdapter.downloadCount;

            // Second snapshot with same resource
            snapshotSubscriber.simulateSnapshot(snapshot);

            // Small delay to ensure async would have run
            Thread.sleep(100);

            // Should not have downloaded again
            assertThat(resourceAdapter.downloadCount).isEqualTo(downloadCountBefore);
        }

        @Test
        @DisplayName("should download multiple resources from snapshot")
        void shouldDownloadMultipleResources() throws Exception {
            resourceAdapter.addDownloadableResource(10L, "sprite1.png", "TEXTURE", new byte[]{1});
            resourceAdapter.addDownloadableResource(20L, "sprite2.png", "TEXTURE", new byte[]{2});
            resourceAdapter.addDownloadableResource(30L, "sprite3.png", "TEXTURE", new byte[]{3});

            CountDownLatch downloadLatch = new CountDownLatch(3);
            orchestrator.setResourceDownloadListener(event -> downloadLatch.countDown());

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(10.0f, 20.0f, 30.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            boolean completed = downloadLatch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            assertThat(orchestrator.isResourceCached(10L)).isTrue();
            assertThat(orchestrator.isResourceCached(20L)).isTrue();
            assertThat(orchestrator.isResourceCached(30L)).isTrue();
        }

        @Test
        @DisplayName("should track cached resource paths")
        void shouldTrackCachedResourcePaths() throws Exception {
            resourceAdapter.addDownloadableResource(42L, "texture.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch downloadLatch = new CountDownLatch(1);
            orchestrator.setResourceDownloadListener(event -> downloadLatch.countDown());

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(42.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);
            downloadLatch.await(5, TimeUnit.SECONDS);

            Path cachedPath = orchestrator.getCachedResourcePath(42L);
            assertThat(cachedPath).isNotNull();
            assertThat(cachedPath.getFileName().toString()).isEqualTo("texture.png");
        }

        @Test
        @DisplayName("should ignore zero and negative resource IDs")
        void shouldIgnoreInvalidResourceIds() throws Exception {
            CountDownLatch downloadLatch = new CountDownLatch(1);
            AtomicBoolean downloadTriggered = new AtomicBoolean(false);
            orchestrator.setResourceDownloadListener(event -> {
                downloadTriggered.set(true);
                downloadLatch.countDown();
            });

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(0.0f, -1.0f, -100.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            // Wait briefly to see if download is triggered
            boolean timedOut = !downloadLatch.await(200, TimeUnit.MILLISECONDS);
            assertThat(timedOut).isTrue();
            assertThat(downloadTriggered.get()).isFalse();
        }

        @Test
        @DisplayName("should handle download failure gracefully")
        void shouldHandleDownloadFailure() throws Exception {
            // Don't add resource to mock - will cause download to fail

            CountDownLatch downloadLatch = new CountDownLatch(1);
            AtomicReference<GameOrchestratorImpl.ResourceDownloadEvent> eventRef = new AtomicReference<>();

            orchestrator.setResourceDownloadListener(event -> {
                eventRef.set(event);
                downloadLatch.countDown();
            });

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(999.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            boolean completed = downloadLatch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            GameOrchestratorImpl.ResourceDownloadEvent event = eventRef.get();
            assertThat(event.status()).isEqualTo(GameOrchestratorImpl.ResourceDownloadEvent.Status.FAILED);
            assertThat(event.error()).isNotNull();
        }

        @Test
        @DisplayName("should clear resource cache")
        void shouldClearResourceCache() throws Exception {
            resourceAdapter.addDownloadableResource(42L, "texture.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch downloadLatch = new CountDownLatch(1);
            orchestrator.setResourceDownloadListener(event -> downloadLatch.countDown());

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(42.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);
            downloadLatch.await(5, TimeUnit.SECONDS);

            assertThat(orchestrator.isResourceCached(42L)).isTrue();

            orchestrator.clearResourceCache();

            assertThat(orchestrator.isResourceCached(42L)).isFalse();
            assertThat(orchestrator.getCachedResourcePath(42L)).isNull();
        }

        @Test
        @DisplayName("should use cache directory for downloaded files")
        void shouldUseCacheDirectory() throws Exception {
            resourceAdapter.addDownloadableResource(42L, "texture.png", "TEXTURE", new byte[]{1, 2, 3});

            CountDownLatch downloadLatch = new CountDownLatch(1);
            orchestrator.setResourceDownloadListener(event -> downloadLatch.countDown());

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "RenderModule",
                    Map.of("RESOURCE_ID", List.of(42.0f))
            );
            snapshotSubscriber.simulateSnapshot(snapshot);
            downloadLatch.await(5, TimeUnit.SECONDS);

            Path cachedPath = orchestrator.getCachedResourcePath(42L);
            assertThat(cachedPath.getParent()).isEqualTo(tempCacheDir);
        }
    }

    @Nested
    @DisplayName("stopGame")
    class StopGame {

        @Test
        @DisplayName("should unsubscribe from snapshots")
        void shouldUnsubscribe() {
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            orchestrator.stopGame(module);

            assertThat(snapshotSubscriber.unsubscribeCalled).isTrue();
        }

        @Test
        @DisplayName("should delete match via adapter")
        void shouldDeleteMatch() {
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            orchestrator.stopGame(module);

            assertThat(matchAdapter.deleteMatchCalled).isTrue();
            assertThat(matchAdapter.lastDeletedMatchId).isEqualTo(1L);
        }

        @Test
        @DisplayName("should stop renderer")
        void shouldStopRenderer() {
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            orchestrator.stopGame(module);

            assertThat(gameRenderer.stopCalled).isTrue();
        }

        @Test
        @DisplayName("should remove session from active sessions")
        void shouldRemoveSession() {
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            orchestrator.stopGame(module);

            assertThat(orchestrator.isGameRunning(module)).isFalse();
            assertThat(orchestrator.getSession(module)).isNull();
        }

        @Test
        @DisplayName("should not fail if game not running")
        void shouldNotFailIfNotRunning() {
            GameFactory module = new TestGameFactory();

            // Should not throw
            orchestrator.stopGame(module);

            assertThat(matchAdapter.deleteMatchCalled).isFalse();
        }
    }

    @Nested
    @DisplayName("registerWatch")
    class RegisterWatch {

        @Test
        @DisplayName("should invoke callback when snapshot received")
        void shouldInvokeCallbackOnSnapshot() {
            AtomicReference<Float> receivedValue = new AtomicReference<>();
            WatchedPropertyUpdate watch = new WatchedPropertyUpdate(
                    "MoveModule.POSITION_X",
                    1L,
                    receivedValue::set
            );

            orchestrator.registerWatch(watch);

            // Start game to get the snapshot callback
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            // Simulate snapshot
            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "POSITION_X", List.of(100.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(receivedValue.get()).isEqualTo(100.0f);
        }

        @Test
        @DisplayName("should not invoke callback for wrong entity")
        void shouldNotInvokeForWrongEntity() {
            AtomicBoolean called = new AtomicBoolean(false);
            WatchedPropertyUpdate watch = new WatchedPropertyUpdate(
                    "MoveModule.POSITION_X",
                    999L, // Different entity
                    value -> called.set(true)
            );

            orchestrator.registerWatch(watch);
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "POSITION_X", List.of(100.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(called.get()).isFalse();
        }

        @Test
        @DisplayName("should handle multiple watches")
        void shouldHandleMultipleWatches() {
            AtomicReference<Float> posX = new AtomicReference<>();
            AtomicReference<Float> posY = new AtomicReference<>();

            orchestrator.registerWatch(new WatchedPropertyUpdate(
                    "MoveModule.POSITION_X", 1L, posX::set));
            orchestrator.registerWatch(new WatchedPropertyUpdate(
                    "MoveModule.POSITION_Y", 1L, posY::set));

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "POSITION_X", List.of(10.0f),
                            "POSITION_Y", List.of(20.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(posX.get()).isEqualTo(10.0f);
            assertThat(posY.get()).isEqualTo(20.0f);
        }
    }

    @Nested
    @DisplayName("unregisterWatch")
    class UnregisterWatch {

        @Test
        @DisplayName("should stop invoking callback after unregister")
        void shouldStopInvoking() {
            AtomicReference<Float> receivedValue = new AtomicReference<>();
            WatchedPropertyUpdate watch = new WatchedPropertyUpdate(
                    "MoveModule.POSITION_X",
                    1L,
                    receivedValue::set
            );

            orchestrator.registerWatch(watch);
            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            // Unregister
            orchestrator.unregisterWatch(watch);

            // Simulate snapshot
            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "POSITION_X", List.of(100.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(receivedValue.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Domain object integration")
    class DomainObjectIntegration {

        @Test
        @DisplayName("should update domain objects on snapshot")
        void shouldUpdateDomainObjects() {
            TestPlayer player = new TestPlayer(1);

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f),
                            "POSITION_X", List.of(50.0f),
                            "POSITION_Y", List.of(75.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(player.positionX).isEqualTo(50.0f);
            assertThat(player.positionY).isEqualTo(75.0f);

            player.dispose();
        }

        @Test
        @DisplayName("should update multiple domain objects")
        void shouldUpdateMultipleDomainObjects() {
            TestPlayer player1 = new TestPlayer(1);
            TestPlayer player2 = new TestPlayer(2);

            GameFactory module = new TestGameFactory();
            orchestrator.startGame(module);

            Map<String, Map<String, List<Float>>> snapshot = createSnapshot(
                    "MoveModule",
                    Map.of(
                            "ENTITY_ID", List.of(1.0f, 2.0f),
                            "POSITION_X", List.of(10.0f, 20.0f),
                            "POSITION_Y", List.of(11.0f, 21.0f)
                    )
            );
            snapshotSubscriber.simulateSnapshot(snapshot);

            assertThat(player1.positionX).isEqualTo(10.0f);
            assertThat(player1.positionY).isEqualTo(11.0f);
            assertThat(player2.positionX).isEqualTo(20.0f);
            assertThat(player2.positionY).isEqualTo(21.0f);

            player1.dispose();
            player2.dispose();
        }
    }

    // Helper methods

    private Map<String, Map<String, List<Float>>> createSnapshot(String moduleName, Map<String, List<Float>> moduleData) {
        Map<String, Map<String, List<Float>>> snapshot = new HashMap<>();
        snapshot.put(moduleName, new HashMap<>(moduleData));
        return snapshot;
    }

    // Test implementations

    static class TestGameFactory implements GameFactory {
        @Override
        public void attachScene(GameScene scene) {
        }
    }

    static class TestGameFactoryWithResources implements GameFactory {
        @Override
        public void attachScene(GameScene scene) {
        }

        @Override
        public List<GameResource> getResources() {
            return List.of(
                    new GameResource("player.png", "TEXTURE", new byte[]{1, 2, 3}),
                    new GameResource("enemy.png", "TEXTURE", new byte[]{4, 5, 6})
            );
        }
    }

    static class TestGameFactoryWithGameMaster implements GameFactory {
        @Override
        public void attachScene(GameScene scene) {
        }

        @Override
        public String getGameMasterName() {
            return "TestGameMaster";
        }

        @Override
        public byte[] getGameMasterJar() {
            return new byte[]{7, 8, 9, 10};
        }
    }

    static class TestPlayer extends DomainObject {
        @EcsEntityId
        long entityId;

        @EcsComponent(componentPath = "MoveModule.POSITION_X")
        float positionX;

        @EcsComponent(componentPath = "MoveModule.POSITION_Y")
        float positionY;

        TestPlayer(long entityId) {
            super(entityId);
        }
    }

    // Mock implementations

    static class MockMatchAdapter implements MatchAdapter {
        boolean createMatchCalled = false;
        boolean deleteMatchCalled = false;
        boolean shouldFail = false;
        List<String> lastEnabledModules;
        List<String> lastEnabledGameMasters;
        long lastDeletedMatchId;

        @Override
        public MatchResponse createMatch(List<String> enabledModules) throws IOException {
            return createMatchWithGameMasters(enabledModules, List.of());
        }

        @Override
        public MatchResponse createMatchWithGameMasters(List<String> enabledModules, List<String> enabledGameMasters) throws IOException {
            if (shouldFail) {
                throw new IOException("Mock failure");
            }
            createMatchCalled = true;
            lastEnabledModules = enabledModules;
            lastEnabledGameMasters = enabledGameMasters;
            return new MatchResponse(1L, enabledModules);
        }

        @Override
        public java.util.Optional<MatchResponse> getMatch(long matchId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<MatchResponse> getAllMatches() {
            return List.of();
        }

        @Override
        public boolean deleteMatch(long matchId) throws IOException {
            deleteMatchCalled = true;
            lastDeletedMatchId = matchId;
            return true;
        }
    }

    static class MockSimulationAdapter implements com.lightningfirefly.engine.api.resource.adapter.SimulationAdapter {
        long tick = 0;
        int advanceTickCalled = 0;

        @Override
        public long getCurrentTick() {
            return tick;
        }

        @Override
        public long advanceTick() {
            advanceTickCalled++;
            return ++tick;
        }
    }

    static class MockModuleAdapter implements ModuleAdapter {
        @Override
        public List<ModuleResponse> getAllModules() {
            return List.of();
        }

        @Override
        public java.util.Optional<ModuleResponse> getModule(String moduleName) {
            return java.util.Optional.empty();
        }

        @Override
        public List<ModuleResponse> uploadModule(String fileName, byte[] jarData) {
            return List.of();
        }

        @Override
        public boolean uninstallModule(String moduleName) {
            return false;
        }

        @Override
        public List<ModuleResponse> reloadModules() {
            return List.of();
        }
    }

    static class MockResourceAdapter implements ResourceAdapter {
        boolean uploadCalled = false;
        boolean shouldFail = false;
        Map<String, Long> uploadedResources = new HashMap<>();
        List<Long> deletedResourceIds = new ArrayList<>();
        private long nextResourceId = 1L;

        // For resource caching tests
        Map<Long, com.lightningfirefly.engine.api.resource.Resource> downloadableResources = new HashMap<>();
        int downloadCount = 0;

        void addDownloadableResource(long id, String name, String type, byte[] data) {
            downloadableResources.put(id, new com.lightningfirefly.engine.api.resource.Resource(id, data, type, name));
        }

        @Override
        public long uploadResource(String resourceName, String resourceType, byte[] data) throws IOException {
            if (shouldFail) {
                throw new IOException("Mock upload failure");
            }
            uploadCalled = true;
            long resourceId = nextResourceId++;
            uploadedResources.put(resourceName, resourceId);
            return resourceId;
        }

        @Override
        public java.util.Optional<com.lightningfirefly.engine.api.resource.Resource> downloadResource(long resourceId) {
            downloadCount++;
            return java.util.Optional.ofNullable(downloadableResources.get(resourceId));
        }

        @Override
        public byte[] downloadChunk(long resourceId, int chunkIndex, int chunkSize) {
            return new byte[0];
        }

        @Override
        public int getTotalChunks(long resourceId, int chunkSize) {
            return 0;
        }

        @Override
        public List<com.lightningfirefly.engine.api.resource.Resource> listResources() {
            return List.of();
        }

        @Override
        public boolean deleteResource(long resourceId) {
            deletedResourceIds.add(resourceId);
            return true;
        }
    }

    static class MockGameMasterAdapter implements GameMasterAdapter {
        boolean uploadCalled = false;
        boolean uninstallCalled = false;
        String lastUploadedFileName;
        String lastUninstalledName;
        List<String> installedGameMasters = new ArrayList<>();

        @Override
        public List<GameMasterResponse> getAllGameMasters() {
            return installedGameMasters.stream()
                    .map(name -> new GameMasterResponse(name, 0))
                    .toList();
        }

        @Override
        public java.util.Optional<GameMasterResponse> getGameMaster(String gameMasterName) {
            if (installedGameMasters.contains(gameMasterName)) {
                return java.util.Optional.of(new GameMasterResponse(gameMasterName, 0));
            }
            return java.util.Optional.empty();
        }

        @Override
        public List<GameMasterResponse> uploadGameMaster(String fileName, byte[] jarData) {
            uploadCalled = true;
            lastUploadedFileName = fileName;
            String name = fileName.replace(".jar", "");
            installedGameMasters.add(name);
            return getAllGameMasters();
        }

        @Override
        public boolean uninstallGameMaster(String gameMasterName) {
            uninstallCalled = true;
            lastUninstalledName = gameMasterName;
            return installedGameMasters.remove(gameMasterName);
        }

        @Override
        public List<GameMasterResponse> reloadGameMasters() {
            return getAllGameMasters();
        }
    }

    static class MockSnapshotSubscriber implements GameOrchestratorImpl.SnapshotSubscriber {
        boolean subscribeCalled = false;
        boolean unsubscribeCalled = false;
        long lastMatchId;
        private Consumer<Map<String, Map<String, List<Float>>>> callback;

        @Override
        public Runnable subscribe(long matchId, Consumer<Map<String, Map<String, List<Float>>>> callback) {
            subscribeCalled = true;
            lastMatchId = matchId;
            this.callback = callback;
            return () -> unsubscribeCalled = true;
        }

        void simulateSnapshot(Map<String, Map<String, List<Float>>> snapshot) {
            if (callback != null) {
                callback.accept(snapshot);
            }
        }
    }

    static class MockGameRenderer implements GameRenderer {
        boolean startAsyncCalled = false;
        boolean stopCalled = false;

        @Override
        public void setControlSystem(com.lightningfirefly.game.engine.ControlSystem controlSystem) {
        }

        @Override
        public void setSpriteMapper(SpriteMapper mapper) {
        }

        @Override
        public void renderSnapshot(Object snapshot) {
        }

        @Override
        public void renderSprites(List<com.lightningfirefly.game.engine.Sprite> sprites) {
        }

        @Override
        public void start(Runnable onUpdate) {
        }

        @Override
        public void startAsync(Runnable onUpdate) {
            startAsyncCalled = true;
        }

        @Override
        public void runFrames(int frames, Runnable onUpdate) {
        }

        @Override
        public void stop() {
            stopCalled = true;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public int getWidth() {
            return 800;
        }

        @Override
        public int getHeight() {
            return 600;
        }

        @Override
        public void setOnError(Consumer<Exception> handler) {
        }

        @Override
        public void dispose() {
        }
    }
}
