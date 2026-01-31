/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
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
class ClientCredentialsGrantHandlerTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private OAuth2Configuration oauth2Config;

    private ClientCredentialsGrantHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClientCredentialsGrantHandler(jwtTokenService, oauth2Config);
    }

    @Test
    void getGrantType_returnsClientCredentials() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.CLIENT_CREDENTIALS);
    }

    @Test
    void handle_withAuthenticatedClient_issuesToken() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1", "scope2"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        when(oauth2Config.serviceTokenLifetimeSeconds()).thenReturn(900);
        when(jwtTokenService.createServiceToken(eq(client), anySet(), eq(900)))
                .thenReturn("test.jwt.token");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("test.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.scopeSet()).containsAll(Set.of("scope1", "scope2"));
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void handle_withRequestedScopes_filtersToAllowed() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1", "scope2"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        when(oauth2Config.serviceTokenLifetimeSeconds()).thenReturn(900);
        when(jwtTokenService.createServiceToken(eq(client), eq(Set.of("scope1")), eq(900)))
                .thenReturn("test.jwt.token");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");
        parameters.put("scope", "scope1");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.scopeSet()).containsExactly("scope1");
    }

    @Test
    void handle_withUnauthorizedScope_throwsInvalidScope() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");
        parameters.put("scope", "unauthorized-scope");

        assertThatThrownBy(() -> handler.handle(client, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_SCOPE);
                });
    }

    @Test
    void handle_withoutClient_throwsInvalidClient() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        assertThatThrownBy(() -> handler.handle(null, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void handle_withNoRequestedScopes_usesAllAllowedScopes() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1", "scope2", "scope3"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        when(oauth2Config.serviceTokenLifetimeSeconds()).thenReturn(900);
        when(jwtTokenService.createServiceToken(eq(client), anySet(), eq(900)))
                .thenReturn("test.jwt.token");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.scopeSet()).containsExactlyInAnyOrder("scope1", "scope2", "scope3");
    }

    @Test
    void validateRequest_acceptsEmptyParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        // Should not throw
        handler.validateRequest(parameters);
    }
}
