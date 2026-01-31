package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.security.LightningPrincipal;

import java.util.Set;

/**
 * Result of WebSocket authentication containing the authenticated principal
 * and additional context about the authentication method.
 *
 * @param principal the authenticated principal
 * @param authType the type of authentication used
 * @param tokenExpiryEpochSeconds when the token expires (null if no expiry)
 * @param matchId the match ID (only for match token auth)
 * @param containerId the container ID (only for match token auth)
 * @param playerId the player ID (only for match token auth)
 * @param playerName the player name (only for match token auth)
 */
public record WebSocketAuthResult(
        LightningPrincipal principal,
        AuthType authType,
        Long tokenExpiryEpochSeconds,
        String matchId,
        String containerId,
        String playerId,
        String playerName
) {
    /**
     * The type of authentication used.
     */
    public enum AuthType {
        /** Authenticated via match token (player session). */
        MATCH_TOKEN,
        /** Authenticated via JWT (service/admin). */
        JWT,
        /** Authenticated via API token exchange. */
        API_TOKEN,
        /** Anonymous access (auth disabled). */
        ANONYMOUS
    }

    /**
     * Creates an anonymous auth result for when auth is disabled.
     */
    public static WebSocketAuthResult anonymous() {
        LightningPrincipal principal = new LightningPrincipal(
                "anonymous", "anonymous", Set.of("*"), null
        );
        return new WebSocketAuthResult(principal, AuthType.ANONYMOUS, null, null, null, null, null);
    }

    /**
     * Creates a JWT auth result.
     */
    public static WebSocketAuthResult fromJwt(LightningPrincipal principal, Long expiryEpochSeconds) {
        return new WebSocketAuthResult(principal, AuthType.JWT, expiryEpochSeconds, null, null, null, null);
    }

    /**
     * Creates an API token auth result.
     */
    public static WebSocketAuthResult fromApiToken(LightningPrincipal principal, Long expiryEpochSeconds) {
        return new WebSocketAuthResult(principal, AuthType.API_TOKEN, expiryEpochSeconds, null, null, null, null);
    }

    /**
     * Check if the session has a required scope.
     */
    public boolean hasScope(String scope) {
        return principal.hasScope(scope);
    }

    /**
     * Check if the session has any of the required scopes.
     */
    public boolean hasAnyScope(Set<String> scopes) {
        return principal.hasAnyScope(scopes);
    }

    /**
     * Check if the token has expired.
     */
    public boolean isExpired() {
        if (tokenExpiryEpochSeconds == null) {
            return false;
        }
        return System.currentTimeMillis() / 1000 > tokenExpiryEpochSeconds;
    }

    /**
     * Check if this is a match token authentication.
     */
    public boolean isMatchTokenAuth() {
        return authType == AuthType.MATCH_TOKEN;
    }

    /**
     * Check if this is anonymous (auth disabled).
     */
    public boolean isAnonymous() {
        return authType == AuthType.ANONYMOUS;
    }
}
