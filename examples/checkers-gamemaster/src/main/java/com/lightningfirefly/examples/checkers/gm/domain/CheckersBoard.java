package com.lightningfirefly.examples.checkers.gm.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Domain object representing the checkers board state.
 *
 * <p>Standard 8x8 checkers board where pieces can only occupy dark squares.
 * The board coordinates are (0,0) at top-left to (7,7) at bottom-right.
 */
@Slf4j
@Getter
public class CheckersBoard {

    public static final int BOARD_SIZE = 8;

    private final Map<Long, CheckerPiece> piecesById = new HashMap<>();
    private final CheckerPiece[][] board = new CheckerPiece[BOARD_SIZE][BOARD_SIZE];
    private int currentPlayer = CheckerPiece.PLAYER_RED; // Red moves first

    /**
     * Add a piece to the board.
     */
    public void addPiece(CheckerPiece piece) {
        piecesById.put(piece.getEntityId(), piece);
        board[piece.getBoardY()][piece.getBoardX()] = piece;
        log.debug("Added piece {} at ({},{})", piece.getEntityId(), piece.getBoardX(), piece.getBoardY());
    }

    /**
     * Get the piece at a board position.
     */
    public CheckerPiece getPieceAt(int x, int y) {
        if (!isValidPosition(x, y)) {
            return null;
        }
        return board[y][x];
    }

    /**
     * Get a piece by entity ID.
     */
    public CheckerPiece getPieceById(long entityId) {
        return piecesById.get(entityId);
    }

    /**
     * Get all pieces for a player.
     */
    public List<CheckerPiece> getPiecesForPlayer(int player) {
        return piecesById.values().stream()
                .filter(p -> !p.isCaptured() && p.getPlayer() == player)
                .toList();
    }

    /**
     * Check if a position is valid on the board.
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    /**
     * Check if a position is a dark square (where pieces can be).
     */
    public boolean isDarkSquare(int x, int y) {
        return (x + y) % 2 == 1;
    }

    /**
     * Try to move a piece. Returns true if the move was valid.
     */
    public MoveResult tryMove(long pieceId, int toX, int toY) {
        CheckerPiece piece = piecesById.get(pieceId);
        if (piece == null || piece.isCaptured()) {
            return MoveResult.invalid("Piece not found or already captured");
        }

        if (piece.getPlayer() != currentPlayer) {
            return MoveResult.invalid("Not this player's turn");
        }

        if (!isValidPosition(toX, toY) || !isDarkSquare(toX, toY)) {
            return MoveResult.invalid("Invalid destination");
        }

        if (getPieceAt(toX, toY) != null) {
            return MoveResult.invalid("Destination occupied");
        }

        int fromX = piece.getBoardX();
        int fromY = piece.getBoardY();
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;

        // Check direction is valid for this piece
        if (!piece.canMoveDirection(deltaY)) {
            return MoveResult.invalid("Cannot move in that direction");
        }

        // Simple move (1 square diagonal)
        if (Math.abs(deltaX) == 1 && Math.abs(deltaY) == 1) {
            return executeSimpleMove(piece, toX, toY);
        }

        // Jump move (2 squares diagonal, capturing opponent)
        if (Math.abs(deltaX) == 2 && Math.abs(deltaY) == 2) {
            int midX = fromX + deltaX / 2;
            int midY = fromY + deltaY / 2;
            CheckerPiece captured = getPieceAt(midX, midY);

            if (captured == null || captured.getPlayer() == piece.getPlayer()) {
                return MoveResult.invalid("No opponent piece to jump");
            }

            return executeJumpMove(piece, toX, toY, captured);
        }

        return MoveResult.invalid("Invalid move distance");
    }

    private MoveResult executeSimpleMove(CheckerPiece piece, int toX, int toY) {
        int fromX = piece.getBoardX();
        int fromY = piece.getBoardY();

        // Update board state
        board[fromY][fromX] = null;
        board[toY][toX] = piece;
        piece.setBoardX(toX);
        piece.setBoardY(toY);

        boolean promoted = false;
        if (piece.shouldPromote()) {
            piece.setKing(true);
            promoted = true;
        }

        // Switch turns
        currentPlayer = (currentPlayer == CheckerPiece.PLAYER_RED)
                ? CheckerPiece.PLAYER_BLACK
                : CheckerPiece.PLAYER_RED;

        log.info("Piece {} moved from ({},{}) to ({},{})",
                piece.getEntityId(), fromX, fromY, toX, toY);

        return MoveResult.success(piece, null, promoted);
    }

    private MoveResult executeJumpMove(CheckerPiece piece, int toX, int toY, CheckerPiece captured) {
        int fromX = piece.getBoardX();
        int fromY = piece.getBoardY();

        // Update board state
        board[fromY][fromX] = null;
        board[toY][toX] = piece;
        board[captured.getBoardY()][captured.getBoardX()] = null;

        piece.setBoardX(toX);
        piece.setBoardY(toY);
        captured.setCaptured(true);

        boolean promoted = false;
        if (piece.shouldPromote()) {
            piece.setKing(true);
            promoted = true;
        }

        // Switch turns (in real checkers, you might get another jump)
        currentPlayer = (currentPlayer == CheckerPiece.PLAYER_RED)
                ? CheckerPiece.PLAYER_BLACK
                : CheckerPiece.PLAYER_RED;

        log.info("Piece {} jumped from ({},{}) to ({},{}), capturing {}",
                piece.getEntityId(), fromX, fromY, toX, toY, captured.getEntityId());

        return MoveResult.success(piece, captured, promoted);
    }

    /**
     * Check if the game is over (one player has no pieces).
     */
    public Optional<Integer> getWinner() {
        boolean redHasPieces = piecesById.values().stream()
                .anyMatch(p -> !p.isCaptured() && p.getPlayer() == CheckerPiece.PLAYER_RED);
        boolean blackHasPieces = piecesById.values().stream()
                .anyMatch(p -> !p.isCaptured() && p.getPlayer() == CheckerPiece.PLAYER_BLACK);

        if (!redHasPieces) {
            return Optional.of(CheckerPiece.PLAYER_BLACK);
        }
        if (!blackHasPieces) {
            return Optional.of(CheckerPiece.PLAYER_RED);
        }
        return Optional.empty();
    }

    /**
     * Result of a move attempt.
     */
    public record MoveResult(
            boolean valid,
            String errorMessage,
            CheckerPiece movedPiece,
            CheckerPiece capturedPiece,
            boolean promoted
    ) {
        public static MoveResult invalid(String message) {
            return new MoveResult(false, message, null, null, false);
        }

        public static MoveResult success(CheckerPiece moved, CheckerPiece captured, boolean promoted) {
            return new MoveResult(true, null, moved, captured, promoted);
        }
    }
}
