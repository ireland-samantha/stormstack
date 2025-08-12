package com.lightningfirefly.games.common.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Player}.
 */
@DisplayName("Player")
class PlayerTest {

    @Nested
    @DisplayName("Opponent")
    class Opponent {

        @Test
        @DisplayName("Player 1 opponent should be Player 2")
        void player1OpponentShouldBePlayer2() {
            assertThat(Player.PLAYER_ONE.opponent()).isEqualTo(Player.PLAYER_TWO);
        }

        @Test
        @DisplayName("Player 2 opponent should be Player 1")
        void player2OpponentShouldBePlayer1() {
            assertThat(Player.PLAYER_TWO.opponent()).isEqualTo(Player.PLAYER_ONE);
        }

        @Test
        @DisplayName("double opponent should return same player")
        void doubleOpponentShouldReturnSame() {
            assertThat(Player.PLAYER_ONE.opponent().opponent()).isEqualTo(Player.PLAYER_ONE);
            assertThat(Player.PLAYER_TWO.opponent().opponent()).isEqualTo(Player.PLAYER_TWO);
        }
    }

    @Nested
    @DisplayName("Display Name")
    class DisplayNameTests {

        @Test
        @DisplayName("Player 1 display name")
        void player1DisplayName() {
            assertThat(Player.PLAYER_ONE.getDisplayName()).isEqualTo("Player 1");
        }

        @Test
        @DisplayName("Player 2 display name")
        void player2DisplayName() {
            assertThat(Player.PLAYER_TWO.getDisplayName()).isEqualTo("Player 2");
        }
    }

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have exactly two players")
        void shouldHaveExactlyTwoPlayers() {
            assertThat(Player.values()).hasSize(2);
        }

        @Test
        @DisplayName("should contain both players")
        void shouldContainBothPlayers() {
            assertThat(Player.values()).containsExactly(Player.PLAYER_ONE, Player.PLAYER_TWO);
        }

        @Test
        @DisplayName("valueOf should work")
        void valueOfShouldWork() {
            assertThat(Player.valueOf("PLAYER_ONE")).isEqualTo(Player.PLAYER_ONE);
            assertThat(Player.valueOf("PLAYER_TWO")).isEqualTo(Player.PLAYER_TWO);
        }
    }
}
