package ca.samanthaireland.auth;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for user management operations.
 *
 * <p>This service provides CRUD operations for users and handles
 * password hashing transparently.
 *
 * <p>Thread-safe.
 */
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final RoleRepository roleRepository;

    /**
     * Create a UserService.
     *
     * @param userRepository the user repository
     * @param passwordService the password service
     * @param roleRepository the role repository for validation
     */
    public UserService(UserRepository userRepository, PasswordService passwordService, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.roleRepository = roleRepository;
    }

    /**
     * Create a new user.
     *
     * @param username the username
     * @param password the plain text password
     * @param roles the role names to assign
     * @return the created user
     * @throws AuthException if username is already taken or roles don't exist
     */
    public User createUser(String username, String password, Set<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw AuthException.usernameTaken(username);
        }

        // Validate roles exist
        for (String roleName : roles) {
            if (!roleRepository.existsByName(roleName)) {
                throw AuthException.invalidRole(roleName);
            }
        }

        String passwordHash = passwordService.hashPassword(password);
        User user = new User(0, username, passwordHash, roles, Instant.now(), true);
        User saved = userRepository.save(user);

        log.info("Created user '{}' with roles {}", username, roles);
        return saved;
    }

    /**
     * Create a new user with default view_only role.
     *
     * @param username the username
     * @param password the plain text password
     * @return the created user
     * @throws AuthException if username is already taken
     */
    public User createUser(String username, String password) {
        return createUser(username, password, Set.of("view_only"));
    }

    /**
     * Find a user by ID.
     *
     * @param id the user ID
     * @return the user if found
     */
    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    /**
     * Find a user by username.
     *
     * @param username the username
     * @return the user if found
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Get all users.
     *
     * @return list of all users
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Update a user's password.
     *
     * @param userId the user ID
     * @param newPassword the new plain text password
     * @return the updated user
     * @throws AuthException if user not found
     */
    public User updatePassword(long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("ID: " + userId));

        String newHash = passwordService.hashPassword(newPassword);
        User updated = user.withPasswordHash(newHash);
        userRepository.save(updated);

        log.info("Updated password for user '{}'", user.username());
        return updated;
    }

    /**
     * Update a user's roles.
     *
     * @param userId the user ID
     * @param roles the new role names
     * @return the updated user
     * @throws AuthException if user not found or roles don't exist
     */
    public User updateRoles(long userId, Set<String> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("ID: " + userId));

        // Validate roles exist
        for (String roleName : roles) {
            if (!roleRepository.existsByName(roleName)) {
                throw AuthException.invalidRole(roleName);
            }
        }

        User updated = user.withRoles(roles);
        userRepository.save(updated);

        log.info("Updated roles for user '{}' to {}", user.username(), roles);
        return updated;
    }

    /**
     * Add a role to a user.
     *
     * @param userId the user ID
     * @param roleName the role name to add
     * @return the updated user
     * @throws AuthException if user not found or role doesn't exist
     */
    public User addRole(long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("ID: " + userId));

        if (!roleRepository.existsByName(roleName)) {
            throw AuthException.invalidRole(roleName);
        }

        Set<String> newRoles = new java.util.HashSet<>(user.roles());
        newRoles.add(roleName);

        User updated = user.withRoles(newRoles);
        userRepository.save(updated);

        log.info("Added role '{}' to user '{}'", roleName, user.username());
        return updated;
    }

    /**
     * Remove a role from a user.
     *
     * @param userId the user ID
     * @param roleName the role name to remove
     * @return the updated user
     * @throws AuthException if user not found
     */
    public User removeRole(long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("ID: " + userId));

        Set<String> newRoles = new java.util.HashSet<>(user.roles());
        newRoles.remove(roleName);

        User updated = user.withRoles(newRoles);
        userRepository.save(updated);

        log.info("Removed role '{}' from user '{}'", roleName, user.username());
        return updated;
    }

    /**
     * Enable or disable a user.
     *
     * @param userId the user ID
     * @param enabled whether the user should be enabled
     * @return the updated user
     * @throws AuthException if user not found
     */
    public User setEnabled(long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound("ID: " + userId));

        User updated = user.withEnabled(enabled);
        userRepository.save(updated);

        log.info("User '{}' {}", user.username(), enabled ? "enabled" : "disabled");
        return updated;
    }

    /**
     * Delete a user.
     *
     * @param userId the user ID
     * @return true if user was deleted
     */
    public boolean deleteUser(long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            userRepository.deleteById(userId);
            log.info("Deleted user '{}'", user.get().username());
            return true;
        }
        return false;
    }

    /**
     * Count total users.
     *
     * @return the number of users
     */
    public long count() {
        return userRepository.count();
    }

    /**
     * Check if a username is available.
     *
     * @param username the username to check
     * @return true if username is available
     */
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }
}
