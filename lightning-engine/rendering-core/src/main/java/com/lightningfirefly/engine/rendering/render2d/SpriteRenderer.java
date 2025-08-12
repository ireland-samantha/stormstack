package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for sprite rendering.
 *
 * <p>A sprite renderer handles the rendering of 2D sprites with textures.
 * It manages the rendering pipeline including shaders, vertex buffers,
 * and transformation matrices.
 */
public interface SpriteRenderer extends AutoCloseable {

    /**
     * Draw a sprite at the specified position.
     *
     * @param sprite the sprite data to render
     */
    void draw(Sprite sprite);

    /**
     * Draw a sprite with a specific texture.
     *
     * @param texture the texture to use
     * @param x the x position
     * @param y the y position
     * @param width the width
     * @param height the height
     */
    void draw(Texture texture, float x, float y, float width, float height);

    /**
     * Draw a sprite with rotation.
     *
     * @param texture the texture to use
     * @param x the x position
     * @param y the y position
     * @param width the width
     * @param height the height
     * @param rotation the rotation in radians
     */
    void draw(Texture texture, float x, float y, float width, float height, float rotation);

    /**
     * Begin a batch of sprite rendering.
     * Call this before drawing multiple sprites for better performance.
     */
    void begin();

    /**
     * End a batch of sprite rendering.
     * Call this after drawing multiple sprites.
     */
    void end();

    /**
     * Dispose of renderer resources.
     */
    void dispose();

    @Override
    default void close() {
        dispose();
    }
}
