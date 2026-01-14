package com.lightningfirefly.auth.cli;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenIssuerTest {

    private static final String TEST_SECRET = "test-secret-key";
    private static final String ISSUER = "https://lightningfirefly.com";

    @Test
    void issueToken_withSingleRole_createsValidJwt() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("test-user", "admin");

        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer(ISSUER)
                .build()
                .verify(token);

        assertThat(jwt.getSubject()).isEqualTo("test-user");
        assertThat(jwt.getClaim("type").asString()).isEqualTo("api_token");
        List<String> roles = jwt.getClaim("roles").asList(String.class);
        assertThat(roles).containsExactly("admin");
    }

    @Test
    void issueToken_withMultipleRoles_includesAllRoles() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("service-account", Set.of("admin", "command_manager", "view_only"));

        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer(ISSUER)
                .build()
                .verify(token);

        assertThat(jwt.getSubject()).isEqualTo("service-account");
        List<String> roles = jwt.getClaim("roles").asList(String.class);
        assertThat(roles).containsExactlyInAnyOrder("admin", "command_manager", "view_only");
    }

    @Test
    void issueToken_hasVeryLongExpiration() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("test-user", "view_only");

        DecodedJWT jwt = JWT.decode(token);
        Instant expiration = jwt.getExpiresAtAsInstant();
        Instant now = Instant.now();

        // Should expire in approximately 100 years (at least 99 years from now)
        long yearsUntilExpiration = ChronoUnit.DAYS.between(now, expiration) / 365;
        assertThat(yearsUntilExpiration).isGreaterThanOrEqualTo(99);
    }

    @Test
    void issueToken_hasCorrectIssuer() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("test-user", "admin");

        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getIssuer()).isEqualTo(ISSUER);
    }

    @Test
    void issueToken_hasIssuedAtTimestamp() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);
        Instant before = Instant.now().minusSeconds(1);

        String token = issuer.issueToken("test-user", "admin");

        DecodedJWT jwt = JWT.decode(token);
        Instant issuedAt = jwt.getIssuedAtAsInstant();
        Instant after = Instant.now().plusSeconds(1);

        assertThat(issuedAt).isAfter(before);
        assertThat(issuedAt).isBefore(after);
    }

    @Test
    void issueToken_canBeVerifiedWithSameSecret() {
        String secret = "my-secure-secret";
        ApiTokenIssuer issuer = new ApiTokenIssuer(secret);

        String token = issuer.issueToken("api-client", "command_manager");

        // Verification should succeed with same secret
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(ISSUER)
                .build()
                .verify(token);

        assertThat(jwt).isNotNull();
    }

    @Test
    void issueToken_withEmptyRoleSet_createsTokenWithNoRoles() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("test-user", Set.of());

        DecodedJWT jwt = JWT.decode(token);
        List<String> roles = jwt.getClaim("roles").asList(String.class);
        assertThat(roles).isEmpty();
    }

    @Test
    void issueToken_withCustomRoleName_createsValidJwt() {
        ApiTokenIssuer issuer = new ApiTokenIssuer(TEST_SECRET);

        String token = issuer.issueToken("test-user", Set.of("custom_role", "another_custom"));

        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(TEST_SECRET))
                .withIssuer(ISSUER)
                .build()
                .verify(token);

        List<String> roles = jwt.getClaim("roles").asList(String.class);
        assertThat(roles).containsExactlyInAnyOrder("custom_role", "another_custom");
    }
}
