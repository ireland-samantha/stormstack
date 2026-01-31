package ca.samanthaireland.stormstack.thunder.auth.quarkus.client;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO from the token exchange endpoint.
 *
 * @param sessionToken the short-lived session JWT
 * @param expiresAt    when the session token expires
 * @param scopes       the permission scopes granted to this token
 */
public record TokenExchangeResponse(
        String sessionToken,
        Instant expiresAt,
        Set<String> scopes
) {
}
