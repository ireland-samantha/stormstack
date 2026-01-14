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


package ca.samanthaireland.engine.core.match;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for PlayerMatch business operations.
 *
 * <p>Provides business-level operations with validation, authorization,
 * and domain logic. Uses {@link PlayerMatchRepository} for persistence.
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Handles business logic only, delegates persistence to repository</li>
 *   <li>ISP: Separate from PlayerMatchRepository with distinct responsibilities</li>
 *   <li>DIP: Depends on PlayerMatchRepository abstraction</li>
 * </ul>
 */
public interface PlayerMatchService {

    /**
     * Join a player to a match.
     *
     * <p>Validates that both player and match exist before creating the association.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the created player-match association
     * @throws ca.samanthaireland.engine.core.exception.EntityNotFoundException if player or match doesn't exist
     * @throws IllegalStateException if player is already in the match
     */
    PlayerMatch joinMatch(long playerId, long matchId);

    /**
     * Join a player to a match without validating match existence.
     *
     * <p>Use this method when the caller has already verified the match exists
     * (e.g., in a container-scoped context where matches are validated separately).
     * Still validates player existence and prevents duplicate joins.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the created player-match association
     * @throws ca.samanthaireland.engine.core.exception.EntityNotFoundException if player doesn't exist
     * @throws IllegalStateException if player is already in the match
     */
    PlayerMatch joinMatchValidated(long playerId, long matchId);

    /**
     * Remove a player from a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @throws ca.samanthaireland.engine.core.exception.EntityNotFoundException if association doesn't exist
     */
    void leaveMatch(long playerId, long matchId);

    /**
     * Get a player-match association.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return an Optional containing the player-match if found
     */
    Optional<PlayerMatch> getPlayerMatch(long playerId, long matchId);

    /**
     * Get a player-match association, throwing if not found.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return the player-match association
     * @throws ca.samanthaireland.engine.core.exception.EntityNotFoundException if not found
     */
    PlayerMatch getPlayerMatchOrThrow(long playerId, long matchId);

    /**
     * Get all players in a match.
     *
     * @param matchId the match ID
     * @return list of player-match associations for the match
     */
    List<PlayerMatch> getPlayersInMatch(long matchId);

    /**
     * Get all matches for a player.
     *
     * @param playerId the player ID
     * @return list of player-match associations for the player
     */
    List<PlayerMatch> getMatchesForPlayer(long playerId);

    /**
     * Check if a player is in a match.
     *
     * @param playerId the player ID
     * @param matchId the match ID
     * @return true if the player is in the match
     */
    boolean isPlayerInMatch(long playerId, long matchId);
}
