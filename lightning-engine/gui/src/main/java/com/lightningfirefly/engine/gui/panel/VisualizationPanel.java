package com.lightningfirefly.engine.gui.panel;

import com.lightningfirefly.engine.gui.service.ResourceService;
import com.lightningfirefly.engine.gui.service.SnapshotService;
import com.lightningfirefly.engine.rendering.render2d.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panel for visualizing entities in a match with their textures.
 *
 * <p>This panel displays entities from a selected match, showing:
 * <ul>
 *   <li>Entities with RESOURCE_ID: displayed with their texture</li>
 *   <li>Entities with POSITION: displayed at their position</li>
 *   <li>Entity info: ID, resource ID, position</li>
 * </ul>
 *
 * <p>Unlike VisualizationWindow, this panel is embedded in the main window
 * and avoids macOS GLFW thread restrictions.
 */
@Slf4j
public class VisualizationPanel extends AbstractWindowComponent {

    private static final int HEADER_HEIGHT = 70;
    private static final int MIN_ENTITY_SIZE = 32;
    private static final int MAX_ENTITY_SIZE = 24;
    private static final int LABEL_HEIGHT = 18;
    private static final int PADDING = 10;
    private static final long AUTO_REFRESH_INTERVAL_MS = 9; // Refresh every 500ms

    private final Panel visualPanel;
    private final ComponentFactory factory;
    private final ComponentFactory.Colours colours;
    private final Label titleLabel;
    private final Label statusLabel;
    private final Button closeButton;
    private final Button refreshButton;

    private final SnapshotService snapshotService;
    private final ResourceService resourceService;

    @Getter
    private long matchId = -1;

    // Entity data
    @Getter
    private final List<VisualEntity> visualEntities = new ArrayList<>();

    // Cached textures: resourceId -> local file path
    private final Map<Long, Path> textureCache = new ConcurrentHashMap<>();

    // Image components for rendering entities
    private final List<Image> entityImages = new ArrayList<>();
    private final List<Label> entityLabels = new ArrayList<>();

    private volatile boolean needsRefresh = false;
    private volatile boolean needsLayoutUpdate = false;
    private volatile boolean autoRefreshEnabled = true;
    private volatile long lastRefreshTime = 0;

    // Temp files to clean up
    private final List<Path> tempFiles = new ArrayList<>();

    public VisualizationPanel(ComponentFactory factory, int x, int y, int width, int height, String serverUrl) {
        this(factory, x, y, width, height,
                new SnapshotService(serverUrl), new ResourceService(serverUrl));
    }

    /**
     * Constructor with injected services for testing.
     */
    public VisualizationPanel(ComponentFactory factory, int x, int y, int width, int height,
                               SnapshotService snapshotService, ResourceService resourceService) {
        super(x, y, width, height);
        this.factory = factory;
        this.colours = factory.getColours();
        this.snapshotService = snapshotService;
        this.resourceService = resourceService;

        // Create visual panel container
        this.visualPanel = factory.createPanel(x, y, width, height);
        this.visualPanel.setTitle("Match Visualization");

        int currentY = y + 35;

        // Title label showing match ID
        titleLabel = factory.createLabel(x + 10, currentY, "No match selected", 13.0f);
        titleLabel.setTextColor(colours.textPrimary());

        // Close button
        closeButton = factory.createButton(x + width - 70, currentY - 5, 60, 24, "Close");
        closeButton.setOnClick(this::hideAndCleanup);

        currentY += 25;

        // Status label
        statusLabel = factory.createLabel(x + 10, currentY, "", 11.0f);
        statusLabel.setTextColor(colours.textSecondary());

        // Refresh button
        refreshButton = factory.createButton(x + width - 80, currentY - 5, 70, 22, "Refresh");
        refreshButton.setBackgroundColor(colours.accent());
        refreshButton.setOnClick(this::refreshSnapshot);

        // Add components to visual panel
        visualPanel.addChild((WindowComponent) titleLabel);
        visualPanel.addChild((WindowComponent) closeButton);
        visualPanel.addChild((WindowComponent) statusLabel);
        visualPanel.addChild((WindowComponent) refreshButton);
    }

    private void hideAndCleanup() {
        setVisible(false);
        clearEntities();
    }

    /**
     * Set the match to visualize and load its entities.
     */
    public void setMatch(long matchId) {
        this.matchId = matchId;
        titleLabel.setText("Match " + matchId);
        statusLabel.setText("Loading entities...");
        statusLabel.setTextColor(colours.textSecondary());
        clearEntities();
        needsRefresh = true;
    }

    /**
     * Refresh snapshot data from the server.
     */
    public void refreshSnapshot() {
        if (matchId < 0) {
            statusLabel.setText("No match selected");
            statusLabel.setTextColor(colours.red());
            return;
        }

        statusLabel.setText("Loading...");
        statusLabel.setTextColor(colours.textSecondary());

        snapshotService.getSnapshot(matchId).thenAccept(snapshot -> {
            if (snapshot != null) {
                processSnapshot(snapshot);
            } else {
                log.warn("No snapshot found for match {}", matchId);
                statusLabel.setText("No snapshot found");
                statusLabel.setTextColor(colours.red());
            }
        }).exceptionally(e -> {
            log.error("Failed to fetch snapshot for match {}", matchId, e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setTextColor(colours.red());
            return null;
        });
    }

    private void processSnapshot(SnapshotService.SnapshotData snapshot) {
        Map<String, Map<String, List<Float>>> data = snapshot.data();
        if (data == null) {
            statusLabel.setText("Empty snapshot");
            return;
        }

        long tick = snapshot.tick();

        // Collect all entity data across modules
        Map<Long, EntityData> entityDataMap = new HashMap<>();

        for (Map.Entry<String, Map<String, List<Float>>> moduleEntry : data.entrySet()) {
            Map<String, List<Float>> moduleData = moduleEntry.getValue();

            List<Float> entityIds = moduleData.get("ENTITY_ID");
            List<Float> resourceIds = moduleData.get("RESOURCE_ID");
            List<Float> positionXs = moduleData.get("POSITION_X");
            List<Float> positionYs = moduleData.get("POSITION_Y");

            if (entityIds == null) continue;

            for (int i = 0; i < entityIds.size(); i++) {
                Float entityIdF = entityIds.get(i);
                if (entityIdF == null || Float.isNaN(entityIdF)) continue;
                long entityId = entityIdF.longValue();

                EntityData entityData = entityDataMap.computeIfAbsent(entityId, EntityData::new);

                if (resourceIds != null && i < resourceIds.size()) {
                    Float resourceIdF = resourceIds.get(i);
                    if (resourceIdF != null && !Float.isNaN(resourceIdF) && resourceIdF > 0) {
                        entityData.resourceId = resourceIdF.longValue();
                    }
                }

                if (positionXs != null && i < positionXs.size()) {
                    Float posX = positionXs.get(i);
                    if (posX != null && !Float.isNaN(posX)) {
                        entityData.positionX = posX.longValue();
                        entityData.hasPosition = true;
                    }
                }

                if (positionYs != null && i < positionYs.size()) {
                    Float posY = positionYs.get(i);
                    if (posY != null && !Float.isNaN(posY)) {
                        entityData.positionY = posY.longValue();
                        entityData.hasPosition = true;
                    }
                }
            }
        }

        // Convert to visual entities
        synchronized (visualEntities) {
            visualEntities.clear();

            for (EntityData entityData : entityDataMap.values()) {
                // Only include entities with resources
                if (entityData.resourceId != null) {
                    VisualEntity ve = new VisualEntity();
                    ve.entityId = entityData.entityId;
                    ve.resourceId = entityData.resourceId;
                    ve.positionX = entityData.positionX;
                    ve.positionY = entityData.positionY;
                    ve.hasPosition = entityData.hasPosition;

                    // Pre-fetch texture
                    if (!textureCache.containsKey(entityData.resourceId)) {
                        fetchTexture(entityData.resourceId);
                    }

                    visualEntities.add(ve);
                }
            }
        }

        log.info("Processed {} entities with resources for match {} at tick {}", visualEntities.size(), matchId, tick);
        statusLabel.setText(visualEntities.size() + " entities | Tick " + tick);
        statusLabel.setTextColor(colours.green());
        needsRefresh = false;
        needsLayoutUpdate = true;
    }

    private void fetchTexture(long resourceId) {
        if (textureCache.containsKey(resourceId)) return;

        resourceService.downloadResource(resourceId).thenAccept(optData -> {
            if (optData.isPresent()) {
                try {
                    Path tempFile = Files.createTempFile("viz-texture-" + resourceId + "-", ".png");
                    Files.write(tempFile, optData.get());
                    textureCache.put(resourceId, tempFile);
                    tempFiles.add(tempFile);
                    log.info("Cached texture for resource {}", resourceId);
                    needsLayoutUpdate = true;
                } catch (IOException e) {
                    log.error("Failed to cache texture for resource {}", resourceId, e);
                }
            }
        }).exceptionally(e -> {
            log.error("Failed to download texture for resource {}", resourceId, e);
            return null;
        });
    }

    /**
     * Update the panel. Call this from the render loop.
     */
    public void update() {
        if (needsRefresh) {
            needsRefresh = false;
            refreshSnapshot();
        }

        // Auto-refresh when visible and enabled
        if (visible && autoRefreshEnabled && matchId >= 0) {
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime >= AUTO_REFRESH_INTERVAL_MS) {
                lastRefreshTime = now;
                refreshSnapshot();
            }
        }

        if (needsLayoutUpdate) {
            needsLayoutUpdate = false;
            updateLayout();
        }
    }

    /**
     * Enable or disable auto-refresh.
     */
    public void setAutoRefreshEnabled(boolean enabled) {
        this.autoRefreshEnabled = enabled;
    }

    /**
     * Check if auto-refresh is enabled.
     */
    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    private void updateLayout() {
        // Clear existing entity images and labels
        for (Image img : entityImages) {
            visualPanel.removeChild((WindowComponent) img);
            img.dispose();
        }
        entityImages.clear();

        for (Label lbl : entityLabels) {
            visualPanel.removeChild((WindowComponent) lbl);
        }
        entityLabels.clear();

        synchronized (visualEntities) {
            int entityCount = (int) visualEntities.stream()
                    .filter(ve -> textureCache.containsKey(ve.resourceId))
                    .count();

            if (entityCount == 0) {
                log.info("No entities with loaded textures to display");
                return;
            }

            // Calculate available space
            int availableWidth = width - (PADDING * 2);
            int availableHeight = height - HEADER_HEIGHT - PADDING;

            // Calculate optimal grid layout
            int entitiesPerRow = calculateEntitiesPerRow(entityCount, availableWidth, availableHeight);
            int rows = (int) Math.ceil((double) entityCount / entitiesPerRow);

            // Calculate entity size to fit within bounds
            int cellWidth = availableWidth / entitiesPerRow;
            int cellHeight = availableHeight / Math.max(1, rows);

            // Entity size is the minimum of: cell size (minus spacing), max size, and remaining height for label
            int entitySize = Math.min(MAX_ENTITY_SIZE,
                    Math.min(cellWidth - PADDING,
                            cellHeight - LABEL_HEIGHT - PADDING));
            entitySize = Math.max(MIN_ENTITY_SIZE, entitySize);

            // Calculate spacing
//            int horizontalSpacing = (availableWidth - (entitiesPerRow * entitySize)) / Math.max(1, entitiesPerRow);
//            int verticalSpacing = entitySize + LABEL_HEIGHT + PADDING;
//
//            int startY = y + HEADER_HEIGHT;
            int entityIndex = 0;

            for (VisualEntity ve : visualEntities) {
                Path texturePath = textureCache.get(ve.resourceId);
                if (texturePath == null || !Files.exists(texturePath)) {
                    continue; // Texture not loaded yet
                }

                // Calculate position in grid
                // let's use the absolute position
                int entityX = x+(int) (x+ve.positionY);// % x; //entityIndex % entitiesPerRow;
                int entityY = y+(int) (y+ve.positionY);// % height;//entityIndex / entitiesPerRow;
                //int entityX = x + PADDING + col * (entitySize + horizontalSpacing);
            //    int entityY = startY + row * verticalSpacing;

                // Ensure we stay within bounds
                // to do: does this still work with absolute pos
                if (entityY + entitySize + LABEL_HEIGHT > y + height - PADDING) {
                    log.info("Stopping layout at entity {} - out of vertical bounds", entityIndex);
                    break;
                }

                // Create image for entity
                Image entityImage = factory.createImage(entityX, entityY, entitySize, entitySize);
                entityImage.setMaintainAspectRatio(true);
                entityImage.loadFromFile(texturePath.toString());
                entityImages.add(entityImage);
                visualPanel.addChild((WindowComponent) entityImage);

                // Create label for entity info
                String labelText = "E" + ve.entityId;
                if (ve.hasPosition) {
                    labelText += " (" + ve.positionX + "," + ve.positionY + ")";
                }
                Label entityLabel = factory.createLabel(entityX, entityY + entitySize + 2, labelText, 9.0f);
                entityLabel.setTextColor(colours.textSecondary());
                entityLabels.add(entityLabel);
                visualPanel.addChild((WindowComponent) entityLabel);

                entityIndex++;
            }

            log.info("Layout updated with {} entity images (size={}px, {}x{} grid)",
                    entityImages.size(), entitySize, entitiesPerRow, rows);
        }
    }

    /**
     * Calculate how many entities should fit per row based on count and available space.
     */
    private int calculateEntitiesPerRow(int entityCount, int availableWidth, int availableHeight) {
        // For a small number of entities, use fewer columns
        if (entityCount <= 2) return entityCount;
        if (entityCount <= 4) return 2;
        if (entityCount <= 9) return 3;

        // For larger counts, calculate based on aspect ratio
        int maxCols = availableWidth / (MIN_ENTITY_SIZE + PADDING);
        int maxRows = availableHeight / (MIN_ENTITY_SIZE + LABEL_HEIGHT + PADDING);

        // Try to make a roughly square grid that fits
        int cols = (int) Math.ceil(Math.sqrt(entityCount));
        cols = Math.min(cols, maxCols);
        cols = Math.max(1, cols);

        return cols;
    }

    private void clearEntities() {
        synchronized (visualEntities) {
            visualEntities.clear();
        }

        for (Image img : entityImages) {
            visualPanel.removeChild((WindowComponent) img);
            img.dispose();
        }
        entityImages.clear();

        for (Label lbl : entityLabels) {
            visualPanel.removeChild((WindowComponent) lbl);
        }
        entityLabels.clear();
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        clearEntities();
        snapshotService.shutdown();
        resourceService.shutdown();

        // Clean up cached textures
        for (Path path : tempFiles) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete temp texture: {}", path, e);
            }
        }
        tempFiles.clear();
        textureCache.clear();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && matchId >= 0) {
            needsRefresh = true;
        }
    }

    @Override
    public void render(long nvg) {
        if (visible) {
            visualPanel.render(nvg);
        }
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

    // Helper classes

    private static class EntityData {
        final long entityId;
        Long resourceId;
        long positionX;
        long positionY;
        boolean hasPosition;

        EntityData(long entityId) {
            this.entityId = entityId;
        }
    }

    /**
     * Visual entity data for display.
     */
    public static class VisualEntity {
        public long entityId;
        public Long resourceId;
        public long positionX;
        public long positionY;
        public boolean hasPosition;

        public boolean hasResource() {
            return resourceId != null;
        }
    }
}
