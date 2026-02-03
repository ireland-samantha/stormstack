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

/// Strongly-typed session identifier.
///
/// Sessions represent active player connections to matches.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct SessionId(pub Uuid);

impl SessionId {
    /// Create a new random session ID.
    #[must_use]
    pub fn new() -> Self {
        Self(Uuid::new_v4())
    }
}

impl Default for SessionId {
    fn default() -> Self {
        Self::new()
    }
}

impl fmt::Display for SessionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Session({})", self.0)
    }
}

impl FromStr for SessionId {
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

    #[test]
    fn session_id_unique() {
        let id1 = SessionId::new();
        let id2 = SessionId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn session_id_display() {
        let id = SessionId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Session("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn session_id_from_str() {
        let id = SessionId::new();
        let s = id.0.to_string();
        let parsed: SessionId = s.parse().expect("parse");
        assert_eq!(id, parsed);
    }

    #[test]
    fn session_id_serialize_roundtrip() {
        let id = SessionId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: SessionId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    // =========================================================================
    // Additional ID type tests - Alex
    // =========================================================================

    #[test]
    fn match_id_unique() {
        let id1 = MatchId::new();
        let id2 = MatchId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn match_id_display() {
        let id = MatchId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Match("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn match_id_default() {
        let id1 = MatchId::default();
        let id2 = MatchId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn match_id_serialize_roundtrip() {
        let id = MatchId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: MatchId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn container_id_display() {
        let id = ContainerId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Container("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn container_id_default() {
        let id1 = ContainerId::default();
        let id2 = ContainerId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn container_id_serialize_roundtrip() {
        let id = ContainerId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: ContainerId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn tenant_id_unique() {
        let id1 = TenantId::new();
        let id2 = TenantId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn tenant_id_display() {
        let id = TenantId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Tenant("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn tenant_id_from_str() {
        let id = TenantId::new();
        let s = id.0.to_string();
        let parsed: TenantId = s.parse().expect("parse");
        assert_eq!(id, parsed);
    }

    #[test]
    fn tenant_id_from_str_invalid() {
        let result: Result<TenantId, _> = "not-a-valid-uuid".parse();
        assert!(result.is_err());
    }

    #[test]
    fn tenant_id_serialize_roundtrip() {
        let id = TenantId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: TenantId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn user_id_unique() {
        let id1 = UserId::new();
        let id2 = UserId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn user_id_display() {
        let id = UserId::new();
        let display = format!("{id}");
        assert!(display.starts_with("User("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn user_id_default() {
        let id1 = UserId::default();
        let id2 = UserId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn user_id_from_str() {
        let id = UserId::new();
        let s = id.0.to_string();
        let parsed: UserId = s.parse().expect("parse");
        assert_eq!(id, parsed);
    }

    #[test]
    fn user_id_from_str_invalid() {
        let result: Result<UserId, _> = "invalid-uuid-string".parse();
        assert!(result.is_err());
    }

    #[test]
    fn user_id_serialize_roundtrip() {
        let id = UserId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: UserId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn connection_id_unique() {
        let id1 = ConnectionId::new();
        let id2 = ConnectionId::new();
        assert_ne!(id1, id2);
    }

    #[test]
    fn connection_id_display() {
        let id = ConnectionId::new();
        let display = format!("{id}");
        assert!(display.starts_with("Connection("));
        assert!(display.ends_with(")"));
    }

    #[test]
    fn connection_id_default() {
        let id1 = ConnectionId::default();
        let id2 = ConnectionId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn connection_id_serialize_roundtrip() {
        let id = ConnectionId::new();
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: ConnectionId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn component_type_id_display() {
        let id = ComponentTypeId(999);
        assert_eq!(format!("{id}"), "ComponentType(999)");
    }

    #[test]
    fn component_type_id_serialize_roundtrip() {
        let id = ComponentTypeId(12345);
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: ComponentTypeId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn entity_id_from_u64() {
        let id: EntityId = 42u64.into();
        assert_eq!(id.0, 42);
    }

    #[test]
    fn entity_id_serialize_roundtrip() {
        let id = EntityId(9999);
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: EntityId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn resource_id_default() {
        let id1 = ResourceId::default();
        let id2 = ResourceId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn resource_id_from_str_invalid() {
        let result: Result<ResourceId, _> = "not-a-uuid".parse();
        assert!(result.is_err());
    }

    #[test]
    fn session_id_default() {
        let id1 = SessionId::default();
        let id2 = SessionId::default();
        // Default should create new unique IDs
        assert_ne!(id1, id2);
    }

    #[test]
    fn session_id_from_str_invalid() {
        let result: Result<SessionId, _> = "bad-uuid".parse();
        assert!(result.is_err());
    }

    // =========================================================================
    // Peer Review Improvements - Dana
    // =========================================================================

    #[test]
    fn entity_id_boundary_zero() {
        let id = EntityId(0);
        assert_eq!(id.0, 0);
        assert_eq!(format!("{id}"), "Entity(0)");
        // Should serialize correctly
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: EntityId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn entity_id_boundary_max() {
        let id = EntityId(u64::MAX);
        assert_eq!(id.0, u64::MAX);
        assert_eq!(format!("{id}"), format!("Entity({})", u64::MAX));
        // Should serialize correctly
        let json = serde_json::to_string(&id).expect("serialize");
        let parsed: EntityId = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(id, parsed);
    }

    #[test]
    fn component_type_id_different_values_not_equal() {
        let id1 = ComponentTypeId(1);
        let id2 = ComponentTypeId(2);
        assert_ne!(id1, id2);
        // Same value should be equal
        let id3 = ComponentTypeId(1);
        assert_eq!(id1, id3);
    }

    #[test]
    fn id_types_hash_correctly() {
        use std::collections::HashMap;
        use std::hash::{Hash, Hasher};
        use std::collections::hash_map::DefaultHasher;

        fn hash_value<T: Hash>(val: &T) -> u64 {
            let mut hasher = DefaultHasher::new();
            val.hash(&mut hasher);
            hasher.finish()
        }

        // EntityId - equal values should have equal hashes
        let e1 = EntityId(42);
        let e2 = EntityId(42);
        assert_eq!(hash_value(&e1), hash_value(&e2));

        // ContainerId - can be used as HashMap key
        let mut container_map: HashMap<ContainerId, String> = HashMap::new();
        let cid = ContainerId::new();
        container_map.insert(cid, "test".to_string());
        assert_eq!(container_map.get(&cid), Some(&"test".to_string()));

        // MatchId - can be used as HashMap key
        let mut match_map: HashMap<MatchId, i32> = HashMap::new();
        let mid = MatchId::new();
        match_map.insert(mid, 100);
        assert_eq!(match_map.get(&mid), Some(&100));

        // ComponentTypeId - equal values should have equal hashes
        let ct1 = ComponentTypeId(999);
        let ct2 = ComponentTypeId(999);
        assert_eq!(hash_value(&ct1), hash_value(&ct2));
    }
}
