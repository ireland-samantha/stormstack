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
}
