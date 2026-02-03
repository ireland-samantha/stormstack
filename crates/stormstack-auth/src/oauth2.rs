//! OAuth2 grant type handlers.
//!
//! Implements OAuth2 token endpoint supporting multiple grant types:
//! - `client_credentials` - For machine-to-machine authentication
//! - `password` - Resource owner password credentials
//! - `refresh_token` - Token refresh
//!
//! # Security
//!
//! - Client secrets are stored as Argon2id hashes
//! - Never logs secrets or tokens
//! - Uses constant-time comparison for credentials

use crate::{Claims, JwtService, PasswordService};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use stormstack_core::{TenantId, UserId};
use tracing::{debug, warn};

/// OAuth2 grant type constants.
pub mod grant_types {
    /// Client credentials grant for machine-to-machine authentication.
    pub const CLIENT_CREDENTIALS: &str = "client_credentials";
    /// Resource owner password credentials grant.
    pub const PASSWORD: &str = "password";
    /// Refresh token grant for obtaining new access tokens.
    pub const REFRESH_TOKEN: &str = "refresh_token";
}

/// OAuth2 token request.
#[derive(Debug, Clone, Deserialize)]
pub struct TokenRequest {
    /// The grant type being requested.
    pub grant_type: String,
    /// Client ID for client_credentials grant.
    pub client_id: Option<String>,
    /// Client secret for client_credentials grant.
    pub client_secret: Option<String>,
    /// Username for password grant.
    pub username: Option<String>,
    /// Password for password grant.
    pub password: Option<String>,
    /// Refresh token for refresh_token grant.
    pub refresh_token: Option<String>,
    /// Requested scope.
    pub scope: Option<String>,
}

/// OAuth2 token response.
#[derive(Debug, Clone, Serialize)]
pub struct TokenResponse {
    /// The access token.
    pub access_token: String,
    /// Token type (always "Bearer").
    pub token_type: String,
    /// Token lifetime in seconds.
    pub expires_in: u64,
    /// Optional refresh token.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refresh_token: Option<String>,
    /// Granted scope.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
}

/// OAuth2 error response per RFC 6749.
#[derive(Debug, Clone, Serialize)]
pub struct TokenError {
    /// Error code.
    pub error: String,
    /// Optional error description.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_description: Option<String>,
}

impl TokenError {
    /// Invalid request error.
    pub fn invalid_request(description: impl Into<String>) -> Self {
        Self {
            error: "invalid_request".to_string(),
            error_description: Some(description.into()),
        }
    }

    /// Invalid client error.
    pub fn invalid_client(description: impl Into<String>) -> Self {
        Self {
            error: "invalid_client".to_string(),
            error_description: Some(description.into()),
        }
    }

    /// Invalid grant error.
    pub fn invalid_grant(description: impl Into<String>) -> Self {
        Self {
            error: "invalid_grant".to_string(),
            error_description: Some(description.into()),
        }
    }

    /// Unauthorized client error.
    pub fn unauthorized_client(description: impl Into<String>) -> Self {
        Self {
            error: "unauthorized_client".to_string(),
            error_description: Some(description.into()),
        }
    }

    /// Unsupported grant type error.
    pub fn unsupported_grant_type() -> Self {
        Self {
            error: "unsupported_grant_type".to_string(),
            error_description: Some("The authorization grant type is not supported".to_string()),
        }
    }

    /// Invalid scope error.
    pub fn invalid_scope(description: impl Into<String>) -> Self {
        Self {
            error: "invalid_scope".to_string(),
            error_description: Some(description.into()),
        }
    }
}

/// Client credentials for OAuth2 client_credentials grant.
#[derive(Clone)]
pub struct ClientCredentials {
    /// Client identifier.
    pub client_id: String,
    /// Hashed client secret (Argon2id).
    pub client_secret_hash: String,
    /// Tenant ID this client belongs to.
    pub tenant_id: TenantId,
    /// Allowed scopes for this client.
    pub allowed_scopes: Vec<String>,
    /// Roles to assign to tokens issued for this client.
    pub roles: Vec<String>,
}

/// User credentials for password grant.
/// In production, this would be backed by a database.
#[derive(Clone)]
pub struct UserCredentials {
    /// User identifier.
    pub user_id: UserId,
    /// Username.
    pub username: String,
    /// Hashed password (Argon2id).
    pub password_hash: String,
    /// Tenant ID.
    pub tenant_id: TenantId,
    /// User roles.
    pub roles: Vec<String>,
}

/// OAuth2 token service supporting multiple grant types.
///
/// # Security
///
/// - Client secrets are verified using constant-time comparison
/// - Passwords are verified using Argon2id
/// - Never logs credentials or tokens
pub struct OAuth2Service {
    jwt_service: JwtService,
    password_service: PasswordService,
    /// Registered OAuth2 clients (for production, use a database).
    clients: HashMap<String, ClientCredentials>,
    /// Registered users (for production, use a database).
    users: HashMap<String, UserCredentials>,
    /// Access token lifetime in seconds.
    access_token_lifetime: u64,
    /// Refresh token lifetime in seconds.
    refresh_token_lifetime: u64,
}

impl OAuth2Service {
    /// Create a new OAuth2 service.
    ///
    /// # Arguments
    ///
    /// * `jwt_service` - JWT service for token generation/validation
    /// * `password_service` - Password service for credential verification
    #[must_use]
    pub fn new(jwt_service: JwtService, password_service: PasswordService) -> Self {
        Self {
            jwt_service,
            password_service,
            clients: HashMap::new(),
            users: HashMap::new(),
            access_token_lifetime: 3600,      // 1 hour
            refresh_token_lifetime: 86400 * 7, // 7 days
        }
    }

    /// Set the access token lifetime.
    #[must_use]
    pub fn with_access_token_lifetime(mut self, seconds: u64) -> Self {
        self.access_token_lifetime = seconds;
        self
    }

    /// Set the refresh token lifetime.
    #[must_use]
    pub fn with_refresh_token_lifetime(mut self, seconds: u64) -> Self {
        self.refresh_token_lifetime = seconds;
        self
    }

    /// Register a client for client_credentials grant.
    ///
    /// # Arguments
    ///
    /// * `creds` - Client credentials to register
    pub fn register_client(&mut self, creds: ClientCredentials) {
        debug!("Registering OAuth2 client: {}", creds.client_id);
        self.clients.insert(creds.client_id.clone(), creds);
    }

    /// Register a user for password grant.
    ///
    /// # Arguments
    ///
    /// * `creds` - User credentials to register
    pub fn register_user(&mut self, creds: UserCredentials) {
        debug!("Registering user: {}", creds.username);
        self.users.insert(creds.username.clone(), creds);
    }

    /// Process an OAuth2 token request.
    ///
    /// # Arguments
    ///
    /// * `request` - The token request
    ///
    /// # Returns
    ///
    /// Token response on success, token error on failure.
    pub fn token(&self, request: &TokenRequest) -> Result<TokenResponse, TokenError> {
        match request.grant_type.as_str() {
            grant_types::CLIENT_CREDENTIALS => self.handle_client_credentials(request),
            grant_types::PASSWORD => self.handle_password_grant(request),
            grant_types::REFRESH_TOKEN => self.handle_refresh_token(request),
            _ => {
                warn!("Unsupported grant type: {}", request.grant_type);
                Err(TokenError::unsupported_grant_type())
            }
        }
    }

    /// Handle client_credentials grant type.
    fn handle_client_credentials(&self, req: &TokenRequest) -> Result<TokenResponse, TokenError> {
        debug!("Processing client_credentials grant");

        // Validate required fields
        let client_id = req
            .client_id
            .as_ref()
            .ok_or_else(|| TokenError::invalid_request("client_id is required"))?;

        let client_secret = req
            .client_secret
            .as_ref()
            .ok_or_else(|| TokenError::invalid_request("client_secret is required"))?;

        // Look up client
        let client = self
            .clients
            .get(client_id)
            .ok_or_else(|| TokenError::invalid_client("Client not found"))?;

        // Verify client secret
        let valid = self
            .password_service
            .verify_password(client_secret, &client.client_secret_hash)
            .map_err(|_| TokenError::invalid_client("Authentication failed"))?;

        if !valid {
            warn!("Invalid client secret for client: {}", client_id);
            return Err(TokenError::invalid_client("Invalid client credentials"));
        }

        // Validate scope if provided
        let granted_scope = self.validate_scope(req.scope.as_deref(), &client.allowed_scopes)?;

        // Generate tokens
        self.generate_tokens(
            UserId::new(), // For client credentials, we generate a service account ID
            client.tenant_id,
            &client.roles,
            granted_scope,
            true, // Include refresh token
        )
    }

    /// Handle password grant type (resource owner password credentials).
    fn handle_password_grant(&self, req: &TokenRequest) -> Result<TokenResponse, TokenError> {
        debug!("Processing password grant");

        // Validate required fields
        let username = req
            .username
            .as_ref()
            .ok_or_else(|| TokenError::invalid_request("username is required"))?;

        let password = req
            .password
            .as_ref()
            .ok_or_else(|| TokenError::invalid_request("password is required"))?;

        // Look up user
        let user = self
            .users
            .get(username)
            .ok_or_else(|| TokenError::invalid_grant("Invalid username or password"))?;

        // Verify password
        let valid = self
            .password_service
            .verify_password(password, &user.password_hash)
            .map_err(|_| TokenError::invalid_grant("Authentication failed"))?;

        if !valid {
            warn!("Invalid password for user: {}", username);
            return Err(TokenError::invalid_grant("Invalid username or password"));
        }

        // Generate tokens
        self.generate_tokens(
            user.user_id,
            user.tenant_id,
            &user.roles,
            req.scope.clone(),
            true, // Include refresh token
        )
    }

    /// Handle refresh_token grant type.
    fn handle_refresh_token(&self, req: &TokenRequest) -> Result<TokenResponse, TokenError> {
        debug!("Processing refresh_token grant");

        // Validate required fields
        let refresh_token = req
            .refresh_token
            .as_ref()
            .ok_or_else(|| TokenError::invalid_request("refresh_token is required"))?;

        // Validate the refresh token
        let claims = self
            .jwt_service
            .validate_token(refresh_token)
            .map_err(|e| {
                warn!("Invalid refresh token: {:?}", e);
                match e {
                    stormstack_core::AuthError::TokenExpired => {
                        TokenError::invalid_grant("Refresh token has expired")
                    }
                    _ => TokenError::invalid_grant("Invalid refresh token"),
                }
            })?;

        // Generate new tokens with same claims
        self.generate_tokens(
            claims.sub,
            claims.tenant_id,
            &claims.roles,
            req.scope.clone(),
            true, // Include new refresh token
        )
    }

    /// Validate requested scope against allowed scopes.
    fn validate_scope(
        &self,
        requested: Option<&str>,
        allowed: &[String],
    ) -> Result<Option<String>, TokenError> {
        match requested {
            None => Ok(None),
            Some(scope_str) => {
                let requested_scopes: Vec<&str> = scope_str.split_whitespace().collect();

                for scope in &requested_scopes {
                    if !allowed.iter().any(|a| a == *scope) {
                        return Err(TokenError::invalid_scope(format!(
                            "Scope '{}' is not allowed",
                            scope
                        )));
                    }
                }

                Ok(Some(requested_scopes.join(" ")))
            }
        }
    }

    /// Generate access and refresh tokens.
    fn generate_tokens(
        &self,
        user_id: UserId,
        tenant_id: TenantId,
        roles: &[String],
        scope: Option<String>,
        include_refresh: bool,
    ) -> Result<TokenResponse, TokenError> {
        // Generate access token
        let claims = Claims::new(user_id, tenant_id, roles.to_vec());
        let access_token = self
            .jwt_service
            .generate_token_with_expiry(&claims, self.access_token_lifetime as i64)
            .map_err(|e| {
                warn!("Failed to generate access token: {:?}", e);
                TokenError::invalid_grant("Token generation failed")
            })?;

        // Generate refresh token if requested
        let refresh_token = if include_refresh {
            let refresh_claims = Claims::new(user_id, tenant_id, roles.to_vec());
            Some(
                self.jwt_service
                    .generate_token_with_expiry(&refresh_claims, self.refresh_token_lifetime as i64)
                    .map_err(|e| {
                        warn!("Failed to generate refresh token: {:?}", e);
                        TokenError::invalid_grant("Token generation failed")
                    })?,
            )
        } else {
            None
        };

        Ok(TokenResponse {
            access_token,
            token_type: "Bearer".to_string(),
            expires_in: self.access_token_lifetime,
            refresh_token,
            scope,
        })
    }

    /// Get a reference to the JWT service.
    #[must_use]
    pub fn jwt_service(&self) -> &JwtService {
        &self.jwt_service
    }

    /// Get a reference to the password service.
    #[must_use]
    pub fn password_service(&self) -> &PasswordService {
        &self.password_service
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{JwtConfig, PasswordConfig};

    fn test_jwt_service() -> JwtService {
        JwtService::with_config(
            b"test-secret-key-for-oauth2-tests!",
            JwtConfig {
                issuer: None, // Disable issuer validation for tests
                audience: None,
                ..JwtConfig::default()
            },
        )
    }

    fn test_password_service() -> PasswordService {
        PasswordService::with_config(PasswordConfig::testing())
    }

    fn create_test_service() -> OAuth2Service {
        let jwt = test_jwt_service();
        let pwd = test_password_service();
        OAuth2Service::new(jwt, pwd)
    }

    fn register_test_client(service: &mut OAuth2Service, client_id: &str, secret: &str) {
        let hash = service.password_service.hash_password(secret).unwrap();
        service.register_client(ClientCredentials {
            client_id: client_id.to_string(),
            client_secret_hash: hash,
            tenant_id: TenantId::new(),
            allowed_scopes: vec!["read".to_string(), "write".to_string()],
            roles: vec!["service".to_string()],
        });
    }

    fn register_test_user(service: &mut OAuth2Service, username: &str, password: &str) {
        let hash = service.password_service.hash_password(password).unwrap();
        service.register_user(UserCredentials {
            user_id: UserId::new(),
            username: username.to_string(),
            password_hash: hash,
            tenant_id: TenantId::new(),
            roles: vec!["user".to_string()],
        });
    }

    #[test]
    fn client_credentials_grant_success() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "test-secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let response = service.token(&request).expect("should succeed");
        assert_eq!(response.token_type, "Bearer");
        assert!(!response.access_token.is_empty());
        assert!(response.refresh_token.is_some());
        assert_eq!(response.expires_in, 3600);
    }

    #[test]
    fn client_credentials_invalid_secret() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "correct-secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("wrong-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_client");
    }

    #[test]
    fn client_credentials_missing_client_id() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: None,
            client_secret: Some("secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
    }

    #[test]
    fn client_credentials_missing_secret() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: None,
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
    }

    #[test]
    fn client_credentials_unknown_client() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("unknown-client".to_string()),
            client_secret: Some("secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_client");
    }

    #[test]
    fn password_grant_success() {
        let mut service = create_test_service();
        register_test_user(&mut service, "testuser", "TestPassword123");

        let request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: Some("testuser".to_string()),
            password: Some("TestPassword123".to_string()),
            refresh_token: None,
            scope: None,
        };

        let response = service.token(&request).expect("should succeed");
        assert_eq!(response.token_type, "Bearer");
        assert!(!response.access_token.is_empty());
        assert!(response.refresh_token.is_some());
    }

    #[test]
    fn password_grant_invalid_password() {
        let mut service = create_test_service();
        register_test_user(&mut service, "testuser", "CorrectPassword1");

        let request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: Some("testuser".to_string()),
            password: Some("WrongPassword1".to_string()),
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_grant");
    }

    #[test]
    fn password_grant_unknown_user() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: Some("unknown".to_string()),
            password: Some("password".to_string()),
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_grant");
    }

    #[test]
    fn password_grant_missing_username() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: Some("password".to_string()),
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
    }

    #[test]
    fn password_grant_missing_password() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: Some("testuser".to_string()),
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
    }

    #[test]
    fn refresh_token_grant_success() {
        let mut service = create_test_service();
        register_test_user(&mut service, "testuser", "TestPassword123");

        // First, get initial tokens
        let initial_request = TokenRequest {
            grant_type: grant_types::PASSWORD.to_string(),
            client_id: None,
            client_secret: None,
            username: Some("testuser".to_string()),
            password: Some("TestPassword123".to_string()),
            refresh_token: None,
            scope: None,
        };

        let initial_response = service.token(&initial_request).expect("should succeed");
        let refresh_token = initial_response.refresh_token.expect("should have refresh token");

        // Now use refresh token
        let refresh_request = TokenRequest {
            grant_type: grant_types::REFRESH_TOKEN.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: Some(refresh_token),
            scope: None,
        };

        let response = service.token(&refresh_request).expect("should succeed");
        assert_eq!(response.token_type, "Bearer");
        assert!(!response.access_token.is_empty());
        // New refresh token should be different
        assert!(response.refresh_token.is_some());
    }

    #[test]
    fn refresh_token_expired() {
        use crate::Claims;

        let jwt = JwtService::with_config(
            b"test-secret-key-for-oauth2-tests!",
            JwtConfig {
                token_lifetime_secs: 3600,
                refresh_lifetime_secs: 3600,
                issuer: None,
                audience: None,
                ..JwtConfig::default()
            },
        );
        let pwd = test_password_service();
        let service = OAuth2Service::new(jwt, pwd);

        // Manually create an expired refresh token
        // JWT library has a 60-second leeway, so we need to expire it by more than that
        let now = chrono::Utc::now().timestamp();
        let mut claims = Claims::new(
            stormstack_core::UserId::new(),
            stormstack_core::TenantId::new(),
            vec!["user".to_string()],
        );
        claims.exp = now - 120; // Expired 2 minutes ago (past 60s leeway)
        claims.iat = now - 3600; // Issued 1 hour ago

        let expired_token = service
            .jwt_service()
            .generate_token(&claims)
            .expect("generate token");

        // Try to use expired refresh token
        let refresh_request = TokenRequest {
            grant_type: grant_types::REFRESH_TOKEN.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: Some(expired_token),
            scope: None,
        };

        let error = service.token(&refresh_request).expect_err("should fail");
        assert_eq!(error.error, "invalid_grant");
        assert!(error
            .error_description
            .as_ref()
            .map(|d| d.contains("expired"))
            .unwrap_or(false));
    }

    #[test]
    fn refresh_token_invalid() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::REFRESH_TOKEN.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: Some("invalid-token".to_string()),
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_grant");
    }

    #[test]
    fn refresh_token_missing() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: grant_types::REFRESH_TOKEN.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
    }

    #[test]
    fn unknown_grant_type_error() {
        let service = create_test_service();

        let request = TokenRequest {
            grant_type: "unknown_grant".to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "unsupported_grant_type");
    }

    #[test]
    fn missing_required_fields() {
        let service = create_test_service();

        // client_credentials without client_id
        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: None,
            client_secret: None,
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_request");
        assert!(error
            .error_description
            .as_ref()
            .map(|d| d.contains("client_id"))
            .unwrap_or(false));
    }

    #[test]
    fn scope_validation() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "test-secret");

        // Request allowed scope
        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: Some("read write".to_string()),
        };

        let response = service.token(&request).expect("should succeed");
        assert_eq!(response.scope, Some("read write".to_string()));

        // Request disallowed scope
        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: Some("admin".to_string()),
        };

        let error = service.token(&request).expect_err("should fail");
        assert_eq!(error.error, "invalid_scope");
    }

    #[test]
    fn token_response_format() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "test-secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: Some("read".to_string()),
        };

        let response = service.token(&request).expect("should succeed");

        // Verify token format
        assert!(response.access_token.split('.').count() == 3); // JWT format
        assert_eq!(response.token_type, "Bearer");
        assert!(response.expires_in > 0);

        if let Some(ref refresh) = response.refresh_token {
            assert!(refresh.split('.').count() == 3); // JWT format
        }
    }

    #[test]
    fn tokens_are_valid_jwt() {
        let mut service = create_test_service();
        register_test_client(&mut service, "test-client", "test-secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let response = service.token(&request).expect("should succeed");

        // Verify access token is valid
        let claims = service
            .jwt_service
            .validate_token(&response.access_token)
            .expect("access token should be valid");
        assert!(claims.roles.contains(&"service".to_string()));

        // Verify refresh token is valid
        if let Some(refresh) = response.refresh_token {
            let claims = service
                .jwt_service
                .validate_token(&refresh)
                .expect("refresh token should be valid");
            assert!(claims.roles.contains(&"service".to_string()));
        }
    }

    #[test]
    fn custom_token_lifetime() {
        let jwt = test_jwt_service();
        let pwd = test_password_service();
        let mut service = OAuth2Service::new(jwt, pwd)
            .with_access_token_lifetime(7200) // 2 hours
            .with_refresh_token_lifetime(86400); // 1 day

        register_test_client(&mut service, "test-client", "test-secret");

        let request = TokenRequest {
            grant_type: grant_types::CLIENT_CREDENTIALS.to_string(),
            client_id: Some("test-client".to_string()),
            client_secret: Some("test-secret".to_string()),
            username: None,
            password: None,
            refresh_token: None,
            scope: None,
        };

        let response = service.token(&request).expect("should succeed");
        assert_eq!(response.expires_in, 7200);
    }
}
