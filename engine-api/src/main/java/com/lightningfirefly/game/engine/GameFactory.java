package com.lightningfirefly.game.engine;

import java.util.List;

/**
 * A game factory defines a complete game that can be installed and run on the engine.
 *
 * <p>A game factory provides:
 * <ul>
 *   <li>Scene setup - attaching sprites, controls, and game master</li>
 *   <li>Resource definitions - textures and other assets</li>
 *   <li>Required ECS modules - the server-side modules needed</li>
 *   <li>Game master JAR - the game logic to run on the server</li>
 * </ul>
 */
public interface GameFactory {

    /**
     * Attach this game to a scene.
     * Called when the game is started to set up sprites, controls, and game master.
     *
     * @param scene the scene to attach to
     */
    void attachScene(GameScene scene);

    /**
     * Get the resources (textures, etc.) required by this game.
     *
     * @return list of resource definitions
     */
    default List<GameResource> getResources() {
        return List.of();
    }

    /**
     * Get the ECS module names required by this game.
     *
     * @return list of module names (e.g., "MoveModule", "SpawnModule")
     */
    default List<String> getRequiredModules() {
        return List.of("MoveModule", "SpawnModule", "RenderModule");
    }

    /**
     * Get the game master name to enable for this game.
     *
     * @return the game master name, or null if no game master is needed
     */
    default String getGameMasterName() {
        return null;
    }

    /**
     * Get the game master JAR bytes if this factory bundles a game master.
     * Return null if the game master is already installed on the server.
     *
     * @return the JAR bytes, or null if not bundled
     */
    default byte[] getGameMasterJar() {
        return null;
    }

    /**
     * Get module JARs that need to be uploaded to the server.
     * Returns a map of module name to JAR bytes.
     *
     * @return map of module names to JAR bytes, empty if no custom modules needed
     */
    default java.util.Map<String, byte[]> getModuleJars() {
        return java.util.Map.of();
    }

    /**
     * Resource definition for a game asset.
     *
     * @param name         the resource name
     * @param type         the resource type (e.g., "TEXTURE")
     * @param data         the resource bytes
     * @param texturePath  the local texture path this maps to (for sprite resolution)
     */
    record GameResource(String name, String type, byte[] data, String texturePath) {
        public GameResource(String name, String type, byte[] data) {
            this(name, type, data, name);
        }
    }
}
