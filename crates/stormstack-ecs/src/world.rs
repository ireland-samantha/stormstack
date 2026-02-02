//! ECS World implementation.

use stormstack_core::{EntityId, Result, WorldDelta, WorldSnapshot};

/// ECS World trait for entity-component management.
///
/// This trait defines the interface for managing entities and components
/// in the game world. Implementations must be `Send + Sync` for use
/// across async boundaries.
pub trait EcsWorld: Send + Sync {
    /// Spawn a new entity and return its ID.
    fn spawn(&mut self) -> EntityId;

    /// Despawn an entity, removing all its components.
    fn despawn(&mut self, entity: EntityId) -> Result<()>;

    /// Check if an entity exists.
    fn exists(&self, entity: EntityId) -> bool;

    /// Get the current tick number.
    fn tick(&self) -> u64;

    /// Advance the world by one tick, running all systems.
    fn advance(&mut self, delta_time: f64) -> Result<()>;

    /// Generate a full snapshot of the world state.
    fn snapshot(&self) -> Result<WorldSnapshot>;

    /// Generate a delta since the given tick.
    fn delta_since(&self, from_tick: u64) -> Result<WorldDelta>;
}

/// StormStack ECS World implementation using legion.
pub struct StormWorld {
    // TODO: Add legion World
    current_tick: u64,
    next_entity_id: u64,
}

impl StormWorld {
    /// Create a new empty world.
    #[must_use]
    pub fn new() -> Self {
        Self {
            current_tick: 0,
            next_entity_id: 1,
        }
    }
}

impl Default for StormWorld {
    fn default() -> Self {
        Self::new()
    }
}

impl EcsWorld for StormWorld {
    fn spawn(&mut self) -> EntityId {
        let id = EntityId(self.next_entity_id);
        self.next_entity_id += 1;
        // TODO: Actually spawn in legion world
        id
    }

    fn despawn(&mut self, _entity: EntityId) -> Result<()> {
        // TODO: Implement
        Ok(())
    }

    fn exists(&self, _entity: EntityId) -> bool {
        // TODO: Implement
        false
    }

    fn tick(&self) -> u64 {
        self.current_tick
    }

    fn advance(&mut self, _delta_time: f64) -> Result<()> {
        self.current_tick += 1;
        // TODO: Run systems
        Ok(())
    }

    fn snapshot(&self) -> Result<WorldSnapshot> {
        Ok(WorldSnapshot::new(self.current_tick))
    }

    fn delta_since(&self, from_tick: u64) -> Result<WorldDelta> {
        Ok(WorldDelta::new(from_tick, self.current_tick))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn spawn_increments_id() {
        let mut world = StormWorld::new();
        let e1 = world.spawn();
        let e2 = world.spawn();
        assert_eq!(e1, EntityId(1));
        assert_eq!(e2, EntityId(2));
    }

    #[test]
    fn advance_increments_tick() {
        let mut world = StormWorld::new();
        assert_eq!(world.tick(), 0);
        world.advance(0.016).unwrap();
        assert_eq!(world.tick(), 1);
    }
}
