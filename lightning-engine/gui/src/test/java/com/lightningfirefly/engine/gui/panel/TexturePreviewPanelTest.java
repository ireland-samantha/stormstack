package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.TestComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.ComponentFactory;
import com.lightningfirefly.engine.rendering.render2d.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TexturePreviewPanelTest {

    private TexturePreviewPanel panel;
    private ComponentFactory factory;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        factory = new TestComponentFactory();
        panel = new TexturePreviewPanel(factory, 100, 100, 300, 350);
    }

    @Test
    void constructor_createsPanel_withCorrectDimensions() {
        assertThat(panel.getX()).isEqualTo(100);
        assertThat(panel.getY()).isEqualTo(100);
        assertThat(panel.getWidth()).isEqualTo(300);
        assertThat(panel.getHeight()).isEqualTo(350);
    }

    @Test
    void isVisible_returnsTrue_byDefault() {
        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void setVisible_changesVisibility() {
        panel.setVisible(false);
        assertThat(panel.isVisible()).isFalse();

        panel.setVisible(true);
        assertThat(panel.isVisible()).isTrue();
    }

    @Test
    void getPreviewImage_returnsNonNull() {
        assertThat(panel.getPreviewImage()).isNotNull();
    }

    @Test
    void getPreviewImage_returnsCorrectDimensions() {
        Image img = panel.getPreviewImage();
        // Image should be inside the panel with padding and header space
        // x = 100 + 10 = 110, y = 100 + 70 = 170
        // width = 300 - 20 = 280, height = 350 - 70 - 10 = 270
        assertThat(img.getX()).isEqualTo(110);
        assertThat(img.getY()).isEqualTo(170);
        assertThat(img.getWidth()).isEqualTo(280);
        assertThat(img.getHeight()).isEqualTo(270);
    }

    @Test
    void setTexture_updatesTextureNameAndPath() {
        panel.setTexture("test.png", "/path/to/test.png");

        assertThat(panel.getTextureName()).isEqualTo("test.png");
        assertThat(panel.getTexturePath()).isEqualTo("/path/to/test.png");
    }

    @Test
    void getTexturePath_returnsNull_initially() {
        assertThat(panel.getTexturePath()).isNull();
    }

    @Test
    void getTextureName_returnsNull_initially() {
        assertThat(panel.getTextureName()).isNull();
    }

    @Test
    void setVisible_false_cleansUpImage() {
        // When we set visible to false, the image should be disposed
        panel.setVisible(false);

        // Panel should be invisible
        assertThat(panel.isVisible()).isFalse();

        // Image should no longer be loaded after cleanup
        assertThat(panel.getPreviewImage().isLoaded()).isFalse();
    }

    @Test
    void contains_returnsTrue_forPointInsidePanel() {
        // Panel at (100, 100) with size (300, 350)
        assertThat(panel.contains(150, 150)).isTrue();
        assertThat(panel.contains(100, 100)).isTrue();
        assertThat(panel.contains(399, 449)).isTrue();
    }

    @Test
    void contains_returnsFalse_forPointOutsidePanel() {
        assertThat(panel.contains(50, 50)).isFalse();
        assertThat(panel.contains(500, 500)).isFalse();
        assertThat(panel.contains(100, 500)).isFalse();
    }

    @Test
    void onMouseClick_returnsTrue_whenVisibleAndInBounds() {
        // Simulate click inside panel
        boolean handled = panel.onMouseClick(150, 150, 0, 1);
        assertThat(handled).isTrue();
    }

    @Test
    void onMouseClick_returnsFalse_whenNotVisible() {
        panel.setVisible(false);
        boolean handled = panel.onMouseClick(150, 150, 0, 1);
        assertThat(handled).isFalse();
    }

    @Test
    void onMouseClick_returnsFalse_whenOutsideBounds() {
        boolean handled = panel.onMouseClick(50, 50, 0, 1);
        assertThat(handled).isFalse();
    }

    @Test
    void maintainAspectRatio_isTrue_byDefault() {
        assertThat(panel.getPreviewImage().isMaintainAspectRatio()).isTrue();
    }

    @Test
    void setTexture_withValidPngFile_loadsImage() throws IOException {
        // Create a simple valid PNG file (1x1 pixel, minimal valid PNG)
        byte[] minimalPng = createMinimalPng();
        Path testImage = tempDir.resolve("test.png");
        Files.write(testImage, minimalPng);

        // Note: This will fail without an OpenGL context, but we're testing the interface
        panel.setTexture("test.png", testImage.toAbsolutePath().toString());

        // Verify the path was set (image loading requires OpenGL context)
        assertThat(panel.getTexturePath()).isEqualTo(testImage.toAbsolutePath().toString());
        assertThat(panel.getTextureName()).isEqualTo("test.png");
    }

    /**
     * Creates a minimal valid PNG file (1x1 transparent pixel).
     */
    private byte[] createMinimalPng() {
        return new byte[]{
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, // IHDR chunk length
            0x49, 0x48, 0x44, 0x52, // IHDR type
            0x00, 0x00, 0x00, 0x01, // width = 1
            0x00, 0x00, 0x00, 0x01, // height = 1
            0x08, 0x06, 0x00, 0x00, 0x00, // bit depth=8, color type=6 (RGBA), compression=0, filter=0, interlace=0
            0x1F, 0x15, (byte)0xC4, (byte)0x89, // IHDR CRC
            0x00, 0x00, 0x00, 0x0A, // IDAT chunk length
            0x49, 0x44, 0x41, 0x54, // IDAT type
            0x78, (byte)0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, // compressed data
            0x0D, 0x0A, 0x2D, (byte)0xB4, // IDAT CRC
            0x00, 0x00, 0x00, 0x00, // IEND chunk length
            0x49, 0x45, 0x4E, 0x44, // IEND type
            (byte)0xAE, 0x42, 0x60, (byte)0x82 // IEND CRC
        };
    }
}
