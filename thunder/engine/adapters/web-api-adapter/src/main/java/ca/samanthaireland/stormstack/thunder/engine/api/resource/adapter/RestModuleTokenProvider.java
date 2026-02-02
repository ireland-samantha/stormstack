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

package ca.samanthaireland.stormstack.thunder.engine.api.resource.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST-based provider for module token operations.
 *
 * <p>This class communicates with the Thunder Auth service via the OAuth2 token
 * endpoint using the module_token grant type. It provides a factory method to
 * create ModuleTokenProvider implementations suitable for use with OnDiskModuleManager.
 *
 * <p>Usage:
 * <pre>{@code
 * // Create the provider
 * RestModuleTokenProvider provider = new RestModuleTokenProvider(
 *     "http://localhost:8082",
 *     "thunder-engine",
 *     "client-secret"
 * );
 *
 * // Issue a token
 * ModuleTokenResult result = provider.issueToken("GridMapModule", permissions, false);
 * String jwt = result.token();
 * }</pre>
 *
 * <p>To integrate with OnDiskModuleManager, use the adapter factory:
 * <pre>{@code
 * ModuleTokenProvider engineProvider = provider.toModuleTokenProvider();
 * new OnDiskModuleManager(scanDir, loader, ctx, registry, store, engineProvider);
 * }</pre>
 */
public class RestModuleTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(RestModuleTokenProvider.class);

    private final ModuleTokenAdapter.HttpModuleTokenAdapter adapter;

    /**
     * Creates a new REST-based module token provider.
     *
     * @param authBaseUrl  the base URL of Thunder Auth (e.g., "http://localhost:8082")
     * @param clientId     the service client ID (e.g., "thunder-engine")
     * @param clientSecret the service client secret
     */
    public RestModuleTokenProvider(String authBaseUrl, String clientId, String clientSecret) {
        this.adapter = new ModuleTokenAdapter.HttpModuleTokenAdapter(authBaseUrl, clientId, clientSecret);
    }

    /**
     * Creates a new REST-based module token provider with custom configuration.
     *
     * @param authBaseUrl  the base URL of Thunder Auth
     * @param clientId     the service client ID
     * @param clientSecret the service client secret
     * @param config       the adapter configuration
     */
    public RestModuleTokenProvider(String authBaseUrl, String clientId, String clientSecret, AdapterConfig config) {
        this.adapter = new ModuleTokenAdapter.HttpModuleTokenAdapter(authBaseUrl, clientId, clientSecret, config);
    }

    /**
     * Issues a new module token via the OAuth2 token endpoint.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of "moduleName.componentName" to permission level
     * @param superuser            whether this module has superuser privileges
     * @return the token result containing the JWT
     * @throws ModuleTokenException if token issuance fails
     */
    public ModuleTokenResult issueToken(
            String moduleName,
            Map<String, ModuleTokenAdapter.ComponentPermission> componentPermissions,
            boolean superuser) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(componentPermissions, "Component permissions cannot be null");

        try {
            ModuleTokenAdapter.IssueModuleTokenRequest request = new ModuleTokenAdapter.IssueModuleTokenRequest(
                    moduleName,
                    componentPermissions,
                    superuser,
                    null  // No container scope by default
            );

            ModuleTokenAdapter.ModuleTokenResponse response = adapter.issueToken(request);

            log.debug("Issued module token via REST for module: {}, superuser: {}", moduleName, superuser);

            return new ModuleTokenResult(
                    moduleName,
                    componentPermissions,
                    superuser,
                    response.token(),
                    response.expiresIn()
            );

        } catch (IOException e) {
            log.error("Failed to issue module token via REST for module: {}", moduleName, e);
            throw new ModuleTokenException("Failed to issue module token: " + e.getMessage(), e);
        }
    }

    /**
     * Issues a regular (non-superuser) module token.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of component permissions
     * @return the token result
     */
    public ModuleTokenResult issueRegularToken(
            String moduleName,
            Map<String, ModuleTokenAdapter.ComponentPermission> componentPermissions) {
        return issueToken(moduleName, componentPermissions, false);
    }

    /**
     * Issues a superuser module token.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of component permissions
     * @return the token result
     */
    public ModuleTokenResult issueSuperuserToken(
            String moduleName,
            Map<String, ModuleTokenAdapter.ComponentPermission> componentPermissions) {
        return issueToken(moduleName, componentPermissions, true);
    }

    /**
     * Result of a module token issuance.
     *
     * @param moduleName           the module name
     * @param componentPermissions the granted permissions
     * @param superuser            whether the module has superuser privileges
     * @param token                the JWT token string
     * @param expiresIn            token lifetime in seconds
     */
    public record ModuleTokenResult(
            String moduleName,
            Map<String, ModuleTokenAdapter.ComponentPermission> componentPermissions,
            boolean superuser,
            String token,
            int expiresIn
    ) {}

    /**
     * Exception thrown when module token operations fail.
     */
    public static class ModuleTokenException extends RuntimeException {
        public ModuleTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
