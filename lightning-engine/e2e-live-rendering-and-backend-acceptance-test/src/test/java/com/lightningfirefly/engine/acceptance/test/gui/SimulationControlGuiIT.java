package com.lightningfirefly.engine.acceptance.test.gui;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance tests for simulation control (tick) functionality.
 *
 * <p>Tests advancing ticks, starting/stopping auto-play through the GUI.
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Simulation Control GUI Acceptance Tests")
@Testcontainers
class SimulationControlGuiIT {

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
        if (app != null && app.isPlaying()) {
            try {
                app.getSimulationService().stop().get();
            } catch (Exception e) {
                // Ignore
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
    @DisplayName("Given GUI with current tick displayed, when clicking Advance, then tick increments")
    void givenGuiWithCurrentTick_whenClickingAdvance_thenTickIncrements() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);
        waitForUpdate(500);

        long initialTick = app.getCurrentTick();

        // When: Clicking Advance
        clickButton("Advance");
        waitForUpdate(500);

        // Then: Tick should increment
        long newTick = app.getCurrentTick();
        assertThat(newTick)
                .as("Tick should increment after Advance")
                .isGreaterThan(initialTick);

        GuiElement tickLabel = driver.findElement(By.textContaining("Tick:"));
        assertThat(tickLabel.getText())
                .as("Tick label should show new value")
                .contains(String.valueOf(newTick));
    }

    @Test
    @DisplayName("Given GUI stopped, when clicking Play, then simulation auto-advances")
    void givenGuiStopped_whenClickingPlay_thenSimulationAutoAdvances() throws Exception {
        // Given: GUI initialized and not playing
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);
        waitForUpdate(500);

        long initialTick = app.getCurrentTick();

        // When: Clicking Play
        clickButton("Play");
        waitForUpdate(500);  // Allow time for async play to start
        assertThat(app.isPlaying())
                .as("Should be playing after Play click")
                .isTrue();

        // And: Waiting for ticks to advance
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            window.runFrames(2);
        }

        // Then: Tick should have advanced
        long tickAfterPlay = app.getSimulationService().getCurrentTick().get();
        assertThat(tickAfterPlay)
                .as("Tick should have advanced during play")
                .isGreaterThan(initialTick);

        // Cleanup
        clickButton("Stop");
        assertThat(app.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("Given simulation playing, when clicking Stop, then simulation stops advancing")
    void givenSimulationPlaying_whenClickingStop_thenSimulationStopsAdvancing() throws Exception {
        // Given: GUI playing
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);
        waitForUpdate(500);

        clickButton("Play");
        waitForUpdate(200);
        assertThat(app.isPlaying()).isTrue();

        // When: Clicking Stop
        clickButton("Stop");
        waitForUpdate(200);

        // Then: Simulation should stop
        assertThat(app.isPlaying())
                .as("Should be stopped after Stop click")
                .isFalse();

        long tickAfterStop = app.getSimulationService().getCurrentTick().get();
        Thread.sleep(300);
        long tickLater = app.getSimulationService().getCurrentTick().get();

        assertThat(tickLater)
                .as("Tick should not advance after Stop")
                .isEqualTo(tickAfterStop);
    }

    @Test
    @DisplayName("Given GUI, when clicking Advance 3 times, then tick increments by 3")
    void givenGui_whenClickingAdvance3Times_thenTickIncrementsBy3() throws Exception {
        // Given: GUI initialized
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);
        waitForUpdate(500);

        long initialTick = app.getCurrentTick();

        // When: Clicking Advance 3 times
        for (int i = 0; i < 3; i++) {
            clickButton("Advance");
            waitForUpdate(200);
        }

        // Then: Tick should increment by 3
        long finalTick = app.getCurrentTick();
        assertThat(finalTick - initialTick)
                .as("Tick should increment by 3 after 3 Advance clicks")
                .isEqualTo(3);
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
        window.runFrames(3);
    }
}
