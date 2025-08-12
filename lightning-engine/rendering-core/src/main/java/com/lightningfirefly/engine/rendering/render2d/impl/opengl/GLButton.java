package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import com.lightningfirefly.engine.rendering.render2d.AbstractWindowComponent;
import com.lightningfirefly.engine.rendering.render2d.Button;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.nanovg.NVGColor;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * A clickable button component.
 * OpenGL/NanoVG implementation of {@link Button}.
 */
@Slf4j
public class GLButton extends AbstractWindowComponent implements Button {

    private String text;
    private float fontSize;
    private float[] textColor;
    private float[] backgroundColor;
    private float[] hoverColor;
    private float[] pressedColor;
    private float[] borderColor;
    private float cornerRadius;
    private int fontId;

    private boolean hovered;
    private boolean pressed;
    private Runnable onClick;

    public GLButton(int x, int y, int width, int height, String text) {
        super(x, y, width, height);
        this.text = text;
        this.fontSize = 14.0f;
        this.textColor = GLColour.TEXT_PRIMARY;
        this.backgroundColor = GLColour.BUTTON_BG;
        this.hoverColor = GLColour.BUTTON_HOVER;
        this.pressedColor = GLColour.BUTTON_PRESSED;
        this.borderColor = GLColour.BORDER;
        this.cornerRadius = 4.0f;
        this.fontId = -1;
        this.hovered = false;
        this.pressed = false;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    public void setTextColor(float[] textColor) {
        this.textColor = textColor;
    }

    public void setBackgroundColor(float[] backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setHoverColor(float[] hoverColor) {
        this.hoverColor = hoverColor;
    }

    public void setPressedColor(float[] pressedColor) {
        this.pressedColor = pressedColor;
    }

    public void setBorderColor(float[] borderColor) {
        this.borderColor = borderColor;
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    public void setFontId(int fontId) {
        this.fontId = fontId;
    }

    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    @Override
    public void render(long nvg) {
        if (!visible) {
            return;
        }

        try (var color = NVGColor.malloc()) {
            // Determine background color based on state
            float[] bgColor = pressed ? pressedColor : (hovered ? hoverColor : backgroundColor);

            // Draw background and border
            NvgUtils.drawRoundedRect(nvg, x, y, width, height, cornerRadius,
                    bgColor, borderColor, GuiConstants.BORDER_WIDTH, color);

            // Draw text centered
            if (text != null && !text.isEmpty()) {
                NvgUtils.drawText(nvg, x + width / 2.0f, y + height / 2.0f, text, textColor,
                        fontId, fontSize, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE, color);
            }
        }
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
}
