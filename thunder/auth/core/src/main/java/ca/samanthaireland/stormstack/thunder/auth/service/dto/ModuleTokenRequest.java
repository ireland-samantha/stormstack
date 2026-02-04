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

package ca.samanthaireland.stormstack.thunder.auth.service.dto;

import java.util.Map;
import java.util.Objects;

/**
 * Request DTO for issuing a module token.
 *
 * <p>Module tokens authorize ECS modules to access components within
 * Thunder Engine containers.
 *
 * @param moduleName           the name of the module (required)
 * @param componentPermissions map of "moduleName.componentName" to permission level (owner/read/write)
 * @param superuser            whether this module has superuser privileges (bypasses permission checks)
 * @param containerId          optional container ID to scope the token
 */
public record ModuleTokenRequest(
        String moduleName,
        Map<String, ComponentPermission> componentPermissions,
        boolean superuser,
        String containerId
) {

    /**
     * Permission level for a component.
     */
    public enum ComponentPermission {
        /** Full access - the module owns this component */
        OWNER,
        /** Read-only access to another module's component */
        READ,
        /** Read and write access to another module's component */
        WRITE
    }

    public ModuleTokenRequest {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(componentPermissions, "Component permissions cannot be null");

        if (moduleName.isBlank()) {
            throw new IllegalArgumentException("Module name cannot be blank");
        }

        // Defensive copy
        componentPermissions = Map.copyOf(componentPermissions);
    }

    /**
     * Creates a request for a regular (non-superuser) module.
     *
     * @param moduleName           the module name
     * @param componentPermissions the component permissions
     * @return the request
     */
    public static ModuleTokenRequest regular(String moduleName, Map<String, ComponentPermission> componentPermissions) {
        return new ModuleTokenRequest(moduleName, componentPermissions, false, null);
    }

    /**
     * Creates a request for a superuser module.
     *
     * @param moduleName           the module name
     * @param componentPermissions the component permissions
     * @return the request
     */
    public static ModuleTokenRequest superuser(String moduleName, Map<String, ComponentPermission> componentPermissions) {
        return new ModuleTokenRequest(moduleName, componentPermissions, true, null);
    }

    /**
     * Creates a request scoped to a specific container.
     *
     * @param containerId the container ID
     * @return a new request with the container ID set
     */
    public ModuleTokenRequest withContainerId(String containerId) {
        return new ModuleTokenRequest(moduleName, componentPermissions, superuser, containerId);
    }
}
