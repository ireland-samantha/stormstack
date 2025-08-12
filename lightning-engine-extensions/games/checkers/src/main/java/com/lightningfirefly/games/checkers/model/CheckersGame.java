package com.lightningfirefly.games.checkers.model;

import com.lightningfirefly.games.common.board.*;
import com.lightningfirefly.engine.util.IdGeneratorV2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Implementation of a checkers game.
 *
 * <p>Standard 8x8 checkers with the following rules:
 * <ul>
 *   <li>Regular pieces move diagonally forward</li>
 *   <li>Kings can move diagonally in any direction</li>
 *   <li>Captures are mandatory</li>
 *   <li>Multi-jump captures are allowed</li>
 *   <li>Promotion to king when reaching the opposite end</li>
 * </ul>
 */
@Slf4j
public class CheckersGame implements BoardGame<CheckerPiece> {

    public static final int BOARD_SIZE = 8;
    public static final int PIECES_PER_PLAYER = 12;

    private final Map<BoardPosition, CheckerPiece> board = new HashMap<>();
    private final List<CheckerPiece> allPieces = new ArrayList<>();
    private Player currentPlayer = Player.PLAYER_ONE;
    private boolean gameOver = false;
    private Player winner = null;

    // For multi-jump tracking
    private BoardPosition mustContinueFrom = null;

    public CheckersGame() {
        reset();
    }

    @Override
    public int getWidth() {
        return BOARD_SIZE;
    }

    @Override
    public int getHeight() {
        return BOARD_SIZE;
    }

    @Override
    public Optional<CheckerPiece> getPieceAt(BoardPosition position) {
        return Optional.ofNullable(board.get(position));
    }

    @Override
    public List<CheckerPiece> getAllPieces() {
        return Collections.unmodifiableList(allPieces);
    }

    @Override
    public List<Move> getValidMoves(BoardPosition position) {
        CheckerPiece piece = board.get(position);
        if (piece == null || piece.getOwner() != currentPlayer || piece.isCaptured()) {
            return List.of();
        }

        // If must continue from a specific position after a capture
        if (mustContinueFrom != null && !mustContinueFrom.equals(position)) {
            return List.of();
        }

        List<Move> captures = getCaptureMoves(piece);

        // If there are captures available, only capture moves are valid (mandatory capture)
        if (!captures.isEmpty()) {
            return captures;
        }

        // Check if any piece can capture - if so, no simple moves allowed
        if (mustContinueFrom == null && hasAnyCapture(currentPlayer)) {
            return List.of();
        }

        // Simple moves
        return getSimpleMoves(piece);
    }

    @Override
    public boolean isValidMove(Move move) {
        List<Move> validMoves = getValidMoves(move.getFrom());
        return validMoves.stream().anyMatch(m ->
                m.getFrom().equals(move.getFrom()) && m.getTo().equals(move.getTo()));
    }

    @Override
    public MoveResult executeMove(Move move) {
        if (!isValidMove(move)) {
            throw new IllegalMoveException(move, "Invalid move");
        }

        CheckerPiece piece = board.get(move.getFrom());
        if (piece == null) {
            throw new IllegalMoveException(move, "No piece at source position");
        }

        // Remove from old position
        board.remove(move.getFrom());

        // Move to new position
        piece.setPosition(move.getTo());
        board.put(move.getTo(), piece);

        // Handle captures
        List<Long> capturedIds = new ArrayList<>();
        for (BoardPosition capturedPos : move.getCapturedPositions()) {
            CheckerPiece captured = board.remove(capturedPos);
            if (captured != null) {
                captured.setCaptured(true);
                capturedIds.add(captured.getId());
                log.debug("Captured piece {} at {}", captured.getId(), capturedPos);
            }
        }

        // Check for promotion
        boolean wasPromotion = false;
        if (!piece.isKing()) {
            int promotionRow = piece.getOwner() == Player.PLAYER_ONE ? BOARD_SIZE - 1 : 0;
            if (move.getTo().row() == promotionRow) {
                piece.promoteToKing();
                wasPromotion = true;
                log.debug("Piece {} promoted to king", piece.getId());
            }
        }

        // Check for multi-jump
        boolean canContinue = false;
        if (!capturedIds.isEmpty() && !wasPromotion) {
            List<Move> moreCaptured = getCaptureMoves(piece);
            if (!moreCaptured.isEmpty()) {
                mustContinueFrom = move.getTo();
                canContinue = true;
            } else {
                mustContinueFrom = null;
            }
        } else {
            mustContinueFrom = null;
        }

        // Switch turns if no more captures
        if (!canContinue) {
            currentPlayer = currentPlayer.opponent();

            // Check for game over
            checkGameOver();
        }

        if (gameOver) {
            return MoveResult.gameEnd(move, winner);
        }

        if (wasPromotion && !capturedIds.isEmpty()) {
            return new MoveResult(move, true, capturedIds, true, false, Optional.empty(), false);
        } else if (wasPromotion) {
            return MoveResult.promotion(move);
        } else if (!capturedIds.isEmpty()) {
            return MoveResult.capture(move, capturedIds, canContinue);
        }

        return MoveResult.success(move);
    }

    @Override
    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    @Override
    public boolean isGameOver() {
        return gameOver;
    }

    @Override
    public Optional<Player> getWinner() {
        return Optional.ofNullable(winner);
    }

    @Override
    public void reset() {
        board.clear();
        allPieces.clear();
        currentPlayer = Player.PLAYER_ONE;
        gameOver = false;
        winner = null;
        mustContinueFrom = null;

        // Place Player 1 pieces (top 3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    BoardPosition pos = new BoardPosition(row, col);
                    CheckerPiece piece = new CheckerPiece(
                            Math.abs(IdGeneratorV2.newId()),
                            Player.PLAYER_ONE,
                            pos
                    );
                    board.put(pos, piece);
                    allPieces.add(piece);
                }
            }
        }

        // Place Player 2 pieces (bottom 3 rows)
        for (int row = BOARD_SIZE - 3; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    BoardPosition pos = new BoardPosition(row, col);
                    CheckerPiece piece = new CheckerPiece(
                            Math.abs(IdGeneratorV2.newId()),
                            Player.PLAYER_TWO,
                            pos
                    );
                    board.put(pos, piece);
                    allPieces.add(piece);
                }
            }
        }

        log.info("Game reset with {} pieces", allPieces.size());
    }

    /**
     * Check if a player must continue capturing from a specific position.
     */
    public BoardPosition getMustContinueFrom() {
        return mustContinueFrom;
    }

    /**
     * Get pieces for a specific player.
     */
    public List<CheckerPiece> getPiecesForPlayer(Player player) {
        return allPieces.stream()
                .filter(p -> p.getOwner() == player && !p.isCaptured())
                .toList();
    }

    private List<Move> getSimpleMoves(CheckerPiece piece) {
        List<Move> moves = new ArrayList<>();
        BoardPosition pos = piece.getPosition();
        int forward = piece.getForwardDirection();

        // Forward diagonal moves
        addSimpleMoveIfValid(moves, pos, forward, -1);
        addSimpleMoveIfValid(moves, pos, forward, 1);

        // Backward diagonal moves (kings only)
        if (piece.canMoveBackward()) {
            addSimpleMoveIfValid(moves, pos, -forward, -1);
            addSimpleMoveIfValid(moves, pos, -forward, 1);
        }

        return moves;
    }

    private void addSimpleMoveIfValid(List<Move> moves, BoardPosition from, int deltaRow, int deltaCol) {
        BoardPosition to = from.offset(deltaRow, deltaCol);
        if (to.isWithinBounds(BOARD_SIZE, BOARD_SIZE) && !board.containsKey(to)) {
            moves.add(new Move(from, to));
        }
    }

    private List<Move> getCaptureMoves(CheckerPiece piece) {
        List<Move> captures = new ArrayList<>();
        BoardPosition pos = piece.getPosition();
        int forward = piece.getForwardDirection();

        // Forward captures
        addCaptureIfValid(captures, piece, pos, forward, -1);
        addCaptureIfValid(captures, piece, pos, forward, 1);

        // Backward captures (kings only)
        if (piece.canMoveBackward()) {
            addCaptureIfValid(captures, piece, pos, -forward, -1);
            addCaptureIfValid(captures, piece, pos, -forward, 1);
        }

        return captures;
    }

    private void addCaptureIfValid(List<Move> captures, CheckerPiece piece,
                                   BoardPosition from, int deltaRow, int deltaCol) {
        BoardPosition enemyPos = from.offset(deltaRow, deltaCol);
        BoardPosition landingPos = from.offset(deltaRow * 2, deltaCol * 2);

        if (!landingPos.isWithinBounds(BOARD_SIZE, BOARD_SIZE)) {
            return;
        }

        CheckerPiece enemy = board.get(enemyPos);
        if (enemy != null &&
                enemy.getOwner() != piece.getOwner() &&
                !enemy.isCaptured() &&
                !board.containsKey(landingPos)) {

            captures.add(new Move(from, landingPos, List.of(enemyPos), Move.MoveType.CAPTURE));
        }
    }

    private boolean hasAnyCapture(Player player) {
        return allPieces.stream()
                .filter(p -> p.getOwner() == player && !p.isCaptured())
                .anyMatch(p -> !getCaptureMoves(p).isEmpty());
    }

    private void checkGameOver() {
        // Count pieces
        long player1Pieces = allPieces.stream()
                .filter(p -> p.getOwner() == Player.PLAYER_ONE && !p.isCaptured())
                .count();
        long player2Pieces = allPieces.stream()
                .filter(p -> p.getOwner() == Player.PLAYER_TWO && !p.isCaptured())
                .count();

        // Check if a player has no pieces
        if (player1Pieces == 0) {
            gameOver = true;
            winner = Player.PLAYER_TWO;
            log.info("Game over: Player 2 wins (no pieces left for Player 1)");
            return;
        }
        if (player2Pieces == 0) {
            gameOver = true;
            winner = Player.PLAYER_ONE;
            log.info("Game over: Player 1 wins (no pieces left for Player 2)");
            return;
        }

        // Check if current player has no valid moves
        boolean hasValidMove = allPieces.stream()
                .filter(p -> p.getOwner() == currentPlayer && !p.isCaptured())
                .anyMatch(p -> !getValidMoves(p.getPosition()).isEmpty());

        if (!hasValidMove) {
            gameOver = true;
            winner = currentPlayer.opponent();
            log.info("Game over: {} wins (no valid moves for {})",
                    winner.getDisplayName(), currentPlayer.getDisplayName());
        }
    }
}
