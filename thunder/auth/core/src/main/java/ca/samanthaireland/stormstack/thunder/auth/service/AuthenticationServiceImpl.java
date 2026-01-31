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
import ca.samanthaireland.stormstack.thunder.auth.model.*;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AuthenticationService.
 *
 * <p>Handles JWT-based authentication using either RSA256 (preferred) or HMAC256 algorithm.
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final ScopeService scopeService;
    private final PasswordService passwordService;
    private final AuthConfiguration config;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public AuthenticationServiceImpl(
            UserRepository userRepository,
            RoleService roleService,
            ScopeService scopeService,
            PasswordService passwordService,
            AuthConfiguration config) {

        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
        this.roleService = Objects.requireNonNull(roleService, "RoleService cannot be null");
        this.scopeService = Objects.requireNonNull(scopeService, "ScopeService cannot be null");
        this.passwordService = Objects.requireNonNull(passwordService, "PasswordService cannot be null");
        this.config = Objects.requireNonNull(config, "AuthConfiguration cannot be null");

        // Prefer RSA keys if configured, fall back to HMAC
        if (config.privateKeyLocation().isPresent() && config.publicKeyLocation().isPresent()) {
            this.algorithm = createRsaAlgorithm(
                    config.publicKeyLocation().get(),
                    config.privateKeyLocation().get()
            );
            log.info("AuthenticationService initialized with RSA256 signing, issuer: {}", config.jwtIssuer());
        } else {
            String secret = config.jwtSecret().orElseGet(AuthenticationServiceImpl::generateSecretKey);
            this.algorithm = Algorithm.HMAC256(secret);
            log.info("AuthenticationService initialized with HMAC256 signing, issuer: {}", config.jwtIssuer());
        }

        this.verifier = JWT.require(algorithm)
                .withIssuer(config.jwtIssuer())
                .build();
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

    @Override
    public AuthToken login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(AuthException::invalidCredentials);

        if (!user.enabled()) {
            throw AuthException.userDisabled(username);
        }

        if (!passwordService.verifyPassword(password, user.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        log.info("User '{}' logged in successfully", username);
        return issueToken(user);
    }

    @Override
    public AuthToken verifyToken(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);

            String userIdStr = decoded.getClaim(AuthToken.CLAIM_USER_ID).asString();
            UserId userId = UserId.fromString(userIdStr);
            String username = decoded.getClaim(AuthToken.CLAIM_USERNAME).asString();
            List<String> roleNames = decoded.getClaim(AuthToken.CLAIM_ROLES).asList(String.class);
            List<String> scopeList = decoded.getClaim(AuthToken.CLAIM_SCOPES).asList(String.class);
            Instant expiresAt = decoded.getExpiresAtAsInstant();

            // Handle tokens without roles (OAuth2 tokens may not have this)
            Set<String> roleSet = roleNames != null ? new HashSet<>(roleNames) : Set.of();

            // Handle OAuth2 "scope" claim (space-delimited) as fallback for "scopes" claim
            Set<String> scopeSet;
            if (scopeList != null) {
                scopeSet = new HashSet<>(scopeList);
            } else {
                // Try OAuth2-style "scope" claim (space-delimited string)
                String scopeStr = decoded.getClaim("scope").asString();
                if (scopeStr != null && !scopeStr.isBlank()) {
                    scopeSet = Set.of(scopeStr.split("\\s+"));
                } else {
                    scopeSet = Set.of();
                }
            }

            return new AuthToken(userId, username, roleSet, scopeSet, expiresAt, token);

        } catch (JWTVerificationException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            throw AuthException.invalidToken(e.getMessage());
        }
    }

    @Override
    public AuthToken refreshToken(String token) {
        AuthToken existing = verifyToken(token);

        User user = userRepository.findById(existing.userId())
                .orElseThrow(() -> AuthException.userNotFound(existing.username()));

        if (!user.enabled()) {
            throw AuthException.userDisabled(user.username());
        }

        log.debug("Refreshing token for user '{}'", user.username());
        return issueToken(user);
    }

    @Override
    public boolean userHasRole(User user, String roleName) {
        // Get role names for user's role IDs
        List<Role> roles = roleService.findAllById(user.roleIds());
        for (Role role : roles) {
            if (roleService.roleIncludes(role.name(), roleName)) {
                return true;
            }
        }
        return false;
    }

    private AuthToken issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(config.sessionExpiryHours(), ChronoUnit.HOURS);

        // Get role names from role IDs
        List<Role> roles = roleService.findAllById(user.roleIds());
        List<String> roleNames = roles.stream()
                .map(Role::name)
                .collect(Collectors.toList());

        // Resolve all scopes from user's roles (including inherited)
        Set<String> resolvedScopes = scopeService.resolveScopes(user.roleIds());
        List<String> scopesList = new ArrayList<>(resolvedScopes);

        String jwt = JWT.create()
                .withIssuer(config.jwtIssuer())
                .withSubject(user.username())  // Required by SmallRye JWT for principal
                .withClaim("upn", user.username())  // MicroProfile JWT user principal name
                .withClaim(AuthToken.CLAIM_USER_ID, user.id().toString())
                .withClaim(AuthToken.CLAIM_USERNAME, user.username())
                .withClaim(AuthToken.CLAIM_ROLES, roleNames)
                .withClaim(AuthToken.CLAIM_SCOPES, scopesList)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);

        return new AuthToken(user.id(), user.username(), new HashSet<>(roleNames), resolvedScopes, expiresAt, jwt);
    }

    private static String generateSecretKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
