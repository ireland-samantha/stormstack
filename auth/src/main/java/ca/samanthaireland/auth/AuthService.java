package ca.samanthaireland.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for authentication and JWT token management.
 *
 * <p>This service handles:
 * <ul>
 *   <li>User login/logout</li>
 *   <li>JWT token issuance and verification</li>
 *   <li>Session management</li>
 * </ul>
 *
 * <p>Thread-safe.
 */
@Slf4j
public class AuthService {

    private static final String ISSUER = "https://lightningfirefly.com";
    private static final int DEFAULT_TOKEN_EXPIRY_HOURS = 24;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final TokenIssuer tokenIssuer;

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final RoleService roleService;
    private final int tokenExpiryHours;

    /**
     * Create an AuthService with a random secret key.
     *
     * @param userRepository the user repository
     * @param passwordService the password service
     * @param roleService the role service for hierarchy resolution
     */
    public AuthService(UserRepository userRepository, PasswordService passwordService, RoleService roleService) {
        this(userRepository, passwordService, roleService, generateSecretKey(), DEFAULT_TOKEN_EXPIRY_HOURS);
    }

    /**
     * Create an AuthService with a custom secret key.
     *
     * @param userRepository the user repository
     * @param passwordService the password service
     * @param roleService the role service for hierarchy resolution
     * @param secretKey the secret key for signing JWTs
     * @param tokenExpiryHours hours until tokens expire
     */
    public AuthService(
            UserRepository userRepository,
            PasswordService passwordService,
            RoleService roleService,
            String secretKey,
            int tokenExpiryHours) {
        this(userRepository, passwordService, roleService, secretKey, tokenExpiryHours, null);
    }

    /**
     * Create an AuthService with a custom secret key and token issuer.
     *
     * @param userRepository the user repository
     * @param passwordService the password service
     * @param roleService the role service for hierarchy resolution
     * @param secretKey the secret key for signing JWTs
     * @param tokenExpiryHours hours until tokens expire
     * @param tokenIssuer custom token issuer, or null to use default HMAC-based issuer
     */
    public AuthService(
            UserRepository userRepository,
            PasswordService passwordService,
            RoleService roleService,
            String secretKey,
            int tokenExpiryHours,
            TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.roleService = roleService;
        this.algorithm = Algorithm.HMAC256(secretKey);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        this.tokenExpiryHours = tokenExpiryHours;
        this.tokenIssuer = tokenIssuer != null ? tokenIssuer : new DefaultTokenIssuer();
        log.info("AuthService initialized with {}h token expiry", tokenExpiryHours);
    }

    /**
     * Default token issuer that uses HMAC256 algorithm.
     */
    private class DefaultTokenIssuer implements TokenIssuer {
        @Override
        public AuthToken issueToken(User user) {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(tokenExpiryHours, ChronoUnit.HOURS);

            List<String> rolesList = new ArrayList<>(user.roles());

            String jwt = JWT.create()
                    .withIssuer(ISSUER)
                    .withClaim(AuthToken.CLAIM_USER_ID, user.id())
                    .withClaim(AuthToken.CLAIM_USERNAME, user.username())
                    .withClaim(AuthToken.CLAIM_ROLES, rolesList)
                    .withIssuedAt(now)
                    .withExpiresAt(expiresAt)
                    .sign(algorithm);

            return new AuthToken(user.id(), user.username(), user.roles(), expiresAt, jwt);
        }
    }

    /**
     * Authenticate a user and issue a JWT token.
     *
     * @param username the username
     * @param password the plain text password
     * @return the authentication token
     * @throws AuthException if credentials are invalid or user is disabled
     */
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

    private AuthToken issueToken(User user) {
        return tokenIssuer.issueToken(user);
    }

    /**X
     * Verify a JWT token and extract the authentication claims.
     *
     * @param token the JWT token string
     * @return the verified authentication token
     * @throws AuthException if the token is invalid or expired
     */
    public AuthToken verifyToken(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);

            long userId = decoded.getClaim(AuthToken.CLAIM_USER_ID).asLong();
            String username = decoded.getClaim(AuthToken.CLAIM_USERNAME).asString();
            List<String> roleValues = decoded.getClaim(AuthToken.CLAIM_ROLES).asList(String.class);
            Instant expiresAt = decoded.getExpiresAtAsInstant();

            Set<String> roles = new HashSet<>(roleValues);

            return new AuthToken(userId, username, roles, expiresAt, token);

        } catch (JWTVerificationException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            throw AuthException.invalidToken(e.getMessage());
        }
    }

    /**
     * Refresh an existing token (issue a new token for the same user).
     *
     * @param token the existing token to refresh
     * @return a new token with extended expiry
     * @throws AuthException if the token is invalid
     */
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

    /**
     * Check if a user has a specific role (considering role hierarchy).
     *
     * @param user the user to check
     * @param roleName the role name to check for
     * @return true if the user has the role (directly or through hierarchy)
     */
    public boolean userHasRole(User user, String roleName) {
        for (String userRole : user.roles()) {
            if (roleService.roleIncludes(userRole, roleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate a cryptographically secure random secret key.
     *
     * @return a base64-encoded secret key
     */
    private static String generateSecretKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
