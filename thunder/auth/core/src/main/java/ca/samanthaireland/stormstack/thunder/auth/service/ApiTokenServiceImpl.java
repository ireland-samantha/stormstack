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

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.ApiTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.CreateApiTokenRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Implementation of ApiTokenService.
 *
 * <p>Handles API token lifecycle including generation, validation, and revocation.
 * Tokens are stored as BCrypt hashes for security.
 */
public class ApiTokenServiceImpl implements ApiTokenService {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenServiceImpl.class);
    private static final String TOKEN_PREFIX = "lat_"; // Lightning API Token
    private static final int SESSION_TOKEN_DURATION_SECONDS = 3600; // 1 hour

    private final ApiTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuthConfiguration config;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom;

    public ApiTokenServiceImpl(
            ApiTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordService passwordService,
            AuthConfiguration config,
            JwtTokenService jwtTokenService) {

        this.tokenRepository = Objects.requireNonNull(tokenRepository, "ApiTokenRepository cannot be null");
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
        this.passwordService = Objects.requireNonNull(passwordService, "PasswordService cannot be null");
        this.config = Objects.requireNonNull(config, "AuthConfiguration cannot be null");
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "JwtTokenService cannot be null");
        this.secureRandom = new SecureRandom();

        log.info("ApiTokenService initialized with JWT issuer: {}", jwtTokenService.getIssuer());
    }

    @Override
    public CreateTokenResult createToken(CreateApiTokenRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");

        // Validate user exists
        userRepository.findById(request.userId())
                .orElseThrow(() -> AuthException.userNotFound(request.userId()));

        // Generate token
        String plaintextToken = generateToken();
        String tokenHash = passwordService.hashPassword(plaintextToken);

        ApiToken token = ApiToken.create(
                request.userId(),
                request.name(),
                tokenHash,
                request.scopes(),
                request.expiresAt()
        );
        ApiToken saved = tokenRepository.save(token);

        log.info("Created API token '{}' for user {}", request.name(), request.userId());
        return new CreateTokenResult(saved, plaintextToken);
    }

    @Override
    public TokenExchangeResult exchangeToken(String plaintextToken, String ipAddress) {
        // Validate the API token (this also records usage)
        ApiToken apiToken = validateToken(plaintextToken, ipAddress);

        // Get the user for the session token
        var user = userRepository.findById(apiToken.userId())
                .orElseThrow(() -> AuthException.userNotFound(apiToken.userId()));

        Instant expiresAt = Instant.now().plusSeconds(SESSION_TOKEN_DURATION_SECONDS);

        // Generate a session JWT using the proper signing algorithm and issuer
        String sessionToken = jwtTokenService.createApiTokenSessionToken(
                user,
                apiToken.scopes(),
                apiToken.id().toString(),
                SESSION_TOKEN_DURATION_SECONDS
        );

        log.info("Exchanged API token {} for session token for user {}",
                apiToken.id(), user.username());

        return new TokenExchangeResult(sessionToken, expiresAt, apiToken.scopes());
    }

    @Override
    public ApiToken validateToken(String plaintextToken, String ipAddress) {
        if (plaintextToken == null || !plaintextToken.startsWith(TOKEN_PREFIX)) {
            throw AuthException.invalidApiToken();
        }

        // Find matching token by checking hash against all active tokens
        List<ApiToken> activeTokens = tokenRepository.findAllActive();
        for (ApiToken token : activeTokens) {
            if (passwordService.verifyPassword(plaintextToken, token.tokenHash())) {
                // Check if expired
                if (token.isExpired()) {
                    throw AuthException.apiTokenExpired();
                }

                // Check if revoked
                if (token.isRevoked()) {
                    throw AuthException.apiTokenRevoked(token.id());
                }

                // Record usage
                ApiToken updated = token.recordUsage(ipAddress);
                tokenRepository.save(updated);

                return updated;
            }
        }

        throw AuthException.invalidApiToken();
    }

    @Override
    public Optional<ApiToken> findById(ApiTokenId tokenId) {
        return tokenRepository.findById(tokenId);
    }

    @Override
    public List<ApiToken> findByUserId(UserId userId) {
        return tokenRepository.findByUserId(userId);
    }

    @Override
    public List<ApiToken> findAll() {
        return tokenRepository.findAll();
    }

    @Override
    public ApiToken revokeToken(ApiTokenId tokenId) {
        ApiToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> AuthException.apiTokenNotFound(tokenId));

        ApiToken revoked = token.revoke();
        ApiToken saved = tokenRepository.save(revoked);

        log.info("Revoked API token: {}", tokenId);
        return saved;
    }

    @Override
    public boolean deleteToken(ApiTokenId tokenId) {
        if (tokenRepository.findById(tokenId).isEmpty()) {
            throw AuthException.apiTokenNotFound(tokenId);
        }

        boolean deleted = tokenRepository.deleteById(tokenId);
        if (deleted) {
            log.info("Deleted API token: {}", tokenId);
        }
        return deleted;
    }

    @Override
    public long count() {
        return tokenRepository.count();
    }

    @Override
    public long countActiveByUserId(UserId userId) {
        return tokenRepository.countActiveByUserId(userId);
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return the generated token with prefix
     */
    private String generateToken() {
        byte[] bytes = new byte[config.apiTokenLengthBytes()];
        secureRandom.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
