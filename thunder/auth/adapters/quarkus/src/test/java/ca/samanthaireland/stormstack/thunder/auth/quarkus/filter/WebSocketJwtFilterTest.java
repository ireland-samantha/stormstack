package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.vertx.core.http.HttpServerRequest;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebSocketJwtFilter}.
 */
@DisplayName("WebSocketJwtFilter")
class WebSocketJwtFilterTest {

    private JWTParser mockJwtParser;
    private WebSocketAuthResultStore authStore;
    private WebSocketJwtFilter filter;

    @BeforeEach
    void setUp() {
        mockJwtParser = mock(JWTParser.class);
        authStore = new WebSocketAuthResultStore();
    }

    private HttpUpgradeCheck.HttpUpgradeContext mockContext(String subprotocol, String query) {
        HttpUpgradeCheck.HttpUpgradeContext context = mock(HttpUpgradeCheck.HttpUpgradeContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.httpRequest()).thenReturn(request);
        when(request.getHeader("Sec-WebSocket-Protocol")).thenReturn(subprotocol);
        when(request.query()).thenReturn(query);
        when(request.path()).thenReturn("/ws/test");
        return context;
    }

    private JsonWebToken mockJwt(String subject, String username, Set<String> scopes) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaim("username")).thenReturn(username);
        when(jwt.getClaim("scopes")).thenReturn(scopes);
        when(jwt.getClaim("match_id")).thenReturn(null); // Not a match token
        when(jwt.getClaim("api_token_id")).thenReturn(null);
        when(jwt.getGroups()).thenReturn(Set.of());
        when(jwt.getExpirationTime()).thenReturn(0L);
        return jwt;
    }

    @Nested
    @DisplayName("Auth enabled")
    class AuthEnabled {

        @BeforeEach
        void setUp() {
            filter = new WebSocketJwtFilter(true, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should authenticate with valid JWT in subprotocol")
        void shouldAuthenticateWithSubprotocolJwt() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("Bearer.valid-jwt", null);
            JsonWebToken jwt = mockJwt("user-123", "testuser", Set.of("engine.command.submit"));
            when(mockJwtParser.parse("valid-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(2); // token key + context key
        }

        @Test
        @DisplayName("should authenticate with valid JWT in query param")
        void shouldAuthenticateWithQueryParamJwt() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, "token=valid-jwt");
            JsonWebToken jwt = mockJwt("user-123", "testuser", Set.of("engine.snapshot.read"));
            when(mockJwtParser.parse("valid-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(2); // token key + context key
        }

        @Test
        @DisplayName("should prefer subprotocol over query param")
        void shouldPreferSubprotocolOverQueryParam() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("Bearer.subprotocol-jwt", "token=query-jwt");
            JsonWebToken jwt = mockJwt("user-123", "testuser", Set.of("engine.*"));
            when(mockJwtParser.parse("subprotocol-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verify(mockJwtParser).parse("subprotocol-jwt");
            verify(mockJwtParser, never()).parse("query-jwt");
        }

        @Test
        @DisplayName("should pass through when no JWT")
        void shouldPassThroughWhenNoJwt() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, null);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should pass through when already authenticated")
        void shouldPassThroughWhenAlreadyAuthenticated() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, "token=jwt");
            // Pre-store auth result
            String contextKey = context.httpRequest().path() + ":" + System.identityHashCode(context);
            authStore.store(contextKey, WebSocketAuthResult.anonymous());

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verifyNoInteractions(mockJwtParser);
        }

        @Test
        @DisplayName("should skip match tokens")
        void shouldSkipMatchTokens() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, "token=match-jwt");
            JsonWebToken jwt = mock(JsonWebToken.class);
            when(jwt.getClaim("match_id")).thenReturn("match-123"); // This is a match token
            when(mockJwtParser.parse("match-jwt")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(0); // No auth stored
        }

        @Test
        @DisplayName("should reject on invalid JWT")
        void shouldRejectOnInvalidJwt() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, "token=invalid");
            when(mockJwtParser.parse("invalid")).thenThrow(new ParseException("Invalid token"));

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
            filter = new WebSocketJwtFilter(false, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should permit without validation")
        void shouldPermitWithoutValidation() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null, "token=any");

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verifyNoInteractions(mockJwtParser);
        }
    }
}
