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

package ca.samanthaireland.stormstack.thunder.engine.internal.auth.module;

import ca.samanthaireland.stormstack.thunder.engine.internal.auth.module.ModuleAuthToken.ComponentPermission;

import java.util.Map;

/**
 * Provider interface for module token operations.
 *
 * <p>This interface abstracts the module token issuance mechanism, allowing
 * different implementations:
 * <ul>
 *   <li>Local implementation using internal JWT signing (for standalone/testing)</li>
 *   <li>Remote implementation calling Thunder Auth service (for production)</li>
 * </ul>
 *
 * <p>The OnDiskModuleManager uses this interface to obtain tokens for loaded modules.
 */
public interface ModuleTokenProvider {

    /**
     * Issues a JWT token for a module with component permissions.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of "moduleName.componentName" to permission level
     * @param superuser            whether this module has superuser privileges
     * @return the module auth token containing the JWT
     */
    ModuleAuthToken issueToken(String moduleName, Map<String, ComponentPermission> componentPermissions, boolean superuser);

    /**
     * Issues a superuser token for system modules like EntityModule.
     *
     * @param moduleName           the name of the system module
     * @param componentPermissions map of component permissions
     * @return the module auth token with superuser privileges
     */
    default ModuleAuthToken issueSuperuserToken(String moduleName, Map<String, ComponentPermission> componentPermissions) {
        return issueToken(moduleName, componentPermissions, true);
    }

    /**
     * Issues a regular (non-superuser) token for a module.
     *
     * @param moduleName           the name of the module
     * @param componentPermissions map of component permissions
     * @return the module auth token without superuser privileges
     */
    default ModuleAuthToken issueRegularToken(String moduleName, Map<String, ComponentPermission> componentPermissions) {
        return issueToken(moduleName, componentPermissions, false);
    }

    /**
     * Refreshes a module's token with new component permissions.
     *
     * <p>This method re-issues a JWT token with updated permission claims.
     * Use this when a new module is installed and existing modules need
     * access to its components.
     *
     * @param existingToken  the module's current token (used to preserve superuser status)
     * @param newPermissions the updated permission claims
     * @return a new auth token with the updated permissions
     */
    ModuleAuthToken refreshToken(ModuleAuthToken existingToken, Map<String, ComponentPermission> newPermissions);

    /**
     * Verifies a JWT token and extracts the module auth claims.
     *
     * @param token the JWT token string
     * @return the verified module auth token
     * @throws ModuleAuthException if the token is invalid or expired
     */
    ModuleAuthToken verifyToken(String token);
}
