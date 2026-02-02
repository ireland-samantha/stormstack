//! ECS world snapshot types for serialization and streaming.
//!
//! Snapshots represent the state of the ECS world at a point in time.
//! Deltas represent changes between snapshots for efficient streaming.

use crate::id::{ComponentTypeId, EntityId};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Full world state snapshot for initial sync.
///
/// Sent to clients when they first subscribe to a match,
/// or periodically to resync state.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorldSnapshot {
    /// Current game tick number.
    pub tick: u64,

    /// Timestamp when snapshot was taken (Unix millis).
    pub timestamp: i64,

    /// All entities and their components.
    pub entities: Vec<EntitySnapshot>,
}

impl WorldSnapshot {
    /// Create a new empty snapshot.
    #[must_use]
    pub fn new(tick: u64) -> Self {
        Self {
            tick,
            timestamp: chrono::Utc::now().timestamp_millis(),
            entities: Vec::new(),
        }
    }

    /// Get the number of entities in this snapshot.
    #[must_use]
    pub fn entity_count(&self) -> usize {
        self.entities.len()
    }
}

/// Single entity snapshot with all its components.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EntitySnapshot {
    /// Entity identifier.
    pub id: EntityId,

    /// Map of component type to serialized component data.
    pub components: HashMap<ComponentTypeId, Vec<u8>>,
}

impl EntitySnapshot {
    /// Create a new entity snapshot.
    #[must_use]
    pub fn new(id: EntityId) -> Self {
        Self {
            id,
            components: HashMap::new(),
        }
    }

    /// Add a component to this snapshot.
    pub fn add_component(&mut self, type_id: ComponentTypeId, data: Vec<u8>) {
        self.components.insert(type_id, data);
    }

    /// Get the number of components in this snapshot.
    #[must_use]
    pub fn component_count(&self) -> usize {
        self.components.len()
    }
}

/// Delta update for incremental sync.
///
/// Contains only changes since the last snapshot/delta,
/// reducing bandwidth for real-time streaming.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorldDelta {
    /// Starting tick (exclusive).
    pub from_tick: u64,

    /// Ending tick (inclusive).
    pub to_tick: u64,

    /// Newly spawned entities with initial state.
    pub spawned: Vec<EntitySnapshot>,

    /// IDs of despawned entities.
    pub despawned: Vec<EntityId>,

    /// Component updates for existing entities.
    pub updated: Vec<ComponentUpdate>,
}

impl WorldDelta {
    /// Create a new empty delta.
    #[must_use]
    pub fn new(from_tick: u64, to_tick: u64) -> Self {
        Self {
            from_tick,
            to_tick,
            spawned: Vec::new(),
            despawned: Vec::new(),
            updated: Vec::new(),
        }
    }

    /// Check if this delta is empty (no changes).
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.spawned.is_empty() && self.despawned.is_empty() && self.updated.is_empty()
    }

    /// Get the total number of changes in this delta.
    #[must_use]
    pub fn change_count(&self) -> usize {
        self.spawned.len() + self.despawned.len() + self.updated.len()
    }
}

/// Single component update for an existing entity.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ComponentUpdate {
    /// Entity being updated.
    pub entity: EntityId,

    /// Type of component being updated.
    pub component_type: ComponentTypeId,

    /// New serialized component data.
    pub data: Vec<u8>,
}

impl ComponentUpdate {
    /// Create a new component update.
    #[must_use]
    pub fn new(entity: EntityId, component_type: ComponentTypeId, data: Vec<u8>) -> Self {
        Self {
            entity,
            component_type,
            data,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn world_snapshot_new() {
        let snapshot = WorldSnapshot::new(100);
        assert_eq!(snapshot.tick, 100);
        assert!(snapshot.entities.is_empty());
        assert!(snapshot.timestamp > 0);
    }

    #[test]
    fn entity_snapshot_add_component() {
        let mut entity = EntitySnapshot::new(EntityId(1));
        entity.add_component(ComponentTypeId(1), vec![1, 2, 3]);
        entity.add_component(ComponentTypeId(2), vec![4, 5, 6]);

        assert_eq!(entity.component_count(), 2);
        assert_eq!(
            entity.components.get(&ComponentTypeId(1)),
            Some(&vec![1, 2, 3])
        );
    }

    #[test]
    fn world_delta_empty() {
        let delta = WorldDelta::new(0, 1);
        assert!(delta.is_empty());
        assert_eq!(delta.change_count(), 0);
    }

    #[test]
    fn world_delta_with_changes() {
        let mut delta = WorldDelta::new(0, 5);
        delta.spawned.push(EntitySnapshot::new(EntityId(1)));
        delta.despawned.push(EntityId(2));
        delta.updated.push(ComponentUpdate::new(
            EntityId(3),
            ComponentTypeId(1),
            vec![1, 2, 3],
        ));

        assert!(!delta.is_empty());
        assert_eq!(delta.change_count(), 3);
    }

    #[test]
    fn snapshot_serialization_roundtrip() {
        let mut snapshot = WorldSnapshot::new(42);
        let mut entity = EntitySnapshot::new(EntityId(1));
        entity.add_component(ComponentTypeId(100), vec![1, 2, 3, 4]);
        snapshot.entities.push(entity);

        let json = serde_json::to_string(&snapshot).expect("serialize");
        let parsed: WorldSnapshot = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(parsed.tick, 42);
        assert_eq!(parsed.entities.len(), 1);
        assert_eq!(parsed.entities[0].id, EntityId(1));
    }
}
