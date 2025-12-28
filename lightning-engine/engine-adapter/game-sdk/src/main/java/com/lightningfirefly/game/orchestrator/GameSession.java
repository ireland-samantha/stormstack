package com.lightningfirefly.game.orchestrator;

/**
 * Record representing an active game session.
 *
 * @param matchId     the match ID on the server
 * @param unsubscribe runnable to call to unsubscribe from snapshots
 */
public record GameSession(long matchId, Runnable unsubscribe) {
}
