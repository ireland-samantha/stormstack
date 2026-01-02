package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel for rendering and editing sprite properties using server resources.
 *
 * <p>Features:
 * <ul>
 *   <li>List available texture resources from the server</li>
 *   <li>Create sprites from server resources</li>
 *   <li>Upload PNG files as new resources</li>
 *   <li>Edit sprite properties (position, size, rotation, z-index)</li>
 *   <li>See changes reflected in real-time</li>
 * </ul>
 */
@Slf4j
public class SpriteRendererPanel extends AbstractWindowComponent {

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final ResourceService resourceService;

    // Resource list components
    private Panel resourcePanel;
    private ListView resourceList;
    private Button refreshButton;
    private Button uploadButton;

    // Properties panel components
    private Panel propertiesPanel;
    private TextField xField;
    private TextField yField;
    private TextField widthField;
    private TextField heightField;
    private TextField rotationField;
    private TextField zIndexField;
    private Label xLabel, yLabel, widthLabel, heightLabel, rotationLabel, zIndexLabel;
    private Label statusLabel;

    // Preview panel components
    private Panel previewPanel;
    private Image previewImage;
    private Label previewLabel;
    private Label dimensionsLabel;

    // State
    private final List<ResourceInfo> resources = new CopyOnWriteArrayList<>();
    private volatile boolean needsRefresh = false;
    private String loadedResourceName = null;
    private long loadedResourceId = -1;
    private float spriteX = 100;
    private float spriteY = 100;
    private float spriteWidth = 64;
    private float spriteHeight = 64;
    private float spriteRotation = 0;
    private int spriteZIndex = 0;

    private static final int RESOURCE_PANEL_WIDTH = 200;
    private static final int PROPS_PANEL_WIDTH = 200;

    public SpriteRendererPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, new ResourceService(serverUrl));
    }

    public SpriteRendererPanel(ComponentFactory factory, int x, int y, int width, int height, ResourceService resourceService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();
        this.resourceService = resourceService;

        // Create main visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Sprite Preview");

        // Status label
        statusLabel = factory.createLabel(x + 15, y + 35, "Select a resource or upload a PNG", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());
        visualPanel.addChild((WindowComponent) statusLabel);

        setupResourcePanel();
        setupPropertiesPanel();
        setupPreviewPanel();

        // Setup resource service listener
        resourceService.addListener(this::onResourceEvent);

        // Initial resource load
        refreshResources();
    }

    private void setupResourcePanel() {
        int panelX = x + 10;
        int panelY = y + 55;
        int panelHeight = height - 65;

        resourcePanel = factory.createPanel(panelX, panelY, RESOURCE_PANEL_WIDTH, panelHeight);
        resourcePanel.setTitle("Resources");

        int contentY = panelY + 35;
        int buttonWidth = 85;
        int buttonSpacing = 10;

        // Refresh button
        refreshButton = factory.createButton(panelX + 10, contentY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(this::refreshResources);
        resourcePanel.addChild((WindowComponent) refreshButton);

        // Upload button
        uploadButton = factory.createButton(panelX + 10 + buttonWidth + buttonSpacing, contentY, buttonWidth, 28, "Upload PNG");
        uploadButton.setBackgroundColor(colours.accent());
        uploadButton.setOnClick(this::uploadPngFile);
        resourcePanel.addChild((WindowComponent) uploadButton);

        contentY += 40;

        // Resource list
        resourceList = factory.createListView(panelX + 10, contentY, RESOURCE_PANEL_WIDTH - 20, panelHeight - 90);
        resourceList.setOnSelectionChanged(this::onResourceSelected);
        resourceList.setOnItemDoubleClicked(this::onResourceDoubleClicked);
        resourcePanel.addChild((WindowComponent) resourceList);

        visualPanel.addChild(resourcePanel);
    }

    private void setupPropertiesPanel() {
        int panelX = x + RESOURCE_PANEL_WIDTH + 20;
        int panelY = y + 55;
        int panelHeight = height - 65;

        propertiesPanel = factory.createPanel(panelX, panelY, PROPS_PANEL_WIDTH, panelHeight);
        propertiesPanel.setTitle("Properties");

        int contentY = panelY + 35;
        int labelX = panelX + 10;
        int fieldX = panelX + 70;
        int fieldWidth = 110;
        int fieldHeight = 26;
        int rowHeight = 36;

        // X position
        xLabel = factory.createLabel(labelX, contentY + 5, "X:", 13.0f);
        xLabel.setTextColor(colours.textPrimary());
        xField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        xField.setText(String.valueOf((int) spriteX));
        xField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) xLabel);
        propertiesPanel.addChild((WindowComponent) xField);
        contentY += rowHeight;

        // Y position
        yLabel = factory.createLabel(labelX, contentY + 5, "Y:", 13.0f);
        yLabel.setTextColor(colours.textPrimary());
        yField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        yField.setText(String.valueOf((int) spriteY));
        yField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) yLabel);
        propertiesPanel.addChild((WindowComponent) yField);
        contentY += rowHeight;

        // Width
        widthLabel = factory.createLabel(labelX, contentY + 5, "Width:", 13.0f);
        widthLabel.setTextColor(colours.textPrimary());
        widthField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        widthField.setText(String.valueOf((int) spriteWidth));
        widthField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) widthLabel);
        propertiesPanel.addChild((WindowComponent) widthField);
        contentY += rowHeight;

        // Height
        heightLabel = factory.createLabel(labelX, contentY + 5, "Height:", 13.0f);
        heightLabel.setTextColor(colours.textPrimary());
        heightField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        heightField.setText(String.valueOf((int) spriteHeight));
        heightField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) heightLabel);
        propertiesPanel.addChild((WindowComponent) heightField);
        contentY += rowHeight;

        // Rotation
        rotationLabel = factory.createLabel(labelX, contentY + 5, "Rot:", 13.0f);
        rotationLabel.setTextColor(colours.textPrimary());
        rotationField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        rotationField.setText(String.valueOf((int) spriteRotation));
        rotationField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) rotationLabel);
        propertiesPanel.addChild((WindowComponent) rotationField);
        contentY += rowHeight;

        // Z-index
        zIndexLabel = factory.createLabel(labelX, contentY + 5, "Z:", 13.0f);
        zIndexLabel.setTextColor(colours.textPrimary());
        zIndexField = factory.createTextField(fieldX, contentY, fieldWidth, fieldHeight);
        zIndexField.setText(String.valueOf(spriteZIndex));
        zIndexField.setOnChange(this::onPropertyChanged);
        propertiesPanel.addChild((WindowComponent) zIndexLabel);
        propertiesPanel.addChild((WindowComponent) zIndexField);

        visualPanel.addChild(propertiesPanel);
    }

    private void setupPreviewPanel() {
        int previewX = x + RESOURCE_PANEL_WIDTH + PROPS_PANEL_WIDTH + 30;
        int previewY = y + 55;
        int previewWidth = width - RESOURCE_PANEL_WIDTH - PROPS_PANEL_WIDTH - 40;
        int previewHeight = height - 65;

        previewPanel = factory.createPanel(previewX, previewY, previewWidth, previewHeight);
        previewPanel.setTitle("Preview");

        // Preview label (shown when no image loaded)
        previewLabel = factory.createLabel(
            previewX + previewWidth / 2 - 60,
            previewY + previewHeight / 2,
            "Select a resource",
            14.0f
        );
        previewLabel.setTextColor(colours.textSecondary());

        // Dimensions label
        dimensionsLabel = factory.createLabel(
            previewX + 15,
            previewY + previewHeight - 25,
            "",
            12.0f
        );
        dimensionsLabel.setTextColor(colours.textSecondary());

        // Image component for preview
        previewImage = factory.createImage(
            previewX + 20,
            previewY + 40,
            previewWidth - 40,
            previewHeight - 80
        );
        previewImage.setMaintainAspectRatio(true);
        previewImage.setVisible(false);

        previewPanel.addChild((WindowComponent) previewLabel);
        previewPanel.addChild((WindowComponent) dimensionsLabel);
        previewPanel.addChild((WindowComponent) previewImage);

        visualPanel.addChild(previewPanel);
    }

    /**
     * Refresh the resource list from the server.
     */
    public void refreshResources() {
        setStatus("Loading resources...", colours.textSecondary());
        resourceService.listResources().thenAccept(resourceInfos -> {
            resources.clear();
            // Filter to only texture resources
            for (ResourceInfo info : resourceInfos) {
                if ("TEXTURE".equalsIgnoreCase(info.type())) {
                    resources.add(info);
                }
            }
            needsRefresh = true;
            setStatus("Loaded " + resources.size() + " texture resources", colours.green());
        });
    }

    private void uploadPngFile() {
        Optional<Path> selected = factory.openFileDialog(
            "Select PNG Image",
            System.getProperty("user.home"),
            "*.png",
            "PNG Images"
        );

        if (selected.isPresent()) {
            Path filePath = selected.get();
            uploadAndLoadResource(filePath);
        }
    }

    private void uploadAndLoadResource(Path filePath) {
        if (!Files.exists(filePath)) {
            setStatus("File not found: " + filePath, colours.red());
            return;
        }

        setStatus("Uploading " + filePath.getFileName() + "...", colours.textSecondary());

        resourceService.uploadResourceFromFile(filePath, "TEXTURE")
            .thenAccept(resourceId -> {
                setStatus("Uploaded successfully!", colours.green());
                // Refresh resource list and select the new resource
                refreshResources();
                loadedResourceId = resourceId;
                loadedResourceName = filePath.getFileName().toString();
            })
            .exceptionally(e -> {
                setStatus("Upload failed: " + e.getMessage(), colours.red());
                return null;
            });
    }

    private void onResourceSelected(int index) {
        // Load the selected resource immediately on single-click
        if (index >= 0 && index < resources.size()) {
            ResourceInfo resource = resources.get(index);
            loadResourceAsSprite(resource);
        }
    }

    private void onResourceDoubleClicked(int index) {
        // Double-click also loads (same behavior as single-click)
        onResourceSelected(index);
    }

    /**
     * Load a resource from the server as the current sprite.
     */
    public void loadResourceAsSprite(ResourceInfo resource) {
        log.info("Loading resource as sprite: {} (id={})", resource.name(), resource.id());
        setStatus("Loading " + resource.name() + "...", colours.textSecondary());

        // Download resource to temp file and load
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path tempFile = tempDir.resolve("sprite_preview_" + resource.name());

        resourceService.downloadResourceToFile(resource.id(), tempFile)
            .thenAccept(success -> {
                log.info("Download completed for {}: success={}", resource.name(), success);
                if (success) {
                    loadedResourceId = resource.id();
                    loadedResourceName = resource.name();
                    loadImageFromFile(tempFile.toString());
                    setStatus("Loaded: " + resource.name(), colours.green());
                } else {
                    setStatus("Failed to download: " + resource.name(), colours.red());
                    log.error("Failed to download resource: {}", resource.name());
                }
            })
            .exceptionally(e -> {
                log.error("Exception downloading resource {}: {}", resource.name(), e.getMessage(), e);
                setStatus("Error: " + e.getMessage(), colours.red());
                return null;
            });
    }

    /**
     * Load a resource by ID.
     */
    public void loadResourceById(long resourceId) {
        for (ResourceInfo resource : resources) {
            if (resource.id() == resourceId) {
                loadResourceAsSprite(resource);
                return;
            }
        }
        setStatus("Resource ID " + resourceId + " not found", colours.red());
    }

    /**
     * Select a resource in the list by index.
     */
    public void selectResourceByIndex(int index) {
        if (index >= 0 && index < resources.size()) {
            resourceList.setSelectedIndex(index);
            loadResourceAsSprite(resources.get(index));
        }
    }

    private void loadImageFromFile(String filePath) {
        log.info("Loading image from file: {}", filePath);

        boolean loaded = previewImage.loadFromFile(filePath);
        if (loaded) {
            // Update dimensions
            int imgWidth = previewImage.getImageWidth();
            int imgHeight = previewImage.getImageHeight();
            dimensionsLabel.setText(String.format("Size: %d x %d px", imgWidth, imgHeight));

            // Set default size to image size if we have valid dimensions
            if (imgWidth > 0 && imgHeight > 0) {
                spriteWidth = imgWidth;
                spriteHeight = imgHeight;
                widthField.setText(String.valueOf(imgWidth));
                heightField.setText(String.valueOf(imgHeight));
            }

            // Show image, hide placeholder
            previewImage.setVisible(true);
            previewLabel.setVisible(false);

            log.info("Image loaded successfully ({}x{})", imgWidth, imgHeight);
        } else {
            log.warn("Image loading deferred to render");
            // Image loading is deferred - will load during render
            previewImage.setVisible(true);
            previewLabel.setVisible(false);
        }
    }

    /**
     * Load an image from classpath resource (for testing).
     */
    public void loadImageResource(String resourcePath) {
        log.info("Loading classpath resource: {}", resourcePath);

        boolean loaded = previewImage.loadFromResource(resourcePath);
        if (loaded) {
            loadedResourceName = resourcePath;
            loadedResourceId = -1; // Not a server resource

            int imgWidth = previewImage.getImageWidth();
            int imgHeight = previewImage.getImageHeight();
            dimensionsLabel.setText(String.format("Size: %d x %d px", imgWidth, imgHeight));

            if (imgWidth > 0 && imgHeight > 0) {
                spriteWidth = imgWidth;
                spriteHeight = imgHeight;
                widthField.setText(String.valueOf(imgWidth));
                heightField.setText(String.valueOf(imgHeight));
            }

            previewImage.setVisible(true);
            previewLabel.setVisible(false);
            setStatus("Loaded: " + resourcePath, colours.green());

            log.info("Resource loaded: {} ({}x{})", resourcePath, imgWidth, imgHeight);
        } else {
            setStatus("Failed to load: " + resourcePath, colours.red());
            log.error("Failed to load resource: {}", resourcePath);
        }
    }

    private void onResourceEvent(ResourceService.ResourceEvent event) {
        switch (event.type()) {
            case UPLOADED -> {
                setStatus("Uploaded: " + event.message(), colours.green());
                refreshResources();
            }
            case DOWNLOADED -> setStatus("Downloaded: " + event.message(), colours.green());
            case DELETED -> {
                setStatus("Resource deleted", colours.green());
                refreshResources();
            }
            case ERROR -> setStatus("Error: " + event.message(), colours.red());
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

            // Update preview image position and size relative to preview panel
            if (previewImage != null) {
                int previewX = x + RESOURCE_PANEL_WIDTH + PROPS_PANEL_WIDTH + 30;
                int previewY = y + 55;
                previewImage.setPosition(
                    (int) spriteX + previewX + 20,
                    (int) spriteY + previewY + 40
                );
                previewImage.setSize((int) spriteWidth, (int) spriteHeight);
            }

            log.debug("Properties updated: x={}, y={}, w={}, h={}, rot={}, z={}",
                spriteX, spriteY, spriteWidth, spriteHeight, spriteRotation, spriteZIndex);
        } catch (Exception e) {
            log.warn("Error parsing property values: {}", e.getMessage());
        }
    }

    private void setStatus(String message, float[] color) {
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
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

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsRefresh) {
            needsRefresh = false;
            updateResourceList();
        }
    }

    private void updateResourceList() {
        List<String> items = new ArrayList<>();
        for (ResourceInfo resource : resources) {
            items.add(resource.name());
        }
        resourceList.setItems(items);

        // Re-select the loaded resource if it's in the list
        if (loadedResourceId >= 0) {
            for (int i = 0; i < resources.size(); i++) {
                if (resources.get(i).id() == loadedResourceId) {
                    resourceList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        if (previewImage != null) {
            previewImage.dispose();
        }
        resourceService.shutdown();
        log.debug("SpriteRendererPanel disposed");
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

    public String getLoadedResourceName() {
        return loadedResourceName;
    }

    public long getLoadedResourceId() {
        return loadedResourceId;
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

    public Button getRefreshButton() {
        return refreshButton;
    }

    public Button getUploadButton() {
        return uploadButton;
    }

    public ListView getResourceList() {
        return resourceList;
    }

    public Image getPreviewImage() {
        return previewImage;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public List<ResourceInfo> getResources() {
        return new ArrayList<>(resources);
    }

    public ResourceService getResourceService() {
        return resourceService;
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

    // ========== Delegate rendering and input to the visual panel ==========

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) return false;
        return contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) return false;
        return visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible) return false;
        return contains(mx, my) && visualPanel.onMouseScroll(mx, my, scrollX, scrollY);
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
