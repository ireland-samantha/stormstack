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

package ca.samanthaireland.stormstack.thunder.engine.quarkus.api.config;

import ca.samanthaireland.stormstack.thunder.engine.api.resource.adapter.ModuleTokenAdapter;
import ca.samanthaireland.stormstack.thunder.engine.api.resource.adapter.RestModuleTokenProvider;
import ca.samanthaireland.stormstack.thunder.engine.internal.auth.module.ModuleAuthToken;
import ca.samanthaireland.stormstack.thunder.engine.internal.auth.module.ModuleTokenProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that bridges RestModuleTokenProvider to ModuleTokenProvider.
 *
 * <p>This adapter converts between the REST adapter types (used for HTTP communication)
 * and the engine core types (used for module authentication within the engine).
 */
class RestModuleTokenProviderAdapter implements ModuleTokenProvider {

    private final RestModuleTokenProvider delegate;

    RestModuleTokenProviderAdapter(RestModuleTokenProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public ModuleAuthToken issueToken(String moduleName,
                                      Map<String, ModuleAuthToken.ComponentPermission> componentPermissions,
                                      boolean superuser) {
        // Convert engine permissions to adapter permissions
        Map<String, ModuleTokenAdapter.ComponentPermission> adapterPerms = convertToAdapterPermissions(componentPermissions);

        RestModuleTokenProvider.ModuleTokenResult result = delegate.issueToken(moduleName, adapterPerms, superuser);

        // Convert back to engine auth token
        return new ModuleAuthToken(
                moduleName,
                componentPermissions,
                superuser,
                result.token()
        );
    }

    @Override
    public ModuleAuthToken refreshToken(ModuleAuthToken existingToken,
                                        Map<String, ModuleAuthToken.ComponentPermission> newPermissions) {
        // For REST-based refresh, we re-issue the token with the new permissions
        return issueToken(existingToken.moduleName(), newPermissions, existingToken.superuser());
    }

    @Override
    public ModuleAuthToken verifyToken(String token) {
        // Token verification happens on the auth server side
        // For now, we trust tokens issued by this provider
        // In production, you might want to call a verification endpoint
        throw new UnsupportedOperationException(
                "Token verification is handled by the auth server. " +
                "Use the auth service's token introspection endpoint if needed.");
    }

    private Map<String, ModuleTokenAdapter.ComponentPermission> convertToAdapterPermissions(
            Map<String, ModuleAuthToken.ComponentPermission> enginePerms) {
        Map<String, ModuleTokenAdapter.ComponentPermission> result = new HashMap<>();
        for (Map.Entry<String, ModuleAuthToken.ComponentPermission> entry : enginePerms.entrySet()) {
            ModuleTokenAdapter.ComponentPermission adapterPerm = switch (entry.getValue()) {
                case OWNER -> ModuleTokenAdapter.ComponentPermission.OWNER;
                case READ -> ModuleTokenAdapter.ComponentPermission.READ;
                case WRITE -> ModuleTokenAdapter.ComponentPermission.WRITE;
            };
            result.put(entry.getKey(), adapterPerm);
        }
        return result;
    }
}
