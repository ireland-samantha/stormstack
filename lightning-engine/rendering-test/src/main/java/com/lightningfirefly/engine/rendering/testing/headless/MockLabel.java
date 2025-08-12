package com.lightningfirefly.engine.rendering.testing.headless;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Label;

/**
 * Mock Label implementation for headless testing.
 */
public class MockLabel extends AbstractWindowComponent implements Label {

    private String text;
    private float fontSize = 16.0f;
    private float[] textColor;
    private int alignment;

    public MockLabel(int x, int y, String text) {
        super(x, y, 0, 16);
        this.text = text;
    }

    public MockLabel(int x, int y, String text, float fontSize) {
        super(x, y, 0, (int) fontSize);
        this.text = text;
        this.fontSize = fontSize;
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
    public float getFontSize() {
        return fontSize;
    }

    @Override
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
        this.height = (int) fontSize;
    }

    @Override
    public float[] getTextColor() {
        return textColor;
    }

    @Override
    public void setTextColor(float[] color) {
        this.textColor = color;
    }

    @Override
    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }

    public int getAlignment() {
        return alignment;
    }

    @Override
    public void render(long nvg) {
        // No rendering in headless mode
    }
}
