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

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.Role;
import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.model.User;
import ca.samanthaireland.lightning.auth.model.UserId;
import ca.samanthaireland.lightning.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of UserService.
 *
 * <p>Handles user CRUD operations with password hashing and role validation.
 */
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordService passwordService;

    public UserServiceImpl(UserRepository userRepository, RoleService roleService, PasswordService passwordService) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository cannot be null");
        this.roleService = Objects.requireNonNull(roleService, "RoleService cannot be null");
        this.passwordService = Objects.requireNonNull(passwordService, "PasswordService cannot be null");
    }

    @Override
    public User createUser(String username, String password, Set<RoleId> roleIds) {
        return createUser(username, password, roleIds, Set.of());
    }

    @Override
    public User createUser(String username, String password, Set<RoleId> roleIds, Set<String> scopes) {
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");
        Objects.requireNonNull(roleIds, "Role IDs cannot be null");
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        if (userRepository.existsByUsername(username)) {
            throw AuthException.usernameTaken(username);
        }

        // Validate roles exist
        if (!roleIds.isEmpty()) {
            List<Role> roles = roleService.findAllById(roleIds);
            if (roles.size() != roleIds.size()) {
                throw AuthException.invalidRole("One or more roles do not exist");
            }
        }

        String passwordHash = passwordService.hashPassword(password);
        User user = User.create(username, passwordHash, roleIds, scopes);
        User saved = userRepository.save(user);
        log.info("Created user: {} ({}) with {} scopes", saved.username(), saved.id(), scopes.size());
        return saved;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public User updatePassword(UserId userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        String passwordHash = passwordService.hashPassword(newPassword);
        User updated = user.withPasswordHash(passwordHash);
        User saved = userRepository.save(updated);
        log.info("Updated password for user: {}", user.username());
        return saved;
    }

    @Override
    public User updateRoles(UserId userId, Set<RoleId> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        // Validate roles exist
        if (!roleIds.isEmpty()) {
            List<Role> roles = roleService.findAllById(roleIds);
            if (roles.size() != roleIds.size()) {
                throw AuthException.invalidRole("One or more roles do not exist");
            }
        }

        User updated = user.withRoleIds(roleIds);
        User saved = userRepository.save(updated);
        log.info("Updated roles for user: {} -> {}", user.username(), roleIds);
        return saved;
    }

    @Override
    public User updateScopes(UserId userId, Set<String> scopes) {
        Objects.requireNonNull(scopes, "Scopes cannot be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        User updated = user.withScopes(scopes);
        User saved = userRepository.save(updated);
        log.info("Updated scopes for user: {} -> {}", user.username(), scopes);
        return saved;
    }

    @Override
    public User addScope(UserId userId, String scope) {
        Objects.requireNonNull(scope, "Scope cannot be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        Set<String> newScopes = new HashSet<>(user.scopes());
        newScopes.add(scope);

        User updated = user.withScopes(newScopes);
        User saved = userRepository.save(updated);
        log.info("Added scope {} for user: {}", scope, user.username());
        return saved;
    }

    @Override
    public User removeScope(UserId userId, String scope) {
        Objects.requireNonNull(scope, "Scope cannot be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        Set<String> newScopes = new HashSet<>(user.scopes());
        newScopes.remove(scope);

        User updated = user.withScopes(newScopes);
        User saved = userRepository.save(updated);
        log.info("Removed scope {} for user: {}", scope, user.username());
        return saved;
    }

    @Override
    public User addRole(UserId userId, RoleId roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        roleService.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        Set<RoleId> newRoles = new HashSet<>(user.roleIds());
        newRoles.add(roleId);

        User updated = user.withRoleIds(newRoles);
        return userRepository.save(updated);
    }

    @Override
    public User removeRole(UserId userId, RoleId roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        Set<RoleId> newRoles = new HashSet<>(user.roleIds());
        newRoles.remove(roleId);

        User updated = user.withRoleIds(newRoles);
        return userRepository.save(updated);
    }

    @Override
    public User setEnabled(UserId userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        User updated = user.withEnabled(enabled);
        User saved = userRepository.save(updated);
        log.info("Set enabled={} for user: {}", enabled, user.username());
        return saved;
    }

    @Override
    public boolean deleteUser(UserId userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw AuthException.userNotFound(userId);
        }

        boolean deleted = userRepository.deleteById(userId);
        if (deleted) {
            log.info("Deleted user: {}", userId);
        }
        return deleted;
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public long count() {
        return userRepository.count();
    }
}
