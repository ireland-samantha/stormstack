package com.lightningfirefly.engine.internal.ext.gamemaster;

import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.match.MatchService;
import com.lightningfirefly.game.domain.GameMaster;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages per-match game master instances and invokes their tick methods.
 *
 * <p>Game masters are created lazily when first needed for a match and cached.
 * The cache is invalidated when matches or game masters change.
 */
@Slf4j
public class GameMasterTickService {

    private final GameMasterManager gameMasterManager;
    private final MatchService matchService;

    /**
     * Cache of game master instances per match.
     * Key: matchId, Value: List of game master instances for that match.
     */
    private final Map<Long, List<GameMaster>> matchGameMasters = new ConcurrentHashMap<>();

    public GameMasterTickService(GameMasterManager gameMasterManager, MatchService matchService) {
        this.gameMasterManager = gameMasterManager;
        this.matchService = matchService;
    }

    /**
     * Called each tick to execute all game masters for all active matches.
     *
     * @param tick the current tick number
     */
    public void onTick(long tick) {
        List<Match> matches = matchService.getAllMatches();
        int totalExecuted = 0;

        for (Match match : matches) {
            if (match.enabledGameMasters() == null || match.enabledGameMasters().isEmpty()) {
                continue;
            }

            List<GameMaster> gameMasters = getOrCreateGameMasters(match);
            for (GameMaster gm : gameMasters) {
                try {
                    gm.onTick();
                    totalExecuted++;
                } catch (Exception e) {
                    log.error("Error executing game master onTick for match {}: {}",
                            match.id(), e.getMessage(), e);
                }
            }
        }

        if (totalExecuted > 0) {
            log.trace("Tick {}: executed {} game masters", tick, totalExecuted);
        }
    }

    /**
     * Get or create game master instances for a match.
     *
     * @param match the match
     * @return list of game master instances
     */
    private List<GameMaster> getOrCreateGameMasters(Match match) {
        return matchGameMasters.computeIfAbsent(match.id(), matchId -> {
            List<GameMaster> instances = new ArrayList<>();
            for (String gameMasterName : match.enabledGameMasters()) {
                GameMaster gm = gameMasterManager.createForMatch(gameMasterName, matchId);
                if (gm != null) {
                    instances.add(gm);
                    log.debug("Created game master '{}' for match {}", gameMasterName, matchId);
                } else {
                    log.warn("Game master '{}' not found for match {}", gameMasterName, matchId);
                }
            }
            return instances;
        });
    }

    /**
     * Invalidate the cache for a specific match.
     *
     * <p>Call this when a match is deleted or its game masters change.
     *
     * @param matchId the match ID
     */
    public void invalidateMatch(long matchId) {
        matchGameMasters.remove(matchId);
        log.debug("Invalidated game master cache for match {}", matchId);
    }

    /**
     * Clear all cached game master instances.
     *
     * <p>Call this when game masters are reloaded.
     */
    public void invalidateAll() {
        matchGameMasters.clear();
        log.debug("Cleared all game master caches");
    }
}
