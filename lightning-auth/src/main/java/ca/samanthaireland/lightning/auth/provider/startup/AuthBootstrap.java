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

package ca.samanthaireland.lightning.auth.provider.startup;

import ca.samanthaireland.lightning.auth.config.AuthConfiguration;
import ca.samanthaireland.lightning.auth.model.Role;
import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.service.RoleService;
import ca.samanthaireland.lightning.auth.service.UserService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Bootstrap component that creates default roles and admin user on startup.
 */
@ApplicationScoped
@Startup
public class AuthBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);

    @Inject
    RoleService roleService;

    @Inject
    UserService userService;

    @Inject
    AuthConfiguration config;

    @PostConstruct
    void init() {
        createDefaultRoles();
        createAdminUserIfConfigured();
    }

    private void createDefaultRoles() {
        // Create view_only role
        RoleId viewOnlyId = createRoleIfNotExists("view_only", "Can view resources and snapshots",
                Set.of(), Set.of());

        // Create command_submit role that includes view_only
        RoleId commandSubmitId = createRoleIfNotExists("command_submit",
                "Can submit commands and view resources",
                Set.of(viewOnlyId), Set.of());

        // Create admin role that includes all others with full admin scopes
        Set<String> adminScopes = Set.of(
                // Control plane scopes
                "control-plane.node.proxy",
                "control-plane.deploy.create",
                "control-plane.deploy.read",
                "control-plane.deploy.delete",
                "control-plane.cluster.read",
                // Auth/IAM scopes
                "auth.user.read",
                "auth.user.create",
                "auth.user.update",
                "auth.user.delete",
                "auth.role.read",
                "auth.role.create",
                "auth.role.update",
                "auth.role.delete",
                "auth.token.read",
                "auth.token.create",
                "auth.token.revoke"
        );
        createRoleIfNotExists("admin",
                "Full administrative access",
                Set.of(commandSubmitId), adminScopes);

        log.info("Default roles initialized");
    }

    private RoleId createRoleIfNotExists(String name, String description, Set<RoleId> includedRoleIds, Set<String> scopes) {
        return roleService.findByName(name)
                .map(Role::id)
                .orElseGet(() -> {
                    Role role = roleService.createRole(name, description, includedRoleIds, scopes);
                    log.info("Created role: {} with {} scopes", name, scopes.size());
                    return role.id();
                });
    }

    private void createAdminUserIfConfigured() {
        // Skip if users already exist
        if (userService.count() > 0) {
            log.debug("Users already exist, skipping admin user creation");
            return;
        }

        // Use configured password or default to "admin"
        String adminPassword = config.initialAdminPassword().orElse("admin");
        RoleId adminRoleId = roleService.findByName("admin")
                .map(Role::id)
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));

        // Create admin user with * scope (full access to all operations)
        Set<String> adminScopes = Set.of("*");
        userService.createUser("admin", adminPassword, Set.of(adminRoleId), adminScopes);

        if (config.initialAdminPassword().isEmpty()) {
            log.warn("Created default admin user (username: admin, password: admin) with full access (*) - CHANGE THIS PASSWORD!");
        } else {
            log.warn("Created initial admin user with configured password and full access (*) - CHANGE THIS PASSWORD!");
        }
    }
}
