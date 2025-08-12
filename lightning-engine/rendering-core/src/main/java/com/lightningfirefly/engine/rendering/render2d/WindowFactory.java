package com.lightningfirefly.engine.rendering.render2d;

/**
 * Factory interface for creating Window instances.
 *
 * <p>This abstraction allows WindowBuilder to create windows without
 * depending on specific implementations (GLWindow, VulkanWindow, etc.).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code GLWindowFactory} - Creates OpenGL/NanoVG-based windows</li>
 * </ul>
 */
public interface WindowFactory {

    /**
     * Create a new Window instance with the specified parameters.
     *
     * @param width window width in pixels
     * @param height window height in pixels
     * @param title window title
     * @return a new Window instance
     */
    Window create(int width, int height, String title);
}
