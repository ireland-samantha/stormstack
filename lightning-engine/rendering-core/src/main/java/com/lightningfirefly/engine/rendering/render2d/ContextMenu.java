package com.lightningfirefly.engine.rendering.render2d;

import java.util.List;

/**
 * Interface for context menus (right-click menus).
 */
public interface ContextMenu extends WindowComponent {

    /**
     * A menu item with label and action.
     */
    interface MenuItem {
        String getLabel();
        Runnable getAction();
        boolean isEnabled();
        boolean isSeparator();
    }

    /**
     * Add a menu item.
     * @param label the display text
     * @param action the action to perform when clicked
     */
    void addItem(String label, Runnable action);

    /**
     * Add a menu item with enabled state.
     * @param label the display text
     * @param action the action to perform when clicked
     * @param enabled whether the item is clickable
     */
    void addItem(String label, Runnable action, boolean enabled);

    /**
     * Add a separator line.
     */
    void addSeparator();

    /**
     * Get all menu items.
     */
    List<MenuItem> getItems();

    /**
     * Clear all items.
     */
    void clearItems();

    /**
     * Show the context menu at the specified position.
     * @param x the x position
     * @param y the y position
     */
    void show(int x, int y);

    /**
     * Hide the context menu.
     */
    void hide();

    /**
     * Check if the menu is currently visible.
     */
    boolean isShowing();
}
