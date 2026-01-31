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
 * Implementation of RoleService.
 *
 * <p>Handles role CRUD operations and role hierarchy resolution.
 */
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository cannot be null");
    }

    @Override
    public Role createRole(String name, String description) {
        return createRole(name, description, Set.of());
    }

    @Override
    public Role createRole(String name, String description, Set<RoleId> includedRoleIds) {
        return createRole(name, description, includedRoleIds, Set.of());
    }

    @Override
    public Role createRole(String name, String description, Set<RoleId> includedRoleIds, Set<String> scopes) {
        Objects.requireNonNull(name, "Role name cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        if (roleRepository.existsByName(name)) {
            throw AuthException.roleNameTaken(name);
        }

        // Validate included roles exist
        if (!includedRoleIds.isEmpty()) {
            List<Role> includedRoles = roleRepository.findAllById(includedRoleIds);
            if (includedRoles.size() != includedRoleIds.size()) {
                throw AuthException.invalidRole("One or more included roles do not exist");
            }
        }

        Role role = Role.create(name, description, includedRoleIds, scopes);
        Role saved = roleRepository.save(role);
        log.info("Created role: {} ({}) with {} scopes", saved.name(), saved.id(), scopes.size());
        return saved;
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return roleRepository.findById(id);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return roleRepository.findByName(name);
    }

    @Override
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Override
    public List<Role> findAllById(Set<RoleId> ids) {
        return roleRepository.findAllById(ids);
    }

    @Override
    public Role updateDescription(RoleId roleId, String description) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        Role updated = role.withDescription(description);
        return roleRepository.save(updated);
    }

    @Override
    public Role updateIncludedRoles(RoleId roleId, Set<RoleId> includedRoleIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        // Validate included roles exist
        if (!includedRoleIds.isEmpty()) {
            List<Role> includedRoles = roleRepository.findAllById(includedRoleIds);
            if (includedRoles.size() != includedRoleIds.size()) {
                throw AuthException.invalidRole("One or more included roles do not exist");
            }
        }

        // Prevent circular inclusion
        if (includedRoleIds.contains(roleId)) {
            throw AuthException.invalidRole("Role cannot include itself");
        }

        Role updated = role.withIncludedRoleIds(includedRoleIds);
        return roleRepository.save(updated);
    }

    @Override
    public boolean deleteRole(RoleId roleId) {
        if (roleRepository.findById(roleId).isEmpty()) {
            throw AuthException.roleNotFound(roleId);
        }

        boolean deleted = roleRepository.deleteById(roleId);
        if (deleted) {
            log.info("Deleted role: {}", roleId);
        }
        return deleted;
    }

    @Override
    public boolean roleIncludes(RoleId roleId, RoleId targetRoleId) {
        if (roleId.equals(targetRoleId)) {
            return true;
        }

        Optional<Role> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty()) {
            return false;
        }

        return roleIncludesTransitive(roleOpt.get(), targetRoleId, new HashSet<>());
    }

    @Override
    public boolean roleIncludes(String roleName, String targetRoleName) {
        if (roleName.equalsIgnoreCase(targetRoleName)) {
            return true;
        }

        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        Optional<Role> targetOpt = roleRepository.findByName(targetRoleName);

        if (roleOpt.isEmpty() || targetOpt.isEmpty()) {
            return false;
        }

        return roleIncludes(roleOpt.get().id(), targetOpt.get().id());
    }

    @Override
    public long count() {
        return roleRepository.count();
    }

    /**
     * Recursively checks if a role includes a target role.
     */
    private boolean roleIncludesTransitive(Role role, RoleId targetRoleId, Set<RoleId> visited) {
        if (role.id().equals(targetRoleId)) {
            return true;
        }

        if (visited.contains(role.id())) {
            return false; // Prevent infinite loops
        }
        visited.add(role.id());

        if (role.includedRoleIds().contains(targetRoleId)) {
            return true;
        }

        // Check transitively
        for (RoleId includedId : role.includedRoleIds()) {
            Optional<Role> includedRole = roleRepository.findById(includedId);
            if (includedRole.isPresent() && roleIncludesTransitive(includedRole.get(), targetRoleId, visited)) {
                return true;
            }
        }

        return false;
    }
}
