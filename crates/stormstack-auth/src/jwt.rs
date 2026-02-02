//! JWT token service.

use crate::claims::Claims;
use stormstack_core::AuthError;

/// JWT token service for token generation and validation.
pub struct JwtService {
    // TODO: Add secret/key configuration
    _secret: String,
}

impl JwtService {
    /// Create a new JWT service with the given secret.
    #[must_use]
    pub fn new(secret: String) -> Self {
        Self { _secret: secret }
    }

    /// Validate a JWT token and extract claims.
    ///
    /// # Errors
    ///
    /// Returns `AuthError::InvalidToken` if the token is malformed.
    /// Returns `AuthError::TokenExpired` if the token has expired.
    pub fn validate_token(&self, _token: &str) -> Result<Claims, AuthError> {
        // TODO: Implement with jsonwebtoken crate
        Err(AuthError::InvalidToken("Not implemented".to_string()))
    }

    /// Generate a new JWT token for the given claims.
    ///
    /// # Errors
    ///
    /// Returns an error if token generation fails.
    pub fn generate_token(&self, _claims: &Claims) -> Result<String, AuthError> {
        // TODO: Implement with jsonwebtoken crate
        Err(AuthError::InvalidToken("Not implemented".to_string()))
    }

    /// Refresh an existing token.
    ///
    /// Creates a new token with extended expiration.
    ///
    /// # Errors
    ///
    /// Returns an error if the original token is invalid or expired.
    pub fn refresh_token(&self, _token: &str) -> Result<String, AuthError> {
        // TODO: Implement
        Err(AuthError::InvalidToken("Not implemented".to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn service_creation() {
        let _service = JwtService::new("test-secret".to_string());
    }

    #[test]
    #[ignore = "TODO: Implement JWT service"]
    fn validate_and_generate_roundtrip() {
        // TODO: Generate token, validate it, verify claims match
    }

    #[test]
    #[ignore = "TODO: Implement JWT service"]
    fn expired_token_rejected() {
        // TODO: Create expired token, verify validation fails
    }

    #[test]
    #[ignore = "TODO: Implement JWT service"]
    fn invalid_token_rejected() {
        // TODO: Try to validate garbage, verify it fails
    }
}
