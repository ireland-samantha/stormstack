/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link MatchTokenRepository} interface.
 *
 * <p>These tests verify the expected behavior of any MatchTokenRepository implementation.
 * They use an in-memory implementation to test the interface contract without
 * requiring external dependencies like MongoDB.
 */
@DisplayName("MatchTokenRepository Contract Tests")
class MatchTokenRepositoryContractTest {

    private MatchTokenRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMatchTokenRepository();
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return token when exists")
        void shouldReturnTokenWhenExists() {
            MatchToken token = createTestToken("match-1", "player-1");
            repository.save(token);

            Optional<MatchToken> found = repository.findById(token.id());

            assertThat(found).isPresent();
            assertThat(found.get().matchId()).isEqualTo("match-1");
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            Optional<MatchToken> found = repository.findById(MatchTokenId.generate());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByMatchId()")
    class FindByMatchId {

        @Test
        @DisplayName("should return all tokens for match")
        void shouldReturnAllTokensForMatch() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-1", "player-2"));
            repository.save(createTestToken("match-2", "player-1"));

            List<MatchToken> tokens = repository.findByMatchId("match-1");

            assertThat(tokens).hasSize(2);
            assertThat(tokens).allMatch(t -> t.matchId().equals("match-1"));
        }

        @Test
        @DisplayName("should return empty list for match with no tokens")
        void shouldReturnEmptyForMatchWithNoTokens() {
            assertThat(repository.findByMatchId("nonexistent")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByPlayerId()")
    class FindByPlayerId {

        @Test
        @DisplayName("should return all tokens for player")
        void shouldReturnAllTokensForPlayer() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-2", "player-1"));
            repository.save(createTestToken("match-1", "player-2"));

            List<MatchToken> tokens = repository.findByPlayerId("player-1");

            assertThat(tokens).hasSize(2);
            assertThat(tokens).allMatch(t -> t.playerId().equals("player-1"));
        }
    }

    @Nested
    @DisplayName("findActiveByMatchAndPlayer()")
    class FindActiveByMatchAndPlayer {

        @Test
        @DisplayName("should return active token for match and player")
        void shouldReturnActiveToken() {
            MatchToken token = createTestToken("match-1", "player-1");
            repository.save(token);

            Optional<MatchToken> found = repository.findActiveByMatchAndPlayer("match-1", "player-1");

            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("should not return revoked token")
        void shouldNotReturnRevokedToken() {
            MatchToken revoked = createTestToken("match-1", "player-1").revoke();
            repository.save(revoked);

            Optional<MatchToken> found = repository.findActiveByMatchAndPlayer("match-1", "player-1");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should not return expired token")
        void shouldNotReturnExpiredToken() {
            MatchToken expired = createExpiredToken("match-1", "player-1");
            repository.save(expired);

            Optional<MatchToken> found = repository.findActiveByMatchAndPlayer("match-1", "player-1");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByMatchId()")
    class FindActiveByMatchId {

        @Test
        @DisplayName("should return only active tokens")
        void shouldReturnOnlyActiveTokens() {
            // Active token
            repository.save(createTestToken("match-1", "player-1"));
            // Revoked token
            repository.save(createTestToken("match-1", "player-2").revoke());
            // Expired token
            repository.save(createExpiredToken("match-1", "player-3"));

            List<MatchToken> active = repository.findActiveByMatchId("match-1");

            assertThat(active).hasSize(1);
            assertThat(active.get(0).playerId()).isEqualTo("player-1");
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all tokens")
        void shouldReturnAllTokens() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-2", "player-2"));

            assertThat(repository.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should persist new token")
        void shouldPersistNewToken() {
            MatchToken token = createTestToken("match-1", "player-1");

            repository.save(token);

            assertThat(repository.findById(token.id())).isPresent();
        }

        @Test
        @DisplayName("should update existing token")
        void shouldUpdateExistingToken() {
            MatchToken token = createTestToken("match-1", "player-1");
            repository.save(token);

            MatchToken revoked = token.revoke();
            repository.save(revoked);

            Optional<MatchToken> found = repository.findById(token.id());
            assertThat(found).isPresent();
            assertThat(found.get().revokedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("should remove existing token")
        void shouldRemoveExistingToken() {
            MatchToken token = createTestToken("match-1", "player-1");
            repository.save(token);

            boolean deleted = repository.deleteById(token.id());

            assertThat(deleted).isTrue();
            assertThat(repository.findById(token.id())).isEmpty();
        }

        @Test
        @DisplayName("should return false for non-existent token")
        void shouldReturnFalseForNonExistent() {
            assertThat(repository.deleteById(MatchTokenId.generate())).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteByMatchId()")
    class DeleteByMatchId {

        @Test
        @DisplayName("should delete all tokens for match")
        void shouldDeleteAllTokensForMatch() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-1", "player-2"));
            repository.save(createTestToken("match-2", "player-1"));

            long deleted = repository.deleteByMatchId("match-1");

            assertThat(deleted).isEqualTo(2);
            assertThat(repository.findByMatchId("match-1")).isEmpty();
            assertThat(repository.findByMatchId("match-2")).hasSize(1);
        }

        @Test
        @DisplayName("should return zero for match with no tokens")
        void shouldReturnZeroForMatchWithNoTokens() {
            assertThat(repository.deleteByMatchId("nonexistent")).isZero();
        }
    }

    @Nested
    @DisplayName("count()")
    class Count {

        @Test
        @DisplayName("should return zero for empty repository")
        void shouldReturnZeroForEmpty() {
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-1", "player-2"));

            assertThat(repository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countActiveByMatchId()")
    class CountActiveByMatchId {

        @Test
        @DisplayName("should count only active tokens")
        void shouldCountOnlyActiveTokens() {
            repository.save(createTestToken("match-1", "player-1"));
            repository.save(createTestToken("match-1", "player-2"));
            repository.save(createTestToken("match-1", "player-3").revoke());

            assertThat(repository.countActiveByMatchId("match-1")).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero for match with no active tokens")
        void shouldReturnZeroForNoActiveTokens() {
            repository.save(createTestToken("match-1", "player-1").revoke());

            assertThat(repository.countActiveByMatchId("match-1")).isZero();
        }
    }

    private MatchToken createTestToken(String matchId, String playerId) {
        return MatchToken.create(
                matchId,
                null,  // containerId
                playerId,
                null,  // userId
                "Player " + playerId,
                Set.of(MatchToken.SCOPE_SUBMIT_COMMANDS, MatchToken.SCOPE_VIEW_SNAPSHOTS),
                Instant.now().plus(1, ChronoUnit.HOURS)
        );
    }

    private MatchToken createExpiredToken(String matchId, String playerId) {
        return MatchToken.create(
                matchId,
                null,  // containerId
                playerId,
                null,  // userId
                "Player " + playerId,
                Set.of(MatchToken.SCOPE_SUBMIT_COMMANDS),
                Instant.now().minus(1, ChronoUnit.HOURS)  // Already expired
        );
    }

    /**
     * In-memory implementation for contract testing.
     */
    private static class InMemoryMatchTokenRepository implements MatchTokenRepository {
        private final Map<MatchTokenId, MatchToken> tokens = new HashMap<>();

        @Override
        public Optional<MatchToken> findById(MatchTokenId id) {
            return Optional.ofNullable(tokens.get(id));
        }

        @Override
        public List<MatchToken> findByMatchId(String matchId) {
            return tokens.values().stream()
                    .filter(t -> t.matchId().equals(matchId))
                    .toList();
        }

        @Override
        public List<MatchToken> findByPlayerId(String playerId) {
            return tokens.values().stream()
                    .filter(t -> t.playerId().equals(playerId))
                    .toList();
        }

        @Override
        public Optional<MatchToken> findActiveByMatchAndPlayer(String matchId, String playerId) {
            Instant now = Instant.now();
            return tokens.values().stream()
                    .filter(t -> t.matchId().equals(matchId))
                    .filter(t -> t.playerId().equals(playerId))
                    .filter(t -> t.revokedAt() == null)
                    .filter(t -> t.expiresAt().isAfter(now))
                    .findFirst();
        }

        @Override
        public List<MatchToken> findActiveByMatchId(String matchId) {
            Instant now = Instant.now();
            return tokens.values().stream()
                    .filter(t -> t.matchId().equals(matchId))
                    .filter(t -> t.revokedAt() == null)
                    .filter(t -> t.expiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public List<MatchToken> findAll() {
            return new ArrayList<>(tokens.values());
        }

        @Override
        public MatchToken save(MatchToken token) {
            tokens.put(token.id(), token);
            return token;
        }

        @Override
        public boolean deleteById(MatchTokenId id) {
            return tokens.remove(id) != null;
        }

        @Override
        public long deleteByMatchId(String matchId) {
            List<MatchTokenId> toDelete = tokens.values().stream()
                    .filter(t -> t.matchId().equals(matchId))
                    .map(MatchToken::id)
                    .toList();
            toDelete.forEach(tokens::remove);
            return toDelete.size();
        }

        @Override
        public long count() {
            return tokens.size();
        }

        @Override
        public long countActiveByMatchId(String matchId) {
            Instant now = Instant.now();
            return tokens.values().stream()
                    .filter(t -> t.matchId().equals(matchId))
                    .filter(t -> t.revokedAt() == null)
                    .filter(t -> t.expiresAt().isAfter(now))
                    .count();
        }
    }
}
