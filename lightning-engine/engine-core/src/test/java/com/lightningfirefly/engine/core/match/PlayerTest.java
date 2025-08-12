package com.lightningfirefly.engine.core.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Player}.
 */
@DisplayName("Player")
class PlayerTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create player with id")
        void shouldCreatePlayerWithId() {
            Player player = new Player(42L);

            assertThat(player.id()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should allow negative id")
        void shouldAllowNegativeId() {
            Player player = new Player(-1L);

            assertThat(player.id()).isEqualTo(-1L);
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal when ids are the same")
        void shouldBeEqualWhenIdsAreSame() {
            Player player1 = new Player(1L);
            Player player2 = new Player(1L);

            assertThat(player1).isEqualTo(player2);
            assertThat(player1.hashCode()).isEqualTo(player2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when ids differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            Player player1 = new Player(1L);
            Player player2 = new Player(2L);

            assertThat(player1).isNotEqualTo(player2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include id in string representation")
        void shouldIncludeIdInString() {
            Player player = new Player(42L);

            assertThat(player.toString()).contains("42");
        }
    }
}
