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


package ca.samanthaireland.stormstack.thunder.engine.core.session;

import java.time.Instant;
import java.util.Set;

/**
 * Represents a player's session in a match.
 *
 * <p>A session tracks a player's connection state to a match, allowing
 * for reconnection after temporary disconnects while preserving entity
 * ownership and game state.
 *
 * <p>Session lifecycle:
 * <ul>
 *   <li>ACTIVE - Player is connected and participating</li>
 *   <li>DISCONNECTED - Temporary disconnect, can reconnect within timeout</li>
 *   <li>EXPIRED - Session timed out, cannot reconnect</li>
 *   <li>ABANDONED - Player explicitly left the match</li>
 * </ul>
 *
 * <p>Sessions may be authorized by a match token, which grants specific
 * permission scopes for what actions the player can perform (e.g., submit
 * commands, view snapshots, receive errors).
 *
 * @param id              the session ID
 * @param playerId        the player ID
 * @param matchId         the match ID
 * @param status          the session status
 * @param connectedAt     when the player connected
 * @param lastActivityAt  when the player last had activity
 * @param disconnectedAt  when the player disconnected (null if connected)
 * @param matchTokenId    the match token ID that authorized this session (nullable)
 * @param playerName      the player's display name
 * @param grantedScopes   the permission scopes granted by the match token
 */
public record PlayerSession(
    long id,
    long playerId,
    long matchId,
    SessionStatus status,
    Instant connectedAt,
    Instant lastActivityAt,
    Instant disconnectedAt,
    String matchTokenId,
    String playerName,
    Set<String> grantedScopes
) {

    /** Scope for submitting commands. */
    public static final String SCOPE_SUBMIT_COMMANDS = "submit_commands";

    /** Scope for viewing snapshots. */
    public static final String SCOPE_VIEW_SNAPSHOTS = "view_snapshots";

    /** Scope for receiving error messages. */
    public static final String SCOPE_RECEIVE_ERRORS = "receive_errors";

    /**
     * Default constructor that ensures grantedScopes is never null.
     */
    public PlayerSession {
        if (grantedScopes == null) {
            grantedScopes = Set.of();
        } else {
            grantedScopes = Set.copyOf(grantedScopes);
        }
    }

    /**
     * Check if this session can be reconnected.
     *
     * @return true if the session is in DISCONNECTED status
     */
    public boolean canReconnect() {
        return status == SessionStatus.DISCONNECTED;
    }

    /**
     * Check if this session is currently active.
     *
     * @return true if the session is in ACTIVE status
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    /**
     * Check if this session has been authorized by a match token.
     *
     * @return true if the session has a match token ID
     */
    public boolean hasMatchToken() {
        return matchTokenId != null && !matchTokenId.isBlank();
    }

    /**
     * Check if this session has the specified scope.
     *
     * @param scope the scope to check
     * @return true if the session has the scope
     */
    public boolean hasScope(String scope) {
        return grantedScopes.contains(scope);
    }

    /**
     * Check if this session can submit commands.
     *
     * @return true if the session has the submit_commands scope
     */
    public boolean canSubmitCommands() {
        return hasScope(SCOPE_SUBMIT_COMMANDS);
    }

    /**
     * Check if this session can view snapshots.
     *
     * @return true if the session has the view_snapshots scope
     */
    public boolean canViewSnapshots() {
        return hasScope(SCOPE_VIEW_SNAPSHOTS);
    }

    /**
     * Check if this session can receive errors.
     *
     * @return true if the session has the receive_errors scope
     */
    public boolean canReceiveErrors() {
        return hasScope(SCOPE_RECEIVE_ERRORS);
    }

    /**
     * Create a new session with updated status.
     *
     * @param newStatus the new status
     * @return a new PlayerSession with the updated status
     */
    public PlayerSession withStatus(SessionStatus newStatus) {
        Instant newDisconnectedAt = newStatus == SessionStatus.DISCONNECTED
            ? Instant.now()
            : disconnectedAt;

        return new PlayerSession(
            id,
            playerId,
            matchId,
            newStatus,
            connectedAt,
            Instant.now(),
            newDisconnectedAt,
            matchTokenId,
            playerName,
            grantedScopes
        );
    }

    /**
     * Create a new session with updated activity timestamp.
     *
     * @return a new PlayerSession with current time as lastActivityAt
     */
    public PlayerSession withActivity() {
        return new PlayerSession(
            id,
            playerId,
            matchId,
            status,
            connectedAt,
            Instant.now(),
            disconnectedAt,
            matchTokenId,
            playerName,
            grantedScopes
        );
    }

    /**
     * Create a new session bound to a match token.
     *
     * @param tokenId  the match token ID
     * @param name     the player's display name
     * @param scopes   the granted permission scopes
     * @return a new PlayerSession with match token binding
     */
    public PlayerSession withMatchToken(String tokenId, String name, Set<String> scopes) {
        return new PlayerSession(
            id,
            playerId,
            matchId,
            status,
            connectedAt,
            lastActivityAt,
            disconnectedAt,
            tokenId,
            name,
            scopes
        );
    }

    /**
     * Create a new active session for a player joining a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return a new active PlayerSession
     */
    public static PlayerSession create(long playerId, long matchId) {
        Instant now = Instant.now();
        return new PlayerSession(
            0,
            playerId,
            matchId,
            SessionStatus.ACTIVE,
            now,
            now,
            null,
            null,
            null,
            Set.of()
        );
    }

    /**
     * Create a new active session for a player joining a match with a match token.
     *
     * @param playerId    the player ID
     * @param matchId     the match ID
     * @param tokenId     the match token ID
     * @param playerName  the player's display name
     * @param scopes      the granted permission scopes
     * @return a new active PlayerSession with match token binding
     */
    public static PlayerSession createWithToken(
            long playerId,
            long matchId,
            String tokenId,
            String playerName,
            Set<String> scopes) {
        Instant now = Instant.now();
        return new PlayerSession(
            0,
            playerId,
            matchId,
            SessionStatus.ACTIVE,
            now,
            now,
            null,
            tokenId,
            playerName,
            scopes
        );
    }
}
