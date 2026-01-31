package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.TokenCache;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.TokenExchangeResponse;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.exception.LightningAuthException;
import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.http.HttpServerRequest;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebSocketApiTokenFilter}.
 */
@DisplayName("WebSocketApiTokenFilter")
class WebSocketApiTokenFilterTest {

    private AuthServiceClient mockAuthClient;
    private TokenCache mockTokenCache;
    private JWTParser mockJwtParser;
    private WebSocketAuthResultStore authStore;
    private WebSocketApiTokenFilter filter;

    @BeforeEach
    void setUp() {
        mockAuthClient = mock(AuthServiceClient.class);
        mockTokenCache = mock(TokenCache.class);
        mockJwtParser = mock(JWTParser.class);
        authStore = new WebSocketAuthResultStore();
    }

    private HttpUpgradeCheck.HttpUpgradeContext mockContext(String query) {
        HttpUpgradeCheck.HttpUpgradeContext context = mock(HttpUpgradeCheck.HttpUpgradeContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.httpRequest()).thenReturn(request);
        when(request.query()).thenReturn(query);
        when(request.path()).thenReturn("/ws/test");
        return context;
    }

    private JsonWebToken mockJwt(String subject, String username, Set<String> scopes) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaim("username")).thenReturn(username);
        when(jwt.getClaim("scopes")).thenReturn(scopes);
        when(jwt.getClaim("api_token_id")).thenReturn("api-token-123");
        when(jwt.getGroups()).thenReturn(Set.of());
        when(jwt.getExpirationTime()).thenReturn(0L);
        return jwt;
    }

    @Nested
    @DisplayName("Auth enabled")
    class AuthEnabled {

        @BeforeEach
        void setUp() {
            filter = new WebSocketApiTokenFilter(true, mockAuthClient, mockTokenCache, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should authenticate with valid API token")
        void shouldAuthenticateWithValidApiToken() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=my-api-token");
            when(mockTokenCache.get("my-api-token")).thenReturn(Optional.empty());

            TokenExchangeResponse exchangeResponse = new TokenExchangeResponse(
                    "session-jwt", Instant.now().plusSeconds(3600), Set.of("engine.*")
            );
            when(mockAuthClient.exchangeToken("my-api-token")).thenReturn(exchangeResponse);

            JsonWebToken jwt = mockJwt("user-123", "testuser", Set.of("engine.*"));
            when(mockJwtParser.parse("session-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(2); // token key + context key
            verify(mockTokenCache).put(eq("my-api-token"), any());
        }

        @Test
        @DisplayName("should use cached session token")
        void shouldUseCachedSessionToken() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=cached-token");

            var cachedSession = new ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.CachedSession(
                    "cached-jwt", Instant.now().plusSeconds(3600), Set.of("engine.*")
            );
            when(mockTokenCache.get("cached-token")).thenReturn(Optional.of(cachedSession));

            JsonWebToken jwt = mockJwt("user-123", "testuser", Set.of("engine.*"));
            when(mockJwtParser.parse("cached-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verify(mockAuthClient, never()).exchangeToken(any());
        }

        @Test
        @DisplayName("should reject when no auth and no API token")
        void shouldRejectWhenNoAuth() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isFalse();
            assertThat(result.getHttpResponseCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("should pass through when already authenticated")
        void shouldPassThroughWhenAlreadyAuthenticated() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=any");
            // Pre-store auth result
            String contextKey = context.httpRequest().path() + ":" + System.identityHashCode(context);
            authStore.store(contextKey, WebSocketAuthResult.anonymous());

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verifyNoInteractions(mockAuthClient);
        }

        @Test
        @DisplayName("should reject on token exchange failure")
        void shouldRejectOnExchangeFailure() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=bad-token");
            when(mockTokenCache.get("bad-token")).thenReturn(Optional.empty());
            when(mockAuthClient.exchangeToken("bad-token"))
                    .thenThrow(new LightningAuthException("INVALID_TOKEN", "Invalid API token"));

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isFalse();
            assertThat(result.getHttpResponseCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("should reject on JWT parse failure")
        void shouldRejectOnJwtParseFailure() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=my-token");
            when(mockTokenCache.get("my-token")).thenReturn(Optional.empty());

            TokenExchangeResponse exchangeResponse = new TokenExchangeResponse(
                    "invalid-jwt", Instant.now().plusSeconds(3600), Set.of("engine.*")
            );
            when(mockAuthClient.exchangeToken("my-token")).thenReturn(exchangeResponse);
            when(mockJwtParser.parse("invalid-jwt")).thenThrow(new ParseException("Invalid JWT"));

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isFalse();
            assertThat(result.getHttpResponseCode()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("Auth disabled")
    class AuthDisabled {

        @BeforeEach
        void setUp() {
            filter = new WebSocketApiTokenFilter(false, mockAuthClient, mockTokenCache, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should permit without validation")
        void shouldPermitWithoutValidation() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("api_token=any");

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verifyNoInteractions(mockAuthClient);
            verifyNoInteractions(mockJwtParser);
        }
    }
}
