package ca.samanthaireland.stormstack.thunder.auth.quarkus.client;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.exception.LightningAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private AuthServiceClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new AuthServiceClient(
                httpClient,
                objectMapper,
                "http://localhost:8082",
                Duration.ofSeconds(10)
        );
    }

    @Test
    void exchangeToken_withValidToken_returnsResponse() throws Exception {
        // OAuth2 token exchange response format (RFC 8693)
        String responseBody = """
                {
                    "access_token": "session.jwt.token",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "scope": "read write",
                    "issued_token_type": "urn:ietf:params:oauth:token-type:access_token"
                }
                """;

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);

        TokenExchangeResponse response = client.exchangeToken("lat_test_token");

        assertThat(response.sessionToken()).isEqualTo("session.jwt.token");
        assertThat(response.scopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void exchangeToken_with401_throwsInvalidTokenException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(401);

        assertThatThrownBy(() -> client.exchangeToken("lat_invalid_token"))
                .isInstanceOf(LightningAuthException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_API_TOKEN")
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void exchangeToken_with403_throwsTokenRevokedException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(403);

        assertThatThrownBy(() -> client.exchangeToken("lat_revoked_token"))
                .isInstanceOf(LightningAuthException.class)
                .hasFieldOrPropertyWithValue("code", "TOKEN_REVOKED")
                .hasMessageContaining("revoked");
    }

    @Test
    void exchangeToken_with500_throwsServiceErrorException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);

        assertThatThrownBy(() -> client.exchangeToken("lat_test_token"))
                .isInstanceOf(LightningAuthException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_SERVICE_ERROR")
                .hasMessageContaining("status 500");
    }

    @Test
    void exchangeToken_withConnectionError_throwsServiceUnavailableException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> client.exchangeToken("lat_test_token"))
                .isInstanceOf(LightningAuthException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_SERVICE_UNAVAILABLE")
                .hasMessageContaining("Failed to communicate");
    }
}
