package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for text input field components.
 */
public interface TextField extends WindowComponent {

    /**
     * Get the current text.
     */
    String getText();

    /**
     * Set the text.
     */
    void setText(String text);

    /**
     * Get the placeholder text.
     */
    String getPlaceholder();

    /**
     * Set the placeholder text.
     */
    void setPlaceholder(String placeholder);

    /**
     * Check if the field is focused.
     */
    boolean isFocused();

    /**
     * Set the focus state.
     */
    void setFocused(boolean focused);

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
     * Set the change handler called when text changes.
     */
    void setOnChange(Runnable onChange);
}
