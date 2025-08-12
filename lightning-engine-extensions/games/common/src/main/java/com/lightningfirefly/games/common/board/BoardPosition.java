package com.lightningfirefly.games.common.board;

/**
 * Represents a position on a game board.
 *
 * @param row the row index (0-based, from top)
 * @param col the column index (0-based, from left)
 */
public record BoardPosition(int row, int col) {

    /**
     * Check if this position is within the given board bounds.
     *
     * @param width board width
     * @param height board height
     * @return true if within bounds
     */
    public boolean isWithinBounds(int width, int height) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    /**
     * Get the position offset by the given delta.
     *
     * @param deltaRow row offset
     * @param deltaCol column offset
     * @return new position
     */
    public BoardPosition offset(int deltaRow, int deltaCol) {
        return new BoardPosition(row + deltaRow, col + deltaCol);
    }

    /**
     * Calculate the distance to another position.
     *
     * @param other the other position
     * @return the distance (max of row and col deltas)
     */
    public int distanceTo(BoardPosition other) {
        return Math.max(Math.abs(row - other.row), Math.abs(col - other.col));
    }

    /**
     * Check if this position is on a dark square (for checkers/chess boards).
     *
     * @return true if on a dark square
     */
    public boolean isDarkSquare() {
        return (row + col) % 2 == 1;
    }

    @Override
    public String toString() {
        return String.format("(%d,%d)", row, col);
    }
}
