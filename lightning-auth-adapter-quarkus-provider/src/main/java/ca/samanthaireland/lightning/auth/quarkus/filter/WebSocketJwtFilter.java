package ca.samanthaireland.lightning.auth.quarkus.filter;

import ca.samanthaireland.lightning.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.lightning.auth.quarkus.security.LightningPrincipal;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * WebSocket HTTP upgrade filter that validates JWT tokens.
 *
 * <p>This filter runs second (after match token filter) and validates JWTs passed via:
 * <ul>
 *   <li>Subprotocol: {@code Sec-WebSocket-Protocol: Bearer.{token}}</li>
 *   <li>Query parameter: {@code ?token={token}}</li>
 * </ul>
 *
 * <p>JWTs should contain:
 * <ul>
 *   <li>{@code sub} - user ID</li>
 *   <li>{@code username} - username (optional, falls back to sub)</li>
 *   <li>{@code scopes} or {@code scope} - permission scopes</li>
 *   <li>{@code api_token_id} - if issued via API token (optional)</li>
 * </ul>
 */
@ApplicationScoped
@Priority(200) // Second priority - runs after match token filter
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class WebSocketJwtFilter implements HttpUpgradeCheck {

    private static final Logger LOG = Logger.getLogger(WebSocketJwtFilter.class);
    private static final String BEARER_SUBPROTOCOL_PREFIX = "Bearer.";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String TOKEN_PARAM = "token";

    private final JWTParser jwtParser;
    private final WebSocketAuthResultStore authStore;
    private final boolean enabled;

    @Inject
    public WebSocketJwtFilter(
            LightningAuthConfig config,
            JWTParser jwtParser,
            WebSocketAuthResultStore authStore) {
        this.enabled = config.enabled();
        this.jwtParser = jwtParser;
        this.authStore = authStore;
    }

    /**
     * Constructor for testing.
     */
    WebSocketJwtFilter(boolean enabled, JWTParser jwtParser, WebSocketAuthResultStore authStore) {
        this.enabled = enabled;
        this.jwtParser = jwtParser;
        this.authStore = authStore;
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        // If auth disabled, should already be handled by match token filter
        if (!enabled) {
            return CheckResult.permitUpgrade();
        }

        // Check if already authenticated by previous filter
        String contextKey = getContextKey(context);
        if (authStore.hasAuth(contextKey)) {
            return CheckResult.permitUpgrade();
        }

        // Extract JWT from subprotocol or query parameter
        Optional<String> token = extractJwtToken(context);
        if (token.isEmpty()) {
            // No JWT - let next filter handle it
            return CheckResult.permitUpgrade();
        }

        // Validate the JWT
        try {
            JsonWebToken jwt = jwtParser.parse(token.get());

            // Skip if this is a match token (has match_id claim)
            if (jwt.getClaim("match_id") != null) {
                LOG.debug("Token is a match token, should have been handled by match token filter");
                return CheckResult.permitUpgrade();
            }

            // Extract claims
            String userId = jwt.getSubject();
            String username = jwt.getClaim("username");
            if (username == null) {
                username = userId;
            }
            Set<String> scopes = extractScopes(jwt);
            String apiTokenId = jwt.getClaim("api_token_id");

            // Extract expiry
            Long expiryEpochSeconds = null;
            long exp = jwt.getExpirationTime();
            if (exp > 0) {
                expiryEpochSeconds = exp;
            }

            // Create principal
            LightningPrincipal principal = new LightningPrincipal(userId, username, scopes, apiTokenId);

            // Store auth result, keyed by token hash for @OnOpen retrieval
            WebSocketAuthResult authResult = WebSocketAuthResult.fromJwt(principal, expiryEpochSeconds);
            String authKey = WebSocketMatchTokenFilter.getAuthKey(TOKEN_PARAM, token.get());
            authStore.store(authKey, authResult);
            // Also store by context key for filter chain check
            authStore.store(contextKey, authResult);

            LOG.debugf("JWT authenticated: user=%s, scopes=%s", username, scopes);
            return CheckResult.permitUpgrade();

        } catch (ParseException e) {
            LOG.warnf("JWT validation failed: %s", e.getMessage());
            return CheckResult.rejectUpgrade(401);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during JWT validation");
            return CheckResult.rejectUpgrade(500);
        }
    }

    /**
     * Extract JWT from subprotocol header or query parameter.
     */
    private Optional<String> extractJwtToken(HttpUpgradeContext context) {
        // Try subprotocol header: Sec-WebSocket-Protocol: Bearer.{token}
        String subprotocol = context.httpRequest().getHeader(SEC_WEBSOCKET_PROTOCOL);
        if (subprotocol != null && subprotocol.startsWith(BEARER_SUBPROTOCOL_PREFIX)) {
            String token = subprotocol.substring(BEARER_SUBPROTOCOL_PREFIX.length());
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }

        // Fall back to query parameter: ?token={token}
        String query = context.httpRequest().query();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && TOKEN_PARAM.equals(kv[0]) && !kv[1].isBlank()) {
                    return Optional.of(kv[1]);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Extract scopes from JWT claims.
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractScopes(JsonWebToken jwt) {
        Set<String> scopes = new HashSet<>();

        // Try "scopes" claim (array or space-separated string)
        Object scopesClaim = jwt.getClaim("scopes");
        if (scopesClaim instanceof Iterable<?>) {
            for (Object scope : (Iterable<?>) scopesClaim) {
                scopes.add(scope.toString());
            }
        } else if (scopesClaim instanceof String) {
            for (String part : ((String) scopesClaim).split("\\s+")) {
                if (!part.isBlank()) {
                    scopes.add(part);
                }
            }
        }

        // Also check "scope" claim (singular, OAuth2 style)
        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim instanceof String) {
            for (String part : ((String) scopeClaim).split("\\s+")) {
                if (!part.isBlank()) {
                    scopes.add(part);
                }
            }
        }

        // Include groups as scopes (MicroProfile pattern)
        Set<String> groups = jwt.getGroups();
        if (groups != null) {
            scopes.addAll(groups);
        }

        return scopes;
    }

    private String getContextKey(HttpUpgradeContext context) {
        return context.httpRequest().path() + ":" + System.identityHashCode(context);
    }
}
