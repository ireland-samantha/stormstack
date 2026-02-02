//! Test fixtures and builders.

use stormstack_auth::Claims;
use stormstack_core::{TenantId, UserId};

/// Create test claims for a user.
#[must_use]
pub fn test_claims() -> Claims {
    Claims::new(UserId::new(), TenantId::new(), vec!["user".to_string()])
}

/// Create admin test claims.
#[must_use]
pub fn admin_claims() -> Claims {
    Claims::new(UserId::new(), TenantId::new(), vec!["admin".to_string()])
}

/// Create claims for a specific tenant.
#[must_use]
pub fn tenant_claims(tenant_id: TenantId) -> Claims {
    Claims::new(UserId::new(), tenant_id, vec!["user".to_string()])
}
