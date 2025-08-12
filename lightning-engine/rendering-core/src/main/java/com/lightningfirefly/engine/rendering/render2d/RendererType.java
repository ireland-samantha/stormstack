package com.lightningfirefly.engine.rendering.render2d;

/**
 * Enumeration of available renderer backends.
 */
public enum RendererType {

    /**
     * NanoVG-based renderer (default).
     * Uses NanoVG for vector graphics rendering with hardware-accelerated anti-aliasing.
     */
    NANOVG("nanovg"),

    /**
     * Pure OpenGL renderer.
     * Uses direct OpenGL calls with custom shaders.
     * Provides more control and potentially better performance for specific use cases.
     */
    PURE_OPENGL("opengl");

    private final String cliName;

    RendererType(String cliName) {
        this.cliName = cliName;
    }

    /**
     * Get the CLI name for this renderer type.
     */
    public String getCliName() {
        return cliName;
    }

    /**
     * Parse a renderer type from a CLI argument string.
     *
     * @param value the CLI value (e.g., "nanovg", "opengl")
     * @return the RendererType, or NANOVG if not recognized
     */
    public static RendererType fromString(String value) {
        if (value == null) {
            return NANOVG;
        }
        for (RendererType type : values()) {
            if (type.cliName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return NANOVG;
    }
}
