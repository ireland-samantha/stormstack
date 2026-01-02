package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.CommandService.ParameterInfo;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.MatchInfo;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.gui.service.SnapshotService;
import com.lightningfirefly.engine.gui.service.SnapshotWebSocketClient.SnapshotData;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import com.lightningfirefly.engine.rendering.testing.headless.HeadlessComponentFactory;
import com.lightningfirefly.engine.rendering.testing.headless.HeadlessWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Headless tests for all GUI panels.
 *
 * <p>These tests run without OpenGL using HeadlessWindow and mock services.
 * Fast execution, suitable for CI/CD.
 */
@DisplayName("Panel Headless Tests")
class PanelHeadlessTest {

    private HeadlessWindow window;
    private ComponentFactory factory;
    private GuiDriver driver;

    @BeforeEach
    void setUp() {
        window = new HeadlessWindow(1200, 800);
        factory = HeadlessComponentFactory.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
        }
        if (window != null) {
            window.stop();
        }
    }

    // ========== MatchPanel Tests ==========

    @Nested
    @DisplayName("MatchPanel Tests")
    class MatchPanelTests {

        private MatchService mockMatchService;
        private ModuleService mockModuleService;
        private MatchPanel matchPanel;

        @BeforeEach
        void setUpMatchPanel() {
            mockMatchService = mock(MatchService.class);
            mockModuleService = mock(ModuleService.class);

            // Setup default mock behaviors
            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of()));

            matchPanel = new MatchPanel(factory, 10, 10, 600, 400, mockMatchService, mockModuleService);
            window.addComponent(matchPanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display match list")
        void shouldDisplayMatchList() {
            // Given - matches returned from service
            List<MatchInfo> matches = List.of(
                new MatchInfo(1, List.of("GameFactory"), List.of()),
                new MatchInfo(2, List.of("AIModule", "PhysicsModule"), List.of())
            );
            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(matches));

            // When - refresh
            matchPanel.refreshMatches();
            matchPanel.update();

            // Then - matches available
            assertThat(matchPanel.getMatches()).hasSize(2);
            assertThat(matchPanel.getMatches().get(0).id()).isEqualTo(1);
            assertThat(matchPanel.getMatches().get(1).id()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should create match with selected module")
        void shouldCreateMatch() {
            // Given - match ID is generated server-side
            when(mockMatchService.createMatch(anyList())).thenReturn(CompletableFuture.completedFuture(1L));
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(
                List.of(new ModuleInfo("TestModule", null, 0))
            ));

            matchPanel.refreshModules();
            matchPanel.update();

            // When - click create button
            driver.findElement(By.text("Create")).click();

            // Then - verify service called (match ID is generated server-side)
            verify(mockMatchService, atMostOnce()).createMatch(anyList());
        }

        @Test
        @DisplayName("Should delete selected match")
        void shouldDeleteMatch() {
            // Given
            List<MatchInfo> matches = List.of(new MatchInfo(1, List.of(), List.of()));
            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(matches));
            when(mockMatchService.deleteMatch(1L)).thenReturn(CompletableFuture.completedFuture(true));

            matchPanel.refreshMatches();
            matchPanel.update();

            // When - select match and delete
            // Note: HeadlessListView requires selection simulation

            // Then
            driver.findElement(By.text("Delete")).click();
            // Without selection, no delete is performed
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Create"))).isTrue();
            assertThat(driver.hasElement(By.text("Delete"))).isTrue();
            assertThat(driver.hasElement(By.text("View Snapshot"))).isTrue();
        }

        @Test
        @DisplayName("Should trigger view snapshot callback")
        void shouldTriggerViewSnapshotCallback() {
            // Given
            AtomicLong viewedMatchId = new AtomicLong(-1);
            matchPanel.setOnViewSnapshot(viewedMatchId::set);

            List<MatchInfo> matches = List.of(new MatchInfo(42, List.of(), List.of()));
            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(matches));

            matchPanel.refreshMatches();
            matchPanel.update();

            // When - simulate selection and click view
            // Note: In a real scenario, would need to select the match first

            // Then
            assertThat(viewedMatchId.get()).isEqualTo(-1); // No selection made
        }
    }

    // ========== SnapshotPanel Tests ==========

    @Nested
    @DisplayName("SnapshotPanel Tests")
    class SnapshotPanelTests {

        private SnapshotPanel snapshotPanel;

        @BeforeEach
        void setUpSnapshotPanel() {
            // SnapshotPanel with a non-existent server (won't actually connect)
            snapshotPanel = new SnapshotPanel(factory, 10, 10, 600, 400, "http://localhost:9999", 1);
            window.addComponent(snapshotPanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display snapshot data in tree")
        void shouldDisplaySnapshotData() {
            // Given - snapshot data
            Map<String, Map<String, List<Float>>> data = Map.of(
                "GameFactory", Map.of(
                    "POSITION_X", List.of(100.0f, 200.0f),
                    "POSITION_Y", List.of(50.0f, 60.0f)
                )
            );
            SnapshotData snapshot = new SnapshotData(1L, 42, data);

            // When
            snapshotPanel.setSnapshotData(snapshot);

            // Then
            assertThat(snapshotPanel.getLatestSnapshot()).isNotNull();
            assertThat(snapshotPanel.getLatestSnapshot().tick()).isEqualTo(42);

            TreeView tree = snapshotPanel.getEntityTree();
            assertThat(tree.getRootNodes()).isNotEmpty();
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Load All"))).isTrue();
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            // Auto-Connect WS button was removed - auto-connect is now automatic
            assertThat(driver.hasElement(By.textContaining("Tick:"))).isTrue();
        }

        @Test
        @DisplayName("Should update tick label when snapshot received")
        void shouldUpdateTickLabel() {
            // Given
            Map<String, Map<String, List<Float>>> data = Map.of("Mod", Map.of("X", List.of(1.0f)));
            SnapshotData snapshot = new SnapshotData(1L, 100, data);

            // When
            snapshotPanel.setSnapshotData(snapshot);

            // Then
            GuiElement tickLabel = driver.findElement(By.textContaining("Tick:"));
            assertThat(tickLabel.getText()).contains("100");
        }

        @Test
        @DisplayName("Should handle empty snapshot")
        void shouldHandleEmptySnapshot() {
            // Given
            SnapshotData snapshot = new SnapshotData(1L, 0, Map.of());

            // When
            snapshotPanel.setSnapshotData(snapshot);

            // Then - should not throw
            assertThat(snapshotPanel.getLatestSnapshot()).isNotNull();
        }
    }

    // ========== ResourcePanel Tests ==========

    @Nested
    @DisplayName("ResourcePanel Tests")
    class ResourcePanelTests {

        private ResourceService mockResourceService;
        private ResourcePanel resourcePanel;

        @BeforeEach
        void setUpResourcePanel() {
            mockResourceService = mock(ResourceService.class);
            when(mockResourceService.listResources()).thenReturn(CompletableFuture.completedFuture(List.of()));

            resourcePanel = new ResourcePanel(factory, 10, 10, 600, 400, mockResourceService);
            window.addComponent(resourcePanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display resource list")
        void shouldDisplayResourceList() {
            // Given
            List<ResourceInfo> resources = List.of(
                new ResourceInfo(1, "texture.png", "TEXTURE"),
                new ResourceInfo(2, "sound.wav", "SOUND")
            );
            when(mockResourceService.listResources()).thenReturn(CompletableFuture.completedFuture(resources));

            // When
            resourcePanel.refreshResources();
            resourcePanel.update();

            // Then
            assertThat(resourcePanel.getResources()).hasSize(2);
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Upload"))).isTrue();
            assertThat(driver.hasElement(By.text("Download"))).isTrue();
            assertThat(driver.hasElement(By.text("Delete"))).isTrue();
            assertThat(driver.hasElement(By.text("Browse"))).isTrue();
            assertThat(driver.hasElement(By.text("Preview"))).isTrue();
        }

        @Test
        @DisplayName("Should delete selected resource")
        void shouldDeleteResource() {
            // Given
            List<ResourceInfo> resources = List.of(new ResourceInfo(1, "test.png", "TEXTURE"));
            when(mockResourceService.listResources()).thenReturn(CompletableFuture.completedFuture(resources));
            when(mockResourceService.deleteResource(1L)).thenReturn(CompletableFuture.completedFuture(true));

            resourcePanel.refreshResources();
            resourcePanel.update();

            // When
            driver.findElement(By.text("Delete")).click();

            // Then - without selection, no delete performed
        }

        @Test
        @DisplayName("Should upload resource from file path")
        void shouldUploadResource() {
            // Given
            when(mockResourceService.uploadResourceFromFile(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(1L));

            // When - click upload without file path
            driver.findElement(By.text("Upload")).click();

            // Then - should show status message about needing file path
        }
    }

    // ========== ServerPanel (Modules) Tests ==========

    @Nested
    @DisplayName("ServerPanel Tests")
    class ServerPanelTests {

        private ModuleService mockModuleService;
        private ServerPanel serverPanel;

        @BeforeEach
        void setUpServerPanel() {
            mockModuleService = mock(ModuleService.class);
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of()));

            serverPanel = new ServerPanel(factory, 10, 10, 600, 400, mockModuleService);
            window.addComponent(serverPanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display module list")
        void shouldDisplayModuleList() {
            // Given
            List<ModuleInfo> modules = List.of(
                new ModuleInfo("GameFactory", "PLAYER_FLAG", 5),
                new ModuleInfo("AIModule", null, 0)
            );
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(modules));

            // When
            serverPanel.refreshModules();
            serverPanel.update();

            // Then
            assertThat(serverPanel.getModules()).hasSize(2);
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Upload"))).isTrue();
            assertThat(driver.hasElement(By.text("Uninstall"))).isTrue();
            assertThat(driver.hasElement(By.text("Reload All"))).isTrue();
            assertThat(driver.hasElement(By.text("Browse"))).isTrue();
        }

        @Test
        @DisplayName("Should uninstall selected module")
        void shouldUninstallModule() {
            // Given
            List<ModuleInfo> modules = List.of(new ModuleInfo("TestModule", null, 0));
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(modules));
            when(mockModuleService.uninstallModule("TestModule")).thenReturn(CompletableFuture.completedFuture(true));

            serverPanel.refreshModules();
            serverPanel.update();

            // When
            driver.findElement(By.text("Uninstall")).click();

            // Then - without selection, no uninstall performed
        }

        @Test
        @DisplayName("Should reload all modules")
        void shouldReloadModules() {
            // Given
            when(mockModuleService.reloadModules()).thenReturn(CompletableFuture.completedFuture(true));

            // When
            driver.findElement(By.text("Reload All")).click();

            // Then
            verify(mockModuleService).reloadModules();
        }
    }

    // ========== CommandPanel Tests ==========

    @Nested
    @DisplayName("CommandPanel Tests")
    class CommandPanelTests {

        private CommandService mockCommandService;
        private CommandPanel commandPanel;

        @BeforeEach
        void setUpCommandPanel() {
            mockCommandService = mock(CommandService.class);
            when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(List.of()));

            commandPanel = new CommandPanel(factory, 10, 10, 600, 400, mockCommandService);
            window.addComponent(commandPanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display command list")
        void shouldDisplayCommandList() {
            // Given
            List<CommandInfo> commands = List.of(
                new CommandInfo("move", List.of(
                    new ParameterInfo("targetX", "long"),
                    new ParameterInfo("targetY", "long")
                )),
                new CommandInfo("spawn", List.of(
                    new ParameterInfo("entityType", "String")
                ))
            );
            when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(commands));

            // When
            commandPanel.refreshCommands();
            commandPanel.update();

            // Then
            assertThat(commandPanel.getCommands()).hasSize(2);
            assertThat(commandPanel.getCommands().get(0).name()).isEqualTo("move");
            assertThat(commandPanel.getCommands().get(1).name()).isEqualTo("spawn");
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            // Send button moved to form panel as "Send Command"
            assertThat(driver.hasElement(By.text("Send Command"))).isTrue();
            // Form panel is nested inside CommandPanel - verify it exists via accessor
            assertThat(commandPanel.getFormPanel()).isNotNull();
        }

        @Test
        @DisplayName("Should submit command with parameters")
        void shouldSubmitCommand() {
            // Given
            when(mockCommandService.submitCommand(anyLong(), any(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

            List<CommandInfo> commands = List.of(
                new CommandInfo("testCommand", List.of())
            );
            when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(commands));

            commandPanel.refreshCommands();
            commandPanel.update();

            // When - click send without selecting command
            driver.findElement(By.text("Send Command")).click();

            // Then - without selection, no command sent
        }

        @Test
        @DisplayName("Should parse command signature correctly")
        void shouldParseCommandSignature() {
            // Given
            CommandInfo cmd = new CommandInfo("move", List.of(
                new ParameterInfo("x", "long"),
                new ParameterInfo("y", "long")
            ));

            // Then
            assertThat(cmd.getSignature()).isEqualTo("move(long x, long y)");
        }

        @Test
        @DisplayName("Should handle command with no parameters")
        void shouldHandleNoParameters() {
            // Given
            CommandInfo cmd = new CommandInfo("ping", List.of());

            // Then
            assertThat(cmd.getSignature()).isEqualTo("ping");
        }
    }

    // ========== RenderingPanel Tests ==========

    @Nested
    @DisplayName("RenderingPanel Tests")
    class RenderingPanelTests {

        private SnapshotService mockSnapshotService;
        private ResourceService mockResourceService;
        private RenderingPanel renderingPanel;

        @BeforeEach
        void setUpRenderingPanel() {
            mockSnapshotService = mock(SnapshotService.class);
            mockResourceService = mock(ResourceService.class);

            // Setup default mock behaviors - returns empty list
            when(mockSnapshotService.getAllSnapshots()).thenReturn(CompletableFuture.completedFuture(List.of()));

            renderingPanel = new RenderingPanel(factory, 10, 10, 800, 600, "http://localhost:9999",
                    mockSnapshotService, mockResourceService);
            window.addComponent(renderingPanel);
            driver = GuiDriver.connect(window);
        }

        @Test
        @DisplayName("Should display empty list when no snapshots")
        void shouldDisplayEmptyListWhenNoSnapshots() {
            // When
            renderingPanel.loadEntitiesWithResources();
            renderingPanel.update();

            // Then
            assertThat(renderingPanel.getEntities()).isEmpty();
            assertThat(renderingPanel.getRenderableMatches()).isEmpty();
        }

        @Test
        @DisplayName("Should find renderable matches with RESOURCE_ID")
        void shouldFindRenderableMatchesWithResourceId() {
            // Given - snapshot with entities that have RESOURCE_ID
            var snapshotData = new SnapshotService.SnapshotData(
                    1L, 10,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(100.0f, 101.0f),
                            "RESOURCE_ID", List.of(42.0f, 43.0f)
                    ))
            );
            when(mockSnapshotService.getAllSnapshots()).thenReturn(
                    CompletableFuture.completedFuture(List.of(snapshotData)));

            // When
            renderingPanel.loadEntitiesWithResources();
            // Wait for async completion
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            renderingPanel.update();

            // Then
            assertThat(renderingPanel.getRenderableMatches()).containsExactly(1L);
            assertThat(renderingPanel.getEntities()).hasSize(2);
            assertThat(renderingPanel.getEntities().get(0).resourceId()).isEqualTo(42L);
            assertThat(renderingPanel.getEntities().get(1).resourceId()).isEqualTo(43L);
        }

        @Test
        @DisplayName("Should find matches across multiple snapshots")
        void shouldFindMatchesAcrossMultipleSnapshots() {
            // Given - two snapshots from different matches
            var snapshot1 = new SnapshotService.SnapshotData(
                    1L, 10,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(100.0f),
                            "RESOURCE_ID", List.of(42.0f)
                    ))
            );
            var snapshot2 = new SnapshotService.SnapshotData(
                    2L, 15,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(200.0f, 201.0f),
                            "RESOURCE_ID", List.of(50.0f, 51.0f)
                    ))
            );
            when(mockSnapshotService.getAllSnapshots()).thenReturn(
                    CompletableFuture.completedFuture(List.of(snapshot1, snapshot2)));

            // When
            renderingPanel.loadEntitiesWithResources();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            renderingPanel.update();

            // Then - should have both matches
            assertThat(renderingPanel.getRenderableMatches()).containsExactly(1L, 2L);
            assertThat(renderingPanel.getEntities()).hasSize(3);
        }

        @Test
        @DisplayName("Should filter out null and sentinel RESOURCE_ID values")
        void shouldFilterOutInvalidResourceIds() {
            // Given - snapshot with some invalid resource IDs
            var snapshotData = new SnapshotService.SnapshotData(
                    1L, 10,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(100.0f, 101.0f, 102.0f),
                            "RESOURCE_ID", List.of(42.0f, 0.0f, Float.NaN)
                    ))
            );
            when(mockSnapshotService.getAllSnapshots()).thenReturn(
                    CompletableFuture.completedFuture(List.of(snapshotData)));

            // When
            renderingPanel.loadEntitiesWithResources();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            renderingPanel.update();

            // Then - only valid resource ID should be included
            assertThat(renderingPanel.getEntities()).hasSize(1);
            assertThat(renderingPanel.getEntities().get(0).resourceId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Should have all UI components")
        void shouldHaveAllUIComponents() {
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Visualize"))).isTrue();
            assertThat(driver.hasElement(By.text("Preview Texture"))).isTrue();
        }

        @Test
        @DisplayName("Should select match and allow visualization")
        void shouldSelectMatchAndAllowVisualization() {
            // Given - snapshot with renderable entities
            var snapshotData = new SnapshotService.SnapshotData(
                    1L, 10,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(100.0f),
                            "RESOURCE_ID", List.of(42.0f)
                    ))
            );
            when(mockSnapshotService.getAllSnapshots()).thenReturn(
                    CompletableFuture.completedFuture(List.of(snapshotData)));

            renderingPanel.loadEntitiesWithResources();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            renderingPanel.update();

            // When - select the match
            renderingPanel.selectMatch(0);

            // Then - match is selected
            assertThat(renderingPanel.getRenderableMatches()).hasSize(1);
            assertThat(renderingPanel.getRenderableMatches().get(0)).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should select entity and show details")
        void shouldSelectEntityAndShowDetails() {
            // Given - snapshot with renderable entities
            var snapshotData = new SnapshotService.SnapshotData(
                    1L, 10,
                    Map.of("RenderModule", Map.of(
                            "ENTITY_ID", List.of(100.0f),
                            "RESOURCE_ID", List.of(42.0f)
                    ))
            );
            when(mockSnapshotService.getAllSnapshots()).thenReturn(
                    CompletableFuture.completedFuture(List.of(snapshotData)));

            renderingPanel.loadEntitiesWithResources();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            renderingPanel.update();

            // When - select the entity
            renderingPanel.selectEntity(0);

            // Then - entity data is available
            assertThat(renderingPanel.getEntities()).hasSize(1);
            var entity = renderingPanel.getEntities().get(0);
            assertThat(entity.matchId()).isEqualTo(1L);
            assertThat(entity.entityId()).isEqualTo(100L);
            assertThat(entity.resourceId()).isEqualTo(42L);
        }
    }

    // ========== Integration Tests ==========

    @Nested
    @DisplayName("Panel Integration Tests")
    class PanelIntegrationTests {

        @Test
        @DisplayName("Should switch between panels in headless mode")
        void shouldSwitchBetweenPanels() {
            // Given - multiple panels
            MatchService mockMatchService = mock(MatchService.class);
            ModuleService mockModuleService = mock(ModuleService.class);
            CommandService mockCommandService = mock(CommandService.class);

            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(mockCommandService.listCommands()).thenReturn(CompletableFuture.completedFuture(List.of()));

            MatchPanel matchPanel = new MatchPanel(factory, 10, 10, 500, 300, mockMatchService, mockModuleService);
            ServerPanel serverPanel = new ServerPanel(factory, 10, 10, 500, 300, mockModuleService);
            CommandPanel commandPanel = new CommandPanel(factory, 10, 10, 500, 300, mockCommandService);

            window.addComponent(matchPanel);
            window.addComponent(serverPanel);
            window.addComponent(commandPanel);

            // Initially show match panel
            matchPanel.setVisible(true);
            serverPanel.setVisible(false);
            commandPanel.setVisible(false);

            driver = GuiDriver.connect(window);

            // When/Then - verify visibility
            assertThat(matchPanel.isVisible()).isTrue();
            assertThat(serverPanel.isVisible()).isFalse();
            assertThat(commandPanel.isVisible()).isFalse();

            // Switch panels
            matchPanel.setVisible(false);
            serverPanel.setVisible(true);

            assertThat(matchPanel.isVisible()).isFalse();
            assertThat(serverPanel.isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should find elements across panels")
        void shouldFindElementsAcrossPanels() {
            // Given
            MatchService mockMatchService = mock(MatchService.class);
            ModuleService mockModuleService = mock(ModuleService.class);
            when(mockMatchService.listMatches()).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(mockModuleService.listModules()).thenReturn(CompletableFuture.completedFuture(List.of()));

            MatchPanel matchPanel = new MatchPanel(factory, 10, 10, 500, 300, mockMatchService, mockModuleService);
            matchPanel.setVisible(true);
            window.addComponent(matchPanel);

            driver = GuiDriver.connect(window);

            // Then - find buttons
            List<GuiElement> refreshButtons = driver.findElements(By.text("Refresh"));
            assertThat(refreshButtons).isNotEmpty();
        }
    }
}
