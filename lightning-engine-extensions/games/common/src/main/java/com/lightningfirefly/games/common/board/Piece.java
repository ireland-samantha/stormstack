package com.lightningfirefly.games.common.board;

/**
 * Interface for a game piece on a board.
 */
public interface Piece {

    /**
     * Get the unique ID of this piece (entity ID in ECS).
     */
    long getId();

    /**
     * Get the owner of this piece.
     */
    Player getOwner();

    /**
     * Get the current position of this piece.
     */
    BoardPosition getPosition();

    /**
     * Get the piece type name (for rendering lookup).
     */
    String getTypeName();

    /**
     * Check if this piece is captured.
     */
    boolean isCaptured();
}
