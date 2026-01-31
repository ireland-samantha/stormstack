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

package ca.samanthaireland.lightning.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a match token that authorizes a player to connect to a specific match.
 *
 * <p>Match tokens are issued when a player is granted access to a match (e.g., after
 * matchmaking, accepting an invite, or joining a lobby). They contain:
 * <ul>
 *   <li>The match identifier the player can access</li>
 *   <li>The player's identity and display name</li>
 *   <li>Optional container scope for multi-container deployments</li>
 *   <li>Permission scopes for what actions the player can perform</li>
 * </ul>
 *
 * <p>Match tokens are short-lived (typically 1-8 hours) and can be revoked
 * if the player leaves the match or is kicked.
 *
 * @param id           unique token identifier
 * @param matchId      the match this token grants access to (external ID)
 * @param containerId  the container hosting the match (external ID, nullable)
 * @param playerId     the player ID (external ID from game system)
 * @param userId       the auth system user ID (nullable, for registered users)
 * @param playerName   display name for the player
 * @param scopes       permission scopes (e.g., "submit_commands", "view_snapshots")
 * @param createdAt    when the token was issued
 * @param expiresAt    when the token expires
 * @param revokedAt    when the token was revoked (null if active)
 * @param jwtToken     the JWT token string (only set when issued, not stored)
 */
public record MatchToken(
        MatchTokenId id,
        String matchId,
        String containerId,
        String playerId,
        UserId userId,
        String playerName,
        Set<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String jwtToken
) {
    /** JWT claim for match ID. */
    public static final String CLAIM_MATCH_ID = "match_id";

    /** JWT claim for container ID. */
    public static final String CLAIM_CONTAINER_ID = "container_id";

    /** JWT claim for player ID. */
    public static final String CLAIM_PLAYER_ID = "player_id";

    /** JWT claim for player name. */
    public static final String CLAIM_PLAYER_NAME = "player_name";

    /** JWT claim for scopes. */
    public static final String CLAIM_SCOPES = "scopes";

    /** JWT claim for match token ID. */
    public static final String CLAIM_TOKEN_ID = "match_token_id";

    /** Default scope: submit commands. */
    public static final String SCOPE_SUBMIT_COMMANDS = "submit_commands";

    /** Default scope: view snapshots. */
    public static final String SCOPE_VIEW_SNAPSHOTS = "view_snapshots";

    /** Default scope: receive errors. */
    public static final String SCOPE_RECEIVE_ERRORS = "receive_errors";

    public MatchToken {
        Objects.requireNonNull(id, "Match token ID cannot be null");
        Objects.requireNonNull(matchId, "Match ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(playerName, "Player name cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");
        Objects.requireNonNull(expiresAt, "Expires at cannot be null");

        if (matchId.isBlank()) {
            throw new IllegalArgumentException("Match ID cannot be blank");
        }
        if (playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be blank");
        }
        if (playerName.isBlank()) {
            throw new IllegalArgumentException("Player name cannot be blank");
        }

        // Defensive copy
        scopes = Set.copyOf(scopes);
    }

    /**
     * Creates a new match token.
     *
     * @param matchId     the match ID
     * @param containerId the container ID (nullable)
     * @param playerId    the player ID
     * @param userId      the auth user ID (nullable)
     * @param playerName  the player's display name
     * @param scopes      the permission scopes
     * @param expiresAt   when the token expires
     * @return a new MatchToken
     */
    public static MatchToken create(
            String matchId,
            String containerId,
            String playerId,
            UserId userId,
            String playerName,
            Set<String> scopes,
            Instant expiresAt) {
        return new MatchToken(
                MatchTokenId.generate(),
                matchId,
                containerId,
                playerId,
                userId,
                playerName,
                scopes,
                Instant.now(),
                expiresAt,
                null,
                null
        );
    }

    /**
     * Creates a match token with default scopes (submit commands, view snapshots, receive errors).
     *
     * @param matchId     the match ID
     * @param containerId the container ID (nullable)
     * @param playerId    the player ID
     * @param userId      the auth user ID (nullable)
     * @param playerName  the player's display name
     * @param expiresAt   when the token expires
     * @return a new MatchToken with default scopes
     */
    public static MatchToken createWithDefaultScopes(
            String matchId,
            String containerId,
            String playerId,
            UserId userId,
            String playerName,
            Instant expiresAt) {
        return create(
                matchId,
                containerId,
                playerId,
                userId,
                playerName,
                Set.of(SCOPE_SUBMIT_COMMANDS, SCOPE_VIEW_SNAPSHOTS, SCOPE_RECEIVE_ERRORS),
                expiresAt
        );
    }

    /**
     * Checks if this token is valid for the specified match.
     *
     * @param targetMatchId the match ID to check
     * @return true if this token grants access to the match
     */
    public boolean isValidForMatch(String targetMatchId) {
        return matchId.equals(targetMatchId) && isActive();
    }

    /**
     * Checks if this token is valid for the specified match and container.
     *
     * @param targetMatchId     the match ID to check
     * @param targetContainerId the container ID to check
     * @return true if this token grants access
     */
    public boolean isValidForMatchAndContainer(String targetMatchId, String targetContainerId) {
        if (!isValidForMatch(targetMatchId)) {
            return false;
        }
        if (containerId == null) {
            return true; // Token not container-scoped
        }
        return containerId.equals(targetContainerId);
    }

    /**
     * Checks if this token authorizes the specified player.
     *
     * @param targetPlayerId the player ID to check
     * @return true if this token is for the player
     */
    public boolean isForPlayer(String targetPlayerId) {
        return playerId.equals(targetPlayerId);
    }

    /**
     * Checks if the token is currently active (not expired or revoked).
     *
     * @return true if the token is active
     */
    public boolean isActive() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if the token has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the token has been revoked.
     *
     * @return true if the token has been revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Checks if the token has the specified scope.
     *
     * @param scope the scope to check
     * @return true if the token has this scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Checks if the player can submit commands.
     *
     * @return true if the token allows command submission
     */
    public boolean canSubmitCommands() {
        return hasScope(SCOPE_SUBMIT_COMMANDS);
    }

    /**
     * Checks if the player can view snapshots.
     *
     * @return true if the token allows snapshot viewing
     */
    public boolean canViewSnapshots() {
        return hasScope(SCOPE_VIEW_SNAPSHOTS);
    }

    /**
     * Creates a revoked version of this token.
     *
     * @return a new MatchToken with revokedAt set to now
     */
    public MatchToken revoke() {
        return new MatchToken(id, matchId, containerId, playerId, userId, playerName,
                scopes, createdAt, expiresAt, Instant.now(), null);
    }

    /**
     * Creates a version of this token with the JWT string attached.
     *
     * @param jwt the JWT token string
     * @return a new MatchToken with the JWT attached
     */
    public MatchToken withJwt(String jwt) {
        return new MatchToken(id, matchId, containerId, playerId, userId, playerName,
                scopes, createdAt, expiresAt, revokedAt, jwt);
    }

    /**
     * Creates a version of this token without the JWT string (for storage).
     *
     * @return a new MatchToken without the JWT
     */
    public MatchToken withoutJwt() {
        return new MatchToken(id, matchId, containerId, playerId, userId, playerName,
                scopes, createdAt, expiresAt, revokedAt, null);
    }
}
