package com.lightningfirefly.engine.rendering.testing.headless;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Image;

/**
 * Mock Image implementation for headless testing.
 */
public class MockImage extends AbstractWindowComponent implements Image {

    private String loadedFilePath;
    private String loadedResourcePath;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private boolean loaded = false;
    private boolean maintainAspectRatio = true;

    public MockImage(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public boolean loadFromFile(String filePath) {
        this.loadedFilePath = filePath;
        this.loaded = true;
        // Simulate loading - in headless mode we just track the path
        return true;
    }

    @Override
    public boolean loadFromResource(String resourcePath) {
        this.loadedResourcePath = resourcePath;
        this.loaded = true;
        return true;
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
        return loaded;
    }

    @Override
    public void dispose() {
        loaded = false;
        loadedFilePath = null;
        loadedResourcePath = null;
        imageWidth = 0;
        imageHeight = 0;
    }

    @Override
    public void setMaintainAspectRatio(boolean maintainAspectRatio) {
        this.maintainAspectRatio = maintainAspectRatio;
    }

    @Override
    public boolean isMaintainAspectRatio() {
        return maintainAspectRatio;
    }

    @Override
    public void render(long nvg) {
        // No rendering in headless mode
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        return visible && contains(mx, my);
    }

    // Test helper methods

    /**
     * Get the file path that was loaded (for testing).
     */
    public String getLoadedFilePath() {
        return loadedFilePath;
    }

    /**
     * Get the resource path that was loaded (for testing).
     */
    public String getLoadedResourcePath() {
        return loadedResourcePath;
    }

    /**
     * Set the simulated image dimensions (for testing).
     */
    public void setImageDimensions(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }
}
