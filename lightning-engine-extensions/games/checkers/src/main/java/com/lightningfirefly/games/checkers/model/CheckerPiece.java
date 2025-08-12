package com.lightningfirefly.games.checkers.model;

import com.lightningfirefly.games.common.board.BoardPosition;
import com.lightningfirefly.games.common.board.Piece;
import com.lightningfirefly.games.common.board.Player;

import java.util.Objects;

/**
 * Represents a checker piece.
 */
public class CheckerPiece implements Piece {

    private final long id;
    private final Player owner;
    private BoardPosition position;
    private boolean king;
    private boolean captured;

    public CheckerPiece(long id, Player owner, BoardPosition position) {
        this(id, owner, position, false);
    }

    public CheckerPiece(long id, Player owner, BoardPosition position, boolean king) {
        this.id = id;
        this.owner = Objects.requireNonNull(owner);
        this.position = Objects.requireNonNull(position);
        this.king = king;
        this.captured = false;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Player getOwner() {
        return owner;
    }

    @Override
    public BoardPosition getPosition() {
        return position;
    }

    public void setPosition(BoardPosition position) {
        this.position = position;
    }

    @Override
    public String getTypeName() {
        if (king) {
            return owner == Player.PLAYER_ONE ? "red-king" : "black-king";
        }
        return owner == Player.PLAYER_ONE ? "red-checker" : "black-checker";
    }

    @Override
    public boolean isCaptured() {
        return captured;
    }

    public void setCaptured(boolean captured) {
        this.captured = captured;
    }

    public boolean isKing() {
        return king;
    }

    public void promoteToKing() {
        this.king = true;
    }

    /**
     * Get the forward direction for this piece's owner.
     *
     * <p>Player 1 moves down (positive row), Player 2 moves up (negative row).
     */
    public int getForwardDirection() {
        return owner == Player.PLAYER_ONE ? 1 : -1;
    }

    /**
     * Check if this piece can move backward (kings only).
     */
    public boolean canMoveBackward() {
        return king;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CheckerPiece that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("%s%s@%s",
                owner == Player.PLAYER_ONE ? "R" : "B",
                king ? "K" : "",
                position);
    }
}
