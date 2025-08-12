package com.lightningfirefly.engine.core.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlayerMatch}.
 */
@DisplayName("PlayerMatch")
class PlayerMatchTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create player match with player and match ids")
        void shouldCreatePlayerMatchWithIds() {
            PlayerMatch playerMatch = new PlayerMatch(10L, 20L);

            assertThat(playerMatch.playerId()).isEqualTo(10L);
            assertThat(playerMatch.matchId()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal when both ids are the same")
        void shouldBeEqualWhenBothIdsAreSame() {
            PlayerMatch pm1 = new PlayerMatch(1L, 2L);
            PlayerMatch pm2 = new PlayerMatch(1L, 2L);

            assertThat(pm1).isEqualTo(pm2);
            assertThat(pm1.hashCode()).isEqualTo(pm2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when player ids differ")
        void shouldNotBeEqualWhenPlayerIdsDiffer() {
            PlayerMatch pm1 = new PlayerMatch(1L, 2L);
            PlayerMatch pm2 = new PlayerMatch(99L, 2L);

            assertThat(pm1).isNotEqualTo(pm2);
        }

        @Test
        @DisplayName("should not be equal when match ids differ")
        void shouldNotBeEqualWhenMatchIdsDiffer() {
            PlayerMatch pm1 = new PlayerMatch(1L, 2L);
            PlayerMatch pm2 = new PlayerMatch(1L, 99L);

            assertThat(pm1).isNotEqualTo(pm2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include both ids in string representation")
        void shouldIncludeBothIdsInString() {
            PlayerMatch playerMatch = new PlayerMatch(42L, 99L);

            String str = playerMatch.toString();

            assertThat(str).contains("42");
            assertThat(str).contains("99");
        }
    }
}
