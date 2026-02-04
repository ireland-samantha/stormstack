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

package ca.samanthaireland.stormstack.thunder.engine.api.resource.adapter;

import ca.samanthaireland.stormstack.thunder.engine.api.resource.adapter.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API adapter for module token operations.
 *
 * <p>Module tokens authorize ECS modules to access components within
 * Thunder Engine containers. This adapter communicates with the Thunder Auth
 * service via OAuth2 token endpoint.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ModuleTokenAdapter tokens = new ModuleTokenAdapter.HttpModuleTokenAdapter(
 *     "http://localhost:8082",
 *     "thunder-engine",
 *     "secret"
 * );
 *
 * // Issue a token for a module
 * ModuleTokenResponse response = tokens.issueToken(
 *     new IssueModuleTokenRequest("GridMapModule", permissions, false, null)
 * );
 *
 * // Refresh with new permissions
 * ModuleTokenResponse refreshed = tokens.refreshToken(response.token(), newPermissions);
 * }</pre>
 */
public interface ModuleTokenAdapter {

    /**
     * Issues a new module token via OAuth2 token endpoint.
     *
     * @param request the token issuance request
     * @return the issued token response
     * @throws IOException if the request fails
     */
    ModuleTokenResponse issueToken(IssueModuleTokenRequest request) throws IOException;

    /**
     * Refreshes a module token with new permissions.
     *
     * @param existingToken the current module token JWT
     * @param newPermissions the updated component permissions
     * @return the new token response
     * @throws IOException if the request fails
     */
    ModuleTokenResponse refreshToken(String existingToken, Map<String, ComponentPermission> newPermissions) throws IOException;

    /**
     * Permission level for a component.
     */
    enum ComponentPermission {
        /** Full access - the module owns this component */
        OWNER,
        /** Read-only access to another module's component */
        READ,
        /** Read and write access to another module's component */
        WRITE
    }

    /**
     * Request to issue a module token.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of "moduleName.componentName" to permission level
     * @param superuser            whether this module has superuser privileges
     * @param containerId          optional container ID to scope the token
     */
    record IssueModuleTokenRequest(
            String moduleName,
            Map<String, ComponentPermission> componentPermissions,
            boolean superuser,
            String containerId
    ) {}

    /**
     * Module token response.
     *
     * @param token     the JWT token
     * @param tokenType the token type (always "Bearer")
     * @param expiresIn lifetime in seconds
     * @param scope     the granted scope
     */
    record ModuleTokenResponse(
            String token,
            String tokenType,
            int expiresIn,
            String scope
    ) {}

    /**
     * Exception thrown when module token operations fail.
     */
    class ModuleTokenException extends IOException {
        private final String errorCode;

        public ModuleTokenException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * HTTP-based implementation of ModuleTokenAdapter.
     */
    class HttpModuleTokenAdapter implements ModuleTokenAdapter {
        private static final String GRANT_TYPE = "urn:stormstack:grant-type:module-token";

        private final HttpClient httpClient;
        private final String authBaseUrl;
        private final String clientId;
        private final String clientSecret;
        private final AdapterConfig config;

        /**
         * Creates a new adapter with service account credentials.
         *
         * @param authBaseUrl  the base URL of Thunder Auth (e.g., "http://localhost:8082")
         * @param clientId     the service client ID
         * @param clientSecret the service client secret
         */
        public HttpModuleTokenAdapter(String authBaseUrl, String clientId, String clientSecret) {
            this(authBaseUrl, clientId, clientSecret, AdapterConfig.defaults());
        }

        /**
         * Creates a new adapter with custom configuration.
         *
         * @param authBaseUrl  the base URL of Thunder Auth
         * @param clientId     the service client ID
         * @param clientSecret the service client secret
         * @param config       the adapter configuration
         */
        public HttpModuleTokenAdapter(String authBaseUrl, String clientId, String clientSecret, AdapterConfig config) {
            this.authBaseUrl = normalizeUrl(authBaseUrl);
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .build();
        }

        private static String normalizeUrl(String url) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        @Override
        public ModuleTokenResponse issueToken(IssueModuleTokenRequest request) throws IOException {
            // Build form-urlencoded body
            StringBuilder body = new StringBuilder();
            body.append("grant_type=").append(encode(GRANT_TYPE));
            body.append("&module_name=").append(encode(request.moduleName()));
            body.append("&component_permissions=").append(encode(serializePermissions(request.componentPermissions())));
            body.append("&superuser=").append(request.superuser());
            if (request.containerId() != null) {
                body.append("&container_id=").append(encode(request.containerId()));
            }

            return executeTokenRequest(body.toString());
        }

        @Override
        public ModuleTokenResponse refreshToken(String existingToken, Map<String, ComponentPermission> newPermissions) throws IOException {
            // For refresh, we need to decode the existing token to get module info
            // Then issue a new token with the same module name but new permissions
            // The auth service handles this via the normal module_token grant

            // Parse module name from existing token (simplified - in production use JWT library)
            // For now, we require the caller to provide the module name separately
            // or use a dedicated refresh endpoint

            throw new UnsupportedOperationException(
                    "Use issueToken with the module name and new permissions instead. " +
                    "The existing token can be decoded client-side to extract module info.");
        }

        private ModuleTokenResponse executeTokenRequest(String body) throws IOException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authBaseUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + encodeBasicAuth(clientId, clientSecret))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseTokenResponse(response.body());
                }

                throw handleErrorResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }

        private ModuleTokenResponse parseTokenResponse(String json) {
            String accessToken = JsonMapper.extractString(json, "access_token");
            String tokenType = JsonMapper.extractString(json, "token_type");
            int expiresIn = (int) JsonMapper.extractLong(json, "expires_in");
            String scope = JsonMapper.extractString(json, "scope");

            return new ModuleTokenResponse(accessToken, tokenType, expiresIn, scope);
        }

        private IOException handleErrorResponse(HttpResponse<String> response) {
            String error = JsonMapper.extractString(response.body(), "error");
            String errorDescription = JsonMapper.extractString(response.body(), "error_description");
            String message = errorDescription != null ? errorDescription : "Request failed with status: " + response.statusCode();
            return new ModuleTokenException(message, error != null ? error : "UNKNOWN");
        }

        private String serializePermissions(Map<String, ComponentPermission> permissions) {
            // Convert to JSON format: {"key": "value", ...}
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, ComponentPermission> entry : permissions.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":");
                json.append("\"").append(entry.getValue().name().toLowerCase()).append("\"");
                first = false;
            }
            json.append("}");
            return json.toString();
        }

        private String escapeJson(String value) {
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        private String encodeBasicAuth(String username, String password) {
            String credentials = username + ":" + password;
            return java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
    }
}
