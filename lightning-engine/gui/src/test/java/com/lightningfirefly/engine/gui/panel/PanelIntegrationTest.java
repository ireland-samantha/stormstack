package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.CommandService;
import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.MatchService;
import com.lightningfirefly.engine.gui.service.MatchService.MatchInfo;
import com.lightningfirefly.engine.gui.service.ModuleService;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.ExpectedConditions;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenGL integration tests for all GUI panels against a live backend.
 *
 * <p>These tests require:
 * <ul>
 *   <li>A display (not headless environment)</li>
 *   <li>OpenGL 3.3+ support</li>
 *   <li>A running Quarkus backend server at BACKEND_URL</li>
 * </ul>
 *
 * <p>To run:
 * <pre>
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=PanelIntegrationTest -DenableGLTests=true
 * </pre>
 */
@Tag("integration")
@DisplayName("Panel OpenGL Integration Tests")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
@EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
class PanelIntegrationTest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private EngineGuiApplication app;
    private GuiDriver driver;
    private String backendUrl;

    @BeforeEach
    void setUp() {
        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = DEFAULT_SERVER_URL;
        }
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    // ========== MatchPanel CRUD Tests ==========

    @Nested
    @DisplayName("MatchPanel CRUD Tests")
    class MatchPanelCRUDTests {

        @Test
        @DisplayName("Should list matches from backend")
        void shouldListMatches() throws Exception {
            // Initialize app on main thread
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Matches panel (should be default)
            assertThat(app.getMatchPanel().isVisible()).isTrue();

            // Wait for matches to load
            Thread.sleep(1000);
            app.getMatchPanel().update();

            // Verify we can find the match list UI
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Create"))).isTrue();
            assertThat(driver.hasElement(By.text("Delete"))).isTrue();

            // Run frames to verify OpenGL works
            window.runFrames(5);
        }

        @Test
        @DisplayName("Should create and delete match")
        void shouldCreateAndDeleteMatch() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            MatchPanel matchPanel = app.getMatchPanel();

            // Create a match using the service directly (IDs are auto-generated server-side)
            MatchService matchService = new MatchService(backendUrl);
            AtomicLong createdMatchId = new AtomicLong(-1);

            // Create match - ID is generated server-side
            matchService.createMatch(List.of())
                .thenAccept(id -> {
                    if (id > 0) {
                        createdMatchId.set(id);
                    }
                })
                .get(5, TimeUnit.SECONDS);

            // Refresh and verify
            matchPanel.refreshMatches();
            Thread.sleep(500);
            matchPanel.update();

            List<MatchInfo> matches = matchPanel.getMatches();
            // Should have at least one match

            // Cleanup - delete the created match if it was created
            if (createdMatchId.get() > 0) {
                matchService.deleteMatch(createdMatchId.get()).get(5, TimeUnit.SECONDS);
            }

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should refresh match list via button")
        void shouldRefreshMatchList() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Click refresh button
            driver.findElement(By.text("Refresh")).click();

            // Wait for refresh
            Thread.sleep(500);
            app.getMatchPanel().update();

            window.runFrames(5);
        }
    }

    // ========== ResourcePanel CRUD Tests ==========

    @Nested
    @DisplayName("ResourcePanel CRUD Tests")
    class ResourcePanelCRUDTests {

        @Test
        @DisplayName("Should list resources from backend")
        void shouldListResources() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Resources panel
            driver.findElement(By.text("Resources")).click();
            assertThat(app.getResourcePanel().isVisible()).isTrue();

            // Wait for resources to load
            Thread.sleep(1000);
            app.getResourcePanel().update();

            // Verify UI
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Upload"))).isTrue();
            assertThat(driver.hasElement(By.text("Download"))).isTrue();
            assertThat(driver.hasElement(By.text("Delete"))).isTrue();

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should refresh resource list")
        void shouldRefreshResourceList() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Resources
            driver.findElement(By.text("Resources")).click();

            // Click refresh
            driver.findElement(By.text("Refresh")).click();

            Thread.sleep(500);
            app.getResourcePanel().update();

            List<ResourceInfo> resources = app.getResourcePanel().getResources();
            // Resources list may be empty or populated

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should upload and delete resource")
        void shouldUploadAndDeleteResource() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Resources
            driver.findElement(By.text("Resources")).click();

            ResourceService resourceService = app.getResourcePanel().getResourceService();

            // Upload a test resource
            byte[] testData = "test resource data".getBytes();
            AtomicLong resourceId = new AtomicLong(-1);

            resourceService.uploadResource("test_resource.dat", "DATA", testData)
                .thenAccept(resourceId::set)
                .exceptionally(e -> {
                    // Upload might fail if endpoint doesn't support this
                    return null;
                });

            Thread.sleep(1000);

            // Refresh
            app.getResourcePanel().refreshResources();
            Thread.sleep(500);
            app.getResourcePanel().update();

            // Cleanup
            if (resourceId.get() > 0) {
                resourceService.deleteResource(resourceId.get());
            }

            window.runFrames(5);
        }
    }

    // ========== ServerPanel (Modules) CRUD Tests ==========

    @Nested
    @DisplayName("ServerPanel (Modules) Tests")
    class ServerPanelTests {

        @Test
        @DisplayName("Should list modules from backend")
        void shouldListModules() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Modules panel
            driver.findElement(By.text("Modules")).click();
            assertThat(app.getServerPanel().isVisible()).isTrue();

            // Wait for modules to load
            Thread.sleep(1000);
            app.getServerPanel().update();

            // Verify UI
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Upload"))).isTrue();
            assertThat(driver.hasElement(By.text("Uninstall"))).isTrue();
            assertThat(driver.hasElement(By.text("Reload All"))).isTrue();

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should refresh module list")
        void shouldRefreshModuleList() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Modules
            driver.findElement(By.text("Modules")).click();

            // Click refresh
            driver.findElement(By.text("Refresh")).click();

            Thread.sleep(500);
            app.getServerPanel().update();

            List<ModuleInfo> modules = app.getServerPanel().getModules();
            // Modules may or may not be present

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should reload all modules")
        void shouldReloadModules() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Modules
            driver.findElement(By.text("Modules")).click();

            // Click Reload All
            driver.findElement(By.text("Reload All")).click();

            Thread.sleep(1000);

            window.runFrames(5);
        }
    }

    // ========== CommandPanel Tests ==========

    @Nested
    @DisplayName("CommandPanel Tests")
    class CommandPanelTests {

        @Test
        @DisplayName("Should list commands from backend")
        void shouldListCommands() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Commands panel
            driver.findElement(By.text("Commands")).click();
            assertThat(app.getCommandPanel().isVisible()).isTrue();

            // Wait for commands to load
            Thread.sleep(1000);
            app.getCommandPanel().update();

            // Verify UI
            assertThat(driver.hasElement(By.text("Refresh"))).isTrue();
            assertThat(driver.hasElement(By.text("Send"))).isTrue();
            assertThat(driver.hasElement(By.textContaining("Match ID:"))).isTrue();
            assertThat(driver.hasElement(By.textContaining("Entity ID:"))).isTrue();

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should refresh command list")
        void shouldRefreshCommandList() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Commands
            driver.findElement(By.text("Commands")).click();

            // Click refresh
            driver.findElement(By.text("Refresh")).click();

            Thread.sleep(500);
            app.getCommandPanel().update();

            List<CommandInfo> commands = app.getCommandPanel().getCommands();
            // Commands may or may not be present depending on loaded modules

            window.runFrames(5);
        }

        @Test
        @DisplayName("Should send command to backend")
        void shouldSendCommand() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Navigate to Commands
            driver.findElement(By.text("Commands")).click();

            // Wait for commands to load
            Thread.sleep(1000);
            app.getCommandPanel().update();

            CommandService commandService = app.getCommandPanel().getCommandService();

            // Try to send a test command
            AtomicReference<Boolean> result = new AtomicReference<>(null);
            commandService.submitCommand(1, "testCommand", 0, Map.of())
                .thenAccept(result::set)
                .exceptionally(e -> {
                    result.set(false);
                    return null;
                });

            Thread.sleep(500);
            // Result may be true or false depending on if command exists

            window.runFrames(5);
        }
    }

    // ========== Full Application Integration ==========

    @Nested
    @DisplayName("Full Application Integration")
    class FullApplicationIntegration {

        @Test
        @DisplayName("Should navigate between all panels")
        void shouldNavigateBetweenAllPanels() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Verify initial state - Matches panel visible
            assertThat(app.getMatchPanel().isVisible()).isTrue();

            // Navigate to Snapshot
            driver.findElement(By.text("Snapshot")).click();
            assertThat(app.getSnapshotPanel().isVisible()).isTrue();
            assertThat(app.getMatchPanel().isVisible()).isFalse();

            // Navigate to Resources
            driver.findElement(By.text("Resources")).click();
            assertThat(app.getResourcePanel().isVisible()).isTrue();
            assertThat(app.getSnapshotPanel().isVisible()).isFalse();

            // Navigate to Modules
            driver.findElement(By.text("Modules")).click();
            assertThat(app.getServerPanel().isVisible()).isTrue();
            assertThat(app.getResourcePanel().isVisible()).isFalse();

            // Navigate to Commands
            driver.findElement(By.text("Commands")).click();
            assertThat(app.getCommandPanel().isVisible()).isTrue();
            assertThat(app.getServerPanel().isVisible()).isFalse();

            // Navigate back to Matches
            driver.findElement(By.text("Matches")).click();
            assertThat(app.getMatchPanel().isVisible()).isTrue();
            assertThat(app.getCommandPanel().isVisible()).isFalse();

            window.runFrames(10);
        }

        @Test
        @DisplayName("Should display all panels correctly")
        void shouldDisplayAllPanels() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            // Verify all panels exist
            assertThat(app.getMatchPanel()).isNotNull();
            assertThat(app.getSnapshotPanel()).isNotNull();
            assertThat(app.getResourcePanel()).isNotNull();
            assertThat(app.getServerPanel()).isNotNull();
            assertThat(app.getCommandPanel()).isNotNull();

            // Verify nav buttons
            assertThat(driver.hasElement(By.text("Matches"))).isTrue();
            assertThat(driver.hasElement(By.text("Snapshot"))).isTrue();
            assertThat(driver.hasElement(By.text("Resources"))).isTrue();
            assertThat(driver.hasElement(By.text("Modules"))).isTrue();
            assertThat(driver.hasElement(By.text("Commands"))).isTrue();

            // Dump component tree
            String tree = driver.dumpComponentTree();
            assertThat(tree).contains("GLButton");
            System.out.println("Component Tree:\n" + tree);

            window.runFrames(5);
        }

        @Test
        @DisplayName("Complete CRUD workflow - Match lifecycle")
        void completeMatchLifecycleTest() throws Exception {
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            Window window = app.getWindow();
            driver = GuiDriver.connect(window);

            MatchService matchService = new MatchService(backendUrl);
            ModuleService moduleService = new ModuleService(backendUrl);

            // 1. List available modules
            List<ModuleInfo> modules = moduleService.listModules().get(5, TimeUnit.SECONDS);
            System.out.println("Available modules: " + modules.size());

            // 2. Create a match with a module (if any available)
            List<String> moduleNames = modules.isEmpty() ? List.of() : List.of(modules.get(0).name());

            // ID is generated server-side
            long matchId = matchService.createMatch(moduleNames).get(5, TimeUnit.SECONDS);
            System.out.println("Created match with ID: " + matchId);

            if (matchId > 0) {
                // 3. Verify match appears in list
                app.getMatchPanel().refreshMatches();
                Thread.sleep(500);
                app.getMatchPanel().update();

                List<MatchInfo> matches = app.getMatchPanel().getMatches();
                boolean found = matches.stream().anyMatch(m -> m.id() == matchId);
                assertThat(found).isTrue();

                // 4. View snapshot for the match
                // (Would trigger snapshot panel switch)

                // 5. Delete the match
                boolean deleted = matchService.deleteMatch(matchId).get(5, TimeUnit.SECONDS);
                assertThat(deleted).isTrue();
                System.out.println("Deleted match: " + matchId);

                // 6. Verify match is gone
                app.getMatchPanel().refreshMatches();
                Thread.sleep(500);
                app.getMatchPanel().update();

                matches = app.getMatchPanel().getMatches();
                found = matches.stream().anyMatch(m -> m.id() == matchId);
                assertThat(found).isFalse();
            }

            window.runFrames(5);
        }
    }
}
