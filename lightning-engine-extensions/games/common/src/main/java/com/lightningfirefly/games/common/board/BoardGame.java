package com.lightningfirefly.games.common.board;

import java.util.List;
import java.util.Optional;

/**
 * Interface for board games with grid-based layouts.
 *
 * <p>This abstraction supports games like checkers, chess, and other
 * tile-based board games.
 *
 * @param <P> the piece type
 */
public interface BoardGame<P extends Piece> {

    /**
     * Get the board width in squares.
     */
    int getWidth();

    /**
     * Get the board height in squares.
     */
    int getHeight();

    /**
     * Get the piece at the given position.
     *
     * @param position the board position
     * @return the piece if present
     */
    Optional<P> getPieceAt(BoardPosition position);

    /**
     * Get all pieces on the board.
     */
    List<P> getAllPieces();

    /**
     * Get all valid moves for a piece at the given position.
     *
     * @param position the piece position
     * @return list of valid target positions
     */
    List<Move> getValidMoves(BoardPosition position);

    /**
     * Check if a move is valid.
     *
     * @param move the move to validate
     * @return true if the move is valid
     */
    boolean isValidMove(Move move);

    /**
     * Execute a move.
     *
     * @param move the move to execute
     * @return the result of the move
     * @throws IllegalMoveException if the move is not valid
     */
    MoveResult executeMove(Move move);

    /**
     * Get the current player's turn.
     */
    Player getCurrentPlayer();

    /**
     * Check if the game is over.
     */
    boolean isGameOver();

    /**
     * Get the winner if the game is over.
     *
     * @return the winner, or empty if no winner yet or draw
     */
    Optional<Player> getWinner();

    /**
     * Reset the game to initial state.
     */
    void reset();
}
