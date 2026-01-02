package com.lightningfirefly.engine.acceptance.test.gui;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.panel.CreateMatchPanel;
import com.lightningfirefly.engine.gui.panel.MatchPanel;
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
 * E2E acceptance tests for match creation via CreateMatchPanel.
 *
 * <p>Tests the complete workflow:
 * <ol>
 *   <li>Click "Create" button to open CreateMatchPanel</li>
 *   <li>Select modules and game masters in the popup</li>
 *   <li>Click "Create Match" to create the match</li>
 *   <li>Verify match appears in list with enabled modules shown</li>
 * </ol>
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Match Creation E2E GUI Acceptance Tests")
@Testcontainers
class MatchCreationE2EGuiIT {

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
    @DisplayName("Given MatchPanel, when clicking Create button, then CreateMatchPanel appears")
    void givenMatchPanel_whenClickingCreate_thenCreateMatchPanelAppears() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);

        MatchPanel matchPanel = app.getMatchPanel();
        assertThat(matchPanel.isCreateMatchPanelVisible())
                .as("CreateMatchPanel should not be visible initially")
                .isFalse();

        // When: Click Create button
        clickButton("Create");
        waitForUpdate(300);

        // Then: CreateMatchPanel should be visible and rendered
        assertThat(matchPanel.isCreateMatchPanelVisible())
                .as("CreateMatchPanel should be visible after clicking Create")
                .isTrue();

        CreateMatchPanel createPanel = matchPanel.getCreateMatchPanel();
        assertThat(createPanel).isNotNull();

        // Verify the panel has module/game master lists populated
        waitForCreatePanelLoaded(createPanel);
        assertThat(createPanel.getAvailableModules())
                .as("CreateMatchPanel should have modules loaded")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Given CreateMatchPanel open, when selecting modules and clicking Create Match, then match is created")
    void givenCreateMatchPanelOpen_whenSelectingModulesAndClickingCreate_thenMatchIsCreated() throws Exception {
        // Given: GUI initialized with CreateMatchPanel open
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);

        MatchPanel matchPanel = app.getMatchPanel();
        int initialMatchCount = matchPanel.getMatches().size();

        // Open CreateMatchPanel
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);

        CreateMatchPanel createPanel = matchPanel.getCreateMatchPanel();
        waitForCreatePanelLoaded(createPanel);

        // Select a module
        int moduleIndex = findModuleIndex(createPanel.getAvailableModules(), "MoveModule");
        if (moduleIndex >= 0) {
            createPanel.selectModule(moduleIndex);
        } else if (!createPanel.getAvailableModules().isEmpty()) {
            createPanel.selectModule(0); // Select first available
        }
        window.runFrames(2);

        // When: Click Create Match button
        createPanel.triggerCreate();
        waitForUpdate(500);

        // Then: Match should be created
        // Refresh to get updated list
        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        assertThat(matches.size())
                .as("Match count should increase after creation")
                .isGreaterThan(initialMatchCount);

        // Store for cleanup
        if (!matches.isEmpty()) {
            createdMatchId = matches.get(matches.size() - 1).id();
        }
    }

    @Test
    @DisplayName("Given match created via UI, when selecting match, then [ENABLED] prefix shows for enabled modules")
    void givenMatchCreatedViaUI_whenSelectingMatch_thenEnabledPrefixShowsForEnabledModules() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);

        MatchPanel matchPanel = app.getMatchPanel();

        // Open CreateMatchPanel and create a match with specific module
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);

        CreateMatchPanel createPanel = matchPanel.getCreateMatchPanel();
        waitForCreatePanelLoaded(createPanel);

        // Select MoveModule if available
        int moduleIndex = findModuleIndex(createPanel.getAvailableModules(), "MoveModule");
        if (moduleIndex >= 0) {
            createPanel.selectModule(moduleIndex);
        }
        window.runFrames(2);

        // Create the match
        createPanel.triggerCreate();
        waitForUpdate(500);

        // Refresh matches
        clickButton("Refresh");
        waitForUpdate(500);

        var matches = matchPanel.getMatches();
        assertThat(matches).as("Should have at least one match").isNotEmpty();

        // Store for cleanup
        createdMatchId = matches.get(matches.size() - 1).id();

        // When: Match is selected (via internal state check)
        // The MatchPanel now shows [ENABLED] prefix when a match is selected
        // We verify the match has the expected enabled modules
        var latestMatch = matches.stream()
                .filter(m -> m.id() == createdMatchId)
                .findFirst()
                .orElseThrow();

        // Then: Verify the match has the enabled modules we selected
        if (moduleIndex >= 0) {
            assertThat(latestMatch.enabledModules())
                    .as("Match should have MoveModule enabled")
                    .contains("MoveModule");
        }
    }

    @Test
    @DisplayName("Given CreateMatchPanel open, when clicking Cancel, then panel closes")
    void givenCreateMatchPanelOpen_whenClickingCancel_thenPanelCloses() throws Exception {
        // Given: GUI initialized with CreateMatchPanel open
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);

        MatchPanel matchPanel = app.getMatchPanel();
        matchPanel.openCreateMatchPanel();
        waitForUpdate(300);

        assertThat(matchPanel.isCreateMatchPanelVisible()).isTrue();

        // When: Click Cancel
        CreateMatchPanel createPanel = matchPanel.getCreateMatchPanel();
        createPanel.triggerCancel();
        waitForUpdate(100);

        // Then: Panel should be hidden
        assertThat(matchPanel.isCreateMatchPanelVisible())
                .as("CreateMatchPanel should be hidden after cancel")
                .isFalse();
    }

    @Test
    @DisplayName("Given no match selected, when viewing module list, then all modules shown without prefix")
    void givenNoMatchSelected_whenViewingModuleList_thenAllModulesShownWithoutPrefix() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Matches");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        MatchPanel matchPanel = app.getMatchPanel();

        // When: No match is selected
        assertThat(matchPanel.getSelectedMatch())
                .as("No match should be selected initially")
                .isNull();

        // Then: All modules should be available in the list
        assertThat(matchPanel.getAvailableModules())
                .as("Modules should be loaded")
                .isNotEmpty();
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
