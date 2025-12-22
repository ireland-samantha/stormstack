package com.lightningfirefly.engine.gui;

import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.GLComponentFactory;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Simple sprite renderer application for viewing and editing sprite properties.
 *
 * <p>Features:
 * <ul>
 *   <li>Upload PNG files to display as sprites</li>
 *   <li>Edit sprite properties (position, size, rotation, z-index)</li>
 *   <li>See changes reflected in real-time</li>
 * </ul>
 */
@Slf4j
public class SpriteRendererApplication {

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    private static final int PANEL_WIDTH = 250;
    private static final int PREVIEW_X = PANEL_WIDTH + 20;
    private static final int PREVIEW_Y = 60;

    private final ComponentFactory componentFactory;
    private Window window;

    // UI Components
    private Panel propertiesPanel;
    private Label titleLabel;
    private Label statusLabel;
    private Button loadButton;

    // Property controls
    private TextField xField;
    private TextField yField;
    private TextField widthField;
    private TextField heightField;
    private TextField rotationField;
    private TextField zIndexField;
    private Label xLabel, yLabel, widthLabel, heightLabel, rotationLabel, zIndexLabel;

    // Preview components
    private Panel previewPanel;
    private Image previewImage;
    private Label previewLabel;
    private Label dimensionsLabel;

    // State
    private String loadedFilePath = null;
    private float spriteX = 100;
    private float spriteY = 100;
    private float spriteWidth = 64;
    private float spriteHeight = 64;
    private float spriteRotation = 0;
    private int spriteZIndex = 0;

    public SpriteRendererApplication() {
        this(GLComponentFactory.getInstance());
    }

    public SpriteRendererApplication(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
    }

    /**
     * Initialize and run the application.
     */
    public void run() {
        initialize();
        start();
    }

    /**
     * Initialize the application without starting the event loop.
     */
    public void initialize() {
        if (window != null) {
            throw new IllegalStateException("Application already initialized");
        }

        window = WindowBuilder.create()
                .size(WINDOW_WIDTH, WINDOW_HEIGHT)
                .title("Sprite Renderer")
                .build();

        setupUI();
        window.setOnUpdate(this::update);

        log.debug("SpriteRendererApplication initialized");
    }

    /**
     * Start the event loop.
     */
    public void start() {
        if (window == null) {
            throw new IllegalStateException("Application not initialized");
        }
        window.run();
        cleanup();
    }

    /**
     * Get the window for testing.
     */
    public Window getWindow() {
        return window;
    }

    private void setupUI() {
        ComponentFactory.Colours colours = componentFactory.getColours();

        // Title
        titleLabel = componentFactory.createLabel(20, 15, "Sprite Renderer", 20.0f);
        titleLabel.setTextColor(colours.textPrimary());

        // Status label
        statusLabel = componentFactory.createLabel(20, 40, "No sprite loaded", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Properties panel
        setupPropertiesPanel(colours);

        // Preview panel
        setupPreviewPanel(colours);

        // Add components to window
        window.addComponent((WindowComponent) titleLabel);
        window.addComponent((WindowComponent) statusLabel);
        window.addComponent(propertiesPanel);
        window.addComponent(previewPanel);
    }

    private void setupPropertiesPanel(ComponentFactory.Colours colours) {
        int panelX = 10;
        int panelY = 60;
        int panelHeight = WINDOW_HEIGHT - panelY - 10;

        propertiesPanel = componentFactory.createPanel(panelX, panelY, PANEL_WIDTH, panelHeight);
        propertiesPanel.setTitle("Sprite Properties");

        int contentY = panelY + 35;
        int labelX = panelX + 15;
        int fieldX = panelX + 80;
        int fieldWidth = 150;
        int fieldHeight = 28;
        int rowHeight = 40;

        // Load button
        loadButton = componentFactory.createButton(labelX, contentY, PANEL_WIDTH - 30, 35, "Load PNG...");
        loadButton.setBackgroundColor(colours.accent());
        loadButton.setOnClick(this::loadPngFile);
        propertiesPanel.addChild((WindowComponent) loadButton);
        contentY += 50;

        // X position
        xLabel = componentFactory.createLabel(labelX, contentY + 6, "X:", 14.0f);
        xLabel.setTextColor(colours.textPrimary());
        xField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        xField.setText(String.valueOf((int) spriteX));
        xField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) xLabel);
        propertiesPanel.addChild((WindowComponent) xField);
        contentY += rowHeight;

        // Y position
        yLabel = componentFactory.createLabel(labelX, contentY + 6, "Y:", 14.0f);
        yLabel.setTextColor(colours.textPrimary());
        yField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        yField.setText(String.valueOf((int) spriteY));
        yField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) yLabel);
        propertiesPanel.addChild((WindowComponent) yField);
        contentY += rowHeight;

        // Width
        widthLabel = componentFactory.createLabel(labelX, contentY + 6, "Width:", 14.0f);
        widthLabel.setTextColor(colours.textPrimary());
        widthField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        widthField.setText(String.valueOf((int) spriteWidth));
        widthField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) widthLabel);
        propertiesPanel.addChild((WindowComponent) widthField);
        contentY += rowHeight;

        // Height
        heightLabel = componentFactory.createLabel(labelX, contentY + 6, "Height:", 14.0f);
        heightLabel.setTextColor(colours.textPrimary());
        heightField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        heightField.setText(String.valueOf((int) spriteHeight));
        heightField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) heightLabel);
        propertiesPanel.addChild((WindowComponent) heightField);
        contentY += rowHeight;

        // Rotation
        rotationLabel = componentFactory.createLabel(labelX, contentY + 6, "Rot:", 14.0f);
        rotationLabel.setTextColor(colours.textPrimary());
        rotationField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        rotationField.setText(String.valueOf((int) spriteRotation));
        rotationField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) rotationLabel);
        propertiesPanel.addChild((WindowComponent) rotationField);
        contentY += rowHeight;

        // Z-index
        zIndexLabel = componentFactory.createLabel(labelX, contentY + 6, "Z:", 14.0f);
        zIndexLabel.setTextColor(colours.textPrimary());
        zIndexField = componentFactory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        zIndexField.setText(String.valueOf(spriteZIndex));
        zIndexField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) zIndexLabel);
        propertiesPanel.addChild((WindowComponent) zIndexField);
    }

    private void setupPreviewPanel(ComponentFactory.Colours colours) {
        int previewWidth = WINDOW_WIDTH - PREVIEW_X - 20;
        int previewHeight = WINDOW_HEIGHT - PREVIEW_Y - 10;

        previewPanel = componentFactory.createPanel(PREVIEW_X, PREVIEW_Y, previewWidth, previewHeight);
        previewPanel.setTitle("Preview");

        // Preview label (shown when no image loaded)
        previewLabel = componentFactory.createLabel(
                PREVIEW_X + previewWidth / 2 - 80,
                PREVIEW_Y + previewHeight / 2,
                "Load a PNG to preview",
                14.0f
        );
        previewLabel.setTextColor(colours.textSecondary());

        // Dimensions label
        dimensionsLabel = componentFactory.createLabel(
                PREVIEW_X + 15,
                PREVIEW_Y + previewHeight - 25,
                "",
                12.0f
        );
        dimensionsLabel.setTextColor(colours.textSecondary());

        // Image component for preview
        previewImage = componentFactory.createImage(
                PREVIEW_X + 20,
                PREVIEW_Y + 40,
                previewWidth - 40,
                previewHeight - 80
        );
        previewImage.setMaintainAspectRatio(true);
        previewImage.setVisible(false);

        previewPanel.addChild((WindowComponent) previewLabel);
        previewPanel.addChild((WindowComponent) dimensionsLabel);
        previewPanel.addChild((WindowComponent) previewImage);
    }

    private void loadPngFile() {
        Optional<Path> selected = componentFactory.openFileDialog(
                "Select PNG Image",
                System.getProperty("user.home"),
                "*.png",
                "PNG Images"
        );

        if (selected.isPresent()) {
            Path filePath = selected.get();
            loadImageFile(filePath.toString());
        }
    }

    /**
     * Load an image file programmatically.
     */
    public void loadImageFile(String filePath) {
        log.info("Loading image: {}", filePath);

        boolean loaded = previewImage.loadFromFile(filePath);
        if (loaded) {
            loadedFilePath = filePath;

            // Update status
            String fileName = Path.of(filePath).getFileName().toString();
            statusLabel.setText("Loaded: " + fileName);

            // Update dimensions
            int imgWidth = previewImage.getImageWidth();
            int imgHeight = previewImage.getImageHeight();
            dimensionsLabel.setText(String.format("Original: %d x %d px", imgWidth, imgHeight));

            // Set default size to image size
            spriteWidth = imgWidth;
            spriteHeight = imgHeight;
            widthField.setText(String.valueOf(imgWidth));
            heightField.setText(String.valueOf(imgHeight));

            // Show image, hide placeholder
            previewImage.setVisible(true);
            previewLabel.setVisible(false);

            log.info("Image loaded: {} ({}x{})", fileName, imgWidth, imgHeight);
        } else {
            statusLabel.setText("Failed to load: " + filePath);
            log.error("Failed to load image: {}", filePath);
        }
    }

    /**
     * Load an image from classpath resource.
     */
    public void loadImageResource(String resourcePath) {
        log.info("Loading resource: {}", resourcePath);

        boolean loaded = previewImage.loadFromResource(resourcePath);
        if (loaded) {
            loadedFilePath = resourcePath;
            statusLabel.setText("Loaded: " + resourcePath);

            int imgWidth = previewImage.getImageWidth();
            int imgHeight = previewImage.getImageHeight();
            dimensionsLabel.setText(String.format("Original: %d x %d px", imgWidth, imgHeight));

            spriteWidth = imgWidth;
            spriteHeight = imgHeight;
            widthField.setText(String.valueOf(imgWidth));
            heightField.setText(String.valueOf(imgHeight));

            previewImage.setVisible(true);
            previewLabel.setVisible(false);

            log.info("Resource loaded: {} ({}x{})", resourcePath, imgWidth, imgHeight);
        } else {
            statusLabel.setText("Failed to load: " + resourcePath);
            log.error("Failed to load resource: {}", resourcePath);
        }
    }

    private void onPropertyChanged() {
        // Parse values from text fields
        try {
            spriteX = parseFloat(xField.getText(), spriteX);
            spriteY = parseFloat(yField.getText(), spriteY);
            spriteWidth = parseFloat(widthField.getText(), spriteWidth);
            spriteHeight = parseFloat(heightField.getText(), spriteHeight);
            spriteRotation = parseFloat(rotationField.getText(), spriteRotation);
            spriteZIndex = parseInt(zIndexField.getText(), spriteZIndex);

            // Update preview image position and size
            if (previewImage != null) {
                previewImage.setPosition(
                        (int) spriteX + PREVIEW_X + 20,
                        (int) spriteY + PREVIEW_Y + 40
                );
                previewImage.setSize((int) spriteWidth, (int) spriteHeight);
            }

            log.debug("Properties updated: x={}, y={}, w={}, h={}, rot={}, z={}",
                    spriteX, spriteY, spriteWidth, spriteHeight, spriteRotation, spriteZIndex);
        } catch (Exception e) {
            log.warn("Error parsing property values: {}", e.getMessage());
        }
    }

    private float parseFloat(String text, float defaultValue) {
        if (text == null || text.isBlank()) return defaultValue;
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String text, int defaultValue) {
        if (text == null || text.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void update() {
        // Frame update logic if needed
    }

    private void cleanup() {
        if (previewImage != null) {
            previewImage.dispose();
        }
        log.debug("SpriteRendererApplication cleaned up");
    }

    // ========== Getters for testing ==========

    public float getSpriteX() {
        return spriteX;
    }

    public float getSpriteY() {
        return spriteY;
    }

    public float getSpriteWidth() {
        return spriteWidth;
    }

    public float getSpriteHeight() {
        return spriteHeight;
    }

    public float getSpriteRotation() {
        return spriteRotation;
    }

    public int getSpriteZIndex() {
        return spriteZIndex;
    }

    public String getLoadedFilePath() {
        return loadedFilePath;
    }

    public boolean isImageLoaded() {
        return previewImage != null && previewImage.isLoaded();
    }

    public TextField getXField() {
        return xField;
    }

    public TextField getYField() {
        return yField;
    }

    public TextField getWidthField() {
        return widthField;
    }

    public TextField getHeightField() {
        return heightField;
    }

    public TextField getRotationField() {
        return rotationField;
    }

    public TextField getZIndexField() {
        return zIndexField;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public Image getPreviewImage() {
        return previewImage;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * Set a property value programmatically (for testing).
     */
    public void setProperty(String property, String value) {
        switch (property.toLowerCase()) {
            case "x" -> {
                xField.setText(value);
                onPropertyChanged();
            }
            case "y" -> {
                yField.setText(value);
                onPropertyChanged();
            }
            case "width" -> {
                widthField.setText(value);
                onPropertyChanged();
            }
            case "height" -> {
                heightField.setText(value);
                onPropertyChanged();
            }
            case "rotation" -> {
                rotationField.setText(value);
                onPropertyChanged();
            }
            case "zindex", "z" -> {
                zIndexField.setText(value);
                onPropertyChanged();
            }
        }
    }

    /**
     * Stop the application.
     */
    public void stop() {
        if (window != null) {
            window.stop();
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        log.info("Starting Sprite Renderer Application");
        SpriteRendererApplication app = new SpriteRendererApplication();
        app.run();
    }
}
