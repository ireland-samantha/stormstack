package com.lightningfirefly.engine.rendering.render2d;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for tree view components.
 */
public interface TreeView extends WindowComponent {

    /**
     * Add a root node.
     */
    void addRootNode(TreeNode node);

    /**
     * Get all root nodes.
     */
    List<TreeNode> getRootNodes();

    /**
     * Clear all nodes.
     */
    void clearNodes();

    /**
     * Expand all nodes.
     */
    void expandAll();

    /**
     * Collapse all nodes.
     */
    void collapseAll();

    /**
     * Get the selected node.
     */
    TreeNode getSelectedNode();

    /**
     * Set the selection handler.
     */
    void setOnSelect(Consumer<TreeNode> onSelect);

    /**
     * Set the item height.
     */
    void setItemHeight(int height);

    /**
     * Set the indent width for child nodes.
     */
    void setIndentWidth(int width);
}
