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

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for match token management.
 *
 * <p>Match tokens authorize players to connect to specific matches and
 * perform match-specific operations like submitting commands and viewing snapshots.
 */
public interface MatchTokenService {

    /**
     * Issues a new match token for a player.
     *
     * <p>The returned token includes the JWT string which should be given to
     * the player. The JWT is only available at issuance time.
     *
     * @param request the token request containing all parameters
     * @return the issued token with JWT
     */
    MatchToken issueToken(IssueMatchTokenRequest request);

    /**
     * Validates a JWT match token and returns the token details.
     *
     * @param jwtToken the JWT token string
     * @return the validated token
     * @throws AuthException if the token is invalid, expired, or revoked
     */
    MatchToken validateToken(String jwtToken);

    /**
     * Validates a token for a specific match.
     *
     * @param jwtToken the JWT token string
     * @param matchId  the expected match ID
     * @return the validated token
     * @throws AuthException if the token is invalid or not valid for the match
     */
    MatchToken validateTokenForMatch(String jwtToken, String matchId);

    /**
     * Validates a token for a specific match and container.
     *
     * @param jwtToken    the JWT token string
     * @param matchId     the expected match ID
     * @param containerId the expected container ID
     * @return the validated token
     * @throws AuthException if the token is invalid or not valid for the match/container
     */
    MatchToken validateTokenForMatchAndContainer(String jwtToken, String matchId, String containerId);

    /**
     * Finds a match token by ID.
     *
     * @param tokenId the token ID
     * @return the token if found
     */
    Optional<MatchToken> findById(MatchTokenId tokenId);

    /**
     * Finds all tokens for a match.
     *
     * @param matchId the match ID
     * @return list of tokens
     */
    List<MatchToken> findByMatchId(String matchId);

    /**
     * Finds all active tokens for a match.
     *
     * @param matchId the match ID
     * @return list of active tokens
     */
    List<MatchToken> findActiveByMatchId(String matchId);

    /**
     * Finds the active token for a player in a match.
     *
     * @param matchId  the match ID
     * @param playerId the player ID
     * @return the active token if found
     */
    Optional<MatchToken> findActiveByMatchAndPlayer(String matchId, String playerId);

    /**
     * Revokes a match token.
     *
     * @param tokenId the token ID to revoke
     * @return the revoked token
     * @throws AuthException if the token doesn't exist
     */
    MatchToken revokeToken(MatchTokenId tokenId);

    /**
     * Revokes all tokens for a player in a match.
     *
     * @param matchId  the match ID
     * @param playerId the player ID
     * @return the number of tokens revoked
     */
    long revokeTokensForPlayer(String matchId, String playerId);

    /**
     * Revokes all tokens for a match.
     *
     * @param matchId the match ID
     * @return the number of tokens revoked
     */
    long revokeTokensForMatch(String matchId);

    /**
     * Deletes all tokens for a match.
     *
     * <p>Use with caution - typically tokens should be revoked rather
     * than deleted to maintain audit history.
     *
     * @param matchId the match ID
     * @return the number of tokens deleted
     */
    long deleteTokensForMatch(String matchId);

    /**
     * Returns the count of active tokens for a match.
     *
     * @param matchId the match ID
     * @return the count of active tokens
     */
    long countActiveByMatchId(String matchId);
}
