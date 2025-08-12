package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.core.match.PlayerMatchRepository;
import com.lightningfirefly.engine.core.match.PlayerMatchService;
import com.lightningfirefly.engine.core.match.PlayerService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of {@link PlayerMatchService}.
 *
 * <p>This implementation provides business logic for player-match operations,
 * delegating persistence to {@link PlayerMatchRepository}.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Player and match must exist before joining</li>
 *   <li>Player cannot join the same match twice</li>
 *   <li>Association must exist before leaving</li>
 * </ul>
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Business logic only, persistence delegated to repository</li>
 *   <li>DIP: Depends on repository and service abstractions</li>
 * </ul>
 */
@Slf4j
public class InMemoryPlayerMatchService implements PlayerMatchService {

    private final PlayerMatchRepository playerMatchRepository;
    private final PlayerService playerService;
    private final MatchService matchService;

    public InMemoryPlayerMatchService(
            PlayerMatchRepository playerMatchRepository,
            PlayerService playerService,
            MatchService matchService) {
        this.playerMatchRepository = Objects.requireNonNull(playerMatchRepository,
                "playerMatchRepository must not be null");
        this.playerService = Objects.requireNonNull(playerService,
                "playerService must not be null");
        this.matchService = Objects.requireNonNull(matchService,
                "matchService must not be null");
    }

    @Override
    public PlayerMatch joinMatch(long playerId, long matchId) {
        log.info("Player {} joining match {}", playerId, matchId);

        // Validate player exists
        if (!playerService.playerExists(playerId)) {
            throw new EntityNotFoundException(String.format("Player %d not found.", playerId));
        }

        // Validate match exists
        if (!matchService.matchExists(matchId)) {
            throw new EntityNotFoundException(String.format("Match %d not found.", matchId));
        }

        // Check if player is already in match
        if (playerMatchRepository.existsByPlayerAndMatch(playerId, matchId)) {
            throw new IllegalStateException(
                    String.format("Player %d is already in match %d.", playerId, matchId));
        }

        PlayerMatch playerMatch = new PlayerMatch(playerId, matchId);
        return playerMatchRepository.save(playerMatch);
    }

    @Override
    public void leaveMatch(long playerId, long matchId) {
        log.info("Player {} leaving match {}", playerId, matchId);

        if (!playerMatchRepository.existsByPlayerAndMatch(playerId, matchId)) {
            throw new EntityNotFoundException(
                    String.format("Player %d is not in match %d.", playerId, matchId));
        }

        playerMatchRepository.deleteByPlayerAndMatch(playerId, matchId);
    }

    @Override
    public Optional<PlayerMatch> getPlayerMatch(long playerId, long matchId) {
        return playerMatchRepository.findByPlayerAndMatch(playerId, matchId);
    }

    @Override
    public PlayerMatch getPlayerMatchOrThrow(long playerId, long matchId) {
        return playerMatchRepository.findByPlayerAndMatch(playerId, matchId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Player %d is not in match %d.", playerId, matchId)));
    }

    @Override
    public List<PlayerMatch> getPlayersInMatch(long matchId) {
        return playerMatchRepository.findByMatchId(matchId);
    }

    @Override
    public List<PlayerMatch> getMatchesForPlayer(long playerId) {
        return playerMatchRepository.findByPlayerId(playerId);
    }

    @Override
    public boolean isPlayerInMatch(long playerId, long matchId) {
        return playerMatchRepository.existsByPlayerAndMatch(playerId, matchId);
    }
}
