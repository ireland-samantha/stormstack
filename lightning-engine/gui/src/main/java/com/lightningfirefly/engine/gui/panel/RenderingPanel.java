package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.SnapshotService;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel for viewing entities that have attached resources (textures).
 *
 * <p>This panel displays:
 * <ul>
 *   <li>A list of matches that have entities with RESOURCE_ID (Renderable Matches)</li>
 *   <li>A Visualize button to show entity visualization in a side panel</li>
 *   <li>A list of entities across all matches that have RESOURCE_ID components</li>
 *   <li>A preview button to view the associated texture</li>
 *   <li>Information about the entity and its resource</li>
 * </ul>
 *
 * <p>The panel uses the SnapshotService to fetch entity data and ResourceService
 * to download and preview textures.
 */
@Slf4j
public class RenderingPanel extends AbstractWindowComponent {

    private static final int PREVIEW_PANEL_WIDTH = 800;
    private static final int MATCHES_SECTION_HEIGHT = 120;

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final String serverUrl;
    private final SnapshotService snapshotService;
    private final ResourceService resourceService;

    // Renderable Matches section
    private final Label matchesLabel;
    private final ListView matchesList;
    private final Button visualizeButton;

    // Entity section
    private final Label statusLabel;
    private final Button refreshButton;
    private final ListView entityList;
    private final Button previewButton;
    private final Label detailLabel;
    private TexturePreviewPanel previewPanel;
    private VisualizationPanel visualizationPanel;

    // Entity data
    private final List<EntityResourceInfo> entities = new ArrayList<>();
    private final List<Long> renderableMatches = new ArrayList<>();
    private volatile boolean needsUpdate = false;
    private int selectedEntityIndex = -1;
    private int selectedMatchIndex = -1;

    // Temp files for preview
    private final Map<Long, Path> downloadedResources = new HashMap<>();

    public RenderingPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height, serverUrl,
                new SnapshotService(serverUrl), new ResourceService(serverUrl));
    }

    /**
     * Constructor with injected services for testing.
     */
    public RenderingPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl,
                          SnapshotService snapshotService, ResourceService resourceService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();
        this.serverUrl = serverUrl;
        this.snapshotService = snapshotService;
        this.resourceService = resourceService;

        // Calculate layout - left side for entity list, right side for preview
        int leftPanelWidth = width - PREVIEW_PANEL_WIDTH - 20;

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, leftPanelWidth, height);
        this.visualPanel.setTitle("Rendering");

        int currentY = y + 35;

        // ===== Renderable Matches Section =====
        matchesLabel = factory.createLabel(x + 10, currentY, "Renderable Matches", 13.0f);
        matchesLabel.setTextColor(colours.textPrimary());
        currentY += 20;

        // Matches list
        matchesList = factory.createListView(x + 10, currentY, leftPanelWidth - 130, 60);
        matchesList.setOnSelectionChanged(this::onMatchSelected);

        // Visualize button (to the right of matches list)
        visualizeButton = factory.createButton(x + leftPanelWidth - 110, currentY + 15, 100, 28, "Visualize");
        visualizeButton.setBackgroundColor(colours.accent());
        visualizeButton.setOnClick(this::visualizeSelectedMatch);

        currentY += 70;

        // ===== Entity Section =====
        // Create status label
        statusLabel = factory.createLabel(x + 10, currentY, "Click Refresh to load entities with resources", 12.0f);
        statusLabel.setTextColor(colours.textSecondary());
        currentY += 20;

        // Create refresh button
        refreshButton = factory.createButton(x + 10, currentY, 100, 28, "Refresh");
        refreshButton.setBackgroundColor(colours.accent());
        refreshButton.setOnClick(this::loadEntitiesWithResources);
        currentY += 35;

        // Create entity list
        int listHeight = height - currentY - 60 + y;
        entityList = factory.createListView(x + 10, currentY, leftPanelWidth - 20, listHeight);
        entityList.setOnSelectionChanged(this::onEntitySelected);

        // Create detail label below the list
        detailLabel = factory.createLabel(x + 10, y + height - 50, "", 12.0f);
        detailLabel.setTextColor(colours.textSecondary());

        // Create preview button
        previewButton = factory.createButton(x + 10, y + height - 30, 120, 26, "Preview Texture");
        previewButton.setBackgroundColor(colours.success());
        previewButton.setOnClick(this::previewSelectedTexture);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) matchesLabel);
        visualPanel.addChild((WindowComponent) matchesList);
        visualPanel.addChild((WindowComponent) visualizeButton);
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
        visualPanel.addChild((WindowComponent) entityList);
        visualPanel.addChild((WindowComponent) detailLabel);
        visualPanel.addChild((WindowComponent) previewButton);

        // Create preview panel (initially hidden)
        int previewX = x + leftPanelWidth + 10;
        previewPanel = new TexturePreviewPanel(factory, previewX, y, PREVIEW_PANEL_WIDTH, height);
        previewPanel.setVisible(false);

        // Create visualization panel (initially hidden) - shares space with preview panel
        visualizationPanel = new VisualizationPanel(factory, previewX, y, PREVIEW_PANEL_WIDTH, height, serverUrl);
        visualizationPanel.setVisible(false);
    }

    /**
     * Load entities that have RESOURCE_ID components from all matches.
     */
    public void loadEntitiesWithResources() {
        statusLabel.setText("Loading snapshots...");
        statusLabel.setTextColor(colours.textSecondary());

        snapshotService.getAllSnapshots().thenAccept(snapshots -> {
            entities.clear();
            renderableMatches.clear();
            selectedEntityIndex = -1;
            selectedMatchIndex = -1;

            java.util.Set<Long> matchesWithResources = new java.util.HashSet<>();

            for (SnapshotService.SnapshotData snapshot : snapshots) {
                long matchId = snapshot.matchId();
                Map<String, Map<String, List<Float>>> data = snapshot.data();

                if (data == null) continue;

                // Look for modules with RESOURCE_ID component
                for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : data.entrySet()) {
                    String moduleName = moduleEntry.getKey();
                    Map<String, List<Float>> moduleData = moduleEntry.getValue();

                    List<Float> resourceIds = moduleData.get("RESOURCE_ID");
                    List<Float> entityIds = moduleData.get("ENTITY_ID");

                    if (resourceIds == null || resourceIds.isEmpty()) continue;

                    for (int i = 0; i < resourceIds.size(); i++) {
                        Float resourceIdF = resourceIds.get(i);
                        // Filter out null/sentinel values (NaN is the null sentinel for floats)
                        if (resourceIdF != null && !Float.isNaN(resourceIdF) && resourceIdF > 0) {
                            long resourceId = resourceIdF.longValue();
                            long entityId = (entityIds != null && i < entityIds.size())
                                    ? entityIds.get(i).longValue() : (long) i;
                            entities.add(new EntityResourceInfo(matchId, moduleName, entityId, resourceId));
                            matchesWithResources.add(matchId);
                        }
                    }
                }
            }

            // Populate renderable matches list
            renderableMatches.addAll(matchesWithResources);
            renderableMatches.sort(Long::compareTo);

            log.info("Found {} entities with RESOURCE_ID across {} matches",
                    entities.size(), renderableMatches.size());
            needsUpdate = true;

            if (entities.isEmpty()) {
                statusLabel.setText("No entities with resources found");
                statusLabel.setTextColor(colours.textSecondary());
            } else {
                statusLabel.setText("Found " + entities.size() + " entities with resources");
                statusLabel.setTextColor(colours.green());
            }
        }).exceptionally(e -> {
            log.error("Failed to load snapshots", e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setTextColor(colours.red());
            return null;
        });
    }

    private void onMatchSelected(int index) {
        selectedMatchIndex = index;
    }

    /**
     * Show visualization panel for the selected match.
     */
    private void visualizeSelectedMatch() {
        if (selectedMatchIndex < 0 || selectedMatchIndex >= renderableMatches.size()) {
            statusLabel.setText("Please select a match to visualize");
            statusLabel.setTextColor(colours.red());
            return;
        }

        long matchId = renderableMatches.get(selectedMatchIndex);

        // Hide preview panel if visible (they share space)
        previewPanel.setVisible(false);

        // Show visualization panel for this match
        statusLabel.setText("Loading visualization for match " + matchId + "...");
        statusLabel.setTextColor(colours.textSecondary());

        visualizationPanel.setMatch(matchId);
        visualizationPanel.setVisible(true);

        statusLabel.setText("Showing visualization for match " + matchId);
        statusLabel.setTextColor(colours.green());
    }

    private void onEntitySelected(int index) {
        selectedEntityIndex = index;
        if (index >= 0 && index < entities.size()) {
            EntityResourceInfo info = entities.get(index);
            detailLabel.setText(String.format("Match: %d | Entity: %d | Resource ID: %d",
                    info.matchId, info.entityId, info.resourceId));
        } else {
            detailLabel.setText("");
        }
    }

    private void previewSelectedTexture() {
        if (selectedEntityIndex < 0 || selectedEntityIndex >= entities.size()) {
            statusLabel.setText("Please select an entity first");
            statusLabel.setTextColor(colours.red());
            return;
        }

        EntityResourceInfo info = entities.get(selectedEntityIndex);
        statusLabel.setText("Downloading resource " + info.resourceId + "...");
        statusLabel.setTextColor(colours.textSecondary());

        // Check if already downloaded
        Path cachedPath = downloadedResources.get(info.resourceId);
        if (cachedPath != null && Files.exists(cachedPath)) {
            showPreview(info, cachedPath);
            return;
        }

        // Download the resource
        resourceService.downloadResource(info.resourceId).thenAccept(optData -> {
            if (optData.isPresent()) {
                try {
                    Path tempFile = Files.createTempFile("resource-" + info.resourceId + "-", ".png");
                    Files.write(tempFile, optData.get());
                    downloadedResources.put(info.resourceId, tempFile);
                    showPreview(info, tempFile);
                } catch (IOException e) {
                    log.error("Failed to write resource to temp file", e);
                    statusLabel.setText("Error saving resource: " + e.getMessage());
                    statusLabel.setTextColor(colours.red());
                }
            } else {
                statusLabel.setText("Resource " + info.resourceId + " not found");
                statusLabel.setTextColor(colours.red());
            }
        }).exceptionally(e -> {
            log.error("Failed to download resource", e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setTextColor(colours.red());
            return null;
        });
    }

    private void showPreview(EntityResourceInfo info, Path filePath) {
        // Hide visualization panel if visible (they share space)
        visualizationPanel.setVisible(false);

        String name = String.format("Entity %d (Resource %d)", info.entityId, info.resourceId);
        previewPanel.setTexture(name, filePath.toString());
        previewPanel.setVisible(true);
        statusLabel.setText("Showing preview for resource " + info.resourceId);
        statusLabel.setTextColor(colours.green());
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsUpdate) {
            needsUpdate = false;
            updateEntityList();
        }
        // Update visualization panel if visible
        if (visualizationPanel.isVisible()) {
            visualizationPanel.update();
        }
    }

    private void updateEntityList() {
        // Update matches list
        List<String> matchItems = new ArrayList<>();
        for (Long matchId : renderableMatches) {
            matchItems.add("Match " + matchId);
        }
        matchesList.setItems(matchItems);

        // Update entities list
        List<String> entityItems = new ArrayList<>();
        for (EntityResourceInfo info : entities) {
            entityItems.add(String.format("Match %d / Entity %d → Resource %d",
                    info.matchId, info.entityId, info.resourceId));
        }
        entityList.setItems(entityItems);
    }

    /**
     * Get the list of entities with resources (for testing).
     */
    public List<EntityResourceInfo> getEntities() {
        return new ArrayList<>(entities);
    }

    /**
     * Select an entity by index (for testing).
     */
    public void selectEntity(int index) {
        entityList.setSelectedIndex(index);
        onEntitySelected(index);
    }

    /**
     * Get the snapshot service (for testing).
     */
    public SnapshotService getSnapshotService() {
        return snapshotService;
    }

    /**
     * Get the resource service (for testing).
     */
    public ResourceService getResourceService() {
        return resourceService;
    }

    /**
     * Get the preview panel (for testing).
     */
    public TexturePreviewPanel getPreviewPanel() {
        return previewPanel;
    }

    /**
     * Get the list of renderable matches (for testing).
     */
    public List<Long> getRenderableMatches() {
        return new ArrayList<>(renderableMatches);
    }

    /**
     * Select a match by index (for testing).
     */
    public void selectMatch(int index) {
        matchesList.setSelectedIndex(index);
        onMatchSelected(index);
    }

    /**
     * Open visualization panel for selected match (for testing).
     */
    public void openVisualization() {
        visualizeSelectedMatch();
    }

    /**
     * Get the visualization panel (for testing).
     */
    public VisualizationPanel getVisualizationPanel() {
        return visualizationPanel;
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        snapshotService.shutdown();
        resourceService.shutdown();

        // Clean up visualization panel
        if (visualizationPanel != null) {
            visualizationPanel.dispose();
        }

        // Clean up temp files
        for (Path path : downloadedResources.values()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", path, e);
            }
        }
        downloadedResources.clear();
    }

    // Delegate rendering and input to both panels

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
            if (previewPanel.isVisible()) {
                previewPanel.render(nvg);
            }
            if (visualizationPanel.isVisible()) {
                visualizationPanel.render(nvg);
            }
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) return false;

        // Check side panels first (they're on top)
        if (visualizationPanel.isVisible() && visualizationPanel.contains(mx, my)) {
            return visualizationPanel.onMouseClick(mx, my, button, action);
        }
        if (previewPanel.isVisible() && previewPanel.contains(mx, my)) {
            return previewPanel.onMouseClick(mx, my, button, action);
        }

        return contains(mx, my) && visualPanel.onMouseClick(mx, my, button, action);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) return false;
        if (visualizationPanel.isVisible()) {
            visualizationPanel.onMouseMove(mx, my);
        }
        if (previewPanel.isVisible()) {
            previewPanel.onMouseMove(mx, my);
        }
        return visualPanel.onMouseMove(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible) return false;

        if (visualizationPanel.isVisible() && visualizationPanel.contains(mx, my)) {
            return visualizationPanel.onMouseScroll(mx, my, scrollX, scrollY);
        }
        if (previewPanel.isVisible() && previewPanel.contains(mx, my)) {
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
        if (!visible) return false;
        if (visualizationPanel.isVisible() && visualizationPanel.onKeyPress(key, action, mods)) {
            return true;
        }
        if (previewPanel.isVisible() && previewPanel.onKeyPress(key, action, mods)) {
            return true;
        }
        return visualPanel.onKeyPress(key, action, mods);
    }

    @Override
    public boolean onCharInput(int codepoint) {
        if (!visible) return false;
        if (visualizationPanel.isVisible() && visualizationPanel.onCharInput(codepoint)) {
            return true;
        }
        if (previewPanel.isVisible() && previewPanel.onCharInput(codepoint)) {
            return true;
        }
        return visualPanel.onCharInput(codepoint);
    }

    /**
     * Entity resource information record.
     */
    public record EntityResourceInfo(long matchId, String moduleName, long entityId, long resourceId) {}
}
