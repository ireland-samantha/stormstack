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

package ca.samanthaireland.stormstack.thunder.auth.spring.exception;

/**
 * Exception thrown when Lightning Auth operations fail.
 *
 * <p>This exception wraps various authentication and authorization failures
 * that can occur during token exchange, JWT validation, or scope enforcement.
 */
public class LightningAuthException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a new authentication exception.
     *
     * @param errorCode the error code
     * @param message   the error message
     */
    public LightningAuthException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new authentication exception with a cause.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param cause     the underlying cause
     */
    public LightningAuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Error codes for Lightning Auth exceptions.
     */
    public enum ErrorCode {
        /** API token is invalid or expired. */
        INVALID_API_TOKEN,

        /** JWT token is invalid or malformed. */
        INVALID_JWT,

        /** JWT token has expired. */
        JWT_EXPIRED,

        /** Required scopes are missing. */
        INSUFFICIENT_SCOPES,

        /** Auth service is unavailable. */
        SERVICE_UNAVAILABLE,

        /** Request to auth service timed out. */
        TIMEOUT,

        /** Unexpected error during authentication. */
        INTERNAL_ERROR
    }

    /**
     * Creates an exception for invalid API token.
     *
     * @return the exception
     */
    public static LightningAuthException invalidApiToken() {
        return new LightningAuthException(ErrorCode.INVALID_API_TOKEN, "Invalid or expired API token");
    }

    /**
     * Creates an exception for invalid JWT.
     *
     * @param reason the reason the JWT is invalid
     * @return the exception
     */
    public static LightningAuthException invalidJwt(String reason) {
        return new LightningAuthException(ErrorCode.INVALID_JWT, "Invalid JWT: " + reason);
    }

    /**
     * Creates an exception for expired JWT.
     *
     * @return the exception
     */
    public static LightningAuthException jwtExpired() {
        return new LightningAuthException(ErrorCode.JWT_EXPIRED, "JWT has expired");
    }

    /**
     * Creates an exception for insufficient scopes.
     *
     * @param required the required scopes
     * @param actual   the actual scopes
     * @return the exception
     */
    public static LightningAuthException insufficientScopes(String[] required, Object actual) {
        return new LightningAuthException(
                ErrorCode.INSUFFICIENT_SCOPES,
                String.format("Insufficient scopes. Required: %s, actual: %s",
                        String.join(", ", required), actual));
    }

    /**
     * Creates an exception for service unavailability.
     *
     * @param cause the underlying cause
     * @return the exception
     */
    public static LightningAuthException serviceUnavailable(Throwable cause) {
        return new LightningAuthException(ErrorCode.SERVICE_UNAVAILABLE,
                "Auth service is unavailable", cause);
    }

    /**
     * Creates an exception for request timeout.
     *
     * @param cause the underlying cause
     * @return the exception
     */
    public static LightningAuthException timeout(Throwable cause) {
        return new LightningAuthException(ErrorCode.TIMEOUT,
                "Request to auth service timed out", cause);
    }

    /**
     * Creates an exception for internal errors.
     *
     * @param message the error message
     * @param cause   the underlying cause
     * @return the exception
     */
    public static LightningAuthException internalError(String message, Throwable cause) {
        return new LightningAuthException(ErrorCode.INTERNAL_ERROR, message, cause);
    }
}
