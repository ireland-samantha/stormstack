//! JWT claims structure.

use serde::{Deserialize, Serialize};
use stormstack_core::{TenantId, UserId};

/// JWT claims structure.
///
/// Contains the user's identity and permissions encoded in the token.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    /// Subject (user ID).
    pub sub: UserId,

    /// Tenant ID.
    pub tenant_id: TenantId,

    /// Assigned roles.
    pub roles: Vec<String>,

    /// Expiration time (Unix timestamp).
    pub exp: i64,

    /// Issued at time (Unix timestamp).
    pub iat: i64,

    /// JWT ID (unique identifier for this token).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub jti: Option<String>,
}

impl Claims {
    /// Create new claims for a user.
    #[must_use]
    pub fn new(user_id: UserId, tenant_id: TenantId, roles: Vec<String>) -> Self {
        let now = chrono::Utc::now().timestamp();
        Self {
            sub: user_id,
            tenant_id,
            roles,
            exp: now + 3600, // 1 hour default
            iat: now,
            jti: Some(uuid::Uuid::new_v4().to_string()),
        }
    }

    /// Check if the claims have expired.
    #[must_use]
    pub fn is_expired(&self) -> bool {
        chrono::Utc::now().timestamp() > self.exp
    }

    /// Check if the user has a specific role.
    #[must_use]
    pub fn has_role(&self, role: &str) -> bool {
        self.roles.iter().any(|r| r == role)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn claims_new() {
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["admin".to_string()],
        );

        assert!(claims.has_role("admin"));
        assert!(!claims.has_role("user"));
        assert!(!claims.is_expired());
    }

    #[test]
    fn claims_expired() {
        let mut claims = Claims::new(UserId::new(), TenantId::new(), vec![]);
        claims.exp = chrono::Utc::now().timestamp() - 1;
        assert!(claims.is_expired());
    }

    #[test]
    fn claims_serialize_roundtrip() {
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["user".to_string(), "moderator".to_string()],
        );

        let json = serde_json::to_string(&claims).expect("serialize");
        let parsed: Claims = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(claims.sub, parsed.sub);
        assert_eq!(claims.tenant_id, parsed.tenant_id);
        assert_eq!(claims.roles, parsed.roles);
    }

    // =========================================================================
    // Additional Claims tests - Alex
    // =========================================================================

    #[test]
    fn claims_has_role_multiple_roles() {
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec![
                "user".to_string(),
                "developer".to_string(),
                "moderator".to_string(),
            ],
        );

        assert!(claims.has_role("user"));
        assert!(claims.has_role("developer"));
        assert!(claims.has_role("moderator"));
        assert!(!claims.has_role("admin"));
        assert!(!claims.has_role("superuser"));
    }

    #[test]
    fn claims_has_role_empty_roles() {
        let claims = Claims::new(UserId::new(), TenantId::new(), vec![]);

        assert!(!claims.has_role("user"));
        assert!(!claims.has_role("admin"));
        assert!(!claims.has_role(""));
    }

    #[test]
    fn claims_has_role_case_sensitive() {
        let claims = Claims::new(
            UserId::new(),
            TenantId::new(),
            vec!["Admin".to_string()],
        );

        assert!(claims.has_role("Admin"));
        assert!(!claims.has_role("admin"));
        assert!(!claims.has_role("ADMIN"));
    }

    #[test]
    fn claims_not_expired_boundary() {
        let mut claims = Claims::new(UserId::new(), TenantId::new(), vec![]);
        // Set expiration to exactly now - should NOT be expired yet
        // (is_expired checks >, not >=)
        claims.exp = chrono::Utc::now().timestamp();
        assert!(!claims.is_expired());
    }

    #[test]
    fn claims_expired_boundary() {
        let mut claims = Claims::new(UserId::new(), TenantId::new(), vec![]);
        // Set expiration to 1 second ago - should be expired
        claims.exp = chrono::Utc::now().timestamp() - 1;
        assert!(claims.is_expired());
    }

    #[test]
    fn claims_far_future_not_expired() {
        let mut claims = Claims::new(UserId::new(), TenantId::new(), vec![]);
        // Set expiration to far future (1 year from now)
        claims.exp = chrono::Utc::now().timestamp() + 365 * 24 * 60 * 60;
        assert!(!claims.is_expired());
    }

    #[test]
    fn claims_jti_is_unique() {
        let claims1 = Claims::new(UserId::new(), TenantId::new(), vec![]);
        let claims2 = Claims::new(UserId::new(), TenantId::new(), vec![]);

        // JTI should be unique for each token
        assert_ne!(claims1.jti, claims2.jti);
        assert!(claims1.jti.is_some());
        assert!(claims2.jti.is_some());
    }

    #[test]
    fn claims_iat_is_recent() {
        let before = chrono::Utc::now().timestamp();
        let claims = Claims::new(UserId::new(), TenantId::new(), vec![]);
        let after = chrono::Utc::now().timestamp();

        // iat should be between before and after
        assert!(claims.iat >= before);
        assert!(claims.iat <= after);
    }

    #[test]
    fn claims_exp_is_one_hour_after_iat() {
        let claims = Claims::new(UserId::new(), TenantId::new(), vec![]);

        // Default expiration is 1 hour (3600 seconds) after iat
        assert_eq!(claims.exp - claims.iat, 3600);
    }

    #[test]
    fn claims_serialize_without_jti() {
        let mut claims = Claims::new(UserId::new(), TenantId::new(), vec!["user".to_string()]);
        claims.jti = None;

        let json = serde_json::to_string(&claims).expect("serialize");
        // jti should not appear in JSON when None (skip_serializing_if)
        assert!(!json.contains("jti"));

        let parsed: Claims = serde_json::from_str(&json).expect("deserialize");
        assert!(parsed.jti.is_none());
    }
}
