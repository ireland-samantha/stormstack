package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance tests for resource management functionality.
 *
 * <p>Tests the business use cases for:
 * <ul>
 *   <li>Viewing resource list</li>
 *   <li>Uploading resources</li>
 *   <li>Downloading resources</li>
 *   <li>Deleting resources</li>
 * </ul>
 */
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Resource Management GUI Acceptance Tests")
@Testcontainers
class ResourceManagementGuiIT {

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
    private Path tempFile;

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);

        // Create a temp file for upload tests
        tempFile = Files.createTempFile("test-resource", ".dat");
        Files.writeString(tempFile, "Test resource content");
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.close();
        }
        if (app != null) {
            app.stop();
        }
        try {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    @DisplayName("View resources panel should show resource list")
    void viewResources_shouldShowResourceList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);

        // Refresh resources
        clickButton("Refresh");
        waitForResourcesLoaded();

        // Verify panel is visible
        var resourcePanel = app.getResourcePanel();
        assertThat(resourcePanel.isVisible()).as("Resource panel should be visible").isTrue();

        // Resource list may be empty initially, but the panel should work
        var resources = resourcePanel.getResources();
        // No assertion on count since backend may or may not have resources
    }

    @Test
    @DisplayName("Resources panel should have all control buttons")
    void resourcesPanel_shouldHaveControlButtons() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);

        // Verify all buttons exist
        assertThat(driver.hasElement(By.text("Refresh"))).as("Refresh button").isTrue();
        assertThat(driver.hasElement(By.text("Upload"))).as("Upload button").isTrue();
        assertThat(driver.hasElement(By.text("Download"))).as("Download button").isTrue();
        assertThat(driver.hasElement(By.text("Delete"))).as("Delete button").isTrue();
        assertThat(driver.hasElement(By.text("Browse"))).as("Browse button").isTrue();
        assertThat(driver.hasElement(By.text("Preview"))).as("Preview button").isTrue();
    }

    @Test
    @DisplayName("Upload resource via service should add to list")
    void uploadResource_shouldAddToList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);
        clickButton("Refresh");
        waitForResourcesLoaded();

        var resourcePanel = app.getResourcePanel();
        int initialCount = resourcePanel.getResources().size();

        // Upload via service (UI requires file dialog which is hard to test)
        Long resourceId = resourcePanel.getResourceService()
                .uploadResourceFromFile(tempFile, "TEXTURE")
                .get();

        assertThat(resourceId).as("Upload should return valid resource ID").isGreaterThan(0);

        // Refresh and verify
        clickButton("Refresh");
        waitForResourcesLoaded();

        var resources = resourcePanel.getResources();
        assertThat(resources.size())
                .as("Resource count should increase after upload")
                .isGreaterThan(initialCount);

        // Verify the uploaded resource exists
        boolean found = resources.stream()
                .anyMatch(r -> r.id() == resourceId);
        assertThat(found).as("Uploaded resource should be in list").isTrue();

        // Cleanup - delete the resource
        resourcePanel.getResourceService().deleteResource(resourceId).get();
    }

    @Test
    @DisplayName("Delete resource should remove from list")
    void deleteResource_shouldRemoveFromList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);

        var resourcePanel = app.getResourcePanel();

        // Upload a resource first
        Long resourceId = resourcePanel.getResourceService()
                .uploadResourceFromFile(tempFile, "TEXTURE")
                .get();

        clickButton("Refresh");
        waitForResourcesLoaded();

        var resourcesBefore = resourcePanel.getResources();
        assertThat(resourcesBefore.stream().anyMatch(r -> r.id() == resourceId))
                .as("Uploaded resource should exist").isTrue();

        // Delete the resource
        resourcePanel.getResourceService().deleteResource(resourceId).get();

        // Refresh and verify
        clickButton("Refresh");
        waitForResourcesLoaded();

        var resourcesAfter = resourcePanel.getResources();
        assertThat(resourcesAfter.stream().noneMatch(r -> r.id() == resourceId))
                .as("Deleted resource should not exist").isTrue();
    }

    @Test
    @DisplayName("Download resource should retrieve content")
    void downloadResource_shouldRetrieveContent() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);

        var resourcePanel = app.getResourcePanel();

        // Upload a resource first
        Long resourceId = resourcePanel.getResourceService()
                .uploadResourceFromFile(tempFile, "TEXTURE")
                .get();

        try {
            // Download the resource
            var contentOpt = resourcePanel.getResourceService()
                    .downloadResource(resourceId)
                    .get();

            assertThat(contentOpt).as("Download should return content").isPresent();
            byte[] content = contentOpt.get();
            assertThat(content).as("Downloaded content should not be empty").isNotEmpty();
            assertThat(new String(content))
                    .as("Downloaded content should match uploaded content")
                    .isEqualTo("Test resource content");
        } finally {
            // Cleanup
            resourcePanel.getResourceService().deleteResource(resourceId).get();
        }
    }

    @Test
    @DisplayName("Resource list should show resource metadata")
    void resourceList_shouldShowMetadata() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Resources panel
        clickButton("Resources");
        waitForUpdate(500);

        var resourcePanel = app.getResourcePanel();

        // Upload a resource
        Long resourceId = resourcePanel.getResourceService()
                .uploadResourceFromFile(tempFile, "TEXTURE")
                .get();

        try {
            clickButton("Refresh");
            waitForResourcesLoaded();

            var resources = resourcePanel.getResources();
            ResourceInfo uploaded = resources.stream()
                    .filter(r -> r.id() == resourceId)
                    .findFirst()
                    .orElse(null);

            assertThat(uploaded).as("Uploaded resource should be found").isNotNull();
            assertThat(uploaded.name()).as("Resource should have a name").isNotBlank();
            assertThat(uploaded.type()).as("Resource should have type").isEqualTo("TEXTURE");
        } finally {
            // Cleanup
            resourcePanel.getResourceService().deleteResource(resourceId).get();
        }
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
        if (app.getResourcePanel() != null) app.getResourcePanel().update();
        window.runFrames(3);
    }

    private void waitForResourcesLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            app.getResourcePanel().update();
            window.runFrames(2);
        }
    }
}
