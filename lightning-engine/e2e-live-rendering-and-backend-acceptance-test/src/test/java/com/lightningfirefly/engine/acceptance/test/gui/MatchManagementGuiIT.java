package com.lightningfirefly.engine.acceptance.test.gui;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.panel.CreateMatchPanel;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance tests for match management functionality.
 *
 * <p>Tests creating, viewing, and deleting matches through the GUI.
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Match Management GUI Acceptance Tests")
@Testcontainers
class MatchManagementGuiIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long createdMatchId = -1;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
    }

    @AfterEach
    void tearDown() {
        if (app != null && createdMatchId > 0) {
            try {
                app.getMatchPanel().getMatchService().deleteMatch(createdMatchId).get();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (driver != null) {
            driver.close();
        }
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("Given GUI with MoveModule selected, when creating match, then match appears in list")
    void givenGuiWithMoveModuleSelected_whenCreatingMatch_thenMatchAppearsInList() throws Exception {
        // Given: GUI initialized and modules loaded
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var initialMatchCount = matchPanel.getMatches().size();

        // And: MoveModule is selected via CreateMatchPanel
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);

        var createPanel = matchPanel.getCreateMatchPanel();
        waitForCreatePanelLoaded(createPanel);

        var modules = createPanel.getAvailableModules();
        int moveModuleIndex = findModuleIndex(modules, "MoveModule");
        assertThat(moveModuleIndex)
                .as("MoveModule should be available")
                .isGreaterThanOrEqualTo(0);
        createPanel.selectModule(moveModuleIndex);
        window.runFrames(2);

        // When: Creating the match
        createPanel.triggerCreate();
        waitForUpdate(500);

        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        createdMatchId = matches.size() > initialMatchCount ? matches.get(matches.size() - 1).id() : -1;

        // Then: Match should be created and appear in list
        assertThat(createdMatchId)
                .as("Match ID should be positive")
                .isGreaterThan(0);

        assertThat(matches.size())
                .as("Match count should increase")
                .isGreaterThan(initialMatchCount);
        assertThat(matches.stream().anyMatch(m -> m.id() == createdMatchId))
                .as("Created match should appear in list")
                .isTrue();
    }

    @Test
    @DisplayName("Given an existing match, when deleting it, then match disappears from list")
    void givenExistingMatch_whenDeletingIt_thenMatchDisappearsFromList() throws Exception {
        // Given: GUI initialized with a created match
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        int initialCount = matchPanel.getMatches().size();

        // Create match via CreateMatchPanel
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);
        var createPanel = matchPanel.getCreateMatchPanel();
        waitForCreatePanelLoaded(createPanel);
        createPanel.triggerCreate();
        waitForUpdate(500);

        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        createdMatchId = matches.size() > initialCount ? matches.get(matches.size() - 1).id() : -1;

        var matchToDelete = matches.stream()
                .filter(m -> m.id() == createdMatchId)
                .findFirst()
                .orElseThrow();

        // When: Deleting the match
        matchPanel.getMatchService().deleteMatch(createdMatchId).get();
        createdMatchId = -1; // Mark as deleted

        clickButton("Refresh");
        waitForUpdate(500);

        // Then: Match should no longer appear in list
        var remainingMatches = matchPanel.getMatches();
        assertThat(remainingMatches.stream().noneMatch(m -> m.id() == matchToDelete.id()))
                .as("Deleted match should not appear in list")
                .isTrue();
    }

    @Test
    @DisplayName("Given a match exists, when viewing snapshot, then navigates to Snapshot panel")
    void givenMatchExists_whenViewingSnapshot_thenNavigatesToSnapshotPanel() throws Exception {
        // Given: GUI with a created match
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        int initialCount = matchPanel.getMatches().size();

        // Create match via CreateMatchPanel
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);
        var createPanel = matchPanel.getCreateMatchPanel();
        waitForCreatePanelLoaded(createPanel);
        createPanel.triggerCreate();
        waitForUpdate(500);

        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        createdMatchId = matches.size() > initialCount ? matches.get(matches.size() - 1).id() : -1;

        assertThat(matchPanel.isVisible()).isTrue();
        assertThat(app.getSnapshotPanel().isVisible()).isFalse();

        // When: Switching to snapshot view
        app.switchToPanel("snapshot");
        waitForUpdate(300);

        // Then: Snapshot panel should be visible
        assertThat(app.getSnapshotPanel().isVisible()).isTrue();
    }

    // ========== Helper Methods ==========

    private int findModuleIndex(List<?> modules, String moduleName) {
        for (int i = 0; i < modules.size(); i++) {
            Object module = modules.get(i);
            try {
                var nameMethod = module.getClass().getMethod("name");
                if (moduleName.equals(nameMethod.invoke(module))) {
                    return i;
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return -1;
    }

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
        }
    }

    private void waitForUpdate(long millis) throws InterruptedException {
        Thread.sleep(millis);
        if (app.getMatchPanel() != null) app.getMatchPanel().update();
        if (app.getServerPanel() != null) app.getServerPanel().update();
        window.runFrames(3);
    }

    private void waitForModulesLoaded() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getServerPanel().update();
            app.getMatchPanel().update();
            window.runFrames(2);
            if (!app.getServerPanel().getModules().isEmpty()) {
                return;
            }
        }
    }

    private void waitForCreatePanelLoaded(CreateMatchPanel createPanel) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            createPanel.update();
            window.runFrames(2);
            if (!createPanel.getAvailableModules().isEmpty()) {
                return;
            }
        }
    }
}
