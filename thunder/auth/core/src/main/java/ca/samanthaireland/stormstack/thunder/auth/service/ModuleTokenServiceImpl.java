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
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest.ComponentPermission;
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
import java.util.stream.Collectors;

/**
 * Implementation of ModuleTokenService using JWT.
 *
 * <p>Module tokens are long-lived (365 days by default) since they are
 * used for in-process authentication within Thunder Engine containers.
 */
public class ModuleTokenServiceImpl implements ModuleTokenService {

    private static final Logger log = LoggerFactory.getLogger(ModuleTokenServiceImpl.class);
    private static final String MODULE_TOKEN_ISSUER_SUFFIX = "/module";
    private static final int DEFAULT_TOKEN_LIFETIME_DAYS = 365;

    private final AuthConfiguration config;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final int tokenLifetimeSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public ModuleTokenServiceImpl(AuthConfiguration config) {
        this.config = Objects.requireNonNull(config, "AuthConfiguration cannot be null");
        this.issuer = config.jwtIssuer() + MODULE_TOKEN_ISSUER_SUFFIX;
        this.tokenLifetimeSeconds = DEFAULT_TOKEN_LIFETIME_DAYS * 24 * 60 * 60;

        // Prefer RSA keys if configured, fall back to HMAC
        if (config.privateKeyLocation().isPresent() && config.publicKeyLocation().isPresent()) {
            this.algorithm = createRsaAlgorithm(
                    config.publicKeyLocation().get(),
                    config.privateKeyLocation().get()
            );
            log.info("ModuleTokenService initialized with RSA256, issuer: {}", issuer);
        } else {
            String secret = config.jwtSecret().orElseGet(ModuleTokenServiceImpl::generateSecretKey);
            this.algorithm = Algorithm.HMAC256(secret);
            log.info("ModuleTokenService initialized with HMAC256, issuer: {}", issuer);
        }

        this.verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build();
    }

    @Override
    public String issueToken(ModuleTokenRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenLifetimeSeconds);
        String jti = generateTokenId();

        // Convert permissions to strings for JWT claim
        Map<String, String> permissionStrings = request.componentPermissions().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().name().toLowerCase()
                ));

        var jwtBuilder = JWT.create()
                .withIssuer(issuer)
                .withSubject(request.moduleName())
                .withClaim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_MODULE)
                .withClaim(CLAIM_MODULE_NAME, request.moduleName())
                .withClaim(CLAIM_COMPONENT_PERMISSIONS, permissionStrings)
                .withClaim(CLAIM_SUPERUSER, request.superuser())
                .withClaim(JwtTokenService.CLAIM_JTI, jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt);

        if (request.containerId() != null) {
            jwtBuilder.withClaim(CLAIM_CONTAINER_ID, request.containerId());
        }

        String jwt = jwtBuilder.sign(algorithm);

        log.info("Issued module token for module: {}, superuser: {}, jti: {}",
                request.moduleName(), request.superuser(), jti);

        return jwt;
    }

    @Override
    public Map<String, Object> verifyToken(String jwtToken) {
        try {
            DecodedJWT decoded = verifier.verify(jwtToken);

            Map<String, Object> claims = new HashMap<>();
            claims.put("iss", decoded.getIssuer());
            claims.put("sub", decoded.getSubject());
            claims.put("exp", decoded.getExpiresAtAsInstant());
            claims.put("iat", decoded.getIssuedAtAsInstant());

            // Extract custom claims
            for (Map.Entry<String, Claim> entry : decoded.getClaims().entrySet()) {
                String key = entry.getKey();
                Claim claim = entry.getValue();

                if (!claim.isNull()) {
                    if (key.equals(CLAIM_COMPONENT_PERMISSIONS)) {
                        claims.put(key, claim.asMap());
                    } else if (key.equals(CLAIM_SUPERUSER)) {
                        claims.put(key, claim.asBoolean());
                    } else if (claim.asString() != null) {
                        claims.put(key, claim.asString());
                    } else if (claim.asInt() != null) {
                        claims.put(key, claim.asInt());
                    } else if (claim.asBoolean() != null) {
                        claims.put(key, claim.asBoolean());
                    }
                }
            }

            return claims;

        } catch (JWTVerificationException e) {
            log.warn("Module token verification failed: {}", e.getMessage());
            throw AuthException.invalidToken("Module token verification failed: " + e.getMessage());
        }
    }

    @Override
    public String refreshToken(String existingToken, Map<String, ComponentPermission> newPermissions) {
        Map<String, Object> claims = verifyToken(existingToken);

        String moduleName = (String) claims.get(CLAIM_MODULE_NAME);
        Boolean superuser = (Boolean) claims.get(CLAIM_SUPERUSER);
        String containerId = (String) claims.get(CLAIM_CONTAINER_ID);

        if (moduleName == null) {
            throw AuthException.invalidToken("Module token missing module_name claim");
        }

        ModuleTokenRequest request = new ModuleTokenRequest(
                moduleName,
                newPermissions,
                superuser != null && superuser,
                containerId
        );

        return issueToken(request);
    }

    @Override
    public int getTokenLifetimeSeconds() {
        return tokenLifetimeSeconds;
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
            throw new IllegalStateException("Failed to load RSA keys for module tokens", e);
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
