package ca.samanthaireland.stormstack.thunder.auth.quarkus.cache;

import java.time.Instant;
import java.util.Set;

/**
 * Cached session data from a token exchange.
 *
 * @param sessionToken the session JWT
 * @param expiresAt    when the session expires
 * @param scopes       the permission scopes
 */
public record CachedSession(
        String sessionToken,
        Instant expiresAt,
        Set<String> scopes
) {

    /**
     * Check if this cached session has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
