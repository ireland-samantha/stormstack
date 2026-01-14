/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.backend.installation;

import ca.samanthaireland.game.domain.GameScene;

import java.util.List;
import java.util.Optional;

/**
 * A game factory defines a complete game that can be installed and run on the engine.
 *
 * <p>A game factory provides:
 * <ul>
 *   <li>Scene setup - attaching sprites, controls, and AI</li>
 *   <li>Resource definitions - textures and other assets</li>
 *   <li>Required ECS modules - the server-side modules needed</li>
 *   <li>AI JAR - the game logic to run on the server</li>
 * </ul>
 */
public interface GameFactory {

    /**
     * Attach this game to a scene.
     * Called when the game is started to set up sprites, controls, and AI.
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
     * @return list of module names (e.g., "MoveModule", "EntityModule")
     */
    default List<String> getRequiredModules() {
        return List.of("MoveModule", "EntityModule", "RenderModule");
    }

    /**
     * Get the AI name to enable for this game.
     *
     * @return the AI name, or empty if no AI is needed
     */
    default Optional<String> getAIName() {
        return Optional.empty();
    }

    /**
     * Get the AI JAR bytes if this factory bundles an AI.
     *
     * @return the JAR bytes, or empty if not bundled
     */
    default Optional<byte[]> getAIJar() {
        return Optional.empty();
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
