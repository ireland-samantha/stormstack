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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A window that visualizes entities in a match with their textures.
 *
 * <p>Rendering rules:
 * <ul>
 *   <li>Entity with RESOURCE_ID but no POSITION: render sprite in corner</li>
 *   <li>Entity with RESOURCE_ID and POSITION: render sprite at position with texture</li>
 *   <li>Entity with POSITION but no RESOURCE_ID: render placeholder sprite</li>
 * </ul>
 */
@Slf4j
public class VisualizationWindow {

    private static final int CORNER_OFFSET = 20;
    private static final int CORNER_SPACING = 80;
    private static final int SPRITE_SIZE = 64;
    private static final int PLACEHOLDER_SIZE = 32;

    private final Window window;
    private final long matchId;
    private final String serverUrl;
    private final SnapshotService snapshotService;
    private final ResourceService resourceService;

    // Cached textures: resourceId -> local file path
    private final Map<Long, Path> textureCache = new ConcurrentHashMap<>();

    // Active sprites: entityId -> sprite
    private final Map<Long, Sprite> entitySprites = new ConcurrentHashMap<>();

    // Sprite ID counter
    private final AtomicInteger spriteIdCounter = new AtomicInteger(1);

    // Entity data from snapshot
    @Getter
    private final List<VisualEntity> visualEntities = new ArrayList<>();

    private volatile boolean needsRefresh = true;
    private volatile boolean needsSpriteUpdate = false;
    private volatile boolean isRunning = false;

    // Path to placeholder texture (generated dynamically)
    private Path placeholderTexturePath;

    /**
     * Create a new visualization window for a match.
     */
    public VisualizationWindow(long matchId, String serverUrl) {
        this.matchId = matchId;
        this.serverUrl = serverUrl;
        this.snapshotService = new SnapshotService(serverUrl);
        this.resourceService = new ResourceService(serverUrl);

        // Create window
        this.window = WindowBuilder.create()
                .size(800, 600)
                .title("Match " + matchId + " Visualization")
                .build();

        // Create placeholder texture
        createPlaceholderTexture();
    }

    /**
     * Create a simple placeholder texture for entities without resources.
     */
    private void createPlaceholderTexture() {
        try {
            // Create a simple colored PNG for placeholder
            placeholderTexturePath = Files.createTempFile("viz-placeholder-", ".png");

            // Create a simple 32x32 gray PNG manually
            // PNG header + IHDR + IDAT with gray pixels + IEND
            byte[] pngData = createSimplePng(PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, 0x80, 0x80, 0x80);
            Files.write(placeholderTexturePath, pngData);

            log.info("Created placeholder texture at {}", placeholderTexturePath);
        } catch (IOException e) {
            log.error("Failed to create placeholder texture", e);
        }
    }

    /**
     * Create a simple solid-color PNG image.
     */
    private byte[] createSimplePng(int width, int height, int r, int g, int b) {
        // Use a minimal PNG with uncompressed data
        // This creates a valid PNG with the specified solid color
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            // PNG signature
            out.write(new byte[] { (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });

            // IHDR chunk
            byte[] ihdr = new byte[] {
                0, 0, 0, (byte)width,  // width
                0, 0, 0, (byte)height, // height
                8,  // bit depth
                2,  // color type (RGB)
                0,  // compression
                0,  // filter
                0   // interlace
            };
            writeChunk(out, "IHDR", ihdr);

            // IDAT chunk - create raw image data
            java.io.ByteArrayOutputStream rawData = new java.io.ByteArrayOutputStream();
            java.util.zip.DeflaterOutputStream deflater = new java.util.zip.DeflaterOutputStream(rawData);
            for (int y = 0; y < height; y++) {
                deflater.write(0); // filter byte (none)
                for (int x = 0; x < width; x++) {
                    deflater.write(r);
                    deflater.write(g);
                    deflater.write(b);
                }
            }
            deflater.finish();
            deflater.close();
            writeChunk(out, "IDAT", rawData.toByteArray());

            // IEND chunk
            writeChunk(out, "IEND", new byte[0]);

        } catch (IOException e) {
            log.error("Failed to create PNG", e);
        }
        return out.toByteArray();
    }

    private void writeChunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        // Length
        int len = data.length;
        out.write((len >> 24) & 0xFF);
        out.write((len >> 16) & 0xFF);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);

        // Type
        out.write(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        // Data
        out.write(data);

        // CRC (simplified - use java.util.zip.CRC32)
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        crc.update(data);
        long crcValue = crc.getValue();
        out.write((int)((crcValue >> 24) & 0xFF));
        out.write((int)((crcValue >> 16) & 0xFF));
        out.write((int)((crcValue >> 8) & 0xFF));
        out.write((int)(crcValue & 0xFF));
    }

    /**
     * Start the visualization window. This opens the window and starts rendering.
     * Call this from the main thread on macOS.
     */
    public void start() {
        isRunning = true;

        // Initial refresh
        refreshSnapshot();

        // Set up update callback
        window.setOnUpdate(() -> {
            update();
            renderEntities();
        });

        // Run the window loop (blocking)
        window.run();

        // Cleanup when window closes
        dispose();
    }

    /**
     * Start the visualization window asynchronously in a new thread.
     * Note: On macOS, GLFW windows must be created on the main thread.
     */
    public void startAsync() {
        Thread windowThread = new Thread(() -> {
            try {
                start();
            } catch (Exception e) {
                log.error("Visualization window error", e);
            }
        }, "VisualizationWindow-" + matchId);
        windowThread.start();
    }

    /**
     * Refresh snapshot data from the server.
     */
    public void refreshSnapshot() {
        snapshotService.getSnapshot(matchId).thenAccept(snapshot -> {
            if (snapshot != null) {
                processSnapshot(snapshot);
            } else {
                log.warn("No snapshot found for match {}", matchId);
            }
        }).exceptionally(e -> {
            log.error("Failed to fetch snapshot for match {}", matchId, e);
            return null;
        });
    }

    private void processSnapshot(SnapshotService.SnapshotData snapshot) {
        Map<String, Map<String, List<Float>>> data = snapshot.data();
        if (data == null) return;

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

                EntityData entityData = entityDataMap.computeIfAbsent(entityId, id -> new EntityData(id));

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
            int cornerIndex = 0;

            for (EntityData entityData : entityDataMap.values()) {
                VisualEntity ve = new VisualEntity();
                ve.entityId = entityData.entityId;
                ve.resourceId = entityData.resourceId;

                if (entityData.hasPosition) {
                    ve.x = (int) entityData.positionX;
                    ve.y = (int) entityData.positionY;
                    ve.hasPosition = true;
                } else if (entityData.resourceId != null) {
                    // Place in corner
                    ve.x = CORNER_OFFSET + (cornerIndex % 5) * CORNER_SPACING;
                    ve.y = CORNER_OFFSET + (cornerIndex / 5) * CORNER_SPACING;
                    cornerIndex++;
                }

                // Pre-fetch texture if entity has resource
                if (entityData.resourceId != null && !textureCache.containsKey(entityData.resourceId)) {
                    fetchTexture(entityData.resourceId);
                }

                visualEntities.add(ve);
            }
        }

        log.info("Processed {} entities for match {}", visualEntities.size(), matchId);
        needsRefresh = false;
        needsSpriteUpdate = true;
    }

    private void fetchTexture(long resourceId) {
        if (textureCache.containsKey(resourceId)) return;

        resourceService.downloadResource(resourceId).thenAccept(optData -> {
            if (optData.isPresent()) {
                try {
                    Path tempFile = Files.createTempFile("viz-texture-" + resourceId + "-", ".png");
                    Files.write(tempFile, optData.get());
                    textureCache.put(resourceId, tempFile);
                    log.info("Cached texture for resource {}", resourceId);
                    // Trigger sprite update now that texture is available
                    needsSpriteUpdate = true;
                } catch (IOException e) {
                    log.error("Failed to cache texture for resource {}", resourceId, e);
                }
            }
        }).exceptionally(e -> {
            log.error("Failed to download texture for resource {}", resourceId, e);
            return null;
        });
    }

    private void update() {
        if (needsRefresh) {
            refreshSnapshot();
        }

        if (needsSpriteUpdate) {
            updateSprites();
            needsSpriteUpdate = false;
        }
    }

    /**
     * Render entities. Called each frame after update.
     * Sprites are rendered by the window automatically.
     */
    private void renderEntities() {
        // Sprites are already added to the window and rendered automatically.
        // This method exists for any additional per-frame rendering logic.
    }

    /**
     * Update sprites based on current visual entities.
     */
    private void updateSprites() {
        synchronized (visualEntities) {
            for (VisualEntity ve : visualEntities) {
                Sprite existingSprite = entitySprites.get(ve.entityId);

                // Determine texture path
                String texturePath = null;
                int spriteSize = SPRITE_SIZE;

                if (ve.hasResource()) {
                    Path cachedTexture = textureCache.get(ve.resourceId);
                    if (cachedTexture != null && Files.exists(cachedTexture)) {
                        texturePath = cachedTexture.toString();
                    } else {
                        // Texture not loaded yet, skip for now
                        continue;
                    }
                } else if (ve.hasPosition) {
                    // Entity with position but no resource - use placeholder
                    if (placeholderTexturePath != null && Files.exists(placeholderTexturePath)) {
                        texturePath = placeholderTexturePath.toString();
                        spriteSize = PLACEHOLDER_SIZE;
                    } else {
                        continue;
                    }
                } else {
                    // Entity with no resource and no position - skip
                    continue;
                }

                if (existingSprite != null) {
                    // Update existing sprite
                    existingSprite.setX(ve.x);
                    existingSprite.setY(ve.y);
                    existingSprite.setTexturePath(texturePath);
                    existingSprite.setSizeX(spriteSize);
                    existingSprite.setSizeY(spriteSize);
                } else {
                    // Create new sprite
                    Sprite sprite = Sprite.builder()
                            .id(spriteIdCounter.getAndIncrement())
                            .x(ve.x)
                            .y(ve.y)
                            .sizeX(spriteSize)
                            .sizeY(spriteSize)
                            .texturePath(texturePath)
                            .build();

                    entitySprites.put(ve.entityId, sprite);
                    window.addSprite(sprite);
                    log.debug("Added sprite for entity {} at ({}, {})", ve.entityId, ve.x, ve.y);
                }
            }
        }
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        isRunning = false;
        snapshotService.shutdown();
        resourceService.shutdown();

        // Clean up cached textures
        for (Path path : textureCache.values()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete cached texture: {}", path, e);
            }
        }
        textureCache.clear();
        entitySprites.clear();

        // Clean up placeholder texture
        if (placeholderTexturePath != null) {
            try {
                Files.deleteIfExists(placeholderTexturePath);
            } catch (IOException e) {
                log.warn("Failed to delete placeholder texture: {}", placeholderTexturePath, e);
            }
        }
    }

    /**
     * Check if the window is still running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get the underlying window (for testing).
     */
    public Window getWindow() {
        return window;
    }

    /**
     * Get the texture cache (for testing).
     */
    public Map<Long, Path> getTextureCache() {
        return new HashMap<>(textureCache);
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
     * Visual entity data for rendering.
     */
    public static class VisualEntity {
        public long entityId;
        public Long resourceId;
        public int x;
        public int y;
        public boolean hasPosition;

        public boolean hasResource() {
            return resourceId != null;
        }
    }
}
