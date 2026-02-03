//! Strongly-typed identifiers for `StormStack` entities.
//!
//! Using newtype wrappers provides compile-time safety against
//! mixing up different ID types (e.g., passing a `MatchId` where
//! a `ContainerId` is expected).

use serde::{Deserialize, Serialize};
use std::fmt;
use std::str::FromStr;
use uuid::Uuid;

/// Strongly-typed entity identifier.
///
/// Entities are the fundamental unit in the ECS system.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct EntityId(pub u64);

impl fmt::Display for EntityId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Entity({})", self.0)
    }
}

impl From<u64> for EntityId {
    fn from(id: u64) -> Self {
        Self(id)
    }
}

/// Strongly-typed container identifier.
///
/// Containers provide isolated execution environments for game matches.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ContainerId(pub Uuid);

impl ContainerId {
    /// Create a new random container ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for ContainerId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for ContainerId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Container({})", self.0)
    }
}

/// Strongly-typed match identifier.
///
/// Matches represent active game sessions within a container.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MatchId(pub Uuid);

impl MatchId {
    /// Create a new random match ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for MatchId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for MatchId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Match({})", self.0)
    }
}

/// Strongly-typed tenant identifier.
///
/// Tenants represent isolated customer environments in multi-tenant deployments.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TenantId(pub Uuid);

impl TenantId {
    /// Create a new random tenant ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for TenantId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for TenantId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Tenant({})", self.0)
    }
}

impl FromStr for TenantId {
    type Err = uuid::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Self(Uuid::parse_str(s)?))
    }
}

/// Strongly-typed user identifier.
///
/// Users are authenticated principals within a tenant.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct UserId(pub Uuid);

impl UserId {
    /// Create a new random user ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for UserId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for UserId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "User({})", self.0)
    }
}

impl FromStr for UserId {
    type Err = uuid::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Self(Uuid::parse_str(s)?))
    }
}

/// Component type identifier for ECS serialization.
///
/// Each component type has a unique ID for serialization purposes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ComponentTypeId(pub u64);

impl fmt::Display for ComponentTypeId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "ComponentType({})", self.0)
    }
}

/// WebSocket connection identifier.
///
/// Uniquely identifies a client WebSocket connection.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ConnectionId(pub Uuid);

impl ConnectionId {
    /// Create a new random connection ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for ConnectionId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for ConnectionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Connection({})", self.0)
    }
}

/// Strongly-typed resource identifier.
///
/// Resources are uploaded assets like WASM modules, game assets, and configurations.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ResourceId(pub Uuid);

impl ResourceId {
    /// Create a new random resource ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for ResourceId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for ResourceId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Resource({})", self.0)
    }
}

impl FromStr for ResourceId {
    type Err = uuid::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Self(Uuid::parse_str(s)?))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn entity_id_display() {
        let id = EntityId(42);
        assert_eq!(format!("{id}"), "Entity(42)");
    }

    #[test]
    fn container_id_unique() {
        let id1 = ContainerId::new();
        let id2 = ContainerId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn ids_serialize_roundtrip() {
        let entity_id = EntityId(123);
        let json = serde_json::to_string(&entity_id).expect("serialize");
        let parsed: EntityId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(entity_id, parsed);
    }

    #[test]
    fn resource_id_unique() {
        let id1 = ResourceId::new();
        let id2 = ResourceId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn resource_id_display() {
        let id = ResourceId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Resource("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn resource_id_from_str() {
        let id = ResourceId::new();
        let s = id.0.to_string();
        let parsed: ResourceId = s.parse().expect("parse");
        assert_eq!(id, parsed);
    }
}
