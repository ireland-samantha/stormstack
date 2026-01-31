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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.http;

import ca.samanthaireland.stormstack.thunder.controlplane.provider.config.JwtAuthConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * REST resource for proxying authentication requests to the auth service.
 *
 * <p>This endpoint allows the web panel (served from the engine or control plane)
 * to authenticate users through the control plane, which forwards requests to
 * the dedicated auth service.
 *
 * <p>Key features:
 * <ul>
 *   <li>Translates simple login requests to OAuth2 password grant</li>
 *   <li>Proxies user, role, and token management endpoints</li>
 *   <li>Provides /api/auth/me endpoint for current user info</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auth/login - User login (translated to OAuth2 password grant)</li>
 *   <li>POST /api/auth/refresh - Token refresh (translated to OAuth2 refresh_token grant)</li>
 *   <li>GET /api/auth/me - Get current user info</li>
 *   <li>* /api/auth/users/* - Proxy to auth service user management</li>
 *   <li>* /api/auth/roles/* - Proxy to auth service role management</li>
 *   <li>* /api/auth/tokens/* - Proxy to auth service API token management</li>
 * </ul>
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthProxyResource {
    private static final Logger log = LoggerFactory.getLogger(AuthProxyResource.class);
    private static final String OAUTH2_CLIENT_ID = "admin-cli"; // Public client for web panel

    private final JwtAuthConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public AuthProxyResource(JwtAuthConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
    }

    // =========================================================================
    // Login/Refresh Endpoints (OAuth2 translation)
    // =========================================================================

    /**
     * Handle user login by translating to OAuth2 password grant.
     *
     * @param loginRequest the login credentials
     * @return JWT token response
     */
    @POST
    @Path("/login")
    public Response login(LoginRequest loginRequest) {
        if (!config.useRemoteValidation()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service not configured"))
                    .build();
        }

        String authServiceUrl = config.authServiceUrl().orElseThrow();

        // Build OAuth2 password grant request
        String formBody = "grant_type=password" +
                "&client_id=" + URLEncoder.encode(OAUTH2_CLIENT_ID, StandardCharsets.UTF_8) +
                "&username=" + URLEncoder.encode(loginRequest.username(), StandardCharsets.UTF_8) +
                "&password=" + URLEncoder.encode(loginRequest.password(), StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServiceUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                OAuth2TokenResponse oauth2Response = objectMapper.readValue(
                        response.body(), OAuth2TokenResponse.class);

                // Convert OAuth2 response to simple login response expected by frontend
                LoginResponse loginResponse = new LoginResponse(
                        oauth2Response.accessToken(),
                        oauth2Response.refreshToken()
                );

                log.info("User '{}' logged in successfully", loginRequest.username());
                return Response.ok(loginResponse).build();
            } else {
                log.warn("Login failed for user '{}': status={}", loginRequest.username(), response.statusCode());
                return Response.status(response.statusCode())
                        .entity(response.body())
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to contact auth service for login", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service unavailable"))
                    .build();
        }
    }

    /**
     * Handle token refresh by translating to OAuth2 refresh_token grant.
     *
     * @param refreshRequest the refresh token
     * @return new JWT token response
     */
    @POST
    @Path("/refresh")
    public Response refresh(RefreshRequest refreshRequest) {
        if (!config.useRemoteValidation()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service not configured"))
                    .build();
        }

        String authServiceUrl = config.authServiceUrl().orElseThrow();

        // Build OAuth2 refresh_token grant request
        String formBody = "grant_type=refresh_token" +
                "&client_id=" + URLEncoder.encode(OAUTH2_CLIENT_ID, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(refreshRequest.refreshToken(), StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServiceUrl + "/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                OAuth2TokenResponse oauth2Response = objectMapper.readValue(
                        response.body(), OAuth2TokenResponse.class);

                LoginResponse loginResponse = new LoginResponse(
                        oauth2Response.accessToken(),
                        oauth2Response.refreshToken()
                );

                log.debug("Token refreshed successfully");
                return Response.ok(loginResponse).build();
            } else {
                log.warn("Token refresh failed: status={}", response.statusCode());
                return Response.status(response.statusCode())
                        .entity(response.body())
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to contact auth service for token refresh", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service unavailable"))
                    .build();
        }
    }

    /**
     * Get current user info by validating the JWT token.
     *
     * @param headers HTTP headers containing Authorization
     * @return current user info
     */
    @GET
    @Path("/me")
    public Response getCurrentUser(@Context HttpHeaders headers) {
        if (!config.useRemoteValidation()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service not configured"))
                    .build();
        }

        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Missing or invalid authorization header"))
                    .build();
        }

        String token = authHeader.substring(7);
        return proxyValidateAndGetUser(token);
    }

    // =========================================================================
    // User Management Proxy Endpoints
    // =========================================================================

    @GET
    @Path("/users")
    public Response listUsers(@Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/users", headers, null);
    }

    @POST
    @Path("/users")
    public Response createUser(@Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("POST", "/api/users", headers, body);
    }

    @GET
    @Path("/users/{id}")
    public Response getUser(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/users/" + id, headers, null);
    }

    @PUT
    @Path("/users/{id}")
    public Response updateUser(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/users/" + id, headers, body);
    }

    @DELETE
    @Path("/users/{id}")
    public Response deleteUser(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("DELETE", "/api/users/" + id, headers, null);
    }

    @PUT
    @Path("/users/{id}/roles")
    public Response updateUserRoles(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/users/" + id, headers, body);
    }

    @PUT
    @Path("/users/{id}/password")
    public Response updateUserPassword(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/users/" + id, headers, body);
    }

    @PUT
    @Path("/users/{id}/enabled")
    public Response setUserEnabled(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/users/" + id, headers, body);
    }

    // =========================================================================
    // Role Management Proxy Endpoints
    // =========================================================================

    @GET
    @Path("/roles")
    public Response listRoles(@Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/roles", headers, null);
    }

    @POST
    @Path("/roles")
    public Response createRole(@Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("POST", "/api/roles", headers, body);
    }

    @GET
    @Path("/roles/{id}")
    public Response getRole(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/roles/" + id, headers, null);
    }

    @PUT
    @Path("/roles/{id}")
    public Response updateRole(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/roles/" + id, headers, body);
    }

    @DELETE
    @Path("/roles/{id}")
    public Response deleteRole(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("DELETE", "/api/roles/" + id, headers, null);
    }

    @PUT
    @Path("/roles/{id}/description")
    public Response updateRoleDescription(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/roles/" + id, headers, body);
    }

    @PUT
    @Path("/roles/{id}/includes")
    public Response updateRoleIncludes(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/roles/" + id, headers, body);
    }

    @PUT
    @Path("/roles/{id}/scopes")
    public Response updateRoleScopes(@PathParam("id") String id, @Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("PUT", "/api/roles/" + id + "/scopes", headers, body);
    }

    @GET
    @Path("/roles/{id}/scopes/resolved")
    public Response getResolvedScopes(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/roles/" + id + "/scopes/resolved", headers, null);
    }

    // =========================================================================
    // API Token Management Proxy Endpoints
    // =========================================================================

    @GET
    @Path("/tokens")
    public Response listApiTokens(@Context HttpHeaders headers) {
        return proxyToAuth("GET", "/api/tokens", headers, null);
    }

    @POST
    @Path("/tokens")
    public Response createApiToken(@Context HttpHeaders headers, InputStream body) {
        return proxyToAuth("POST", "/api/tokens", headers, body);
    }

    @DELETE
    @Path("/tokens/{id}")
    public Response deleteApiToken(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("DELETE", "/api/tokens/" + id, headers, null);
    }

    @POST
    @Path("/tokens/{id}/revoke")
    public Response revokeApiToken(@PathParam("id") String id, @Context HttpHeaders headers) {
        return proxyToAuth("POST", "/api/tokens/" + id + "/revoke", headers, null);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Response proxyValidateAndGetUser(String token) {
        String authServiceUrl = config.authServiceUrl().orElseThrow();

        try {
            String json = objectMapper.writeValueAsString(Map.of("token", token));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServiceUrl + "/api/validate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Response.ok(response.body()).type(MediaType.APPLICATION_JSON).build();
            } else {
                return Response.status(response.statusCode())
                        .entity(response.body())
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to contact auth service for user info", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service unavailable"))
                    .build();
        }
    }

    private Response proxyToAuth(String method, String path, HttpHeaders headers, InputStream bodyStream) {
        if (!config.useRemoteValidation()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service not configured"))
                    .build();
        }

        String authServiceUrl = config.authServiceUrl().orElseThrow();

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(authServiceUrl + path))
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()));

            // Forward Authorization header
            String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }

            // Forward Content-Type header
            String contentType = headers.getHeaderString(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                requestBuilder.header("Content-Type", contentType);
            } else {
                requestBuilder.header("Content-Type", "application/json");
            }

            // Set method and body
            byte[] body = null;
            if (bodyStream != null) {
                body = bodyStream.readAllBytes();
            }

            requestBuilder.method(method, body != null
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody());

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());

            // Forward response body if present
            if (response.body() != null && !response.body().isEmpty()) {
                responseBuilder.entity(response.body()).type(MediaType.APPLICATION_JSON);
            }

            return responseBuilder.build();

        } catch (IOException | InterruptedException e) {
            log.error("Failed to proxy request to auth service: {} {}", method, path, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Auth service unavailable"))
                    .build();
        }
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public record LoginRequest(String username, String password) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String token, String refreshToken) {}

    public record RefreshRequest(String refreshToken) {}

    private record OAuth2TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") int expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope
    ) {}
}
