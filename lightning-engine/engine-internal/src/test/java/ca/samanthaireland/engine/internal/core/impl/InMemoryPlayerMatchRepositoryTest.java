/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.internal.core.impl;

import ca.samanthaireland.engine.internal.core.match.InMemoryPlayerMatchRepository;
import ca.samanthaireland.engine.core.match.PlayerMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryPlayerMatchRepository}.
 *
 * <p>Tests verify Repository pattern compliance with pure CRUD operations.
 */
@DisplayName("InMemoryPlayerMatchRepository")
class InMemoryPlayerMatchRepositoryTest {

    private InMemoryPlayerMatchRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPlayerMatchRepository();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should store player-match")
        void shouldStorePlayerMatch() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);

            PlayerMatch result = repository.save(playerMatch);

            assertThat(result).isEqualTo(playerMatch);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should overwrite existing player-match with same composite key")
        void shouldOverwriteExistingPlayerMatch() {
            PlayerMatch pm1 = new PlayerMatch(1L, 100L);
            PlayerMatch pm2 = new PlayerMatch(1L, 100L);

            repository.save(pm1);
            PlayerMatch result = repository.save(pm2);

            assertThat(result).isEqualTo(pm2);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should allow same player in different matches")
        void shouldAllowSamePlayerInDifferentMatches() {
            PlayerMatch pm1 = new PlayerMatch(1L, 100L);
            PlayerMatch pm2 = new PlayerMatch(1L, 200L);

            repository.save(pm1);
            PlayerMatch result = repository.save(pm2);

            assertThat(result).isEqualTo(pm2);
            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("should reject null player-match")
        void shouldRejectNullPlayerMatch() {
            assertThatThrownBy(() -> repository.save(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("playerMatch must not be null");
        }
    }

    @Nested
    @DisplayName("deleteByPlayerAndMatch")
    class DeleteByPlayerAndMatch {

        @Test
        @DisplayName("should remove player-match")
        void shouldRemovePlayerMatch() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            repository.save(playerMatch);

            repository.deleteByPlayerAndMatch(1L, 100L);

            assertThat(repository.findByPlayerAndMatch(1L, 100L)).isEmpty();
            assertThat(repository.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not throw when deleting non-existent player-match")
        void shouldNotThrowWhenDeletingNonExistent() {
            repository.deleteByPlayerAndMatch(999L, 999L);

            assertThat(repository.count()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByPlayerAndMatch")
    class FindByPlayerAndMatch {

        @Test
        @DisplayName("should return player-match when found")
        void shouldReturnPlayerMatchWhenFound() {
            PlayerMatch playerMatch = new PlayerMatch(1L, 100L);
            repository.save(playerMatch);

            Optional<PlayerMatch> result = repository.findByPlayerAndMatch(1L, 100L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(playerMatch);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<PlayerMatch> result = repository.findByPlayerAndMatch(1L, 100L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByMatchId")
    class FindByMatchId {

        @Test
        @DisplayName("should return all players in match")
        void shouldReturnAllPlayersInMatch() {
            repository.save(new PlayerMatch(1L, 100L));
            repository.save(new PlayerMatch(2L, 100L));
            repository.save(new PlayerMatch(3L, 200L));

            List<PlayerMatch> result = repository.findByMatchId(100L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PlayerMatch::playerId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("should return empty list when no matches")
        void shouldReturnEmptyListWhenNoMatches() {
            List<PlayerMatch> result = repository.findByMatchId(100L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByPlayerId")
    class FindByPlayerId {

        @Test
        @DisplayName("should return all matches for player")
        void shouldReturnAllMatchesForPlayer() {
            repository.save(new PlayerMatch(1L, 100L));
            repository.save(new PlayerMatch(1L, 200L));
            repository.save(new PlayerMatch(2L, 100L));

            List<PlayerMatch> result = repository.findByPlayerId(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PlayerMatch::matchId).containsExactlyInAnyOrder(100L, 200L);
        }

        @Test
        @DisplayName("should return empty list when no matches")
        void shouldReturnEmptyListWhenNoMatches() {
            List<PlayerMatch> result = repository.findByPlayerId(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByPlayerAndMatch")
    class ExistsByPlayerAndMatch {

        @Test
        @DisplayName("should return true when player-match exists")
        void shouldReturnTrueWhenPlayerMatchExists() {
            repository.save(new PlayerMatch(1L, 100L));

            assertThat(repository.existsByPlayerAndMatch(1L, 100L)).isTrue();
        }

        @Test
        @DisplayName("should return false when player-match does not exist")
        void shouldReturnFalseWhenPlayerMatchDoesNotExist() {
            assertThat(repository.existsByPlayerAndMatch(999L, 999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertThat(repository.count()).isEqualTo(0);

            repository.save(new PlayerMatch(1L, 100L));
            assertThat(repository.count()).isEqualTo(1);

            repository.save(new PlayerMatch(2L, 100L));
            assertThat(repository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove all player-matches")
        void shouldRemoveAllPlayerMatches() {
            repository.save(new PlayerMatch(1L, 100L));
            repository.save(new PlayerMatch(2L, 100L));
            repository.save(new PlayerMatch(3L, 200L));

            repository.clear();

            assertThat(repository.count()).isEqualTo(0);
        }
    }
}
