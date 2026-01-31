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
import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.MatchTokenRepository;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.IssueMatchTokenRequest;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Implementation of MatchTokenService.
 *
 * <p>Handles match token issuance and validation using JWT.
 */
public class MatchTokenServiceImpl implements MatchTokenService {

    private static final Logger log = LoggerFactory.getLogger(MatchTokenServiceImpl.class);
    private static final String MATCH_TOKEN_ISSUER_SUFFIX = "/match";

    private final MatchTokenRepository tokenRepository;
    private final AuthConfiguration config;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;

    public MatchTokenServiceImpl(
            MatchTokenRepository tokenRepository,
            AuthConfiguration config) {

        this.tokenRepository = Objects.requireNonNull(tokenRepository, "MatchTokenRepository cannot be null");
        this.config = Objects.requireNonNull(config, "AuthConfiguration cannot be null");

        this.issuer = config.jwtIssuer() + MATCH_TOKEN_ISSUER_SUFFIX;

        String secret = config.jwtSecret().orElseGet(MatchTokenServiceImpl::generateSecretKey);
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build();

        log.info("MatchTokenService initialized with issuer: {}", issuer);
    }

    @Override
    public MatchToken issueToken(IssueMatchTokenRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plus(request.validFor());
        Set<String> scopes = request.effectiveScopes();

        // Create the token model
        MatchToken token = MatchToken.create(
                request.matchId(),
                request.containerId(),
                request.playerId(),
                request.userId(),
                request.playerName(),
                scopes,
                expiresAt
        );

        // Generate JWT
        var jwtBuilder = JWT.create()
                .withIssuer(issuer)
                .withClaim(MatchToken.CLAIM_TOKEN_ID, token.id().toString())
                .withClaim(MatchToken.CLAIM_MATCH_ID, request.matchId())
                .withClaim(MatchToken.CLAIM_PLAYER_ID, request.playerId())
                .withClaim(MatchToken.CLAIM_PLAYER_NAME, request.playerName())
                .withClaim(MatchToken.CLAIM_SCOPES, new ArrayList<>(scopes))
                .withIssuedAt(now)
                .withExpiresAt(expiresAt);

        if (request.containerId() != null) {
            jwtBuilder.withClaim(MatchToken.CLAIM_CONTAINER_ID, request.containerId());
        }

        String jwt = jwtBuilder.sign(algorithm);

        // Save token metadata (without JWT)
        tokenRepository.save(token.withoutJwt());

        log.info("Issued match token {} for player {} in match {}",
                token.id(), request.playerId(), request.matchId());

        // Return token with JWT attached
        return token.withJwt(jwt);
    }

    @Override
    public MatchToken validateToken(String jwtToken) {
        try {
            DecodedJWT decoded = verifier.verify(jwtToken);

            String tokenIdStr = decoded.getClaim(MatchToken.CLAIM_TOKEN_ID).asString();
            MatchTokenId tokenId = MatchTokenId.fromString(tokenIdStr);

            // Look up token in repository to check revocation status
            MatchToken stored = tokenRepository.findById(tokenId)
                    .orElseThrow(() -> AuthException.invalidToken("Match token not found"));

            if (stored.isRevoked()) {
                throw AuthException.invalidToken("Match token has been revoked");
            }

            if (stored.isExpired()) {
                throw AuthException.expiredToken();
            }

            return stored;

        } catch (JWTVerificationException e) {
            log.warn("Match token verification failed: {}", e.getMessage());
            throw AuthException.invalidToken(e.getMessage());
        }
    }

    @Override
    public MatchToken validateTokenForMatch(String jwtToken, String matchId) {
        MatchToken token = validateToken(jwtToken);

        if (!token.isValidForMatch(matchId)) {
            throw AuthException.permissionDenied("Token not valid for match: " + matchId);
        }

        return token;
    }

    @Override
    public MatchToken validateTokenForMatchAndContainer(String jwtToken, String matchId, String containerId) {
        MatchToken token = validateToken(jwtToken);

        if (!token.isValidForMatchAndContainer(matchId, containerId)) {
            throw AuthException.permissionDenied("Token not valid for match/container: " + matchId + "/" + containerId);
        }

        return token;
    }

    @Override
    public Optional<MatchToken> findById(MatchTokenId tokenId) {
        return tokenRepository.findById(tokenId);
    }

    @Override
    public List<MatchToken> findByMatchId(String matchId) {
        return tokenRepository.findByMatchId(matchId);
    }

    @Override
    public List<MatchToken> findActiveByMatchId(String matchId) {
        return tokenRepository.findActiveByMatchId(matchId);
    }

    @Override
    public Optional<MatchToken> findActiveByMatchAndPlayer(String matchId, String playerId) {
        return tokenRepository.findActiveByMatchAndPlayer(matchId, playerId);
    }

    @Override
    public MatchToken revokeToken(MatchTokenId tokenId) {
        MatchToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> AuthException.invalidToken("Match token not found: " + tokenId));

        MatchToken revoked = token.revoke();
        tokenRepository.save(revoked);

        log.info("Revoked match token: {}", tokenId);
        return revoked;
    }

    @Override
    public long revokeTokensForPlayer(String matchId, String playerId) {
        List<MatchToken> tokens = tokenRepository.findByMatchId(matchId).stream()
                .filter(t -> t.playerId().equals(playerId) && t.isActive())
                .toList();

        for (MatchToken token : tokens) {
            tokenRepository.save(token.revoke());
        }

        log.info("Revoked {} tokens for player {} in match {}", tokens.size(), playerId, matchId);
        return tokens.size();
    }

    @Override
    public long revokeTokensForMatch(String matchId) {
        List<MatchToken> tokens = tokenRepository.findActiveByMatchId(matchId);

        for (MatchToken token : tokens) {
            tokenRepository.save(token.revoke());
        }

        log.info("Revoked {} tokens for match {}", tokens.size(), matchId);
        return tokens.size();
    }

    @Override
    public long deleteTokensForMatch(String matchId) {
        long deleted = tokenRepository.deleteByMatchId(matchId);
        log.info("Deleted {} tokens for match {}", deleted, matchId);
        return deleted;
    }

    @Override
    public long countActiveByMatchId(String matchId) {
        return tokenRepository.countActiveByMatchId(matchId);
    }

    private static String generateSecretKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
