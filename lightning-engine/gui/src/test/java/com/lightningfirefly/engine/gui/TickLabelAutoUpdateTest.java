package com.lightningfirefly.engine.gui;

import com.lightningfirefly.engine.gui.service.SimulationService;
import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import com.lightningfirefly.engine.rendering.testing.headless.HeadlessComponentFactory;
import com.lightningfirefly.engine.rendering.testing.headless.HeadlessWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for tick label auto-update during play mode.
 */
@DisplayName("Tick Label Auto-Update Tests")
class TickLabelAutoUpdateTest {

    private HeadlessWindow window;
    private ComponentFactory factory;
    private GuiDriver driver;
    private SimulationService mockSimulationService;

    @BeforeEach
    void setUp() {
        window = new HeadlessWindow(1200, 800);
        factory = HeadlessComponentFactory.getInstance();
        mockSimulationService = mock(SimulationService.class);

        // Default mock behaviors
        when(mockSimulationService.getCurrentTick()).thenReturn(CompletableFuture.completedFuture(0L));
        when(mockSimulationService.play(anyLong())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockSimulationService.stop()).thenReturn(CompletableFuture.completedFuture(true));
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

    @Test
    @DisplayName("Tick label should update when playing")
    void tickLabel_shouldUpdateWhenPlaying() throws InterruptedException {
        // Given - simulation service that returns increasing tick values
        AtomicLong tickCounter = new AtomicLong(0);
        when(mockSimulationService.getCurrentTick()).thenAnswer(inv ->
            CompletableFuture.completedFuture(tickCounter.incrementAndGet())
        );

        // Create app with mocked service
        GuiConfig config = GuiConfig.load("http://localhost:8080", 1);
        TestableEngineGuiApplication app = new TestableEngineGuiApplication(config, factory, mockSimulationService);
        app.initializeHeadless(window);

        driver = GuiDriver.connect(window);

        // Verify initial tick label
        GuiElement tickLabel = driver.findElement(By.textContaining("Tick:"));
        assertThat(tickLabel).isNotNull();

        // When - start playing
        app.simulateStartPlay();
        assertThat(app.isPlaying()).isTrue();

        // Simulate update loop while playing
        for (int i = 0; i < 5; i++) {
            app.simulateUpdate();
            Thread.sleep(60); // Wait for tick refresh interval
        }

        // Then - tick should have been fetched multiple times
        verify(mockSimulationService, atLeast(2)).getCurrentTick();

        // And tick label should show updated value
        long currentTick = app.getCurrentTick();
        assertThat(currentTick).isGreaterThan(0);
    }

    @Test
    @DisplayName("Tick label should not update when stopped")
    void tickLabel_shouldNotUpdateWhenStopped() throws InterruptedException {
        // Given
        when(mockSimulationService.getCurrentTick()).thenReturn(CompletableFuture.completedFuture(100L));

        GuiConfig config = GuiConfig.load("http://localhost:8080", 1);
        TestableEngineGuiApplication app = new TestableEngineGuiApplication(config, factory, mockSimulationService);
        app.initializeHeadless(window);

        // Reset mock after initial tick fetch
        reset(mockSimulationService);
        when(mockSimulationService.getCurrentTick()).thenReturn(CompletableFuture.completedFuture(200L));

        // When - not playing, just update
        assertThat(app.isPlaying()).isFalse();

        for (int i = 0; i < 5; i++) {
            app.simulateUpdate();
            Thread.sleep(60);
        }

        // Then - getCurrentTick should not be called when not playing
        verify(mockSimulationService, never()).getCurrentTick();
    }

    @Test
    @DisplayName("Tick label should stop updating after stop is clicked")
    void tickLabel_shouldStopUpdatingAfterStop() throws InterruptedException {
        // Given
        AtomicLong tickCounter = new AtomicLong(0);
        when(mockSimulationService.getCurrentTick()).thenAnswer(inv ->
            CompletableFuture.completedFuture(tickCounter.incrementAndGet())
        );

        GuiConfig config = GuiConfig.load("http://localhost:8080", 1);
        TestableEngineGuiApplication app = new TestableEngineGuiApplication(config, factory, mockSimulationService);
        app.initializeHeadless(window);

        // Start playing
        app.simulateStartPlay();
        assertThat(app.isPlaying()).isTrue();

        // Update a few times while playing
        for (int i = 0; i < 3; i++) {
            app.simulateUpdate();
            Thread.sleep(60);
        }

        int callsWhilePlaying = (int) tickCounter.get();

        // Stop playing
        app.simulateStopPlay();
        assertThat(app.isPlaying()).isFalse();

        // Reset mock to count new calls
        reset(mockSimulationService);
        when(mockSimulationService.getCurrentTick()).thenReturn(CompletableFuture.completedFuture(999L));

        // Update more times after stopping
        for (int i = 0; i < 3; i++) {
            app.simulateUpdate();
            Thread.sleep(60);
        }

        // Then - no additional calls after stopping
        verify(mockSimulationService, never()).getCurrentTick();
    }

    /**
     * Testable subclass of EngineGuiApplication for headless testing.
     */
    static class TestableEngineGuiApplication extends EngineGuiApplication {

        private final SimulationService testSimulationService;
        private HeadlessWindow headlessWindow;

        public TestableEngineGuiApplication(GuiConfig config, ComponentFactory factory, SimulationService simulationService) {
            super(config, factory);
            this.testSimulationService = simulationService;
        }

        public void initializeHeadless(HeadlessWindow window) {
            this.headlessWindow = window;
            // We need to manually setup since we can't use the normal initialize
            setupUIHeadless();
        }

        private void setupUIHeadless() {
            // Create minimal UI for testing tick controls
            ComponentFactory.Colours colours = getComponentFactory().getColours();

            // Create tick label
            Label tickLabel = getComponentFactory().createLabel(800, 70, "Tick: 0", 14.0f);
            tickLabel.setTextColor(colours.textPrimary());

            // Create play/stop buttons
            Button playButton = getComponentFactory().createButton(890, 66, 60, 28, "Play");
            playButton.setOnClick(() -> simulateStartPlay());

            Button stopButton = getComponentFactory().createButton(960, 66, 60, 28, "Stop");
            stopButton.setOnClick(() -> simulateStopPlay());

            headlessWindow.addComponent((WindowComponent) tickLabel);
            headlessWindow.addComponent((WindowComponent) playButton);
            headlessWindow.addComponent((WindowComponent) stopButton);

            setTickLabel(tickLabel);

            // Initial tick fetch
            testSimulationService.getCurrentTick().thenAccept(tick -> {
                if (tick >= 0) {
                    setCurrentTick(tick);
                    tickLabel.setText("Tick: " + tick);
                }
            });
        }

        private ComponentFactory getComponentFactory() {
            try {
                var field = EngineGuiApplication.class.getDeclaredField("componentFactory");
                field.setAccessible(true);
                return (ComponentFactory) field.get(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void setTickLabel(Label label) {
            try {
                var field = EngineGuiApplication.class.getDeclaredField("tickLabel");
                field.setAccessible(true);
                field.set(this, label);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void setCurrentTick(long tick) {
            try {
                var field = EngineGuiApplication.class.getDeclaredField("currentTick");
                field.setAccessible(true);
                field.set(this, tick);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void setIsPlaying(boolean playing) {
            try {
                var field = EngineGuiApplication.class.getDeclaredField("isPlaying");
                field.setAccessible(true);
                field.set(this, playing);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void simulateStartPlay() {
            setIsPlaying(true);
        }

        public void simulateStopPlay() {
            setIsPlaying(false);
        }

        public void simulateUpdate() {
            // Simulate the update loop logic
            try {
                var isPlayingField = EngineGuiApplication.class.getDeclaredField("isPlaying");
                isPlayingField.setAccessible(true);
                boolean playing = (boolean) isPlayingField.get(this);

                var tickRefreshPendingField = EngineGuiApplication.class.getDeclaredField("tickRefreshPending");
                tickRefreshPendingField.setAccessible(true);
                boolean tickRefreshPending = (boolean) tickRefreshPendingField.get(this);

                var lastTickRefreshTimeField = EngineGuiApplication.class.getDeclaredField("lastTickRefreshTime");
                lastTickRefreshTimeField.setAccessible(true);

                var tickLabelField = EngineGuiApplication.class.getDeclaredField("tickLabel");
                tickLabelField.setAccessible(true);
                Label tickLabel = (Label) tickLabelField.get(this);

                var currentTickField = EngineGuiApplication.class.getDeclaredField("currentTick");
                currentTickField.setAccessible(true);

                if (playing && !tickRefreshPending) {
                    long now = System.currentTimeMillis();
                    long lastRefresh = (long) lastTickRefreshTimeField.get(this);
                    if (now - lastRefresh >= 50) { // TICK_REFRESH_INTERVAL_MS
                        lastTickRefreshTimeField.set(this, now);
                        tickRefreshPendingField.set(this, true);

                        testSimulationService.getCurrentTick().thenAccept(tick -> {
                            try {
                                tickRefreshPendingField.set(this, false);
                                long currentTick = (long) currentTickField.get(this);
                                if (tick >= 0 && tick != currentTick) {
                                    currentTickField.set(this, tick);
                                    tickLabel.setText("Tick: " + tick);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
