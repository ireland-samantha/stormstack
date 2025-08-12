package com.lightningfirefly.engine.rendering.testing.headless;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Panel;
import com.lightningfirefly.engine.rendering.render2d.WindowComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Panel implementation for headless testing.
 */
public class MockPanel extends AbstractWindowComponent implements Panel {

    private final List<WindowComponent> children = new ArrayList<>();
    private String title;
    private float[] backgroundColor;
    private float[] borderColor;
    private float borderWidth = 1.0f;
    private float cornerRadius = 4.0f;
    private float titleFontSize = 14.0f;

    public MockPanel(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public MockPanel(int x, int y, int width, int height, String title) {
        super(x, y, width, height);
        this.title = title;
    }

    @Override
    public void addChild(WindowComponent child) {
        children.add(child);
    }

    @Override
    public void removeChild(WindowComponent child) {
        children.remove(child);
    }

    @Override
    public void clearChildren() {
        children.clear();
    }

    @Override
    public List<WindowComponent> getChildren() {
        return children;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setBackgroundColor(float[] color) {
        this.backgroundColor = color;
    }

    @Override
    public void setBorderColor(float[] color) {
        this.borderColor = color;
    }

    @Override
    public void setBorderWidth(float width) {
        this.borderWidth = width;
    }

    @Override
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
    }

    @Override
    public void setTitleFontSize(float fontSize) {
        this.titleFontSize = fontSize;
    }

    public float getBorderWidth() {
        return borderWidth;
    }

    public float getCornerRadius() {
        return cornerRadius;
    }

    public float getTitleFontSize() {
        return titleFontSize;
    }

    @Override
    public void render(long nvg) {
        // No rendering in headless mode
        for (WindowComponent child : children) {
            child.render(nvg);
        }
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible || !contains(mx, my)) {
            return false;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onMouseClick(mx, my, button, action)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) {
            return false;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).onMouseMove(mx, my);
        }
        return contains(mx, my);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, double scrollX, double scrollY) {
        if (!visible || !contains(mx, my)) {
            return false;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onMouseScroll(mx, my, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyPress(int key, int action) {
        return onKeyPress(key, action, 0);
    }

    @Override
    public boolean onKeyPress(int key, int action, int mods) {
        if (!visible) {
            return false;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onKeyPress(key, action, mods)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCharInput(int codepoint) {
        if (!visible) {
            return false;
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onCharInput(codepoint)) {
                return true;
            }
        }
        return false;
    }
}
