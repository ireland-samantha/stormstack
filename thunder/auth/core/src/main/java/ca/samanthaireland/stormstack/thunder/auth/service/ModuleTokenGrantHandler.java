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

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest.ComponentPermission;
import ca.samanthaireland.stormstack.thunder.auth.util.SimpleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the module_token grant type for issuing module authentication tokens.
 *
 * <p>This grant is used by Thunder Engine to request JWT tokens for loaded modules.
 * The engine authenticates as a service client and provides module details.
 *
 * <p>Request parameters:
 * <ul>
 *   <li>grant_type=urn:stormstack:grant-type:module-token (required)</li>
 *   <li>module_name (required) - the name of the module</li>
 *   <li>component_permissions (required) - JSON map of permissions</li>
 *   <li>superuser (optional) - boolean, defaults to false</li>
 *   <li>container_id (optional) - scope token to a specific container</li>
 * </ul>
 *
 * <p>Requires scope: {@code module.token.issue}
 */
public class ModuleTokenGrantHandler implements OAuth2GrantHandler {

    private static final Logger log = LoggerFactory.getLogger(ModuleTokenGrantHandler.class);

    private static final String PARAM_MODULE_NAME = "module_name";
    private static final String PARAM_COMPONENT_PERMISSIONS = "component_permissions";
    private static final String PARAM_SUPERUSER = "superuser";
    private static final String PARAM_CONTAINER_ID = "container_id";
    private static final String REQUIRED_SCOPE = "module.token.issue";

    private final ModuleTokenService moduleTokenService;

    public ModuleTokenGrantHandler(ModuleTokenService moduleTokenService) {
        this.moduleTokenService = moduleTokenService;
    }

    @Override
    public GrantType getGrantType() {
        return GrantType.MODULE_TOKEN;
    }

    @Override
    public OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters) {
        if (client == null) {
            log.error("module_token grant requires authenticated client");
            throw AuthException.invalidClient("Client authentication required");
        }

        // Check required scope
        if (!client.allowedScopes().contains(REQUIRED_SCOPE) && !client.allowedScopes().contains("*")) {
            log.warn("Client {} lacks required scope: {}", client.clientId(), REQUIRED_SCOPE);
            throw AuthException.invalidScope("Client not authorized for scope: " + REQUIRED_SCOPE);
        }

        String moduleName = parameters.get(PARAM_MODULE_NAME);
        String permissionsJson = parameters.get(PARAM_COMPONENT_PERMISSIONS);
        boolean superuser = "true".equalsIgnoreCase(parameters.get(PARAM_SUPERUSER));
        String containerId = parameters.get(PARAM_CONTAINER_ID);

        // Parse component permissions from JSON
        Map<String, ComponentPermission> componentPermissions = parseComponentPermissions(permissionsJson);

        // Build the request
        ModuleTokenRequest request = new ModuleTokenRequest(
                moduleName,
                componentPermissions,
                superuser,
                containerId
        );

        // Issue the token
        String moduleToken = moduleTokenService.issueToken(request);
        int expiresIn = moduleTokenService.getTokenLifetimeSeconds();

        log.info("Issued module token for module: {} via client: {}", moduleName, client.clientId());

        // Return OAuth2-style response with the module token as access_token
        return new OAuth2TokenResponse(
                moduleToken,
                OAuth2TokenResponse.TOKEN_TYPE_BEARER,
                expiresIn,
                null,  // No refresh token for module tokens
                REQUIRED_SCOPE
        );
    }

    @Override
    public void validateRequest(Map<String, String> parameters) {
        if (!parameters.containsKey(PARAM_MODULE_NAME) || parameters.get(PARAM_MODULE_NAME).isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: " + PARAM_MODULE_NAME);
        }

        if (!parameters.containsKey(PARAM_COMPONENT_PERMISSIONS) || parameters.get(PARAM_COMPONENT_PERMISSIONS).isBlank()) {
            throw AuthException.invalidRequest("Missing required parameter: " + PARAM_COMPONENT_PERMISSIONS);
        }

        // Validate that permissions is valid JSON
        try {
            parseComponentPermissions(parameters.get(PARAM_COMPONENT_PERMISSIONS));
        } catch (IllegalArgumentException e) {
            throw AuthException.invalidRequest("Invalid " + PARAM_COMPONENT_PERMISSIONS + ": " + e.getMessage());
        }
    }

    private Map<String, ComponentPermission> parseComponentPermissions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, String> rawMap = SimpleJsonParser.parseObject(json);
            Map<String, ComponentPermission> result = new HashMap<>();

            for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                String permissionStr = entry.getValue().toUpperCase();
                try {
                    ComponentPermission permission = ComponentPermission.valueOf(permissionStr);
                    result.put(entry.getKey(), permission);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Invalid permission level '" + entry.getValue() + "' for key '" + entry.getKey() +
                                    "'. Valid values: OWNER, READ, WRITE");
                }
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }
}
