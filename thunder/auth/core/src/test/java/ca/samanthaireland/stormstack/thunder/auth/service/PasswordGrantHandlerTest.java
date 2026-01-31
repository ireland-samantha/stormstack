/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.OAuth2Configuration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.*;
import ca.samanthaireland.stormstack.thunder.auth.repository.RefreshTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordGrantHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ScopeService scopeService;

    @Mock
    private OAuth2Configuration oauth2Config;

    private PasswordGrantHandler handler;
    private ServiceClient testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        handler = new PasswordGrantHandler(
                userRepository,
                passwordService,
                jwtTokenService,
                refreshTokenRepository,
                scopeService,
                oauth2Config
        );

        testClient = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("*"),
                Set.of(GrantType.PASSWORD, GrantType.REFRESH_TOKEN)
        );

        testUser = User.create("testuser", "password-hash", Set.of());
    }

    @Test
    void getGrantType_returnsPassword() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.PASSWORD);
    }

    @Test
    void handle_withValidCredentials_issuesTokens() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordService.verifyPassword("password123", "password-hash")).thenReturn(true);
        when(scopeService.resolveScopes(anySet())).thenReturn(Set.of("user.read", "user.write"));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), anySet(), eq(3600)))
                .thenReturn("access.jwt.token");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), anySet(), eq(604800)))
                .thenReturn("refresh.jwt.token");
        when(passwordService.hashToken("refresh.jwt.token")).thenReturn("hashed-refresh");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");
        parameters.put("password", "password123");

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.accessToken()).isEqualTo("access.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("refresh.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void handle_withInvalidUsername_throwsInvalidGrant() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "unknown");
        parameters.put("password", "password123");

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_GRANT);
                });
    }

    @Test
    void handle_withInvalidPassword_throwsInvalidGrant() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordService.verifyPassword("wrongpassword", "password-hash")).thenReturn(false);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");
        parameters.put("password", "wrongpassword");

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_GRANT);
                });
    }

    @Test
    void handle_withDisabledUser_throwsInvalidGrant() {
        User disabledUser = testUser.withEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(disabledUser));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");
        parameters.put("password", "password123");

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_GRANT);
                });
    }

    @Test
    void handle_withRequestedScopes_filtersToUserScopes() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordService.verifyPassword("password123", "password-hash")).thenReturn(true);
        when(scopeService.resolveScopes(anySet())).thenReturn(Set.of("user.read", "user.write"));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), eq(Set.of("user.read")), eq(3600)))
                .thenReturn("access.jwt.token");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), eq(Set.of("user.read")), eq(604800)))
                .thenReturn("refresh.jwt.token");
        when(passwordService.hashToken(anyString())).thenReturn("hashed");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");
        parameters.put("password", "password123");
        parameters.put("scope", "user.read");

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.scopeSet()).containsExactly("user.read");
    }

    @Test
    void validateRequest_withMissingUsername_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("password", "password123");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void validateRequest_withMissingPassword_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void validateRequest_withValidParameters_doesNotThrow() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "password");
        parameters.put("username", "testuser");
        parameters.put("password", "password123");

        // Should not throw
        handler.validateRequest(parameters);
    }
}
