package com.lightningfirefly.engine.rendering.render2d;

/**
 * Base interface for all GUI components.
 */
public interface WindowComponent {

    /**
     * Get the unique identifier of this component.
     * @return the component ID, or null if not set
     */
    String getId();

    /**
     * Set the unique identifier of this component.
     * @param id the component ID
     */
    void setId(String id);

    /**
     * Get the x position of the component.
     */
    int getX();

    /**
     * Get the y position of the component.
     */
    int getY();

    /**
     * Get the width of the component.
     */
    int getWidth();

    /**
     * Get the height of the component.
     */
    int getHeight();

    /**
     * Set the position of the component.
     */
    void setPosition(int x, int y);

    /**
     * Set the size of the component.
     */
    void setSize(int width, int height);

    /**
     * Check if the component is visible.
     */
    boolean isVisible();

    /**
     * Set the visibility of the component.
     */
    void setVisible(boolean visible);

    /**
     * Render the component using the abstract Renderer interface.
     * This is the preferred method for new components.
     *
     * @param renderer the renderer to use for drawing
     */
    default void render(Renderer renderer) {
        // Default implementation: no-op
        // Subclasses should override this method
    }

    /**
     * Render the component using NanoVG context.
     *
     * @param nvg the NanoVG context handle
     * @deprecated Use {@link #render(Renderer)} instead for backend-agnostic rendering.
     *             This method will be removed in a future release.
     */
    @Deprecated
    void render(long nvg);

    /**
     * Check if a point is within this component's bounds.
     */
    default boolean contains(int px, int py) {
        return px >= getX() && px < getX() + getWidth()
            && py >= getY() && py < getY() + getHeight();
    }

    /**
     * Handle mouse click event.
     * @param x mouse x position
     * @param y mouse y position
     * @param button mouse button (0=left, 1=right, 2=middle)
     * @param action 1=press, 0=release
     * @return true if the event was handled
     */
    default boolean onMouseClick(int x, int y, int button, int action) {
        return false;
    }

    /**
     * Handle mouse move event.
     * @param x mouse x position
     * @param y mouse y position
     * @return true if the event was handled
     */
    default boolean onMouseMove(int x, int y) {
        return false;
    }

    /**
     * Handle mouse scroll event.
     * @param x mouse x position
     * @param y mouse y position
     * @param scrollX horizontal scroll amount
     * @param scrollY vertical scroll amount
     * @return true if the event was handled
     */
    default boolean onMouseScroll(int x, int y, double scrollX, double scrollY) {
        return false;
    }

    /**
     * Handle key press event.
     * @param key the key code
     * @param action 1=press, 0=release, 2=repeat
     * @return true if the event was handled
     */
    default boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    /**
     * Handle key press event with modifier keys.
     * @param key the key code
     * @param action 1=press, 0=release, 2=repeat
     * @param mods modifier keys bitmask (GLFW_MOD_SHIFT, GLFW_MOD_CONTROL, GLFW_MOD_ALT, GLFW_MOD_SUPER)
     * @return true if the event was handled
     */
    default boolean onKeyPress(int key, int action, int mods) {
        return false;
    }

    /**
     * Handle character input event.
     * @param codepoint the unicode codepoint
     * @return true if the event was handled
     */
    default boolean onCharInput(int codepoint) {
        return false;
    }

    /**
     * Clear focus from this component.
     * Called by the window when a click happens elsewhere.
     * Components that support focus (like text fields) should override this.
     */
    default void clearFocus() {
        // Default: do nothing (most components don't track focus)
    }
}
