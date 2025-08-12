package com.lightningfirefly.engine.internal.core.match;

import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.core.match.Player;
import com.lightningfirefly.engine.core.match.PlayerRepository;
import com.lightningfirefly.engine.core.match.PlayerService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of {@link PlayerService}.
 *
 * <p>This implementation provides business logic for player operations,
 * delegating persistence to {@link PlayerRepository}.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Player must not be null when creating</li>
 *   <li>Player must exist before deletion</li>
 * </ul>
 *
 * <p>SOLID compliance:
 * <ul>
 *   <li>SRP: Business logic only, persistence delegated to repository</li>
 *   <li>DIP: Depends on PlayerRepository abstraction</li>
 * </ul>
 */
@Slf4j
public class InMemoryPlayerService implements PlayerService {

    private final PlayerRepository playerRepository;

    public InMemoryPlayerService(PlayerRepository playerRepository) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
    }

    @Override
    public Player createPlayer(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        log.info("Creating player with id: {}", player.id());
        return playerRepository.save(player);
    }

    @Override
    public void deletePlayer(long playerId) {
        log.info("Deleting player: {}", playerId);
        if (!playerRepository.existsById(playerId)) {
            throw new EntityNotFoundException(String.format("Player %d not found.", playerId));
        }
        playerRepository.deleteById(playerId);
    }

    @Override
    public Optional<Player> getPlayer(long playerId) {
        return playerRepository.findById(playerId);
    }

    @Override
    public Player getPlayerOrThrow(long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Player %d not found.", playerId)));
    }

    @Override
    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    @Override
    public boolean playerExists(long playerId) {
        return playerRepository.existsById(playerId);
    }
}
