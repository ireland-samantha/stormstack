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

package ca.samanthaireland.lightning.auth.repository;

import ca.samanthaireland.lightning.auth.model.MatchToken;
import ca.samanthaireland.lightning.auth.model.MatchTokenId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Match Token persistence.
 *
 * <p>Implementations of this interface handle the persistence of MatchToken entities
 * to various storage backends (MongoDB, in-memory, etc.).
 */
public interface MatchTokenRepository {

    /**
     * Finds a match token by its unique ID.
     *
     * @param id the token ID
     * @return the token if found
     */
    Optional<MatchToken> findById(MatchTokenId id);

    /**
     * Finds all match tokens for a specific match.
     *
     * @param matchId the match ID
     * @return list of tokens for the match
     */
    List<MatchToken> findByMatchId(String matchId);

    /**
     * Finds all match tokens for a specific player.
     *
     * @param playerId the player ID
     * @return list of tokens for the player
     */
    List<MatchToken> findByPlayerId(String playerId);

    /**
     * Finds an active token for a player in a specific match.
     *
     * @param matchId  the match ID
     * @param playerId the player ID
     * @return the active token if found
     */
    Optional<MatchToken> findActiveByMatchAndPlayer(String matchId, String playerId);

    /**
     * Finds all active tokens for a match.
     *
     * @param matchId the match ID
     * @return list of active tokens
     */
    List<MatchToken> findActiveByMatchId(String matchId);

    /**
     * Returns all match tokens.
     *
     * @return list of all tokens
     */
    List<MatchToken> findAll();

    /**
     * Saves a match token (insert or update).
     *
     * <p>If the token ID already exists, updates the existing token.
     * Otherwise, inserts a new token.
     *
     * @param token the token to save
     * @return the saved token
     */
    MatchToken save(MatchToken token);

    /**
     * Deletes a match token by its ID.
     *
     * @param id the token ID
     * @return true if the token was deleted
     */
    boolean deleteById(MatchTokenId id);

    /**
     * Deletes all tokens for a match.
     *
     * @param matchId the match ID
     * @return the number of tokens deleted
     */
    long deleteByMatchId(String matchId);

    /**
     * Returns the total count of match tokens.
     *
     * @return the token count
     */
    long count();

    /**
     * Returns the count of active tokens for a match.
     *
     * @param matchId the match ID
     * @return the count of active tokens
     */
    long countActiveByMatchId(String matchId);
}
