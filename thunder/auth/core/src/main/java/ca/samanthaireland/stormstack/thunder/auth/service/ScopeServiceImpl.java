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
import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of ScopeService.
 *
 * <p>Handles scope management operations including transitive scope resolution
 * through role hierarchy.
 */
public class ScopeServiceImpl implements ScopeService {

    private static final Logger log = LoggerFactory.getLogger(ScopeServiceImpl.class);

    private final RoleRepository roleRepository;

    public ScopeServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository cannot be null");
    }

    @Override
    public Role updateScopes(RoleId roleId, Set<String> scopes) {
        Objects.requireNonNull(roleId, "RoleId cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        Role updated = role.withScopes(scopes);
        Role saved = roleRepository.save(updated);
        log.info("Updated scopes for role {}: {} scopes", role.name(), scopes.size());
        return saved;
    }

    @Override
    public Role addScope(RoleId roleId, String scope) {
        Objects.requireNonNull(roleId, "RoleId cannot be null");
        Objects.requireNonNull(scope, "Scope cannot be null");

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        if (role.hasScope(scope)) {
            return role; // Already has scope
        }

        Set<String> newScopes = new HashSet<>(role.scopes());
        newScopes.add(scope);

        Role updated = role.withScopes(newScopes);
        Role saved = roleRepository.save(updated);
        log.info("Added scope '{}' to role {}", scope, role.name());
        return saved;
    }

    @Override
    public Role removeScope(RoleId roleId, String scope) {
        Objects.requireNonNull(roleId, "RoleId cannot be null");
        Objects.requireNonNull(scope, "Scope cannot be null");

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        if (!role.hasScope(scope)) {
            return role; // Doesn't have scope
        }

        Set<String> newScopes = new HashSet<>(role.scopes());
        newScopes.remove(scope);

        Role updated = role.withScopes(newScopes);
        Role saved = roleRepository.save(updated);
        log.info("Removed scope '{}' from role {}", scope, role.name());
        return saved;
    }

    @Override
    public Set<String> resolveScopes(RoleId roleId) {
        Objects.requireNonNull(roleId, "RoleId cannot be null");

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        Set<String> allScopes = new HashSet<>();
        collectScopesTransitive(role, allScopes, new HashSet<>());
        return Collections.unmodifiableSet(allScopes);
    }

    @Override
    public Set<String> resolveScopes(Set<RoleId> roleIds) {
        Objects.requireNonNull(roleIds, "RoleIds cannot be null");

        Set<String> allScopes = new HashSet<>();
        Set<RoleId> visited = new HashSet<>();

        for (RoleId roleId : roleIds) {
            roleRepository.findById(roleId).ifPresent(role ->
                    collectScopesTransitive(role, allScopes, visited)
            );
        }

        return Collections.unmodifiableSet(allScopes);
    }

    /**
     * Recursively collects all scopes from a role and its included roles.
     */
    private void collectScopesTransitive(Role role, Set<String> allScopes, Set<RoleId> visited) {
        if (visited.contains(role.id())) {
            return; // Prevent infinite loops from circular inclusions
        }
        visited.add(role.id());

        // Add direct scopes
        allScopes.addAll(role.scopes());

        // Recursively add scopes from included roles
        for (RoleId includedId : role.includedRoleIds()) {
            roleRepository.findById(includedId).ifPresent(includedRole ->
                    collectScopesTransitive(includedRole, allScopes, visited)
            );
        }
    }
}
