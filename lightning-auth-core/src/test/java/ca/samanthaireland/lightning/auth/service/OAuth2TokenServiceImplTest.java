/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.repository.ServiceClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2TokenServiceImplTest {

    @Mock
    private ServiceClientRepository clientRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private OAuth2GrantHandler clientCredentialsHandler;

    @Mock
    private OAuth2GrantHandler passwordHandler;

    private OAuth2TokenServiceImpl tokenService;
    private ServiceClient testClient;

    @BeforeEach
    void setUp() {
        when(clientCredentialsHandler.getGrantType()).thenReturn(GrantType.CLIENT_CREDENTIALS);
        when(passwordHandler.getGrantType()).thenReturn(GrantType.PASSWORD);

        tokenService = new OAuth2TokenServiceImpl(
                clientRepository,
                passwordService,
                List.of(clientCredentialsHandler, passwordHandler)
        );

        testClient = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1", "scope2"),
                Set.of(GrantType.CLIENT_CREDENTIALS, GrantType.PASSWORD)
        );
    }

    @Test
    void processTokenRequest_withValidClientCredentials_delegatesToHandler() {
        OAuth2TokenResponse expectedResponse = OAuth2TokenResponse.forClientCredentials(
                "access-token", 900, Set.of("scope1")
        );

        when(clientRepository.findByClientId("test-client")).thenReturn(Optional.of(testClient));
        when(passwordService.verifyPassword("secret", "secret-hash")).thenReturn(true);
        when(clientCredentialsHandler.handle(eq(testClient), anyMap())).thenReturn(expectedResponse);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");
        parameters.put("client_id", "test-client");
        parameters.put("client_secret", "secret");

        OAuth2TokenResponse response = tokenService.processTokenRequest(parameters);

        assertThat(response).isEqualTo(expectedResponse);
        verify(clientCredentialsHandler).validateRequest(parameters);
        verify(clientCredentialsHandler).handle(eq(testClient), anyMap());
    }

    @Test
    void processTokenRequest_withPreAuthenticatedClient_skipesAuthentication() {
        OAuth2TokenResponse expectedResponse = OAuth2TokenResponse.forClientCredentials(
                "access-token", 900, Set.of("scope1")
        );

        when(clientCredentialsHandler.handle(eq(testClient), anyMap())).thenReturn(expectedResponse);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        OAuth2TokenResponse response = tokenService.processTokenRequest(testClient, parameters);

        assertThat(response).isEqualTo(expectedResponse);
        verify(clientRepository, never()).findByClientId(anyString());
    }

    @Test
    void processTokenRequest_withMissingGrantType_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();

        assertThatThrownBy(() -> tokenService.processTokenRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void processTokenRequest_withUnsupportedGrantType_throwsUnsupportedGrantType() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "unknown_grant");

        assertThatThrownBy(() -> tokenService.processTokenRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.UNSUPPORTED_GRANT_TYPE);
                });
    }

    @Test
    void processTokenRequest_withUnauthorizedGrantType_throwsUnauthorizedClient() {
        ServiceClient passwordOnlyClient = ServiceClient.createConfidential(
                "password-client",
                "secret-hash",
                "Password Client",
                Set.of("scope1"),
                Set.of(GrantType.PASSWORD)
        );

        when(clientRepository.findByClientId("password-client")).thenReturn(Optional.of(passwordOnlyClient));
        when(passwordService.verifyPassword("secret", "secret-hash")).thenReturn(true);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");
        parameters.put("client_id", "password-client");
        parameters.put("client_secret", "secret");

        assertThatThrownBy(() -> tokenService.processTokenRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.UNAUTHORIZED_CLIENT);
                });
    }

    @Test
    void processTokenRequest_clientCredentialsWithoutAuth_throwsInvalidClient() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        assertThatThrownBy(() -> tokenService.processTokenRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void authenticateClient_withValidCredentials_returnsClient() {
        when(clientRepository.findByClientId("test-client")).thenReturn(Optional.of(testClient));
        when(passwordService.verifyPassword("secret", "secret-hash")).thenReturn(true);

        ServiceClient result = tokenService.authenticateClient("test-client", "secret");

        assertThat(result).isEqualTo(testClient);
    }

    @Test
    void authenticateClient_withInvalidSecret_throwsInvalidClient() {
        when(clientRepository.findByClientId("test-client")).thenReturn(Optional.of(testClient));
        when(passwordService.verifyPassword("wrong", "secret-hash")).thenReturn(false);

        assertThatThrownBy(() -> tokenService.authenticateClient("test-client", "wrong"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void authenticateClient_withUnknownClient_throwsInvalidClient() {
        when(clientRepository.findByClientId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.authenticateClient("unknown", "secret"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void authenticateClient_withDisabledClient_throwsClientDisabled() {
        ServiceClient disabledClient = testClient.withEnabled(false);
        when(clientRepository.findByClientId("test-client")).thenReturn(Optional.of(disabledClient));
        when(passwordService.verifyPassword("secret", "secret-hash")).thenReturn(true);

        assertThatThrownBy(() -> tokenService.authenticateClient("test-client", "secret"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void authenticateClient_withPublicClient_throwsInvalidClient() {
        ServiceClient publicClient = ServiceClient.createPublic(
                "public-client",
                "Public Client",
                Set.of("scope1"),
                Set.of(GrantType.PASSWORD)
        );

        when(clientRepository.findByClientId("public-client")).thenReturn(Optional.of(publicClient));

        assertThatThrownBy(() -> tokenService.authenticateClient("public-client", "secret"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void getClient_withValidClientId_returnsClient() {
        when(clientRepository.findByClientId("test-client")).thenReturn(Optional.of(testClient));

        ServiceClient result = tokenService.getClient("test-client");

        assertThat(result).isEqualTo(testClient);
    }

    @Test
    void getClient_withUnknownClientId_throwsClientNotFound() {
        when(clientRepository.findByClientId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.getClient("unknown"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.CLIENT_NOT_FOUND);
                });
    }

    @Test
    void getClient_withNullClientId_throwsInvalidRequest() {
        assertThatThrownBy(() -> tokenService.getClient(null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void processTokenRequest_withDisabledClient_throwsClientDisabled() {
        ServiceClient disabledClient = testClient.withEnabled(false);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "client_credentials");

        assertThatThrownBy(() -> tokenService.processTokenRequest(disabledClient, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }
}
