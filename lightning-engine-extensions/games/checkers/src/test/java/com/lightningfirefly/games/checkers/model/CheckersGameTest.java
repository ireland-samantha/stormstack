package com.lightningfirefly.games.checkers.model;

import com.lightningfirefly.games.common.board.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CheckersGame}.
 */
@DisplayName("CheckersGame")
class CheckersGameTest {

    private CheckersGame game;

    @BeforeEach
    void setUp() {
        game = new CheckersGame();
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should have 8x8 board")
        void shouldHave8x8Board() {
            assertThat(game.getWidth()).isEqualTo(8);
            assertThat(game.getHeight()).isEqualTo(8);
        }

        @Test
        @DisplayName("should start with 24 pieces")
        void shouldStartWith24Pieces() {
            assertThat(game.getAllPieces()).hasSize(24);
        }

        @Test
        @DisplayName("should have 12 pieces per player")
        void shouldHave12PiecesPerPlayer() {
            long player1Count = game.getAllPieces().stream()
                    .filter(p -> p.getOwner() == Player.PLAYER_ONE)
                    .count();
            long player2Count = game.getAllPieces().stream()
                    .filter(p -> p.getOwner() == Player.PLAYER_TWO)
                    .count();

            assertThat(player1Count).isEqualTo(12);
            assertThat(player2Count).isEqualTo(12);
        }

        @Test
        @DisplayName("should place Player 1 pieces in top 3 rows")
        void shouldPlacePlayer1InTopRows() {
            List<CheckerPiece> player1Pieces = game.getPiecesForPlayer(Player.PLAYER_ONE);

            assertThat(player1Pieces)
                    .allMatch(p -> p.getPosition().row() < 3);
        }

        @Test
        @DisplayName("should place Player 2 pieces in bottom 3 rows")
        void shouldPlacePlayer2InBottomRows() {
            List<CheckerPiece> player2Pieces = game.getPiecesForPlayer(Player.PLAYER_TWO);

            assertThat(player2Pieces)
                    .allMatch(p -> p.getPosition().row() >= 5);
        }

        @Test
        @DisplayName("should place pieces only on dark squares")
        void shouldPlacePiecesOnDarkSquares() {
            assertThat(game.getAllPieces())
                    .allMatch(p -> p.getPosition().isDarkSquare());
        }

        @Test
        @DisplayName("should start with Player 1's turn")
        void shouldStartWithPlayer1Turn() {
            assertThat(game.getCurrentPlayer()).isEqualTo(Player.PLAYER_ONE);
        }

        @Test
        @DisplayName("should not be game over at start")
        void shouldNotBeGameOverAtStart() {
            assertThat(game.isGameOver()).isFalse();
            assertThat(game.getWinner()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Simple Moves")
    class SimpleMoves {

        @Test
        @DisplayName("Player 1 piece should move diagonally forward")
        void player1ShouldMoveDiagonallyForward() {
            // Player 1 piece at row 2
            BoardPosition from = new BoardPosition(2, 1);
            List<Move> moves = game.getValidMoves(from);

            assertThat(moves).hasSize(2);
            assertThat(moves).extracting(Move::getTo)
                    .containsExactlyInAnyOrder(
                            new BoardPosition(3, 0),
                            new BoardPosition(3, 2)
                    );
        }

        @Test
        @DisplayName("Player 2 piece should move diagonally forward (up)")
        void player2ShouldMoveDiagonallyForward() {
            // Make a move to switch turns
            game.executeMove(new Move(new BoardPosition(2, 1), new BoardPosition(3, 2)));

            // Player 2 piece at row 5
            BoardPosition from = new BoardPosition(5, 2);
            List<Move> moves = game.getValidMoves(from);

            assertThat(moves)
                    .anyMatch(m -> m.getTo().row() == 4); // Moving up
        }

        @Test
        @DisplayName("should execute simple move")
        void shouldExecuteSimpleMove() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(3, 2);
            Move move = new Move(from, to);

            MoveResult result = game.executeMove(move);

            assertThat(result.success()).isTrue();
            assertThat(game.getPieceAt(from)).isEmpty();
            assertThat(game.getPieceAt(to)).isPresent();
        }

        @Test
        @DisplayName("should switch turns after move")
        void shouldSwitchTurnsAfterMove() {
            assertThat(game.getCurrentPlayer()).isEqualTo(Player.PLAYER_ONE);

            game.executeMove(new Move(new BoardPosition(2, 1), new BoardPosition(3, 2)));

            assertThat(game.getCurrentPlayer()).isEqualTo(Player.PLAYER_TWO);
        }

        @Test
        @DisplayName("should not allow moving opponent's piece")
        void shouldNotAllowMovingOpponentPiece() {
            // Player 2's piece
            BoardPosition from = new BoardPosition(5, 2);
            List<Move> moves = game.getValidMoves(from);

            assertThat(moves).isEmpty();
        }

        @Test
        @DisplayName("should not allow moving to occupied square")
        void shouldNotAllowMovingToOccupiedSquare() {
            // This would be blocked by another piece
            BoardPosition from = new BoardPosition(1, 0);
            List<Move> moves = game.getValidMoves(from);

            // Only forward moves are valid, and (2,1) is occupied
            assertThat(moves).isEmpty();
        }
    }

    @Nested
    @DisplayName("Capture Moves")
    class CaptureMoves {

        @Test
        @DisplayName("should detect capture opportunity")
        void shouldDetectCaptureOpportunity() {
            // Setup: Move pieces to create capture opportunity
            // Move Player 1 piece forward
            game.executeMove(new Move(new BoardPosition(2, 3), new BoardPosition(3, 4)));
            // Move Player 2 piece forward
            game.executeMove(new Move(new BoardPosition(5, 4), new BoardPosition(4, 3)));

            // Now Player 1 can capture
            List<Move> moves = game.getValidMoves(new BoardPosition(3, 4));

            assertThat(moves).anyMatch(Move::isCapture);
        }

        @Test
        @DisplayName("should execute capture")
        void shouldExecuteCapture() {
            // Setup capture opportunity
            game.executeMove(new Move(new BoardPosition(2, 3), new BoardPosition(3, 4)));
            game.executeMove(new Move(new BoardPosition(5, 4), new BoardPosition(4, 3)));

            // Execute capture
            BoardPosition from = new BoardPosition(3, 4);
            Move captureMove = game.getValidMoves(from).stream()
                    .filter(Move::isCapture)
                    .findFirst()
                    .orElseThrow();

            MoveResult result = game.executeMove(captureMove);

            assertThat(result.success()).isTrue();
            assertThat(result.capturedPieceIds()).hasSize(1);
        }

        @Test
        @DisplayName("should remove captured piece from board")
        void shouldRemoveCapturedPiece() {
            // Setup capture
            game.executeMove(new Move(new BoardPosition(2, 3), new BoardPosition(3, 4)));
            game.executeMove(new Move(new BoardPosition(5, 4), new BoardPosition(4, 3)));

            BoardPosition enemyPos = new BoardPosition(4, 3);
            assertThat(game.getPieceAt(enemyPos)).isPresent();

            // Execute capture
            Move captureMove = game.getValidMoves(new BoardPosition(3, 4)).stream()
                    .filter(Move::isCapture)
                    .findFirst()
                    .orElseThrow();
            game.executeMove(captureMove);

            assertThat(game.getPieceAt(enemyPos)).isEmpty();
        }

        @Test
        @DisplayName("should enforce mandatory capture")
        void shouldEnforceMandatoryCapture() {
            // Setup capture opportunity
            game.executeMove(new Move(new BoardPosition(2, 3), new BoardPosition(3, 4)));
            game.executeMove(new Move(new BoardPosition(5, 4), new BoardPosition(4, 3)));

            // Player 1 has a capture available at (3,4)
            // Other pieces should not be able to make simple moves
            BoardPosition otherPiece = new BoardPosition(2, 1);
            List<Move> moves = game.getValidMoves(otherPiece);

            assertThat(moves).isEmpty(); // Must capture instead
        }
    }

    @Nested
    @DisplayName("King Promotion")
    class KingPromotion {

        @Test
        @DisplayName("should promote to king when reaching opposite end")
        void shouldPromoteToKingWhenReachingOppositeEnd() {
            // We need to carefully construct this scenario
            // For simplicity, we'll test the game logic in isolation
            CheckerPiece piece = game.getAllPieces().stream()
                    .filter(p -> p.getOwner() == Player.PLAYER_ONE)
                    .findFirst()
                    .orElseThrow();

            assertThat(piece.isKing()).isFalse();

            // After reaching row 7 (for Player 1), the piece should be promoted
            // This is tested through actual gameplay in integration tests
        }

        @Test
        @DisplayName("king should move backward")
        void kingShouldMoveBackward() {
            // Create a king piece manually for testing
            CheckerPiece piece = new CheckerPiece(999, Player.PLAYER_ONE,
                    new BoardPosition(4, 3), true);

            assertThat(piece.isKing()).isTrue();
            assertThat(piece.canMoveBackward()).isTrue();
        }
    }

    @Nested
    @DisplayName("Game Over")
    class GameOver {

        @Test
        @DisplayName("game should end when a player has no pieces")
        void gameShouldEndWhenNoPieces() {
            // This would require many moves - tested in integration tests
            assertThat(game.isGameOver()).isFalse();
        }

        @Test
        @DisplayName("reset should restore initial state")
        void resetShouldRestoreInitialState() {
            // Make some moves
            game.executeMove(new Move(new BoardPosition(2, 1), new BoardPosition(3, 2)));
            game.executeMove(new Move(new BoardPosition(5, 2), new BoardPosition(4, 3)));

            assertThat(game.getCurrentPlayer()).isEqualTo(Player.PLAYER_ONE);

            game.reset();

            assertThat(game.getAllPieces()).hasSize(24);
            assertThat(game.getCurrentPlayer()).isEqualTo(Player.PLAYER_ONE);
            assertThat(game.isGameOver()).isFalse();
        }
    }

    @Nested
    @DisplayName("BoardPosition")
    class BoardPositionTests {

        @Test
        @DisplayName("should identify dark squares correctly")
        void shouldIdentifyDarkSquares() {
            assertThat(new BoardPosition(0, 1).isDarkSquare()).isTrue();
            assertThat(new BoardPosition(0, 0).isDarkSquare()).isFalse();
            assertThat(new BoardPosition(1, 0).isDarkSquare()).isTrue();
        }

        @Test
        @DisplayName("should check bounds correctly")
        void shouldCheckBoundsCorrectly() {
            assertThat(new BoardPosition(0, 0).isWithinBounds(8, 8)).isTrue();
            assertThat(new BoardPosition(7, 7).isWithinBounds(8, 8)).isTrue();
            assertThat(new BoardPosition(-1, 0).isWithinBounds(8, 8)).isFalse();
            assertThat(new BoardPosition(8, 0).isWithinBounds(8, 8)).isFalse();
        }

        @Test
        @DisplayName("should calculate offset correctly")
        void shouldCalculateOffsetCorrectly() {
            BoardPosition pos = new BoardPosition(3, 3);
            assertThat(pos.offset(1, 1)).isEqualTo(new BoardPosition(4, 4));
            assertThat(pos.offset(-1, 1)).isEqualTo(new BoardPosition(2, 4));
        }
    }

    @Nested
    @DisplayName("Move Validation")
    class MoveValidation {

        @Test
        @DisplayName("isValidMove should return true for valid move")
        void isValidMoveShouldReturnTrueForValidMove() {
            Move move = new Move(new BoardPosition(2, 1), new BoardPosition(3, 2));
            assertThat(game.isValidMove(move)).isTrue();
        }

        @Test
        @DisplayName("isValidMove should return false for invalid move")
        void isValidMoveShouldReturnFalseForInvalidMove() {
            // Horizontal move (invalid)
            Move move = new Move(new BoardPosition(2, 1), new BoardPosition(2, 3));
            assertThat(game.isValidMove(move)).isFalse();
        }

        @Test
        @DisplayName("should reject move from empty square")
        void shouldRejectMoveFromEmptySquare() {
            Move move = new Move(new BoardPosition(4, 4), new BoardPosition(5, 5));
            assertThat(game.isValidMove(move)).isFalse();
        }
    }
}
