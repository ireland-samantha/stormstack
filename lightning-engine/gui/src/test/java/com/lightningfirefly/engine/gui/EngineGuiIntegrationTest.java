package com.lightningfirefly.engine.gui;

import com.lightningfirefly.engine.gui.panel.MatchPanel;
import com.lightningfirefly.engine.gui.panel.ResourcePanel;
import com.lightningfirefly.engine.gui.panel.ServerPanel;
import com.lightningfirefly.engine.gui.panel.SnapshotPanel;
import com.lightningfirefly.engine.rendering.render2d.Button;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.Label;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.engine.rendering.render2d.WindowComponent;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.ExpectedConditions;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import com.lightningfirefly.engine.rendering.testing.GuiElement;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EngineGuiApplication with real OpenGL rendering.
 *
 * <p><strong>IMPORTANT:</strong> These tests require:
 * <ul>
 *   <li>A display (not headless environment)</li>
 *   <li>OpenGL 3.3+ support</li>
 *   <li>For backend tests: A running Quarkus server at configured URL</li>
 * </ul>
 *
 * <p><strong>How it works:</strong> Tests run with {@code -XstartOnFirstThread} via
 * Maven Surefire's argLine, and OpenGL is initialized directly on the test thread
 * (which is the main thread). Each test uses {@code window.runFrames(n)} to verify
 * real OpenGL rendering works.
 *
 * <p>To run these tests:
 * <pre>
 * # Run all OpenGL tests (no backend required for some)
 * ./mvnw test -pl lightning-engine/gui -Dtest=EngineGuiIntegrationTest -DenableGLTests=true
 *
 * # With backend integration (for full integration tests)
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=EngineGuiIntegrationTest -DenableGLTests=true
 * </pre>
 *
 * <p>These tests are disabled by default (require {@code -DenableGLTests=true}).
 */
@Slf4j
@Tag("integration")
@DisplayName("Engine GUI Integration Tests")
class EngineGuiIntegrationTest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private Window window;
    private GuiDriver driver;

    @BeforeAll
    static void checkPrerequisites() {
        // Check if we can create OpenGL context
        // This will fail fast if LWJGL is not properly configured
        log.info("Integration test prerequisites check...");
        log.info("  DISPLAY: " + System.getenv("DISPLAY"));
        log.info("  enableGLTests: " + System.getProperty("enableGLTests"));
        log.info("  BACKEND_URL: " + System.getenv("BACKEND_URL"));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
            driver = null;
        }

        if (window != null) {
            window.stop();
            window = null;
        }
    }

    /**
     * Tests that verify the framework works with real OpenGL window.
     * Require display but NOT a running backend server.
     *
     * <p>On macOS, GLFW must run on the main thread. These tests use a pattern where:
     * 1. The test thread (main thread with -XstartOnFirstThread) initializes the window
     * 2. A background thread runs assertions after window is ready
     * 3. The main thread runs the event loop briefly then stops
     */
    @Nested
    @DisplayName("OpenGL Window Tests (No Backend)")
    @EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
    class OpenGLWindowTests {

        @Test
        @DisplayName("Create window and find components")
        void createWindow_findComponents() throws Exception {
            // Create a simple window with some components
            // OpenGL initialization happens on the test thread (main thread)
            ComponentFactory factory = GLComponentFactory.getInstance();

            window = WindowBuilder.create()
                .size(800, 600)
                .title("Test Window")
                .build();

            // Add components
            Button saveButton = factory.createButton(100, 100, 120, 40, "Save");
            ((WindowComponent) saveButton).setId("saveBtn");
            window.addComponent((WindowComponent) saveButton);

            Button cancelButton = factory.createButton(230, 100, 120, 40, "Cancel");
            ((WindowComponent) cancelButton).setId("cancelBtn");
            window.addComponent((WindowComponent) cancelButton);

            Label statusLabel = factory.createLabel(100, 160, "Status: Ready");
            ((WindowComponent) statusLabel).setId("statusLabel");
            window.addComponent((WindowComponent) statusLabel);

            // Connect driver to window
            driver = GuiDriver.connect(window);

            // Find components
            GuiElement saveBtn = driver.findElement(By.id("saveBtn"));
            assertThat(saveBtn).isNotNull();
            assertThat(saveBtn.getText()).isEqualTo("Save");

            GuiElement cancelBtn = driver.findElement(By.id("cancelBtn"));
            assertThat(cancelBtn).isNotNull();
            assertThat(cancelBtn.getText()).isEqualTo("Cancel");

            GuiElement statusLbl = driver.findElement(By.id("statusLabel"));
            assertThat(statusLbl).isNotNull();
            assertThat(statusLbl.getText()).isEqualTo("Status: Ready");

            // Find all buttons
            List<GuiElement> buttons = driver.findElements(By.type(Button.class));
            assertThat(buttons).hasSize(2);

            // Dump component tree for debugging
            String tree = driver.dumpComponentTree();
            assertThat(tree).contains("GLButton");
            assertThat(tree).contains("#saveBtn");
            assertThat(tree).contains("#cancelBtn");

            // Run a few frames to verify OpenGL works
            window.runFrames(10);
        }

        @Test
        @DisplayName("Click button in OpenGL window")
        void clickButton_inOpenGLWindow() throws Exception {
            ComponentFactory factory = GLComponentFactory.getInstance();
            AtomicReference<Boolean> clicked = new AtomicReference<>(false);

            window = WindowBuilder.create()
                .size(800, 600)
                .title("Click Test")
                .build();

            Button button = factory.createButton(100, 100, 150, 50, "Click Me");
            ((WindowComponent) button).setId("testBtn");
            button.setOnClick(() -> clicked.set(true));
            window.addComponent((WindowComponent) button);

            driver = GuiDriver.connect(window);

            // Click the button
            driver.findElement(By.id("testBtn")).click();

            // Verify click was registered
            assertThat(clicked.get()).isTrue();

            // Run a few frames to verify OpenGL works
            window.runFrames(10);
        }
    }

    /**
     * Full integration tests with EngineGuiApplication and live backend.
     * Require both display AND a running Quarkus backend server.
     *
     * <p>These tests run OpenGL on the main thread (test thread) to comply with
     * macOS GLFW requirements.
     */
    @Nested
    @DisplayName("Full Integration Tests (With Backend)")
    @EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
    @EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
    class FullIntegrationTests {

        private EngineGuiApplication app;
        private String backendUrl;

        @BeforeEach
        void setUp() {
            backendUrl = System.getenv("BACKEND_URL");
            if (backendUrl == null || backendUrl.isEmpty()) {
                backendUrl = DEFAULT_SERVER_URL;
            }
        }

        @AfterEach
        void tearDownApp() {
            if (app != null) {
                app.stop();
                app = null;
            }
        }

        @Test
        @DisplayName("Launch application and verify panels exist")
        void launchApplication_verifyPanels() throws Exception {
            // Initialize app on main thread (required for GLFW on macOS)
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            // Now we can access the window
            Window appWindow = app.getWindow();
            assertThat(appWindow).isNotNull();

            // Get panels and verify they were created
            MatchPanel matchPanel = app.getMatchPanel();
            ResourcePanel resourcePanel = app.getResourcePanel();
            ServerPanel serverPanel = app.getServerPanel();
            SnapshotPanel snapshotPanel = app.getSnapshotPanel();

            assertThat(matchPanel).isNotNull();
            assertThat(resourcePanel).isNotNull();
            assertThat(serverPanel).isNotNull();
            assertThat(snapshotPanel).isNotNull();

            // Connect driver and verify we can find components
            driver = GuiDriver.connect(appWindow);
            assertThat(driver.hasElement(By.text("Matches"))).isTrue();
            assertThat(driver.hasElement(By.text("Snapshot"))).isTrue();
            assertThat(driver.hasElement(By.text("Resources"))).isTrue();
            assertThat(driver.hasElement(By.text("Modules"))).isTrue();

            // Run a few frames to verify OpenGL works
            appWindow.runFrames(10);
        }

        @Test
        @DisplayName("Navigate between panels via nav buttons")
        void navigateBetweenPanels() throws Exception {
            // Initialize app on main thread
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            // Connect driver to window
            driver = GuiDriver.connect(app.getWindow());

            // Initial state: Matches panel should be visible
            assertThat(app.getMatchPanel().isVisible()).isTrue();
            assertThat(app.getSnapshotPanel().isVisible()).isFalse();

            // Navigate to Snapshot panel
            driver.findElement(By.text("Snapshot")).click();

            // Verify snapshot panel is now visible
            assertThat(app.getSnapshotPanel().isVisible()).isTrue();
            assertThat(app.getMatchPanel().isVisible()).isFalse();

            // Navigate to Resources panel
            driver.findElement(By.text("Resources")).click();

            assertThat(app.getResourcePanel().isVisible()).isTrue();
            assertThat(app.getSnapshotPanel().isVisible()).isFalse();

            // Navigate to Modules panel
            driver.findElement(By.text("Modules")).click();

            assertThat(app.getServerPanel().isVisible()).isTrue();
            assertThat(app.getResourcePanel().isVisible()).isFalse();

            // Navigate back to Matches
            driver.findElement(By.text("Matches")).click();

            assertThat(app.getMatchPanel().isVisible()).isTrue();
            assertThat(app.getServerPanel().isVisible()).isFalse();

            // Run a few frames to verify OpenGL works
            app.getWindow().runFrames(10);
        }

        @Test
        @DisplayName("Find and interact with components using locators")
        void findAndInteractWithComponents() throws Exception {
            // Initialize app on main thread
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            driver = GuiDriver.connect(app.getWindow());

            // Find all buttons
            List<GuiElement> buttons = driver.findElements(By.type(Button.class));
            assertThat(buttons).isNotEmpty();

            // Find the title label
            GuiElement titleLabel = driver.findElement(By.textContaining("Lightning Engine"));
            assertThat(titleLabel).isNotNull();

            // Dump component tree for debugging
            String tree = driver.dumpComponentTree();
            assertThat(tree).contains("Lightning Engine");
            log.info("Component Tree:\n" + tree);

            // Run a few frames to verify OpenGL works
            app.getWindow().runFrames(10);
        }
    }

    /**
     * Tests demonstrating the Page Object pattern with real OpenGL.
     * These show how to structure tests for better maintainability.
     */
    @Nested
    @DisplayName("Page Object Pattern Examples")
    @EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
    class PageObjectExamples {

        /**
         * Example Page Object for the main application navigation.
         */
        static class MainAppPage {
            private final GuiDriver driver;

            MainAppPage(GuiDriver driver) {
                this.driver = driver;
            }

            public void navigateToMatches() {
                driver.findElement(By.text("Matches")).click();
            }

            public void navigateToSnapshot() {
                driver.findElement(By.text("Snapshot")).click();
            }

            public void navigateToResources() {
                driver.findElement(By.text("Resources")).click();
            }

            public void navigateToModules() {
                driver.findElement(By.text("Modules")).click();
            }

            public boolean isNavButtonActive(String buttonText) {
                // Would check background color for active state
                return driver.hasElement(By.text(buttonText));
            }
        }

        /**
         * Example Page Object for the Match Panel.
         */
        static class MatchPanelPage {
            private final GuiDriver driver;

            MatchPanelPage(GuiDriver driver) {
                this.driver = driver;
            }

            public void waitForMatchList() {
                driver.waitFor(Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOf(By.type(
                        com.lightningfirefly.engine.rendering.render2d.ListView.class)));
            }

            public List<GuiElement> getMatchItems() {
                return driver.findElements(By.type(
                    com.lightningfirefly.engine.rendering.render2d.ListView.class));
            }

            public void selectMatch(int index) {
                // Would interact with list view to select a match
            }

            public void clickViewSnapshot() {
                driver.findElement(By.text("View Snapshot")).click();
            }

            public void clickCreateMatch() {
                driver.findElement(By.text("Create Match")).click();
            }
        }

        private EngineGuiApplication app;

        @AfterEach
        void tearDownPageObjectApp() {
            if (app != null) {
                app.stop();
                app = null;
            }
        }

        @Test
        @DisplayName("Demonstrate Page Object usage with real application")
        @EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
        void demonstratePageObjectUsage() throws Exception {
            final String backendUrl = System.getenv("BACKEND_URL") != null
                ? System.getenv("BACKEND_URL")
                : DEFAULT_SERVER_URL;

            // Initialize app on main thread (required for GLFW on macOS)
            app = new EngineGuiApplication(backendUrl);
            app.initialize();

            // Connect driver and create Page Objects
            driver = GuiDriver.connect(app.getWindow());
            MainAppPage mainPage = new MainAppPage(driver);

            // Verify we can use Page Object methods
            assertThat(mainPage.isNavButtonActive("Matches")).isTrue();

            // Navigate using Page Object
            mainPage.navigateToSnapshot();
            assertThat(app.getSnapshotPanel().isVisible()).isTrue();

            // Navigate to Resources
            mainPage.navigateToResources();
            assertThat(app.getResourcePanel().isVisible()).isTrue();

            // Back to Matches
            mainPage.navigateToMatches();
            assertThat(app.getMatchPanel().isVisible()).isTrue();

            // Run a few frames to verify OpenGL works
            app.getWindow().runFrames(10);
        }
    }

    /**
     * Documentation tests showing the framework's capabilities.
     */
    @Nested
    @DisplayName("Framework Capabilities Documentation")
    class CapabilitiesDocumentation {

        @Test
        @DisplayName("Document available locator strategies")
        void documentLocatorStrategies() {
            // This test documents all available locator strategies

            // By.id("elementId") - Find by unique component ID
            // By.text("Button Text") - Find by exact text match
            // By.textContaining("partial") - Find by partial text
            // By.type(Button.class) - Find by component type
            // By.title("Panel Title") - Find panels by title
            // By.and(locator1, locator2) - Combine with AND
            // By.or(locator1, locator2) - Combine with OR
            // By.type(X).within(By.title("Panel")) - Scoped search

            assertThat(By.id("test")).isNotNull();
            assertThat(By.text("test")).isNotNull();
            assertThat(By.textContaining("test")).isNotNull();
            assertThat(By.type(Button.class)).isNotNull();
            assertThat(By.title("test")).isNotNull();
            assertThat(By.and(By.id("a"), By.id("b"))).isNotNull();
            assertThat(By.or(By.id("a"), By.id("b"))).isNotNull();
        }

        @Test
        @DisplayName("Document wait conditions")
        void documentWaitConditions() {
            // This test documents all available wait conditions

            // ExpectedConditions.presenceOf(locator) - Element exists
            // ExpectedConditions.visibilityOf(locator) - Element is visible
            // ExpectedConditions.invisibilityOf(locator) - Element not visible
            // ExpectedConditions.textToBe(locator, "text") - Exact text
            // ExpectedConditions.textContains(locator, "partial") - Contains text
            // ExpectedConditions.elementCount(locator, n) - N elements found

            assertThat(ExpectedConditions.presenceOf(By.id("test"))).isNotNull();
            assertThat(ExpectedConditions.visibilityOf(By.id("test"))).isNotNull();
            assertThat(ExpectedConditions.invisibilityOf(By.id("test"))).isNotNull();
            assertThat(ExpectedConditions.textToBe(By.id("test"), "value")).isNotNull();
            assertThat(ExpectedConditions.textContains(By.id("test"), "partial")).isNotNull();
            assertThat(ExpectedConditions.elementCount(By.type(Button.class), 3)).isNotNull();
        }
    }
}
