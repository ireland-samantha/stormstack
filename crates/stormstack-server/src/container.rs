//! Execution container management.
//!
//! Containers provide isolated execution environments for game matches.
//! Each container has its own ECS world and can run multiple matches.

use dashmap::DashMap;
use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::sync::Arc;
use std::time::Instant;
use stormstack_core::{ContainerId, MatchConfig, MatchId, Result, StormError, TenantId, UserId};
use stormstack_ecs::{shared_world, EcsWorld, SharedWorld};
use tracing::{debug, trace, warn};

/// Match state enumeration.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MatchState {
    /// Match is created but not yet started.
    Pending,
    /// Match is actively running.
    Active,
    /// Match has finished.
    Completed,
}

impl Default for MatchState {
    fn default() -> Self {
        Self::Pending
    }
}

/// Summary information about a match.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchSummary {
    /// Match ID.
    pub id: MatchId,
    /// Current state.
    pub state: MatchState,
    /// Number of players currently in the match.
    pub player_count: usize,
    /// Maximum players allowed.
    pub max_players: u32,
    /// Game mode.
    pub game_mode: String,
    /// Current tick.
    pub current_tick: u64,
}

/// Match state within a container.
#[derive(Debug)]
pub struct Match {
    /// Match identifier.
    pub id: MatchId,
    /// Match configuration.
    pub config: MatchConfig,
    /// Current tick for this match.
    pub current_tick: u64,
    /// Whether the match is running.
    pub running: bool,
    /// Match state.
    state: MatchState,
    /// Set of player user IDs.
    players: HashSet<UserId>,
    /// Creation timestamp.
    created_at: Instant,
}

impl Match {
    /// Create a new match with the given configuration.
    #[must_use]
    pub fn new(config: MatchConfig) -> Self {
        Self {
            id: MatchId::new(),
            config,
            current_tick: 0,
            running: false,
            state: MatchState::Pending,
            players: HashSet::new(),
            created_at: Instant::now(),
        }
    }

    /// Create a new match with a specific ID.
    #[must_use]
    pub fn with_id(id: MatchId, config: MatchConfig) -> Self {
        Self {
            id,
            config,
            current_tick: 0,
            running: false,
            state: MatchState::Pending,
            players: HashSet::new(),
            created_at: Instant::now(),
        }
    }

    /// Advance the match by one tick.
    pub fn tick(&mut self) {
        if self.running && self.state == MatchState::Active {
            self.current_tick += 1;
        }
    }

    /// Stop the match.
    pub fn stop(&mut self) {
        self.running = false;
    }

    /// Get the current match state.
    #[must_use]
    pub fn state(&self) -> MatchState {
        self.state
    }

    /// Get the set of players.
    #[must_use]
    pub fn players(&self) -> &HashSet<UserId> {
        &self.players
    }

    /// Get the number of players.
    #[must_use]
    pub fn player_count(&self) -> usize {
        self.players.len()
    }

    /// Get the creation instant.
    #[must_use]
    pub fn created_at(&self) -> Instant {
        self.created_at
    }

    /// Check if the match is full.
    #[must_use]
    pub fn is_full(&self) -> bool {
        self.players.len() >= self.config.max_players as usize
    }

    /// Transition to a new state.
    ///
    /// # Errors
    ///
    /// Returns an error if the state transition is invalid.
    pub fn transition_to(&mut self, new_state: MatchState) -> Result<()> {
        let valid = match (self.state, new_state) {
            (MatchState::Pending, MatchState::Active) => true,
            (MatchState::Active, MatchState::Completed) => true,
            (MatchState::Pending, MatchState::Completed) => true, // Cancel
            _ => false,
        };

        if valid {
            debug!(
                "Match {:?} transitioning from {:?} to {:?}",
                self.id, self.state, new_state
            );
            self.state = new_state;
            // Update running flag based on state
            self.running = new_state == MatchState::Active;
            Ok(())
        } else {
            Err(StormError::InvalidState(format!(
                "Cannot transition from {:?} to {:?}",
                self.state, new_state
            )))
        }
    }

    /// Add a player to the match.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is full or not in a joinable state.
    pub fn add_player(&mut self, user_id: UserId) -> Result<()> {
        if self.state == MatchState::Completed {
            return Err(StormError::InvalidState(
                "Cannot join a completed match".to_string(),
            ));
        }

        if self.is_full() {
            return Err(StormError::ResourceExhausted(format!(
                "Match {} is full (max {} players)",
                self.id, self.config.max_players
            )));
        }

        self.players.insert(user_id);
        trace!("Player {:?} joined match {:?}", user_id, self.id);
        Ok(())
    }

    /// Remove a player from the match.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is completed.
    pub fn remove_player(&mut self, user_id: UserId) -> Result<bool> {
        if self.state == MatchState::Completed {
            return Err(StormError::InvalidState(
                "Cannot leave a completed match".to_string(),
            ));
        }

        let removed = self.players.remove(&user_id);
        if removed {
            trace!("Player {:?} left match {:?}", user_id, self.id);
        }
        Ok(removed)
    }

    /// Create a summary of this match.
    #[must_use]
    pub fn summary(&self) -> MatchSummary {
        MatchSummary {
            id: self.id,
            state: self.state,
            player_count: self.players.len(),
            max_players: self.config.max_players,
            game_mode: self.config.game_mode.clone(),
            current_tick: self.current_tick,
        }
    }
}

/// Module info for tracking loaded modules.
#[derive(Debug, Clone)]
pub struct LoadedModule {
    /// Module name.
    pub name: String,
    /// Module version.
    pub version: String,
}

/// Execution container for isolated game instances.
///
/// Each container has:
/// - A unique identifier
/// - A tenant ID for multi-tenancy isolation
/// - Its own ECS world
/// - A collection of active matches
/// - A list of loaded modules
pub struct Container {
    /// Container identifier.
    id: ContainerId,
    /// Tenant that owns this container.
    tenant_id: TenantId,
    /// Shared ECS world for this container.
    world: SharedWorld,
    /// Active matches within this container.
    matches: DashMap<MatchId, Match>,
    /// Loaded modules.
    modules: RwLock<Vec<LoadedModule>>,
}

impl Container {
    /// Create a new container for the specified tenant.
    #[must_use]
    pub fn new(tenant_id: TenantId) -> Self {
        let id = ContainerId::new();
        debug!("Creating container {:?} for tenant {:?}", id, tenant_id);
        Self {
            id,
            tenant_id,
            world: shared_world(),
            matches: DashMap::new(),
            modules: RwLock::new(Vec::new()),
        }
    }

    /// Create a container with a specific ID.
    #[must_use]
    pub fn with_id(id: ContainerId, tenant_id: TenantId) -> Self {
        debug!("Creating container {:?} for tenant {:?}", id, tenant_id);
        Self {
            id,
            tenant_id,
            world: shared_world(),
            matches: DashMap::new(),
            modules: RwLock::new(Vec::new()),
        }
    }

    /// Get the container ID.
    #[must_use]
    pub fn id(&self) -> ContainerId {
        self.id
    }

    /// Get the tenant ID.
    #[must_use]
    pub fn tenant_id(&self) -> TenantId {
        self.tenant_id
    }

    /// Get the shared ECS world.
    #[must_use]
    pub fn world(&self) -> &SharedWorld {
        &self.world
    }

    /// Get the number of active matches.
    #[must_use]
    pub fn match_count(&self) -> usize {
        self.matches.len()
    }

    /// Create a new match within this container.
    ///
    /// # Errors
    ///
    /// Returns an error if match creation fails.
    pub fn create_match(&self, config: MatchConfig) -> Result<MatchId> {
        let game_match = Match::new(config);
        let match_id = game_match.id;
        self.matches.insert(match_id, game_match);
        debug!(
            "Created match {:?} in container {:?}",
            match_id, self.id
        );
        Ok(match_id)
    }

    /// Get a match by ID.
    #[must_use]
    pub fn get_match(&self, match_id: MatchId) -> Option<dashmap::mapref::one::Ref<'_, MatchId, Match>> {
        self.matches.get(&match_id)
    }

    /// Remove a match from this container.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found.
    pub fn remove_match(&self, match_id: MatchId) -> Result<Match> {
        self.matches
            .remove(&match_id)
            .map(|(_, m)| m)
            .ok_or_else(|| StormError::MatchNotFound(match_id))
    }

    /// Get all match IDs in this container.
    #[must_use]
    pub fn match_ids(&self) -> Vec<MatchId> {
        self.matches.iter().map(|entry| *entry.key()).collect()
    }

    /// Delete a match from this container.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found.
    pub fn delete_match(&self, match_id: MatchId) -> Result<()> {
        self.matches
            .remove(&match_id)
            .map(|_| {
                debug!("Deleted match {:?} from container {:?}", match_id, self.id);
            })
            .ok_or_else(|| StormError::MatchNotFound(match_id))
    }

    /// List all matches in this container as summaries.
    #[must_use]
    pub fn list_matches(&self) -> Vec<MatchSummary> {
        self.matches.iter().map(|r| r.value().summary()).collect()
    }

    /// Add a player to a match.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found, full, or not in a joinable state.
    pub fn join_match(&self, match_id: MatchId, user_id: UserId) -> Result<()> {
        let mut entry = self
            .matches
            .get_mut(&match_id)
            .ok_or(StormError::MatchNotFound(match_id))?;

        entry.add_player(user_id)
    }

    /// Remove a player from a match.
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found or in a completed state.
    pub fn leave_match(&self, match_id: MatchId, user_id: UserId) -> Result<()> {
        let mut entry = self
            .matches
            .get_mut(&match_id)
            .ok_or(StormError::MatchNotFound(match_id))?;

        entry.remove_player(user_id)?;
        Ok(())
    }

    /// Start a match (transition from Pending to Active).
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found or cannot be started.
    pub fn start_match(&self, match_id: MatchId) -> Result<()> {
        let mut entry = self
            .matches
            .get_mut(&match_id)
            .ok_or(StormError::MatchNotFound(match_id))?;

        entry.transition_to(MatchState::Active)
    }

    /// Complete a match (transition to Completed).
    ///
    /// # Errors
    ///
    /// Returns an error if the match is not found or cannot be completed.
    pub fn complete_match(&self, match_id: MatchId) -> Result<()> {
        let mut entry = self
            .matches
            .get_mut(&match_id)
            .ok_or(StormError::MatchNotFound(match_id))?;

        entry.transition_to(MatchState::Completed)
    }

    /// Get all active matches.
    #[must_use]
    pub fn active_matches(&self) -> Vec<MatchSummary> {
        self.matches
            .iter()
            .filter(|r| r.value().state() == MatchState::Active)
            .map(|r| r.value().summary())
            .collect()
    }

    /// Load a module into this container.
    pub fn load_module(&self, name: String, version: String) {
        let mut modules = self.modules.write();
        modules.push(LoadedModule { name: name.clone(), version: version.clone() });
        debug!(
            "Loaded module {} v{} into container {:?}",
            name, version, self.id
        );
    }

    /// Get loaded modules.
    #[must_use]
    pub fn loaded_modules(&self) -> Vec<LoadedModule> {
        self.modules.read().clone()
    }

    /// Check if a module is loaded.
    #[must_use]
    pub fn has_module(&self, name: &str) -> bool {
        self.modules.read().iter().any(|m| m.name == name)
    }

    /// Execute a tick for all matches and the ECS world.
    ///
    /// This advances the world simulation by one tick.
    ///
    /// # Arguments
    ///
    /// * `delta_time` - Time elapsed since the last tick in seconds.
    ///
    /// # Errors
    ///
    /// Returns an error if tick execution fails.
    pub fn tick(&self, delta_time: f64) -> Result<()> {
        trace!("Ticking container {:?} with dt={}", self.id, delta_time);

        // Advance the ECS world
        {
            let mut world = self.world.write();
            world.advance(delta_time)?;
        }

        // Tick all running matches
        for mut entry in self.matches.iter_mut() {
            entry.value_mut().tick();
        }

        Ok(())
    }

    /// Get the current tick of the ECS world.
    #[must_use]
    pub fn current_tick(&self) -> u64 {
        self.world.read().tick()
    }

    /// Get the entity count in this container's world.
    #[must_use]
    pub fn entity_count(&self) -> usize {
        self.world.read().entity_count()
    }
}

impl std::fmt::Debug for Container {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Container")
            .field("id", &self.id)
            .field("tenant_id", &self.tenant_id)
            .field("match_count", &self.matches.len())
            .field("module_count", &self.modules.read().len())
            .finish()
    }
}

/// Thread-safe container reference.
pub type SharedContainer = Arc<Container>;

/// Service for managing containers across the server.
///
/// Provides thread-safe operations for:
/// - Creating and deleting containers
/// - Listing containers by tenant
/// - Looking up containers by ID
/// - Ticking all containers
#[derive(Debug)]
pub struct ContainerService {
    /// All containers indexed by ID.
    containers: DashMap<ContainerId, SharedContainer>,
    /// Container IDs indexed by tenant.
    containers_by_tenant: DashMap<TenantId, HashSet<ContainerId>>,
}

impl ContainerService {
    /// Create a new container service.
    #[must_use]
    pub fn new() -> Self {
        debug!("Creating ContainerService");
        Self {
            containers: DashMap::new(),
            containers_by_tenant: DashMap::new(),
        }
    }

    /// Create a new container for a tenant.
    ///
    /// Returns the ID of the newly created container.
    pub fn create_container(&self, tenant_id: TenantId) -> ContainerId {
        let container = Arc::new(Container::new(tenant_id));
        let container_id = container.id();

        self.containers.insert(container_id, container);
        self.containers_by_tenant
            .entry(tenant_id)
            .or_default()
            .insert(container_id);

        debug!(
            "ContainerService: created container {:?} for tenant {:?}",
            container_id, tenant_id
        );
        container_id
    }

    /// Get a container by ID.
    ///
    /// Returns `None` if the container doesn't exist.
    #[must_use]
    pub fn get_container(&self, container_id: ContainerId) -> Option<SharedContainer> {
        self.containers.get(&container_id).map(|c| c.clone())
    }

    /// Get a container by ID, verifying tenant ownership.
    ///
    /// # Errors
    ///
    /// Returns `ContainerNotFound` if the container doesn't exist or
    /// belongs to a different tenant.
    pub fn get_container_for_tenant(
        &self,
        container_id: ContainerId,
        tenant_id: TenantId,
    ) -> Result<SharedContainer> {
        let container = self
            .containers
            .get(&container_id)
            .map(|c| c.clone())
            .ok_or_else(|| StormError::ContainerNotFound(container_id))?;

        if container.tenant_id() != tenant_id {
            warn!(
                "Tenant {:?} attempted to access container {:?} owned by {:?}",
                tenant_id,
                container_id,
                container.tenant_id()
            );
            return Err(StormError::ContainerNotFound(container_id));
        }

        Ok(container)
    }

    /// Delete a container.
    ///
    /// # Errors
    ///
    /// Returns `ContainerNotFound` if the container doesn't exist.
    pub fn delete_container(&self, container_id: ContainerId) -> Result<()> {
        let container = self
            .containers
            .remove(&container_id)
            .ok_or_else(|| StormError::ContainerNotFound(container_id))?;

        // Remove from tenant index
        let tenant_id = container.1.tenant_id();
        if let Some(mut tenant_containers) = self.containers_by_tenant.get_mut(&tenant_id) {
            tenant_containers.remove(&container_id);
        }

        debug!("ContainerService: deleted container {:?}", container_id);
        Ok(())
    }

    /// Delete a container, verifying tenant ownership.
    ///
    /// # Errors
    ///
    /// Returns `ContainerNotFound` if the container doesn't exist or
    /// belongs to a different tenant.
    pub fn delete_container_for_tenant(
        &self,
        container_id: ContainerId,
        tenant_id: TenantId,
    ) -> Result<()> {
        // First verify ownership
        let container = self
            .containers
            .get(&container_id)
            .ok_or_else(|| StormError::ContainerNotFound(container_id))?;

        if container.tenant_id() != tenant_id {
            warn!(
                "Tenant {:?} attempted to delete container {:?} owned by {:?}",
                tenant_id,
                container_id,
                container.tenant_id()
            );
            return Err(StormError::ContainerNotFound(container_id));
        }
        drop(container);

        // Now delete
        self.delete_container(container_id)
    }

    /// List all containers for a tenant.
    #[must_use]
    pub fn list_containers_for_tenant(&self, tenant_id: TenantId) -> Vec<SharedContainer> {
        let container_ids: Vec<ContainerId> = self
            .containers_by_tenant
            .get(&tenant_id)
            .map(|ids| ids.iter().copied().collect())
            .unwrap_or_default();

        container_ids
            .into_iter()
            .filter_map(|id| self.containers.get(&id).map(|c| c.clone()))
            .collect()
    }

    /// Get the total number of containers.
    #[must_use]
    pub fn container_count(&self) -> usize {
        self.containers.len()
    }

    /// Check if a container exists.
    #[must_use]
    pub fn has_container(&self, container_id: ContainerId) -> bool {
        self.containers.contains_key(&container_id)
    }

    /// Tick all containers.
    ///
    /// # Arguments
    ///
    /// * `delta_time` - Time elapsed since the last tick in seconds.
    ///
    /// # Errors
    ///
    /// Returns an error if any container fails to tick.
    pub fn tick_all(&self, delta_time: f64) -> Result<()> {
        for entry in self.containers.iter() {
            entry.value().tick(delta_time)?;
        }
        Ok(())
    }

    /// Tick a specific container.
    ///
    /// # Errors
    ///
    /// Returns `ContainerNotFound` if the container doesn't exist,
    /// or a tick error if execution fails.
    pub fn tick_container(&self, container_id: ContainerId, delta_time: f64) -> Result<()> {
        let container = self
            .containers
            .get(&container_id)
            .ok_or_else(|| StormError::ContainerNotFound(container_id))?;

        container.tick(delta_time)
    }
}

impl Default for ContainerService {
    fn default() -> Self {
        Self::new()
    }
}

/// Thread-safe shared container service.
pub type SharedContainerService = Arc<ContainerService>;

/// Create a new shared container service.
#[must_use]
pub fn shared_container_service() -> SharedContainerService {
    Arc::new(ContainerService::new())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_container_returns_id() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let container_id = service.create_container(tenant_id);

        // Verify the ID is valid (not checking specific value since it's random)
        assert!(service.has_container(container_id));
        assert_eq!(service.container_count(), 1);
    }

    #[test]
    fn get_container_after_create() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let container_id = service.create_container(tenant_id);
        let container = service.get_container(container_id);

        assert!(container.is_some());
        let container = container.unwrap();
        assert_eq!(container.id(), container_id);
        assert_eq!(container.tenant_id(), tenant_id);
    }

    #[test]
    fn delete_container() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let container_id = service.create_container(tenant_id);
        assert!(service.has_container(container_id));

        let result = service.delete_container(container_id);
        assert!(result.is_ok());
        assert!(!service.has_container(container_id));
        assert_eq!(service.container_count(), 0);
    }

    #[test]
    fn delete_nonexistent_container_fails() {
        let service = ContainerService::new();
        let container_id = ContainerId::new();

        let result = service.delete_container(container_id);
        assert!(matches!(result, Err(StormError::ContainerNotFound(_))));
    }

    #[test]
    fn list_containers_for_tenant() {
        let service = ContainerService::new();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create containers for tenant1
        let c1 = service.create_container(tenant1);
        let c2 = service.create_container(tenant1);

        // Create container for tenant2
        let c3 = service.create_container(tenant2);

        // List tenant1's containers
        let tenant1_containers = service.list_containers_for_tenant(tenant1);
        assert_eq!(tenant1_containers.len(), 2);

        let ids: Vec<ContainerId> = tenant1_containers.iter().map(|c| c.id()).collect();
        assert!(ids.contains(&c1));
        assert!(ids.contains(&c2));
        assert!(!ids.contains(&c3));

        // List tenant2's containers
        let tenant2_containers = service.list_containers_for_tenant(tenant2);
        assert_eq!(tenant2_containers.len(), 1);
        assert_eq!(tenant2_containers[0].id(), c3);
    }

    #[test]
    fn list_containers_for_tenant_empty() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let containers = service.list_containers_for_tenant(tenant_id);
        assert!(containers.is_empty());
    }

    #[test]
    fn tick_advances_world() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let container_id = service.create_container(tenant_id);
        let container = service.get_container(container_id).unwrap();

        // Initial tick should be 0
        assert_eq!(container.current_tick(), 0);

        // Tick the container
        service.tick_container(container_id, 0.016).expect("tick");

        // Tick should advance
        assert_eq!(container.current_tick(), 1);

        // Tick again
        service.tick_container(container_id, 0.016).expect("tick");
        assert_eq!(container.current_tick(), 2);
    }

    #[test]
    fn tick_all_containers() {
        let service = ContainerService::new();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        let c1_id = service.create_container(tenant1);
        let c2_id = service.create_container(tenant2);

        // Tick all
        service.tick_all(0.016).expect("tick all");

        // Both should have advanced
        let c1 = service.get_container(c1_id).unwrap();
        let c2 = service.get_container(c2_id).unwrap();

        assert_eq!(c1.current_tick(), 1);
        assert_eq!(c2.current_tick(), 1);
    }

    #[test]
    fn tenant_isolation_get() {
        let service = ContainerService::new();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create container for tenant1
        let container_id = service.create_container(tenant1);

        // tenant1 can access it
        let result = service.get_container_for_tenant(container_id, tenant1);
        assert!(result.is_ok());

        // tenant2 cannot access it (returns ContainerNotFound to avoid leaking info)
        let result = service.get_container_for_tenant(container_id, tenant2);
        assert!(matches!(result, Err(StormError::ContainerNotFound(_))));
    }

    #[test]
    fn tenant_isolation_delete() {
        let service = ContainerService::new();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create container for tenant1
        let container_id = service.create_container(tenant1);

        // tenant2 cannot delete it
        let result = service.delete_container_for_tenant(container_id, tenant2);
        assert!(matches!(result, Err(StormError::ContainerNotFound(_))));

        // Container should still exist
        assert!(service.has_container(container_id));

        // tenant1 can delete it
        let result = service.delete_container_for_tenant(container_id, tenant1);
        assert!(result.is_ok());
        assert!(!service.has_container(container_id));
    }

    #[test]
    fn tenant_isolation_list() {
        let service = ContainerService::new();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create containers for different tenants
        service.create_container(tenant1);
        service.create_container(tenant1);
        service.create_container(tenant2);

        // Each tenant only sees their own containers
        let t1_containers = service.list_containers_for_tenant(tenant1);
        let t2_containers = service.list_containers_for_tenant(tenant2);

        assert_eq!(t1_containers.len(), 2);
        assert_eq!(t2_containers.len(), 1);

        // Verify all t1 containers belong to t1
        for c in t1_containers {
            assert_eq!(c.tenant_id(), tenant1);
        }

        // Verify all t2 containers belong to t2
        for c in t2_containers {
            assert_eq!(c.tenant_id(), tenant2);
        }
    }

    #[test]
    fn container_create_match() {
        let container = Container::new(TenantId::new());

        let match_id = container.create_match(MatchConfig::default()).expect("create match");

        assert_eq!(container.match_count(), 1);
        assert!(container.get_match(match_id).is_some());
    }

    #[test]
    fn container_remove_match() {
        let container = Container::new(TenantId::new());

        let match_id = container.create_match(MatchConfig::default()).expect("create match");
        assert_eq!(container.match_count(), 1);

        let removed = container.remove_match(match_id).expect("remove match");
        assert_eq!(removed.id, match_id);
        assert_eq!(container.match_count(), 0);
    }

    #[test]
    fn container_remove_nonexistent_match_fails() {
        let container = Container::new(TenantId::new());
        let match_id = MatchId::new();

        let result = container.remove_match(match_id);
        assert!(matches!(result, Err(StormError::MatchNotFound(_))));
    }

    #[test]
    fn container_match_ids() {
        let container = Container::new(TenantId::new());

        let m1 = container.create_match(MatchConfig::default()).expect("create");
        let m2 = container.create_match(MatchConfig::default()).expect("create");

        let ids = container.match_ids();
        assert_eq!(ids.len(), 2);
        assert!(ids.contains(&m1));
        assert!(ids.contains(&m2));
    }

    #[test]
    fn container_tick_advances_matches() {
        let container = Container::new(TenantId::new());

        let match_id = container.create_match(MatchConfig::default()).expect("create");

        // Initial state
        {
            let m = container.get_match(match_id).unwrap();
            assert_eq!(m.current_tick, 0);
        }

        // Start the match so it can tick
        container.start_match(match_id).expect("start");

        // Tick
        container.tick(0.016).expect("tick");

        // Check match advanced
        {
            let m = container.get_match(match_id).unwrap();
            assert_eq!(m.current_tick, 1);
        }
    }

    #[test]
    fn container_load_module() {
        let container = Container::new(TenantId::new());

        container.load_module("physics".to_string(), "1.0.0".to_string());
        container.load_module("ai".to_string(), "2.1.0".to_string());

        assert!(container.has_module("physics"));
        assert!(container.has_module("ai"));
        assert!(!container.has_module("networking"));

        let modules = container.loaded_modules();
        assert_eq!(modules.len(), 2);
    }

    #[test]
    fn container_entity_count() {
        let container = Container::new(TenantId::new());

        assert_eq!(container.entity_count(), 0);

        // Spawn entities in the world
        {
            let mut world = container.world().write();
            world.spawn();
            world.spawn();
        }

        assert_eq!(container.entity_count(), 2);
    }

    #[test]
    fn shared_container_service_creation() {
        let service = shared_container_service();
        assert_eq!(service.container_count(), 0);
    }

    #[test]
    fn match_tick() {
        let mut game_match = Match::new(MatchConfig::default());

        assert_eq!(game_match.current_tick, 0);
        assert!(!game_match.running); // Match starts in Pending state, not running

        // Tick while pending - should not advance
        game_match.tick();
        assert_eq!(game_match.current_tick, 0);

        // Start the match
        game_match.transition_to(MatchState::Active).unwrap();
        assert!(game_match.running);

        game_match.tick();
        assert_eq!(game_match.current_tick, 1);

        game_match.tick();
        assert_eq!(game_match.current_tick, 2);
    }

    #[test]
    fn match_stop() {
        let mut game_match = Match::new(MatchConfig::default());
        game_match.transition_to(MatchState::Active).unwrap();

        game_match.tick();
        assert_eq!(game_match.current_tick, 1);

        game_match.stop();
        assert!(!game_match.running);

        // Stopped match should not advance
        game_match.tick();
        assert_eq!(game_match.current_tick, 1);
    }

    #[test]
    fn container_debug_format() {
        let container = Container::new(TenantId::new());
        let debug_str = format!("{:?}", container);

        assert!(debug_str.contains("Container"));
        assert!(debug_str.contains("id"));
        assert!(debug_str.contains("tenant_id"));
    }

    #[test]
    fn delete_removes_from_tenant_index() {
        let service = ContainerService::new();
        let tenant_id = TenantId::new();

        let container_id = service.create_container(tenant_id);
        assert_eq!(service.list_containers_for_tenant(tenant_id).len(), 1);

        service.delete_container(container_id).expect("delete");

        // Should no longer appear in tenant's list
        assert!(service.list_containers_for_tenant(tenant_id).is_empty());
    }

    #[test]
    fn tick_nonexistent_container_fails() {
        let service = ContainerService::new();
        let container_id = ContainerId::new();

        let result = service.tick_container(container_id, 0.016);
        assert!(matches!(result, Err(StormError::ContainerNotFound(_))));
    }

    #[test]
    fn container_with_id() {
        let id = ContainerId::new();
        let tenant_id = TenantId::new();

        let container = Container::with_id(id, tenant_id);

        assert_eq!(container.id(), id);
        assert_eq!(container.tenant_id(), tenant_id);
    }

    #[test]
    fn match_with_id() {
        let match_id = MatchId::new();
        let config = MatchConfig::default();

        let game_match = Match::with_id(match_id, config);

        assert_eq!(game_match.id, match_id);
        assert!(!game_match.running); // Match starts in Pending state
        assert_eq!(game_match.state(), MatchState::Pending);
    }

    // === New Match Service Tests ===

    #[test]
    fn get_match_returns_match() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig {
            max_players: 4,
            game_mode: "battle".to_string(),
            ..Default::default()
        };

        let match_id = container.create_match(config).unwrap();
        let match_ref = container.get_match(match_id);

        assert!(match_ref.is_some());
        let match_ref = match_ref.unwrap();
        assert_eq!(match_ref.id, match_id);
        assert_eq!(match_ref.config.max_players, 4);
        assert_eq!(match_ref.config.game_mode, "battle");
        assert_eq!(match_ref.state(), MatchState::Pending);
    }

    #[test]
    fn join_match_adds_player() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();
        let user_id = UserId::new();

        let result = container.join_match(match_id, user_id);

        assert!(result.is_ok());
        let match_ref = container.get_match(match_id).unwrap();
        assert_eq!(match_ref.player_count(), 1);
        assert!(match_ref.players().contains(&user_id));
    }

    #[test]
    fn leave_match_removes_player() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();
        let user_id = UserId::new();

        container.join_match(match_id, user_id).unwrap();
        let result = container.leave_match(match_id, user_id);

        assert!(result.is_ok());
        let match_ref = container.get_match(match_id).unwrap();
        assert_eq!(match_ref.player_count(), 0);
        assert!(!match_ref.players().contains(&user_id));
    }

    #[test]
    fn match_state_transitions() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();

        // Initial state is Pending
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.state(), MatchState::Pending);
        }

        // Transition to Active
        assert!(container.start_match(match_id).is_ok());
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.state(), MatchState::Active);
        }

        // Transition to Completed
        assert!(container.complete_match(match_id).is_ok());
        {
            let match_ref = container.get_match(match_id).unwrap();
            assert_eq!(match_ref.state(), MatchState::Completed);
        }
    }

    #[test]
    fn list_matches_returns_all() {
        let container = Container::new(TenantId::new());

        let config1 = MatchConfig {
            game_mode: "mode1".to_string(),
            ..Default::default()
        };
        let config2 = MatchConfig {
            game_mode: "mode2".to_string(),
            ..Default::default()
        };

        let id1 = container.create_match(config1).unwrap();
        let id2 = container.create_match(config2).unwrap();

        let summaries = container.list_matches();

        assert_eq!(summaries.len(), 2);

        let ids: Vec<MatchId> = summaries.iter().map(|s| s.id).collect();
        assert!(ids.contains(&id1));
        assert!(ids.contains(&id2));
    }

    #[test]
    fn delete_match_removes_it() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();

        assert_eq!(container.match_count(), 1);

        let result = container.delete_match(match_id);
        assert!(result.is_ok());
        assert_eq!(container.match_count(), 0);
        assert!(container.get_match(match_id).is_none());
    }

    #[test]
    fn join_full_match_fails() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig {
            max_players: 2,
            ..Default::default()
        };
        let match_id = container.create_match(config).unwrap();

        let user1 = UserId::new();
        let user2 = UserId::new();
        let user3 = UserId::new();

        container.join_match(match_id, user1).unwrap();
        container.join_match(match_id, user2).unwrap();

        let result = container.join_match(match_id, user3);
        assert!(result.is_err());
    }

    #[test]
    fn join_completed_match_fails() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();

        // Complete the match directly
        container.complete_match(match_id).unwrap();

        let user_id = UserId::new();
        let result = container.join_match(match_id, user_id);
        assert!(result.is_err());
    }

    #[test]
    fn leave_completed_match_fails() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();
        let user_id = UserId::new();

        container.join_match(match_id, user_id).unwrap();
        container.complete_match(match_id).unwrap();

        let result = container.leave_match(match_id, user_id);
        assert!(result.is_err());
    }

    #[test]
    fn active_matches_filters_correctly() {
        let container = Container::new(TenantId::new());

        let id1 = container.create_match(MatchConfig::default()).unwrap();
        let id2 = container.create_match(MatchConfig::default()).unwrap();
        let _id3 = container.create_match(MatchConfig::default()).unwrap();

        // Start only some matches
        container.start_match(id1).unwrap();
        container.start_match(id2).unwrap();
        // id3 remains Pending

        // Complete one
        container.complete_match(id1).unwrap();

        let active = container.active_matches();
        assert_eq!(active.len(), 1);
        assert_eq!(active[0].id, id2);
    }

    #[test]
    fn invalid_state_transition_fails() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig::default();
        let match_id = container.create_match(config).unwrap();

        // Start the match
        container.start_match(match_id).unwrap();

        // Try to start again (Active -> Active is invalid)
        let result = container.start_match(match_id);
        assert!(result.is_err());
    }

    #[test]
    fn match_summary_contains_correct_info() {
        let container = Container::new(TenantId::new());
        let config = MatchConfig {
            max_players: 8,
            game_mode: "deathmatch".to_string(),
            ..Default::default()
        };
        let match_id = container.create_match(config).unwrap();

        container.join_match(match_id, UserId::new()).unwrap();
        container.join_match(match_id, UserId::new()).unwrap();

        let match_ref = container.get_match(match_id).unwrap();
        let summary = match_ref.summary();

        assert_eq!(summary.id, match_id);
        assert_eq!(summary.state, MatchState::Pending);
        assert_eq!(summary.player_count, 2);
        assert_eq!(summary.max_players, 8);
        assert_eq!(summary.game_mode, "deathmatch");
    }

    #[test]
    fn match_tick_only_advances_when_active() {
        let container = Container::new(TenantId::new());
        let match_id = container.create_match(MatchConfig::default()).unwrap();

        // Tick while pending - should not advance
        container.tick(0.016).unwrap();
        {
            let m = container.get_match(match_id).unwrap();
            assert_eq!(m.current_tick, 0);
        }

        // Start the match
        container.start_match(match_id).unwrap();

        // Tick while active - should advance
        container.tick(0.016).unwrap();
        {
            let m = container.get_match(match_id).unwrap();
            assert_eq!(m.current_tick, 1);
        }
    }

    #[test]
    fn join_nonexistent_match_fails() {
        let container = Container::new(TenantId::new());
        let fake_id = MatchId::new();
        let user_id = UserId::new();

        let result = container.join_match(fake_id, user_id);
        assert!(result.is_err());
    }

    #[test]
    fn leave_nonexistent_match_fails() {
        let container = Container::new(TenantId::new());
        let fake_id = MatchId::new();
        let user_id = UserId::new();

        let result = container.leave_match(fake_id, user_id);
        assert!(result.is_err());
    }
}
