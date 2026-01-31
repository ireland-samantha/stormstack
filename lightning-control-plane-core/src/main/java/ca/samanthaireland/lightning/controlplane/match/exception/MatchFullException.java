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

package ca.samanthaireland.lightning.controlplane.match.exception;

import ca.samanthaireland.lightning.controlplane.match.model.ClusterMatchId;

/**
 * Exception thrown when a match cannot accept more players due to reaching its player limit.
 */
public class MatchFullException extends RuntimeException {

    private final ClusterMatchId matchId;
    private final int playerLimit;
    private final int currentPlayers;

    /**
     * Creates a new MatchFullException.
     *
     * @param matchId        the match ID
     * @param playerLimit    the maximum number of players allowed
     * @param currentPlayers the current number of players
     */
    public MatchFullException(ClusterMatchId matchId, int playerLimit, int currentPlayers) {
        super(String.format("Match %s is full (limit: %d, current: %d)",
                matchId.value(), playerLimit, currentPlayers));
        this.matchId = matchId;
        this.playerLimit = playerLimit;
        this.currentPlayers = currentPlayers;
    }

    public ClusterMatchId getMatchId() {
        return matchId;
    }

    public int getPlayerLimit() {
        return playerLimit;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }
}
