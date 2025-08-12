package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowFactory;

/**
 * Factory for creating OpenGL/NanoVG-based Window instances.
 *
 * <p>This is the default implementation of {@link WindowFactory} that creates
 * {@link GLWindow} instances using LWJGL and NanoVG.
 */
public class GLWindowFactory implements WindowFactory {

    private static final GLWindowFactory INSTANCE = new GLWindowFactory();

    /**
     * Get the singleton instance.
     */
    public static GLWindowFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public Window create(int width, int height, String title) {
        return new GLWindow(width, height, title);
    }
}
