package com.lightningfirefly.games.common.render;

import com.lightningfirefly.games.common.board.BoardGame;
import com.lightningfirefly.games.common.board.BoardPosition;
import com.lightningfirefly.games.common.board.Move;
import com.lightningfirefly.games.common.board.Piece;

import java.util.List;

/**
 * Interface for rendering board games.
 *
 * <p>Implementations handle the visual representation of the game board,
 * pieces, and user interactions.
 *
 * @param <P> the piece type
 */
public interface BoardGameRenderer<P extends Piece> {

    /**
     * Initialize the renderer with a game.
     *
     * @param game the game to render
     */
    void initialize(BoardGame<P> game);

    /**
     * Render the current game state.
     *
     * @param nvg the NanoVG context (or 0 if not using NanoVG)
     */
    void render(long nvg);

    /**
     * Highlight the valid moves for a selected piece.
     *
     * @param position the selected piece position
     * @param validMoves the valid moves
     */
    void highlightValidMoves(BoardPosition position, List<Move> validMoves);

    /**
     * Clear move highlights.
     */
    void clearHighlights();

    /**
     * Set the selected square.
     *
     * @param position the selected position, or null to clear selection
     */
    void setSelectedSquare(BoardPosition position);

    /**
     * Convert screen coordinates to board position.
     *
     * @param screenX the screen X coordinate
     * @param screenY the screen Y coordinate
     * @return the board position, or null if outside the board
     */
    BoardPosition screenToBoard(int screenX, int screenY);

    /**
     * Convert board position to screen coordinates.
     *
     * @param position the board position
     * @return array of [x, y] screen coordinates
     */
    int[] boardToScreen(BoardPosition position);

    /**
     * Get the square size in pixels.
     */
    int getSquareSize();

    /**
     * Set the board origin (top-left corner).
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    void setOrigin(int x, int y);

    /**
     * Set the square size.
     *
     * @param size the size in pixels
     */
    void setSquareSize(int size);

    /**
     * Dispose of renderer resources.
     */
    void dispose();
}
