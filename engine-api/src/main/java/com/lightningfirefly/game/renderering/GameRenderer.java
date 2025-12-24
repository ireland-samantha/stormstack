package com.lightningfirefly.game.renderering;

import com.lightningfirefly.game.domain.ControlSystem;
import com.lightningfirefly.game.domain.Sprite;
import com.lightningfirefly.game.orchestrator.SpriteMapper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for rendering game state to a window.
 *
 * <p>The GameRenderer abstracts the rendering layer, providing a clean API for:
 * <ul>
 *   <li>Creating and managing a game window</li>
 *   <li>Rendering sprites based on ECS snapshot data</li>
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
 * renderer.setSpriteMapper(snapshot -> convertToSprites(snapshot));
 *
 * // Game loop
 * renderer.start(() -> {
 *     // Called every frame
 *     Snapshot snapshot = fetchLatestSnapshot();
 *     renderer.renderSnapshot(snapshot);
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
     * @param mapper function that extracts sprites from a snapshot
     */
    void setSpriteMapper(SpriteMapper mapper);

    /**
     * Render a snapshot by converting it to sprites and displaying them.
     *
     * @param snapshot the ECS snapshot to render
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
