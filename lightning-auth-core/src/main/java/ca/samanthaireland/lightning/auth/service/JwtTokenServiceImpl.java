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

import ca.samanthaireland.lightning.auth.config.AuthConfiguration;
import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.model.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Implementation of JwtTokenService using auth0-jwt library.
 *
 * <p>Creates OAuth2-compliant JWTs with standardized claims.
 */
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenServiceImpl.class);
    private static final String AUDIENCE = "stormstack-api";

    private final AuthConfiguration config;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenServiceImpl(AuthConfiguration config) {
        this.config = Objects.requireNonNull(config, "AuthConfiguration cannot be null");

        // Prefer RSA keys if configured, fall back to HMAC
        if (config.privateKeyLocation().isPresent() && config.publicKeyLocation().isPresent()) {
            this.algorithm = createRsaAlgorithm(
                    config.publicKeyLocation().get(),
                    config.privateKeyLocation().get()
            );
            log.info("JwtTokenService initialized with RSA256, issuer: {}", config.jwtIssuer());
        } else {
            String secret = config.jwtSecret().orElseGet(JwtTokenServiceImpl::generateSecretKey);
            this.algorithm = Algorithm.HMAC256(secret);
            log.info("JwtTokenService initialized with HMAC256, issuer: {}", config.jwtIssuer());
        }

        this.verifier = JWT.require(algorithm)
                .withIssuer(config.jwtIssuer())
                .build();
    }

    @Override
    public String createServiceToken(ServiceClient client, Set<String> scopes, int expiresIn) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresIn);
        String jti = generateTokenId();

        String jwt = JWT.create()
                .withIssuer(config.jwtIssuer())
                .withSubject(client.clientId().value())
                .withAudience(AUDIENCE)
                .withClaim(CLAIM_CLIENT_ID, client.clientId().value())
                .withClaim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_SERVICE)
                .withClaim(CLAIM_SCOPE, String.join(" ", scopes))
                .withClaim(CLAIM_JTI, jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);

        log.debug("Created service token for client: {}, jti: {}, expires: {}",
                client.clientId(), jti, expiresAt);

        return jwt;
    }

    @Override
    public String createUserAccessToken(User user, ServiceClient client, Set<String> scopes, int expiresIn) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresIn);
        String jti = generateTokenId();

        String jwt = JWT.create()
                .withIssuer(config.jwtIssuer())
                .withSubject(user.id().toString())
                .withAudience(AUDIENCE)
                .withClaim("upn", user.username())
                .withClaim("user_id", user.id().toString())
                .withClaim("username", user.username())
                .withClaim(CLAIM_CLIENT_ID, client.clientId().value())
                .withClaim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .withClaim(CLAIM_SCOPE, String.join(" ", scopes))
                .withClaim(CLAIM_JTI, jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);

        log.debug("Created access token for user: {}, client: {}, jti: {}",
                user.username(), client.clientId(), jti);

        return jwt;
    }

    @Override
    public String createRefreshToken(User user, ServiceClient client, Set<String> scopes, int expiresIn) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresIn);
        String jti = generateTokenId();

        String jwt = JWT.create()
                .withIssuer(config.jwtIssuer())
                .withSubject(user.id().toString())
                .withClaim("user_id", user.id().toString())
                .withClaim(CLAIM_CLIENT_ID, client.clientId().value())
                .withClaim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .withClaim(CLAIM_SCOPE, String.join(" ", scopes))
                .withClaim(CLAIM_JTI, jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);

        log.debug("Created refresh token for user: {}, client: {}, jti: {}",
                user.username(), client.clientId(), jti);

        return jwt;
    }

    @Override
    public Map<String, Object> verifyToken(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);

            Map<String, Object> claims = new HashMap<>();
            claims.put("iss", decoded.getIssuer());
            claims.put("sub", decoded.getSubject());
            claims.put("aud", decoded.getAudience());
            claims.put("exp", decoded.getExpiresAtAsInstant());
            claims.put("iat", decoded.getIssuedAtAsInstant());

            // Extract custom claims
            for (Map.Entry<String, Claim> entry : decoded.getClaims().entrySet()) {
                String key = entry.getKey();
                Claim claim = entry.getValue();

                if (!claim.isNull()) {
                    if (claim.asString() != null) {
                        claims.put(key, claim.asString());
                    } else if (claim.asInt() != null) {
                        claims.put(key, claim.asInt());
                    } else if (claim.asList(String.class) != null) {
                        claims.put(key, claim.asList(String.class));
                    }
                }
            }

            return claims;

        } catch (JWTVerificationException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            throw AuthException.invalidToken(e.getMessage());
        }
    }

    @Override
    public String getIssuer() {
        return config.jwtIssuer();
    }

    private String generateTokenId() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Algorithm createRsaAlgorithm(String publicKeyLocation, String privateKeyLocation) {
        try {
            RSAPublicKey publicKey = loadPublicKey(publicKeyLocation);
            RSAPrivateKey privateKey = loadPrivateKey(privateKeyLocation);
            return Algorithm.RSA256(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA keys", e);
        }
    }

    private RSAPublicKey loadPublicKey(String location) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readKeyFile(location);
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private RSAPrivateKey loadPrivateKey(String location) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readKeyFile(location);
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private String readKeyFile(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String resource = location.substring("classpath:".length());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resource);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            return Files.readString(Path.of(location));
        }
    }

    private static String generateSecretKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
