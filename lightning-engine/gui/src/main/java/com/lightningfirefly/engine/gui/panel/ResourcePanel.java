package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceEvent;
import com.lightningfirefly.engine.gui.service.ResourceService.ResourceInfo;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Panel for managing resources with CRUD operations.
 *
 * <p>This panel is decoupled from OpenGL implementation. It uses ComponentFactory
 * to create UI components and only depends on interfaces.
 */
@Slf4j
public class ResourcePanel extends AbstractWindowComponent {

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final ResourceService resourceService;
    private final ListView resourceList;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button uploadButton;
    private final Button downloadButton;
    private final Button deleteButton;
    private final Button browseButton;
    private final Button previewButton;
    private final TextField filePathField;

    private final List<ResourceInfo> resources = new CopyOnWriteArrayList<>();
    private volatile boolean needsRefresh = false;
    private volatile String statusMessage = "";

    private TexturePreviewPanel previewPanel;

    public ResourcePanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, new ResourceService(serverUrl));
        refreshResources();
    }

    public ResourcePanel(ComponentFactory factory, int x, int y, int width, int height, ResourceService resourceService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Resource Manager");

        this.resourceService = resourceService;

        // Create status label
        statusLabel = factory.createLabel(x + 10, y + 35, "Ready", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Create buttons
        int buttonY = y + 55;
        int buttonWidth = 70;
        int buttonSpacing = 8;

        refreshButton = factory.createButton(x + 10, buttonY, buttonWidth, 28, "Refresh");
        refreshButton.setOnClick(this::refreshResources);

        uploadButton = factory.createButton(x + 10 + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28, "Upload");
        uploadButton.setOnClick(this::uploadResource);

        downloadButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 28, "Download");
        downloadButton.setOnClick(this::downloadResource);

        deleteButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, 28, "Delete");
        deleteButton.setOnClick(this::deleteResource);

        browseButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 4, buttonY, buttonWidth, 28, "Browse");
        browseButton.setOnClick(this::browseForFile);

        previewButton = factory.createButton(x + 10 + (buttonWidth + buttonSpacing) * 5, buttonY, buttonWidth, 28, "Preview");
        previewButton.setOnClick(this::previewResource);

        // Create file path input
        filePathField = factory.createTextField(x + 10, buttonY + 38, width - 20, 28);
        filePathField.setPlaceholder("Enter file path or click Browse...");

        // Create resource list
        resourceList = factory.createListView(x + 10, buttonY + 76, width - 20, height - buttonY - 86 + y);
        resourceList.setOnSelectionChanged(this::onResourceSelected);
        resourceList.setOnItemDoubleClicked(this::onResourceDoubleClicked);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) uploadButton);
        visualPanel.addChild((WindowComponent) downloadButton);
        visualPanel.addChild((WindowComponent) deleteButton);
        visualPanel.addChild((WindowComponent) browseButton);
        visualPanel.addChild((WindowComponent) previewButton);
        visualPanel.addChild((WindowComponent) filePathField);
        visualPanel.addChild((WindowComponent) resourceList);

        // Setup resource service listener
        resourceService.addListener(this::onResourceEvent);
    }

    /**
     * Refresh the resource list.
     */
    public void refreshResources() {
        setStatus("Loading resources...", colours.textSecondary());
        resourceService.listResources().thenAccept(resourceInfos -> {
            resources.clear();
            resources.addAll(resourceInfos);
            needsRefresh = true;
            setStatus("Loaded " + resourceInfos.size() + " resources", colours.green());
        });
    }

    /**
     * Open a file browser to select a file.
     */
    private void browseForFile() {
        Optional<Path> selectedFile = factory.openFileDialog(
            "Select Resource File",
            System.getProperty("user.home"),
            "*.png,*.jpg,*.tga,*.dds",
            "Image files (*.png, *.jpg, *.tga, *.dds)"
        );

        selectedFile.ifPresent(path -> {
            filePathField.setText(path.toAbsolutePath().toString());
            setStatus("Selected: " + path.getFileName(), colours.textPrimary());
        });
    }

    /**
     * Upload a resource from the file path field.
     */
    private void uploadResource() {
        String filePath = filePathField.getText().trim();
        if (filePath.isEmpty()) {
            setStatus("Enter a file path to upload", colours.yellow());
            return;
        }

        Path path = Path.of(filePath);
        if (!java.nio.file.Files.exists(path)) {
            setStatus("File not found: " + filePath, colours.red());
            return;
        }

        setStatus("Uploading...", colours.textSecondary());
        resourceService.uploadResourceFromFile(path, "TEXTURE")
            .thenAccept(id -> {
                setStatus("Uploaded successfully (ID: " + id + ")", colours.green());
                refreshResources();
            })
            .exceptionally(e -> {
                setStatus("Upload failed: " + e.getMessage(), colours.red());
                return null;
            });
    }

    /**
     * Download the selected resource to the user's Downloads directory.
     */
    private void downloadResource() {
        int selectedIndex = resourceList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= resources.size()) {
            setStatus("Select a resource to download", colours.yellow());
            return;
        }

        ResourceInfo resource = resources.get(selectedIndex);
        Path downloadsDir = Path.of(System.getProperty("user.home"), "Downloads");
        Path targetPath = downloadsDir.resolve(resource.name());

        setStatus("Downloading...", colours.textSecondary());
        resourceService.downloadResourceToFile(resource.id(), targetPath)
            .thenAccept(success -> {
                if (success) {
                    setStatus("Downloaded to: " + targetPath, colours.green());
                } else {
                    setStatus("Download failed", colours.red());
                }
            });
    }

    /**
     * Delete the selected resource.
     */
    private void deleteResource() {
        int selectedIndex = resourceList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= resources.size()) {
            setStatus("Select a resource to delete", colours.yellow());
            return;
        }

        ResourceInfo resource = resources.get(selectedIndex);
        setStatus("Deleting...", colours.textSecondary());
        resourceService.deleteResource(resource.id())
            .thenAccept(success -> {
                if (success) {
                    setStatus("Deleted successfully", colours.green());
                    refreshResources();
                } else {
                    setStatus("Delete failed", colours.red());
                }
            });
    }

    /**
     * Preview the selected texture resource.
     */
    private void previewResource() {
        int selectedIndex = resourceList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= resources.size()) {
            setStatus("Select a resource to preview", colours.yellow());
            return;
        }

        ResourceInfo resource = resources.get(selectedIndex);
        if (!resource.type().equalsIgnoreCase("TEXTURE")) {
            setStatus("Only texture resources can be previewed", colours.yellow());
            return;
        }

        // Download to temp file and show preview
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path tempFile = tempDir.resolve("preview_" + resource.name());

        setStatus("Loading preview...", colours.textSecondary());
        resourceService.downloadResourceToFile(resource.id(), tempFile)
            .thenAccept(success -> {
                if (success) {
                    showTexturePreview(resource.name(), tempFile.toString());
                    setStatus("Preview: " + resource.name(), colours.green());
                } else {
                    setStatus("Failed to load preview", colours.red());
                }
            });
    }

    private void showTexturePreview(String name, String texturePath) {
        log.info("ResourcePanel.showTexturePreview() - name: {}, path: {}", name, texturePath);

        // Create or update preview panel
        if (previewPanel == null) {
            int previewWidth = 300;
            int previewHeight = 350;
            // Position preview panel to overlay the right side of the resource panel
            // This ensures it stays within the window bounds
            int previewX = x + width - previewWidth - 20;
            int previewY = y + 50; // Below the buttons
            log.info("Creating TexturePreviewPanel at ({}, {}) size {}x{}", previewX, previewY, previewWidth, previewHeight);
            previewPanel = new TexturePreviewPanel(factory, previewX, previewY, previewWidth, previewHeight);
        }
        previewPanel.setTexture(name, texturePath);
        previewPanel.setVisible(true);
        log.info("Preview panel visible: {}", previewPanel.isVisible());
    }

    /**
     * Get the texture preview panel (may be null if not created yet).
     */
    public TexturePreviewPanel getPreviewPanel() {
        return previewPanel;
    }

    private void onResourceSelected(int index) {
        // Could update details panel or preview here
    }

    private void onResourceDoubleClicked(int index) {
        if (index >= 0 && index < resources.size()) {
            ResourceInfo resource = resources.get(index);
            setStatus("Selected: " + resource.name() + " (ID: " + resource.id() + ")", colours.textPrimary());
        }
    }

    private void onResourceEvent(ResourceEvent event) {
        switch (event.type()) {
            case UPLOADED -> setStatus("Uploaded: " + event.message(), colours.green());
            case DOWNLOADED -> setStatus("Downloaded: " + event.message(), colours.green());
            case DELETED -> setStatus("Deleted resource " + event.resourceId(), colours.green());
            case ERROR -> setStatus("Error: " + event.message(), colours.red());
        }
    }

    private void setStatus(String message, float[] color) {
        statusMessage = message;
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
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
            items.add(String.format("[%d] %s (%s)", resource.id(), resource.name(), resource.type()));
        }
        resourceList.setItems(items);
    }

    /**
     * Cleanup resources.
     */
    public void dispose() {
        resourceService.shutdown();
    }

    /**
     * Get the underlying resource service.
     */
    public ResourceService getResourceService() {
        return resourceService;
    }

    /**
     * Get the list of loaded resources.
     */
    public List<ResourceInfo> getResources() {
        return new ArrayList<>(resources);
    }

    // ========== Test Helper Methods ==========

    /**
     * Set the file path field text (for testing).
     */
    public void setFilePath(String path) {
        filePathField.setText(path);
    }

    /**
     * Get the file path field text (for testing).
     */
    public String getFilePath() {
        return filePathField.getText();
    }

    /**
     * Trigger upload of the file in the file path field (for testing).
     */
    public void uploadSelectedFile() {
        uploadResource();
    }

    /**
     * Select a resource by index (for testing).
     */
    public void selectResource(int index) {
        resourceList.setSelectedIndex(index);
    }

    /**
     * Get the selected resource index (for testing).
     */
    public int getSelectedResourceIndex() {
        return resourceList.getSelectedIndex();
    }

    /**
     * Trigger preview of the selected resource (for testing).
     */
    public void previewSelectedResource() {
        previewResource();
    }

    /**
     * Trigger delete of the selected resource (for testing).
     */
    public void deleteSelectedResource() {
        deleteResource();
    }

    /**
     * Get the status message (for testing).
     */
    public String getStatusMessage() {
        return statusMessage;
    }

    // Delegate rendering and input to the visual panel

    private boolean loggedRenderOnce = false;

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
            if (previewPanel != null && previewPanel.isVisible()) {
                if (!loggedRenderOnce) {
                    log.info("ResourcePanel.render() - rendering preview panel at ({}, {})",
                        previewPanel.getX(), previewPanel.getY());
                    loggedRenderOnce = true;
                }
                previewPanel.render(nvg);
            }
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) return false;

        // Check preview panel first
        if (previewPanel != null && previewPanel.isVisible() && previewPanel.contains(mx, my)) {
            return previewPanel.onMouseClick(mx, my, button, action);
        }

        return contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) return false;

        if (previewPanel != null && previewPanel.isVisible()) {
            previewPanel.onMouseMove(mx, my);
        }

        return visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible) return false;

        if (previewPanel != null && previewPanel.isVisible() && previewPanel.contains(mx, my)) {
            return previewPanel.onMouseScroll(mx, my, scrollX, scrollY);
        }

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
