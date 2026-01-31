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

package ca.samanthaireland.stormstack.thunder.controlplane.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Request to issue a match token for a player.
 *
 * @param matchId       the cluster-unique match ID
 * @param containerId   the container ID (for WebSocket routing)
 * @param playerId      the player ID
 * @param playerName    the player's display name
 * @param scopes        permission scopes to grant
 * @param validForHours how long the token should be valid
 */
public record IssueMatchTokenRequest(
        String matchId,
        String containerId,
        String playerId,
        String playerName,
        Set<String> scopes,
        int validForHours
) {
    public IssueMatchTokenRequest {
        Objects.requireNonNull(matchId, "matchId cannot be null");
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(playerName, "playerName cannot be null");
        if (validForHours < 1) {
            throw new IllegalArgumentException("validForHours must be at least 1");
        }
    }

    /**
     * Creates a request with default scopes and validity period.
     *
     * @param matchId     the match ID
     * @param containerId the container ID
     * @param playerId    the player ID
     * @param playerName  the player's display name
     * @return a new request with default settings
     */
    public static IssueMatchTokenRequest withDefaults(
            String matchId,
            String containerId,
            String playerId,
            String playerName
    ) {
        return new IssueMatchTokenRequest(
                matchId,
                containerId,
                playerId,
                playerName,
                Set.of("match.command.send", "match.snapshot.read"),
                8 // 8 hours default
        );
    }
}
