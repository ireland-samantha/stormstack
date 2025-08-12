package com.lightningfirefly.engine.rendering.render2d;

import java.util.List;

/**
 * Interface for tree node data in a TreeView.
 */
public interface TreeNode {

    /**
     * Get the node label.
     */
    String getLabel();

    /**
     * Set the node label.
     */
    void setLabel(String label);

    /**
     * Get the child nodes.
     */
    List<TreeNode> getChildren();

    /**
     * Add a child node.
     */
    void addChild(TreeNode child);

    /**
     * Remove a child node.
     */
    void removeChild(TreeNode child);

    /**
     * Clear all children.
     */
    void clearChildren();

    /**
     * Check if the node is expanded.
     */
    boolean isExpanded();

    /**
     * Set the expanded state.
     */
    void setExpanded(boolean expanded);

    /**
     * Check if the node has children.
     */
    default boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    /**
     * Get user data associated with this node.
     */
    Object getUserData();

    /**
     * Set user data associated with this node.
     */
    void setUserData(Object userData);
}
