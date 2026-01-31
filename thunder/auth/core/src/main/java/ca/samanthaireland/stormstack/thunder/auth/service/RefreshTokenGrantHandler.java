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

import ca.samanthaireland.stormstack.thunder.auth.config.OAuth2Configuration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.RefreshToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.repository.RefreshTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Handles the OAuth2 Refresh Token grant (RFC 6749 Section 6).
 *
 * <p>This grant is used to obtain a new access token using a refresh token.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=refresh_token (required)</li>
 *   <li>refresh_token (required)</li>
 *   <li>scope (optional) - can only request same or fewer scopes</li>
 * </ul>
 *
 * <p>This implementation uses refresh token rotation for security:
 * the old refresh token is invalidated and a new one is issued.
 */
public class RefreshTokenGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenGrantHandler.class);

    private static final String PARAM_REFRESH_TOKEN = "refresh_token";
    private static final String PARAM_SCOPE = "scope";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final JwtTokenService jwtTokenService;
    private final OAuth2Configuration oauth2Config;

    public RefreshTokenGrantHandler(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordService passwordService,
            JwtTokenService jwtTokenService,
            OAuth2Configuration oauth2Config) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.jwtTokenService = jwtTokenService;
        this.oauth2Config = oauth2Config;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.REFRESH_TOKEN;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        String refreshTokenJwt = parameters.get(PARAM_REFRESH_TOKEN);

        // Verify the JWT structure
        Map<String, Object> claims;
        try {
            claims = jwtTokenService.verifyToken(refreshTokenJwt);
        } catch (AuthException e) {
            log.warn("Refresh token JWT verification failed: {}", e.getMessage());
            throw AuthException.invalidRefreshToken("Invalid or expired refresh token");
        }

        // Verify token type
        String tokenType = (String) claims.get(JwtTokenService.CLAIM_TOKEN_TYPE);
        if (!JwtTokenService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            log.warn("Token is not a refresh token: type={}", tokenType);
            throw AuthException.invalidRefreshToken("Invalid token type");
        }

        // Get user ID from token
        String userIdStr = (String) claims.get("user_id");
        if (userIdStr == null) {
            throw AuthException.invalidRefreshToken("Token missing user_id claim");
        }

        // Get client ID from token and verify it matches
        String tokenClientId = (String) claims.get(JwtTokenService.CLAIM_CLIENT_ID);
        if (client != null && tokenClientId != null && !tokenClientId.equals(client.clientId().value())) {
            log.warn("Refresh token client mismatch: token={}, request={}",
                    tokenClientId, client.clientId());
            throw AuthException.invalidRefreshToken("Token was issued to a different client");
        }

        // Look up the user
        User user = userRepository.findById(ca.samanthaireland.stormstack.thunder.auth.model.UserId.fromString(userIdStr))
                .orElseThrow(() -> {
                    log.warn("Refresh token user not found: {}", userIdStr);
                    return AuthException.invalidRefreshToken("User not found");
                });

        if (!user.enabled()) {
            log.warn("Refresh token for disabled user: {}", user.username());
            throw AuthException.invalidRefreshToken("User account is disabled");
        }

        // Get original scopes from token
        String scopeStr = (String) claims.get(JwtTokenService.CLAIM_SCOPE);
        Set<String> originalScopes = scopeStr != null && !scopeStr.isBlank()
                ? Set.of(scopeStr.split("\\s+"))
                : Set.of();

        // If scope parameter provided, verify it's a subset
        String requestedScopeParam = parameters.get(PARAM_SCOPE);
        Set<String> grantedScopes;
        if (requestedScopeParam != null && !requestedScopeParam.isBlank()) {
            Set<String> requestedScopes = Set.of(requestedScopeParam.split("\\s+"));
            // Can only request same or fewer scopes
            if (!originalScopes.containsAll(requestedScopes)) {
                throw AuthException.invalidScope("Cannot request additional scopes during refresh");
            }
            grantedScopes = requestedScopes;
        } else {
            grantedScopes = originalScopes;
        }

        // Create new access token
        int accessTokenLifetime = oauth2Config.userTokenLifetimeSeconds();
        String accessToken = jwtTokenService.createUserAccessToken(
                user, client, grantedScopes, accessTokenLifetime);

        // Refresh token rotation: create a new refresh token
        int refreshTokenLifetime = oauth2Config.refreshTokenLifetimeSeconds();
        String newRefreshTokenJwt = jwtTokenService.createRefreshToken(
                user, client, grantedScopes, refreshTokenLifetime);

        // Store new refresh token (hash with SHA-256 - JWTs are too long for BCrypt)
        String newRefreshTokenHash = passwordService.hashToken(newRefreshTokenJwt);
        RefreshToken newRefreshToken = RefreshToken.create(
                newRefreshTokenHash,
                user.id(),
                client.clientId(),
                grantedScopes,
                Instant.now().plusSeconds(refreshTokenLifetime)
        );
        refreshTokenRepository.save(newRefreshToken);

        log.info("Refresh grant: issued new tokens for user: {}, client: {}",
                user.username(), client != null ? client.clientId() : "unknown");

        return OAuth2TokenResponse.forRefresh(
                accessToken,
                newRefreshTokenJwt,
                accessTokenLifetime,
                grantedScopes
        );
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        String refreshToken = parameters.get(PARAM_REFRESH_TOKEN);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: refresh_token");
        }
    }
}
