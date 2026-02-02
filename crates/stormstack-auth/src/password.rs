//! Password hashing service.

use stormstack_core::AuthError;

/// Password hashing service using Argon2.
pub struct PasswordService {
    // TODO: Add Argon2 configuration
}

impl PasswordService {
    /// Create a new password service with default settings.
    #[must_use]
    pub fn new() -> Self {
        Self {}
    }

    /// Hash a password for storage.
    ///
    /// # Errors
    ///
    /// Returns `AuthError::HashingFailed` if hashing fails.
    pub fn hash_password(&self, _password: &str) -> Result<String, AuthError> {
        // TODO: Implement with argon2 crate
        Err(AuthError::HashingFailed("Not implemented".to_string()))
    }

    /// Verify a password against a stored hash.
    ///
    /// # Errors
    ///
    /// Returns `AuthError::InvalidCredentials` if verification fails.
    pub fn verify_password(&self, _password: &str, _hash: &str) -> Result<bool, AuthError> {
        // TODO: Implement with argon2 crate
        // Note: Must use constant-time comparison
        Err(AuthError::HashingFailed("Not implemented".to_string()))
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

    #[test]
    fn service_creation() {
        let _service = PasswordService::new();
    }

    #[test]
    #[ignore = "TODO: Implement password service"]
    fn hash_and_verify_roundtrip() {
        // TODO: Hash password, verify it matches
    }

    #[test]
    #[ignore = "TODO: Implement password service"]
    fn wrong_password_rejected() {
        // TODO: Hash password, verify wrong password fails
    }

    #[test]
    #[ignore = "TODO: Implement password service"]
    fn different_hashes_for_same_password() {
        // TODO: Hash same password twice, verify hashes differ (salt)
    }
}
