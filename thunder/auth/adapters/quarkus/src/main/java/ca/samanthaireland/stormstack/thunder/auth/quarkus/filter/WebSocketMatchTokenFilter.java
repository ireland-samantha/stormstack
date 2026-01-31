package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningPrincipal;
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
 * WebSocket HTTP upgrade filter that validates match tokens (player session tokens).
 *
 * <p>This filter runs first (highest priority) and validates match tokens passed via:
 * <ul>
 *   <li>Query parameter: {@code ?match_token={token}}</li>
 * </ul>
 *
 * <p>Match tokens are JWTs containing:
 * <ul>
 *   <li>{@code match_id} - the match this token grants access to</li>
 *   <li>{@code container_id} - the container hosting the match (optional)</li>
 *   <li>{@code player_id} - the player ID</li>
 *   <li>{@code player_name} - display name for the player</li>
 *   <li>{@code scopes} - permission scopes (submit_commands, view_snapshots, receive_errors)</li>
 * </ul>
 *
 * <p>On successful validation, stores a {@link WebSocketAuthResult} in the shared store.
 */
@ApplicationScoped
@Priority(100) // Highest priority - runs first
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class WebSocketMatchTokenFilter implements HttpUpgradeCheck {

    private static final Logger LOG = Logger.getLogger(WebSocketMatchTokenFilter.class);
    private static final String MATCH_TOKEN_PARAM = "match_token";

    // Match token JWT claims
    private static final String CLAIM_MATCH_ID = "match_id";
    private static final String CLAIM_CONTAINER_ID = "container_id";
    private static final String CLAIM_PLAYER_ID = "player_id";
    private static final String CLAIM_PLAYER_NAME = "player_name";
    private static final String CLAIM_SCOPES = "scopes";
    private static final String CLAIM_TOKEN_ID = "match_token_id";

    private final JWTParser jwtParser;
    private final WebSocketAuthResultStore authStore;
    private final boolean enabled;

    @Inject
    public WebSocketMatchTokenFilter(
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
    WebSocketMatchTokenFilter(boolean enabled, JWTParser jwtParser, WebSocketAuthResultStore authStore) {
        this.enabled = enabled;
        this.jwtParser = jwtParser;
        this.authStore = authStore;
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        // If auth disabled, create anonymous session and permit
        if (!enabled) {
            LOG.debug("Auth disabled, allowing anonymous WebSocket connection");
            storeAnonymousAuth(context);
            return CheckResult.permitUpgrade();
        }

        // Check if already authenticated by previous filter
        if (authStore.hasAuth(getContextKey(context))) {
            return CheckResult.permitUpgrade();
        }

        // Extract match token from query parameter
        Optional<String> token = extractMatchToken(context);
        if (token.isEmpty()) {
            // No match token - let next filter handle it
            return CheckResult.permitUpgrade();
        }

        // Validate the match token JWT
        try {
            JsonWebToken jwt = jwtParser.parse(token.get());

            // Verify this is a match token (has match_id claim)
            String matchId = jwt.getClaim(CLAIM_MATCH_ID);
            if (matchId == null) {
                LOG.debug("Token is not a match token (no match_id claim), passing to next filter");
                return CheckResult.permitUpgrade();
            }

            // Extract match token claims
            String containerId = jwt.getClaim(CLAIM_CONTAINER_ID);
            String playerId = jwt.getClaim(CLAIM_PLAYER_ID);
            String playerName = jwt.getClaim(CLAIM_PLAYER_NAME);
            String tokenId = jwt.getClaim(CLAIM_TOKEN_ID);
            Set<String> scopes = extractScopes(jwt);

            // Extract expiry
            Long expiryEpochSeconds = null;
            long exp = jwt.getExpirationTime();
            if (exp > 0) {
                expiryEpochSeconds = exp;
            }

            // Create principal with match context
            LightningPrincipal principal = new LightningPrincipal(
                    playerId != null ? playerId : jwt.getSubject(),
                    playerName != null ? playerName : playerId,
                    scopes,
                    tokenId
            );

            // Store auth result with match context, keyed by token hash for @OnOpen retrieval
            WebSocketAuthResult authResult = new WebSocketAuthResult(
                    principal,
                    WebSocketAuthResult.AuthType.MATCH_TOKEN,
                    expiryEpochSeconds,
                    matchId,
                    containerId,
                    playerId,
                    playerName
            );
            String authKey = getAuthKey(MATCH_TOKEN_PARAM, token.get());
            authStore.store(authKey, authResult);
            // Also store by context key for filter chain check
            authStore.store(getContextKey(context), authResult);

            LOG.debugf("Match token authenticated: player=%s, match=%s, scopes=%s",
                    playerName, matchId, scopes);
            return CheckResult.permitUpgrade();

        } catch (ParseException e) {
            LOG.warnf("Match token validation failed: %s", e.getMessage());
            return CheckResult.rejectUpgrade(401);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during match token validation");
            return CheckResult.rejectUpgrade(500);
        }
    }

    /**
     * Extract match token from query parameter.
     */
    private Optional<String> extractMatchToken(HttpUpgradeContext context) {
        String query = context.httpRequest().query();
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && MATCH_TOKEN_PARAM.equals(kv[0]) && !kv[1].isBlank()) {
                return Optional.of(kv[1]);
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

        Object scopesClaim = jwt.getClaim(CLAIM_SCOPES);
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

        // Also include groups
        Set<String> groups = jwt.getGroups();
        if (groups != null) {
            scopes.addAll(groups);
        }

        return scopes;
    }

    private void storeAnonymousAuth(HttpUpgradeContext context) {
        LightningPrincipal anonymous = new LightningPrincipal(
                "anonymous", "anonymous", Set.of("*"), null
        );
        WebSocketAuthResult authResult = new WebSocketAuthResult(
                anonymous, WebSocketAuthResult.AuthType.ANONYMOUS, null, null, null, null, null
        );
        // Store with path-based key for anonymous connections (no token to key by)
        String path = context.httpRequest().path();
        authStore.store("anon:" + path + ":" + System.identityHashCode(context), authResult);
        authStore.store(getContextKey(context), authResult);
    }

    /**
     * Generate a key for storing auth results that can be reconstructed in @OnOpen.
     * Uses a hash of the token to avoid storing raw tokens as keys.
     *
     * @param tokenType the type of token (match_token, token, api_token)
     * @param token the token value
     * @return a key for storing the auth result
     */
    static String getAuthKey(String tokenType, String token) {
        return tokenType + ":" + Integer.toHexString(token.hashCode());
    }

    private String getContextKey(HttpUpgradeContext context) {
        return context.httpRequest().path() + ":" + System.identityHashCode(context);
    }
}
