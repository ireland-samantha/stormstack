/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.lightning.controlplane.provider.auth;

import ca.samanthaireland.lightning.controlplane.provider.config.JwtAuthConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP client for authenticating with the lightning-auth service using OAuth2.
 *
 * <p>Uses client credentials grant for service-to-service authentication,
 * with automatic token caching and refresh.
 */
@ApplicationScoped
public class AuthServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final JwtAuthConfig config;

    // OAuth2 token cache
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile CachedToken cachedServiceToken;

    @Inject
    public AuthServiceClient(JwtAuthConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Cached OAuth2 token with expiry tracking.
     */
    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired(int bufferSeconds) {
            return Instant.now().plusSeconds(bufferSeconds).isAfter(expiresAt);
        }
    }

    /**
     * OAuth2 token response from the auth service.
     */
    private record OAuth2TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") int expiresIn,
            @JsonProperty("scope") String scope
    ) {}

    /**
     * Result of token validation.
     */
    public sealed interface ValidationResult {
        /**
         * Successful validation result containing user info and an exchanged JWT token.
         *
         * @param userId    the user ID
         * @param username  the username
         * @param roles     the user's roles
         * @param expiresAt when the token expires
         * @param jwtToken  the JWT token (can be used for forwarding to other services)
         */
        record Success(String userId, String username, Set<String> roles, Instant expiresAt, String jwtToken) implements ValidationResult {}
        record Failure(int statusCode, String message) implements ValidationResult {}
    }

    /**
     * Checks if remote validation is configured.
     *
     * @return true if auth service URL is configured
     */
    public boolean isRemoteValidationEnabled() {
        return config.useRemoteValidation();
    }

    /**
     * Validate a JWT token via the lightning-auth service.
     *
     * @param token the JWT token to validate
     * @return validation result with token claims or failure
     */
    public ValidationResult validateToken(String token) {
        if (!isRemoteValidationEnabled()) {
            return new ValidationResult.Failure(503, "Remote auth validation not configured");
        }

        try {
            String json = objectMapper.writeValueAsString(new ValidateRequest(token));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.authServiceUrl().get() + "/api/validate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthTokenResponse dto = objectMapper.readValue(response.body(), AuthTokenResponse.class);
                return new ValidationResult.Success(
                        dto.userId(),
                        dto.username(),
                        dto.roles(),
                        dto.expiresAt(),
                        dto.token()
                );
            } else if (response.statusCode() == 401) {
                return new ValidationResult.Failure(401, "Invalid token");
            } else {
                log.warn("Auth service validation failed with status {}", response.statusCode());
                return new ValidationResult.Failure(response.statusCode(), "Token validation failed");
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to contact auth service for validation", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ValidationResult.Failure(503, "Auth service unavailable");
        }
    }

    /**
     * Result of match token issuance.
     */
    public sealed interface MatchTokenResult {
        record Success(String tokenId, String matchId, String playerId, String token, Instant expiresAt) implements MatchTokenResult {}
        record Failure(int statusCode, String message) implements MatchTokenResult {}
    }

    /**
     * Issue a match token via the lightning-auth service.
     *
     * @param matchId     the match ID
     * @param containerId the container ID
     * @param playerId    the player ID (usually "system" for deploy tokens)
     * @param playerName  the player name
     * @param scopes      permission scopes for the token
     * @return result with token or failure
     */
    public MatchTokenResult issueMatchToken(String matchId, long containerId, String playerId,
                                            String playerName, Set<String> scopes) {
        if (!isRemoteValidationEnabled()) {
            return new MatchTokenResult.Failure(503, "Remote auth service not configured");
        }

        try {
            IssueMatchTokenRequest request = new IssueMatchTokenRequest(
                    matchId,
                    String.valueOf(containerId),
                    playerId,
                    null, // no userId for system tokens
                    playerName,
                    scopes,
                    24 // 24 hours validity
            );

            String json = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.authServiceUrl().get() + "/api/match-tokens"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getServiceToken())
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                IssueMatchTokenResponse dto = objectMapper.readValue(response.body(), IssueMatchTokenResponse.class);
                return new MatchTokenResult.Success(
                        dto.id(),
                        dto.matchId(),
                        dto.playerId(),
                        dto.token(),
                        dto.expiresAt()
                );
            } else {
                log.warn("Failed to issue match token: status={}", response.statusCode());
                return new MatchTokenResult.Failure(response.statusCode(), "Failed to issue match token");
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to contact auth service for match token issuance", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new MatchTokenResult.Failure(503, "Auth service unavailable");
        }
    }

    /**
     * Get a service-to-service token for auth service calls using OAuth2 client credentials.
     *
     * <p>This method:
     * <ol>
     *   <li>Checks if a cached token exists and is still valid</li>
     *   <li>If not, requests a new token from the auth service</li>
     *   <li>Caches the new token for reuse</li>
     * </ol>
     *
     * @return the service access token
     * @throws RuntimeException if token acquisition fails
     */
    private String getServiceToken() {
        // Check if OAuth2 is configured
        if (!config.isOAuth2Configured()) {
            log.warn("OAuth2 not configured, falling back to environment variable");
            return System.getenv().getOrDefault("CONTROL_PLANE_SERVICE_TOKEN",
                    System.getenv().getOrDefault("CONTROL_PLANE_TOKEN", ""));
        }

        // Check cached token
        CachedToken cached = cachedServiceToken;
        if (cached != null && !cached.isExpired(config.oauth2TokenRefreshBufferSeconds())) {
            return cached.accessToken;
        }

        // Need to refresh - acquire lock to prevent concurrent refresh
        tokenLock.lock();
        try {
            // Double-check after acquiring lock
            cached = cachedServiceToken;
            if (cached != null && !cached.isExpired(config.oauth2TokenRefreshBufferSeconds())) {
                return cached.accessToken;
            }

            // Request new token
            OAuth2TokenResponse response = requestServiceToken();
            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn);
            cachedServiceToken = new CachedToken(response.accessToken, expiresAt);

            log.info("Obtained new service token, expires at: {}", expiresAt);
            return response.accessToken;

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Request a new service token using OAuth2 client credentials grant.
     */
    private OAuth2TokenResponse requestServiceToken() {
        String authServiceUrl = config.authServiceUrl().orElseThrow(() ->
                new IllegalStateException("Auth service URL not configured"));

        String clientId = config.oauth2ClientId();
        String clientSecret = config.oauth2ClientSecret().orElseThrow(() ->
                new IllegalStateException("OAuth2 client secret not configured"));

        // Build form-encoded body
        String body = "grant_type=client_credentials&scope=" +
                URLEncoder.encode(config.oauth2Scopes(), StandardCharsets.UTF_8);

        // Build Basic auth header
        String credentials = clientId + ":" + clientSecret;
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServiceUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth)
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), OAuth2TokenResponse.class);
            } else {
                log.error("OAuth2 token request failed: status={}, body={}",
                        response.statusCode(), response.body());
                throw new RuntimeException("Failed to obtain service token: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            log.error("Failed to request OAuth2 token", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to obtain service token: " + e.getMessage(), e);
        }
    }

    /**
     * Invalidate the cached service token, forcing a refresh on next call.
     */
    public void invalidateServiceToken() {
        tokenLock.lock();
        try {
            cachedServiceToken = null;
            log.info("Service token cache invalidated");
        } finally {
            tokenLock.unlock();
        }
    }

    // Internal DTOs
    private record ValidateRequest(String token) {}

    private record AuthTokenResponse(
            String userId,
            String username,
            Set<String> roles,
            Instant expiresAt,
            String token
    ) {}

    private record IssueMatchTokenRequest(
            String matchId,
            String containerId,
            String playerId,
            String userId,
            String playerName,
            Set<String> scopes,
            Integer validForHours
    ) {}

    private record IssueMatchTokenResponse(
            String id,
            String matchId,
            String containerId,
            String playerId,
            String userId,
            String playerName,
            Set<String> scopes,
            Instant createdAt,
            Instant expiresAt,
            String token
    ) {}
}
