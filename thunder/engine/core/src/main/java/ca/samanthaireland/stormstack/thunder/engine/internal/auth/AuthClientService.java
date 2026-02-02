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

package ca.samanthaireland.stormstack.thunder.engine.internal.auth;

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
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing OAuth2 service account authentication.
 *
 * <p>This service handles obtaining and caching access tokens for Thunder Engine
 * to communicate with Thunder Auth service. It uses the client_credentials grant
 * type for service-to-service authentication.
 *
 * <p>Thread Safety: This class is thread-safe. Token caching uses a lock to
 * prevent concurrent refresh attempts.
 */
public class AuthClientService {

    private static final Logger log = LoggerFactory.getLogger(AuthClientService.class);
    private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofMinutes(5);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final String authBaseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final HttpClient httpClient;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile CachedToken cachedToken;

    /**
     * Creates a new AuthClientService.
     *
     * @param authBaseUrl  the base URL of Thunder Auth (e.g., "http://localhost:8082")
     * @param clientId     the service client ID
     * @param clientSecret the service client secret
     * @param scope        the scope to request (space-separated)
     */
    public AuthClientService(String authBaseUrl, String clientId, String clientSecret, String scope) {
        this.authBaseUrl = normalizeUrl(Objects.requireNonNull(authBaseUrl, "authBaseUrl cannot be null"));
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret cannot be null");
        this.scope = scope;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();
        log.info("AuthClientService initialized for client: {} against {}", clientId, authBaseUrl);
    }

    /**
     * Creates a new AuthClientService with a custom HttpClient (for testing).
     */
    AuthClientService(String authBaseUrl, String clientId, String clientSecret, String scope, HttpClient httpClient) {
        this.authBaseUrl = normalizeUrl(Objects.requireNonNull(authBaseUrl, "authBaseUrl cannot be null"));
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret cannot be null");
        this.scope = scope;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
    }

    private static String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Gets a valid access token, refreshing if necessary.
     *
     * <p>This method returns a cached token if it's still valid (with a buffer
     * before expiry), otherwise it obtains a new token from the auth service.
     *
     * @return a valid access token
     * @throws AuthClientException if token acquisition fails
     */
    public String getAccessToken() throws AuthClientException {
        CachedToken token = cachedToken;

        if (token != null && !token.isExpiringSoon()) {
            return token.accessToken;
        }

        tokenLock.lock();
        try {
            // Double-check after acquiring lock
            token = cachedToken;
            if (token != null && !token.isExpiringSoon()) {
                return token.accessToken;
            }

            // Obtain new token
            cachedToken = fetchAccessToken();
            log.info("Obtained new access token for client: {}, expires in: {}s",
                    clientId, cachedToken.expiresIn);
            return cachedToken.accessToken;

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Forces a refresh of the access token.
     *
     * @return the new access token
     * @throws AuthClientException if token acquisition fails
     */
    public String refreshAccessToken() throws AuthClientException {
        tokenLock.lock();
        try {
            cachedToken = fetchAccessToken();
            log.info("Refreshed access token for client: {}", clientId);
            return cachedToken.accessToken;
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Clears the cached token, forcing a refresh on next access.
     */
    public void clearCache() {
        tokenLock.lock();
        try {
            cachedToken = null;
            log.debug("Cleared cached token for client: {}", clientId);
        } finally {
            tokenLock.unlock();
        }
    }

    private CachedToken fetchAccessToken() throws AuthClientException {
        StringBuilder body = new StringBuilder();
        body.append("grant_type=client_credentials");
        if (scope != null && !scope.isBlank()) {
            body.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authBaseUrl + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + encodeBasicAuth(clientId, clientSecret))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseTokenResponse(response.body());
            }

            String errorDescription = extractJsonField(response.body(), "error_description");
            String error = extractJsonField(response.body(), "error");
            throw new AuthClientException(
                    "Failed to obtain access token: " + (errorDescription != null ? errorDescription : error),
                    response.statusCode()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthClientException("Token request interrupted", e);
        } catch (IOException e) {
            throw new AuthClientException("Failed to connect to auth service: " + e.getMessage(), e);
        }
    }

    private CachedToken parseTokenResponse(String json) {
        String accessToken = extractJsonField(json, "access_token");
        String expiresInStr = extractJsonField(json, "expires_in");

        if (accessToken == null) {
            throw new AuthClientException("Invalid token response: missing access_token", 200);
        }

        int expiresIn = 3600; // Default to 1 hour
        if (expiresInStr != null) {
            try {
                expiresIn = Integer.parseInt(expiresInStr);
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        return new CachedToken(accessToken, expiresIn);
    }

    private String extractJsonField(String json, String field) {
        // Simple JSON field extraction without a full parser
        String pattern = "\"" + field + "\"";
        int fieldIndex = json.indexOf(pattern);
        if (fieldIndex == -1) return null;

        int colonIndex = json.indexOf(':', fieldIndex);
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        char startChar = json.charAt(valueStart);
        if (startChar == '"') {
            // String value
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else if (Character.isDigit(startChar) || startChar == '-') {
            // Numeric value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.')) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }

        return null;
    }

    private String encodeBasicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Cached token with expiry tracking.
     */
    private static class CachedToken {
        final String accessToken;
        final int expiresIn;
        final Instant obtainedAt;

        CachedToken(String accessToken, int expiresIn) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.obtainedAt = Instant.now();
        }

        boolean isExpiringSoon() {
            Instant expiresAt = obtainedAt.plusSeconds(expiresIn);
            return Instant.now().isAfter(expiresAt.minus(TOKEN_EXPIRY_BUFFER));
        }
    }

    /**
     * Exception thrown when authentication operations fail.
     */
    public static class AuthClientException extends RuntimeException {
        private final int statusCode;

        public AuthClientException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public AuthClientException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
