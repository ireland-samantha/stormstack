package ca.samanthaireland.auth;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * Bootstrap utility for initializing the authentication system.
 *
 * <p>Creates default roles and users when the system starts.
 *
 * <p>Use the {@link Builder} to configure custom repository implementations:
 * <pre>{@code
 * AuthBootstrap bootstrap = AuthBootstrap.builder()
 *     .withUserRepository(myUserRepo)
 *     .withRoleRepository(myRoleRepo)
 *     .withAdminPassword("secret")
 *     .build();
 * }</pre>
 */
@Slf4j
public class AuthBootstrap {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserService userService;
    private final RoleService roleService;
    private final PasswordService passwordService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;

    /**
     * Create an AuthBootstrap with all required services.
     *
     * @param userService the user service
     * @param roleService the role service
     * @param passwordService the password service
     * @param userRepository the user repository
     * @param roleRepository the role repository
     * @param authService the auth service
     */
    public AuthBootstrap(
            UserService userService,
            RoleService roleService,
            PasswordService passwordService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuthService authService) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
    }

    /**
     * Create a new builder for configuring AuthBootstrap.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a fully initialized auth system with default configuration.
     *
     * <p>This factory method creates all auth components and initializes
     * the default roles and admin user. Uses in-memory repositories.
     *
     * <p>For custom repository implementations, use {@link #builder()} instead.
     *
     * @return the initialized AuthBootstrap
     */
    public static AuthBootstrap createDefault() {
        return builder().build();
    }

    /**
     * Create a fully initialized auth system with a specific admin password.
     *
     * <p>This factory method is primarily for testing, allowing explicit control
     * over the admin password without relying on environment variables.
     *
     * <p>For custom repository implementations, use {@link #builder()} instead.
     *
     * @param adminPassword the password to use for the admin user
     * @return the initialized AuthBootstrap
     */
    public static AuthBootstrap createWithAdminPassword(String adminPassword) {
        return builder()
                .withAdminPassword(adminPassword)
                .build();
    }

    /**
     * Builder for constructing AuthBootstrap with custom dependencies.
     * Follows the Dependency Inversion Principle by allowing injection of
     * different repository implementations.
     */
    public static class Builder {
        private UserRepository userRepository;
        private RoleRepository roleRepository;
        private PasswordService passwordService;
        private String adminPassword;

        /**
         * Set a custom user repository implementation.
         *
         * @param userRepository the user repository to use
         * @return this builder
         */
        public Builder withUserRepository(UserRepository userRepository) {
            this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
            return this;
        }

        /**
         * Set a custom role repository implementation.
         *
         * @param roleRepository the role repository to use
         * @return this builder
         */
        public Builder withRoleRepository(RoleRepository roleRepository) {
            this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository cannot be null");
            return this;
        }

        /**
         * Set a custom password service implementation.
         *
         * @param passwordService the password service to use
         * @return this builder
         */
        public Builder withPasswordService(PasswordService passwordService) {
            this.passwordService = Objects.requireNonNull(passwordService, "passwordService cannot be null");
            return this;
        }

        /**
         * Set the admin password to use.
         * If not set, password is resolved from environment variable or generated.
         *
         * @param adminPassword the admin password
         * @return this builder
         */
        public Builder withAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
            return this;
        }

        /**
         * Build the AuthBootstrap instance.
         *
         * @return the configured and initialized AuthBootstrap
         */
        public AuthBootstrap build() {
            // Use defaults for any unset dependencies
            PasswordService effectivePasswordService = passwordService != null
                    ? passwordService : new PasswordService();
            UserRepository effectiveUserRepository = userRepository != null
                    ? userRepository : new InMemoryUserRepository();
            RoleRepository effectiveRoleRepository = roleRepository != null
                    ? roleRepository : new InMemoryRoleRepository();

            // Build services using the configured repositories
            RoleService roleService = new RoleService(effectiveRoleRepository);
            UserService userService = new UserService(effectiveUserRepository, effectivePasswordService, effectiveRoleRepository);
            AuthService authService = new AuthService(effectiveUserRepository, effectivePasswordService, roleService);

            AuthBootstrap bootstrap = new AuthBootstrap(
                    userService, roleService, effectivePasswordService,
                    effectiveUserRepository, effectiveRoleRepository, authService);

            // Initialize with the configured or resolved password
            String effectivePassword = adminPassword != null ? adminPassword : resolveAdminPassword();
            bootstrap.initializeDefaultsWithPassword(effectivePassword);

            return bootstrap;
        }
    }

    /**
     * Initialize default roles and users if they don't exist.
     */
    public void initializeDefaults() {
        createDefaultRoles();
        createAdminUserIfNotExists(resolveAdminPassword());
    }

    /**
     * Initialize default roles and users with a specific admin password.
     *
     * @param adminPassword the password to use for the admin user
     */
    private void initializeDefaultsWithPassword(String adminPassword) {
        createDefaultRoles();
        createAdminUserIfNotExists(adminPassword);
    }

    /**
     * Create a role if it doesn't exist.
     *
     * @param name the role name
     * @param description the role description
     * @param includedRoles roles that this role includes
     */
    public void createRoleIfNotExists(String name, String description, Set<String> includedRoles) {
        if (!roleRepository.existsByName(name)) {
            roleService.createRole(name, description, includedRoles);
            log.info("Created role '{}'", name);
        } else {
            log.debug("Role '{}' already exists", name);
        }
    }

    /**
     * Create a role if it doesn't exist (no included roles).
     *
     * @param name the role name
     * @param description the role description
     */
    public void createRoleIfNotExists(String name, String description) {
        createRoleIfNotExists(name, description, Set.of());
    }

    /**
     * Create the default roles.
     */
    private void createDefaultRoles() {
        // Create base role first (no dependencies)
        createRoleIfNotExists("view_only", "Read-only access to snapshots and status");

        // Create command_manager which includes view_only
        createRoleIfNotExists("command_manager", "Can post commands and view data",
                Set.of("view_only"));

        // Create admin which includes all other roles
        createRoleIfNotExists("admin", "Full access to all endpoints",
                Set.of("command_manager", "view_only"));
    }

    /**
     * Create the default admin user if it doesn't exist.
     *
     * @param adminPassword the password to use for the admin user
     */
    private void createAdminUserIfNotExists(String adminPassword) {
        if (!userRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
            userService.createUser(
                    DEFAULT_ADMIN_USERNAME,
                    adminPassword,
                    Set.of("admin")
            );
            log.info("Created default admin user '{}'", DEFAULT_ADMIN_USERNAME);
            log.warn("SECURITY WARNING: Default admin credentials are in use. Change the password immediately in production!");
        } else {
            log.debug("Admin user already exists");
        }
    }

    /**
     * Get the user service.
     *
     * @return the user service
     */
    public UserService getUserService() {
        return userService;
    }

    /**
     * Get the role service.
     *
     * @return the role service
     */
    public RoleService getRoleService() {
        return roleService;
    }

    /**
     * Get the password service.
     *
     * @return the password service
     */
    public PasswordService getPasswordService() {
        return passwordService;
    }

    /**
     * Get the user repository.
     *
     * @return the user repository
     */
    public UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Get the role repository.
     *
     * @return the role repository
     */
    public RoleRepository getRoleRepository() {
        return roleRepository;
    }

    /**
     * Get the auth service.
     *
     * @return the auth service
     */
    public AuthService getAuthService() {
        return authService;
    }

    /**
     * Resolve the admin password from environment variable, system property, or generate a secure one.
     * Priority: 1. ADMIN_INITIAL_PASSWORD env var, 2. admin.initial.password system property, 3. secure random
     *
     * @return the resolved admin password
     */
    private static String resolveAdminPassword() {
        // Check environment variable first (production use)
        String envPassword = System.getenv("ADMIN_INITIAL_PASSWORD");
        if (envPassword != null && !envPassword.isBlank()) {
            return envPassword;
        }

        // Check system property (useful for testing)
        String propPassword = System.getProperty("admin.initial.password");
        if (propPassword != null && !propPassword.isBlank()) {
            return propPassword;
        }

        // Generate secure random password as fallback
        return generateSecurePassword();
    }

    /**
     * Generate a cryptographically secure random password.
     * Used as fallback when no password is configured.
     *
     * @return a secure random password
     */
    private static String generateSecurePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Log that a random password was generated (but not the password itself)
        System.err.println("WARNING: No ADMIN_INITIAL_PASSWORD environment variable set.");
        System.err.println("Generated random admin password. Set ADMIN_INITIAL_PASSWORD env var for production.");
        return password;
    }
}
