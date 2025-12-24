package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.EngineGuiApplication;
import com.lightningfirefly.engine.gui.panel.SpriteRendererPanel;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.testing.By;
import com.lightningfirefly.engine.rendering.testing.GuiDriver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GUI acceptance tests for the Sprite Renderer panel.
 *
 * <p>Tests the business use cases for:
 * <ul>
 *   <li>Viewing sprite renderer panel</li>
 *   <li>Loading sprites from server resources</li>
 *   <li>Uploading PNG files as resources</li>
 *   <li>Editing sprite properties</li>
 *   <li>Real-time property reflection</li>
 * </ul>
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@DisplayName("Sprite Renderer Panel Acceptance Tests")
@Testcontainers
class SpriteRendererPanelIT {

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
    private Path tempPngFile;

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        backendUrl = String.format("http://%s:%d", host, port);

        // Create a temp PNG file for upload tests
        tempPngFile = createTestPngFile();
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
            if (tempPngFile != null) {
                Files.deleteIfExists(tempPngFile);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    @DisplayName("Navigate to Sprites panel should show sprite renderer")
    void navigateToSpritesPanel_shouldShowSpriteRenderer() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        // Verify panel is visible
        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();
        assertThat(spritePanel.isVisible()).as("Sprite panel should be visible").isTrue();

        log.info("Sprite Renderer panel loaded successfully");
    }

    @Test
    @DisplayName("Sprites panel should have all UI components")
    void spritesPanel_shouldHaveAllUIComponents() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        // Verify key UI elements exist
        assertThat(driver.hasElement(By.text("Refresh"))).as("Refresh button").isTrue();
        assertThat(driver.hasElement(By.text("Upload PNG"))).as("Upload PNG button").isTrue();

        // Verify property fields via panel
        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();
        assertThat(spritePanel.getXField()).as("X field").isNotNull();
        assertThat(spritePanel.getYField()).as("Y field").isNotNull();
        assertThat(spritePanel.getWidthField()).as("Width field").isNotNull();
        assertThat(spritePanel.getHeightField()).as("Height field").isNotNull();
        assertThat(spritePanel.getRotationField()).as("Rotation field").isNotNull();
        assertThat(spritePanel.getZIndexField()).as("Z-Index field").isNotNull();
        assertThat(spritePanel.getResourceList()).as("Resource list").isNotNull();
        assertThat(spritePanel.getPreviewImage()).as("Preview image").isNotNull();

        log.info("All Sprite Renderer UI components verified");
    }

    @Test
    @DisplayName("Default property values should be set correctly")
    void defaultPropertyValues_shouldBeSetCorrectly() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Check default values
        assertThat(spritePanel.getSpriteX()).isEqualTo(100);
        assertThat(spritePanel.getSpriteY()).isEqualTo(100);
        assertThat(spritePanel.getSpriteWidth()).isEqualTo(64);
        assertThat(spritePanel.getSpriteHeight()).isEqualTo(64);
        assertThat(spritePanel.getSpriteRotation()).isEqualTo(0);
        assertThat(spritePanel.getSpriteZIndex()).isEqualTo(0);

        // Check text field values match
        assertThat(spritePanel.getXField().getText()).isEqualTo("100");
        assertThat(spritePanel.getYField().getText()).isEqualTo("100");
        assertThat(spritePanel.getWidthField().getText()).isEqualTo("64");
        assertThat(spritePanel.getHeightField().getText()).isEqualTo("64");

        log.info("Default property values verified");
    }

    @Test
    @DisplayName("Upload PNG should add resource to list")
    void uploadPng_shouldAddResourceToList() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Refresh to get initial count
        clickButton("Refresh");
        waitForResourcesLoaded();
        int initialCount = spritePanel.getResources().size();

        // Upload via service (UI upload requires file dialog)
        Long resourceId = spritePanel.getResourceService()
                .uploadResourceFromFile(tempPngFile, "TEXTURE")
                .get();

        assertThat(resourceId).as("Upload should return valid resource ID").isGreaterThan(0);

        // Refresh and verify
        clickButton("Refresh");
        waitForResourcesLoaded();

        var resources = spritePanel.getResources();
        assertThat(resources.size())
                .as("Resource count should increase after upload")
                .isGreaterThan(initialCount);

        // Verify the uploaded resource exists
        boolean found = resources.stream().anyMatch(r -> r.id() == resourceId);
        assertThat(found).as("Uploaded resource should be in list").isTrue();

        log.info("Uploaded PNG resource ID: " + resourceId);

        // Cleanup
        spritePanel.getResourceService().deleteResource(resourceId).get();
    }

    @Test
    @DisplayName("Selecting resource should load sprite for preview")
    void selectingResource_shouldLoadSpriteForPreview() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Upload a resource first
        Long resourceId = spritePanel.getResourceService()
                .uploadResourceFromFile(tempPngFile, "TEXTURE")
                .get();

        try {
            // Refresh to show the resource
            clickButton("Refresh");
            waitForResourcesLoaded();

            // Find and load the resource
            var resources = spritePanel.getResources();
            ResourceInfo uploadedResource = resources.stream()
                    .filter(r -> r.id() == resourceId)
                    .findFirst()
                    .orElse(null);

            assertThat(uploadedResource).as("Uploaded resource should be found").isNotNull();

            // Load the resource as sprite
            spritePanel.loadResourceAsSprite(uploadedResource);
            waitForUpdate(1000);
            window.runFrames(20);

            // Verify resource is loaded
            assertThat(spritePanel.getLoadedResourceId()).as("Loaded resource ID").isEqualTo(resourceId);
            assertThat(spritePanel.getLoadedResourceName()).as("Loaded resource name").isNotNull();

            log.info("Resource loaded as sprite: " + uploadedResource.name());
        } finally {
            // Cleanup
            spritePanel.getResourceService().deleteResource(resourceId).get();
        }
    }

    @Test
    @DisplayName("Changing X property should update sprite position")
    void changingXProperty_shouldUpdateSpritePosition() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Store initial X value
        float initialX = spritePanel.getSpriteX();

        // Change X property
        spritePanel.setProperty("x", "250");

        // Verify property changed
        assertThat(spritePanel.getSpriteX()).isEqualTo(250f);
        assertThat(spritePanel.getSpriteX()).isNotEqualTo(initialX);

        window.runFrames(10);

        log.info("X property changed from " + initialX + " to " + spritePanel.getSpriteX());
    }

    @Test
    @DisplayName("Multiple property changes should be reflected correctly")
    void multiplePropertyChanges_shouldBeReflectedCorrectly() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Change multiple properties
        spritePanel.setProperty("x", "150");
        spritePanel.setProperty("y", "200");
        spritePanel.setProperty("width", "80");
        spritePanel.setProperty("height", "80");
        spritePanel.setProperty("rotation", "30");
        spritePanel.setProperty("z", "3");

        // Verify all changes
        assertThat(spritePanel.getSpriteX()).isEqualTo(150f);
        assertThat(spritePanel.getSpriteY()).isEqualTo(200f);
        assertThat(spritePanel.getSpriteWidth()).isEqualTo(80f);
        assertThat(spritePanel.getSpriteHeight()).isEqualTo(80f);
        assertThat(spritePanel.getSpriteRotation()).isEqualTo(30f);
        assertThat(spritePanel.getSpriteZIndex()).isEqualTo(3);

        window.runFrames(10);

        log.info("All properties updated:");
        log.info("  Position: " + spritePanel.getSpriteX() + ", " + spritePanel.getSpriteY());
        log.info("  Size: " + spritePanel.getSpriteWidth() + "x" + spritePanel.getSpriteHeight());
        log.info("  Rotation: " + spritePanel.getSpriteRotation());
        log.info("  Z-Index: " + spritePanel.getSpriteZIndex());
    }

    @Test
    @DisplayName("Invalid property values should be handled gracefully")
    void invalidPropertyValues_shouldBeHandledGracefully() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Store current values
        float prevX = spritePanel.getSpriteX();
        float prevY = spritePanel.getSpriteY();

        // Try setting invalid values
        spritePanel.setProperty("x", "not-a-number");
        spritePanel.setProperty("y", "");

        // Values should remain unchanged
        assertThat(spritePanel.getSpriteX()).isEqualTo(prevX);
        assertThat(spritePanel.getSpriteY()).isEqualTo(prevY);

        window.runFrames(5);

        log.info("Invalid values handled gracefully");
    }

    @Test
    @DisplayName("Resource list should show texture resources")
    void resourceList_shouldShowTextureResources() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Upload multiple resources
        Long resourceId1 = spritePanel.getResourceService()
                .uploadResourceFromFile(tempPngFile, "TEXTURE")
                .get();

        Path tempPng2 = createTestPngFile();
        Long resourceId2 = spritePanel.getResourceService()
                .uploadResourceFromFile(tempPng2, "TEXTURE")
                .get();

        try {
            clickButton("Refresh");
            waitForResourcesLoaded();

            var resources = spritePanel.getResources();
            assertThat(resources.size()).as("Should have at least 2 resources").isGreaterThanOrEqualTo(2);

            // Verify both uploaded resources exist
            boolean found1 = resources.stream().anyMatch(r -> r.id() == resourceId1);
            boolean found2 = resources.stream().anyMatch(r -> r.id() == resourceId2);
            assertThat(found1).as("First resource should exist").isTrue();
            assertThat(found2).as("Second resource should exist").isTrue();

            log.info("Resource list shows " + resources.size() + " texture resources");
        } finally {
            // Cleanup
            spritePanel.getResourceService().deleteResource(resourceId1).get();
            spritePanel.getResourceService().deleteResource(resourceId2).get();
            Files.deleteIfExists(tempPng2);
        }
    }

    @Test
    @DisplayName("Switching panels should preserve sprite state")
    void switchingPanels_shouldPreserveSpriteState() throws Exception {
        // Initialize GUI
        app = new EngineGuiApplication(backendUrl);
        app.initialize();
        window = app.getWindow();
        driver = GuiDriver.connect(window);
        window.runFrames(5);

        // Navigate to Sprites panel
        clickButton("Sprites");
        waitForUpdate(500);

        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();

        // Set some properties
        spritePanel.setProperty("x", "200");
        spritePanel.setProperty("y", "150");

        // Switch to another panel
        clickButton("Resources");
        waitForUpdate(500);

        // Switch back to Sprites
        clickButton("Sprites");
        waitForUpdate(500);

        // Verify state is preserved
        assertThat(spritePanel.getSpriteX()).isEqualTo(200f);
        assertThat(spritePanel.getSpriteY()).isEqualTo(150f);

        log.info("Panel state preserved after switching");
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
        SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();
        if (spritePanel != null) spritePanel.update();
        window.runFrames(3);
    }

    private void waitForResourcesLoaded() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            SpriteRendererPanel spritePanel = app.getSpriteRendererPanel();
            if (spritePanel != null) spritePanel.update();
            window.runFrames(2);
        }
    }

    private Path createTestPngFile() throws Exception {
        // Create a simple 32x32 red PNG
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                image.setRGB(x, y, 0xFFFF0000); // Red
            }
        }

        Path tempFile = Files.createTempFile("test-sprite", ".png");
        ImageIO.write(image, "PNG", tempFile.toFile());
        return tempFile;
    }
}
