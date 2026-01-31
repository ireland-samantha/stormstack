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

package ca.samanthaireland.stormstack.thunder.auth.exception;

import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;

/**
 * Base exception for authentication and authorization errors.
 *
 * <p>This exception provides typed error codes for different failure scenarios,
 * making it easy to map to appropriate HTTP status codes in REST endpoints.
 */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Error codes for authentication and authorization failures.
     */
    public enum ErrorCode {
        /** Login credentials are invalid. */
        INVALID_CREDENTIALS,

        /** JWT token format or signature is invalid. */
        INVALID_TOKEN,

        /** JWT token has expired. */
        EXPIRED_TOKEN,

        /** User account is disabled. */
        USER_DISABLED,

        /** Required permission is missing. */
        PERMISSION_DENIED,

        /** User does not exist. */
        USER_NOT_FOUND,

        /** Username is already taken. */
        USERNAME_TAKEN,

        /** Role does not exist. */
        ROLE_NOT_FOUND,

        /** Role name is already taken. */
        ROLE_NAME_TAKEN,

        /** Invalid role assignment. */
        INVALID_ROLE,

        /** API token does not exist. */
        API_TOKEN_NOT_FOUND,

        /** API token has been revoked. */
        API_TOKEN_REVOKED,

        /** API token has expired. */
        API_TOKEN_EXPIRED,

        /** API token is invalid. */
        INVALID_API_TOKEN,

        /** Match token does not exist. */
        MATCH_TOKEN_NOT_FOUND,

        /** Match token has been revoked. */
        MATCH_TOKEN_REVOKED,

        /** Match token has expired. */
        MATCH_TOKEN_EXPIRED,

        /** Match token is invalid. */
        INVALID_MATCH_TOKEN,

        /** Match token not valid for requested resource. */
        MATCH_TOKEN_SCOPE_DENIED,

        // OAuth2 Error Codes (RFC 6749 Section 5.2)

        /** The request is missing a required parameter or has an invalid value. */
        INVALID_REQUEST,

        /** Client authentication failed. */
        INVALID_CLIENT,

        /** The provided authorization grant is invalid or expired. */
        INVALID_GRANT,

        /** The client is not authorized to use this grant type. */
        UNAUTHORIZED_CLIENT,

        /** The authorization grant type is not supported. */
        UNSUPPORTED_GRANT_TYPE,

        /** The requested scope is invalid, unknown, or malformed. */
        INVALID_SCOPE,

        /** The client is disabled or not found. */
        CLIENT_NOT_FOUND,

        /** The refresh token is invalid or expired. */
        INVALID_REFRESH_TOKEN
    }

    /**
     * Create a new AuthException with a message and error code.
     *
     * @param message   the error message
     * @param errorCode the error code
     */
    public AuthException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new AuthException with a message, cause, and error code.
     *
     * @param message   the error message
     * @param cause     the underlying cause
     * @param errorCode the error code
     */
    public AuthException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Get the error code for this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // Factory methods for common error scenarios

    /**
     * Create an exception for invalid credentials.
     *
     * @return the exception
     */
    public static AuthException invalidCredentials() {
        return new AuthException("Invalid username or password", ErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * Create an exception for an invalid token.
     *
     * @param message detailed message
     * @return the exception
     */
    public static AuthException invalidToken(String message) {
        return new AuthException(message, ErrorCode.INVALID_TOKEN);
    }

    /**
     * Create an exception for an expired token.
     *
     * @return the exception
     */
    public static AuthException expiredToken() {
        return new AuthException("Token has expired", ErrorCode.EXPIRED_TOKEN);
    }

    /**
     * Create an exception for a disabled user.
     *
     * @param username the disabled user's name
     * @return the exception
     */
    public static AuthException userDisabled(String username) {
        return new AuthException("User account is disabled: " + username, ErrorCode.USER_DISABLED);
    }

    /**
     * Create an exception for missing permissions.
     *
     * @param requiredRole the role name that was required
     * @return the exception
     */
    public static AuthException permissionDenied(String requiredRole) {
        return new AuthException("Permission denied. Required role: " + requiredRole, ErrorCode.PERMISSION_DENIED);
    }

    /**
     * Create an exception for user not found.
     *
     * @param userId the user ID that was not found
     * @return the exception
     */
    public static AuthException userNotFound(UserId userId) {
        return new AuthException("User not found: " + userId, ErrorCode.USER_NOT_FOUND);
    }

    /**
     * Create an exception for user not found by username.
     *
     * @param username the username that was not found
     * @return the exception
     */
    public static AuthException userNotFound(String username) {
        return new AuthException("User not found: " + username, ErrorCode.USER_NOT_FOUND);
    }

    /**
     * Create an exception for duplicate username.
     *
     * @param username the duplicate username
     * @return the exception
     */
    public static AuthException usernameTaken(String username) {
        return new AuthException("Username already taken: " + username, ErrorCode.USERNAME_TAKEN);
    }

    /**
     * Create an exception for role not found.
     *
     * @param roleId the role ID that was not found
     * @return the exception
     */
    public static AuthException roleNotFound(RoleId roleId) {
        return new AuthException("Role not found: " + roleId, ErrorCode.ROLE_NOT_FOUND);
    }

    /**
     * Create an exception for role not found by name.
     *
     * @param roleName the role name that was not found
     * @return the exception
     */
    public static AuthException roleNotFound(String roleName) {
        return new AuthException("Role not found: " + roleName, ErrorCode.ROLE_NOT_FOUND);
    }

    /**
     * Create an exception for duplicate role name.
     *
     * @param roleName the duplicate role name
     * @return the exception
     */
    public static AuthException roleNameTaken(String roleName) {
        return new AuthException("Role name already taken: " + roleName, ErrorCode.ROLE_NAME_TAKEN);
    }

    /**
     * Create an exception for invalid role assignment.
     *
     * @param roleName the invalid role name
     * @return the exception
     */
    public static AuthException invalidRole(String roleName) {
        return new AuthException("Invalid role: " + roleName, ErrorCode.INVALID_ROLE);
    }

    /**
     * Create an exception for API token not found.
     *
     * @param tokenId the token ID that was not found
     * @return the exception
     */
    public static AuthException apiTokenNotFound(ApiTokenId tokenId) {
        return new AuthException("API token not found: " + tokenId, ErrorCode.API_TOKEN_NOT_FOUND);
    }

    /**
     * Create an exception for revoked API token.
     *
     * @param tokenId the revoked token ID
     * @return the exception
     */
    public static AuthException apiTokenRevoked(ApiTokenId tokenId) {
        return new AuthException("API token has been revoked: " + tokenId, ErrorCode.API_TOKEN_REVOKED);
    }

    /**
     * Create an exception for expired API token.
     *
     * @return the exception
     */
    public static AuthException apiTokenExpired() {
        return new AuthException("API token has expired", ErrorCode.API_TOKEN_EXPIRED);
    }

    /**
     * Create an exception for invalid API token.
     *
     * @return the exception
     */
    public static AuthException invalidApiToken() {
        return new AuthException("Invalid API token", ErrorCode.INVALID_API_TOKEN);
    }

    /**
     * Create an exception for match token not found.
     *
     * @param tokenId the token ID that was not found
     * @return the exception
     */
    public static AuthException matchTokenNotFound(MatchTokenId tokenId) {
        return new AuthException("Match token not found: " + tokenId, ErrorCode.MATCH_TOKEN_NOT_FOUND);
    }

    /**
     * Create an exception for revoked match token.
     *
     * @param tokenId the revoked token ID
     * @return the exception
     */
    public static AuthException matchTokenRevoked(MatchTokenId tokenId) {
        return new AuthException("Match token has been revoked: " + tokenId, ErrorCode.MATCH_TOKEN_REVOKED);
    }

    /**
     * Create an exception for expired match token.
     *
     * @return the exception
     */
    public static AuthException matchTokenExpired() {
        return new AuthException("Match token has expired", ErrorCode.MATCH_TOKEN_EXPIRED);
    }

    /**
     * Create an exception for invalid match token.
     *
     * @return the exception
     */
    public static AuthException invalidMatchToken() {
        return new AuthException("Invalid match token", ErrorCode.INVALID_MATCH_TOKEN);
    }

    /**
     * Create an exception for invalid match token.
     *
     * @param message detailed message
     * @return the exception
     */
    public static AuthException invalidMatchToken(String message) {
        return new AuthException("Invalid match token: " + message, ErrorCode.INVALID_MATCH_TOKEN);
    }

    /**
     * Create an exception for match token scope denial.
     *
     * @param scope the scope that was required
     * @return the exception
     */
    public static AuthException matchTokenScopeDenied(String scope) {
        return new AuthException("Match token missing required scope: " + scope, ErrorCode.MATCH_TOKEN_SCOPE_DENIED);
    }

    // OAuth2 Error Factory Methods

    /**
     * Create an exception for invalid OAuth2 request.
     *
     * @param message detailed description of what's missing or invalid
     * @return the exception
     */
    public static AuthException invalidRequest(String message) {
        return new AuthException(message, ErrorCode.INVALID_REQUEST);
    }

    /**
     * Create an exception for invalid client credentials.
     *
     * @return the exception
     */
    public static AuthException invalidClient() {
        return new AuthException("Client authentication failed", ErrorCode.INVALID_CLIENT);
    }

    /**
     * Create an exception for invalid client credentials with message.
     *
     * @param message detailed message
     * @return the exception
     */
    public static AuthException invalidClient(String message) {
        return new AuthException(message, ErrorCode.INVALID_CLIENT);
    }

    /**
     * Create an exception for invalid grant.
     *
     * @param message detailed description of what's wrong with the grant
     * @return the exception
     */
    public static AuthException invalidGrant(String message) {
        return new AuthException(message, ErrorCode.INVALID_GRANT);
    }

    /**
     * Create an exception for unauthorized client.
     *
     * @param clientId the client ID
     * @param grantType the grant type that was attempted
     * @return the exception
     */
    public static AuthException unauthorizedClient(String clientId, String grantType) {
        return new AuthException(
                "Client '" + clientId + "' is not authorized to use grant type: " + grantType,
                ErrorCode.UNAUTHORIZED_CLIENT);
    }

    /**
     * Create an exception for unsupported grant type.
     *
     * @param grantType the unsupported grant type
     * @return the exception
     */
    public static AuthException unsupportedGrantType(String grantType) {
        return new AuthException("Unsupported grant type: " + grantType, ErrorCode.UNSUPPORTED_GRANT_TYPE);
    }

    /**
     * Create an exception for invalid scope.
     *
     * @param scope the invalid scope
     * @return the exception
     */
    public static AuthException invalidScope(String scope) {
        return new AuthException("Invalid scope: " + scope, ErrorCode.INVALID_SCOPE);
    }

    /**
     * Create an exception for scope not allowed for client.
     *
     * @param clientId the client ID
     * @param scope the disallowed scope
     * @return the exception
     */
    public static AuthException scopeNotAllowed(String clientId, String scope) {
        return new AuthException(
                "Client '" + clientId + "' is not allowed to request scope: " + scope,
                ErrorCode.INVALID_SCOPE);
    }

    /**
     * Create an exception for client not found.
     *
     * @param clientId the client ID that was not found
     * @return the exception
     */
    public static AuthException clientNotFound(String clientId) {
        return new AuthException("Client not found: " + clientId, ErrorCode.CLIENT_NOT_FOUND);
    }

    /**
     * Create an exception for disabled client.
     *
     * @param clientId the disabled client ID
     * @return the exception
     */
    public static AuthException clientDisabled(String clientId) {
        return new AuthException("Client is disabled: " + clientId, ErrorCode.INVALID_CLIENT);
    }

    /**
     * Create an exception for invalid refresh token.
     *
     * @return the exception
     */
    public static AuthException invalidRefreshToken() {
        return new AuthException("Invalid or expired refresh token", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    /**
     * Create an exception for invalid refresh token with message.
     *
     * @param message detailed message
     * @return the exception
     */
    public static AuthException invalidRefreshToken(String message) {
        return new AuthException(message, ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
