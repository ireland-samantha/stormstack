package ca.samanthaireland.auth;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Bootstrap utility for initializing the authentication system.
 *
 * <p>Creates default roles and users when the system starts.
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
     * Create a fully initialized auth system with default configuration.
     *
     * <p>This factory method creates all auth components and initializes
     * the default roles and admin user.
     *
     * @return the initialized AuthBootstrap
     */
    public static AuthBootstrap createDefault() {
        PasswordService passwordService = new PasswordService();
        UserRepository userRepository = new InMemoryUserRepository();
        RoleRepository roleRepository = new InMemoryRoleRepository();
        RoleService roleService = new RoleService(roleRepository);
        UserService userService = new UserService(userRepository, passwordService, roleRepository);
        AuthService authService = new AuthService(userRepository, passwordService, roleService);

        AuthBootstrap bootstrap = new AuthBootstrap(
                userService, roleService, passwordService, userRepository, roleRepository, authService);
        bootstrap.initializeDefaults();

        return bootstrap;
    }

    /**
     * Initialize default roles and users if they don't exist.
     */
    public void initializeDefaults() {
        createDefaultRoles();
        createAdminUserIfNotExists();
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
     */
    private void createAdminUserIfNotExists() {
        if (!userRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
            // Resolve password at runtime to allow configuration via env vars or system properties
            String adminPassword = resolveAdminPassword();
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
