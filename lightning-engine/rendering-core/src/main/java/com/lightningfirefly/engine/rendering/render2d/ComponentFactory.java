package com.lightningfirefly.engine.rendering.render2d;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Factory interface for creating GUI components.
 *
 * <p>This abstraction allows client code to create GUI components without
 * depending on specific rendering implementations (e.g., OpenGL).
 *
 * <p>Usage with dependency injection:
 * <pre>{@code
 * public class MyPanel {
 *     private final ComponentFactory factory;
 *
 *     public MyPanel(ComponentFactory factory) {
 *         this.factory = factory;
 *         Button button = factory.createButton(10, 10, 100, 30, "Click Me");
 *         Label label = factory.createLabel(10, 50, "Hello");
 *     }
 * }
 * }</pre>
 */
public interface ComponentFactory {

    /**
     * Create a button component.
     */
    Button createButton(int x, int y, int width, int height, String text);

    /**
     * Create a label component.
     */
    Label createLabel(int x, int y, String text);

    /**
     * Create a label component with font size.
     */
    Label createLabel(int x, int y, String text, float fontSize);

    /**
     * Create a panel component.
     */
    Panel createPanel(int x, int y, int width, int height);

    /**
     * Create a text field component.
     */
    TextField createTextField(int x, int y, int width, int height);

    /**
     * Create a list view component.
     */
    ListView createListView(int x, int y, int width, int height);

    /**
     * Create a tree view component.
     */
    TreeView createTreeView(int x, int y, int width, int height);

    /**
     * Create a tree node.
     */
    TreeNode createTreeNode(String text);

    /**
     * Create a tree node with user data.
     */
    TreeNode createTreeNode(String text, Object userData);

    /**
     * Create an image component.
     */
    Image createImage(int x, int y, int width, int height);

    /**
     * Get standard colours for the current theme.
     */
    Colours getColours();

    /**
     * Open a file dialog.
     *
     * @param title the dialog title
     * @param initialDir the initial directory
     * @param filters file filters (e.g., "*.png,*.jpg")
     * @param filterDescription description of the filter
     * @return the selected file path, or empty if cancelled
     */
    Optional<Path> openFileDialog(String title, String initialDir, String filters, String filterDescription);

    /**
     * Interface providing access to standard theme colours.
     */
    interface Colours {
        float[] white();
        float[] black();
        float[] red();
        float[] green();
        float[] blue();
        float[] yellow();
        float[] transparent();

        float[] background();
        float[] panelBg();
        float[] buttonBg();
        float[] buttonHover();
        float[] buttonPressed();
        float[] border();
        float[] textPrimary();
        float[] textSecondary();
        float[] accent();
        float[] selected();
        float[] success();
        float[] warning();
        float[] error();

        /**
         * Create a new colour with modified alpha.
         */
        float[] withAlpha(float[] colour, float alpha);
    }
}
