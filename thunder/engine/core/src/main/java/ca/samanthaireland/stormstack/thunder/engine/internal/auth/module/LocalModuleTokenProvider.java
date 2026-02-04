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
 * Local implementation of ModuleTokenProvider using in-process JWT signing.
 *
 * <p>This provider uses the local ModuleAuthService for token operations,
 * suitable for:
 * <ul>
 *   <li>Testing and development environments</li>
 *   <li>Standalone deployments without Thunder Auth</li>
 *   <li>Scenarios where external auth service is unavailable</li>
 * </ul>
 *
 * <p>For production deployments with Thunder Auth, use RestModuleTokenProvider instead.
 */
public class LocalModuleTokenProvider implements ModuleTokenProvider {

    private final ModuleAuthService authService;

    /**
     * Creates a new local provider with a randomly generated secret.
     */
    public LocalModuleTokenProvider() {
        this.authService = new ModuleAuthService();
    }

    /**
     * Creates a new local provider with a specific secret (for testing).
     *
     * @param secret the secret to use for signing/verifying tokens
     */
    public LocalModuleTokenProvider(String secret) {
        this.authService = new ModuleAuthService(secret);
    }

    /**
     * Creates a new local provider wrapping an existing auth service.
     *
     * @param authService the auth service to delegate to
     */
    public LocalModuleTokenProvider(ModuleAuthService authService) {
        this.authService = authService;
    }

    @Override
    public ModuleAuthToken issueToken(String moduleName, Map<String, ComponentPermission> componentPermissions, boolean superuser) {
        return authService.issueToken(moduleName, componentPermissions, superuser);
    }

    @Override
    public ModuleAuthToken refreshToken(ModuleAuthToken existingToken, Map<String, ComponentPermission> newPermissions) {
        return authService.refreshToken(existingToken, newPermissions);
    }

    @Override
    public ModuleAuthToken verifyToken(String token) {
        return authService.verifyToken(token);
    }
}
