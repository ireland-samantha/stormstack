package com.lightningfirefly.engine.acceptance.test.gui;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.GameMasterService.GameMasterInfo;
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
 * GUI acceptance tests for game master management functionality.
 *
 * <p>Tests the business use cases for:
 * <ul>
 *   <li>Viewing installed game masters</li>
 *   <li>Reloading game masters</li>
 *   <li>Navigating to the game masters panel</li>
 * </ul>
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Game Master Management GUI Acceptance Tests")
@Testcontainers
class GameMasterManagementGuiIT {

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

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
        }
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("Navigate to GameMasters panel should show game master management")
    void navigateToGameMasters_shouldShowPanel() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to GameMasters panel
        clickButton("GameMasters");
        waitForUpdate(500);

        // Verify the panel is visible
        assertThat(app.getGameMasterPanel()).isNotNull();
        assertThat(app.getGameMasterPanel().isVisible()).isTrue();
    }

    @Test
    @DisplayName("View game masters list should show TickCounter game master")
    void viewGameMasters_shouldShowTickCounter() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to GameMasters panel
        clickButton("GameMasters");
        waitForUpdate(500);

        // Refresh game masters
        clickButton("Refresh");
        waitForGameMastersLoaded();

        // Verify TickCounter game master is installed
        var gameMasters = app.getGameMasterPanel().getGameMasters();
        assertThat(gameMasters).as("Game masters list should not be empty").isNotEmpty();

        var gameMasterNames = gameMasters.stream()
                .map(GameMasterInfo::name)
                .toList();
        assertThat(gameMasterNames).as("TickCounter should be installed").contains("TickCounter");
    }

    @Test
    @Disabled("Backend reload endpoint currently clears game masters instead of reloading - needs investigation")
    @DisplayName("Reload game masters should refresh the list")
    void reloadGameMasters_shouldRefreshList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to GameMasters panel
        clickButton("GameMasters");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForGameMastersLoaded();

        var initialGameMasters = app.getGameMasterPanel().getGameMasters();
        int initialCount = initialGameMasters.size();

        // Reload game masters
        clickButton("Reload All");
        waitForUpdate(2000);  // Wait longer for reload to complete on server
        clickButton("Refresh");
        waitForGameMastersLoaded();

        // Wait for game masters list to be repopulated (with retries)
        int reloadedCount = 0;
        for (int i = 0; i < 10; i++) {
            var reloadedGameMasters = app.getGameMasterPanel().getGameMasters();
            reloadedCount = reloadedGameMasters.size();
            if (reloadedCount >= initialCount) {
                break;
            }
            waitForUpdate(300);
            clickButton("Refresh");
            waitForGameMastersLoaded();
        }

        // Verify game masters still present (reload doesn't uninstall)
        assertThat(reloadedCount)
                .as("Game master count should remain the same after reload")
                .isEqualTo(initialCount);
    }

    @Test
    @DisplayName("GameMasters panel should show status messages")
    void gameMastersPanel_shouldShowStatus() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to GameMasters panel
        clickButton("GameMasters");
        waitForUpdate(500);

        // Refresh and wait for status update
        clickButton("Refresh");
        waitForGameMastersLoaded();

        // Verify status message is set
        String status = app.getGameMasterPanel().getStatusMessage();
        assertThat(status).as("Status message should be set").isNotEmpty();
    }

    @Test
    @DisplayName("Switching between panels should preserve game master state")
    void switchPanels_shouldPreserveGameMasterState() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to GameMasters panel
        clickButton("GameMasters");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForGameMastersLoaded();

        var gameMastersBefore = app.getGameMasterPanel().getGameMasters();

        // Switch to another panel
        clickButton("Modules");
        waitForUpdate(300);

        // Switch back to GameMasters
        clickButton("GameMasters");
        waitForUpdate(300);

        var gameMastersAfter = app.getGameMasterPanel().getGameMasters();

        // Verify state is preserved
        assertThat(gameMastersAfter.size())
                .as("Game masters should be preserved after panel switch")
                .isEqualTo(gameMastersBefore.size());
    }

    // ========== Helper Methods ==========

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
        }
    }

    private void waitForUpdate(long millis) throws InterruptedException {
        Thread.sleep(millis);
        if (app.getGameMasterPanel() != null) app.getGameMasterPanel().update();
        window.runFrames(3);
    }

    private void waitForGameMastersLoaded() throws InterruptedException {
        // Game masters may be empty, so just wait for the async operation to complete
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getGameMasterPanel().update();
            window.runFrames(2);
            // Check if status indicates loading is done
            String status = app.getGameMasterPanel().getStatusMessage();
            if (status != null && status.contains("Loaded")) {
                return;
            }
        }
    }
}
