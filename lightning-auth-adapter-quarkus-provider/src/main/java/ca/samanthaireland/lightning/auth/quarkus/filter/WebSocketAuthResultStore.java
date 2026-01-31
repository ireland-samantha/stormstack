package ca.samanthaireland.lightning.auth.quarkus.filter;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for WebSocket authentication results.
 *
 * <p>Stores authentication results during the HTTP upgrade process.
 * Results are stored by a context key during upgrade checks and can
 * be retrieved by connection ID after the WebSocket connection is established.
 *
 * <p>Sessions should be removed when connections close to prevent memory leaks.
 */
@ApplicationScoped
public class WebSocketAuthResultStore {

    private static final Logger LOG = Logger.getLogger(WebSocketAuthResultStore.class);

    /**
     * Map of context keys (during upgrade) and connection IDs (after upgrade) to auth results.
     */
    private final Map<String, WebSocketAuthResult> authResults = new ConcurrentHashMap<>();

    /**
     * Stores an auth result by key.
     *
     * @param key the context key or connection ID
     * @param result the auth result
     */
    public void store(String key, WebSocketAuthResult result) {
        authResults.put(key, result);
        LOG.debugf("Stored auth result for key=%s, type=%s, user=%s",
                key, result.authType(), result.principal().getUsername());
    }

    /**
     * Retrieves an auth result by key.
     *
     * @param key the context key or connection ID
     * @return the auth result if found
     */
    public Optional<WebSocketAuthResult> get(String key) {
        return Optional.ofNullable(authResults.get(key));
    }

    /**
     * Checks if an auth result exists for the key.
     *
     * @param key the context key or connection ID
     * @return true if auth exists
     */
    public boolean hasAuth(String key) {
        return authResults.containsKey(key);
    }

    /**
     * Removes an auth result.
     *
     * @param key the context key or connection ID
     * @return the removed result if it existed
     */
    public Optional<WebSocketAuthResult> remove(String key) {
        WebSocketAuthResult removed = authResults.remove(key);
        if (removed != null) {
            LOG.debugf("Removed auth result for key=%s", key);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Transfers an auth result from one key to another.
     * Used to transfer from upgrade context key to connection ID.
     *
     * @param fromKey the source key
     * @param toKey the destination key
     * @return true if transfer succeeded
     */
    public boolean transfer(String fromKey, String toKey) {
        WebSocketAuthResult result = authResults.remove(fromKey);
        if (result != null) {
            authResults.put(toKey, result);
            LOG.debugf("Transferred auth result from key=%s to key=%s", fromKey, toKey);
            return true;
        }
        return false;
    }

    /**
     * Gets the count of stored auth results.
     *
     * @return the count
     */
    public int size() {
        return authResults.size();
    }

    /**
     * Retrieves an auth result by token and transfers it to be keyed by connection ID.
     * This is the primary method for use in WebSocket @OnOpen handlers.
     *
     * <p>Looks up auth using the token hash key (same method used by filters),
     * removes it, and re-stores with connection ID as the key.
     *
     * @param tokenType the token parameter name (match_token, token, api_token)
     * @param token the token value
     * @param connectionId the WebSocket connection ID
     * @return the auth result if found
     */
    public Optional<WebSocketAuthResult> claimByToken(String tokenType, String token, String connectionId) {
        String tokenKey = WebSocketMatchTokenFilter.getAuthKey(tokenType, token);
        WebSocketAuthResult result = authResults.remove(tokenKey);
        if (result != null) {
            authResults.put(connectionId, result);
            LOG.debugf("Claimed auth result from token key=%s to connection=%s", tokenKey, connectionId);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Try to claim auth for a connection by checking all supported token types.
     * Extracts tokens from query string and attempts to claim each in order.
     * Also checks for anonymous auth stored during HTTP upgrade.
     *
     * @param query the query string from handshake
     * @param connectionId the WebSocket connection ID
     * @param path the WebSocket path (for anonymous auth lookup)
     * @return the auth result if found
     */
    public Optional<WebSocketAuthResult> claimFromQuery(String query, String connectionId, String path) {
        // Try match_token first
        if (query != null && !query.isBlank()) {
            String matchToken = extractParam(query, "match_token");
            if (matchToken != null) {
                Optional<WebSocketAuthResult> result = claimByToken("match_token", matchToken, connectionId);
                if (result.isPresent()) {
                    return result;
                }
            }

            // Try token (JWT)
            String jwtToken = extractParam(query, "token");
            if (jwtToken != null) {
                Optional<WebSocketAuthResult> result = claimByToken("token", jwtToken, connectionId);
                if (result.isPresent()) {
                    return result;
                }
            }

            // Try api_token
            String apiToken = extractParam(query, "api_token");
            if (apiToken != null) {
                Optional<WebSocketAuthResult> result = claimByToken("api_token", apiToken, connectionId);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        // Try to find anonymous auth by path prefix
        Optional<WebSocketAuthResult> anonResult = claimAnonymousAuth(path, connectionId);
        if (anonResult.isPresent()) {
            return anonResult;
        }

        return Optional.empty();
    }

    /**
     * Try to claim auth for a connection by checking all supported token types.
     * @deprecated Use {@link #claimFromQuery(String, String, String)} instead
     */
    @Deprecated
    public Optional<WebSocketAuthResult> claimFromQuery(String query, String connectionId) {
        return claimFromQuery(query, connectionId, null);
    }

    /**
     * Claim anonymous auth stored with a path-based key.
     */
    private Optional<WebSocketAuthResult> claimAnonymousAuth(String path, String connectionId) {
        if (path == null) {
            return Optional.empty();
        }

        // Look for anonymous auth entries with matching path
        String anonPrefix = "anon:" + path + ":";
        for (var entry : authResults.entrySet()) {
            if (entry.getKey().startsWith(anonPrefix)) {
                WebSocketAuthResult result = authResults.remove(entry.getKey());
                if (result != null) {
                    authResults.put(connectionId, result);
                    LOG.debugf("Claimed anonymous auth from key=%s to connection=%s", entry.getKey(), connectionId);
                    return Optional.of(result);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Extract a parameter value from a query string.
     */
    private String extractParam(String query, String paramName) {
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && paramName.equals(kv[0]) && !kv[1].isBlank()) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * Removes all expired auth results.
     *
     * @return the number of results removed
     */
    public int removeExpired() {
        int removed = 0;
        for (var entry : authResults.entrySet()) {
            if (entry.getValue().isExpired()) {
                authResults.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            LOG.debugf("Removed %d expired auth results", removed);
        }
        return removed;
    }
}
