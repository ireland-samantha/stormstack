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
class RefreshTokenGrantHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private OAuth2Configuration oauth2Config;

    private RefreshTokenGrantHandler handler;
    private ServiceClient testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        handler = new RefreshTokenGrantHandler(
                userRepository,
                refreshTokenRepository,
                passwordService,
                jwtTokenService,
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
    void getGrantType_returnsRefreshToken() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.REFRESH_TOKEN);
    }

    @Test
    void handle_withValidRefreshToken_issuesNewTokens() {
        String refreshTokenJwt = "valid.refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", testUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client",
                JwtTokenService.CLAIM_SCOPE, "user.read user.write"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), anySet(), eq(3600)))
                .thenReturn("new.access.jwt");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), anySet(), eq(604800)))
                .thenReturn("new.refresh.jwt");
        when(passwordService.hashToken("new.refresh.jwt")).thenReturn("hashed-refresh");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.accessToken()).isEqualTo("new.access.jwt");
        assertThat(response.refreshToken()).isEqualTo("new.refresh.jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.scopeSet()).containsExactlyInAnyOrder("user.read", "user.write");

        // Verify token rotation - new refresh token is stored
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void handle_withInvalidJwt_throwsInvalidRefreshToken() {
        String invalidJwt = "invalid.jwt";

        when(jwtTokenService.verifyToken(invalidJwt))
                .thenThrow(AuthException.invalidToken("JWT verification failed"));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", invalidJwt);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void handle_withWrongTokenType_throwsInvalidRefreshToken() {
        String accessToken = "access.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, "access",
                "user_id", testUser.id().value().toString()
        );

        when(jwtTokenService.verifyToken(accessToken)).thenReturn(claims);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", accessToken);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void handle_withMissingUserIdClaim_throwsInvalidRefreshToken() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH
                // Missing user_id claim
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void handle_withDifferentClient_throwsInvalidRefreshToken() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", testUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "different-client",
                JwtTokenService.CLAIM_SCOPE, "user.read"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                    assertThat(ae.getMessage()).contains("different client");
                });
    }

    @Test
    void handle_withNonExistentUser_throwsInvalidRefreshToken() {
        String refreshTokenJwt = "refresh.jwt";
        UserId unknownUserId = UserId.generate();
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", unknownUserId.value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client",
                JwtTokenService.CLAIM_SCOPE, "user.read"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void handle_withDisabledUser_throwsInvalidRefreshToken() {
        String refreshTokenJwt = "refresh.jwt";
        User disabledUser = testUser.withEnabled(false);
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", disabledUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client",
                JwtTokenService.CLAIM_SCOPE, "user.read"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(disabledUser.id())).thenReturn(Optional.of(disabledUser));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REFRESH_TOKEN);
                    assertThat(ae.getMessage()).contains("disabled");
                });
    }

    @Test
    void handle_withDowngradedScopes_usesRequestedScopes() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", testUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client",
                JwtTokenService.CLAIM_SCOPE, "user.read user.write admin.full"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), eq(Set.of("user.read")), eq(3600)))
                .thenReturn("new.access.jwt");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), eq(Set.of("user.read")), eq(604800)))
                .thenReturn("new.refresh.jwt");
        when(passwordService.hashToken("new.refresh.jwt")).thenReturn("hashed-refresh");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);
        parameters.put("scope", "user.read");  // Requesting fewer scopes

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.scopeSet()).containsExactly("user.read");
    }

    @Test
    void handle_withAdditionalScopes_throwsInvalidScope() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", testUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client",
                JwtTokenService.CLAIM_SCOPE, "user.read"
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);
        parameters.put("scope", "user.read admin.full");  // Requesting additional scope

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_SCOPE);
                    assertThat(ae.getMessage()).contains("additional scopes");
                });
    }

    @Test
    void handle_withEmptyOriginalScopes_usesEmptyScopes() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = Map.of(
                JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH,
                "user_id", testUser.id().value().toString(),
                JwtTokenService.CLAIM_CLIENT_ID, "test-client"
                // No scope claim
        );

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), eq(Set.of()), eq(3600)))
                .thenReturn("new.access.jwt");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), eq(Set.of()), eq(604800)))
                .thenReturn("new.refresh.jwt");
        when(passwordService.hashToken("new.refresh.jwt")).thenReturn("hashed-refresh");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.scopeSet()).isEmpty();
    }

    @Test
    void handle_withNullClientInToken_stillWorks() {
        String refreshTokenJwt = "refresh.jwt";
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtTokenService.CLAIM_TOKEN_TYPE, JwtTokenService.TOKEN_TYPE_REFRESH);
        claims.put("user_id", testUser.id().value().toString());
        claims.put(JwtTokenService.CLAIM_CLIENT_ID, "test-client");  // Must match testClient
        claims.put(JwtTokenService.CLAIM_SCOPE, "user.read");

        when(jwtTokenService.verifyToken(refreshTokenJwt)).thenReturn(claims);
        when(userRepository.findById(testUser.id())).thenReturn(Optional.of(testUser));
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);
        when(oauth2Config.refreshTokenLifetimeSeconds()).thenReturn(604800);
        when(jwtTokenService.createUserAccessToken(eq(testUser), eq(testClient), anySet(), eq(3600)))
                .thenReturn("new.access.jwt");
        when(jwtTokenService.createRefreshToken(eq(testUser), eq(testClient), anySet(), eq(604800)))
                .thenReturn("new.refresh.jwt");
        when(passwordService.hashToken("new.refresh.jwt")).thenReturn("hashed-refresh");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", refreshTokenJwt);

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.accessToken()).isEqualTo("new.access.jwt");
        assertThat(response.refreshToken()).isEqualTo("new.refresh.jwt");
    }

    @Test
    void validateRequest_withMissingRefreshToken_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        // No refresh_token parameter

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("refresh_token");
                });
    }

    @Test
    void validateRequest_withEmptyRefreshToken_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", "   ");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void validateRequest_withValidRefreshToken_doesNotThrow() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("refresh_token", "valid.refresh.jwt");

        // Should not throw
        handler.validateRequest(parameters);
    }
}
