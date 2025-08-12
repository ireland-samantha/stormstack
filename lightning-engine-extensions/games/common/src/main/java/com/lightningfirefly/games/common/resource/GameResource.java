package com.lightningfirefly.games.common.resource;

/**
 * Represents a game resource (texture, sound, etc.).
 *
 * @param id the resource ID
 * @param name the resource name
 * @param type the resource type
 * @param data the resource data (may be null if not loaded)
 */
public record GameResource(
        long id,
        String name,
        ResourceType type,
        byte[] data
) {
    /**
     * Check if the resource data is loaded.
     */
    public boolean isLoaded() {
        return data != null && data.length > 0;
    }

    /**
     * Resource types.
     */
    public enum ResourceType {
        TEXTURE,
        SOUND,
        CONFIG,
        FONT
    }
}
