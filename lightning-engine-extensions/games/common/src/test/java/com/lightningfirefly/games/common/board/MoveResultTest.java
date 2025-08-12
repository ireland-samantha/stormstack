package com.lightningfirefly.games.common.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MoveResult}.
 */
@DisplayName("MoveResult")
class MoveResultTest {

    private final Move sampleMove = new Move(new BoardPosition(2, 1), new BoardPosition(3, 2));

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("success() should create successful result")
        void successShouldCreateSuccessfulResult() {
            MoveResult result = MoveResult.success(sampleMove);

            assertThat(result.success()).isTrue();
            assertThat(result.move()).isEqualTo(sampleMove);
            assertThat(result.capturedPieceIds()).isEmpty();
            assertThat(result.wasPromotion()).isFalse();
            assertThat(result.gameOver()).isFalse();
            assertThat(result.winner()).isEmpty();
            assertThat(result.canContinueCapturing()).isFalse();
        }

        @Test
        @DisplayName("capture() should create capture result")
        void captureShouldCreateCaptureResult() {
            List<Long> capturedIds = List.of(10L, 20L);

            MoveResult result = MoveResult.capture(sampleMove, capturedIds, true);

            assertThat(result.success()).isTrue();
            assertThat(result.capturedPieceIds()).containsExactly(10L, 20L);
            assertThat(result.wasPromotion()).isFalse();
            assertThat(result.canContinueCapturing()).isTrue();
        }

        @Test
        @DisplayName("capture() with no continuation")
        void captureShouldNotContinue() {
            MoveResult result = MoveResult.capture(sampleMove, List.of(10L), false);

            assertThat(result.canContinueCapturing()).isFalse();
        }

        @Test
        @DisplayName("promotion() should create promotion result")
        void promotionShouldCreatePromotionResult() {
            MoveResult result = MoveResult.promotion(sampleMove);

            assertThat(result.success()).isTrue();
            assertThat(result.wasPromotion()).isTrue();
            assertThat(result.capturedPieceIds()).isEmpty();
            assertThat(result.gameOver()).isFalse();
        }

        @Test
        @DisplayName("gameEnd() should create game end result with winner")
        void gameEndShouldCreateGameEndResultWithWinner() {
            MoveResult result = MoveResult.gameEnd(sampleMove, Player.PLAYER_ONE);

            assertThat(result.success()).isTrue();
            assertThat(result.gameOver()).isTrue();
            assertThat(result.winner()).contains(Player.PLAYER_ONE);
        }

        @Test
        @DisplayName("gameEnd() should handle null winner (draw)")
        void gameEndShouldHandleNullWinner() {
            MoveResult result = MoveResult.gameEnd(sampleMove, null);

            assertThat(result.gameOver()).isTrue();
            assertThat(result.winner()).isEmpty();
        }

        @Test
        @DisplayName("failure() should create failed result")
        void failureShouldCreateFailedResult() {
            MoveResult result = MoveResult.failure(sampleMove);

            assertThat(result.success()).isFalse();
            assertThat(result.capturedPieceIds()).isEmpty();
            assertThat(result.wasPromotion()).isFalse();
            assertThat(result.gameOver()).isFalse();
        }
    }

    @Nested
    @DisplayName("Record Properties")
    class RecordProperties {

        @Test
        @DisplayName("should expose all properties")
        void shouldExposeAllProperties() {
            Move move = new Move(new BoardPosition(3, 4), new BoardPosition(5, 6));
            List<Long> captured = List.of(1L, 2L);
            MoveResult result = new MoveResult(move, true, captured, true, true,
                    java.util.Optional.of(Player.PLAYER_TWO), true);

            assertThat(result.move()).isEqualTo(move);
            assertThat(result.success()).isTrue();
            assertThat(result.capturedPieceIds()).isEqualTo(captured);
            assertThat(result.wasPromotion()).isTrue();
            assertThat(result.gameOver()).isTrue();
            assertThat(result.winner()).contains(Player.PLAYER_TWO);
            assertThat(result.canContinueCapturing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal results should be equal")
        void equalResultsShouldBeEqual() {
            MoveResult result1 = MoveResult.success(sampleMove);
            MoveResult result2 = MoveResult.success(sampleMove);

            assertThat(result1).isEqualTo(result2);
        }

        @Test
        @DisplayName("different results should not be equal")
        void differentResultsShouldNotBeEqual() {
            MoveResult result1 = MoveResult.success(sampleMove);
            MoveResult result2 = MoveResult.failure(sampleMove);

            assertThat(result1).isNotEqualTo(result2);
        }
    }
}
