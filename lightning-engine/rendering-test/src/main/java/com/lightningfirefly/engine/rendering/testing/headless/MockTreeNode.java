package com.lightningfirefly.engine.rendering.testing.headless;

import com.lightningfirefly.engine.rendering.render2d.TreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock TreeNode implementation for headless testing.
 */
public class MockTreeNode implements TreeNode {

    private String label;
    private Object userData;
    private boolean expanded = false;
    private final List<TreeNode> children = new ArrayList<>();

    public MockTreeNode(String label) {
        this.label = label;
    }

    public MockTreeNode(String label, Object userData) {
        this.label = label;
        this.userData = userData;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public List<TreeNode> getChildren() {
        return children;
    }

    @Override
    public void addChild(TreeNode child) {
        children.add(child);
    }

    @Override
    public void removeChild(TreeNode child) {
        children.remove(child);
    }

    @Override
    public void clearChildren() {
        children.clear();
    }

    @Override
    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public Object getUserData() {
        return userData;
    }

    @Override
    public void setUserData(Object userData) {
        this.userData = userData;
    }

    @Override
    public String toString() {
        return "MockTreeNode{label='" + label + "', children=" + children.size() + "}";
    }
}
