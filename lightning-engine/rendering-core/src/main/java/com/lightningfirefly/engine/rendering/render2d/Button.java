package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for clickable button components.
 */
public interface Button extends WindowComponent {

    /**
     * Get the button text.
     */
    String getText();

    /**
     * Set the button text.
     */
    void setText(String text);

    /**
     * Set the font size.
     */
    void setFontSize(float fontSize);

    /**
     * Set the text color.
     */
    void setTextColor(float[] color);

    /**
     * Set the background color.
     */
    void setBackgroundColor(float[] color);

    /**
     * Set the hover color.
     */
    void setHoverColor(float[] color);

    /**
     * Set the pressed color.
     */
    void setPressedColor(float[] color);

    /**
     * Set the border color.
     */
    void setBorderColor(float[] color);

    /**
     * Set the corner radius.
     */
    void setCornerRadius(float radius);

    /**
     * Set the click handler.
     */
    void setOnClick(Runnable onClick);
}
