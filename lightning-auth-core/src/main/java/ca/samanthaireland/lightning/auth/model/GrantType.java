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

package ca.samanthaireland.lightning.auth.model;

/**
 * OAuth2 grant types supported by the authorization server.
 *
 * <p>Each grant type represents a different way for a client to obtain
 * an access token.
 */
public enum GrantType {

    /**
     * Client Credentials Grant (RFC 6749 Section 4.4).
     *
     * <p>Used for service-to-service authentication where the client
     * is acting on its own behalf, not on behalf of a user.
     */
    CLIENT_CREDENTIALS("client_credentials"),

    /**
     * Resource Owner Password Credentials Grant (RFC 6749 Section 4.3).
     *
     * <p>Used when the client has a trusted relationship with the user
     * and can collect their username/password directly.
     */
    PASSWORD("password"),

    /**
     * Refresh Token Grant (RFC 6749 Section 6).
     *
     * <p>Used to obtain a new access token using a refresh token.
     */
    REFRESH_TOKEN("refresh_token"),

    /**
     * Token Exchange Grant (RFC 8693).
     *
     * <p>Used to exchange one token type for another (e.g., API token
     * for a session JWT).
     */
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange");

    private final String value;

    GrantType(String value) {
        this.value = value;
    }

    /**
     * Gets the OAuth2 grant_type parameter value.
     *
     * @return the grant type string as used in OAuth2 requests
     */
    public String getValue() {
        return value;
    }

    /**
     * Finds a GrantType by its OAuth2 parameter value.
     *
     * @param value the grant_type parameter value
     * @return the GrantType, or null if not found
     */
    public static GrantType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (GrantType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
