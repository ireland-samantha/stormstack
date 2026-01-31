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

package ca.samanthaireland.lightning.auth.service.dto;

import ca.samanthaireland.lightning.auth.model.MatchToken;
import ca.samanthaireland.lightning.auth.model.UserId;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Request DTO for issuing a match token.
 *
 * <p>Encapsulates all parameters needed to create a match token that authorizes
 * a player to connect to a specific match.
 *
 * @param matchId     the match ID this token grants access to (required)
 * @param containerId the container hosting the match (optional, null for any container)
 * @param playerId    the player ID from the game system (required)
 * @param userId      the auth system user ID (optional, for registered users)
 * @param playerName  display name for the player (required)
 * @param scopes      permission scopes (optional, uses defaults if null)
 * @param validFor    how long the token should be valid (required)
 */
public record IssueMatchTokenRequest(
        String matchId,
        String containerId,
        String playerId,
        UserId userId,
        String playerName,
        Set<String> scopes,
        Duration validFor
) {
    public IssueMatchTokenRequest {
        Objects.requireNonNull(matchId, "Match ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(playerName, "Player name cannot be null");
        Objects.requireNonNull(validFor, "Valid duration cannot be null");

        if (matchId.isBlank()) {
            throw new IllegalArgumentException("Match ID cannot be blank");
        }
        if (playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be blank");
        }
        if (playerName.isBlank()) {
            throw new IllegalArgumentException("Player name cannot be blank");
        }
        if (validFor.isNegative() || validFor.isZero()) {
            throw new IllegalArgumentException("Valid duration must be positive");
        }

        // Defensive copy for scopes if provided
        if (scopes != null) {
            scopes = Set.copyOf(scopes);
        }
    }

    /**
     * Creates a request with default scopes.
     *
     * @param matchId     the match ID
     * @param containerId the container ID (nullable)
     * @param playerId    the player ID
     * @param userId      the user ID (nullable)
     * @param playerName  the player name
     * @param validFor    validity duration
     * @return request with default scopes
     */
    public static IssueMatchTokenRequest withDefaultScopes(
            String matchId,
            String containerId,
            String playerId,
            UserId userId,
            String playerName,
            Duration validFor) {
        return new IssueMatchTokenRequest(
                matchId,
                containerId,
                playerId,
                userId,
                playerName,
                Set.of(
                        MatchToken.SCOPE_SUBMIT_COMMANDS,
                        MatchToken.SCOPE_VIEW_SNAPSHOTS,
                        MatchToken.SCOPE_RECEIVE_ERRORS
                ),
                validFor
        );
    }

    /**
     * Check if this request uses default scopes.
     *
     * @return true if scopes is null (will use defaults)
     */
    public boolean usesDefaultScopes() {
        return scopes == null;
    }

    /**
     * Get the effective scopes, using defaults if not specified.
     *
     * @return the scopes to use
     */
    public Set<String> effectiveScopes() {
        if (scopes != null) {
            return scopes;
        }
        return Set.of(
                MatchToken.SCOPE_SUBMIT_COMMANDS,
                MatchToken.SCOPE_VIEW_SNAPSHOTS,
                MatchToken.SCOPE_RECEIVE_ERRORS
        );
    }
}
