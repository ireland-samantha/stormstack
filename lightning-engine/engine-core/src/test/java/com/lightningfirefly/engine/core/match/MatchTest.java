package com.lightningfirefly.engine.core.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Match}.
 */
@DisplayName("Match")
class MatchTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create match with id and enabled modules")
        void shouldCreateMatchWithIdAndModules() {
            List<String> modules = List.of("MoveModule", "SpawnModule");
            Match match = new Match(1L, modules);

            assertThat(match.id()).isEqualTo(1L);
            assertThat(match.enabledModules()).containsExactly("MoveModule", "SpawnModule");
        }

        @Test
        @DisplayName("should create match with empty modules list")
        void shouldCreateMatchWithEmptyModules() {
            Match match = new Match(1L, List.of());

            assertThat(match.id()).isEqualTo(1L);
            assertThat(match.enabledModules()).isEmpty();
        }

        @Test
        @DisplayName("should allow null modules (no validation in record)")
        void shouldAllowNullModules() {
            Match match = new Match(1L, null);

            assertThat(match.id()).isEqualTo(1L);
            assertThat(match.enabledModules()).isNull();
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal when id and modules are the same")
        void shouldBeEqualWhenIdAndModulesAreSame() {
            List<String> modules = List.of("MoveModule");
            Match match1 = new Match(1L, modules);
            Match match2 = new Match(1L, modules);

            assertThat(match1).isEqualTo(match2);
            assertThat(match1.hashCode()).isEqualTo(match2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when ids differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            List<String> modules = List.of("MoveModule");
            Match match1 = new Match(1L, modules);
            Match match2 = new Match(2L, modules);

            assertThat(match1).isNotEqualTo(match2);
        }

        @Test
        @DisplayName("should not be equal when modules differ")
        void shouldNotBeEqualWhenModulesDiffer() {
            Match match1 = new Match(1L, List.of("MoveModule"));
            Match match2 = new Match(1L, List.of("SpawnModule"));

            assertThat(match1).isNotEqualTo(match2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include id and modules in string representation")
        void shouldIncludeIdAndModulesInString() {
            Match match = new Match(42L, List.of("MoveModule", "SpawnModule"));

            String str = match.toString();

            assertThat(str).contains("42");
            assertThat(str).contains("MoveModule");
            assertThat(str).contains("SpawnModule");
        }
    }
}
