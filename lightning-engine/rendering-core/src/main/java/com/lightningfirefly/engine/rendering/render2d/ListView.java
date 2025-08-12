package com.lightningfirefly.engine.rendering.render2d;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface for scrollable list view components.
 */
public interface ListView extends WindowComponent {

    /**
     * Set the list items.
     */
    void setItems(List<String> items);

    /**
     * Get the list items.
     */
    List<String> getItems();

    /**
     * Add an item to the list.
     */
    void addItem(String item);

    /**
     * Remove an item at the specified index.
     */
    void removeItem(int index);

    /**
     * Clear all items.
     */
    void clearItems();

    /**
     * Get the selected index (for single-selection mode).
     */
    int getSelectedIndex();

    /**
     * Set the selected index (for single-selection mode).
     */
    void setSelectedIndex(int index);

    /**
     * Get the selected item (for single-selection mode).
     */
    String getSelectedItem();

    /**
     * Set the selection changed handler (receives index).
     */
    void setOnSelectionChanged(Consumer<Integer> onSelectionChanged);

    /**
     * Set the double-click handler (receives index).
     */
    void setOnItemDoubleClicked(Consumer<Integer> onItemDoubleClicked);

    /**
     * Set the item height.
     */
    void setItemHeight(float height);

    /**
     * Set the font size.
     */
    void setFontSize(float fontSize);

    // Multi-selection support

    /**
     * Enable or disable multi-selection mode.
     * In multi-selection mode, clicking toggles selection without clearing others.
     */
    default void setMultiSelectEnabled(boolean enabled) {
        // Default: no-op for backwards compatibility
    }

    /**
     * Check if multi-selection mode is enabled.
     */
    default boolean isMultiSelectEnabled() {
        return false;
    }

    /**
     * Get all selected indices (for multi-selection mode).
     */
    default Set<Integer> getSelectedIndices() {
        int idx = getSelectedIndex();
        return idx >= 0 ? Set.of(idx) : Set.of();
    }

    /**
     * Get all selected items (for multi-selection mode).
     */
    default List<String> getSelectedItems() {
        String item = getSelectedItem();
        return item != null ? List.of(item) : List.of();
    }

    /**
     * Select an index (adds to selection in multi-select mode).
     */
    default void selectIndex(int index) {
        setSelectedIndex(index);
    }

    /**
     * Deselect an index.
     */
    default void deselectIndex(int index) {
        if (getSelectedIndex() == index) {
            setSelectedIndex(-1);
        }
    }

    /**
     * Toggle selection at an index.
     */
    default void toggleSelection(int index) {
        if (getSelectedIndices().contains(index)) {
            deselectIndex(index);
        } else {
            selectIndex(index);
        }
    }

    /**
     * Clear all selections.
     */
    default void clearSelection() {
        setSelectedIndex(-1);
    }

    /**
     * Select all items.
     */
    default void selectAll() {
        // Default: select first item for single-select mode
        if (!getItems().isEmpty()) {
            setSelectedIndex(0);
        }
    }
}
