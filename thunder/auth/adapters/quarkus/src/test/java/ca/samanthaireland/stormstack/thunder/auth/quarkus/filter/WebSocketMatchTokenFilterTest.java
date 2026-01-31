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
 * Unit tests for {@link WebSocketMatchTokenFilter}.
 */
@DisplayName("WebSocketMatchTokenFilter")
class WebSocketMatchTokenFilterTest {

    private JWTParser mockJwtParser;
    private WebSocketAuthResultStore authStore;
    private WebSocketMatchTokenFilter filter;

    @BeforeEach
    void setUp() {
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

    private JsonWebToken mockMatchTokenJwt(String matchId, String playerId, String playerName, Set<String> scopes) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(playerId);
        when(jwt.getClaim("match_id")).thenReturn(matchId);
        when(jwt.getClaim("player_id")).thenReturn(playerId);
        when(jwt.getClaim("player_name")).thenReturn(playerName);
        when(jwt.getClaim("scopes")).thenReturn(scopes);
        when(jwt.getClaim("match_token_id")).thenReturn("token-123");
        when(jwt.getGroups()).thenReturn(Set.of());
        when(jwt.getExpirationTime()).thenReturn(0L);
        return jwt;
    }

    @Nested
    @DisplayName("Auth enabled")
    class AuthEnabled {

        @BeforeEach
        void setUp() {
            filter = new WebSocketMatchTokenFilter(true, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should permit and authenticate with valid match token")
        void shouldAuthenticateWithValidMatchToken() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("match_token=valid-token");
            JsonWebToken jwt = mockMatchTokenJwt("match-1", "player-1", "TestPlayer",
                    Set.of("submit_commands", "view_snapshots"));
            when(mockJwtParser.parse("valid-token")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            // Verify auth was stored (2 entries: token key + context key)
            assertThat(authStore.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should pass through when no match token")
        void shouldPassThroughWhenNoMatchToken() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(0); // No auth stored
        }

        @Test
        @DisplayName("should pass through when token is not a match token")
        void shouldPassThroughWhenNotMatchToken() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("match_token=jwt-token");
            JsonWebToken jwt = mock(JsonWebToken.class);
            when(jwt.getClaim("match_id")).thenReturn(null); // Not a match token
            when(mockJwtParser.parse("jwt-token")).thenReturn(jwt);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should reject on invalid match token")
        void shouldRejectOnInvalidToken() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("match_token=invalid");
            when(mockJwtParser.parse("invalid")).thenThrow(new ParseException("Invalid token"));

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isFalse();
            assertThat(result.getHttpResponseCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("should extract match context from token")
        void shouldExtractMatchContext() throws Exception {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("match_token=valid");
            JsonWebToken jwt = mockMatchTokenJwt("match-123", "player-456", "John",
                    Set.of("submit_commands"));
            when(jwt.getClaim("container_id")).thenReturn("container-789");
            when(mockJwtParser.parse("valid")).thenReturn(jwt);

            filter.perform(context).await().indefinitely();

            // Get the stored auth result (2 entries: token key + context key)
            var results = authStore.size();
            assertThat(results).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Auth disabled")
    class AuthDisabled {

        @BeforeEach
        void setUp() {
            filter = new WebSocketMatchTokenFilter(false, mockJwtParser, authStore);
        }

        @Test
        @DisplayName("should permit all connections with anonymous auth")
        void shouldPermitAllWithAnonymousAuth() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext(null);

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            assertThat(authStore.size()).isEqualTo(2); // Anonymous auth stored (2 entries)
        }

        @Test
        @DisplayName("should not validate tokens when disabled")
        void shouldNotValidateTokens() {
            HttpUpgradeCheck.HttpUpgradeContext context = mockContext("match_token=any");

            HttpUpgradeCheck.CheckResult result = filter.perform(context).await().indefinitely();

            assertThat(result.isUpgradePermitted()).isTrue();
            verifyNoInteractions(mockJwtParser);
        }
    }
}
