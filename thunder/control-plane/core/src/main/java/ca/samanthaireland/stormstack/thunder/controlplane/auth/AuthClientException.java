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

package ca.samanthaireland.stormstack.thunder.controlplane.auth;

/**
 * Exception thrown when communication with the auth service fails.
 */
public class AuthClientException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    /**
     * Creates a new AuthClientException.
     *
     * @param message    the error message
     * @param statusCode the HTTP status code (0 if not applicable)
     * @param errorCode  the error code from the auth service (null if not available)
     */
    public AuthClientException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    /**
     * Creates a new AuthClientException with a cause.
     *
     * @param message the error message
     * @param cause   the cause
     */
    public AuthClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
    }

    /**
     * Creates an exception for a connection failure.
     *
     * @param authUrl the URL that couldn't be reached
     * @param cause   the cause
     * @return a new exception
     */
    public static AuthClientException connectionFailed(String authUrl, Throwable cause) {
        return new AuthClientException(
                "Failed to connect to auth service at " + authUrl,
                cause
        );
    }

    /**
     * Creates an exception for an HTTP error response.
     *
     * @param statusCode the HTTP status code
     * @param errorCode  the error code from the response
     * @param message    the error message
     * @return a new exception
     */
    public static AuthClientException httpError(int statusCode, String errorCode, String message) {
        return new AuthClientException(
                "Auth service returned error: " + message,
                statusCode,
                errorCode
        );
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
