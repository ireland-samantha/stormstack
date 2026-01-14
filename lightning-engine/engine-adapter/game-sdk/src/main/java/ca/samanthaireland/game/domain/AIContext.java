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


package ca.samanthaireland.game.domain;

import ca.samanthaireland.game.backend.installation.AIFactory;

/**
 * Context provided to AI for interaction with the server.
 *
 * <p>Example usage:
 * <pre>{@code
 * public class MyAI implements AI {
 *     private final AIContext context;
 *
 *     public MyAI(AIContext context) {
 *         this.context = context;
 *     }
 *
 *     @Override
 *     public void onTick() {
 *         long matchId = context.getMatchId();
 *         // Access game state and implement tick logic
 *     }
 * }
 * }</pre>
 *
 * @see AIFactory
 * @see AI
 */
public interface AIContext {

    /**
     * Get the current match ID this AI is operating in.
     *
     * <p>AI instances are bound to specific matches for isolation.
     * All entity operations should be scoped to this match ID.
     *
     * @return the match ID
     */
    long getMatchId();

    /**
     * Get the current tick number.
     *
     * @return the current tick number
     */
    long getCurrentTick();

    /**
     * Execute an AI command.
     *
     * @param aiCommand the command to execute
     */
    void executeCommand(AICommand aiCommand);

    /**
     * Look up a resource ID by its name.
     *
     * <p>Resources are uploaded by the client and stored on the server.
     * This method allows the AI to look up resource IDs for
     * attaching sprites to entities.
     *
     * @param resourceName the name of the resource (e.g., "red-checker")
     * @return the resource ID, or -1 if not found
     */
    default long getResourceIdByName(String resourceName) {
        return -1; // Default implementation returns not found
    }
}
