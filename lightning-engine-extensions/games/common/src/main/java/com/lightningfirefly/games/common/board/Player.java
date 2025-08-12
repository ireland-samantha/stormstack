package com.lightningfirefly.games.common.board;

/**
 * Represents a player in a board game.
 */
public enum Player {
    /**
     * The first player (typically red/white in checkers, white in chess).
     */
    PLAYER_ONE,

    /**
     * The second player (typically black).
     */
    PLAYER_TWO;

    /**
     * Get the opponent player.
     */
    public Player opponent() {
        return this == PLAYER_ONE ? PLAYER_TWO : PLAYER_ONE;
    }

    /**
     * Get a display name for the player.
     */
    public String getDisplayName() {
        return this == PLAYER_ONE ? "Player 1" : "Player 2";
    }
}
