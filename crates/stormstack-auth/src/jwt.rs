//! JWT token service.
//!
//! Provides secure JWT token generation and validation using HMAC-SHA256.
//!
//! # Security
//!
//! - Uses constant-time comparison for token validation
//! - Never logs tokens or secrets
//! - Validates expiration and issued-at times
//! - Supports configurable token lifetime

use crate::claims::Claims;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use stormstack_core::AuthError;
use tracing::{debug, warn};

/// Internal JWT claims structure for jsonwebtoken.
///
/// This is separate from our public Claims to handle the JWT-specific fields.
#[derive(Debug, Serialize, Deserialize)]
struct JwtClaims {
    /// Subject (user ID as string)
    sub: String,
    /// Tenant ID as string
    tenant_id: String,
    /// Roles
    roles: Vec<String>,
    /// Expiration time (required by JWT spec)
    exp: i64,
    /// Issued at time
    iat: i64,
    /// JWT ID
    #[serde(skip_serializing_if = "Option::is_none")]
    jti: Option<String>,
}

impl From<&Claims> for JwtClaims {
    fn from(claims: &Claims) -> Self {
        Self {
            sub: claims.sub.0.to_string(),      // Use inner UUID
            tenant_id: claims.tenant_id.0.to_string(),  // Use inner UUID
            roles: claims.roles.clone(),
            exp: claims.exp,
            iat: claims.iat,
            jti: claims.jti.clone(),
        }
    }
}

/// Configuration for JWT token generation.
#[derive(Debug, Clone)]
pub struct JwtConfig {
    /// Token validity duration in seconds.
    pub token_lifetime_secs: i64,
    /// Refresh token validity duration in seconds.
    pub refresh_lifetime_secs: i64,
    /// Algorithm to use for signing.
    pub algorithm: Algorithm,
    /// Issuer claim value.
    pub issuer: Option<String>,
    /// Audience claim value.
    pub audience: Option<String>,
}

impl Default for JwtConfig {
    fn default() -> Self {
        Self {
            token_lifetime_secs: 3600,         // 1 hour
            refresh_lifetime_secs: 86400 * 7,  // 7 days
            algorithm: Algorithm::HS256,
            issuer: Some("stormstack".to_string()),
            audience: None,
        }
    }
}

/// JWT token service for token generation and validation.
///
/// # Security
///
/// The secret must be at least 32 bytes (256 bits) for HS256.
/// Never log or expose the secret or generated tokens.
pub struct JwtService {
    encoding_key: EncodingKey,
    decoding_key: DecodingKey,
    config: JwtConfig,
    validation: Validation,
}

impl JwtService {
    /// Create a new JWT service with the given secret.
    ///
    /// # Arguments
    ///
    /// * `secret` - HMAC secret key (minimum 32 bytes recommended)
    ///
    /// # Panics
    ///
    /// Panics if the secret is empty.
    #[must_use]
    pub fn new(secret: impl AsRef<[u8]>) -> Self {
        Self::with_config(secret, JwtConfig::default())
    }

    /// Create a new JWT service with custom configuration.
    ///
    /// # Arguments
    ///
    /// * `secret` - HMAC secret key (minimum 32 bytes recommended)
    /// * `config` - JWT configuration
    ///
    /// # Panics
    ///
    /// Panics if the secret is empty.
    #[must_use]
    pub fn with_config(secret: impl AsRef<[u8]>, config: JwtConfig) -> Self {
        let secret = secret.as_ref();
        assert!(!secret.is_empty(), "JWT secret cannot be empty");

        if secret.len() < 32 {
            warn!("JWT secret is less than 32 bytes - consider using a longer secret");
        }

        let encoding_key = EncodingKey::from_secret(secret);
        let decoding_key = DecodingKey::from_secret(secret);

        let mut validation = Validation::new(config.algorithm);
        validation.validate_exp = true;

        if let Some(ref issuer) = config.issuer {
            validation.set_issuer(&[issuer]);
        }

        if let Some(ref audience) = config.audience {
            validation.set_audience(&[audience]);
        }

        Self {
            encoding_key,
            decoding_key,
            config,
            validation,
        }
    }

    /// Validate a JWT token and extract claims.
    ///
    /// # Errors
    ///
    /// Returns `AuthError::InvalidToken` if the token is malformed.
    /// Returns `AuthError::TokenExpired` if the token has expired.
    pub fn validate_token(&self, token: &str) -> Result<Claims, AuthError> {
        debug!("Validating JWT token");

        let token_data = decode::<JwtClaims>(token, &self.decoding_key, &self.validation)
            .map_err(|e| {
                // Don't log the actual token for security
                match e.kind() {
                    jsonwebtoken::errors::ErrorKind::ExpiredSignature => {
                        debug!("Token validation failed: expired");
                        AuthError::TokenExpired
                    }
                    jsonwebtoken::errors::ErrorKind::InvalidSignature => {
                        debug!("Token validation failed: invalid signature");
                        AuthError::InvalidToken("Invalid signature".to_string())
                    }
                    jsonwebtoken::errors::ErrorKind::InvalidToken => {
                        debug!("Token validation failed: malformed");
                        AuthError::InvalidToken("Malformed token".to_string())
                    }
                    _ => {
                        debug!("Token validation failed: {}", e);
                        AuthError::InvalidToken(format!("Validation failed: {e}"))
                    }
                }
            })?;

        let jwt_claims = token_data.claims;

        // Parse user ID
        let user_id = jwt_claims
            .sub
            .parse()
            .map_err(|_| AuthError::InvalidToken("Invalid user ID in token".to_string()))?;

        // Parse tenant ID
        let tenant_id = jwt_claims
            .tenant_id
            .parse()
            .map_err(|_| AuthError::InvalidToken("Invalid tenant ID in token".to_string()))?;

        Ok(Claims {
            sub: user_id,
            tenant_id,
            roles: jwt_claims.roles,
            exp: jwt_claims.exp,
            iat: jwt_claims.iat,
            jti: jwt_claims.jti,
        })
    }

    /// Generate a new JWT token for the given claims.
    ///
    /// # Errors
    ///
    /// Returns an error if token generation fails.
    pub fn generate_token(&self, claims: &Claims) -> Result<String, AuthError> {
        debug!("Generating JWT token for user {}", claims.sub);

        let jwt_claims = JwtClaims::from(claims);

        let header = Header::new(self.config.algorithm);

        encode(&header, &jwt_claims, &self.encoding_key).map_err(|e| {
            warn!("Failed to generate JWT token: {}", e);
            AuthError::InvalidToken(format!("Token generation failed: {e}"))
        })
    }

    /// Generate a token with custom expiration.
    ///
    /// # Arguments
    ///
    /// * `claims` - Base claims (exp will be overwritten)
    /// * `lifetime_secs` - Token lifetime in seconds
    ///
    /// # Errors
    ///
    /// Returns an error if token generation fails.
    pub fn generate_token_with_expiry(
        &self,
        claims: &Claims,
        lifetime_secs: i64,
    ) -> Result<String, AuthError> {
        let mut claims = claims.clone();
        let now = chrono::Utc::now().timestamp();
        claims.iat = now;
        claims.exp = now + lifetime_secs;
        claims.jti = Some(uuid::Uuid::new_v4().to_string());

        self.generate_token(&claims)
    }

    /// Refresh an existing token.
    ///
    /// Creates a new token with extended expiration from the same claims.
    ///
    /// # Errors
    ///
    /// Returns an error if the original token is invalid.
    /// Note: This allows refreshing expired tokens within a grace period.
    pub fn refresh_token(&self, token: &str) -> Result<String, AuthError> {
        debug!("Refreshing JWT token");

        // Create validation that allows expired tokens (for refresh)
        let mut refresh_validation = self.validation.clone();
        refresh_validation.validate_exp = false;

        let token_data =
            decode::<JwtClaims>(token, &self.decoding_key, &refresh_validation).map_err(|e| {
                debug!("Token refresh failed: {}", e);
                AuthError::InvalidToken(format!("Refresh failed: {e}"))
            })?;

        let jwt_claims = token_data.claims;

        // Check if the token is too old to refresh (older than refresh lifetime)
        let now = chrono::Utc::now().timestamp();
        let token_age = now - jwt_claims.iat;
        if token_age > self.config.refresh_lifetime_secs {
            return Err(AuthError::TokenExpired);
        }

        // Parse IDs
        let user_id = jwt_claims
            .sub
            .parse()
            .map_err(|_| AuthError::InvalidToken("Invalid user ID".to_string()))?;

        let tenant_id = jwt_claims
            .tenant_id
            .parse()
            .map_err(|_| AuthError::InvalidToken("Invalid tenant ID".to_string()))?;

        // Create new claims with fresh timestamps
        let new_claims = Claims {
            sub: user_id,
            tenant_id,
            roles: jwt_claims.roles,
            exp: now + self.config.token_lifetime_secs,
            iat: now,
            jti: Some(uuid::Uuid::new_v4().to_string()),
        };

        self.generate_token(&new_claims)
    }

    /// Get the configured token lifetime in seconds.
    #[must_use]
    pub fn token_lifetime_secs(&self) -> i64 {
        self.config.token_lifetime_secs
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_core::{TenantId, UserId};

    fn test_secret() -> Vec<u8> {
        // 32-byte test secret
        b"test-secret-key-for-jwt-testing!".to_vec()
    }

    fn test_claims() -> Claims {
        Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["user".to_string(), "developer".to_string()],
        )
    }

    #[test]
    fn service_creation() {
        let _service = JwtService::new(test_secret());
    }

    #[test]
    #[should_panic(expected = "JWT secret cannot be empty")]
    fn empty_secret_panics() {
        let _service = JwtService::new(Vec::<u8>::new());
    }

    #[test]
    fn validate_and_generate_roundtrip() {
        let service = JwtService::new(test_secret());
        let claims = test_claims();

        let token = service.generate_token(&claims).expect("generate token");
        let validated = service.validate_token(&token).expect("validate token");

        assert_eq!(claims.sub, validated.sub);
        assert_eq!(claims.tenant_id, validated.tenant_id);
        assert_eq!(claims.roles, validated.roles);
    }

    #[test]
    fn expired_token_rejected() {
        let service = JwtService::new(test_secret());
        let mut claims = test_claims();
        claims.exp = chrono::Utc::now().timestamp() - 100; // Expired 100 seconds ago

        let token = service.generate_token(&claims).expect("generate token");
        let result = service.validate_token(&token);

        assert!(matches!(result, Err(AuthError::TokenExpired)));
    }

    #[test]
    fn invalid_token_rejected() {
        let service = JwtService::new(test_secret());

        // Completely invalid token
        let result = service.validate_token("not-a-valid-token");
        assert!(matches!(result, Err(AuthError::InvalidToken(_))));

        // Token signed with different secret
        let other_service = JwtService::new(b"different-secret-key-for-testing!");
        let claims = test_claims();
        let other_token = other_service.generate_token(&claims).expect("generate token");
        let result = service.validate_token(&other_token);
        assert!(matches!(result, Err(AuthError::InvalidToken(_))));
    }

    #[test]
    fn refresh_token_creates_new_token() {
        let service = JwtService::new(test_secret());
        let claims = test_claims();

        let token = service.generate_token(&claims).expect("generate token");
        let refreshed = service.refresh_token(&token).expect("refresh token");

        // Should be a different token
        assert_ne!(token, refreshed);

        // But should have same user/tenant
        let validated = service.validate_token(&refreshed).expect("validate refreshed");
        assert_eq!(claims.sub, validated.sub);
        assert_eq!(claims.tenant_id, validated.tenant_id);
    }

    #[test]
    fn refresh_allows_recently_expired_token() {
        // Use config without issuer to avoid validation complexity
        let config = JwtConfig {
            token_lifetime_secs: 3600,        // 1 hour for refreshed tokens
            refresh_lifetime_secs: 86400,     // 1 day refresh window
            issuer: None,                     // No issuer validation
            audience: None,
            ..JwtConfig::default()
        };
        let service = JwtService::with_config(test_secret(), config);

        // Create a token that expired 120 seconds ago (exceeds default 60s leeway)
        // but was issued recently enough to still be within refresh window
        let now = chrono::Utc::now().timestamp();
        let mut claims = test_claims();
        claims.iat = now - 300;   // Issued 5 minutes ago
        claims.exp = now - 120;   // Expired 2 minutes ago (past 60s leeway)

        let token = service.generate_token(&claims).expect("generate token");

        // Normal validation should fail (token is expired beyond leeway)
        let result = service.validate_token(&token);
        assert!(
            matches!(result, Err(AuthError::TokenExpired)),
            "Expected TokenExpired, got: {:?}",
            result
        );

        // But refresh should work (token was issued within refresh window)
        let refreshed = service.refresh_token(&token).expect("refresh should work");
        let validated = service.validate_token(&refreshed).expect("validate refreshed");
        assert_eq!(claims.sub, validated.sub);
    }

    #[test]
    fn refresh_rejects_very_old_token() {
        let config = JwtConfig {
            token_lifetime_secs: 3600,
            refresh_lifetime_secs: 100, // Short refresh window
            ..JwtConfig::default()
        };
        let service = JwtService::with_config(test_secret(), config);

        let mut claims = test_claims();
        claims.iat = chrono::Utc::now().timestamp() - 200; // Issued 200 seconds ago
        claims.exp = claims.iat + 3600;

        let token = service.generate_token(&claims).expect("generate token");
        let result = service.refresh_token(&token);

        assert!(matches!(result, Err(AuthError::TokenExpired)));
    }

    #[test]
    fn generate_with_custom_expiry() {
        let service = JwtService::new(test_secret());
        let claims = test_claims();

        let token = service
            .generate_token_with_expiry(&claims, 7200) // 2 hours
            .expect("generate token");

        let validated = service.validate_token(&token).expect("validate token");
        let now = chrono::Utc::now().timestamp();

        // Expiry should be approximately 2 hours from now
        assert!(validated.exp > now + 7100);
        assert!(validated.exp < now + 7300);
    }

    #[test]
    fn token_contains_all_roles() {
        let service = JwtService::new(test_secret());
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["admin".to_string(), "moderator".to_string(), "user".to_string()],
        );

        let token = service.generate_token(&claims).expect("generate token");
        let validated = service.validate_token(&token).expect("validate token");

        assert_eq!(validated.roles.len(), 3);
        assert!(validated.roles.contains(&"admin".to_string()));
        assert!(validated.roles.contains(&"moderator".to_string()));
        assert!(validated.roles.contains(&"user".to_string()));
    }
}
