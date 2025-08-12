package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Panel for previewing texture resources.
 *
 * <p>This panel displays a texture image with its name and dimensions.
 * It uses the Image component for rendering textures as part of the UI layer.
 */
@Slf4j
public class TexturePreviewPanel extends AbstractWindowComponent {

    private static final int PREVIEW_PADDING = 10;
    private static final int HEADER_HEIGHT = 70; // Space for title, name, dimensions, close button

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final Label nameLabel;
    private final Label dimensionsLabel;
    private final Button closeButton;
    private final Image previewImage;

    private String texturePath;
    private String textureName;

    public TexturePreviewPanel(ComponentFactory factory, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Texture Preview");

        // Create name label
        nameLabel = factory.createLabel(x + 10, y + 35, "No texture loaded", 12.0f);
        nameLabel.setTextColor(colours.textPrimary());

        // Create dimensions label
        dimensionsLabel = factory.createLabel(x + 10, y + 55, "", 11.0f);
        dimensionsLabel.setTextColor(colours.textSecondary());

        // Create close button
        closeButton = factory.createButton(x + width - 70, y + 35, 60, 24, "Close");
        closeButton.setOnClick(this::hideAndCleanup);

        // Create preview image component
        int imageX = x + PREVIEW_PADDING;
        int imageY = y + HEADER_HEIGHT;
        int imageWidth = width - (PREVIEW_PADDING * 2);
        int imageHeight = height - HEADER_HEIGHT - PREVIEW_PADDING;
        previewImage = factory.createImage(imageX, imageY, imageWidth, imageHeight);
        previewImage.setMaintainAspectRatio(true);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) nameLabel);
        visualPanel.addChild((WindowComponent) dimensionsLabel);
        visualPanel.addChild((WindowComponent) closeButton);
        visualPanel.addChild((WindowComponent) previewImage);
    }

    private void hideAndCleanup() {
        setVisible(false);
        // Clean up the image when closing
        previewImage.dispose();
    }

    /**
     * Set the texture to preview.
     *
     * @param name the texture name
     * @param path the file path to the texture
     */
    public void setTexture(String name, String path) {
        this.textureName = name;
        this.texturePath = path;
        nameLabel.setText(name);

        log.info("TexturePreviewPanel.setTexture() - name: {}, path: {}", name, path);

        // Verify the file exists
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            log.error("TexturePreviewPanel - file does not exist: {}", path);
            dimensionsLabel.setText("File not found");
            return;
        }
        log.info("TexturePreviewPanel - file exists, size: {} bytes", file.length());

        // Load the texture into the image component
        boolean loaded = previewImage.loadFromFile(path);
        log.info("TexturePreviewPanel - loadFromFile returned: {}", loaded);

        if (loaded) {
            // Update dimensions label with actual image dimensions
            // Note: If deferred loading is used, dimensions will be 0 until render
            int imgWidth = previewImage.getImageWidth();
            int imgHeight = previewImage.getImageHeight();
            if (imgWidth == 0 && imgHeight == 0) {
                dimensionsLabel.setText("Loading...");
                log.info("Texture preview using deferred loading for: {}", name);
            } else {
                dimensionsLabel.setText(String.format("%dx%d pixels", imgWidth, imgHeight));
                log.info("Loaded texture preview: {} ({}x{})", name, imgWidth, imgHeight);
            }
        } else {
            dimensionsLabel.setText("Failed to load image");
            log.warn("Failed to load texture preview for: {}", path);
        }
    }

    /**
     * Get the current texture path.
     */
    public String getTexturePath() {
        return texturePath;
    }

    /**
     * Get the current texture name.
     */
    public String getTextureName() {
        return textureName;
    }

    /**
     * Get the preview image component.
     */
    public Image getPreviewImage() {
        return previewImage;
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible) {
            // Clean up image resources when hidden
            previewImage.dispose();
        }
    }

    private boolean loggedRenderOnce = false;

    @Override
    public void render(long nvg) {
        if (!visible) return;

        if (!loggedRenderOnce) {
            log.info("TexturePreviewPanel.render() - visible: {}, textureName: {}, texturePath: {}",
                visible, textureName, texturePath);
            log.info("TexturePreviewPanel.render() - previewImage isLoaded: {}, isVisible: {}",
                previewImage.isLoaded(), previewImage.isVisible());
            loggedRenderOnce = true;
        }

        visualPanel.render(nvg);
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        return visible && contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        return visible && visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        return visible && contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        return visible && visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        return visible && visualPanel.onCharInput(codepoint);
    }
}
