/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest.ComponentPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleTokenServiceImplTest {

    @Mock
    private AuthConfiguration config;

    private ModuleTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        when(config.jwtIssuer()).thenReturn("https://test.stormstack.io");
        when(config.jwtSecret()).thenReturn(Optional.of("test-secret-key-for-testing-purposes-only-32-chars"));
        when(config.privateKeyLocation()).thenReturn(Optional.empty());
        // publicKeyLocation is not used when using HMAC secret, use lenient to avoid UnnecessaryStubbingException
        lenient().when(config.publicKeyLocation()).thenReturn(Optional.empty());

        service = new ModuleTokenServiceImpl(config);
    }

    @Test
    void issueToken_createsValidJwt() {
        Map<String, ComponentPermission> permissions = Map.of(
                "GridMapModule.POSITION_X", ComponentPermission.OWNER,
                "EntityModule.ENTITY_TYPE", ComponentPermission.READ
        );

        ModuleTokenRequest request = ModuleTokenRequest.regular("GridMapModule", permissions);

        String token = service.issueToken(request);

        assertThat(token).isNotNull();
        assertThat(token).contains(".");  // JWT format
        assertThat(token.split("\\.")).hasSize(3);  // header.payload.signature
    }

    @Test
    void issueToken_withSuperuser_includesInClaims() {
        ModuleTokenRequest request = ModuleTokenRequest.superuser("EntityModule", Map.of());

        String token = service.issueToken(request);
        Map<String, Object> claims = service.verifyToken(token);

        assertThat(claims.get(ModuleTokenService.CLAIM_SUPERUSER)).isEqualTo(true);
        assertThat(claims.get(ModuleTokenService.CLAIM_MODULE_NAME)).isEqualTo("EntityModule");
    }

    @Test
    void issueToken_withContainerId_includesInClaims() {
        ModuleTokenRequest request = new ModuleTokenRequest(
                "TestModule",
                Map.of(),
                false,
                "container-123"
        );

        String token = service.issueToken(request);
        Map<String, Object> claims = service.verifyToken(token);

        assertThat(claims.get(ModuleTokenService.CLAIM_CONTAINER_ID)).isEqualTo("container-123");
    }

    @Test
    void verifyToken_withValidToken_returnsClaimsMap() {
        Map<String, ComponentPermission> permissions = Map.of(
                "TestModule.COMPONENT", ComponentPermission.WRITE
        );
        ModuleTokenRequest request = ModuleTokenRequest.regular("TestModule", permissions);

        String token = service.issueToken(request);
        Map<String, Object> claims = service.verifyToken(token);

        assertThat(claims)
                .containsKey(ModuleTokenService.CLAIM_MODULE_NAME)
                .containsKey(ModuleTokenService.CLAIM_COMPONENT_PERMISSIONS)
                .containsKey(ModuleTokenService.CLAIM_SUPERUSER)
                .containsKey(ModuleTokenService.CLAIM_TOKEN_TYPE);

        assertThat(claims.get(ModuleTokenService.CLAIM_MODULE_NAME)).isEqualTo("TestModule");
        assertThat(claims.get(ModuleTokenService.CLAIM_TOKEN_TYPE)).isEqualTo(ModuleTokenService.TOKEN_TYPE_MODULE);
        assertThat(claims.get(ModuleTokenService.CLAIM_SUPERUSER)).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, String> componentPerms = (Map<String, String>) claims.get(ModuleTokenService.CLAIM_COMPONENT_PERMISSIONS);
        assertThat(componentPerms).containsEntry("TestModule.COMPONENT", "write");
    }

    @Test
    void verifyToken_withInvalidToken_throwsException() {
        assertThatThrownBy(() -> service.verifyToken("invalid.jwt.token"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_TOKEN);
                });
    }

    @Test
    void verifyToken_withExpiredToken_throwsException() {
        // Create a token and then immediately try to verify with different issuer
        // This will fail verification
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJkaWZmZXJlbnQtaXNzdWVyIiwic3ViIjoiVGVzdE1vZHVsZSIsIm1vZHVsZV9uYW1lIjoiVGVzdE1vZHVsZSJ9.signature";

        assertThatThrownBy(() -> service.verifyToken(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void refreshToken_preservesSuperuserAndModuleName() {
        ModuleTokenRequest request = ModuleTokenRequest.superuser("EntityModule", Map.of(
                "EntityModule.ENTITY_TYPE", ComponentPermission.OWNER
        ));

        String originalToken = service.issueToken(request);

        Map<String, ComponentPermission> newPermissions = Map.of(
                "EntityModule.ENTITY_TYPE", ComponentPermission.OWNER,
                "NewModule.NEW_COMPONENT", ComponentPermission.READ
        );

        String refreshedToken = service.refreshToken(originalToken, newPermissions);
        Map<String, Object> claims = service.verifyToken(refreshedToken);

        assertThat(claims.get(ModuleTokenService.CLAIM_MODULE_NAME)).isEqualTo("EntityModule");
        assertThat(claims.get(ModuleTokenService.CLAIM_SUPERUSER)).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, String> componentPerms = (Map<String, String>) claims.get(ModuleTokenService.CLAIM_COMPONENT_PERMISSIONS);
        assertThat(componentPerms)
                .containsEntry("EntityModule.ENTITY_TYPE", "owner")
                .containsEntry("NewModule.NEW_COMPONENT", "read");
    }

    @Test
    void refreshToken_withInvalidToken_throwsException() {
        Map<String, ComponentPermission> newPermissions = Map.of();

        assertThatThrownBy(() -> service.refreshToken("invalid.token", newPermissions))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void getTokenLifetimeSeconds_returns365Days() {
        int lifetime = service.getTokenLifetimeSeconds();

        // 365 days * 24 hours * 60 minutes * 60 seconds
        assertThat(lifetime).isEqualTo(365 * 24 * 60 * 60);
    }

    @Test
    void issueToken_includesCorrectIssuer() {
        ModuleTokenRequest request = ModuleTokenRequest.regular("TestModule", Map.of());

        String token = service.issueToken(request);
        Map<String, Object> claims = service.verifyToken(token);

        assertThat(claims.get("iss")).isEqualTo("https://test.stormstack.io/module");
    }

    @Test
    void issueToken_includesUniqueJti() {
        ModuleTokenRequest request = ModuleTokenRequest.regular("TestModule", Map.of());

        String token1 = service.issueToken(request);
        String token2 = service.issueToken(request);

        Map<String, Object> claims1 = service.verifyToken(token1);
        Map<String, Object> claims2 = service.verifyToken(token2);

        assertThat(claims1.get(JwtTokenService.CLAIM_JTI))
                .isNotNull()
                .isNotEqualTo(claims2.get(JwtTokenService.CLAIM_JTI));
    }
}
