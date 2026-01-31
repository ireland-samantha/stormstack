package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.CachedSession;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.TokenCache;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.TokenExchangeResponse;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.exception.LightningAuthException;
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
 * WebSocket HTTP upgrade filter that validates API tokens.
 *
 * <p>This filter runs last (lowest priority) and handles API tokens passed via:
 * <ul>
 *   <li>Query parameter: {@code ?api_token={token}}</li>
 * </ul>
 *
 * <p>API tokens are exchanged for a session JWT via the Lightning Auth service.
 * Exchange results are cached to avoid repeated calls.
 *
 * <p>If no authentication method succeeds and auth is enabled, this filter
 * rejects the upgrade with 401.
 */
@ApplicationScoped
@Priority(300) // Lowest priority - runs last
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class WebSocketApiTokenFilter implements HttpUpgradeCheck {

    private static final Logger LOG = Logger.getLogger(WebSocketApiTokenFilter.class);
    private static final String API_TOKEN_PARAM = "api_token";

    private final AuthServiceClient authServiceClient;
    private final TokenCache tokenCache;
    private final JWTParser jwtParser;
    private final WebSocketAuthResultStore authStore;
    private final boolean enabled;

    @Inject
    public WebSocketApiTokenFilter(
            LightningAuthConfig config,
            AuthServiceClient authServiceClient,
            TokenCache tokenCache,
            JWTParser jwtParser,
            WebSocketAuthResultStore authStore) {
        this.enabled = config.enabled();
        this.authServiceClient = authServiceClient;
        this.tokenCache = tokenCache;
        this.jwtParser = jwtParser;
        this.authStore = authStore;
    }

    /**
     * Constructor for testing.
     */
    WebSocketApiTokenFilter(
            boolean enabled,
            AuthServiceClient authServiceClient,
            TokenCache tokenCache,
            JWTParser jwtParser,
            WebSocketAuthResultStore authStore) {
        this.enabled = enabled;
        this.authServiceClient = authServiceClient;
        this.tokenCache = tokenCache;
        this.jwtParser = jwtParser;
        this.authStore = authStore;
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        // If auth disabled, should already be handled by previous filters
        if (!enabled) {
            return CheckResult.permitUpgrade();
        }

        String contextKey = getContextKey(context);

        // Check if already authenticated by previous filter
        if (authStore.hasAuth(contextKey)) {
            return CheckResult.permitUpgrade();
        }

        // Extract API token from query parameter
        Optional<String> apiToken = extractApiToken(context);

        if (apiToken.isEmpty()) {
            // No API token and no previous auth - reject
            LOG.debug("No authentication provided for WebSocket upgrade");
            return CheckResult.rejectUpgrade(401);
        }

        // Exchange API token for session JWT
        try {
            String sessionToken = getOrExchangeToken(apiToken.get());

            // Parse the session JWT to extract claims
            JsonWebToken jwt = jwtParser.parse(sessionToken);

            String userId = jwt.getSubject();
            String username = jwt.getClaim("username");
            if (username == null) {
                username = userId;
            }
            Set<String> scopes = extractScopes(jwt);
            String apiTokenId = jwt.getClaim("api_token_id");

            Long expiryEpochSeconds = null;
            long exp = jwt.getExpirationTime();
            if (exp > 0) {
                expiryEpochSeconds = exp;
            }

            // Create principal and store auth result, keyed by token hash for @OnOpen retrieval
            LightningPrincipal principal = new LightningPrincipal(userId, username, scopes, apiTokenId);
            WebSocketAuthResult authResult = WebSocketAuthResult.fromApiToken(principal, expiryEpochSeconds);
            String authKey = WebSocketMatchTokenFilter.getAuthKey(API_TOKEN_PARAM, apiToken.get());
            authStore.store(authKey, authResult);
            // Also store by context key for filter chain check
            authStore.store(contextKey, authResult);

            LOG.debugf("API token authenticated: user=%s, scopes=%s", username, scopes);
            return CheckResult.permitUpgrade();

        } catch (LightningAuthException e) {
            LOG.warnf("API token exchange failed: %s", e.getMessage());
            return CheckResult.rejectUpgrade(401);
        } catch (ParseException e) {
            LOG.warnf("Session JWT validation failed: %s", e.getMessage());
            return CheckResult.rejectUpgrade(401);
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during API token validation");
            return CheckResult.rejectUpgrade(500);
        }
    }

    /**
     * Extract API token from query parameter.
     */
    private Optional<String> extractApiToken(HttpUpgradeContext context) {
        String query = context.httpRequest().query();
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && API_TOKEN_PARAM.equals(kv[0]) && !kv[1].isBlank()) {
                return Optional.of(kv[1]);
            }
        }
        return Optional.empty();
    }

    /**
     * Get cached session token or exchange API token for a new one.
     */
    private String getOrExchangeToken(String apiToken) {
        // Try cache first
        Optional<CachedSession> cached = tokenCache.get(apiToken);
        if (cached.isPresent()) {
            LOG.debug("Using cached session token for API token");
            return cached.get().sessionToken();
        }

        // Exchange token via auth service
        TokenExchangeResponse response = authServiceClient.exchangeToken(apiToken);

        // Cache the result
        tokenCache.put(apiToken, response);

        return response.sessionToken();
    }

    /**
     * Extract scopes from JWT claims.
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractScopes(JsonWebToken jwt) {
        Set<String> scopes = new HashSet<>();

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

        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim instanceof String) {
            for (String part : ((String) scopeClaim).split("\\s+")) {
                if (!part.isBlank()) {
                    scopes.add(part);
                }
            }
        }

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
