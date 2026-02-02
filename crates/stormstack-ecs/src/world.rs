//! ECS World implementation using legion.
//!
//! Provides a wrapper around legion's World that tracks entity IDs
//! and supports snapshot/delta generation for streaming.

use legion::storage::IntoComponentSource;
use legion::{Entity, EntityStore, Resources, Schedule, World};
use parking_lot::RwLock;
use serde::{de::DeserializeOwned, Serialize};
use std::any::TypeId;
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use stormstack_core::{
    ComponentTypeId, EntityId, EntitySnapshot, Result, StormError, WorldDelta, WorldSnapshot,
};
use tracing::{debug, trace, warn};

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

/// Tracks changes for delta generation.
#[derive(Debug, Default)]
struct ChangeTracker {
    spawned_at_tick: HashMap<u64, Vec<EntityId>>,
    despawned_at_tick: HashMap<u64, Vec<EntityId>>,
    updated_at_tick: HashMap<u64, Vec<(EntityId, ComponentTypeId)>>,
}

impl ChangeTracker {
    fn new() -> Self {
        Self::default()
    }

    fn record_spawn(&mut self, tick: u64, entity: EntityId) {
        self.spawned_at_tick.entry(tick).or_default().push(entity);
    }

    fn record_despawn(&mut self, tick: u64, entity: EntityId) {
        self.despawned_at_tick.entry(tick).or_default().push(entity);
    }

    fn record_update(&mut self, tick: u64, entity: EntityId, component_type: ComponentTypeId) {
        self.updated_at_tick
            .entry(tick)
            .or_default()
            .push((entity, component_type));
    }

    fn get_changes_since(
        &self,
        from_tick: u64,
    ) -> (
        Vec<EntityId>,
        Vec<EntityId>,
        Vec<(EntityId, ComponentTypeId)>,
    ) {
        let mut spawned = Vec::new();
        let mut despawned = Vec::new();
        let mut updated = Vec::new();

        for (tick, entities) in &self.spawned_at_tick {
            if *tick >= from_tick {
                spawned.extend(entities.iter().copied());
            }
        }

        for (tick, entities) in &self.despawned_at_tick {
            if *tick >= from_tick {
                despawned.extend(entities.iter().copied());
            }
        }

        for (tick, updates) in &self.updated_at_tick {
            if *tick >= from_tick {
                updated.extend(updates.iter().copied());
            }
        }

        (spawned, despawned, updated)
    }

    fn cleanup_before(&mut self, tick: u64) {
        self.spawned_at_tick.retain(|t, _| *t >= tick);
        self.despawned_at_tick.retain(|t, _| *t >= tick);
        self.updated_at_tick.retain(|t, _| *t >= tick);
    }
}

/// Component type registry for serialization.
#[derive(Default)]
struct TypeRegistry {
    type_to_id: HashMap<TypeId, ComponentTypeId>,
    next_id: u64,
}

impl TypeRegistry {
    fn new() -> Self {
        Self {
            type_to_id: HashMap::new(),
            next_id: 1,
        }
    }

    fn register<C: 'static>(&mut self) -> ComponentTypeId {
        let type_id = TypeId::of::<C>();
        if let Some(&id) = self.type_to_id.get(&type_id) {
            return id;
        }

        let id = ComponentTypeId(self.next_id);
        self.next_id += 1;
        self.type_to_id.insert(type_id, id);
        id
    }

    fn get<C: 'static>(&self) -> Option<ComponentTypeId> {
        self.type_to_id.get(&TypeId::of::<C>()).copied()
    }
}

/// Marker component for empty entities.
#[derive(Clone, Copy, Debug, Default)]
pub struct Marker;

/// StormStack ECS World implementation using legion.
///
/// Wraps legion's World with:
/// - Stable entity IDs (our EntityId vs legion's Entity)
/// - Change tracking for delta generation
/// - Component type registration for serialization
pub struct StormWorld {
    world: World,
    resources: Resources,
    schedule: Option<Schedule>,
    current_tick: u64,
    delta_time: f64,
    id_to_entity: HashMap<EntityId, Entity>,
    entity_to_id: HashMap<Entity, EntityId>,
    next_entity_id: u64,
    changes: ChangeTracker,
    type_registry: TypeRegistry,
}

impl StormWorld {
    /// Create a new empty world.
    #[must_use]
    pub fn new() -> Self {
        debug!("Creating new StormWorld");
        Self {
            world: World::default(),
            resources: Resources::default(),
            schedule: None,
            current_tick: 0,
            delta_time: 0.0,
            id_to_entity: HashMap::new(),
            entity_to_id: HashMap::new(),
            next_entity_id: 1,
            changes: ChangeTracker::new(),
            type_registry: TypeRegistry::new(),
        }
    }

    /// Set the system schedule to run on each tick.
    pub fn set_schedule(&mut self, schedule: Schedule) {
        self.schedule = Some(schedule);
    }

    /// Insert a resource into the world.
    pub fn insert_resource<R: 'static + Send + Sync>(&mut self, resource: R) {
        self.resources.insert(resource);
    }

    /// Register a component type for serialization.
    pub fn register_component<C: legion::storage::Component + Serialize + DeserializeOwned + 'static>(
        &mut self,
    ) -> ComponentTypeId {
        self.type_registry.register::<C>()
    }

    /// Get the component type ID for a registered type.
    pub fn component_type_id<C: 'static>(&self) -> Option<ComponentTypeId> {
        self.type_registry.get::<C>()
    }

    /// Spawn an entity with components.
    ///
    /// Components should be provided as a tuple.
    pub fn spawn_with<T>(&mut self, components: T) -> EntityId
    where
        Option<T>: IntoComponentSource,
    {
        let entity = self.world.push(components);
        self.register_entity(entity)
    }

    /// Register a legion entity and return our entity ID.
    fn register_entity(&mut self, entity: Entity) -> EntityId {
        let id = EntityId(self.next_entity_id);
        self.next_entity_id += 1;

        self.id_to_entity.insert(id, entity);
        self.entity_to_id.insert(entity, id);
        self.changes.record_spawn(self.current_tick, id);

        trace!("Spawned entity {:?} (legion: {:?})", id, entity);
        id
    }

    /// Get a component from an entity.
    pub fn get_component<C: legion::storage::Component + Clone>(&self, entity: EntityId) -> Option<C> {
        let legion_entity = self.id_to_entity.get(&entity)?;
        let entry = self.world.entry_ref(*legion_entity).ok()?;
        entry.into_component::<C>().ok().cloned()
    }

    /// Add a component to an existing entity.
    pub fn add_component<C: legion::storage::Component>(
        &mut self,
        entity: EntityId,
        component: C,
    ) -> Result<()> {
        let legion_entity = *self
            .id_to_entity
            .get(&entity)
            .ok_or_else(|| StormError::EntityNotFound(entity))?;

        match self.world.entry(legion_entity) {
            Some(mut entry) => {
                entry.add_component(component);
                if let Some(type_id) = self.type_registry.get::<C>() {
                    self.changes
                        .record_update(self.current_tick, entity, type_id);
                }
                Ok(())
            }
            None => Err(StormError::EntityNotFound(entity)),
        }
    }

    /// Remove a component from an entity.
    pub fn remove_component<C: legion::storage::Component>(
        &mut self,
        entity: EntityId,
    ) -> Result<()> {
        let legion_entity = *self
            .id_to_entity
            .get(&entity)
            .ok_or_else(|| StormError::EntityNotFound(entity))?;

        match self.world.entry(legion_entity) {
            Some(mut entry) => {
                entry.remove_component::<C>();
                Ok(())
            }
            None => Err(StormError::EntityNotFound(entity)),
        }
    }

    /// Get the current delta time.
    pub fn delta_time(&self) -> f64 {
        self.delta_time
    }

    /// Get all entity IDs.
    pub fn entities(&self) -> impl Iterator<Item = EntityId> + '_ {
        self.id_to_entity.keys().copied()
    }

    /// Get the entity count.
    pub fn entity_count(&self) -> usize {
        self.id_to_entity.len()
    }

    /// Clean up change history before a given tick.
    pub fn cleanup_history(&mut self, before_tick: u64) {
        self.changes.cleanup_before(before_tick);
    }

    /// Get access to the underlying legion world for queries.
    pub fn legion_world(&self) -> &World {
        &self.world
    }

    /// Get mutable access to the underlying legion world.
    pub fn legion_world_mut(&mut self) -> &mut World {
        &mut self.world
    }
}

impl Default for StormWorld {
    fn default() -> Self {
        Self::new()
    }
}

impl EcsWorld for StormWorld {
    fn spawn(&mut self) -> EntityId {
        self.spawn_with((Marker,))
    }

    fn despawn(&mut self, entity: EntityId) -> Result<()> {
        let legion_entity = self
            .id_to_entity
            .remove(&entity)
            .ok_or_else(|| StormError::EntityNotFound(entity))?;

        self.entity_to_id.remove(&legion_entity);

        if self.world.remove(legion_entity) {
            self.changes.record_despawn(self.current_tick, entity);
            trace!("Despawned entity {:?}", entity);
            Ok(())
        } else {
            warn!("Failed to remove legion entity {:?}", legion_entity);
            Err(StormError::EntityNotFound(entity))
        }
    }

    fn exists(&self, entity: EntityId) -> bool {
        self.id_to_entity.contains_key(&entity)
    }

    fn tick(&self) -> u64 {
        self.current_tick
    }

    fn advance(&mut self, delta_time: f64) -> Result<()> {
        self.delta_time = delta_time;
        self.current_tick += 1;

        trace!(
            "Advancing world to tick {} (dt: {})",
            self.current_tick,
            delta_time
        );

        if let Some(ref mut schedule) = self.schedule {
            schedule.execute(&mut self.world, &mut self.resources);
        }

        Ok(())
    }

    fn snapshot(&self) -> Result<WorldSnapshot> {
        let mut snapshot = WorldSnapshot::new(self.current_tick);

        for &entity_id in self.id_to_entity.keys() {
            snapshot.entities.push(EntitySnapshot::new(entity_id));
        }

        debug!(
            "Generated snapshot at tick {} with {} entities",
            self.current_tick,
            snapshot.entity_count()
        );
        Ok(snapshot)
    }

    fn delta_since(&self, from_tick: u64) -> Result<WorldDelta> {
        let mut delta = WorldDelta::new(from_tick, self.current_tick);

        let (spawned, despawned, _updated) = self.changes.get_changes_since(from_tick);

        let despawned_set: HashSet<_> = despawned.iter().copied().collect();

        for entity_id in spawned {
            if !despawned_set.contains(&entity_id) && self.exists(entity_id) {
                delta.spawned.push(EntitySnapshot::new(entity_id));
            }
        }

        for entity_id in despawned {
            delta.despawned.push(entity_id);
        }

        debug!(
            "Generated delta from tick {} to {} with {} changes",
            from_tick,
            self.current_tick,
            delta.change_count()
        );
        Ok(delta)
    }
}

impl stormstack_core::CommandWorld for StormWorld {
    fn spawn_entity(&mut self) -> EntityId {
        self.spawn_with((Marker,))
    }

    fn despawn_entity(&mut self, entity: EntityId) -> Result<()> {
        EcsWorld::despawn(self, entity)
    }

    fn entity_exists(&self, entity: EntityId) -> bool {
        self.exists(entity)
    }

    fn current_tick(&self) -> u64 {
        self.current_tick
    }
}

unsafe impl Send for StormWorld {}
unsafe impl Sync for StormWorld {}

/// Thread-safe wrapper for shared world access.
pub type SharedWorld = Arc<RwLock<StormWorld>>;

/// Create a new shared world.
#[must_use]
pub fn shared_world() -> SharedWorld {
    Arc::new(RwLock::new(StormWorld::new()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::{Deserialize, Serialize};

    #[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
    struct Position {
        x: f32,
        y: f32,
    }

    #[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
    struct Velocity {
        dx: f32,
        dy: f32,
    }

    #[test]
    fn spawn_increments_id() {
        let mut world = StormWorld::new();
        let e1 = world.spawn();
        let e2 = world.spawn();
        assert_eq!(e1, EntityId(1));
        assert_eq!(e2, EntityId(2));
    }

    #[test]
    fn spawn_with_components() {
        let mut world = StormWorld::new();
        let entity = world.spawn_with((
            Position { x: 1.0, y: 2.0 },
            Velocity { dx: 0.5, dy: -0.5 },
        ));

        assert!(world.exists(entity));
        assert_eq!(world.entity_count(), 1);

        let pos = world.get_component::<Position>(entity).expect("position");
        assert_eq!(pos.x, 1.0);
        assert_eq!(pos.y, 2.0);

        let vel = world.get_component::<Velocity>(entity).expect("velocity");
        assert_eq!(vel.dx, 0.5);
        assert_eq!(vel.dy, -0.5);
    }

    #[test]
    fn despawn_removes_entity() {
        let mut world = StormWorld::new();
        let entity = world.spawn_with((Position { x: 0.0, y: 0.0 },));

        assert!(world.exists(entity));
        world.despawn(entity).expect("despawn");
        assert!(!world.exists(entity));
        assert_eq!(world.entity_count(), 0);
    }

    #[test]
    fn despawn_nonexistent_fails() {
        let mut world = StormWorld::new();
        let result = world.despawn(EntityId(999));
        assert!(matches!(result, Err(StormError::EntityNotFound(_))));
    }

    #[test]
    fn advance_increments_tick() {
        let mut world = StormWorld::new();
        assert_eq!(world.tick(), 0);
        world.advance(0.016).expect("advance");
        assert_eq!(world.tick(), 1);
        assert_eq!(world.delta_time(), 0.016);
    }

    #[test]
    fn add_component_to_entity() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        world
            .add_component(entity, Position { x: 10.0, y: 20.0 })
            .expect("add");

        let pos = world.get_component::<Position>(entity).expect("get position");
        assert_eq!(pos.x, 10.0);
        assert_eq!(pos.y, 20.0);
    }

    #[test]
    fn remove_component_from_entity() {
        let mut world = StormWorld::new();
        let entity = world.spawn_with((Position { x: 5.0, y: 5.0 },));

        world.remove_component::<Position>(entity).expect("remove");
        assert!(world.get_component::<Position>(entity).is_none());
    }

    #[test]
    fn snapshot_includes_entities() {
        let mut world = StormWorld::new();
        world.spawn_with((Position { x: 1.0, y: 2.0 },));
        world.spawn_with((Position { x: 3.0, y: 4.0 },));
        world.spawn_with((Velocity { dx: 0.0, dy: 0.0 },));

        let snapshot = world.snapshot().expect("snapshot");
        assert_eq!(snapshot.entity_count(), 3);
        assert_eq!(snapshot.tick, 0);
    }

    #[test]
    fn delta_tracks_spawns() {
        let mut world = StormWorld::new();

        world.advance(0.016).expect("advance");
        let e1 = world.spawn();
        let e2 = world.spawn();
        world.advance(0.016).expect("advance");

        let delta = world.delta_since(0).expect("delta");
        assert_eq!(delta.from_tick, 0);
        assert_eq!(delta.to_tick, 2);
        assert_eq!(delta.spawned.len(), 2);
        assert!(delta.spawned.iter().any(|s| s.id == e1));
        assert!(delta.spawned.iter().any(|s| s.id == e2));
    }

    #[test]
    fn delta_tracks_despawns() {
        let mut world = StormWorld::new();
        let entity = world.spawn();
        world.advance(0.016).expect("advance");

        world.despawn(entity).expect("despawn");
        world.advance(0.016).expect("advance");

        let delta = world.delta_since(1).expect("delta");
        assert_eq!(delta.despawned.len(), 1);
        assert!(delta.despawned.contains(&entity));
    }

    #[test]
    fn shared_world_works() {
        let world = shared_world();

        {
            let mut w = world.write();
            w.spawn_with((Position { x: 1.0, y: 1.0 },));
        }

        {
            let w = world.read();
            assert_eq!(w.entity_count(), 1);
        }
    }

    #[test]
    fn register_component_type() {
        let mut world = StormWorld::new();
        let type_id = world.register_component::<Position>();

        assert!(type_id.0 > 0);
        assert_eq!(world.component_type_id::<Position>(), Some(type_id));
    }

    #[test]
    fn component_type_ids_are_unique() {
        let mut world = StormWorld::new();
        let pos_id = world.register_component::<Position>();
        let vel_id = world.register_component::<Velocity>();

        assert_ne!(pos_id, vel_id);
    }

    #[test]
    fn entities_iterator() {
        let mut world = StormWorld::new();
        let e1 = world.spawn();
        let e2 = world.spawn();
        let e3 = world.spawn();

        let entities: Vec<_> = world.entities().collect();
        assert_eq!(entities.len(), 3);
        assert!(entities.contains(&e1));
        assert!(entities.contains(&e2));
        assert!(entities.contains(&e3));
    }

    #[test]
    fn cleanup_history() {
        let mut world = StormWorld::new();

        for _ in 0..10 {
            world.spawn();
            world.advance(0.016).expect("advance");
        }

        world.cleanup_history(5);

        let delta = world.delta_since(0).expect("delta");
        assert!(delta.spawned.len() <= 5);
    }

    // =========================================================================
    // CommandWorld integration tests
    // =========================================================================

    use stormstack_core::{
        Command, CommandContext, CommandQueue, CommandWorld, DespawnEntityCommand, MatchId,
        SpawnEntityCommand, UserId,
    };

    #[test]
    fn command_world_spawn_entity() {
        let mut world = StormWorld::new();
        let entity = CommandWorld::spawn_entity(&mut world);
        assert!(CommandWorld::entity_exists(&world, entity));
        assert_eq!(world.entity_count(), 1);
    }

    #[test]
    fn command_world_despawn_entity() {
        let mut world = StormWorld::new();
        let entity = CommandWorld::spawn_entity(&mut world);
        assert!(CommandWorld::entity_exists(&world, entity));

        CommandWorld::despawn_entity(&mut world, entity).expect("despawn");
        assert!(!CommandWorld::entity_exists(&world, entity));
        assert_eq!(world.entity_count(), 0);
    }

    #[test]
    fn command_world_current_tick() {
        let mut world = StormWorld::new();
        assert_eq!(CommandWorld::current_tick(&world), 0);

        world.advance(0.016).expect("advance");
        assert_eq!(CommandWorld::current_tick(&world), 1);
    }

    #[test]
    fn spawn_command_with_storm_world() {
        let cmd = SpawnEntityCommand::new();
        let mut world = StormWorld::new();
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let entity_id = EntityId(
            result.data.as_ref().unwrap()["entity_id"]
                .as_u64()
                .unwrap(),
        );
        assert!(CommandWorld::entity_exists(&world, entity_id));
    }

    #[test]
    fn despawn_command_with_storm_world() {
        let mut world = StormWorld::new();
        let entity = CommandWorld::spawn_entity(&mut world);

        let cmd = DespawnEntityCommand::new(entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());
        assert!(!CommandWorld::entity_exists(&world, entity));
    }

    #[test]
    fn command_queue_with_storm_world() {
        let mut queue = CommandQueue::new();
        let user_id = UserId::new();

        // Queue spawn commands
        queue.push(Box::new(SpawnEntityCommand::new()), user_id);
        queue.push(Box::new(SpawnEntityCommand::new()), user_id);
        queue.push(Box::new(SpawnEntityCommand::new()), user_id);

        let mut world = StormWorld::new();
        let match_id = MatchId::new();

        let results = queue.execute_all(&mut world, match_id);

        assert_eq!(results.len(), 3);
        assert!(results.iter().all(|r| r.is_success()));
        assert_eq!(world.entity_count(), 3);

        // Now despawn all entities
        let entities: Vec<_> = world.entities().collect();
        for entity in entities {
            queue.push(Box::new(DespawnEntityCommand::new(entity)), user_id);
        }

        let results = queue.execute_all(&mut world, match_id);
        assert_eq!(results.len(), 3);
        assert!(results.iter().all(|r| r.is_success()));
        assert_eq!(world.entity_count(), 0);
    }

    #[test]
    fn command_tracks_changes() {
        let mut world = StormWorld::new();
        let match_id = MatchId::new();
        let user_id = UserId::new();

        // Advance to tick 1
        world.advance(0.016).expect("advance");

        // Spawn via command
        let cmd = SpawnEntityCommand::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 1);
        let result = cmd.execute(&mut ctx).expect("execute");

        let entity_id = EntityId(
            result.data.as_ref().unwrap()["entity_id"]
                .as_u64()
                .unwrap(),
        );

        // Check that changes are tracked
        let delta = world.delta_since(0).expect("delta");
        assert!(delta.spawned.iter().any(|s| s.id == entity_id));
    }
}
