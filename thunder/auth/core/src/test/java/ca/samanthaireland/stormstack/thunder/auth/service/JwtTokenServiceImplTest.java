/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenServiceImplTest {

    @Mock
    private AuthConfiguration config;

    private JwtTokenServiceImpl jwtTokenService;
    private ServiceClient testClient;
    private User testUser;

    @BeforeEach
    void setUp() {
        when(config.jwtIssuer()).thenReturn("https://auth.test.com");
        when(config.privateKeyLocation()).thenReturn(Optional.empty());
        when(config.publicKeyLocation()).thenReturn(Optional.empty());
        when(config.jwtSecret()).thenReturn(Optional.of("test-secret-key-for-hmac-256-algorithm"));

        jwtTokenService = new JwtTokenServiceImpl(config);

        testClient = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("scope1", "scope2"),
                Set.of(GrantType.CLIENT_CREDENTIALS)
        );

        testUser = User.create("testuser", "password-hash", Set.of());
    }

    @Test
    void createServiceToken_generatesValidJwt() {
        String token = jwtTokenService.createServiceToken(testClient, Set.of("scope1", "scope2"), 900);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts

        // Verify the token can be verified
        Map<String, Object> claims = jwtTokenService.verifyToken(token);
        assertThat(claims.get("sub")).isEqualTo("test-client");
        assertThat(claims.get("client_id")).isEqualTo("test-client");
        assertThat(claims.get("token_type")).isEqualTo("service");
        assertThat(claims.get("scope")).asString().contains("scope1");
        assertThat(claims.get("iss")).isEqualTo("https://auth.test.com");
    }

    @Test
    void createUserAccessToken_generatesValidJwt() {
        String token = jwtTokenService.createUserAccessToken(testUser, testClient, Set.of("user.read"), 3600);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);

        Map<String, Object> claims = jwtTokenService.verifyToken(token);
        assertThat(claims.get("sub")).isEqualTo(testUser.id().toString());
        assertThat(claims.get("username")).isEqualTo("testuser");
        assertThat(claims.get("client_id")).isEqualTo("test-client");
        assertThat(claims.get("token_type")).isEqualTo("access");
        assertThat(claims.get("scope")).asString().contains("user.read");
    }

    @Test
    void createRefreshToken_generatesValidJwt() {
        String token = jwtTokenService.createRefreshToken(testUser, testClient, Set.of("user.read"), 604800);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);

        Map<String, Object> claims = jwtTokenService.verifyToken(token);
        assertThat(claims.get("sub")).isEqualTo(testUser.id().toString());
        assertThat(claims.get("client_id")).isEqualTo("test-client");
        assertThat(claims.get("token_type")).isEqualTo("refresh");
    }

    @Test
    void verifyToken_withValidToken_returnsClaims() {
        String token = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), 900);

        Map<String, Object> claims = jwtTokenService.verifyToken(token);

        assertThat(claims).containsKey("iss");
        assertThat(claims).containsKey("sub");
        assertThat(claims).containsKey("exp");
        assertThat(claims).containsKey("iat");
        assertThat(claims).containsKey("jti");
    }

    @Test
    void verifyToken_withInvalidToken_throwsInvalidToken() {
        assertThatThrownBy(() -> jwtTokenService.verifyToken("invalid.token.here"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_TOKEN);
                });
    }

    @Test
    void verifyToken_withTamperedToken_throwsInvalidToken() {
        String token = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), 900);

        // Tamper with the payload
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + ".tampered" + "." + parts[2];

        assertThatThrownBy(() -> jwtTokenService.verifyToken(tamperedToken))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_TOKEN);
                });
    }

    @Test
    void verifyToken_withExpiredToken_throwsInvalidToken() throws InterruptedException {
        // Create a token that expires in 1 second
        String token = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), 1);

        // Wait for expiration
        Thread.sleep(1500);

        assertThatThrownBy(() -> jwtTokenService.verifyToken(token))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_TOKEN);
                });
    }

    @Test
    void getIssuer_returnsConfiguredIssuer() {
        assertThat(jwtTokenService.getIssuer()).isEqualTo("https://auth.test.com");
    }

    @Test
    void createServiceToken_includesJti() {
        String token1 = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), 900);
        String token2 = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), 900);

        Map<String, Object> claims1 = jwtTokenService.verifyToken(token1);
        Map<String, Object> claims2 = jwtTokenService.verifyToken(token2);

        assertThat(claims1.get("jti")).isNotNull();
        assertThat(claims2.get("jti")).isNotNull();
        assertThat(claims1.get("jti")).isNotEqualTo(claims2.get("jti")); // Unique per token
    }

    @Test
    void createServiceToken_setsCorrectExpiration() {
        int expiresIn = 900;
        Instant beforeCreation = Instant.now();

        String token = jwtTokenService.createServiceToken(testClient, Set.of("scope1"), expiresIn);

        Instant afterCreation = Instant.now();
        Map<String, Object> claims = jwtTokenService.verifyToken(token);

        // Expiration is stored as Instant in the claims map
        Object expClaim = claims.get("exp");
        assertThat(expClaim).isNotNull();

        Instant expiration;
        if (expClaim instanceof Instant) {
            expiration = (Instant) expClaim;
        } else if (expClaim instanceof Number) {
            // Handle case where it's stored as epoch seconds
            expiration = Instant.ofEpochSecond(((Number) expClaim).longValue());
        } else {
            throw new AssertionError("Unexpected exp claim type: " + expClaim.getClass());
        }

        assertThat(expiration).isAfter(beforeCreation.plusSeconds(expiresIn - 1));
        assertThat(expiration).isBefore(afterCreation.plusSeconds(expiresIn + 1));
    }

    @Test
    void createUserAccessToken_includesUpnClaim() {
        String token = jwtTokenService.createUserAccessToken(testUser, testClient, Set.of("scope1"), 3600);

        Map<String, Object> claims = jwtTokenService.verifyToken(token);

        assertThat(claims.get("upn")).isEqualTo("testuser");
    }

    @Test
    void createServiceToken_withEmptyScopes_generatesValidToken() {
        String token = jwtTokenService.createServiceToken(testClient, Set.of(), 900);

        assertThat(token).isNotNull();
        Map<String, Object> claims = jwtTokenService.verifyToken(token);
        assertThat(claims.get("scope")).isEqualTo("");
    }

    @Test
    void createServiceToken_withMultipleScopes_joinsWithSpace() {
        String token = jwtTokenService.createServiceToken(testClient, Set.of("read", "write", "admin"), 900);

        Map<String, Object> claims = jwtTokenService.verifyToken(token);
        String scope = (String) claims.get("scope");

        assertThat(scope).contains("read");
        assertThat(scope).contains("write");
        assertThat(scope).contains("admin");
    }
}
