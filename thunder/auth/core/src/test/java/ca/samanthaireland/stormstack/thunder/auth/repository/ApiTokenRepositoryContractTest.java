/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link ApiTokenRepository} interface.
 *
 * <p>These tests verify the expected behavior of any ApiTokenRepository implementation.
 * They use an in-memory implementation to test the interface contract without
 * requiring external dependencies like MongoDB.
 */
@DisplayName("ApiTokenRepository Contract Tests")
class ApiTokenRepositoryContractTest {

    private ApiTokenRepository repository;
    private UserId userId1;
    private UserId userId2;

    @BeforeEach
    void setUp() {
        repository = new InMemoryApiTokenRepository();
        userId1 = UserId.generate();
        userId2 = UserId.generate();
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return token when exists")
        void shouldReturnTokenWhenExists() {
            ApiToken token = createTestToken(userId1, "test-token");
            repository.save(token);

            Optional<ApiToken> found = repository.findById(token.id());

            assertThat(found).isPresent();
            assertThat(found.get().name()).isEqualTo("test-token");
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            Optional<ApiToken> found = repository.findById(ApiTokenId.generate());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserId {

        @Test
        @DisplayName("should return all tokens for user")
        void shouldReturnAllTokensForUser() {
            repository.save(createTestToken(userId1, "token1"));
            repository.save(createTestToken(userId1, "token2"));
            repository.save(createTestToken(userId2, "token3"));

            List<ApiToken> tokens = repository.findByUserId(userId1);

            assertThat(tokens).hasSize(2);
            assertThat(tokens).extracting(ApiToken::name)
                    .containsExactlyInAnyOrder("token1", "token2");
        }

        @Test
        @DisplayName("should return empty list for user with no tokens")
        void shouldReturnEmptyForUserWithNoTokens() {
            List<ApiToken> tokens = repository.findByUserId(UserId.generate());

            assertThat(tokens).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all tokens")
        void shouldReturnAllTokens() {
            repository.save(createTestToken(userId1, "token1"));
            repository.save(createTestToken(userId1, "token2"));
            repository.save(createTestToken(userId2, "token3"));

            List<ApiToken> tokens = repository.findAll();

            assertThat(tokens).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no tokens")
        void shouldReturnEmptyWhenNoTokens() {
            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllActive()")
    class FindAllActive {

        @Test
        @DisplayName("should return only active tokens")
        void shouldReturnOnlyActiveTokens() {
            // Active token (not revoked, not expired)
            repository.save(createTestToken(userId1, "active"));

            // Revoked token
            ApiToken revoked = createTestToken(userId1, "revoked").revoke();
            repository.save(revoked);

            // Expired token
            ApiToken expired = createTestTokenWithExpiry(userId1, "expired",
                    Instant.now().minus(1, ChronoUnit.DAYS));
            repository.save(expired);

            List<ApiToken> active = repository.findAllActive();

            assertThat(active).hasSize(1);
            assertThat(active.get(0).name()).isEqualTo("active");
        }

        @Test
        @DisplayName("should include tokens with no expiry")
        void shouldIncludeTokensWithNoExpiry() {
            ApiToken noExpiry = createTestToken(userId1, "no-expiry");
            repository.save(noExpiry);

            List<ApiToken> active = repository.findAllActive();

            assertThat(active).hasSize(1);
        }

        @Test
        @DisplayName("should include tokens with future expiry")
        void shouldIncludeTokensWithFutureExpiry() {
            ApiToken futureExpiry = createTestTokenWithExpiry(userId1, "future",
                    Instant.now().plus(7, ChronoUnit.DAYS));
            repository.save(futureExpiry);

            List<ApiToken> active = repository.findAllActive();

            assertThat(active).hasSize(1);
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should persist new token")
        void shouldPersistNewToken() {
            ApiToken token = createTestToken(userId1, "new-token");

            repository.save(token);

            assertThat(repository.findById(token.id())).isPresent();
        }

        @Test
        @DisplayName("should update existing token")
        void shouldUpdateExistingToken() {
            ApiToken token = createTestToken(userId1, "test-token");
            repository.save(token);

            ApiToken revoked = token.revoke();
            repository.save(revoked);

            Optional<ApiToken> found = repository.findById(token.id());
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
            ApiToken token = createTestToken(userId1, "test-token");
            repository.save(token);

            boolean deleted = repository.deleteById(token.id());

            assertThat(deleted).isTrue();
            assertThat(repository.findById(token.id())).isEmpty();
        }

        @Test
        @DisplayName("should return false for non-existent token")
        void shouldReturnFalseForNonExistent() {
            boolean deleted = repository.deleteById(ApiTokenId.generate());

            assertThat(deleted).isFalse();
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
            repository.save(createTestToken(userId1, "token1"));
            repository.save(createTestToken(userId1, "token2"));

            assertThat(repository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("countActiveByUserId()")
    class CountActiveByUserId {

        @Test
        @DisplayName("should count only active tokens for user")
        void shouldCountOnlyActiveTokensForUser() {
            // Active tokens for user1
            repository.save(createTestToken(userId1, "active1"));
            repository.save(createTestToken(userId1, "active2"));

            // Revoked token for user1
            repository.save(createTestToken(userId1, "revoked").revoke());

            // Active token for user2 (should not be counted)
            repository.save(createTestToken(userId2, "other-user"));

            long count = repository.countActiveByUserId(userId1);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero for user with no tokens")
        void shouldReturnZeroForUserWithNoTokens() {
            assertThat(repository.countActiveByUserId(UserId.generate())).isZero();
        }

        @Test
        @DisplayName("should exclude expired tokens")
        void shouldExcludeExpiredTokens() {
            repository.save(createTestToken(userId1, "active"));
            repository.save(createTestTokenWithExpiry(userId1, "expired",
                    Instant.now().minus(1, ChronoUnit.HOURS)));

            long count = repository.countActiveByUserId(userId1);

            assertThat(count).isEqualTo(1);
        }
    }

    private ApiToken createTestToken(UserId userId, String name) {
        return new ApiToken(
                ApiTokenId.generate(),
                userId,
                name,
                "token-hash-" + name,
                Set.of("scope1", "scope2"),
                Instant.now(),
                null,  // no expiry
                null,  // not revoked
                null,  // never used
                null   // no IP
        );
    }

    private ApiToken createTestTokenWithExpiry(UserId userId, String name, Instant expiresAt) {
        return new ApiToken(
                ApiTokenId.generate(),
                userId,
                name,
                "token-hash-" + name,
                Set.of("scope1"),
                Instant.now(),
                expiresAt,
                null,
                null,
                null
        );
    }

    /**
     * In-memory implementation for contract testing.
     */
    private static class InMemoryApiTokenRepository implements ApiTokenRepository {
        private final Map<ApiTokenId, ApiToken> tokens = new HashMap<>();

        @Override
        public Optional<ApiToken> findById(ApiTokenId id) {
            return Optional.ofNullable(tokens.get(id));
        }

        @Override
        public List<ApiToken> findByUserId(UserId userId) {
            return tokens.values().stream()
                    .filter(t -> t.userId().equals(userId))
                    .toList();
        }

        @Override
        public List<ApiToken> findAll() {
            return new ArrayList<>(tokens.values());
        }

        @Override
        public List<ApiToken> findAllActive() {
            Instant now = Instant.now();
            return tokens.values().stream()
                    .filter(t -> t.revokedAt() == null)
                    .filter(t -> t.expiresAt() == null || t.expiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public ApiToken save(ApiToken token) {
            tokens.put(token.id(), token);
            return token;
        }

        @Override
        public boolean deleteById(ApiTokenId id) {
            return tokens.remove(id) != null;
        }

        @Override
        public long count() {
            return tokens.size();
        }

        @Override
        public long countActiveByUserId(UserId userId) {
            Instant now = Instant.now();
            return tokens.values().stream()
                    .filter(t -> t.userId().equals(userId))
                    .filter(t -> t.revokedAt() == null)
                    .filter(t -> t.expiresAt() == null || t.expiresAt().isAfter(now))
                    .count();
        }
    }
}
