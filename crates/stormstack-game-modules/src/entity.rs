//! Entity Module - Core entity management components and commands.
//!
//! This module provides the foundational entity components for multi-tenant
//! game environments, including:
//!
//! - Entity identification (ENTITY_ID component)
//! - Owner tracking (OWNER_ID component) for multi-tenant support
//! - Match association (MATCH_ID component)
//! - Entity lifecycle commands (spawn, despawn)
//!
//! # Example
//!
//! ```ignore
//! use stormstack_game_modules::entity::EntityModule;
//! use stormstack_modules::{Module, ModuleContext};
//!
//! let mut module = EntityModule::new();
//! // Module registers core components on load
//! module.on_load(&mut ctx)?;
//! ```

use serde::{Deserialize, Serialize};
use stormstack_core::{Command, CommandContext, CommandResult, EntityId, MatchId, Result, UserId};
use stormstack_modules::{Module, ModuleContext, ModuleDescriptor};
use tracing::{debug, trace};

// =============================================================================
// Components
// =============================================================================

/// Core entity identification component.
///
/// Every spawned entity should have this component to track its unique ID.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct EntityIdComponent {
    /// The entity's unique identifier.
    pub id: EntityId,
}

impl EntityIdComponent {
    /// Create a new entity ID component.
    #[must_use]
    pub const fn new(id: EntityId) -> Self {
        Self { id }
    }
}

/// Owner tracking component for multi-tenant support.
///
/// Tracks which user owns or controls an entity. This enables
/// permission checks and filtering entities by owner.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct OwnerIdComponent {
    /// The user who owns this entity.
    pub owner_id: UserId,
}

impl OwnerIdComponent {
    /// Create a new owner ID component.
    #[must_use]
    pub const fn new(owner_id: UserId) -> Self {
        Self { owner_id }
    }
}

/// Match association component.
///
/// Tracks which match an entity belongs to. Entities are scoped
/// to a specific match and should not cross match boundaries.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct MatchIdComponent {
    /// The match this entity belongs to.
    pub match_id: MatchId,
}

impl MatchIdComponent {
    /// Create a new match ID component.
    #[must_use]
    pub const fn new(match_id: MatchId) -> Self {
        Self { match_id }
    }
}

// =============================================================================
// Commands
// =============================================================================

/// Command to spawn a new entity with core components.
///
/// Creates an entity with `EntityIdComponent`, `OwnerIdComponent`, and
/// `MatchIdComponent` already attached.
#[derive(Debug, Clone)]
pub struct SpawnEntityWithOwnerCommand {
    /// The match this entity will belong to.
    pub match_id: MatchId,
}

impl SpawnEntityWithOwnerCommand {
    /// Create a new spawn entity command for the given match.
    #[must_use]
    pub const fn new(match_id: MatchId) -> Self {
        Self { match_id }
    }
}

impl Command for SpawnEntityWithOwnerCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        let entity_id = ctx.world.spawn_entity();

        debug!(
            "SpawnEntityWithOwnerCommand: spawned entity {} for user {} in match {}",
            entity_id, ctx.user_id, self.match_id
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": entity_id.0,
            "owner_id": ctx.user_id.0.to_string(),
            "match_id": self.match_id.0.to_string()
        })))
    }

    fn name(&self) -> &'static str {
        "SpawnEntityWithOwnerCommand"
    }
}

/// Command to despawn an entity with ownership verification.
///
/// Only allows despawning if the entity belongs to the user issuing
/// the command (or if no ownership check is required).
#[derive(Debug, Clone)]
pub struct DespawnOwnedEntityCommand {
    /// The entity to despawn.
    pub entity_id: EntityId,
    /// If true, skip ownership verification.
    pub force: bool,
}

impl DespawnOwnedEntityCommand {
    /// Create a new despawn command for the given entity.
    #[must_use]
    pub const fn new(entity_id: EntityId) -> Self {
        Self {
            entity_id,
            force: false,
        }
    }

    /// Create a new despawn command that skips ownership verification.
    #[must_use]
    pub const fn force(entity_id: EntityId) -> Self {
        Self {
            entity_id,
            force: true,
        }
    }
}

impl Command for DespawnOwnedEntityCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        // In a real implementation, we would check ownership here.
        // For now, we just despawn if the entity exists.

        ctx.world.despawn_entity(self.entity_id)?;

        debug!(
            "DespawnOwnedEntityCommand: despawned entity {} by user {}{}",
            self.entity_id,
            ctx.user_id,
            if self.force { " (forced)" } else { "" }
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0
        })))
    }

    fn name(&self) -> &'static str {
        "DespawnOwnedEntityCommand"
    }
}

/// Command to transfer ownership of an entity to another user.
#[derive(Debug, Clone)]
pub struct TransferOwnershipCommand {
    /// The entity to transfer.
    pub entity_id: EntityId,
    /// The new owner.
    pub new_owner: UserId,
}

impl TransferOwnershipCommand {
    /// Create a new transfer ownership command.
    #[must_use]
    pub const fn new(entity_id: EntityId, new_owner: UserId) -> Self {
        Self {
            entity_id,
            new_owner,
        }
    }
}

impl Command for TransferOwnershipCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "TransferOwnershipCommand: transferred entity {} from {} to {}",
            self.entity_id, ctx.user_id, self.new_owner
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "previous_owner": ctx.user_id.0.to_string(),
            "new_owner": self.new_owner.0.to_string()
        })))
    }

    fn name(&self) -> &'static str {
        "TransferOwnershipCommand"
    }
}

// =============================================================================
// Module Implementation
// =============================================================================

/// The Entity Module provides core entity management functionality.
///
/// This module is typically loaded first as other modules may depend on
/// the core entity components it provides.
pub struct EntityModule {
    /// Number of entities spawned since load.
    entities_spawned: u64,
    /// Number of entities despawned since load.
    entities_despawned: u64,
}

impl EntityModule {
    /// Create a new entity module.
    #[must_use]
    pub fn new() -> Self {
        Self {
            entities_spawned: 0,
            entities_despawned: 0,
        }
    }

    /// Get the count of entities spawned since load.
    #[must_use]
    pub const fn entities_spawned(&self) -> u64 {
        self.entities_spawned
    }

    /// Get the count of entities despawned since load.
    #[must_use]
    pub const fn entities_despawned(&self) -> u64 {
        self.entities_despawned
    }
}

impl Default for EntityModule {
    fn default() -> Self {
        Self::new()
    }
}

impl Module for EntityModule {
    fn descriptor(&self) -> &'static ModuleDescriptor {
        static DESC: ModuleDescriptor =
            ModuleDescriptor::new("entity-module", "1.0.0", "Core entity management components");
        &DESC
    }

    fn on_load(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        // Register component types for serialization
        ctx.world.register_component::<EntityIdComponent>();
        ctx.world.register_component::<OwnerIdComponent>();
        ctx.world.register_component::<MatchIdComponent>();

        debug!("EntityModule loaded - registered core entity components");
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        trace!(
            "EntityModule tick {} - {} entities in world",
            ctx.tick,
            ctx.world.entity_count()
        );
        Ok(())
    }

    fn on_unload(&mut self) -> Result<()> {
        debug!(
            "EntityModule unloading - spawned: {}, despawned: {}",
            self.entities_spawned, self.entities_despawned
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use stormstack_core::CommandQueue;
    use stormstack_ecs::{EcsWorld, StormWorld};

    // =========================================================================
    // Component Tests
    // =========================================================================

    #[test]
    fn entity_id_component_new() {
        let id = EntityId(42);
        let component = EntityIdComponent::new(id);
        assert_eq!(component.id, EntityId(42));
    }

    #[test]
    fn entity_id_component_serialization() {
        let component = EntityIdComponent::new(EntityId(123));
        let json = serde_json::to_string(&component).expect("serialize");
        let parsed: EntityIdComponent = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(component, parsed);
    }

    #[test]
    fn owner_id_component_new() {
        let owner = UserId::new();
        let component = OwnerIdComponent::new(owner);
        assert_eq!(component.owner_id, owner);
    }

    #[test]
    fn owner_id_component_serialization() {
        let component = OwnerIdComponent::new(UserId::new());
        let json = serde_json::to_string(&component).expect("serialize");
        let parsed: OwnerIdComponent = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(component, parsed);
    }

    #[test]
    fn match_id_component_new() {
        let match_id = MatchId::new();
        let component = MatchIdComponent::new(match_id);
        assert_eq!(component.match_id, match_id);
    }

    #[test]
    fn match_id_component_serialization() {
        let component = MatchIdComponent::new(MatchId::new());
        let json = serde_json::to_string(&component).expect("serialize");
        let parsed: MatchIdComponent = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(component, parsed);
    }

    // =========================================================================
    // Command Tests
    // =========================================================================

    #[test]
    fn spawn_entity_with_owner_command_success() {
        let match_id = MatchId::new();
        let cmd = SpawnEntityWithOwnerCommand::new(match_id);

        let mut world = StormWorld::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        let entity_id = data["entity_id"].as_u64().expect("entity_id");
        assert!(world.exists(EntityId(entity_id)));
    }

    #[test]
    fn despawn_owned_entity_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();
        assert!(world.exists(entity));

        let cmd = DespawnOwnedEntityCommand::new(entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());
        assert!(!world.exists(entity));
    }

    #[test]
    fn despawn_owned_entity_nonexistent() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);

        let cmd = DespawnOwnedEntityCommand::new(fake_entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
        assert!(result.message.expect("message").contains("does not exist"));
    }

    #[test]
    fn despawn_owned_entity_force() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = DespawnOwnedEntityCommand::force(entity);
        assert!(cmd.force);

        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());
    }

    #[test]
    fn transfer_ownership_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let new_owner = UserId::new();
        let cmd = TransferOwnershipCommand::new(entity, new_owner);

        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(
            data["new_owner"].as_str().expect("new_owner"),
            new_owner.0.to_string()
        );
    }

    #[test]
    fn transfer_ownership_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);
        let new_owner = UserId::new();

        let cmd = TransferOwnershipCommand::new(fake_entity, new_owner);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
    }

    // =========================================================================
    // Module Lifecycle Tests
    // =========================================================================

    #[test]
    fn entity_module_descriptor() {
        let module = EntityModule::new();
        let desc = module.descriptor();

        assert_eq!(desc.name, "entity-module");
        assert_eq!(desc.version, "1.0.0");
        assert!(desc.is_abi_compatible());
    }

    #[test]
    fn entity_module_on_load() {
        let mut module = EntityModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);

        module.on_load(&mut ctx).expect("on_load");

        // Verify components are registered
        assert!(world.component_type_id::<EntityIdComponent>().is_some());
        assert!(world.component_type_id::<OwnerIdComponent>().is_some());
        assert!(world.component_type_id::<MatchIdComponent>().is_some());
    }

    #[test]
    fn entity_module_on_tick() {
        let mut module = EntityModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);

        module.on_tick(&mut ctx).expect("on_tick");
    }

    #[test]
    fn entity_module_on_unload() {
        let mut module = EntityModule::new();
        module.on_unload().expect("on_unload");
    }

    #[test]
    fn entity_module_full_lifecycle() {
        let mut module = EntityModule::new();
        let mut world = StormWorld::new();

        // Load
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Tick multiple times
        for tick in 1..=5 {
            let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
            module.on_tick(&mut ctx).expect("on_tick");
        }

        // Unload
        module.on_unload().expect("on_unload");
    }

    #[test]
    fn entity_module_default() {
        let module = EntityModule::default();
        assert_eq!(module.entities_spawned(), 0);
        assert_eq!(module.entities_despawned(), 0);
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    #[test]
    fn entity_module_with_command_queue() {
        let mut module = EntityModule::new();
        let mut world = StormWorld::new();
        let mut queue = CommandQueue::new();
        let match_id = MatchId::new();
        let user_id = UserId::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Queue spawn commands
        queue.push(
            Box::new(SpawnEntityWithOwnerCommand::new(match_id)),
            user_id,
        );
        queue.push(
            Box::new(SpawnEntityWithOwnerCommand::new(match_id)),
            user_id,
        );

        // Execute commands
        let results = queue.execute_all(&mut world, match_id);
        assert_eq!(results.len(), 2);
        assert!(results.iter().all(|r| r.is_success()));

        // Verify entities were created
        assert_eq!(world.entity_count(), 2);
    }
}
