package ca.samanthaireland.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Service for password hashing and verification using BCrypt.
 *
 * <p>BCrypt is a password-hashing function that automatically handles
 * salting and is designed to be computationally expensive to resist
 * brute-force attacks.
 *
 * <p>Thread-safe - can be used as a singleton.
 */
public class PasswordService {

    /**
     * Default cost factor for BCrypt hashing.
     * Higher values are more secure but slower.
     * 12 provides good security while keeping latency reasonable.
     */
    private static final int DEFAULT_COST = 12;

    private final int cost;

    /**
     * Create a PasswordService with default cost factor.
     */
    public PasswordService() {
        this(DEFAULT_COST);
    }

    /**
     * Create a PasswordService with custom cost factor.
     *
     * @param cost BCrypt cost factor (4-31, higher = more secure but slower)
     */
    public PasswordService(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("BCrypt cost must be between 4 and 31");
        }
        this.cost = cost;
    }

    /**
     * Hash a password using BCrypt.
     *
     * <p>The returned hash includes the salt and can be stored directly
     * in the database. Each call produces a different hash due to
     * random salt generation.
     *
     * @param password the plain text password
     * @return the BCrypt hash
     */
    public String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray());
    }

    /**
     * Verify a password against a BCrypt hash.
     *
     * @param password the plain text password to verify
     * @param hash the BCrypt hash to check against
     * @return true if the password matches the hash
     */
    public boolean verifyPassword(String password, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }

    /**
     * Check if a hash needs to be rehashed (e.g., if cost factor changed).
     *
     * <p>This can be used during login to upgrade old hashes to
     * use stronger cost factors.
     *
     * @param hash the existing BCrypt hash
     * @return true if the hash should be regenerated
     */
    public boolean needsRehash(String hash) {
        try {
            // Extract cost from hash and compare
            int hashCost = Integer.parseInt(hash.substring(4, 6));
            return hashCost < cost;
        } catch (Exception e) {
            // If we can't parse, assume rehash is needed
            return true;
        }
    }

    /**
     * Get the configured cost factor.
     *
     * @return the BCrypt cost factor
     */
    public int getCost() {
        return cost;
    }
}
