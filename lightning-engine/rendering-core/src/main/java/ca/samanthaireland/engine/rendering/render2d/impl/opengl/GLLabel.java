/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.rendering.render2d.impl.opengl;

import ca.samanthaireland.engine.rendering.render2d.AbstractWindowComponent;
import ca.samanthaireland.engine.rendering.render2d.Label;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.nanovg.NVGColor;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * A simple text label component with overflow handling.
 * OpenGL/NanoVG implementation of {@link Label}.
 *
 * <p>Supports multiple overflow modes:
 * <ul>
 *   <li>VISIBLE - Text extends beyond bounds (default)</li>
 *   <li>CLIP - Text is clipped at bounds</li>
 *   <li>ELLIPSIS - Text is truncated with "..."</li>
 *   <li>SCROLL - Text scrolls horizontally on hover</li>
 * </ul>
 */
@Slf4j
public class GLLabel extends AbstractWindowComponent implements Label {

    private String text;
    private float fontSize;
    private float[] textColor;
    private int fontId;
    private int alignment;

    // Overflow handling
    private int maxWidth = 0;
    private OverflowMode overflowMode = OverflowMode.VISIBLE;

    // Scroll animation state
    private boolean hovered = false;
    private float scrollOffset = 0;
    private long lastScrollTime = 0;
    private static final float SCROLL_SPEED = 30.0f; // pixels per second
    private static final long SCROLL_DELAY_MS = 500; // Delay before scrolling starts
    private long hoverStartTime = 0;

    public GLLabel(int x, int y, String text) {
        this(x, y, text, 16.0f);
    }

    public GLLabel(int x, int y, String text, float fontSize) {
        super(x, y, 0, (int) fontSize);
        this.text = text;
        this.fontSize = fontSize;
        this.textColor = GLColour.TEXT_PRIMARY;
        this.fontId = -1;
        this.alignment = NVG_ALIGN_LEFT | NVG_ALIGN_TOP;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.scrollOffset = 0; // Reset scroll when text changes
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
        this.height = (int) fontSize;
    }

    public float[] getTextColor() {
        return textColor;
    }

    public void setTextColor(float[] textColor) {
        this.textColor = textColor;
    }

    public void setFontId(int fontId) {
        this.fontId = fontId;
    }

    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }

    @Override
    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.width = maxWidth;
    }

    @Override
    public void setOverflowMode(OverflowMode mode) {
        this.overflowMode = mode;
        this.scrollOffset = 0;
    }

    @Override
    public void render(long nvg) {
        if (!visible || text == null || text.isEmpty()) {
            return;
        }

        try (var color = NVGColor.malloc()) {
            // Use explicit font or fall back to context default
            int effectiveFontId = fontId >= 0 ? fontId : GLContext.getDefaultFontId();
            if (effectiveFontId >= 0) {
                nvgFontFaceId(nvg, effectiveFontId);
            }
            nvgFontSize(nvg, fontSize);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgFillColor(nvg, GLColour.rgba(textColor, color));

            float adjustedY = y + fontSize * 0.8f;

            // No overflow handling needed if maxWidth is 0
            if (maxWidth <= 0 || overflowMode == OverflowMode.VISIBLE) {
                nvgTextAlign(nvg, alignment);
                nvgText(nvg, x, adjustedY, text);
                return;
            }

            // Measure text width
            float[] bounds = new float[4];
            nvgTextBounds(nvg, 0, 0, text, bounds);
            float textWidth = bounds[2] - bounds[0];

            // Text fits, no overflow handling needed
            if (textWidth <= maxWidth) {
                nvgText(nvg, x, adjustedY, text);
                return;
            }

            // Handle overflow based on mode
            switch (overflowMode) {
                case CLIP:
                    renderClipped(nvg, adjustedY, color);
                    break;
                case ELLIPSIS:
                    renderEllipsis(nvg, adjustedY, textWidth, color);
                    break;
                case SCROLL:
                    renderScrolling(nvg, adjustedY, textWidth, color);
                    break;
                default:
                    nvgText(nvg, x, adjustedY, text);
            }
        }
    }

    private void renderClipped(long nvg, float adjustedY, NVGColor color) {
        nvgSave(nvg);
        nvgIntersectScissor(nvg, x, y, maxWidth, height + 4);
        nvgText(nvg, x, adjustedY, text);
        nvgRestore(nvg);
    }

    private void renderEllipsis(long nvg, float adjustedY, float textWidth, NVGColor color) {
        // Find how much text fits with ellipsis
        String ellipsis = "...";
        float[] ellipsisBounds = new float[4];
        nvgTextBounds(nvg, 0, 0, ellipsis, ellipsisBounds);
        float ellipsisWidth = ellipsisBounds[2] - ellipsisBounds[0];

        float availableWidth = maxWidth - ellipsisWidth;
        String truncated = text;

        // Binary search for the right length
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String testText = text.substring(0, mid);
            float[] testBounds = new float[4];
            nvgTextBounds(nvg, 0, 0, testText, testBounds);
            float testWidth = testBounds[2] - testBounds[0];

            if (testWidth <= availableWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        truncated = text.substring(0, low) + ellipsis;

        nvgSave(nvg);
        nvgIntersectScissor(nvg, x, y, maxWidth, height + 4);
        nvgText(nvg, x, adjustedY, truncated);
        nvgRestore(nvg);
    }

    private void renderScrolling(long nvg, float adjustedY, float textWidth, NVGColor color) {
        // Update scroll animation
        if (hovered) {
            long now = System.currentTimeMillis();
            if (now - hoverStartTime > SCROLL_DELAY_MS) {
                float deltaTime = (now - lastScrollTime) / 1000.0f;
                lastScrollTime = now;

                float maxScroll = textWidth - maxWidth + 10;
                scrollOffset += SCROLL_SPEED * deltaTime;

                // Ping-pong scroll
                if (scrollOffset > maxScroll) {
                    scrollOffset = maxScroll;
                }
            }
        } else {
            // Reset scroll when not hovered
            scrollOffset = Math.max(0, scrollOffset - SCROLL_SPEED * 0.016f);
        }

        nvgSave(nvg);
        nvgIntersectScissor(nvg, x, y, maxWidth, height + 4);
        nvgText(nvg, x - scrollOffset, adjustedY, text);
        nvgRestore(nvg);
    }

    @Override
    public boolean onMouseMove(int mx, int my) {
        boolean wasHovered = hovered;
        hovered = contains(mx, my);

        if (hovered && !wasHovered) {
            hoverStartTime = System.currentTimeMillis();
            lastScrollTime = hoverStartTime;
        }

        return false;
    }

    @Override
    public boolean contains(int px, int py) {
        int effectiveWidth = maxWidth > 0 ? maxWidth : width;
        return px >= x && px < x + effectiveWidth
            && py >= y && py < y + height + 4;
    }
}
