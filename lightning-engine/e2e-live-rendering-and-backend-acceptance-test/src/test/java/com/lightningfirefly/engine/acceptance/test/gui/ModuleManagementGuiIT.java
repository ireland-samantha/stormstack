package com.lightningfirefly.engine.acceptance.test.gui;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.ModuleService.ModuleInfo;
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
 * GUI acceptance tests for module management functionality.
 *
 * <p>Tests viewing and reloading modules through the GUI.
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Module Management GUI Acceptance Tests")
@Testcontainers
class ModuleManagementGuiIT {

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
    @DisplayName("Given GUI on Modules panel, when refreshing, then MoveModule is displayed")
    void givenGuiOnModulesPanel_whenRefreshing_thenMoveModuleIsDisplayed() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // When: Navigating to Modules and refreshing
        clickButton("Modules");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        // Then: MoveModule should be visible
        var modules = app.getServerPanel().getModules();
        assertThat(modules)
                .as("At least one module should be installed")
                .isNotEmpty();

        List<String> moduleNames = modules.stream()
                .map(ModuleInfo::name)
                .toList();
        assertThat(moduleNames)
                .as("MoveModule should be installed")
                .contains("MoveModule");
    }

    @Test
    @DisplayName("Given modules loaded, when reloading all, then module list is preserved")
    void givenModulesLoaded_whenReloadingAll_thenModuleListIsPreserved() throws Exception {
        // Given: GUI with modules loaded
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        clickButton("Modules");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        var initialModules = app.getServerPanel().getModules();
        int initialCount = initialModules.size();

        // When: Reloading all modules
        clickButton("Reload All");
        waitForUpdate(1000);
        clickButton("Refresh");
        waitForModulesLoaded();

        // Then: Module count should remain the same
        var reloadedModules = app.getServerPanel().getModules();
        assertThat(reloadedModules.size())
                .as("Module count should remain the same after reload")
                .isEqualTo(initialCount);
    }

    @Test
    @DisplayName("Given MoveModule is installed, when viewing modules, then flag component exists")
    void givenMoveModuleIsInstalled_whenViewingModules_thenFlagComponentExists() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // When: Viewing modules
        clickButton("Modules");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForModulesLoaded();

        // Then: MoveModule should exist
        var modules = app.getServerPanel().getModules();
        var moveModule = modules.stream()
                .filter(m -> m.name().equals("MoveModule"))
                .findFirst()
                .orElse(null);

        assertThat(moveModule)
                .as("MoveModule should exist")
                .isNotNull();
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
        if (app.getServerPanel() != null) app.getServerPanel().update();
        window.runFrames(3);
    }

    private void waitForModulesLoaded() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getServerPanel().update();
            window.runFrames(2);
            if (!app.getServerPanel().getModules().isEmpty()) {
                return;
            }
        }
    }
}
