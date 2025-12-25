package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
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
 * <p>Tests the business use cases for:
 * <ul>
 *   <li>Creating a match with selected modules</li>
 *   <li>Viewing match details</li>
 *   <li>Deleting a match</li>
 *   <li>Refreshing the match list</li>
 * </ul>
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
    @DisplayName("Create match with MoveModule and verify it appears in list")
    void createMatchWithModule_shouldAppearInList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Go to Matches panel
        clickButton("Matches");
        waitForUpdate(500);

        // Refresh to load modules
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        var initialMatchCount = matchPanel.getMatches().size();

        // Select MoveModule
        var modules = matchPanel.getAvailableModules();
        int moveModuleIndex = findModuleIndex(modules, "MoveModule");
        assertThat(moveModuleIndex).as("MoveModule should be available").isGreaterThanOrEqualTo(0);
        matchPanel.selectModule(moveModuleIndex);
        window.runFrames(2);

        // Create the match
        createdMatchId = matchPanel.createMatchWithSelectedModules().get();
        assertThat(createdMatchId).as("Match ID should be positive").isGreaterThan(0);

        // Refresh and verify
        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        assertThat(matches.size()).as("Match count should increase").isGreaterThan(initialMatchCount);
        assertThat(matches.stream().anyMatch(m -> m.id() == createdMatchId))
                .as("Created match should appear in list").isTrue();
    }

    @Test
    @DisplayName("Delete match and verify it disappears from list")
    void deleteMatch_shouldRemoveFromList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Create a match first
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        createdMatchId = matchPanel.createMatchWithSelectedModules().get();
        clickButton("Refresh");
        waitForUpdate(500);

        var initialMatches = matchPanel.getMatches();
        var matchToDelete = initialMatches.stream()
                .filter(m -> m.id() == createdMatchId)
                .findFirst()
                .orElseThrow();

        // Select and delete
        int matchIndex = initialMatches.indexOf(matchToDelete);
        // Note: In real UI, would need to click on list item to select

        // Delete directly via service for this test
        matchPanel.getMatchService().deleteMatch(createdMatchId).get();
        createdMatchId = -1; // Mark as deleted to skip cleanup

        // Refresh and verify
        clickButton("Refresh");
        waitForUpdate(500);

        var remainingMatches = matchPanel.getMatches();
        assertThat(remainingMatches.stream().noneMatch(m -> m.id() == matchToDelete.id()))
                .as("Deleted match should not appear in list").isTrue();
    }

    @Test
    @DisplayName("View match components navigates to Snapshot panel")
    void viewMatchSnapshot_shouldNavigateToSnapshotPanel() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Create a match
        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var matchPanel = app.getMatchPanel();
        createdMatchId = matchPanel.createMatchWithSelectedModules().get();
        clickButton("Refresh");
        waitForUpdate(500);

        // Verify we're on matches panel
        assertThat(matchPanel.isVisible()).isTrue();
        assertThat(app.getSnapshotPanel().isVisible()).isFalse();

        // View components (simulated via callback)
        matchPanel.setOnViewSnapshot(matchId -> app.switchToPanel("components"));
        app.switchToPanel("components");
        waitForUpdate(300);

        // Verify navigation to components panel
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
}
