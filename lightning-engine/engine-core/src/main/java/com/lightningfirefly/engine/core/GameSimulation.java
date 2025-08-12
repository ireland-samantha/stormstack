package com.lightningfirefly.engine.core;

import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.Player;
import com.lightningfirefly.engine.core.match.PlayerMatch;
import com.lightningfirefly.engine.core.snapshot.Snapshot;
import com.lightningfirefly.engine.ext.module.ModuleFactory;

import java.util.List;
import java.util.Optional;

public interface GameSimulation {
    long advanceTick();

    long getCurrentTick();

    /**
     * Start auto-advancing ticks at the specified interval.
     *
     * @param intervalMs the interval between ticks in milliseconds
     */
    void startAutoAdvance(long intervalMs);

    /**
     * Stop auto-advancing ticks.
     */
    void stopAutoAdvance();

    /**
     * Check if auto-advance is currently running.
     *
     * @return true if auto-advancing, false otherwise
     */
    boolean isAutoAdvancing();
    void installModule(String jarPath);
    void installModule(Class<? extends ModuleFactory> factoryClass);

    // Command operations
    void enqueueCommand(String commandName, CommandPayload payload);

    // Match operations
    Match createMatch(Match match);
    Optional<Match> getMatch(long matchId);
    List<Match> getAllMatches();
    void deleteMatch(long matchId);

    // Player operations
    void createPlayer(Player player);
    Optional<Player> getPlayer(long playerId);
    List<Player> getAllPlayers();
    void deletePlayer(long playerId);

    // PlayerMatch operations
    void joinMatch(PlayerMatch playerMatch);
    Optional<PlayerMatch> getPlayerMatch(long playerId, long matchId);
    List<PlayerMatch> getPlayerMatchesByMatch(long matchId);
    List<PlayerMatch> getPlayerMatchesByPlayer(long playerId);
    void leaveMatch(PlayerMatch playerMatch);
}
