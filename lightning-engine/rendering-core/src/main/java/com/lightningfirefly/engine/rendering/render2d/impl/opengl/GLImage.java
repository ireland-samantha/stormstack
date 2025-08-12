package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Image;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGPaint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG-based image component for displaying textures in the UI.
 * OpenGL implementation of {@link Image}.
 */
@Slf4j
public class GLImage extends AbstractWindowComponent implements Image {

    private int imageHandle = -1;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private boolean maintainAspectRatio = true;
    private float[] borderColor = GLColour.BORDER;

    // Store the NVG context used to create the image (needed for cleanup)
    private long createdInNvgContext = 0;

    public GLImage(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public boolean loadFromFile(String filePath) {
        long nvg = GLContext.getNvgContext();
        if (nvg == 0) {
            log.warn("Cannot load image - no NVG context available. Will retry during render.");
            // Store path for deferred loading
            return deferredLoadFromFile(filePath);
        }
        return doLoadFromFile(nvg, filePath);
    }

    private boolean deferredLoadFromFile(String filePath) {
        // Store for later loading during render
        this.deferredFilePath = filePath;
        return true;
    }

    private String deferredFilePath = null;

    private boolean doLoadFromFile(long nvg, String filePath) {
        try {
            // Dispose previous image if any
            disposeImage(nvg);

            // Load image bytes from file
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            // Create NanoVG image
            imageHandle = nvgCreateImageMem(nvg, NVG_IMAGE_GENERATE_MIPMAPS, buffer);
            if (imageHandle <= 0) {
                log.error("Failed to create NanoVG image from file: {}", filePath);
                return false;
            }

            // Get image dimensions
            int[] w = new int[1];
            int[] h = new int[1];
            nvgImageSize(nvg, imageHandle, w, h);
            imageWidth = w[0];
            imageHeight = h[0];
            createdInNvgContext = nvg;

            log.debug("Loaded image from file: {} ({}x{})", filePath, imageWidth, imageHeight);
            return true;
        } catch (IOException e) {
            log.error("Failed to load image from file: {}", filePath, e);
            return false;
        }
    }

    @Override
    public boolean loadFromResource(String resourcePath) {
        long nvg = GLContext.getNvgContext();
        if (nvg == 0) {
            log.warn("Cannot load image - no NVG context available");
            return false;
        }

        try {
            // Dispose previous image if any
            disposeImage(nvg);

            // Load image bytes from classpath
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    log.error("Image resource not found: {}", resourcePath);
                    return false;
                }

                ByteBuffer buffer = ioResourceToByteBuffer(in);

                // Create NanoVG image
                imageHandle = nvgCreateImageMem(nvg, NVG_IMAGE_GENERATE_MIPMAPS, buffer);
                if (imageHandle <= 0) {
                    log.error("Failed to create NanoVG image from resource: {}", resourcePath);
                    return false;
                }

                // Get image dimensions
                int[] w = new int[1];
                int[] h = new int[1];
                nvgImageSize(nvg, imageHandle, w, h);
                imageWidth = w[0];
                imageHeight = h[0];
                createdInNvgContext = nvg;

                log.debug("Loaded image from resource: {} ({}x{})", resourcePath, imageWidth, imageHeight);
                return true;
            }
        } catch (IOException e) {
            log.error("Failed to load image from resource: {}", resourcePath, e);
            return false;
        }
    }

    private ByteBuffer ioResourceToByteBuffer(InputStream source) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(source);
        ByteBuffer buffer = BufferUtils.createByteBuffer(1024);
        while (true) {
            int bytes = rbc.read(buffer);
            if (bytes == -1)
                break;
            if (buffer.remaining() == 0)
                buffer = resizeBuffer(buffer, buffer.capacity() * 2);
        }
        buffer.flip();
        return buffer;
    }

    private ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    @Override
    public int getImageWidth() {
        return imageWidth;
    }

    @Override
    public int getImageHeight() {
        return imageHeight;
    }

    @Override
    public boolean isLoaded() {
        return imageHandle > 0;
    }

    @Override
    public void dispose() {
        if (createdInNvgContext != 0) {
            disposeImage(createdInNvgContext);
        }
    }

    private void disposeImage(long nvg) {
        if (imageHandle > 0 && nvg != 0) {
            nvgDeleteImage(nvg, imageHandle);
            imageHandle = -1;
            imageWidth = 0;
            imageHeight = 0;
            createdInNvgContext = 0;
        }
    }

    @Override
    public void setMaintainAspectRatio(boolean maintainAspectRatio) {
        this.maintainAspectRatio = maintainAspectRatio;
    }

    @Override
    public boolean isMaintainAspectRatio() {
        return maintainAspectRatio;
    }

    /**
     * Set the border color.
     */
    public void setBorderColor(float[] borderColor) {
        this.borderColor = borderColor;
    }

    @Override
    public void render(long nvg) {
        if (!visible) {
            log.trace("GLImage.render() - not visible, skipping");
            return;
        }

        // Handle deferred loading
        if (deferredFilePath != null && imageHandle <= 0) {
            log.info("GLImage.render() - performing deferred load from: {}", deferredFilePath);
            boolean success = doLoadFromFile(nvg, deferredFilePath);
            log.info("GLImage.render() - deferred load result: {}, imageHandle: {}", success, imageHandle);
            deferredFilePath = null;
        }

        try (var color = org.lwjgl.nanovg.NVGColor.malloc();
             var paint = NVGPaint.malloc()) {

            log.debug("GLImage.render() - drawing at ({}, {}) size {}x{}, imageHandle: {}",
                x, y, width, height, imageHandle);

            // Draw border/background
            nvgBeginPath(nvg);
            nvgRoundedRect(nvg, x, y, width, height, 4);
            nvgStrokeColor(nvg, GLColour.rgba(borderColor, color));
            nvgStrokeWidth(nvg, 1.0f);
            nvgStroke(nvg);

            if (imageHandle > 0) {
                // Calculate rendering area maintaining aspect ratio if needed
                float drawX = x;
                float drawY = y;
                float drawWidth = width;
                float drawHeight = height;

                if (maintainAspectRatio && imageWidth > 0 && imageHeight > 0) {
                    float aspectRatio = (float) imageWidth / imageHeight;
                    float containerAspect = (float) width / height;

                    if (aspectRatio > containerAspect) {
                        // Image is wider - fit to width
                        drawHeight = width / aspectRatio;
                        drawY = y + (height - drawHeight) / 2;
                    } else {
                        // Image is taller - fit to height
                        drawWidth = height * aspectRatio;
                        drawX = x + (width - drawWidth) / 2;
                    }
                }

                // Create image pattern
                nvgImagePattern(nvg, drawX, drawY, drawWidth, drawHeight, 0, imageHandle, 1.0f, paint);

                // Draw image
                nvgBeginPath(nvg);
                nvgRoundedRect(nvg, drawX, drawY, drawWidth, drawHeight, 2);
                nvgFillPaint(nvg, paint);
                nvgFill(nvg);
            } else {
                // No image - draw placeholder
                nvgFillColor(nvg, GLColour.rgba(GLColour.DARK_GRAY, color));
                nvgFill(nvg);

                // Draw "No Image" text
                int fontId = GLContext.getDefaultFontId();
                if (fontId >= 0) {
                    nvgFontFaceId(nvg, fontId);
                    nvgFontSize(nvg, 12.0f);
                    nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                    nvgFillColor(nvg, GLColour.rgba(GLColour.TEXT_SECONDARY, color));
                    nvgText(nvg, x + width / 2.0f, y + height / 2.0f, "No Image");
                }
            }
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        return visible && contains(mx, my);
    }
}
