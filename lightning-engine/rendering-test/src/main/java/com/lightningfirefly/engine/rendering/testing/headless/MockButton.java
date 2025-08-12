package com.lightningfirefly.engine.rendering.testing.headless;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Button;

/**
 * Mock Button implementation for headless testing.
 */
public class MockButton extends AbstractWindowComponent implements Button {

    private String text;
    private float fontSize = 14.0f;
    private float[] textColor;
    private float[] backgroundColor;
    private float[] hoverColor;
    private float[] pressedColor;
    private float[] borderColor;
    private float cornerRadius = 4.0f;
    private Runnable onClick;
    private boolean hovered;
    private boolean pressed;

    public MockButton(int x, int y, int width, int height, String text) {
        super(x, y, width, height);
        this.text = text;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    @Override
    public void setTextColor(float[] color) {
        this.textColor = color;
    }

    @Override
    public void setBackgroundColor(float[] color) {
        this.backgroundColor = color;
    }

    @Override
    public void setHoverColor(float[] color) {
        this.hoverColor = color;
    }

    @Override
    public void setPressedColor(float[] color) {
        this.pressedColor = color;
    }

    @Override
    public void setBorderColor(float[] color) {
        this.borderColor = color;
    }

    @Override
    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
    }

    @Override
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    @Override
    public void render(long nvg) {
        // No rendering in headless mode
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button, int action) {
        if (!visible) {
            return false;
        }

        boolean wasPressed = pressed;

        if (contains(mx, my)) {
            if (button == 0) { // Left mouse button
                if (action == 1) { // Press
                    pressed = true;
                    return true;
                } else if (action == 0) { // Release
                    if (pressed && onClick != null) {
                        onClick.run();
                    }
                    pressed = false;
                    return true;
                }
            }
        } else {
            pressed = false;
        }

        return wasPressed != pressed;
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        if (!visible) {
            return false;
        }

        boolean wasHovered = hovered;
        hovered = contains(mx, my);

        if (!hovered) {
            pressed = false;
        }

        return wasHovered != hovered;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isPressed() {
        return pressed;
    }

    public float getFontSize() {
        return fontSize;
    }

    public float getCornerRadius() {
        return cornerRadius;
    }
}
