package com.lightningfirefly.engine.internal.core.impl;

import com.lightningfirefly.engine.internal.core.match.InMemoryMatchRepository;
import com.lightningfirefly.engine.core.match.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryMatchRepository}.
 *
 * <p>Tests verify Repository pattern compliance with pure CRUD operations.
 */
@DisplayName("InMemoryMatchRepository")
class InMemoryMatchRepositoryTest {

    private InMemoryMatchRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMatchRepository();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should store match")
        void shouldStoreMatch() {
            Match match = new Match(1L, List.of());

            Match result = repository.save(match);

            assertThat(result).isEqualTo(match);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should overwrite existing match with same id")
        void shouldOverwriteExistingMatch() {
            Match match1 = new Match(1L, List.of());
            Match match2 = new Match(1L, List.of("module1"));

            repository.save(match1);
            Match result = repository.save(match2);

            assertThat(result).isEqualTo(match2);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should reject null match")
        void shouldRejectNullMatch() {
            assertThatThrownBy(() -> repository.save(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("match must not be null");
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("should remove match")
        void shouldRemoveMatch() {
            Match match = new Match(1L, List.of());
            repository.save(match);

            repository.deleteById(1L);

            assertThat(repository.findById(1L)).isEmpty();
            assertThat(repository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not throw when deleting non-existent match")
        void shouldNotThrowWhenDeletingNonExistent() {
            repository.deleteById(999L);

            assertThat(repository.count()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return match when found")
        void shouldReturnMatchWhenFound() {
            Match match = new Match(1L, List.of());
            repository.save(match);

            Optional<Match> result = repository.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(match);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<Match> result = repository.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all matches")
        void shouldReturnAllMatches() {
            repository.save(new Match(1L, List.of()));
            repository.save(new Match(2L, List.of()));
            repository.save(new Match(3L, List.of()));

            List<Match> result = repository.findAll();

            assertThat(result).hasSize(3);
            assertThat(result).extracting(Match::id).containsExactlyInAnyOrder(1L, 2L, 3L);
        }

        @Test
        @DisplayName("should return empty list when no matches")
        void shouldReturnEmptyListWhenNoMatches() {
            List<Match> result = repository.findAll();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsById")
    class ExistsById {

        @Test
        @DisplayName("should return true when match exists")
        void shouldReturnTrueWhenMatchExists() {
            repository.save(new Match(1L, List.of()));

            assertThat(repository.existsById(1L)).isTrue();
        }

        @Test
        @DisplayName("should return false when match does not exist")
        void shouldReturnFalseWhenMatchDoesNotExist() {
            assertThat(repository.existsById(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertThat(repository.count()).isEqualTo(0);

            repository.save(new Match(1L, List.of()));
            assertThat(repository.count()).isEqualTo(1);

            repository.save(new Match(2L, List.of()));
            assertThat(repository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove all matches")
        void shouldRemoveAllMatches() {
            repository.save(new Match(1L, List.of()));
            repository.save(new Match(2L, List.of()));
            repository.save(new Match(3L, List.of()));

            repository.clear();

            assertThat(repository.count()).isEqualTo(0);
        }
    }
}
