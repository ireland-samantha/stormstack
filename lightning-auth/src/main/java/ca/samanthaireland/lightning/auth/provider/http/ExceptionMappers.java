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

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.provider.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps domain exceptions to appropriate HTTP responses.
 */
@Provider
public class ExceptionMappers implements ExceptionMapper<AuthException> {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMappers.class);

    @Override
    public Response toResponse(AuthException ex) {
        Response.Status status = mapToHttpStatus(ex);

        log.warn("Auth error: {} - {} (HTTP {})",
                ex.getErrorCode(),
                ex.getMessage(),
                status.getStatusCode());

        String code = ex.getErrorCode() != null
                ? ex.getErrorCode().name()
                : "AUTH_ERROR";

        return Response.status(status)
                .entity(ErrorResponse.of(code, ex.getMessage()))
                .build();
    }

    private Response.Status mapToHttpStatus(AuthException ex) {
        if (ex.getErrorCode() == null) {
            return Response.Status.INTERNAL_SERVER_ERROR;
        }

        return switch (ex.getErrorCode()) {
            // 401 Unauthorized - authentication failed
            case INVALID_CREDENTIALS, INVALID_TOKEN, EXPIRED_TOKEN,
                 INVALID_API_TOKEN, API_TOKEN_EXPIRED,
                 INVALID_MATCH_TOKEN, MATCH_TOKEN_EXPIRED,
                 INVALID_CLIENT, INVALID_GRANT, INVALID_REFRESH_TOKEN,
                 CLIENT_NOT_FOUND -> Response.Status.UNAUTHORIZED;

            // 403 Forbidden - authenticated but not allowed
            case USER_DISABLED, PERMISSION_DENIED, API_TOKEN_REVOKED,
                 MATCH_TOKEN_REVOKED, MATCH_TOKEN_SCOPE_DENIED,
                 UNAUTHORIZED_CLIENT -> Response.Status.FORBIDDEN;

            // 404 Not Found
            case USER_NOT_FOUND, ROLE_NOT_FOUND, API_TOKEN_NOT_FOUND,
                 MATCH_TOKEN_NOT_FOUND -> Response.Status.NOT_FOUND;

            // 409 Conflict
            case USERNAME_TAKEN, ROLE_NAME_TAKEN -> Response.Status.CONFLICT;

            // 400 Bad Request
            case INVALID_ROLE, INVALID_REQUEST, UNSUPPORTED_GRANT_TYPE,
                 INVALID_SCOPE -> Response.Status.BAD_REQUEST;
        };
    }
}
