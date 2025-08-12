package com.lightningfirefly.games.common.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BoardPosition}.
 */
@DisplayName("BoardPosition")
class BoardPositionTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should store row and column")
        void shouldStoreRowAndColumn() {
            BoardPosition pos = new BoardPosition(3, 5);

            assertThat(pos.row()).isEqualTo(3);
            assertThat(pos.col()).isEqualTo(5);
        }

        @Test
        @DisplayName("should allow negative coordinates")
        void shouldAllowNegativeCoordinates() {
            BoardPosition pos = new BoardPosition(-1, -2);

            assertThat(pos.row()).isEqualTo(-1);
            assertThat(pos.col()).isEqualTo(-2);
        }
    }

    @Nested
    @DisplayName("Bounds Checking")
    class BoundsChecking {

        @ParameterizedTest
        @CsvSource({
                "0, 0, true",
                "7, 7, true",
                "3, 4, true",
                "-1, 0, false",
                "0, -1, false",
                "8, 0, false",
                "0, 8, false",
                "8, 8, false"
        })
        @DisplayName("should check bounds correctly for 8x8 board")
        void shouldCheckBounds(int row, int col, boolean expected) {
            BoardPosition pos = new BoardPosition(row, col);

            assertThat(pos.isWithinBounds(8, 8)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should check bounds for different board sizes")
        void shouldCheckBoundsForDifferentSizes() {
            BoardPosition pos = new BoardPosition(4, 4);

            assertThat(pos.isWithinBounds(5, 5)).isTrue();
            assertThat(pos.isWithinBounds(4, 5)).isFalse();
            assertThat(pos.isWithinBounds(5, 4)).isFalse();
        }
    }

    @Nested
    @DisplayName("Dark Square Detection")
    class DarkSquareDetection {

        @ParameterizedTest
        @CsvSource({
                "0, 0, false",
                "0, 1, true",
                "0, 2, false",
                "1, 0, true",
                "1, 1, false",
                "1, 2, true",
                "2, 3, true",
                "3, 3, false",
                "7, 7, false",
                "7, 6, true"
        })
        @DisplayName("should identify dark squares correctly")
        void shouldIdentifyDarkSquares(int row, int col, boolean expected) {
            BoardPosition pos = new BoardPosition(row, col);

            assertThat(pos.isDarkSquare()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Offset Calculation")
    class OffsetCalculation {

        @Test
        @DisplayName("should calculate offset correctly")
        void shouldCalculateOffset() {
            BoardPosition pos = new BoardPosition(3, 3);

            assertThat(pos.offset(1, 1)).isEqualTo(new BoardPosition(4, 4));
            assertThat(pos.offset(-1, 1)).isEqualTo(new BoardPosition(2, 4));
            assertThat(pos.offset(1, -1)).isEqualTo(new BoardPosition(4, 2));
            assertThat(pos.offset(-1, -1)).isEqualTo(new BoardPosition(2, 2));
        }

        @Test
        @DisplayName("should handle zero offset")
        void shouldHandleZeroOffset() {
            BoardPosition pos = new BoardPosition(5, 5);

            assertThat(pos.offset(0, 0)).isEqualTo(pos);
        }

        @Test
        @DisplayName("should allow offset to negative coordinates")
        void shouldAllowOffsetToNegative() {
            BoardPosition pos = new BoardPosition(0, 0);

            assertThat(pos.offset(-2, -3)).isEqualTo(new BoardPosition(-2, -3));
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("equal positions should be equal")
        void equalPositionsShouldBeEqual() {
            BoardPosition pos1 = new BoardPosition(3, 4);
            BoardPosition pos2 = new BoardPosition(3, 4);

            assertThat(pos1).isEqualTo(pos2);
            assertThat(pos1.hashCode()).isEqualTo(pos2.hashCode());
        }

        @Test
        @DisplayName("different positions should not be equal")
        void differentPositionsShouldNotBeEqual() {
            BoardPosition pos1 = new BoardPosition(3, 4);
            BoardPosition pos2 = new BoardPosition(4, 3);

            assertThat(pos1).isNotEqualTo(pos2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should have readable toString")
        void shouldHaveReadableToString() {
            BoardPosition pos = new BoardPosition(3, 4);

            String str = pos.toString();

            assertThat(str).contains("3").contains("4");
        }
    }
}
