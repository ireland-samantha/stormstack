//! Database models for `StormStack` entities.
//!
//! These models represent the database records and provide
//! serialization/deserialization for `SQLx` queries.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use stormstack_core::{ContainerId, MatchId, TenantId, UserId};

/// Database record for a container.
///
/// Containers provide isolated execution environments for game matches.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContainerRecord {
    /// Unique container identifier.
    pub id: ContainerId,
    /// Tenant that owns this container.
    pub tenant_id: TenantId,
    /// Current simulation tick.
    pub current_tick: u64,
    /// When the container was created.
    pub created_at: DateTime<Utc>,
    /// When the container was last updated.
    pub updated_at: DateTime<Utc>,
}

impl ContainerRecord {
    /// Create a new container record with the given tenant.
    #[must_use]
    pub fn new(tenant_id: TenantId) -> Self {
        let now = Utc::now();
        Self {
            id: ContainerId::new(),
            tenant_id,
            current_tick: 0,
            created_at: now,
            updated_at: now,
        }
    }
}

/// Database record for a match.
///
/// Matches represent active game sessions within a container.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchRecord {
    /// Unique match identifier.
    pub id: MatchId,
    /// Container running this match.
    pub container_id: ContainerId,
    /// Current match state: "pending", "active", or "completed".
    pub state: String,
    /// Game mode for this match.
    pub game_mode: String,
    /// Maximum number of players allowed.
    pub max_players: i32,
    /// Current simulation tick for this match.
    pub current_tick: u64,
    /// List of player user IDs.
    pub players: Vec<UserId>,
    /// When the match was created.
    pub created_at: DateTime<Utc>,
}

impl MatchRecord {
    /// Create a new match record.
    #[must_use]
    pub fn new(container_id: ContainerId, game_mode: String, max_players: i32) -> Self {
        Self {
            id: MatchId::new(),
            container_id,
            state: "pending".to_string(),
            game_mode,
            max_players,
            current_tick: 0,
            players: Vec::new(),
            created_at: Utc::now(),
        }
    }
}

/// Database record for a user.
///
/// Users are authenticated principals within a tenant.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserRecord {
    /// Unique user identifier.
    pub id: UserId,
    /// Tenant that this user belongs to.
    pub tenant_id: TenantId,
    /// User's email address (unique within tenant).
    pub email: String,
    /// Hashed password.
    pub password_hash: String,
    /// User roles for authorization.
    pub roles: Vec<String>,
    /// When the user was created.
    pub created_at: DateTime<Utc>,
}

impl UserRecord {
    /// Create a new user record.
    #[must_use]
    pub fn new(tenant_id: TenantId, email: String, password_hash: String) -> Self {
        Self {
            id: UserId::new(),
            tenant_id,
            email,
            password_hash,
            roles: Vec::new(),
            created_at: Utc::now(),
        }
    }

    /// Create a new user record with roles.
    #[must_use]
    pub fn with_roles(
        tenant_id: TenantId,
        email: String,
        password_hash: String,
        roles: Vec<String>,
    ) -> Self {
        Self {
            id: UserId::new(),
            tenant_id,
            email,
            password_hash,
            roles,
            created_at: Utc::now(),
        }
    }
}
