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

import java.util.Objects;
import java.util.Set;

/**
 * OAuth2 token response as defined in RFC 6749 Section 5.1.
 *
 * <p>This record represents the successful response from the token endpoint.
 *
 * @param accessToken  the access token issued by the authorization server
 * @param tokenType    the type of token (always "Bearer")
 * @param expiresIn    the lifetime in seconds of the access token
 * @param refreshToken the refresh token (optional, not for client_credentials)
 * @param scope        space-delimited list of scopes granted
 */
public record OAuth2TokenResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        String refreshToken,
        String scope
) {

    /**
     * Token type constant per RFC 6749.
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    public OAuth2TokenResponse {
        Objects.requireNonNull(accessToken, "Access token cannot be null");
        Objects.requireNonNull(tokenType, "Token type cannot be null");

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be blank");
        }
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("Expires in must be positive");
        }
    }

    /**
     * Creates a token response for client_credentials grant (no refresh token).
     *
     * @param accessToken the access token
     * @param expiresIn   lifetime in seconds
     * @param scopes      granted scopes
     * @return the token response
     */
    public static OAuth2TokenResponse forClientCredentials(
            String accessToken,
            int expiresIn,
            Set<String> scopes) {
        return new OAuth2TokenResponse(
                accessToken,
                TOKEN_TYPE_BEARER,
                expiresIn,
                null,
                String.join(" ", scopes)
        );
    }

    /**
     * Creates a token response for password grant (with refresh token).
     *
     * @param accessToken  the access token
     * @param refreshToken the refresh token
     * @param expiresIn    lifetime in seconds
     * @param scopes       granted scopes
     * @return the token response
     */
    public static OAuth2TokenResponse forPassword(
            String accessToken,
            String refreshToken,
            int expiresIn,
            Set<String> scopes) {
        return new OAuth2TokenResponse(
                accessToken,
                TOKEN_TYPE_BEARER,
                expiresIn,
                refreshToken,
                String.join(" ", scopes)
        );
    }

    /**
     * Creates a token response for refresh token grant.
     *
     * @param accessToken  the new access token
     * @param refreshToken the new refresh token (optional rotation)
     * @param expiresIn    lifetime in seconds
     * @param scopes       granted scopes
     * @return the token response
     */
    public static OAuth2TokenResponse forRefresh(
            String accessToken,
            String refreshToken,
            int expiresIn,
            Set<String> scopes) {
        return new OAuth2TokenResponse(
                accessToken,
                TOKEN_TYPE_BEARER,
                expiresIn,
                refreshToken,
                String.join(" ", scopes)
        );
    }

    /**
     * Creates a token response for token exchange grant.
     *
     * @param accessToken the access token
     * @param expiresIn   lifetime in seconds
     * @param scopes      granted scopes
     * @return the token response
     */
    public static OAuth2TokenResponse forTokenExchange(
            String accessToken,
            int expiresIn,
            Set<String> scopes) {
        return new OAuth2TokenResponse(
                accessToken,
                TOKEN_TYPE_BEARER,
                expiresIn,
                null,
                String.join(" ", scopes)
        );
    }

    /**
     * Gets the scopes as a Set.
     *
     * @return the scopes, or empty set if none
     */
    public Set<String> scopeSet() {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Set.of(scope.split("\\s+"));
    }
}
