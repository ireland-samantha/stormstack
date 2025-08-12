package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for text label components.
 */
public interface Label extends WindowComponent {

    /**
     * Text overflow behavior when text exceeds the label width.
     */
    enum OverflowMode {
        /** Text is not clipped, may extend beyond bounds */
        VISIBLE,
        /** Text is clipped at bounds */
        CLIP,
        /** Text is truncated with ellipsis (...) */
        ELLIPSIS,
        /** Text scrolls horizontally on hover */
        SCROLL
    }

    /**
     * Get the label text.
     */
    String getText();

    /**
     * Set the label text.
     */
    void setText(String text);

    /**
     * Get the font size.
     */
    float getFontSize();

    /**
     * Set the font size.
     */
    void setFontSize(float fontSize);

    /**
     * Get the text color.
     */
    float[] getTextColor();

    /**
     * Set the text color.
     */
    void setTextColor(float[] color);

    /**
     * Set the text alignment.
     */
    void setAlignment(int alignment);

    /**
     * Set the maximum width for the label. Text exceeding this width
     * will be handled according to the overflow mode.
     * @param maxWidth the maximum width in pixels, or 0 for unlimited
     */
    default void setMaxWidth(int maxWidth) {
        // Default implementation does nothing
    }

    /**
     * Set the overflow mode for handling text that exceeds the label width.
     * @param mode the overflow mode
     */
    default void setOverflowMode(OverflowMode mode) {
        // Default implementation does nothing
    }
}
