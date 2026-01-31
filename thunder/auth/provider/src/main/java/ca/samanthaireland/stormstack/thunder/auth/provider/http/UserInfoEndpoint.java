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

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.service.AuthenticationService;
import ca.samanthaireland.stormstack.thunder.auth.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OIDC UserInfo endpoint (RFC 7662).
 *
 * <p>Returns claims about the authenticated user based on the access token.
 * This replaces the legacy {@code GET /api/auth/me} endpoint.
 *
 * <p>Standard OIDC claims returned:
 * <ul>
 *   <li>sub - Subject (user ID)</li>
 *   <li>preferred_username - Username</li>
 *   <li>roles - Custom claim with user's role IDs</li>
 *   <li>scopes - Custom claim with user's permission scopes</li>
 * </ul>
 */
@Path("/oauth2/userinfo")
@Produces(MediaType.APPLICATION_JSON)
public class UserInfoEndpoint {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserService userService;

    /**
     * Get user information for the authenticated user.
     *
     * <p>Requires a valid Bearer token in the Authorization header.
     *
     * @param headers HTTP headers containing the Authorization header
     * @return user claims as a JSON object
     */
    @GET
    public Response getUserInfo(@Context HttpHeaders headers) {
        String bearerToken = extractBearerToken(headers);
        var authToken = authenticationService.verifyToken(bearerToken);

        User user = userService.findById(authToken.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.id().toString());
        claims.put("preferred_username", user.username());
        claims.put("name", user.username());

        // Include role IDs as custom claim
        claims.put("roles", user.roleIds().stream()
                .map(Object::toString)
                .toList());

        // Include scopes from the user
        claims.put("scopes", user.scopes());

        // Include enabled status
        claims.put("enabled", user.enabled());

        // Include scopes from the token (may include additional role-based scopes)
        if (authToken.scopes() != null && !authToken.scopes().isEmpty()) {
            claims.put("scope", String.join(" ", authToken.scopes()));
        }

        return Response.ok(claims).build();
    }

    /**
     * POST is also supported per OIDC spec.
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response getUserInfoPost(@Context HttpHeaders headers) {
        return getUserInfo(headers);
    }

    private String extractBearerToken(HttpHeaders headers) {
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw AuthException.invalidRequest("Bearer token required");
        }
        return authHeader.substring(7);
    }
}
