package com.lightningfirefly.engine.rendering.render2d;

/**
 * Interface for loading and managing fonts.
 */
public interface FontLoader {

    /**
     * Load the default system font.
     *
     * @return the font ID, or -1 if loading failed
     */
    int loadDefaultFont();

    /**
     * Load a font from a resource path.
     *
     * @param name the name to register the font as
     * @param resourcePath the classpath resource path to the font file
     * @return the font ID, or -1 if loading failed
     */
    int loadFontFromResource(String name, String resourcePath);

    /**
     * Load a font from a file path.
     *
     * @param name the name to register the font as
     * @param filePath the file system path to the font file
     * @return the font ID, or -1 if loading failed
     */
    int loadFontFromFile(String name, String filePath);

    /**
     * Get a loaded font by name.
     *
     * @param name the font name
     * @return the font ID, or -1 if not found
     */
    int getFont(String name);
}
