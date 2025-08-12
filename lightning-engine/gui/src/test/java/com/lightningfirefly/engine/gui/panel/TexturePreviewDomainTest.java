package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.Image;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain integration test for the Texture Preview workflow.
 *
 * <p>This test validates the complete texture preview workflow:
 * <ol>
 *   <li>Navigate to Resources panel</li>
 *   <li>Upload a texture file (red-checker.png)</li>
 *   <li>Select the uploaded texture</li>
 *   <li>Click Preview and verify the preview panel shows</li>
 *   <li>Verify the image is loaded in the preview</li>
 *   <li>Close the preview</li>
 *   <li>Delete the uploaded resource</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 * BACKEND_URL=http://localhost:8080 ./mvnw test -pl lightning-engine/gui \
 *     -Dtest=TexturePreviewDomainTest -DenableGLTests=true
 * </pre>
 */
@Slf4j
@Tag("integration")
@Tag("domain")
@DisplayName("Texture Preview Domain Integration Test")
@EnabledIfSystemProperty(named = "enableGLTests", matches = "true")
@EnabledIfEnvironmentVariable(named = "BACKEND_URL", matches = ".+")
class TexturePreviewDomainTest {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final String TEST_TEXTURE_PATH = "textures/red-checker.png";

    private EngineGuiApplication app;
    private GuiDriver driver;
    private Window window;
    private String backendUrl;
    private long uploadedResourceId = -1;

    @BeforeEach
    void setUp() {
        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null || backendUrl.isEmpty()) {
            backendUrl = DEFAULT_SERVER_URL;
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up uploaded resource if any
        if (uploadedResourceId > 0 && app != null && app.getResourcePanel() != null) {
            try {
                app.getResourcePanel().getResourceService().deleteResource(uploadedResourceId).get();
                log.info("Cleaned up resource ID: " + uploadedResourceId);
            } catch (Exception e) {
                log.info("Failed to clean up resource: " + e.getMessage());
            }
        }

        if (driver != null) {
            driver.close();
            driver = null;
        }
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    @Test
    @DisplayName("Complete texture preview workflow: upload, preview, verify, delete")
    void completeTexturePreviewWorkflow() throws Exception {
        log.info("=== Starting Texture Preview test with backend: " + backendUrl + " ===");

        // Initialize the application
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);

        // Run a few frames to let UI initialize
        window.runFrames(5);
        log.info("Window initialized, running frames...");

        // ===== STEP 1: Navigate to Resources panel =====
        log.info("=== STEP 1: Navigate to Resources panel ===");
        clickButton("Resources");
        waitForUpdate(500);

        // Verify resource panel is visible
        ResourcePanel resourcePanel = app.getResourcePanel();
        assertThat(resourcePanel).as("Resource panel should exist").isNotNull();
        assertThat(resourcePanel.isVisible()).as("Resource panel should be visible").isTrue();

        // ===== STEP 2: Refresh resources to get current list =====
        log.info("=== STEP 2: Refresh resources ===");
        clickButton("Refresh");
        waitForResourcesLoaded();

        int initialResourceCount = resourcePanel.getResources().size();
        log.info("Initial resource count: " + initialResourceCount);

        // ===== STEP 3: Upload a texture file =====
        log.info("=== STEP 3: Upload texture file ===");

        // Get the absolute path to the test texture
        Path texturePath = getTestTexturePath();
        log.info("Uploading texture from: " + texturePath);

        // Set the file path and trigger upload
        resourcePanel.setFilePath(texturePath.toAbsolutePath().toString());
        window.runFrames(2);
        assertThat(resourcePanel.getFilePath())
            .as("File path should be set")
            .isEqualTo(texturePath.toAbsolutePath().toString());

        resourcePanel.uploadSelectedFile();
        waitForUpdate(1500); // Wait for upload to complete

        // Refresh and verify upload succeeded
        clickButton("Refresh");
        waitForResourcesLoaded();

        List<ResourceInfo> resourcesAfterUpload = resourcePanel.getResources();
        log.info("Resources after upload: " + resourcesAfterUpload.size());
        assertThat(resourcesAfterUpload.size())
            .as("Resource should be uploaded")
            .isGreaterThan(initialResourceCount);

        // Find the uploaded resource (should be the newest one with red-checker in the name)
        ResourceInfo uploadedResource = resourcesAfterUpload.stream()
            .filter(r -> r.name().contains("red-checker"))
            .findFirst()
            .orElse(null);

        if (uploadedResource == null && resourcesAfterUpload.size() > initialResourceCount) {
            // Fallback: get the resource with the highest ID
            uploadedResource = resourcesAfterUpload.stream()
                .max((a, b) -> Long.compare(a.id(), b.id()))
                .orElse(null);
        }

        assertThat(uploadedResource)
            .as("Uploaded resource should be found")
            .isNotNull();

        uploadedResourceId = uploadedResource.id();
        log.info("Uploaded resource ID: " + uploadedResourceId + ", name: " + uploadedResource.name());

        // ===== STEP 4: Select the uploaded resource =====
        log.info("=== STEP 4: Select the uploaded resource ===");

        // Find the index of the uploaded resource
        int resourceIndex = -1;
        for (int i = 0; i < resourcesAfterUpload.size(); i++) {
            if (resourcesAfterUpload.get(i).id() == uploadedResourceId) {
                resourceIndex = i;
                break;
            }
        }
        assertThat(resourceIndex).as("Resource index should be found").isGreaterThanOrEqualTo(0);

        resourcePanel.selectResource(resourceIndex);
        window.runFrames(2);
        assertThat(resourcePanel.getSelectedResourceIndex())
            .as("Resource should be selected")
            .isEqualTo(resourceIndex);

        // ===== STEP 5: Click Preview and verify preview panel shows =====
        log.info("=== STEP 5: Preview the texture ===");

        // Trigger preview
        resourcePanel.previewSelectedResource();
        waitForUpdate(1000); // Wait for download and preview to complete

        // Verify preview panel is visible
        TexturePreviewPanel previewPanel = resourcePanel.getPreviewPanel();
        assertThat(previewPanel)
            .as("Preview panel should be created")
            .isNotNull();
        assertThat(previewPanel.isVisible())
            .as("Preview panel should be visible")
            .isTrue();

        // Verify texture name is set
        String textureName = previewPanel.getTextureName();
        log.info("Preview texture name: " + textureName);
        assertThat(textureName)
            .as("Texture name should be set")
            .isNotNull()
            .isNotEmpty();

        // Verify texture path is set
        String texturePathStr = previewPanel.getTexturePath();
        log.info("Preview texture path: " + texturePathStr);
        assertThat(texturePathStr)
            .as("Texture path should be set")
            .isNotNull()
            .isNotEmpty();

        // ===== STEP 6: Verify the image component is loaded =====
        log.info("=== STEP 6: Verify image is loaded ===");

        Image previewImage = previewPanel.getPreviewImage();
        assertThat(previewImage)
            .as("Preview image component should exist")
            .isNotNull();

        // Run frames to allow deferred image loading
        window.runFrames(10);

        // The image should be visible
        assertThat(previewImage.isVisible())
            .as("Preview image should be visible")
            .isTrue();

        // Verify the image was actually loaded (deferred loading should complete during render)
        log.info("Image loaded: " + previewImage.isLoaded());
        log.info("Image dimensions: " + previewImage.getImageWidth() + "x" + previewImage.getImageHeight());

        // After running frames, the deferred loading should have completed
        assertThat(previewImage.isLoaded())
            .as("Preview image should be loaded after running frames")
            .isTrue();

        // Verify image has actual dimensions (not 0x0)
        assertThat(previewImage.getImageWidth())
            .as("Image width should be positive after loading")
            .isGreaterThan(0);
        assertThat(previewImage.getImageHeight())
            .as("Image height should be positive after loading")
            .isGreaterThan(0);

        // ===== STEP 7: Close the preview =====
        log.info("=== STEP 7: Close the preview ===");

        // Click the Close button in the preview panel
        clickButton("Close");
        window.runFrames(2);

        assertThat(previewPanel.isVisible())
            .as("Preview panel should be hidden after close")
            .isFalse();

        // ===== STEP 8: Delete the uploaded resource =====
        log.info("=== STEP 8: Delete the uploaded resource ===");

        // Re-select the resource (selection might have been lost)
        resourcePanel.selectResource(resourceIndex);
        window.runFrames(2);

        resourcePanel.deleteSelectedResource();
        waitForUpdate(1000);

        // Refresh and verify deletion
        clickButton("Refresh");
        waitForResourcesLoaded();

        List<ResourceInfo> resourcesAfterDelete = resourcePanel.getResources();
        log.info("Resources after delete: " + resourcesAfterDelete.size());

        boolean resourceStillExists = resourcesAfterDelete.stream()
            .anyMatch(r -> r.id() == uploadedResourceId);

        assertThat(resourceStillExists)
            .as("Uploaded resource should be deleted")
            .isFalse();

        // Mark as cleaned up so tearDown doesn't try to delete again
        uploadedResourceId = -1;

        log.info("=== TEST PASSED ===");
    }

    // ========== Helper Methods ==========

    private Path getTestTexturePath() {
        // Try to find the texture in the classpath
        java.net.URL resource = getClass().getClassLoader().getResource(TEST_TEXTURE_PATH);
        if (resource != null) {
            try {
                return Path.of(resource.toURI());
            } catch (Exception e) {
                // Fall through
            }
        }

        // Try the direct path in the project
        Path projectPath = Path.of("lightning-engine/rendering-core/src/main/resources/" + TEST_TEXTURE_PATH);
        if (java.nio.file.Files.exists(projectPath)) {
            return projectPath.toAbsolutePath();
        }

        // Try absolute path from working directory
        Path absolutePath = Path.of(System.getProperty("user.dir"))
            .resolve("lightning-engine/rendering-core/src/main/resources/" + TEST_TEXTURE_PATH);
        if (java.nio.file.Files.exists(absolutePath)) {
            return absolutePath;
        }

        throw new RuntimeException("Could not find test texture: " + TEST_TEXTURE_PATH);
    }

    private void clickButton(String text) {
        driver.refreshRegistry();
        if (driver.hasElement(By.text(text))) {
            driver.findElement(By.text(text)).click();
            window.runFrames(2);
        } else {
            log.info("WARNING: Button '" + text + "' not found");
        }
    }

    private void waitForUpdate(long millis) throws InterruptedException {
        Thread.sleep(millis);
        if (app.getResourcePanel() != null) {
            app.getResourcePanel().update();
        }
        window.runFrames(3);
    }

    private void waitForResourcesLoaded() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            app.getResourcePanel().update();
            window.runFrames(2);
            if (i % 5 == 0) {
                log.info("  Wait iteration " + i + ": " + app.getResourcePanel().getResources().size() + " resources");
            }
        }
    }
}
