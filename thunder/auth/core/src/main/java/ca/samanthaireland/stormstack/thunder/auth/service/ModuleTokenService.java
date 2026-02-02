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

import ca.samanthaireland.stormstack.thunder.auth.service.dto.ModuleTokenRequest;

import java.util.Map;

/**
 * Service for module token management.
 *
 * <p>Module tokens authorize ECS modules to access components within
 * Thunder Engine containers. Each module receives a JWT containing
 * its component permissions.
 */
public interface ModuleTokenService {

    /**
     * JWT claim key for the token type.
     */
    String CLAIM_TOKEN_TYPE = "token_type";

    /**
     * JWT claim key for the module name.
     */
    String CLAIM_MODULE_NAME = "module_name";

    /**
     * JWT claim key for component permissions map.
     */
    String CLAIM_COMPONENT_PERMISSIONS = "component_permissions";

    /**
     * JWT claim key for superuser status.
     */
    String CLAIM_SUPERUSER = "superuser";

    /**
     * JWT claim key for container ID.
     */
    String CLAIM_CONTAINER_ID = "container_id";

    /**
     * Token type value for module tokens.
     */
    String TOKEN_TYPE_MODULE = "module";

    /**
     * Issues a new module token.
     *
     * @param request the token request containing module name, permissions, etc.
     * @return the issued JWT token
     */
    String issueToken(ModuleTokenRequest request);

    /**
     * Verifies a module token and returns its claims.
     *
     * @param jwtToken the JWT token string
     * @return the decoded claims as a map
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the token is invalid
     */
    Map<String, Object> verifyToken(String jwtToken);

    /**
     * Refreshes a module token with updated permissions.
     *
     * <p>This re-issues a token with the same module name and superuser status,
     * but with updated component permissions.
     *
     * @param existingToken    the current module token
     * @param newPermissions   the updated component permissions
     * @return the new JWT token
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the existing token is invalid
     */
    String refreshToken(String existingToken, Map<String, ModuleTokenRequest.ComponentPermission> newPermissions);

    /**
     * Gets the token lifetime in seconds.
     *
     * @return the token lifetime
     */
    int getTokenLifetimeSeconds();
}
