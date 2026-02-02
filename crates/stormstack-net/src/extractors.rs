//! Common request extractors.
//!
//! Provides extractors for authentication, pagination, and other common patterns.

use axum::{
    extract::FromRequestParts,
    http::{request::Parts, StatusCode},
};
use serde::Deserialize;
use stormstack_auth::{Claims, JwtService};
use stormstack_core::{TenantId, UserId};
use std::future::Future;

/// Authenticated user extracted from JWT.
#[derive(Debug, Clone)]
pub struct AuthUser {
    /// User identifier.
    pub user_id: UserId,
    /// Tenant identifier.
    pub tenant_id: TenantId,
    /// User roles.
    pub roles: Vec<String>,
    /// Full claims.
    pub claims: Claims,
}

/// State required for authentication extraction.
pub trait AuthState: Send + Sync {
    /// Get the JWT service.
    fn jwt_service(&self) -> &JwtService;
}

/// Extract auth from Authorization header.
impl<S> FromRequestParts<S> for AuthUser
where
    S: AuthState + Send + Sync,
{
    type Rejection = (StatusCode, &'static str);

    fn from_request_parts(
        parts: &mut Parts,
        state: &S,
    ) -> impl Future<Output = Result<Self, Self::Rejection>> + Send {
        async move {
            // Get Authorization header
            let auth_header = parts
                .headers
                .get("Authorization")
                .and_then(|v| v.to_str().ok())
                .ok_or((StatusCode::UNAUTHORIZED, "Missing Authorization header"))?;

            // Extract Bearer token
            let token = auth_header
                .strip_prefix("Bearer ")
                .ok_or((StatusCode::UNAUTHORIZED, "Invalid Authorization format"))?;

            // Validate token
            let claims = state
                .jwt_service()
                .validate_token(token)
                .map_err(|_| (StatusCode::UNAUTHORIZED, "Invalid token"))?;

            Ok(AuthUser {
                user_id: claims.sub,
                tenant_id: claims.tenant_id,
                roles: claims.roles.clone(),
                claims,
            })
        }
    }
}

/// Optional authentication - returns None if not authenticated.
#[derive(Debug, Clone)]
pub struct OptionalAuth(pub Option<AuthUser>);

impl<S> FromRequestParts<S> for OptionalAuth
where
    S: AuthState + Send + Sync,
{
    type Rejection = (StatusCode, &'static str);

    fn from_request_parts(
        parts: &mut Parts,
        state: &S,
    ) -> impl Future<Output = Result<Self, Self::Rejection>> + Send {
        async move {
            // Get Authorization header - if missing, return None
            let auth_header = match parts.headers.get("Authorization").and_then(|v| v.to_str().ok()) {
                Some(h) => h,
                None => return Ok(OptionalAuth(None)),
            };

            // Extract Bearer token - if invalid format, return None
            let token = match auth_header.strip_prefix("Bearer ") {
                Some(t) => t,
                None => return Ok(OptionalAuth(None)),
            };

            // Validate token - if invalid, return None
            let claims = match state.jwt_service().validate_token(token) {
                Ok(c) => c,
                Err(_) => return Ok(OptionalAuth(None)),
            };

            Ok(OptionalAuth(Some(AuthUser {
                user_id: claims.sub,
                tenant_id: claims.tenant_id,
                roles: claims.roles.clone(),
                claims,
            })))
        }
    }
}

/// Pagination parameters.
#[derive(Debug, Clone, Deserialize)]
pub struct Pagination {
    /// Page number (1-indexed).
    #[serde(default = "default_page")]
    pub page: u32,
    /// Items per page.
    #[serde(default = "default_per_page")]
    pub per_page: u32,
}

fn default_page() -> u32 {
    1
}

fn default_per_page() -> u32 {
    20
}

impl Default for Pagination {
    fn default() -> Self {
        Self {
            page: 1,
            per_page: 20,
        }
    }
}

impl Pagination {
    /// Maximum allowed items per page.
    pub const MAX_PER_PAGE: u32 = 100;

    /// Calculate offset for database queries.
    #[must_use]
    pub fn offset(&self) -> u32 {
        (self.page.saturating_sub(1)) * self.per_page
    }

    /// Get clamped per_page value.
    #[must_use]
    pub fn limit(&self) -> u32 {
        self.per_page.min(Self::MAX_PER_PAGE)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pagination_defaults() {
        let p = Pagination::default();
        assert_eq!(p.page, 1);
        assert_eq!(p.per_page, 20);
    }

    #[test]
    fn pagination_offset() {
        let p = Pagination { page: 1, per_page: 20 };
        assert_eq!(p.offset(), 0);

        let p = Pagination { page: 2, per_page: 20 };
        assert_eq!(p.offset(), 20);

        let p = Pagination { page: 5, per_page: 10 };
        assert_eq!(p.offset(), 40);
    }

    #[test]
    fn pagination_limit_clamped() {
        let p = Pagination { page: 1, per_page: 200 };
        assert_eq!(p.limit(), Pagination::MAX_PER_PAGE);
    }

    #[test]
    fn auth_user_fields() {
        let user = AuthUser {
            user_id: UserId::new(),
            tenant_id: TenantId::new(),
            roles: vec!["admin".to_string()],
            claims: Claims::new(UserId::new(), TenantId::new(), vec!["admin".to_string()]),
        };

        assert!(!user.roles.is_empty());
        assert!(user.claims.roles.contains(&"admin".to_string()));
    }
}
