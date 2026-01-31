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

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.model.AuthToken;
import ca.samanthaireland.lightning.auth.provider.dto.AuthTokenResponse;
import ca.samanthaireland.lightning.auth.provider.dto.ValidateTokenRequest;
import ca.samanthaireland.lightning.auth.service.AuthenticationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.lightning.auth.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for service-to-service token validation.
 */
@Path("/api/validate")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class ValidationResource {

    @Inject
    AuthenticationService authenticationService;

    /**
     * Validate a JWT token and return its claims.
     *
     * <p>This endpoint is intended for service-to-service communication
     * where other services need to validate tokens issued by this auth service.
     *
     * @param request the token to validate
     * @return the token claims if valid
     */
    @POST
    public AuthTokenResponse validateToken(@Valid ValidateTokenRequest request) {
        AuthToken authToken = authenticationService.verifyToken(request.token());
        return AuthTokenResponse.from(authToken);
    }
}
