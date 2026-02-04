/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleTokenGrantHandlerTest {

    @Mock
    private ModuleTokenService moduleTokenService;

    private ModuleTokenGrantHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ModuleTokenGrantHandler(moduleTokenService);
    }

    @Test
    void getGrantType_returnsModuleToken() {
        assertThat(handler.getGrantType()).isEqualTo(GrantType.MODULE_TOKEN);
    }

    @Test
    void handle_withValidRequest_issuesToken() {
        ServiceClient client = ServiceClient.createConfidential(
                "thunder-engine",
                "secret-hash",
                "Thunder Engine",
                Set.of("module.token.issue"),
                Set.of(GrantType.CLIENT_CREDENTIALS, GrantType.MODULE_TOKEN)
        );

        when(moduleTokenService.issueToken(any())).thenReturn("test.module.jwt.token");
        when(moduleTokenService.getTokenLifetimeSeconds()).thenReturn(31536000);  // 1 year

        Map<String, String> parameters = new HashMap<>();
        parameters.put("grant_type", "urn:stormstack:grant-type:module-token");
        parameters.put("module_name", "GridMapModule");
        parameters.put("component_permissions", "{\"GridMapModule.POSITION_X\": \"owner\", \"EntityModule.ENTITY_TYPE\": \"read\"}");
        parameters.put("superuser", "false");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("test.module.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(31536000);
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void handle_withSuperuserFlag_passesToService() {
        ServiceClient client = ServiceClient.createConfidential(
                "thunder-engine",
                "secret-hash",
                "Thunder Engine",
                Set.of("module.token.issue"),
                Set.of(GrantType.MODULE_TOKEN)
        );

        when(moduleTokenService.issueToken(argThat(request ->
                request.superuser() && request.moduleName().equals("EntityModule"))))
                .thenReturn("superuser.jwt.token");
        when(moduleTokenService.getTokenLifetimeSeconds()).thenReturn(31536000);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "EntityModule");
        parameters.put("component_permissions", "{}");
        parameters.put("superuser", "true");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("superuser.jwt.token");
    }

    @Test
    void handle_withoutClient_throwsInvalidClient() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{}");

        assertThatThrownBy(() -> handler.handle(null, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_CLIENT);
                });
    }

    @Test
    void handle_withoutRequiredScope_throwsInvalidScope() {
        ServiceClient client = ServiceClient.createConfidential(
                "test-client",
                "secret-hash",
                "Test Client",
                Set.of("other.scope"),  // Missing module.token.issue
                Set.of(GrantType.MODULE_TOKEN)
        );

        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{}");

        assertThatThrownBy(() -> handler.handle(client, parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_SCOPE);
                });
    }

    @Test
    void handle_withWildcardScope_succeeds() {
        ServiceClient client = ServiceClient.createConfidential(
                "admin-client",
                "secret-hash",
                "Admin Client",
                Set.of("*"),  // Wildcard allows all
                Set.of(GrantType.MODULE_TOKEN)
        );

        when(moduleTokenService.issueToken(any())).thenReturn("admin.jwt.token");
        when(moduleTokenService.getTokenLifetimeSeconds()).thenReturn(31536000);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{}");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("admin.jwt.token");
    }

    @Test
    void validateRequest_withMissingModuleName_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("component_permissions", "{}");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("module_name");
                });
    }

    @Test
    void validateRequest_withMissingPermissions_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("component_permissions");
                });
    }

    @Test
    void validateRequest_withInvalidJson_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "not valid json");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                });
    }

    @Test
    void validateRequest_withInvalidPermissionLevel_throwsInvalidRequest() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{\"SomeModule.Component\": \"invalid_level\"}");

        assertThatThrownBy(() -> handler.validateRequest(parameters))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.INVALID_REQUEST);
                    assertThat(ae.getMessage()).contains("invalid_level");
                });
    }

    @Test
    void validateRequest_withValidParameters_succeeds() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{\"Module.Component\": \"owner\"}");

        // Should not throw
        handler.validateRequest(parameters);
    }

    @Test
    void handle_withContainerId_includesInRequest() {
        ServiceClient client = ServiceClient.createConfidential(
                "thunder-engine",
                "secret-hash",
                "Thunder Engine",
                Set.of("module.token.issue"),
                Set.of(GrantType.MODULE_TOKEN)
        );

        when(moduleTokenService.issueToken(argThat(request ->
                request.containerId() != null && request.containerId().equals("container-123"))))
                .thenReturn("scoped.jwt.token");
        when(moduleTokenService.getTokenLifetimeSeconds()).thenReturn(31536000);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("module_name", "TestModule");
        parameters.put("component_permissions", "{}");
        parameters.put("container_id", "container-123");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        assertThat(response.accessToken()).isEqualTo("scoped.jwt.token");
    }
}
