package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryUserRepository")
class InMemoryUserRepositoryTest {

    private InMemoryUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
    }

    @Nested
    @DisplayName("withAdminUser factory")
    class WithAdminUser {

        @Test
        @DisplayName("should create repository with admin user")
        void shouldCreateRepositoryWithAdminUser() {
            InMemoryUserRepository repo = InMemoryUserRepository.withAdminUser("hashedPassword");

            Optional<User> admin = repo.findByUsername("admin");

            assertThat(admin).isPresent();
            assertThat(admin.get().username()).isEqualTo("admin");
            assertThat(admin.get().passwordHash()).isEqualTo("hashedPassword");
            assertThat(admin.get().roles()).containsExactly("admin");
            assertThat(admin.get().enabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should generate ID for new user")
        void shouldGenerateIdForNewUser() {
            User user = new User(0, "testuser", "hash", Set.of("view_only"), Instant.now(), true);

            User saved = repository.save(user);

            assertThat(saved.id()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should preserve provided data")
        void shouldPreserveProvidedData() {
            User user = new User(0, "testuser", "hash", Set.of("admin", "view_only"), Instant.now(), false);

            User saved = repository.save(user);

            assertThat(saved.username()).isEqualTo("testuser");
            assertThat(saved.passwordHash()).isEqualTo("hash");
            assertThat(saved.roles()).containsExactlyInAnyOrder("admin", "view_only");
            assertThat(saved.enabled()).isFalse();
        }

        @Test
        @DisplayName("should update existing user")
        void shouldUpdateExistingUser() {
            User user = repository.save(new User(0, "user1", "hash1", Set.of("view_only"), Instant.now(), true));
            User updated = user.withPasswordHash("hash2");

            repository.save(updated);

            Optional<User> found = repository.findById(user.id());
            assertThat(found).isPresent();
            assertThat(found.get().passwordHash()).isEqualTo("hash2");
        }

        @Test
        @DisplayName("should handle username change")
        void shouldHandleUsernameChange() {
            User user = repository.save(new User(0, "oldname", "hash", Set.of("view_only"), Instant.now(), true));
            User renamed = new User(user.id(), "newname", user.passwordHash(), user.roles(), user.createdAt(), user.enabled());

            repository.save(renamed);

            assertThat(repository.findByUsername("oldname")).isEmpty();
            assertThat(repository.findByUsername("newname")).isPresent();
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            repository.save(new User(0, "testuser", "hash", Set.of("view_only"), Instant.now(), true));

            Optional<User> result = repository.findByUsername("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> result = repository.findByUsername("nonexistent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            User saved = repository.save(new User(0, "user", "hash", Set.of("view_only"), Instant.now(), true));

            Optional<User> result = repository.findById(saved.id());

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(saved.id());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> result = repository.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return empty list when no users")
        void shouldReturnEmptyListWhenNoUsers() {
            List<User> result = repository.findAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return all users")
        void shouldReturnAllUsers() {
            repository.save(new User(0, "user1", "hash", Set.of("view_only"), Instant.now(), true));
            repository.save(new User(0, "user2", "hash", Set.of("admin"), Instant.now(), true));

            List<User> result = repository.findAll();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(User::username).containsExactlyInAnyOrder("user1", "user2");
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("should return true and delete user when found")
        void shouldReturnTrueAndDeleteUserWhenFound() {
            User saved = repository.save(new User(0, "user", "hash", Set.of("view_only"), Instant.now(), true));

            boolean result = repository.deleteById(saved.id());

            assertThat(result).isTrue();
            assertThat(repository.findById(saved.id())).isEmpty();
            assertThat(repository.findByUsername("user")).isEmpty();
        }

        @Test
        @DisplayName("should return false when not found")
        void shouldReturnFalseWhenNotFound() {
            boolean result = repository.deleteById(999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByUsername")
    class ExistsByUsername {

        @Test
        @DisplayName("should return true when username exists")
        void shouldReturnTrueWhenUsernameExists() {
            repository.save(new User(0, "existing", "hash", Set.of("view_only"), Instant.now(), true));

            assertThat(repository.existsByUsername("existing")).isTrue();
        }

        @Test
        @DisplayName("should return false when username does not exist")
        void shouldReturnFalseWhenUsernameDoesNotExist() {
            assertThat(repository.existsByUsername("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return 0 when empty")
        void shouldReturnZeroWhenEmpty() {
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            repository.save(new User(0, "user1", "hash", Set.of("view_only"), Instant.now(), true));
            repository.save(new User(0, "user2", "hash", Set.of("view_only"), Instant.now(), true));
            repository.save(new User(0, "user3", "hash", Set.of("view_only"), Instant.now(), true));

            assertThat(repository.count()).isEqualTo(3);
        }
    }
}
