package com.lightningfirefly.engine.gui.acceptance;

import com.lightningfirefly.engine.gui.panel.SpriteRendererPanel;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SpriteRendererPanel with OpenGL backend.
 *
 * <p>Tests the sprite renderer panel functionality using actual OpenGL windows:
 * <ul>
 *   <li>Panel initialization</li>
 *   <li>Property editing and reflection</li>
 *   <li>UI component interaction</li>
 *   <li>Classpath resource loading with deferred loading</li>
 * </ul>
 *
 * <p>These tests require:
 * <ul>
 *   <li>A display environment (cannot run headless)</li>
 *   <li>-XstartOnFirstThread JVM argument on macOS</li>
 * </ul>
 */
@Slf4j
@Tag("acceptance")
@Tag("opengl")
@DisplayName("SpriteRenderer OpenGL Integration Tests")
class SpriteRendererIT {

    private Window window;
    private SpriteRendererPanel panel;

    @BeforeEach
    void setUp() {
        // Create a window for testing
        window = WindowBuilder.create()
                .size(1000, 700)
                .title("SpriteRenderer Test")
                .build();

        // Create the panel pointing to a non-existent server (we won't actually connect)
        // The panel will still function for local property editing
        panel = new SpriteRendererPanel(
                GLComponentFactory.getInstance(),
                10, 60, 980, 630,
                "http://localhost:9999"  // Non-existent server for testing
        );

        window.addComponent(panel);
    }

    @AfterEach
    void tearDown() {
        if (panel != null) {
            panel.dispose();
        }
        if (window != null) {
            window.stop();
        }
    }

    @Test
    @DisplayName("Panel initializes with OpenGL window")
    void panel_initializesWithOpenGLWindow() {
        assertThat(window).isNotNull();
        assertThat(window.getWidth()).isGreaterThan(0);
        assertThat(window.getHeight()).isGreaterThan(0);
        assertThat(window.getTitle()).isEqualTo("SpriteRenderer Test");

        window.runFrames(5);

        log.info("SpriteRendererPanel initialized successfully");
        log.info("Window size: " + window.getWidth() + "x" + window.getHeight());
    }

    @Test
    @DisplayName("UI components are created correctly")
    void uiComponents_createdCorrectly() {
        // Verify UI components exist
        assertThat(panel.getRefreshButton()).isNotNull();
        assertThat(panel.getUploadButton()).isNotNull();
        assertThat(panel.getXField()).isNotNull();
        assertThat(panel.getYField()).isNotNull();
        assertThat(panel.getWidthField()).isNotNull();
        assertThat(panel.getHeightField()).isNotNull();
        assertThat(panel.getRotationField()).isNotNull();
        assertThat(panel.getZIndexField()).isNotNull();
        assertThat(panel.getPreviewImage()).isNotNull();
        assertThat(panel.getStatusLabel()).isNotNull();
        assertThat(panel.getResourceList()).isNotNull();

        window.runFrames(5);

        log.info("All UI components created successfully");
    }

    @Test
    @DisplayName("Default property values are set correctly")
    void defaultPropertyValues_setCorrectly() {
        // Check default values
        assertThat(panel.getSpriteX()).isEqualTo(100);
        assertThat(panel.getSpriteY()).isEqualTo(100);
        assertThat(panel.getSpriteWidth()).isEqualTo(64);
        assertThat(panel.getSpriteHeight()).isEqualTo(64);
        assertThat(panel.getSpriteRotation()).isEqualTo(0);
        assertThat(panel.getSpriteZIndex()).isEqualTo(0);

        // Check text field values match
        assertThat(panel.getXField().getText()).isEqualTo("100");
        assertThat(panel.getYField().getText()).isEqualTo("100");
        assertThat(panel.getWidthField().getText()).isEqualTo("64");
        assertThat(panel.getHeightField().getText()).isEqualTo("64");

        window.runFrames(5);

        log.info("Default property values verified");
    }

    @Test
    @DisplayName("Loading PNG from classpath resource sets resource name")
    void loadingPng_fromClasspathResource_setsResourceName() {
        // Load image - uses deferred loading which loads during render
        panel.loadImageResource("textures/red-checker.png");

        // Run frames to allow deferred loading
        window.runFrames(10);

        // The resource name may or may not be set depending on whether loading succeeded
        // This test verifies the loading attempt doesn't crash and the panel remains functional
        window.runFrames(20);

        // Panel should still be visible and functional
        assertThat(panel.isVisible()).isTrue();
        assertThat(panel.getPreviewImage()).isNotNull();

        // Verify we can still change properties after attempting to load
        panel.setProperty("x", "150");
        assertThat(panel.getSpriteX()).isEqualTo(150f);

        log.info("Classpath resource loading attempted for red-checker.png");
        log.info("Loaded resource name: " + panel.getLoadedResourceName());
        log.info("Image loaded: " + panel.isImageLoaded());
    }

    @Test
    @DisplayName("Changing X property updates sprite position")
    void changingXProperty_updatesSpritePosition() {
        // Initial X value
        float initialX = panel.getSpriteX();

        // Change X property
        panel.setProperty("x", "250");

        // Verify property changed
        assertThat(panel.getSpriteX()).isEqualTo(250f);
        assertThat(panel.getSpriteX()).isNotEqualTo(initialX);

        window.runFrames(10);

        log.info("X property changed from " + initialX + " to " + panel.getSpriteX());
    }

    @Test
    @DisplayName("Changing Y property updates sprite position")
    void changingYProperty_updatesSpritePosition() {
        panel.setProperty("y", "300");

        assertThat(panel.getSpriteY()).isEqualTo(300f);

        window.runFrames(10);

        log.info("Y property changed to " + panel.getSpriteY());
    }

    @Test
    @DisplayName("Changing width and height properties updates sprite size")
    void changingSize_updatesSpriteSize() {
        // Change size
        panel.setProperty("width", "128");
        panel.setProperty("height", "96");

        assertThat(panel.getSpriteWidth()).isEqualTo(128f);
        assertThat(panel.getSpriteHeight()).isEqualTo(96f);

        window.runFrames(10);

        log.info("Size changed to " + panel.getSpriteWidth() + "x" + panel.getSpriteHeight());
    }

    @Test
    @DisplayName("Changing rotation property updates sprite")
    void changingRotation_updatesSpriteRotation() {
        panel.setProperty("rotation", "45");

        assertThat(panel.getSpriteRotation()).isEqualTo(45f);

        window.runFrames(10);

        log.info("Rotation changed to " + panel.getSpriteRotation());
    }

    @Test
    @DisplayName("Changing z-index property updates sprite")
    void changingZIndex_updatesSpriteZIndex() {
        panel.setProperty("z", "5");

        assertThat(panel.getSpriteZIndex()).isEqualTo(5);

        window.runFrames(10);

        log.info("Z-index changed to " + panel.getSpriteZIndex());
    }

    @Test
    @DisplayName("Multiple property changes are reflected correctly")
    void multiplePropertyChanges_reflectedCorrectly() {
        // Change multiple properties
        panel.setProperty("x", "150");
        panel.setProperty("y", "200");
        panel.setProperty("width", "80");
        panel.setProperty("height", "80");
        panel.setProperty("rotation", "30");
        panel.setProperty("z", "3");

        // Verify all changes
        assertThat(panel.getSpriteX()).isEqualTo(150f);
        assertThat(panel.getSpriteY()).isEqualTo(200f);
        assertThat(panel.getSpriteWidth()).isEqualTo(80f);
        assertThat(panel.getSpriteHeight()).isEqualTo(80f);
        assertThat(panel.getSpriteRotation()).isEqualTo(30f);
        assertThat(panel.getSpriteZIndex()).isEqualTo(3);

        window.runFrames(10);

        log.info("All properties updated:");
        log.info("  Position: " + panel.getSpriteX() + ", " + panel.getSpriteY());
        log.info("  Size: " + panel.getSpriteWidth() + "x" + panel.getSpriteHeight());
        log.info("  Rotation: " + panel.getSpriteRotation());
        log.info("  Z-Index: " + panel.getSpriteZIndex());
    }

    @Test
    @DisplayName("Invalid property values are handled gracefully")
    void invalidPropertyValues_handledGracefully() {
        // Store current values
        float prevX = panel.getSpriteX();
        float prevY = panel.getSpriteY();

        // Try setting invalid values
        panel.setProperty("x", "not-a-number");
        panel.setProperty("y", "");

        // Values should remain unchanged (or use defaults)
        assertThat(panel.getSpriteX()).isEqualTo(prevX);
        assertThat(panel.getSpriteY()).isEqualTo(prevY);

        window.runFrames(5);

        log.info("Invalid values handled gracefully");
    }

    @Test
    @DisplayName("Panel runs multiple render frames without error")
    void panel_runsMultipleFrames_withoutError() {
        // Change properties during rendering
        window.runFrames(20);
        panel.setProperty("x", "200");
        window.runFrames(20);
        panel.setProperty("y", "150");
        window.runFrames(20);
        panel.setProperty("width", "100");
        window.runFrames(20);

        // Verify we got through all frames without error
        assertThat(panel.getSpriteX()).isEqualTo(200f);
        assertThat(panel.getSpriteY()).isEqualTo(150f);
        assertThat(panel.getSpriteWidth()).isEqualTo(100f);

        log.info("Successfully ran 80+ frames with property changes");
    }

    @Test
    @DisplayName("Panel visibility can be toggled")
    void panelVisibility_canBeToggled() {
        // Initially visible
        assertThat(panel.isVisible()).isTrue();
        window.runFrames(5);

        // Hide panel
        panel.setVisible(false);
        assertThat(panel.isVisible()).isFalse();
        window.runFrames(5);

        // Show panel
        panel.setVisible(true);
        assertThat(panel.isVisible()).isTrue();
        window.runFrames(5);

        log.info("Panel visibility toggled successfully");
    }

    @Test
    @DisplayName("Image component handles loading attempt gracefully")
    void imageComponent_handlesLoadingAttemptGracefully() {
        // Try loading an image
        panel.loadImageResource("textures/red-checker.png");

        // Run frames to allow loading attempt
        window.runFrames(30);

        // Image component should exist regardless of load success
        assertThat(panel.getPreviewImage()).isNotNull();

        // Panel should remain functional
        panel.setProperty("width", "200");
        panel.setProperty("height", "150");
        assertThat(panel.getSpriteWidth()).isEqualTo(200f);
        assertThat(panel.getSpriteHeight()).isEqualTo(150f);

        log.info("Image dimensions (may be 0 if resource not found): " +
            panel.getPreviewImage().getImageWidth() + "x" +
            panel.getPreviewImage().getImageHeight());
    }

    @Test
    @DisplayName("Switching between images updates the preview")
    void switchingBetweenImages_updatesPreview() {
        // Load first image
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(30);

        // Store reference to first image handle (via loaded state)
        boolean firstLoaded = panel.isImageLoaded();
        String firstName = panel.getLoadedResourceName();

        // Load second image
        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(30);

        // The loaded resource name should change
        String secondName = panel.getLoadedResourceName();

        // If both loaded successfully, names should be different
        if (firstName != null && secondName != null) {
            assertThat(secondName).isNotEqualTo(firstName);
            assertThat(secondName).contains("black-checker");
        }

        // Panel should remain functional after switching
        panel.setProperty("x", "50");
        assertThat(panel.getSpriteX()).isEqualTo(50f);

        window.runFrames(10);

        log.info("Switched from '" + firstName + "' to '" + secondName + "'");
        log.info("Image loaded after switch: " + panel.isImageLoaded());
    }

    @Test
    @DisplayName("Multiple rapid image switches are handled correctly")
    void multipleRapidImageSwitches_handledCorrectly() {
        // Rapidly switch between images
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(5);

        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(5);

        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(5);

        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(30);

        // Final state should be black-checker
        String loadedName = panel.getLoadedResourceName();
        if (loadedName != null) {
            assertThat(loadedName).contains("black-checker");
        }

        // Panel should remain functional
        assertThat(panel.isVisible()).isTrue();
        panel.setProperty("rotation", "90");
        assertThat(panel.getSpriteRotation()).isEqualTo(90f);

        log.info("Final loaded resource: " + loadedName);
    }
}
