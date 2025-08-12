package com.lightningfirefly.games.common.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Move}.
 */
@DisplayName("Move")
class MoveTest {

    @Nested
    @DisplayName("Simple Move")
    class SimpleMove {

        @Test
        @DisplayName("should create simple move")
        void shouldCreateSimpleMove() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(3, 2);

            Move move = new Move(from, to);

            assertThat(move.getFrom()).isEqualTo(from);
            assertThat(move.getTo()).isEqualTo(to);
            assertThat(move.getType()).isEqualTo(Move.MoveType.SIMPLE);
            assertThat(move.isCapture()).isFalse();
            assertThat(move.getCapturedPositions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Capture Move")
    class CaptureMove {

        @Test
        @DisplayName("should create capture move")
        void shouldCreateCaptureMove() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(4, 3);
            List<BoardPosition> captured = List.of(new BoardPosition(3, 2));

            Move move = new Move(from, to, captured);

            assertThat(move.getFrom()).isEqualTo(from);
            assertThat(move.getTo()).isEqualTo(to);
            assertThat(move.isCapture()).isTrue();
            assertThat(move.getCapturedPositions()).containsExactly(new BoardPosition(3, 2));
        }

        @Test
        @DisplayName("should create capture move with explicit type")
        void shouldCreateCaptureMoveWithExplicitType() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(4, 3);
            List<BoardPosition> captured = List.of(new BoardPosition(3, 2));

            Move move = new Move(from, to, captured, Move.MoveType.CAPTURE);

            assertThat(move.getType()).isEqualTo(Move.MoveType.CAPTURE);
            assertThat(move.isCapture()).isTrue();
        }

        @Test
        @DisplayName("isCapture should check captured positions, not type")
        void isCaptureShouldCheckCapturedPositions() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(3, 2);

            // Even with CAPTURE type, if no captured positions, isCapture is false
            Move move = new Move(from, to, List.of(), Move.MoveType.CAPTURE);

            assertThat(move.isCapture()).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("captured positions should be immutable")
        void capturedPositionsShouldBeImmutable() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(4, 3);
            List<BoardPosition> captured = List.of(new BoardPosition(3, 2));

            Move move = new Move(from, to, captured);

            assertThatThrownBy(() -> move.getCapturedPositions().add(new BoardPosition(5, 5)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should handle null captured positions")
        void shouldHandleNullCapturedPositions() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(3, 2);

            Move move = new Move(from, to, null, Move.MoveType.SIMPLE);

            assertThat(move.getCapturedPositions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal moves should be equal")
        void equalMovesShouldBeEqual() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(3, 2);

            Move move1 = new Move(from, to);
            Move move2 = new Move(from, to);

            assertThat(move1).isEqualTo(move2);
            assertThat(move1.hashCode()).isEqualTo(move2.hashCode());
        }

        @Test
        @DisplayName("different moves should not be equal")
        void differentMovesShouldNotBeEqual() {
            Move move1 = new Move(new BoardPosition(2, 1), new BoardPosition(3, 2));
            Move move2 = new Move(new BoardPosition(2, 1), new BoardPosition(3, 0));

            assertThat(move1).isNotEqualTo(move2);
        }

        @Test
        @DisplayName("capture moves with same captures should be equal")
        void captureMovesShouldCompareCaptures() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(4, 3);
            List<BoardPosition> captured = List.of(new BoardPosition(3, 2));

            Move move1 = new Move(from, to, captured);
            Move move2 = new Move(from, to, captured);

            assertThat(move1).isEqualTo(move2);
        }

        @Test
        @DisplayName("moves with different captured positions should not be equal")
        void movesDifferentCapturesShouldNotBeEqual() {
            BoardPosition from = new BoardPosition(2, 1);
            BoardPosition to = new BoardPosition(4, 3);

            Move move1 = new Move(from, to, List.of(new BoardPosition(3, 2)));
            Move move2 = new Move(from, to, List.of(new BoardPosition(3, 4)));

            assertThat(move1).isNotEqualTo(move2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("simple move toString")
        void simpleMoveToString() {
            Move move = new Move(new BoardPosition(2, 1), new BoardPosition(3, 2));

            String str = move.toString();

            assertThat(str).contains("->").doesNotContain("captures");
        }

        @Test
        @DisplayName("capture move toString")
        void captureMoveToString() {
            Move move = new Move(
                    new BoardPosition(2, 1),
                    new BoardPosition(4, 3),
                    List.of(new BoardPosition(3, 2))
            );

            String str = move.toString();

            assertThat(str).contains("->").contains("captures");
        }
    }

    @Nested
    @DisplayName("Move Types")
    class MoveTypes {

        @Test
        @DisplayName("should have all move types")
        void shouldHaveAllMoveTypes() {
            assertThat(Move.MoveType.values()).containsExactlyInAnyOrder(
                    Move.MoveType.SIMPLE,
                    Move.MoveType.CAPTURE,
                    Move.MoveType.PROMOTION,
                    Move.MoveType.CAPTURE_AND_PROMOTION
            );
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        @DisplayName("should reject null from position")
        void shouldRejectNullFromPosition() {
            assertThatThrownBy(() -> new Move(null, new BoardPosition(3, 2)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null to position")
        void shouldRejectNullToPosition() {
            assertThatThrownBy(() -> new Move(new BoardPosition(2, 1), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
