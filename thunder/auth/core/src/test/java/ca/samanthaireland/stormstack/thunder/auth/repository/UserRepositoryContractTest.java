/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link UserRepository} interface.
 *
 * <p>These tests verify the expected behavior of any UserRepository implementation.
 * They use an in-memory implementation to test the interface contract without
 * requiring external dependencies like MongoDB.
 */
@DisplayName("UserRepository Contract Tests")
class UserRepositoryContractTest {

    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return user when exists")
        void shouldReturnUserWhenExists() {
            User user = createTestUser("testuser");
            repository.save(user);

            Optional<User> found = repository.findById(user.id());

            assertThat(found).isPresent();
            assertThat(found.get().username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            Optional<User> found = repository.findById(UserId.generate());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUsername()")
    class FindByUsername {

        @Test
        @DisplayName("should return user with exact match")
        void shouldReturnUserWithExactMatch() {
            User user = createTestUser("testuser");
            repository.save(user);

            Optional<User> found = repository.findByUsername("testuser");

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(user.id());
        }

        @Test
        @DisplayName("should return user case-insensitively")
        void shouldReturnUserCaseInsensitively() {
            User user = createTestUser("TestUser");
            repository.save(user);

            Optional<User> found = repository.findByUsername("testuser");

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(user.id());
        }

        @Test
        @DisplayName("should return empty for non-existent username")
        void shouldReturnEmptyForNonExistent() {
            Optional<User> found = repository.findByUsername("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all users")
        void shouldReturnAllUsers() {
            repository.save(createTestUser("user1"));
            repository.save(createTestUser("user2"));
            repository.save(createTestUser("user3"));

            List<User> users = repository.findAll();

            assertThat(users).hasSize(3);
            assertThat(users).extracting(User::username)
                    .containsExactlyInAnyOrder("user1", "user2", "user3");
        }

        @Test
        @DisplayName("should return empty list when no users")
        void shouldReturnEmptyWhenNoUsers() {
            List<User> users = repository.findAll();

            assertThat(users).isEmpty();
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should persist new user")
        void shouldPersistNewUser() {
            User user = createTestUser("newuser");

            User saved = repository.save(user);

            assertThat(saved).isNotNull();
            assertThat(repository.findById(user.id())).isPresent();
        }

        @Test
        @DisplayName("should update existing user")
        void shouldUpdateExistingUser() {
            User user = createTestUser("testuser");
            repository.save(user);

            User updated = user.withEnabled(false);
            repository.save(updated);

            Optional<User> found = repository.findById(user.id());
            assertThat(found).isPresent();
            assertThat(found.get().enabled()).isFalse();
        }

        @Test
        @DisplayName("should update user roles")
        void shouldUpdateUserRoles() {
            User user = createTestUser("testuser");
            repository.save(user);

            RoleId newRoleId = RoleId.generate();
            User updated = user.withRoleIds(Set.of(newRoleId));
            repository.save(updated);

            Optional<User> found = repository.findById(user.id());
            assertThat(found).isPresent();
            assertThat(found.get().roleIds()).containsExactly(newRoleId);
        }
    }

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("should remove existing user")
        void shouldRemoveExistingUser() {
            User user = createTestUser("testuser");
            repository.save(user);

            boolean deleted = repository.deleteById(user.id());

            assertThat(deleted).isTrue();
            assertThat(repository.findById(user.id())).isEmpty();
        }

        @Test
        @DisplayName("should return false for non-existent user")
        void shouldReturnFalseForNonExistent() {
            boolean deleted = repository.deleteById(UserId.generate());

            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByUsername()")
    class ExistsByUsername {

        @Test
        @DisplayName("should return true for existing username")
        void shouldReturnTrueForExisting() {
            repository.save(createTestUser("testuser"));

            assertThat(repository.existsByUsername("testuser")).isTrue();
        }

        @Test
        @DisplayName("should return true case-insensitively")
        void shouldReturnTrueCaseInsensitively() {
            repository.save(createTestUser("TestUser"));

            assertThat(repository.existsByUsername("testuser")).isTrue();
            assertThat(repository.existsByUsername("TESTUSER")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-existent username")
        void shouldReturnFalseForNonExistent() {
            assertThat(repository.existsByUsername("nonexistent")).isFalse();
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
            repository.save(createTestUser("user1"));
            repository.save(createTestUser("user2"));
            repository.save(createTestUser("user3"));

            assertThat(repository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("should decrease after deletion")
        void shouldDecreaseAfterDeletion() {
            User user = createTestUser("user1");
            repository.save(user);
            repository.save(createTestUser("user2"));

            repository.deleteById(user.id());

            assertThat(repository.count()).isEqualTo(1);
        }
    }

    private User createTestUser(String username) {
        return new User(
                UserId.generate(),
                username,
                "password-hash",
                Set.of(),
                Set.of(),
                Instant.now(),
                true
        );
    }

    /**
     * In-memory implementation for contract testing.
     */
    private static class InMemoryUserRepository implements UserRepository {
        private final Map<UserId, User> users = new HashMap<>();

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.values().stream()
                    .filter(u -> u.username().equalsIgnoreCase(username))
                    .findFirst();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public User save(User user) {
            users.put(user.id(), user);
            return user;
        }

        @Override
        public boolean deleteById(UserId id) {
            return users.remove(id) != null;
        }

        @Override
        public boolean existsByUsername(String username) {
            return users.values().stream()
                    .anyMatch(u -> u.username().equalsIgnoreCase(username));
        }

        @Override
        public long count() {
            return users.size();
        }
    }
}
