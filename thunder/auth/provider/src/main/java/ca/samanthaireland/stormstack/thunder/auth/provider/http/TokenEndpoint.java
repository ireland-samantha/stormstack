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
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.OAuth2ErrorResponse;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.OAuth2TokenResponseDto;
import ca.samanthaireland.stormstack.thunder.auth.service.OAuth2TokenService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 Token Endpoint (RFC 6749 Section 3.2).
 *
 * <p>Handles token requests for all supported grant types:
 * <ul>
 *   <li>client_credentials - service-to-service authentication</li>
 *   <li>password - resource owner password credentials</li>
 *   <li>refresh_token - refresh token exchange</li>
 *   <li>urn:ietf:params:oauth:grant-type:token-exchange - token exchange (RFC 8693)</li>
 * </ul>
 *
 * <p>Per OAuth2 spec, this endpoint accepts form-encoded POST requests.
 */
@Path("/oauth2/token")
@Produces(MediaType.APPLICATION_JSON)
public class TokenEndpoint {

    private static final Logger log = Logger.getLogger(TokenEndpoint.class);

    @Inject
    OAuth2TokenService tokenService;

    /**
     * Process a token request.
     *
     * <p>Accepts application/x-www-form-urlencoded per OAuth2 spec.
     *
     * @param form    the form parameters
     * @param headers HTTP headers (for Basic auth)
     * @return token response or error
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response token(MultivaluedMap<String, String> form, @Context HttpHeaders headers) {
        try {
            // Convert form to simple map
            Map<String, String> parameters = new HashMap<>();
            for (Map.Entry<String, java.util.List<String>> entry : form.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    parameters.put(entry.getKey(), entry.getValue().get(0));
                }
            }

            // Try to extract client credentials from HTTP Basic auth header
            ServiceClient authenticatedClient = extractBasicAuthClient(headers, parameters);

            OAuth2TokenResponse response;
            if (authenticatedClient != null) {
                // Client was authenticated via Basic auth
                response = tokenService.processTokenRequest(authenticatedClient, parameters);
            } else {
                // Let the service handle client authentication from form parameters
                response = tokenService.processTokenRequest(parameters);
            }

            log.infof("Token issued for grant_type=%s", parameters.get("grant_type"));
            return Response.ok(OAuth2TokenResponseDto.from(response)).build();

        } catch (AuthException e) {
            log.warnf("Token request failed: %s - %s", e.getErrorCode(), e.getMessage());
            return buildErrorResponse(e);
        } catch (Exception e) {
            log.errorf(e, "Unexpected error in token endpoint");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(OAuth2ErrorResponse.of("server_error", "Internal server error"))
                    .build();
        }
    }

    /**
     * Extract client credentials from HTTP Basic auth header.
     *
     * <p>If present, authenticates the client and returns it.
     * If not present, returns null to let form-based auth proceed.
     *
     * @param headers    HTTP headers
     * @param parameters request parameters (for adding client_id if needed)
     * @return authenticated client or null
     */
    private ServiceClient extractBasicAuthClient(HttpHeaders headers, Map<String, String> parameters) {
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return null;
        }

        try {
            String credentials = new String(
                    Base64.getDecoder().decode(authHeader.substring(6)),
                    StandardCharsets.UTF_8
            );

            int colonIndex = credentials.indexOf(':');
            if (colonIndex < 0) {
                throw AuthException.invalidClient("Invalid Basic auth credentials format");
            }

            String clientId = credentials.substring(0, colonIndex);
            String clientSecret = credentials.substring(colonIndex + 1);

            // Validate client_id matches form parameter if provided
            String formClientId = parameters.get("client_id");
            if (formClientId != null && !formClientId.equals(clientId)) {
                throw AuthException.invalidClient(
                        "client_id in form does not match Basic auth client_id");
            }

            // Add client_id to parameters for downstream processing
            parameters.put("client_id", clientId);

            return tokenService.authenticateClient(clientId, clientSecret);

        } catch (IllegalArgumentException e) {
            throw AuthException.invalidClient("Invalid Basic auth encoding");
        }
    }

    /**
     * Build an OAuth2 error response with appropriate status code.
     *
     * @param e the exception
     * @return the response
     */
    private Response buildErrorResponse(AuthException e) {
        Response.Status status = switch (e.getErrorCode()) {
            case INVALID_CLIENT, CLIENT_NOT_FOUND -> Response.Status.UNAUTHORIZED;
            case UNAUTHORIZED_CLIENT -> Response.Status.FORBIDDEN;
            default -> Response.Status.BAD_REQUEST;
        };

        // Per RFC 6749 Section 5.2, invalid_client errors should return 401
        // with WWW-Authenticate header
        if (status == Response.Status.UNAUTHORIZED) {
            return Response.status(status)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"oauth2\"")
                    .entity(OAuth2ErrorResponse.from(e))
                    .build();
        }

        return Response.status(status)
                .entity(OAuth2ErrorResponse.from(e))
                .build();
    }
}
