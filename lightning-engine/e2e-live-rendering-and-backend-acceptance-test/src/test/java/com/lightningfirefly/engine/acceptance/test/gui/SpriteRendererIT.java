package com.lightningfirefly.engine.acceptance.test.gui;

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
 * <p>Tests sprite renderer panel functionality using actual OpenGL windows.
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
        window = WindowBuilder.create()
                .size(1000, 700)
                .title("SpriteRenderer Test")
                .build();

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
    @DisplayName("Given a window builder, when creating panel, then OpenGL window initializes")
    void givenWindowBuilder_whenCreatingPanel_thenOpenGLWindowInitializes() {
        assertThat(window).isNotNull();
        assertThat(window.getWidth()).isGreaterThan(0);
        assertThat(window.getHeight()).isGreaterThan(0);
        assertThat(window.getTitle()).isEqualTo("SpriteRenderer Test");

        window.runFrames(5);
        log.info("SpriteRendererPanel initialized successfully");
    }

    @Test
    @DisplayName("Given a panel, when checking UI components, then all components exist")
    void givenPanel_whenCheckingUIComponents_thenAllComponentsExist() {
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
    @DisplayName("Given a new panel, when checking defaults, then default values are correct")
    void givenNewPanel_whenCheckingDefaults_thenDefaultValuesAreCorrect() {
        assertThat(panel.getSpriteX()).isEqualTo(100);
        assertThat(panel.getSpriteY()).isEqualTo(100);
        assertThat(panel.getSpriteWidth()).isEqualTo(64);
        assertThat(panel.getSpriteHeight()).isEqualTo(64);
        assertThat(panel.getSpriteRotation()).isEqualTo(0);
        assertThat(panel.getSpriteZIndex()).isEqualTo(0);

        assertThat(panel.getXField().getText()).isEqualTo("100");
        assertThat(panel.getYField().getText()).isEqualTo("100");
        assertThat(panel.getWidthField().getText()).isEqualTo("64");
        assertThat(panel.getHeightField().getText()).isEqualTo("64");

        window.runFrames(5);
        log.info("Default property values verified");
    }

    @Test
    @DisplayName("Given a panel, when setting X property, then sprite position updates")
    void givenPanel_whenSettingXProperty_thenSpritePositionUpdates() {
        float initialX = panel.getSpriteX();

        panel.setProperty("x", "250");

        assertThat(panel.getSpriteX()).isEqualTo(250f);
        assertThat(panel.getSpriteX()).isNotEqualTo(initialX);

        window.runFrames(10);
        log.info("X property changed from {} to {}", initialX, panel.getSpriteX());
    }

    @Test
    @DisplayName("Given a panel, when setting Y property, then sprite position updates")
    void givenPanel_whenSettingYProperty_thenSpritePositionUpdates() {
        panel.setProperty("y", "300");
        assertThat(panel.getSpriteY()).isEqualTo(300f);

        window.runFrames(10);
        log.info("Y property changed to {}", panel.getSpriteY());
    }

    @Test
    @DisplayName("Given a panel, when setting size properties, then sprite size updates")
    void givenPanel_whenSettingSizeProperties_thenSpriteSizeUpdates() {
        panel.setProperty("width", "128");
        panel.setProperty("height", "96");

        assertThat(panel.getSpriteWidth()).isEqualTo(128f);
        assertThat(panel.getSpriteHeight()).isEqualTo(96f);

        window.runFrames(10);
        log.info("Size changed to {}x{}", panel.getSpriteWidth(), panel.getSpriteHeight());
    }

    @Test
    @DisplayName("Given a panel, when setting rotation, then sprite rotation updates")
    void givenPanel_whenSettingRotation_thenSpriteRotationUpdates() {
        panel.setProperty("rotation", "45");
        assertThat(panel.getSpriteRotation()).isEqualTo(45f);

        window.runFrames(10);
        log.info("Rotation changed to {}", panel.getSpriteRotation());
    }

    @Test
    @DisplayName("Given a panel, when setting z-index, then sprite z-index updates")
    void givenPanel_whenSettingZIndex_thenSpriteZIndexUpdates() {
        panel.setProperty("z", "5");
        assertThat(panel.getSpriteZIndex()).isEqualTo(5);

        window.runFrames(10);
        log.info("Z-index changed to {}", panel.getSpriteZIndex());
    }

    @Test
    @DisplayName("Given a panel, when setting multiple properties, then all reflect correctly")
    void givenPanel_whenSettingMultipleProperties_thenAllReflectCorrectly() {
        panel.setProperty("x", "150");
        panel.setProperty("y", "200");
        panel.setProperty("width", "80");
        panel.setProperty("height", "80");
        panel.setProperty("rotation", "30");
        panel.setProperty("z", "3");

        assertThat(panel.getSpriteX()).isEqualTo(150f);
        assertThat(panel.getSpriteY()).isEqualTo(200f);
        assertThat(panel.getSpriteWidth()).isEqualTo(80f);
        assertThat(panel.getSpriteHeight()).isEqualTo(80f);
        assertThat(panel.getSpriteRotation()).isEqualTo(30f);
        assertThat(panel.getSpriteZIndex()).isEqualTo(3);

        window.runFrames(10);
        log.info("All properties updated successfully");
    }

    @Test
    @DisplayName("Given a panel, when setting invalid values, then handled gracefully")
    void givenPanel_whenSettingInvalidValues_thenHandledGracefully() {
        float prevX = panel.getSpriteX();
        float prevY = panel.getSpriteY();

        panel.setProperty("x", "not-a-number");
        panel.setProperty("y", "");

        assertThat(panel.getSpriteX()).isEqualTo(prevX);
        assertThat(panel.getSpriteY()).isEqualTo(prevY);

        window.runFrames(5);
        log.info("Invalid values handled gracefully");
    }

    @Test
    @DisplayName("Given a panel, when running multiple frames with changes, then no errors")
    void givenPanel_whenRunningMultipleFramesWithChanges_thenNoErrors() {
        window.runFrames(20);
        panel.setProperty("x", "200");
        window.runFrames(20);
        panel.setProperty("y", "150");
        window.runFrames(20);
        panel.setProperty("width", "100");
        window.runFrames(20);

        assertThat(panel.getSpriteX()).isEqualTo(200f);
        assertThat(panel.getSpriteY()).isEqualTo(150f);
        assertThat(panel.getSpriteWidth()).isEqualTo(100f);

        log.info("Successfully ran 80+ frames with property changes");
    }

    @Test
    @DisplayName("Given a panel, when toggling visibility, then visibility changes")
    void givenPanel_whenTogglingVisibility_thenVisibilityChanges() {
        assertThat(panel.isVisible()).isTrue();
        window.runFrames(5);

        panel.setVisible(false);
        assertThat(panel.isVisible()).isFalse();
        window.runFrames(5);

        panel.setVisible(true);
        assertThat(panel.isVisible()).isTrue();
        window.runFrames(5);

        log.info("Panel visibility toggled successfully");
    }

    @Test
    @DisplayName("Given a panel, when loading image resource, then handled gracefully")
    void givenPanel_whenLoadingImageResource_thenHandledGracefully() {
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(30);

        assertThat(panel.getPreviewImage()).isNotNull();
        assertThat(panel.isVisible()).isTrue();

        panel.setProperty("width", "200");
        panel.setProperty("height", "150");
        assertThat(panel.getSpriteWidth()).isEqualTo(200f);
        assertThat(panel.getSpriteHeight()).isEqualTo(150f);

        log.info("Image resource loading handled gracefully");
    }

    @Test
    @DisplayName("Given a panel, when switching images, then preview updates")
    void givenPanel_whenSwitchingImages_thenPreviewUpdates() {
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(30);

        String firstName = panel.getLoadedResourceName();

        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(30);

        String secondName = panel.getLoadedResourceName();

        if (firstName != null && secondName != null) {
            assertThat(secondName).isNotEqualTo(firstName);
            assertThat(secondName).contains("black-checker");
        }

        panel.setProperty("x", "50");
        assertThat(panel.getSpriteX()).isEqualTo(50f);

        window.runFrames(10);
        log.info("Switched from '{}' to '{}'", firstName, secondName);
    }

    @Test
    @DisplayName("Given a panel, when rapidly switching images, then handled correctly")
    void givenPanel_whenRapidlySwitchingImages_thenHandledCorrectly() {
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(5);
        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(5);
        panel.loadImageResource("textures/red-checker.png");
        window.runFrames(5);
        panel.loadImageResource("textures/black-checker.png");
        window.runFrames(30);

        String loadedName = panel.getLoadedResourceName();
        if (loadedName != null) {
            assertThat(loadedName).contains("black-checker");
        }

        assertThat(panel.isVisible()).isTrue();
        panel.setProperty("rotation", "90");
        assertThat(panel.getSpriteRotation()).isEqualTo(90f);

        log.info("Final loaded resource: {}", loadedName);
    }
}
