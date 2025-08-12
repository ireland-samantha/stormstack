package com.lightningfirefly.engine.rendering.render2d.impl.opengl;

import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowComponent;

/**
 * Thread-local context for GUI rendering.
 * Provides access to shared resources like fonts during the render pass.
 */
public final class GLContext {

    private static final ThreadLocal<GLContext> CURRENT = new ThreadLocal<>();

    private int defaultFontId = -1;
    private long nvgContext;
    private Window window;

    private GLContext() {
    }

    /**
     * Get the current GUI context for this thread.
     * Returns null if no context is active.
     */
    public static GLContext current() {
        return CURRENT.get();
    }

    /**
     * Get the default font ID.
     * Returns -1 if no font is available.
     */
    public static int getDefaultFontId() {
        GLContext ctx = CURRENT.get();
        return ctx != null ? ctx.defaultFontId : -1;
    }

    /**
     * Get the NanoVG context handle.
     */
    public static long getNvgContext() {
        GLContext ctx = CURRENT.get();
        return ctx != null ? ctx.nvgContext : 0;
    }

    /**
     * Get the current window.
     */
    public static Window getWindow() {
        GLContext ctx = CURRENT.get();
        return ctx != null ? ctx.window : null;
    }

    /**
     * Show an overlay component on top of everything else.
     */
    public static void showOverlay(WindowComponent overlay) {
        GLContext ctx = CURRENT.get();
        if (ctx != null && ctx.window != null) {
            ctx.window.showOverlay(overlay);
        }
    }

    /**
     * Hide an overlay component.
     */
    public static void hideOverlay(WindowComponent overlay) {
        GLContext ctx = CURRENT.get();
        if (ctx != null && ctx.window != null) {
            ctx.window.hideOverlay(overlay);
        }
    }

    /**
     * Begin a render context. Call this at the start of the render loop.
     */
    public static void begin(long nvgContext, int defaultFontId) {
        begin(nvgContext, defaultFontId, null);
    }

    /**
     * Begin a render context with window reference.
     */
    public static void begin(long nvgContext, int defaultFontId, Window window) {
        GLContext ctx = CURRENT.get();
        if (ctx == null) {
            ctx = new GLContext();
            CURRENT.set(ctx);
        }
        ctx.nvgContext = nvgContext;
        ctx.defaultFontId = defaultFontId;
        ctx.window = window;
    }

    /**
     * End the render context.
     * Note: We keep the window reference so that input handlers can access it
     * for overlay management (context menus, dialogs, etc.) between frames.
     */
    public static void end() {
        // Keep the context around for reuse, just clear the nvg context
        GLContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.nvgContext = 0;
            // Keep window reference for input event handling between frames
        }
    }
}
