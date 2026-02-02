//! Password hashing service.
//!
//! Provides secure password hashing using Argon2id, the recommended
//! algorithm for password hashing.
//!
//! # Security
//!
//! - Uses Argon2id (hybrid of Argon2i and Argon2d)
//! - Generates cryptographically secure random salts
//! - Uses recommended OWASP parameters
//! - Constant-time verification (handled by argon2 crate)
//! - Never logs passwords

use argon2::{
    password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Algorithm, Argon2, Params, Version,
};
use rand_core::OsRng;
use stormstack_core::AuthError;
use tracing::{debug, warn};

/// Configuration for Argon2 password hashing.
///
/// Default values follow OWASP recommendations for password storage.
#[derive(Debug, Clone)]
pub struct PasswordConfig {
    /// Memory cost in KiB.
    /// Default: 19456 KiB (19 MiB) - OWASP minimum recommendation
    pub memory_cost_kib: u32,

    /// Number of iterations (time cost).
    /// Default: 2 - OWASP minimum recommendation
    pub time_cost: u32,

    /// Degree of parallelism.
    /// Default: 1
    pub parallelism: u32,

    /// Output hash length in bytes.
    /// Default: 32 bytes (256 bits)
    pub output_len: usize,
}

impl Default for PasswordConfig {
    fn default() -> Self {
        // OWASP recommended minimum parameters for Argon2id
        // See: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
        Self {
            memory_cost_kib: 19456, // 19 MiB
            time_cost: 2,
            parallelism: 1,
            output_len: 32,
        }
    }
}

impl PasswordConfig {
    /// Create a lightweight configuration for testing.
    ///
    /// **WARNING**: Do not use in production! These parameters are too weak.
    #[must_use]
    pub fn testing() -> Self {
        Self {
            memory_cost_kib: 1024, // 1 MiB - fast for tests
            time_cost: 1,
            parallelism: 1,
            output_len: 32,
        }
    }
}

/// Password hashing service using Argon2id.
///
/// # Security
///
/// This service uses Argon2id, which combines the benefits of
/// Argon2i (side-channel resistance) and Argon2d (GPU resistance).
///
/// The default parameters follow OWASP recommendations. Never reduce
/// these parameters in production.
pub struct PasswordService {
    argon2: Argon2<'static>,
}

impl PasswordService {
    /// Create a new password service with default (OWASP-recommended) settings.
    ///
    /// # Panics
    ///
    /// Panics if the Argon2 parameters are invalid (should not happen with defaults).
    #[must_use]
    pub fn new() -> Self {
        Self::with_config(PasswordConfig::default())
    }

    /// Create a new password service with custom configuration.
    ///
    /// # Arguments
    ///
    /// * `config` - Argon2 parameters
    ///
    /// # Panics
    ///
    /// Panics if the parameters are invalid.
    #[must_use]
    pub fn with_config(config: PasswordConfig) -> Self {
        let params = Params::new(
            config.memory_cost_kib,
            config.time_cost,
            config.parallelism,
            Some(config.output_len),
        )
        .expect("Invalid Argon2 parameters");

        let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);

        Self { argon2 }
    }

    /// Hash a password for storage.
    ///
    /// # Arguments
    ///
    /// * `password` - The plaintext password to hash
    ///
    /// # Returns
    ///
    /// The password hash in PHC string format, which includes:
    /// - Algorithm identifier
    /// - Parameters (memory, time, parallelism)
    /// - Salt
    /// - Hash
    ///
    /// # Errors
    ///
    /// Returns `AuthError::HashingFailed` if hashing fails.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use stormstack_auth::PasswordService;
    ///
    /// let service = PasswordService::new();
    /// let hash = service.hash_password("my_secure_password").unwrap();
    /// // Store `hash` in database
    /// ```
    pub fn hash_password(&self, password: &str) -> Result<String, AuthError> {
        debug!("Hashing password");

        if password.is_empty() {
            return Err(AuthError::HashingFailed("Password cannot be empty".to_string()));
        }

        // Generate a cryptographically secure random salt
        let salt = SaltString::generate(&mut OsRng);

        // Hash the password
        let hash = self
            .argon2
            .hash_password(password.as_bytes(), &salt)
            .map_err(|e| {
                warn!("Password hashing failed: {}", e);
                AuthError::HashingFailed(format!("Hashing failed: {e}"))
            })?;

        Ok(hash.to_string())
    }

    /// Verify a password against a stored hash.
    ///
    /// # Arguments
    ///
    /// * `password` - The plaintext password to verify
    /// * `hash` - The stored hash in PHC string format
    ///
    /// # Returns
    ///
    /// `Ok(true)` if the password matches, `Ok(false)` if it doesn't.
    ///
    /// # Errors
    ///
    /// Returns `AuthError::HashingFailed` if the hash is malformed.
    ///
    /// # Security
    ///
    /// This function uses constant-time comparison to prevent timing attacks.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use stormstack_auth::PasswordService;
    ///
    /// let service = PasswordService::new();
    /// let hash = service.hash_password("correct_password").unwrap();
    ///
    /// assert!(service.verify_password("correct_password", &hash).unwrap());
    /// assert!(!service.verify_password("wrong_password", &hash).unwrap());
    /// ```
    pub fn verify_password(&self, password: &str, hash: &str) -> Result<bool, AuthError> {
        debug!("Verifying password");

        // Parse the stored hash
        let parsed_hash = PasswordHash::new(hash).map_err(|e| {
            warn!("Failed to parse password hash: {}", e);
            AuthError::HashingFailed(format!("Invalid hash format: {e}"))
        })?;

        // Verify the password (constant-time comparison)
        match self.argon2.verify_password(password.as_bytes(), &parsed_hash) {
            Ok(()) => Ok(true),
            Err(argon2::password_hash::Error::Password) => Ok(false),
            Err(e) => {
                warn!("Password verification failed: {}", e);
                Err(AuthError::HashingFailed(format!("Verification failed: {e}")))
            }
        }
    }

    /// Check if a password meets minimum strength requirements.
    ///
    /// # Arguments
    ///
    /// * `password` - The password to check
    ///
    /// # Returns
    ///
    /// `true` if the password meets requirements, `false` otherwise.
    ///
    /// # Requirements
    ///
    /// - Minimum 8 characters
    /// - At least one uppercase letter
    /// - At least one lowercase letter
    /// - At least one digit
    #[must_use]
    pub fn check_password_strength(password: &str) -> bool {
        if password.len() < 8 {
            return false;
        }

        let has_upper = password.chars().any(|c| c.is_uppercase());
        let has_lower = password.chars().any(|c| c.is_lowercase());
        let has_digit = password.chars().any(|c| c.is_ascii_digit());

        has_upper && has_lower && has_digit
    }
}

impl Default for PasswordService {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_service() -> PasswordService {
        // Use faster parameters for testing
        PasswordService::with_config(PasswordConfig::testing())
    }

    #[test]
    fn service_creation() {
        let _service = PasswordService::new();
    }

    #[test]
    fn hash_and_verify_roundtrip() {
        let service = test_service();
        let password = "SecureP@ssw0rd!";

        let hash = service.hash_password(password).expect("hash password");

        // Hash should be in PHC format
        assert!(hash.starts_with("$argon2id$"));

        // Verification should succeed
        let result = service.verify_password(password, &hash).expect("verify");
        assert!(result);
    }

    #[test]
    fn wrong_password_rejected() {
        let service = test_service();
        let password = "CorrectPassword1";
        let wrong_password = "WrongPassword1";

        let hash = service.hash_password(password).expect("hash password");

        let result = service
            .verify_password(wrong_password, &hash)
            .expect("verify");
        assert!(!result);
    }

    #[test]
    fn different_hashes_for_same_password() {
        let service = test_service();
        let password = "SamePassword123";

        let hash1 = service.hash_password(password).expect("hash 1");
        let hash2 = service.hash_password(password).expect("hash 2");

        // Hashes should be different (different salts)
        assert_ne!(hash1, hash2);

        // But both should verify correctly
        assert!(service.verify_password(password, &hash1).expect("verify 1"));
        assert!(service.verify_password(password, &hash2).expect("verify 2"));
    }

    #[test]
    fn empty_password_rejected() {
        let service = test_service();

        let result = service.hash_password("");
        assert!(matches!(result, Err(AuthError::HashingFailed(_))));
    }

    #[test]
    fn invalid_hash_format_rejected() {
        let service = test_service();

        let result = service.verify_password("password", "not-a-valid-hash");
        assert!(matches!(result, Err(AuthError::HashingFailed(_))));
    }

    #[test]
    fn password_strength_minimum_length() {
        assert!(!PasswordService::check_password_strength("Aa1"));
        assert!(!PasswordService::check_password_strength("Aa12345")); // 7 chars
        assert!(PasswordService::check_password_strength("Aa123456")); // 8 chars
    }

    #[test]
    fn password_strength_requires_uppercase() {
        assert!(!PasswordService::check_password_strength("abcd1234")); // no upper
        assert!(PasswordService::check_password_strength("Abcd1234"));
    }

    #[test]
    fn password_strength_requires_lowercase() {
        assert!(!PasswordService::check_password_strength("ABCD1234")); // no lower
        assert!(PasswordService::check_password_strength("ABCd1234"));
    }

    #[test]
    fn password_strength_requires_digit() {
        assert!(!PasswordService::check_password_strength("Abcdefgh")); // no digit
        assert!(PasswordService::check_password_strength("Abcdefg1"));
    }

    #[test]
    fn hash_format_is_phc() {
        let service = test_service();
        let hash = service.hash_password("TestPassword1").expect("hash");

        // PHC format: $algorithm$version$params$salt$hash
        assert!(hash.starts_with("$argon2id$v=19$"));
        assert!(hash.contains("m="));
        assert!(hash.contains("t="));
        assert!(hash.contains("p="));
    }

    #[test]
    fn unicode_passwords_supported() {
        let service = test_service();
        let password = "Pässwörd123!日本語";

        let hash = service.hash_password(password).expect("hash");
        assert!(service.verify_password(password, &hash).expect("verify"));
    }

    #[test]
    fn long_passwords_supported() {
        let service = test_service();
        let password = "A1".repeat(500); // 1000 character password

        let hash = service.hash_password(&password).expect("hash");
        assert!(service.verify_password(&password, &hash).expect("verify"));
    }
}
