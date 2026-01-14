package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserService")
class UserServiceTest {

    private UserService userService;
    private InMemoryUserRepository userRepository;
    private PasswordService passwordService;
    private InMemoryRoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordService = new PasswordService(4); // Low cost for fast tests
        roleRepository = new InMemoryRoleRepository();

        // Create default roles for testing
        roleRepository.save(new Role(0, "view_only", "Read-only access"));
        roleRepository.save(new Role(0, "command_manager", "Command access", Set.of("view_only")));
        roleRepository.save(new Role(0, "admin", "Full access", Set.of("command_manager", "view_only")));

        userService = new UserService(userRepository, passwordService, roleRepository);
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user with hashed password")
        void shouldCreateUserWithHashedPassword() {
            User user = userService.createUser("newuser", "password123", Set.of("view_only"));

            assertThat(user.username()).isEqualTo("newuser");
            assertThat(user.passwordHash()).startsWith("$2"); // BCrypt hash
            assertThat(user.passwordHash()).isNotEqualTo("password123");
            assertThat(passwordService.verifyPassword("password123", user.passwordHash())).isTrue();
        }

        @Test
        @DisplayName("should assign provided roles")
        void shouldAssignProvidedRoles() {
            User user = userService.createUser("admin", "pass", Set.of("admin", "command_manager"));

            assertThat(user.roles()).containsExactlyInAnyOrder("admin", "command_manager");
        }

        @Test
        @DisplayName("should enable user by default")
        void shouldEnableUserByDefault() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            assertThat(user.enabled()).isTrue();
        }

        @Test
        @DisplayName("should set creation timestamp")
        void shouldSetCreationTimestamp() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            assertThat(user.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should assign default view_only role when using simple overload")
        void shouldAssignDefaultRole() {
            User user = userService.createUser("user", "pass");

            assertThat(user.roles()).containsExactly("view_only");
        }

        @Test
        @DisplayName("should throw when username is taken")
        void shouldThrowWhenUsernameTaken() {
            userService.createUser("existing", "pass", Set.of("view_only"));

            assertThatThrownBy(() -> userService.createUser("existing", "pass2", Set.of("admin")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USERNAME_TAKEN));
        }

        @Test
        @DisplayName("should throw when role does not exist")
        void shouldThrowWhenRoleDoesNotExist() {
            assertThatThrownBy(() -> userService.createUser("user", "pass", Set.of("nonexistent_role")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_ROLE));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            User created = userService.createUser("user", "pass", Set.of("view_only"));

            Optional<User> result = userService.findById(created.id());

            assertThat(result).isPresent();
            assertThat(result.get().username()).isEqualTo("user");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> result = userService.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            userService.createUser("testuser", "pass", Set.of("view_only"));

            Optional<User> result = userService.findByUsername("testuser");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<User> result = userService.findByUsername("nonexistent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all users")
        void shouldReturnAllUsers() {
            userService.createUser("user1", "pass", Set.of("view_only"));
            userService.createUser("user2", "pass", Set.of("admin"));

            List<User> result = userService.findAll();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("should update password hash")
        void shouldUpdatePasswordHash() {
            User user = userService.createUser("user", "oldpass", Set.of("view_only"));
            String oldHash = user.passwordHash();

            User updated = userService.updatePassword(user.id(), "newpass");

            assertThat(updated.passwordHash()).isNotEqualTo(oldHash);
            assertThat(passwordService.verifyPassword("newpass", updated.passwordHash())).isTrue();
            assertThat(passwordService.verifyPassword("oldpass", updated.passwordHash())).isFalse();
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            assertThatThrownBy(() -> userService.updatePassword(999L, "newpass"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("updateRoles")
    class UpdateRoles {

        @Test
        @DisplayName("should update user roles")
        void shouldUpdateUserRoles() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            User updated = userService.updateRoles(user.id(), Set.of("admin", "command_manager"));

            assertThat(updated.roles()).containsExactlyInAnyOrder("admin", "command_manager");
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            assertThatThrownBy(() -> userService.updateRoles(999L, Set.of("admin")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("should throw when role does not exist")
        void shouldThrowWhenRoleDoesNotExist() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            assertThatThrownBy(() -> userService.updateRoles(user.id(), Set.of("nonexistent_role")))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_ROLE));
        }
    }

    @Nested
    @DisplayName("addRole")
    class AddRole {

        @Test
        @DisplayName("should add role to user")
        void shouldAddRoleToUser() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            User updated = userService.addRole(user.id(), "command_manager");

            assertThat(updated.roles()).containsExactlyInAnyOrder("view_only", "command_manager");
        }

        @Test
        @DisplayName("should throw when role does not exist")
        void shouldThrowWhenRoleDoesNotExist() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            assertThatThrownBy(() -> userService.addRole(user.id(), "nonexistent_role"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_ROLE));
        }
    }

    @Nested
    @DisplayName("removeRole")
    class RemoveRole {

        @Test
        @DisplayName("should remove role from user")
        void shouldRemoveRoleFromUser() {
            User user = userService.createUser("user", "pass", Set.of("admin", "command_manager"));

            User updated = userService.removeRole(user.id(), "command_manager");

            assertThat(updated.roles()).containsExactly("admin");
        }
    }

    @Nested
    @DisplayName("setEnabled")
    class SetEnabled {

        @Test
        @DisplayName("should disable user")
        void shouldDisableUser() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));
            assertThat(user.enabled()).isTrue();

            User updated = userService.setEnabled(user.id(), false);

            assertThat(updated.enabled()).isFalse();
        }

        @Test
        @DisplayName("should enable user")
        void shouldEnableUser() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));
            userService.setEnabled(user.id(), false);

            User updated = userService.setEnabled(user.id(), true);

            assertThat(updated.enabled()).isTrue();
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            assertThatThrownBy(() -> userService.setEnabled(999L, false))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("should return true and delete user")
        void shouldReturnTrueAndDeleteUser() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));

            boolean result = userService.deleteUser(user.id());

            assertThat(result).isTrue();
            assertThat(userService.findById(user.id())).isEmpty();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenUserNotFound() {
            boolean result = userService.deleteUser(999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isUsernameAvailable")
    class IsUsernameAvailable {

        @Test
        @DisplayName("should return true when username is available")
        void shouldReturnTrueWhenAvailable() {
            assertThat(userService.isUsernameAvailable("newuser")).isTrue();
        }

        @Test
        @DisplayName("should return false when username is taken")
        void shouldReturnFalseWhenTaken() {
            userService.createUser("taken", "pass", Set.of("view_only"));

            assertThat(userService.isUsernameAvailable("taken")).isFalse();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertThat(userService.count()).isZero();

            userService.createUser("user1", "pass", Set.of("view_only"));
            userService.createUser("user2", "pass", Set.of("view_only"));

            assertThat(userService.count()).isEqualTo(2);
        }
    }
}
