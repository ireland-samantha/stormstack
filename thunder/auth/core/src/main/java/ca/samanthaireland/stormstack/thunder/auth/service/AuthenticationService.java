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

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.AuthToken;
import ca.samanthaireland.stormstack.thunder.auth.model.User;

/**
 * Service for authentication operations.
 *
 * <p>Handles user login, JWT token issuance, verification, and refresh.
 */
public interface AuthenticationService {

    /**
     * Authenticates a user and issues a JWT token.
     *
     * @param username the username
     * @param password the plain text password
     * @return the authentication token containing JWT
     * @throws AuthException if credentials are invalid or user is disabled
     */
    AuthToken login(String username, String password);

    /**
     * Verifies a JWT token and extracts the claims.
     *
     * @param token the JWT token string
     * @return the verified authentication token
     * @throws AuthException if the token is invalid or expired
     */
    AuthToken verifyToken(String token);

    /**
     * Refreshes an existing token (issues a new token for the same user).
     *
     * @param token the existing JWT token
     * @return a new token with extended expiry
     * @throws AuthException if the token is invalid or user is disabled
     */
    AuthToken refreshToken(String token);

    /**
     * Checks if a user has a specific role (considering role hierarchy).
     *
     * @param user     the user to check
     * @param roleName the role name to check for
     * @return true if the user has the role (directly or through hierarchy)
     */
    boolean userHasRole(User user, String roleName);
}
