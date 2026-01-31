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
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.AuthToken;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.ApiTokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.CreateApiTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.CreateApiTokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.auth.service.ApiTokenService;
import ca.samanthaireland.stormstack.thunder.auth.service.AuthenticationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.auth.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST resource for API token management (admin only).
 */
@Path("/api/tokens")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class ApiTokenResource {

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    AuthenticationService authenticationService;

    /**
     * List all API tokens.
     */
    @GET
    @Scopes("auth.token.read")
    public List<ApiTokenResponse> listTokens() {
        return apiTokenService.findAll().stream()
                .map(ApiTokenResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Create a new API token.
     *
     * <p>The plaintext token is only returned in this response.
     */
    @POST
    @Scopes("auth.token.create")
    public Response createToken(@Valid CreateApiTokenRequest request, @Context HttpHeaders headers) {
        AuthToken authToken = extractBearerToken(headers);

        var coreRequest = new ca.samanthaireland.stormstack.thunder.auth.service.dto.CreateApiTokenRequest(
                authToken.userId(),
                request.name(),
                request.scopes(),
                request.expiresAt()
        );

        var result = apiTokenService.createToken(coreRequest);

        return Response.created(URI.create("/api/tokens/" + result.token().id()))
                .entity(CreateApiTokenResponse.from(result.token(), result.plaintextToken()))
                .build();
    }

    /**
     * Get a token by ID.
     */
    @GET
    @Path("/{id}")
    @Scopes("auth.token.read")
    public ApiTokenResponse getToken(@PathParam("id") String id) {
        ApiTokenId tokenId = ApiTokenId.fromString(id);
        return apiTokenService.findById(tokenId)
                .map(ApiTokenResponse::from)
                .orElseThrow(() -> AuthException.apiTokenNotFound(tokenId));
    }

    /**
     * Revoke (soft-delete) a token.
     */
    @DELETE
    @Path("/{id}")
    @Scopes("auth.token.revoke")
    public Response revokeToken(@PathParam("id") String id) {
        ApiTokenId tokenId = ApiTokenId.fromString(id);
        apiTokenService.revokeToken(tokenId);
        return Response.noContent().build();
    }

    private AuthToken extractBearerToken(HttpHeaders headers) {
        String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new NotAuthorizedException("Bearer token required",
                    Response.status(Response.Status.UNAUTHORIZED).build());
        }
        return authenticationService.verifyToken(authHeader.substring(7));
    }
}
