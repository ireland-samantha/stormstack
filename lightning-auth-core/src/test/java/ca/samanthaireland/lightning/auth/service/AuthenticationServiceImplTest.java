/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.config.AuthConfiguration;
import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.*;
import ca.samanthaireland.lightning.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthenticationServiceImpl")
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private ScopeService scopeService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private AuthConfiguration config;

    private AuthenticationServiceImpl authService;

    private User testUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        when(config.jwtIssuer()).thenReturn("https://test.lightningfirefly.com");
        when(config.jwtSecret()).thenReturn(Optional.of("test-secret-key-for-unit-tests-only"));
        when(config.sessionExpiryHours()).thenReturn(24);

        authService = new AuthenticationServiceImpl(userRepository, roleService, scopeService, passwordService, config);

        adminRole = new Role(RoleId.generate(), "admin", "Admin role", Set.of());
        testUser = new User(
                UserId.generate(),
                "testuser",
                "$2a$10$hashedpassword",
                Set.of(adminRole.id()),
                Set.of(),
                Instant.now(),
                true
        );
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return token for valid credentials")
        void shouldReturnTokenForValidCredentials() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("password123", testUser.passwordHash())).thenReturn(true);
            when(roleService.findAllById(any())).thenReturn(List.of(adminRole));

            AuthToken token = authService.login("testuser", "password123");

            assertThat(token).isNotNull();
            assertThat(token.username()).isEqualTo("testuser");
            assertThat(token.userId()).isEqualTo(testUser.id());
            assertThat(token.roleNames()).contains("admin");
            assertThat(token.jwtToken()).isNotBlank();
        }

        @Test
        @DisplayName("should throw for non-existent user")
        void shouldThrowForNonExistentUser() {
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("unknown", "password"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("should throw for wrong password")
        void shouldThrowForWrongPassword() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("wrongpassword", testUser.passwordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.login("testuser", "wrongpassword"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("should throw for disabled user")
        void shouldThrowForDisabledUser() {
            User disabledUser = new User(
                    testUser.id(),
                    testUser.username(),
                    testUser.passwordHash(),
                    testUser.roleIds(),
                    Set.of(),
                    testUser.createdAt(),
                    false // disabled
            );
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.login("testuser", "password"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.USER_DISABLED);
        }
    }

    @Nested
    @DisplayName("verifyToken")
    class VerifyToken {

        @Test
        @DisplayName("should verify valid token")
        void shouldVerifyValidToken() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("password123", testUser.passwordHash())).thenReturn(true);
            when(roleService.findAllById(any())).thenReturn(List.of(adminRole));

            AuthToken loginToken = authService.login("testuser", "password123");

            AuthToken verified = authService.verifyToken(loginToken.jwtToken());

            assertThat(verified.username()).isEqualTo("testuser");
            assertThat(verified.userId()).isEqualTo(testUser.id());
        }

        @Test
        @DisplayName("should throw for invalid token")
        void shouldThrowForInvalidToken() {
            assertThatThrownBy(() -> authService.verifyToken("invalid.token.here"))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("should throw for tampered token")
        void shouldThrowForTamperedToken() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("password123", testUser.passwordHash())).thenReturn(true);
            when(roleService.findAllById(any())).thenReturn(List.of(adminRole));

            AuthToken loginToken = authService.login("testuser", "password123");
            String tamperedToken = loginToken.jwtToken() + "tampered";

            assertThatThrownBy(() -> authService.verifyToken(tamperedToken))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.INVALID_TOKEN);
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("should refresh valid token")
        void shouldRefreshValidToken() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("password123", testUser.passwordHash())).thenReturn(true);
            when(roleService.findAllById(any())).thenReturn(List.of(adminRole));

            AuthToken loginToken = authService.login("testuser", "password123");

            AuthToken refreshed = authService.refreshToken(loginToken.jwtToken());

            assertThat(refreshed).isNotNull();
            assertThat(refreshed.username()).isEqualTo("testuser");
            assertThat(refreshed.userId()).isEqualTo(testUser.id());
            assertThat(refreshed.roleNames()).contains("admin");
            assertThat(refreshed.jwtToken()).isNotBlank();
        }

        @Test
        @DisplayName("should throw for disabled user on refresh")
        void shouldThrowForDisabledUserOnRefresh() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(passwordService.verifyPassword("password123", testUser.passwordHash())).thenReturn(true);
            when(roleService.findAllById(any())).thenReturn(List.of(adminRole));

            AuthToken loginToken = authService.login("testuser", "password123");

            User disabledUser = new User(
                    testUser.id(),
                    testUser.username(),
                    testUser.passwordHash(),
                    testUser.roleIds(),
                    Set.of(),
                    testUser.createdAt(),
                    false
            );
            when(userRepository.findById(testUser.id())).thenReturn(Optional.of(disabledUser));

            assertThatThrownBy(() -> authService.refreshToken(loginToken.jwtToken()))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthException.ErrorCode.USER_DISABLED);
        }
    }

    @Nested
    @DisplayName("userHasRole")
    class UserHasRole {

        @Test
        @DisplayName("should return true when user has role")
        void shouldReturnTrueWhenUserHasRole() {
            when(roleService.findAllById(testUser.roleIds())).thenReturn(List.of(adminRole));
            when(roleService.roleIncludes("admin", "admin")).thenReturn(true);

            assertThat(authService.userHasRole(testUser, "admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when user lacks role")
        void shouldReturnFalseWhenUserLacksRole() {
            when(roleService.findAllById(testUser.roleIds())).thenReturn(List.of(adminRole));
            when(roleService.roleIncludes("admin", "superadmin")).thenReturn(false);

            assertThat(authService.userHasRole(testUser, "superadmin")).isFalse();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject null userRepository")
        void shouldRejectNullUserRepository() {
            assertThatThrownBy(() -> new AuthenticationServiceImpl(null, roleService, scopeService, passwordService, config))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null roleService")
        void shouldRejectNullRoleService() {
            assertThatThrownBy(() -> new AuthenticationServiceImpl(userRepository, null, scopeService, passwordService, config))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null scopeService")
        void shouldRejectNullScopeService() {
            assertThatThrownBy(() -> new AuthenticationServiceImpl(userRepository, roleService, null, passwordService, config))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null passwordService")
        void shouldRejectNullPasswordService() {
            assertThatThrownBy(() -> new AuthenticationServiceImpl(userRepository, roleService, scopeService, null, config))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null config")
        void shouldRejectNullConfig() {
            assertThatThrownBy(() -> new AuthenticationServiceImpl(userRepository, roleService, scopeService, passwordService, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should generate secret if not provided")
        void shouldGenerateSecretIfNotProvided() {
            when(config.jwtSecret()).thenReturn(Optional.empty());

            AuthenticationServiceImpl service = new AuthenticationServiceImpl(
                    userRepository, roleService, scopeService, passwordService, config);

            assertThat(service).isNotNull();
        }
    }
}
