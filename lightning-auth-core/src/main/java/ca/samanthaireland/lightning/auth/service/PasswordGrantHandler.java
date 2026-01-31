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

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.model.RefreshToken;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.model.User;
import ca.samanthaireland.lightning.auth.repository.RefreshTokenRepository;
import ca.samanthaireland.lightning.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the OAuth2 Resource Owner Password Credentials grant (RFC 6749 Section 4.3).
 *
 * <p>This grant is used when the client has a trusted relationship with the user
 * and can collect their username/password directly. This is suitable for admin
 * tools and trusted first-party applications.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=password (required)</li>
 *   <li>username (required)</li>
 *   <li>password (required)</li>
 *   <li>scope (optional) - space-delimited list of requested scopes</li>
 * </ul>
 *
 * <p>Response includes both access_token and refresh_token.
 */
public class PasswordGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(PasswordGrantHandler.class);

    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_SCOPE = "scope";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ScopeService scopeService;
    private final OAuth2Configuration oauth2Config;

    public PasswordGrantHandler(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtTokenService jwtTokenService,
            RefreshTokenRepository refreshTokenRepository,
            ScopeService scopeService,
            OAuth2Configuration oauth2Config) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.scopeService = scopeService;
        this.oauth2Config = oauth2Config;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.PASSWORD;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        // Client is required for password grant to track refresh tokens and scopes
        if (client == null) {
            throw AuthException.invalidRequest("client_id is required for password grant");
        }

        String username = parameters.get(PARAM_USERNAME);
        String password = parameters.get(PARAM_PASSWORD);

        // Authenticate user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Password grant: user not found: {}", username);
                    return AuthException.invalidGrant("Invalid username or password");
                });

        if (!user.enabled()) {
            log.warn("Password grant: user disabled: {}", username);
            throw AuthException.invalidGrant("User account is disabled");
        }

        if (!passwordService.verifyPassword(password, user.passwordHash())) {
            log.warn("Password grant: invalid password for user: {}", username);
            throw AuthException.invalidGrant("Invalid username or password");
        }

        // Determine granted scopes (combine user's direct scopes with role-inherited scopes)
        Set<String> requestedScopes = parseScopes(parameters.get(PARAM_SCOPE));
        Set<String> roleScopes = scopeService.resolveScopes(user.roleIds());
        Set<String> userScopes = new HashSet<>(roleScopes);
        userScopes.addAll(user.scopes()); // Add user's direct scopes
        Set<String> grantedScopes;

        if (requestedScopes.isEmpty()) {
            // If no scopes requested, grant user's scopes (filtered by client if present)
            grantedScopes = client != null ? client.filterAllowedScopes(userScopes) : userScopes;
        } else {
            // Filter to scopes allowed for user (and client if present)
            Set<String> clientAllowed = client != null
                    ? client.filterAllowedScopes(requestedScopes)
                    : requestedScopes;
            grantedScopes = clientAllowed.stream()
                    .filter(userScopes::contains)
                    .collect(Collectors.toSet());

            if (grantedScopes.isEmpty() && !requestedScopes.isEmpty()) {
                throw AuthException.invalidScope("None of the requested scopes are available");
            }
        }

        // Create access token
        int accessTokenLifetime = oauth2Config.userTokenLifetimeSeconds();
        String accessToken = jwtTokenService.createUserAccessToken(
                user, client, grantedScopes, accessTokenLifetime);

        // Create refresh token
        int refreshTokenLifetime = oauth2Config.refreshTokenLifetimeSeconds();
        String refreshTokenJwt = jwtTokenService.createRefreshToken(
                user, client, grantedScopes, refreshTokenLifetime);

        // Store refresh token (hash it with SHA-256 - JWTs are too long for BCrypt)
        String refreshTokenHash = passwordService.hashToken(refreshTokenJwt);
        RefreshToken refreshToken = RefreshToken.create(
                refreshTokenHash,
                user.id(),
                client.clientId(),
                grantedScopes,
                Instant.now().plusSeconds(refreshTokenLifetime)
        );
        refreshTokenRepository.save(refreshToken);

        log.info("Password grant: issued tokens for user: {}, client: {}, scopes: {}",
                username, client.clientId(), grantedScopes);

        return OAuth2TokenResponse.forPassword(
                accessToken,
                refreshTokenJwt,
                accessTokenLifetime,
                grantedScopes
        );
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        String username = parameters.get(PARAM_USERNAME);
        String password = parameters.get(PARAM_PASSWORD);

        if (username == null || username.isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: username");
        }
        if (password == null || password.isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: password");
        }
    }

    private Set<String> parseScopes(String scopeParam) {
        if (scopeParam == null || scopeParam.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopeParam.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
