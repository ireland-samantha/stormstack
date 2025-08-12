package com.lightningfirefly.games.checkers.model;

import com.lightningfirefly.games.common.board.BoardPosition;
import com.lightningfirefly.games.common.board.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CheckerPiece}.
 */
@DisplayName("CheckerPiece")
class CheckerPieceTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create piece with correct properties")
        void shouldCreatePieceWithCorrectProperties() {
            BoardPosition pos = new BoardPosition(2, 3);
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE, pos);

            assertThat(piece.getId()).isEqualTo(1L);
            assertThat(piece.getOwner()).isEqualTo(Player.PLAYER_ONE);
            assertThat(piece.getPosition()).isEqualTo(pos);
            assertThat(piece.isKing()).isFalse();
            assertThat(piece.isCaptured()).isFalse();
        }

        @Test
        @DisplayName("should create king piece")
        void shouldCreateKingPiece() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_TWO,
                    new BoardPosition(5, 4), true);

            assertThat(piece.isKing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Movement Direction")
    class MovementDirection {

        @Test
        @DisplayName("Player 1 should move forward (down)")
        void player1ShouldMoveForward() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(2, 3));

            assertThat(piece.getForwardDirection()).isEqualTo(1);
        }

        @Test
        @DisplayName("Player 2 should move forward (up)")
        void player2ShouldMoveForward() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_TWO,
                    new BoardPosition(5, 3));

            assertThat(piece.getForwardDirection()).isEqualTo(-1);
        }

        @Test
        @DisplayName("regular piece cannot move backward")
        void regularPieceCannotMoveBackward() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(2, 3));

            assertThat(piece.canMoveBackward()).isFalse();
        }

        @Test
        @DisplayName("king can move backward")
        void kingCanMoveBackward() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(4, 3), true);

            assertThat(piece.canMoveBackward()).isTrue();
        }
    }

    @Nested
    @DisplayName("King Promotion")
    class KingPromotion {

        @Test
        @DisplayName("should promote to king")
        void shouldPromoteToKing() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(6, 3));

            assertThat(piece.isKing()).isFalse();

            piece.promoteToKing();

            assertThat(piece.isKing()).isTrue();
            assertThat(piece.canMoveBackward()).isTrue();
        }

        @Test
        @DisplayName("promoting already promoted piece should be idempotent")
        void promotingAlreadyPromotedShouldBeIdempotent() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(6, 3), true);

            piece.promoteToKing();

            assertThat(piece.isKing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Capture State")
    class CaptureState {

        @Test
        @DisplayName("should mark piece as captured")
        void shouldMarkPieceAsCaptured() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(3, 2));

            assertThat(piece.isCaptured()).isFalse();

            piece.setCaptured(true);

            assertThat(piece.isCaptured()).isTrue();
        }
    }

    @Nested
    @DisplayName("Position Updates")
    class PositionUpdates {

        @Test
        @DisplayName("should update position")
        void shouldUpdatePosition() {
            BoardPosition initial = new BoardPosition(2, 3);
            BoardPosition updated = new BoardPosition(3, 4);
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE, initial);

            piece.setPosition(updated);

            assertThat(piece.getPosition()).isEqualTo(updated);
        }
    }

    @Nested
    @DisplayName("Type Name")
    class TypeName {

        @Test
        @DisplayName("Player 1 regular piece type name")
        void player1RegularTypeName() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(2, 3));

            assertThat(piece.getTypeName()).isEqualTo("red-checker");
        }

        @Test
        @DisplayName("Player 1 king type name")
        void player1KingTypeName() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_ONE,
                    new BoardPosition(7, 3), true);

            assertThat(piece.getTypeName()).isEqualTo("red-king");
        }

        @Test
        @DisplayName("Player 2 regular piece type name")
        void player2RegularTypeName() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_TWO,
                    new BoardPosition(5, 3));

            assertThat(piece.getTypeName()).isEqualTo("black-checker");
        }

        @Test
        @DisplayName("Player 2 king type name")
        void player2KingTypeName() {
            CheckerPiece piece = new CheckerPiece(1L, Player.PLAYER_TWO,
                    new BoardPosition(0, 3), true);

            assertThat(piece.getTypeName()).isEqualTo("black-king");
        }
    }

    @Nested
    @DisplayName("Display Name")
    class DisplayNameTests {

        @Test
        @DisplayName("should return formatted display name")
        void shouldReturnFormattedDisplayName() {
            CheckerPiece piece = new CheckerPiece(42L, Player.PLAYER_ONE,
                    new BoardPosition(2, 3));

            assertThat(piece.getDisplayName()).isEqualTo("Checker #42");
        }
    }
}
