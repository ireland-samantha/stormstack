package com.lightningfirefly.engine.gui;

import com.lightningfirefly.engine.rendering.render2d.*;
import com.lightningfirefly.engine.rendering.render2d.impl.opengl.*;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Test implementation of ComponentFactory for unit testing without OpenGL.
 * Creates real GL* components but doesn't require actual OpenGL context.
 */
public class TestComponentFactory implements ComponentFactory {

    private final TestColours colours = new TestColours();

    @Override
    public Button createButton(int x, int y, int width, int height, String text) {
        return new GLButton(x, y, width, height, text);
    }

    @Override
    public Label createLabel(int x, int y, String text) {
        return createLabel(x, y, text, 14.0f);
    }

    @Override
    public Label createLabel(int x, int y, String text, float fontSize) {
        return new GLLabel(x, y, text, fontSize);
    }

    @Override
    public Panel createPanel(int x, int y, int width, int height) {
        return new GLPanel(x, y, width, height);
    }

    @Override
    public TextField createTextField(int x, int y, int width, int height) {
        return new GLTextField(x, y, width, height);
    }

    @Override
    public ListView createListView(int x, int y, int width, int height) {
        return new GLListView(x, y, width, height);
    }

    @Override
    public TreeView createTreeView(int x, int y, int width, int height) {
        return new GLTreeView(x, y, width, height);
    }

    @Override
    public TreeNode createTreeNode(String text) {
        return new GLTreeNode(text);
    }

    @Override
    public TreeNode createTreeNode(String text, Object userData) {
        return new GLTreeNode(text, userData);
    }

    @Override
    public Image createImage(int x, int y, int width, int height) {
        return new GLImage(x, y, width, height);
    }

    @Override
    public Colours getColours() {
        return colours;
    }

    @Override
    public Optional<Path> openFileDialog(String title, String initialDir, String filters, String filterDescription) {
        return Optional.empty();
    }

    /**
     * Test implementation of Colours that doesn't depend on GLColour constants.
     */
    private static class TestColours implements Colours {
        private static final float[] WHITE = {1.0f, 1.0f, 1.0f, 1.0f};
        private static final float[] BLACK = {0.0f, 0.0f, 0.0f, 1.0f};
        private static final float[] RED = {1.0f, 0.0f, 0.0f, 1.0f};
        private static final float[] GREEN = {0.0f, 1.0f, 0.0f, 1.0f};
        private static final float[] BLUE = {0.0f, 0.0f, 1.0f, 1.0f};
        private static final float[] YELLOW = {1.0f, 1.0f, 0.0f, 1.0f};
        private static final float[] TRANSPARENT = {0.0f, 0.0f, 0.0f, 0.0f};
        private static final float[] GRAY = {0.5f, 0.5f, 0.5f, 1.0f};
        private static final float[] DARK_GRAY = {0.2f, 0.2f, 0.2f, 1.0f};

        @Override public float[] white() { return WHITE; }
        @Override public float[] black() { return BLACK; }
        @Override public float[] red() { return RED; }
        @Override public float[] green() { return GREEN; }
        @Override public float[] blue() { return BLUE; }
        @Override public float[] yellow() { return YELLOW; }
        @Override public float[] transparent() { return TRANSPARENT; }
        @Override public float[] background() { return DARK_GRAY; }
        @Override public float[] panelBg() { return DARK_GRAY; }
        @Override public float[] buttonBg() { return GRAY; }
        @Override public float[] buttonHover() { return GRAY; }
        @Override public float[] buttonPressed() { return GRAY; }
        @Override public float[] border() { return GRAY; }
        @Override public float[] textPrimary() { return WHITE; }
        @Override public float[] textSecondary() { return GRAY; }
        @Override public float[] accent() { return BLUE; }
        @Override public float[] selected() { return BLUE; }
        @Override public float[] success() { return GREEN; }
        @Override public float[] warning() { return YELLOW; }
        @Override public float[] error() { return RED; }

        @Override
        public float[] withAlpha(float[] colour, float alpha) {
            return new float[]{colour[0], colour[1], colour[2], alpha};
        }
    }
}
