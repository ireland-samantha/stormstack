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


package ca.samanthaireland.stormstack.thunder.engine.core.match;

import java.util.List;

/**
 * Represents a game match within an execution container.
 *
 * @param id              Unique match identifier
 * @param containerId     ID of the container this match belongs to (0 for default container)
 * @param enabledModules  List of module names enabled for this match
 * @param playerLimit     Maximum number of players allowed (0 means unlimited)
 */
public record Match(
        long id,
        long containerId,
        List<String> enabledModules,
        int playerLimit
) {
    /**
     * Creates a match for the default container (container ID 0) with no player limit.
     * Provided for backward compatibility.
     */
    public Match(long id, List<String> enabledModules) {
        this(id, 0L, enabledModules, 0);
    }

    /**
     * Creates a match with no player limit.
     * Provided for backward compatibility.
     */
    public Match(long id, long containerId, List<String> enabledModules) {
        this(id, containerId, enabledModules, 0);
    }

    /**
     * Checks if this match can accept another player.
     *
     * @param currentPlayerCount the current number of players in the match
     * @return true if another player can join, false if at capacity
     */
    public boolean canAcceptPlayer(int currentPlayerCount) {
        return playerLimit == 0 || currentPlayerCount < playerLimit;
    }

    /**
     * Creates a copy of this match with a different container ID.
     */
    public Match withContainerId(long newContainerId) {
        return new Match(id, newContainerId, enabledModules, playerLimit);
    }

    /**
     * Creates a copy of this match with a different ID.
     */
    public Match withId(long newId) {
        return new Match(newId, containerId, enabledModules, playerLimit);
    }

    /**
     * Creates a copy of this match with a different player limit.
     */
    public Match withPlayerLimit(int newPlayerLimit) {
        return new Match(id, containerId, enabledModules, newPlayerLimit);
    }
}
