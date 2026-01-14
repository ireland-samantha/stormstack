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


package ca.samanthaireland.game.renderering;

import ca.samanthaireland.game.domain.ControlSystem;
import ca.samanthaireland.game.domain.Sprite;
import ca.samanthaireland.game.orchestrator.SpriteSnapshotMapper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for rendering game state to a window.
 *
 * <p>The GameRenderer abstracts the rendering layer, providing a clean API for:
 * <ul>
 *   <li>Creating and managing a game window</li>
 *   <li>Rendering sprites based on ECS components data</li>
 *   <li>Handling input via ControlSystem</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * GameRenderer renderer = GameRendererBuilder.create()
 *     .windowSize(800, 600)
 *     .title("My Game")
 *     .build();
 *
 * renderer.setControlSystem(myControls);
 * renderer.setSpriteMapper(components -> convertToSprites(components));
 *
 * // Game loop
 * renderer.start(() -> {
 *     // Called every frame
 *     Snapshot components = fetchLatestSnapshot();
 *     renderer.renderSnapshot(components);
 * });
 * }</pre>
 */
public interface GameRenderer {

    /**
     * Set the control system for handling input events.
     *
     * @param controlSystem the control system to use
     */
    void setControlSystem(ControlSystem controlSystem);

    /**
     * Set the sprite mapper function that converts snapshots to sprites.
     *
     * @param mapper function that extracts sprites from a components
     */
    void setSpriteMapper(SpriteSnapshotMapper mapper);

    /**
     * Render snapshot components by converting them to sprites and displaying them.
     *
     * @param snapshot the ECS components to render
     */
    void renderSnapshot(Object snapshot);

    /**
     * Directly render a list of sprites.
     *
     * @param sprites the sprites to render
     */
    void renderSprites(List<Sprite> sprites);

    /**
     * Start the render loop with the given update callback.
     * This method blocks until the window is closed.
     *
     * @param onUpdate called every frame before rendering
     */
    void start(Runnable onUpdate);

    /**
     * Start the render loop in the background.
     * Returns immediately, runs the loop on a separate thread.
     *
     * @param onUpdate called every frame before rendering
     */
    void startAsync(Runnable onUpdate);

    /**
     * Run a specific number of frames (useful for testing).
     *
     * @param frames   number of frames to run
     * @param onUpdate called every frame before rendering
     */
    void runFrames(int frames, Runnable onUpdate);

    /**
     * Stop the render loop and close the window.
     */
    void stop();

    /**
     * Check if the renderer is currently running.
     *
     * @return true if the render loop is active
     */
    boolean isRunning();

    /**
     * Get the window width.
     *
     * @return window width in pixels
     */
    int getWidth();

    /**
     * Get the window height.
     *
     * @return window height in pixels
     */
    int getHeight();

    /**
     * Set an error handler for runtime errors.
     *
     * @param handler called when an error occurs
     */
    void setOnError(Consumer<Exception> handler);

    /**
     * Dispose of resources and cleanup.
     */
    void dispose();

}
