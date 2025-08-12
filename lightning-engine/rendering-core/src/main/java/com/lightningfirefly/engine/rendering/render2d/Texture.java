package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for texture resources.
 */
public interface Texture extends AutoCloseable {

    /**
     * Get the texture ID.
     */
    int getId();

    /**
     * Get the texture width in pixels.
     */
    int getWidth();

    /**
     * Get the texture height in pixels.
     */
    int getHeight();

    /**
     * Bind the texture for rendering.
     */
    void bind();

    /**
     * Dispose of the texture resources.
     */
    void dispose();

    @Override
    default void close() {
        dispose();
    }
}
