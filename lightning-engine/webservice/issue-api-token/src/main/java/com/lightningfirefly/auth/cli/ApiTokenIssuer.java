package com.lightningfirefly.auth.cli;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Issues non-expiring API tokens as JWTs.
 *
 * <p>API tokens are JWTs that never expire (or have a very long expiration).
 * They are used for service-to-service authentication or for automated clients
 * that need persistent access without token refresh.
 */
public class ApiTokenIssuer {

    private static final String ISSUER = "https://lightningfirefly.com";
    private static final String TOKEN_TYPE = "api_token";

    // 100 years expiration (effectively never expires)
    private static final long EXPIRATION_YEARS = 100;

    private final Algorithm algorithm;

    /**
     * Create an issuer with the given secret key.
     *
     * @param secret the secret key used to sign tokens
     */
    public ApiTokenIssuer(String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * Issue a non-expiring API token.
     *
     * @param username the username or service account name
     * @param roles the role names to grant
     * @return the JWT token string
     */
    public String issueToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_YEARS * 365, ChronoUnit.DAYS);

        List<String> roleList = new ArrayList<>(roles);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withIssuedAt(now)
                .withExpiresAt(expiration)
                .withClaim("type", TOKEN_TYPE)
                .withClaim("roles", roleList)
                .sign(algorithm);
    }

    /**
     * Issue a token with a single role.
     *
     * @param username the username
     * @param role the role name to grant
     * @return the JWT token string
     */
    public String issueToken(String username, String role) {
        return issueToken(username, Set.of(role));
    }
}
