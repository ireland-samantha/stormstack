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


package ca.samanthaireland.lightning.engine.api.resource.adapter;

import ca.samanthaireland.lightning.engine.api.resource.adapter.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

/**
 * REST API adapter for authentication operations using OAuth2 endpoints.
 *
 * <p>Provides methods for login, token refresh, and current user retrieval
 * via standard OAuth2 endpoints.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * AuthAdapter auth = new AuthAdapter.HttpAuthAdapter("http://localhost:8082", "admin-cli");
 *
 * // Login using OAuth2 Resource Owner Password Grant
 * AuthToken token = auth.login("admin", "password");
 *
 * // Refresh token using OAuth2 Refresh Token Grant
 * AuthAdapter authWithToken = new AuthAdapter.HttpAuthAdapter(
 *     "http://localhost:8082",
 *     "admin-cli",
 *     AdapterConfig.defaults().withBearerToken(token.token())
 * );
 * AuthToken newToken = authWithToken.refresh();
 * }</pre>
 */
public interface AuthAdapter {

    /**
     * Authenticate with username and password using OAuth2 Password Grant.
     *
     * @param username the username
     * @param password the password
     * @return authentication token with JWT and user details
     * @throws IOException if authentication fails or network error occurs
     * @throws AuthenticationException if credentials are invalid
     */
    AuthToken login(String username, String password) throws IOException;

    /**
     * Refresh the current JWT token using OAuth2 Refresh Token Grant.
     *
     * <p>Requires an authenticated adapter (configured with a refresh token).</p>
     *
     * @return new authentication token
     * @throws IOException if refresh fails or network error occurs
     */
    AuthToken refresh() throws IOException;

    /**
     * Get the currently authenticated user via the OAuth2 UserInfo endpoint.
     *
     * <p>Requires an authenticated adapter (configured with a bearer token).</p>
     *
     * @return current user information
     * @throws IOException if request fails or network error occurs
     */
    CurrentUser getCurrentUser() throws IOException;

    /**
     * Authentication token response.
     *
     * @param token the JWT access token string
     * @param refreshToken the refresh token (may be null for service tokens)
     * @param username the authenticated username (may be null for service tokens)
     * @param roles the user's roles
     * @param expiresAt token expiration time (may be null)
     */
    record AuthToken(String token, String refreshToken, String username, Set<String> roles, Instant expiresAt) {
        /**
         * Creates an AuthToken without refresh token (for backward compatibility).
         */
        public AuthToken(String token, String username, Set<String> roles, Instant expiresAt) {
            this(token, null, username, roles, expiresAt);
        }
    }

    /**
     * Current user information.
     *
     * @param sub the subject identifier (user ID)
     * @param username the username
     * @param roles the user's roles
     * @param scopes the user's permission scopes
     */
    record CurrentUser(String sub, String username, Set<String> roles, Set<String> scopes) {
        /**
         * Creates a CurrentUser without sub and scopes (for backward compatibility).
         */
        public CurrentUser(String username, Set<String> roles) {
            this(null, username, roles, Set.of());
        }
    }

    /**
     * Exception thrown when authentication fails due to invalid credentials.
     */
    class AuthenticationException extends IOException {
        public AuthenticationException(String message) {
            super(message);
        }
    }

    /**
     * HTTP-based implementation of AuthAdapter using OAuth2 endpoints.
     */
    class HttpAuthAdapter implements AuthAdapter {
        private final HttpClient httpClient;
        private final String baseUrl;
        private final String clientId;
        private final AdapterConfig config;
        private String refreshToken;

        public HttpAuthAdapter(String baseUrl, String clientId) {
            this(baseUrl, clientId, AdapterConfig.defaults());
        }

        public HttpAuthAdapter(String baseUrl, String clientId, AdapterConfig config) {
            this.baseUrl = normalizeUrl(baseUrl);
            this.clientId = clientId;
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        public HttpAuthAdapter(String baseUrl, String clientId, HttpClient httpClient) {
            this.baseUrl = normalizeUrl(baseUrl);
            this.clientId = clientId;
            this.config = AdapterConfig.defaults();
            this.httpClient = httpClient;
        }

        /**
         * Backward-compatible constructor without clientId.
         * Uses "admin-cli" as the default client ID.
         */
        public HttpAuthAdapter(String baseUrl) {
            this(baseUrl, "admin-cli", AdapterConfig.defaults());
        }

        /**
         * Backward-compatible constructor without clientId.
         * Uses "admin-cli" as the default client ID.
         */
        public HttpAuthAdapter(String baseUrl, AdapterConfig config) {
            this(baseUrl, "admin-cli", config);
        }

        /**
         * Backward-compatible constructor with HttpClient but without clientId.
         * Uses "admin-cli" as the default client ID.
         */
        public HttpAuthAdapter(String baseUrl, HttpClient httpClient) {
            this(baseUrl, "admin-cli", httpClient);
        }

        private static String normalizeUrl(String url) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        private HttpRequest.Builder requestBuilder(String path) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path));
            if (config.hasAuthentication()) {
                builder.header("Authorization", "Bearer " + config.getBearerToken());
            }
            return builder;
        }

        @Override
        public AuthToken login(String username, String password) throws IOException {
            // OAuth2 Resource Owner Password Grant (RFC 6749 §4.3)
            String formBody = "grant_type=password" +
                    "&client_id=" + urlEncode(clientId) +
                    "&username=" + urlEncode(username) +
                    "&password=" + urlEncode(password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseOAuth2TokenResponse(response.body());
                } else if (response.statusCode() == 400 || response.statusCode() == 401) {
                    String error = JsonMapper.extractString(response.body(), "error");
                    String errorDescription = JsonMapper.extractString(response.body(), "error_description");
                    throw new AuthenticationException(
                            errorDescription != null ? errorDescription :
                                    "Invalid username or password (error: " + error + ")");
                }
                throw new IOException("Login failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public AuthToken refresh() throws IOException {
            if (refreshToken == null && !config.hasAuthentication()) {
                throw new IOException("Refresh token required for token refresh");
            }

            // OAuth2 Refresh Token Grant (RFC 6749 §6)
            String tokenToUse = refreshToken != null ? refreshToken : config.getBearerToken();
            String formBody = "grant_type=refresh_token" +
                    "&client_id=" + urlEncode(clientId) +
                    "&refresh_token=" + urlEncode(tokenToUse);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseOAuth2TokenResponse(response.body());
                } else if (response.statusCode() == 400 || response.statusCode() == 401) {
                    String error = JsonMapper.extractString(response.body(), "error");
                    String errorDescription = JsonMapper.extractString(response.body(), "error_description");
                    throw new AuthenticationException(
                            errorDescription != null ? errorDescription :
                                    "Token refresh failed (error: " + error + ")");
                }
                throw new IOException("Token refresh failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        @Override
        public CurrentUser getCurrentUser() throws IOException {
            if (!config.hasAuthentication()) {
                throw new IOException("Bearer token required to get current user");
            }

            // OAuth2/OIDC UserInfo endpoint
            HttpRequest request = requestBuilder("/oauth2/userinfo")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseUserInfoResponse(response.body());
                } else if (response.statusCode() == 401) {
                    throw new AuthenticationException("Not authenticated");
                }
                throw new IOException("Get current user failed with status: " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private AuthToken parseOAuth2TokenResponse(String json) {
            String accessToken = JsonMapper.extractString(json, "access_token");
            String refreshTok = JsonMapper.extractString(json, "refresh_token");
            String expiresInStr = JsonMapper.extractString(json, "expires_in");

            // Store refresh token for later use
            if (refreshTok != null) {
                this.refreshToken = refreshTok;
            }

            // Calculate expiration time
            Instant expiresAt = null;
            if (expiresInStr != null) {
                try {
                    int expiresIn = Integer.parseInt(expiresInStr);
                    expiresAt = Instant.now().plusSeconds(expiresIn);
                } catch (NumberFormatException ignored) {
                    // Ignore invalid expires_in
                }
            }

            // For OAuth2 tokens, username might not be directly available
            // Clients should call getCurrentUser() for full user info
            return new AuthToken(accessToken, refreshTok, null, Set.of(), expiresAt);
        }

        private CurrentUser parseUserInfoResponse(String json) {
            String sub = JsonMapper.extractString(json, "sub");
            String username = JsonMapper.extractString(json, "preferred_username");
            Set<String> roles = JsonMapper.extractStringSet(json, "roles");
            Set<String> scopes = JsonMapper.extractStringSet(json, "scopes");
            return new CurrentUser(sub, username, roles != null ? roles : Set.of(), scopes != null ? scopes : Set.of());
        }

        private String urlEncode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
