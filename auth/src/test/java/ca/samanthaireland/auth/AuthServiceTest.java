package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthService")
class AuthServiceTest {

    private AuthService authService;
    private UserService userService;
    private InMemoryUserRepository userRepository;
    private InMemoryRoleRepository roleRepository;
    private PasswordService passwordService;
    private RoleService roleService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        roleRepository = new InMemoryRoleRepository();
        passwordService = new PasswordService(4); // Low cost for fast tests

        // Create default roles for testing
        roleRepository.save(new Role(0, "view_only", "Read-only access"));
        roleRepository.save(new Role(0, "command_manager", "Command access", Set.of("view_only")));
        roleRepository.save(new Role(0, "admin", "Full access", Set.of("command_manager", "view_only")));

        roleService = new RoleService(roleRepository);
        userService = new UserService(userRepository, passwordService, roleRepository);
        authService = new AuthService(userRepository, passwordService, roleService, "test-secret-key", 24);
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return token for valid credentials")
        void shouldReturnTokenForValidCredentials() {
            userService.createUser("testuser", "password123", Set.of("view_only"));

            AuthToken token = authService.login("testuser", "password123");

            assertThat(token).isNotNull();
            assertThat(token.username()).isEqualTo("testuser");
            assertThat(token.roles()).containsExactly("view_only");
            assertThat(token.jwtToken()).isNotBlank();
        }

        @Test
        @DisplayName("should include all user roles in token")
        void shouldIncludeAllUserRolesInToken() {
            userService.createUser("admin", "pass", Set.of("admin", "command_manager"));

            AuthToken token = authService.login("admin", "pass");

            assertThat(token.roles()).containsExactlyInAnyOrder("admin", "command_manager");
        }

        @Test
        @DisplayName("should throw for wrong password")
        void shouldThrowForWrongPassword() {
            userService.createUser("user", "correctpass", Set.of("view_only"));

            assertThatThrownBy(() -> authService.login("user", "wrongpass"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("should throw for nonexistent user")
        void shouldThrowForNonexistentUser() {
            assertThatThrownBy(() -> authService.login("nonexistent", "pass"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("should throw for disabled user")
        void shouldThrowForDisabledUser() {
            User user = userService.createUser("disabled", "pass", Set.of("view_only"));
            userService.setEnabled(user.id(), false);

            assertThatThrownBy(() -> authService.login("disabled", "pass"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_DISABLED));
        }
    }

    @Nested
    @DisplayName("token claims")
    class TokenClaims {

        @Test
        @DisplayName("should issue token with correct claims")
        void shouldIssueTokenWithCorrectClaims() {
            User user = userService.createUser("testuser", "pass", Set.of("command_manager"));

            AuthToken token = authService.login("testuser", "pass");

            assertThat(token.userId()).isEqualTo(user.id());
            assertThat(token.username()).isEqualTo("testuser");
            assertThat(token.roles()).containsExactly("command_manager");
            assertThat(token.expiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("should set expiration in the future")
        void shouldSetExpirationInFuture() {
            userService.createUser("user", "pass", Set.of("view_only"));

            AuthToken token = authService.login("user", "pass");

            assertThat(token.expiresAt()).isAfter(Instant.now());
            assertThat(token.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyToken")
    class VerifyToken {

        @Test
        @DisplayName("should verify valid token")
        void shouldVerifyValidToken() {
            userService.createUser("user", "pass", Set.of("admin"));
            AuthToken original = authService.login("user", "pass");

            AuthToken verified = authService.verifyToken(original.jwtToken());

            assertThat(verified.userId()).isEqualTo(original.userId());
            assertThat(verified.username()).isEqualTo(original.username());
            assertThat(verified.roles()).isEqualTo(original.roles());
        }

        @Test
        @DisplayName("should throw for invalid token")
        void shouldThrowForInvalidToken() {
            assertThatThrownBy(() -> authService.verifyToken("invalid.token.here"))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("should throw for tampered token")
        void shouldThrowForTamperedToken() {
            userService.createUser("user", "pass", Set.of("view_only"));
            AuthToken token = authService.login("user", "pass");
            String tamperedToken = token.jwtToken() + "tampered";

            assertThatThrownBy(() -> authService.verifyToken(tamperedToken))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("should throw for token signed with different secret")
        void shouldThrowForTokenSignedWithDifferentSecret() {
            AuthService otherService = new AuthService(userRepository, passwordService, roleService, "other-secret", 24);
            userService.createUser("user", "pass", Set.of("view_only"));
            AuthToken tokenFromOther = otherService.login("user", "pass");

            assertThatThrownBy(() -> authService.verifyToken(tokenFromOther.jwtToken()))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.INVALID_TOKEN));
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("should return new token with extended expiry")
        void shouldReturnNewTokenWithExtendedExpiry() throws InterruptedException {
            userService.createUser("user", "pass", Set.of("view_only"));
            AuthToken original = authService.login("user", "pass");

            // Wait briefly so the new token has a different timestamp
            Thread.sleep(1100);

            AuthToken refreshed = authService.refreshToken(original.jwtToken());

            assertThat(refreshed.userId()).isEqualTo(original.userId());
            assertThat(refreshed.username()).isEqualTo(original.username());
            assertThat(refreshed.jwtToken()).isNotEqualTo(original.jwtToken());
            assertThat(refreshed.expiresAt()).isAfterOrEqualTo(original.expiresAt());
        }

        @Test
        @DisplayName("should throw for invalid token")
        void shouldThrowForInvalidToken() {
            assertThatThrownBy(() -> authService.refreshToken("invalid"))
                    .isInstanceOf(AuthException.class);
        }

        @Test
        @DisplayName("should throw if user was disabled")
        void shouldThrowIfUserWasDisabled() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));
            AuthToken token = authService.login("user", "pass");
            userService.setEnabled(user.id(), false);

            assertThatThrownBy(() -> authService.refreshToken(token.jwtToken()))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_DISABLED));
        }

        @Test
        @DisplayName("should throw if user was deleted")
        void shouldThrowIfUserWasDeleted() {
            User user = userService.createUser("user", "pass", Set.of("view_only"));
            AuthToken token = authService.login("user", "pass");
            userService.deleteUser(user.id());

            assertThatThrownBy(() -> authService.refreshToken(token.jwtToken()))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                            .isEqualTo(AuthException.ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("AuthToken helper methods")
    class AuthTokenHelpers {

        @Test
        @DisplayName("hasRole should check role names")
        void hasRoleShouldCheckRoleNames() {
            userService.createUser("admin", "pass", Set.of("admin"));
            AuthToken token = authService.login("admin", "pass");

            assertThat(token.hasRole("admin")).isTrue();
            assertThat(token.hasRole("nonexistent")).isFalse();
        }

        @Test
        @DisplayName("isAdmin should return true only for admin")
        void isAdminShouldReturnTrueOnlyForAdmin() {
            userService.createUser("admin", "pass", Set.of("admin"));
            userService.createUser("viewer", "pass", Set.of("view_only"));

            assertThat(authService.login("admin", "pass").isAdmin()).isTrue();
            assertThat(authService.login("viewer", "pass").isAdmin()).isFalse();
        }

        @Test
        @DisplayName("canSubmitCommands should check command manager role")
        void canSubmitCommandsShouldCheckCommandManagerRole() {
            userService.createUser("cmdmgr", "pass", Set.of("command_manager"));
            userService.createUser("viewer", "pass", Set.of("view_only"));
            userService.createUser("admin", "pass", Set.of("admin"));

            assertThat(authService.login("cmdmgr", "pass").canSubmitCommands()).isTrue();
            assertThat(authService.login("admin", "pass").canSubmitCommands()).isTrue();
            assertThat(authService.login("viewer", "pass").canSubmitCommands()).isFalse();
        }

        @Test
        @DisplayName("canViewSnapshots should be true for all roles")
        void canViewSnapshotsShouldBeTrueForAllRoles() {
            userService.createUser("viewer", "pass", Set.of("view_only"));
            userService.createUser("cmdmgr", "pass", Set.of("command_manager"));
            userService.createUser("admin", "pass", Set.of("admin"));

            assertThat(authService.login("viewer", "pass").canViewSnapshots()).isTrue();
            assertThat(authService.login("cmdmgr", "pass").canViewSnapshots()).isTrue();
            assertThat(authService.login("admin", "pass").canViewSnapshots()).isTrue();
        }
    }
}
