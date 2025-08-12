package com.lightningfirefly.games.common.board;

import java.util.List;
import java.util.Objects;

/**
 * Represents a move in a board game.
 */
public class Move {

    private final BoardPosition from;
    private final BoardPosition to;
    private final List<BoardPosition> capturedPositions;
    private final MoveType type;

    /**
     * Create a simple move.
     */
    public Move(BoardPosition from, BoardPosition to) {
        this(from, to, List.of(), MoveType.SIMPLE);
    }

    /**
     * Create a capture move.
     */
    public Move(BoardPosition from, BoardPosition to, List<BoardPosition> capturedPositions) {
        this(from, to, capturedPositions, MoveType.CAPTURE);
    }

    /**
     * Create a move with explicit type.
     */
    public Move(BoardPosition from, BoardPosition to, List<BoardPosition> capturedPositions, MoveType type) {
        this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to);
        this.capturedPositions = capturedPositions != null ? List.copyOf(capturedPositions) : List.of();
        this.type = type;
    }

    public BoardPosition getFrom() {
        return from;
    }

    public BoardPosition getTo() {
        return to;
    }

    public List<BoardPosition> getCapturedPositions() {
        return capturedPositions;
    }

    public MoveType getType() {
        return type;
    }

    public boolean isCapture() {
        return !capturedPositions.isEmpty();
    }

    /**
     * Type of move.
     */
    public enum MoveType {
        SIMPLE,
        CAPTURE,
        PROMOTION,
        CAPTURE_AND_PROMOTION
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Move move)) return false;
        return from.equals(move.from) && to.equals(move.to) &&
                capturedPositions.equals(move.capturedPositions) && type == move.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, capturedPositions, type);
    }

    @Override
    public String toString() {
        return from + " -> " + to + (isCapture() ? " (captures " + capturedPositions + ")" : "");
    }
}
