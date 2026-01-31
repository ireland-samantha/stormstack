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

package ca.samanthaireland.lightning.auth.provider.dto;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 error response as defined in RFC 6749 Section 5.2.
 *
 * @param error            error code (required)
 * @param errorDescription human-readable description (optional)
 * @param errorUri         URI with more information (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuth2ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription,
        @JsonProperty("error_uri") String errorUri
) {

    // Standard OAuth2 error codes
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_CLIENT = "invalid_client";
    public static final String INVALID_GRANT = "invalid_grant";
    public static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    public static final String INVALID_SCOPE = "invalid_scope";
    public static final String SERVER_ERROR = "server_error";

    /**
     * Creates an error response with error code and description.
     *
     * @param error       the error code
     * @param description the description
     * @return the error response
     */
    public static OAuth2ErrorResponse of(String error, String description) {
        return new OAuth2ErrorResponse(error, description, null);
    }

    /**
     * Creates an error response from an AuthException.
     *
     * @param ex the exception
     * @return the error response
     */
    public static OAuth2ErrorResponse from(AuthException ex) {
        String errorCode = mapErrorCode(ex.getErrorCode());
        return of(errorCode, ex.getMessage());
    }

    private static String mapErrorCode(AuthException.ErrorCode code) {
        return switch (code) {
            case INVALID_REQUEST -> INVALID_REQUEST;
            case INVALID_CLIENT, CLIENT_NOT_FOUND -> INVALID_CLIENT;
            case INVALID_GRANT, INVALID_CREDENTIALS, EXPIRED_TOKEN, INVALID_REFRESH_TOKEN -> INVALID_GRANT;
            case UNAUTHORIZED_CLIENT -> UNAUTHORIZED_CLIENT;
            case UNSUPPORTED_GRANT_TYPE -> UNSUPPORTED_GRANT_TYPE;
            case INVALID_SCOPE -> INVALID_SCOPE;
            default -> SERVER_ERROR;
        };
    }
}
