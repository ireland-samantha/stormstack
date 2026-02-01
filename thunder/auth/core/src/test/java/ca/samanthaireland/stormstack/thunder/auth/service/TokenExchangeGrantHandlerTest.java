/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.OAuth2Configuration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenExchangeGrantHandlerTest {

    @Mock
    private ApiTokenService apiTokenService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private OAuth2Configuration oauth2Config;

    private TokenExchangeGrantHandler handler;
    private ServiceClient testClient;

    @BeforeEach
    void setUp() {
        handler = new TokenExchangeGrantHandler(apiTokenService, jwtTokenService, oauth2Config);

        testClient = ServiceClient.createPublic(
                "thunder-cli",
                "Thunder CLI",
                Set.of("*"),
                Set.of(GrantType.PASSWORD, GrantType.REFRESH_TOKEN, GrantType.TOKEN_EXCHANGE)
        );
    }

    @Test
    void getGrantType_returnsTokenExchange() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.TOKEN_EXCHANGE);
    }

    @Test
    void handle_withValidApiToken_exchangesForSessionToken() {
        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        Set<String> tokenScopes = Set.of("engine.container.create", "engine.container.delete");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.accessToken()).isEqualTo(sessionToken);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.scopeSet()).containsExactlyInAnyOrder("engine.container.create", "engine.container.delete");
    }

    @Test
    void handle_withRequestedScopes_filtersToRequestedSubset() {
        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        Set<String> tokenScopes = Set.of("engine.container.create", "engine.container.delete", "engine.module.read");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);
        parameters.put("scope", "engine.container.create");

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        assertThat(response.scopeSet()).containsExactly("engine.container.create");
    }

    @Test
    void handle_withMultipleRequestedScopes_filtersToAvailableScopes() {
        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        Set<String> tokenScopes = Set.of("engine.container.create", "engine.module.read");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);
        // Request scope that token has + one it doesn't
        parameters.put("scope", "engine.container.create engine.admin.full");

        OAuth2TokenResponse response = handler.handle(testClient, parameters);

        // Only engine.container.create should be granted
        assertThat(response.scopeSet()).containsExactly("engine.container.create");
    }

    @Test
    void handle_withRequestedScopesNotInToken_throwsInvalidScope() {
        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        Set<String> tokenScopes = Set.of("engine.container.create");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        // Don't stub oauth2Config - the exception is thrown before it's needed

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);
        // Request scopes that aren't in the token
        parameters.put("scope", "admin.full");

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_SCOPE);
                });
    }

    @Test
    void handle_withUnsupportedTokenType_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", "some-token");
        parameters.put("subject_token_type", "urn:ietf:params:oauth:token-type:jwt");

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void handle_withRestrictedClient_filtersScopesToClientAllowed() {
        // Create a client with restricted scopes
        ServiceClient restrictedClient = ServiceClient.createPublic(
                "restricted-cli",
                "Restricted CLI",
                Set.of("engine.container.create"),
                Set.of(GrantType.TOKEN_EXCHANGE)
        );

        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        // Token has more scopes than client is allowed
        Set<String> tokenScopes = Set.of("engine.container.create", "engine.container.delete", "admin.full");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        OAuth2TokenResponse response = handler.handle(restrictedClient, parameters);

        // Should only include scopes allowed for the client
        assertThat(response.scopeSet()).containsExactly("engine.container.create");
    }

    @Test
    void handle_withNullClient_usesAllTokenScopes() {
        String apiToken = "lat_test123456";
        String sessionToken = "session.jwt.token";
        Set<String> tokenScopes = Set.of("engine.container.create", "admin.full");

        ApiTokenService.TokenExchangeResult exchangeResult = new ApiTokenService.TokenExchangeResult(
                sessionToken,
                java.time.Instant.now().plusSeconds(3600),
                tokenScopes
        );

        when(apiTokenService.exchangeToken(apiToken, null)).thenReturn(exchangeResult);
        when(oauth2Config.userTokenLifetimeSeconds()).thenReturn(3600);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        OAuth2TokenResponse response = handler.handle(null, parameters);

        // Should include all token scopes when no client restriction
        assertThat(response.scopeSet()).containsExactlyInAnyOrder("engine.container.create", "admin.full");
    }

    @Test
    void validateRequest_withMissingSubjectToken_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("subject_token");
                });
    }

    @Test
    void validateRequest_withEmptySubjectToken_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", "   ");
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void validateRequest_withMissingSubjectTokenType_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", "lat_test123456");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("subject_token_type");
                });
    }

    @Test
    void validateRequest_withEmptySubjectTokenType_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", "lat_test123456");
        parameters.put("subject_token_type", "");

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
        parameters.put("subject_token", "lat_test123456");
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        // Should not throw
        handler.validateRequest(parameters);
    }

    @Test
    void handle_whenApiTokenServiceThrows_propagatesException() {
        String apiToken = "lat_invalid";

        when(apiTokenService.exchangeToken(apiToken, null))
                .thenThrow(AuthException.invalidToken("Token not found"));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("subject_token", apiToken);
        parameters.put("subject_token_type", TokenExchangeGrantHandler.TOKEN_TYPE_API_TOKEN);

        assertThatThrownBy(() -> handler.handle(testClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_TOKEN);
                });
    }
}
