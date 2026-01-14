package ca.samanthaireland.auth;

/**
 * Exception thrown when authentication or authorization fails.
 *
 * <p>This exception is thrown when:
 * <ul>
 *   <li>Login credentials are invalid</li>
 *   <li>A JWT token is invalid, expired, or malformed</li>
 *   <li>User account is disabled</li>
 *   <li>Required permissions are missing</li>
 *   <li>Role operations fail</li>
 * </ul>
 */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Error codes for authentication failures.
     */
    public enum ErrorCode {
        INVALID_CREDENTIALS,
        INVALID_TOKEN,
        EXPIRED_TOKEN,
        USER_DISABLED,
        PERMISSION_DENIED,
        USER_NOT_FOUND,
        USERNAME_TAKEN,
        ROLE_NOT_FOUND,
        ROLE_TAKEN,
        INVALID_ROLE
    }

    /**
     * Create a new AuthException with a message and error code.
     *
     * @param message the error message
     * @param errorCode the error code
     */
    public AuthException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new AuthException with just a message.
     *
     * @param message the error message
     */
    public AuthException(String message) {
        super(message);
        this.errorCode = null;
    }

    /**
     * Create a new AuthException with a message, cause, and error code.
     *
     * @param message the error message
     * @param cause the underlying cause
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
        return new AuthException("Permission denied. Required role: " + requiredRole,
                ErrorCode.PERMISSION_DENIED);
    }

    /**
     * Create an exception for user not found.
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
    public static AuthException roleTaken(String roleName) {
        return new AuthException("Role name already taken: " + roleName, ErrorCode.ROLE_TAKEN);
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
}
