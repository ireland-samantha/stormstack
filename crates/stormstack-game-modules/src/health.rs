//! Health Module - Health, damage, and death systems.
//!
//! This module provides health management functionality:
//!
//! - Health component (current health value)
//! - MaxHealth component (maximum health cap)
//! - Dead marker component
//! - Health system that marks entities as Dead when health <= 0
//! - Commands for damage, healing, and direct health manipulation
//!
//! # Example
//!
//! ```ignore
//! use stormstack_game_modules::health::{HealthModule, Health, MaxHealth};
//!
//! let mut module = HealthModule::new();
//! module.on_load(&mut ctx)?;
//!
//! // Create an entity with health
//! world.spawn_with((
//!     Health::new(100),
//!     MaxHealth::new(100),
//! ));
//!
//! // On each tick, entities with health <= 0 get the Dead component
//! ```

use legion::IntoQuery;
use serde::{Deserialize, Serialize};
use stormstack_core::{Command, CommandContext, CommandResult, EntityId, Result};
use stormstack_ecs::StormWorld;
use stormstack_modules::{Module, ModuleContext, ModuleDescriptor};
use tracing::{debug, trace};

// =============================================================================
// Components
// =============================================================================

/// Current health value component.
///
/// Represents an entity's current hit points.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Health {
    /// Current health value.
    pub value: i32,
}

impl Health {
    /// Create a new health component with the given value.
    #[must_use]
    pub const fn new(value: i32) -> Self {
        Self { value }
    }

    /// Create a full health component (same as max).
    #[must_use]
    pub const fn full(max: i32) -> Self {
        Self::new(max)
    }

    /// Check if this health value represents death (health <= 0).
    #[must_use]
    pub const fn is_dead(&self) -> bool {
        self.value <= 0
    }

    /// Check if this health is at full capacity given a max.
    #[must_use]
    pub const fn is_full(&self, max: i32) -> bool {
        self.value >= max
    }

    /// Get the percentage of health remaining (0.0 to 1.0).
    #[must_use]
    pub fn percentage(&self, max: i32) -> f32 {
        if max <= 0 {
            return 0.0;
        }
        (self.value as f32 / max as f32).clamp(0.0, 1.0)
    }
}

impl Default for Health {
    fn default() -> Self {
        Self::new(100)
    }
}

/// Maximum health capacity component.
///
/// Caps the maximum value that Health can be healed to.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct MaxHealth {
    /// Maximum health value.
    pub value: i32,
}

impl MaxHealth {
    /// Create a new max health component.
    #[must_use]
    pub const fn new(value: i32) -> Self {
        Self { value }
    }
}

impl Default for MaxHealth {
    fn default() -> Self {
        Self::new(100)
    }
}

/// Marker component indicating an entity is dead.
///
/// Added automatically by the health system when health drops to 0 or below.
/// Can be used to filter dead entities in queries.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub struct Dead;

impl Dead {
    /// Create a new Dead marker.
    #[must_use]
    pub const fn new() -> Self {
        Self
    }
}

// =============================================================================
// Commands
// =============================================================================

/// Command to deal damage to an entity.
#[derive(Debug, Clone)]
pub struct DamageCommand {
    /// The entity to damage.
    pub entity_id: EntityId,
    /// Amount of damage to deal.
    pub amount: i32,
    /// Optional source of the damage (for tracking).
    pub source: Option<EntityId>,
}

impl DamageCommand {
    /// Create a new damage command.
    #[must_use]
    pub const fn new(entity_id: EntityId, amount: i32) -> Self {
        Self {
            entity_id,
            amount,
            source: None,
        }
    }

    /// Create a damage command with a source entity.
    #[must_use]
    pub const fn with_source(entity_id: EntityId, amount: i32, source: EntityId) -> Self {
        Self {
            entity_id,
            amount,
            source: Some(source),
        }
    }
}

impl Command for DamageCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "DamageCommand: dealt {} damage to entity {}{}",
            self.amount,
            self.entity_id,
            self.source
                .map(|s| format!(" from {s}"))
                .unwrap_or_default()
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "damage": self.amount,
            "source": self.source.map(|s| s.0)
        })))
    }

    fn name(&self) -> &'static str {
        "DamageCommand"
    }
}

/// Command to heal an entity.
#[derive(Debug, Clone)]
pub struct HealCommand {
    /// The entity to heal.
    pub entity_id: EntityId,
    /// Amount to heal.
    pub amount: i32,
    /// Optional source of healing (for tracking).
    pub source: Option<EntityId>,
}

impl HealCommand {
    /// Create a new heal command.
    #[must_use]
    pub const fn new(entity_id: EntityId, amount: i32) -> Self {
        Self {
            entity_id,
            amount,
            source: None,
        }
    }

    /// Create a heal command with a source entity.
    #[must_use]
    pub const fn with_source(entity_id: EntityId, amount: i32, source: EntityId) -> Self {
        Self {
            entity_id,
            amount,
            source: Some(source),
        }
    }
}

impl Command for HealCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "HealCommand: healed entity {} for {}{}",
            self.entity_id,
            self.amount,
            self.source
                .map(|s| format!(" from {s}"))
                .unwrap_or_default()
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "healing": self.amount,
            "source": self.source.map(|s| s.0)
        })))
    }

    fn name(&self) -> &'static str {
        "HealCommand"
    }
}

/// Command to set an entity's health directly.
#[derive(Debug, Clone)]
pub struct SetHealthCommand {
    /// The entity to modify.
    pub entity_id: EntityId,
    /// The new health value.
    pub health: i32,
}

impl SetHealthCommand {
    /// Create a new set health command.
    #[must_use]
    pub const fn new(entity_id: EntityId, health: i32) -> Self {
        Self { entity_id, health }
    }
}

impl Command for SetHealthCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "SetHealthCommand: set entity {} health to {}",
            self.entity_id, self.health
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "health": self.health
        })))
    }

    fn name(&self) -> &'static str {
        "SetHealthCommand"
    }
}

/// Command to set an entity's max health.
#[derive(Debug, Clone)]
pub struct SetMaxHealthCommand {
    /// The entity to modify.
    pub entity_id: EntityId,
    /// The new max health value.
    pub max_health: i32,
}

impl SetMaxHealthCommand {
    /// Create a new set max health command.
    #[must_use]
    pub const fn new(entity_id: EntityId, max_health: i32) -> Self {
        Self {
            entity_id,
            max_health,
        }
    }
}

impl Command for SetMaxHealthCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "SetMaxHealthCommand: set entity {} max health to {}",
            self.entity_id, self.max_health
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "max_health": self.max_health
        })))
    }

    fn name(&self) -> &'static str {
        "SetMaxHealthCommand"
    }
}

/// Command to instantly kill an entity.
#[derive(Debug, Clone)]
pub struct KillCommand {
    /// The entity to kill.
    pub entity_id: EntityId,
}

impl KillCommand {
    /// Create a new kill command.
    #[must_use]
    pub const fn new(entity_id: EntityId) -> Self {
        Self { entity_id }
    }
}

impl Command for KillCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!("KillCommand: killed entity {}", self.entity_id);

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0
        })))
    }

    fn name(&self) -> &'static str {
        "KillCommand"
    }
}

/// Command to revive a dead entity.
#[derive(Debug, Clone)]
pub struct ReviveCommand {
    /// The entity to revive.
    pub entity_id: EntityId,
    /// Health to revive with (None = full health).
    pub revive_health: Option<i32>,
}

impl ReviveCommand {
    /// Create a revive command that restores to full health.
    #[must_use]
    pub const fn full(entity_id: EntityId) -> Self {
        Self {
            entity_id,
            revive_health: None,
        }
    }

    /// Create a revive command with specific health.
    #[must_use]
    pub const fn with_health(entity_id: EntityId, health: i32) -> Self {
        Self {
            entity_id,
            revive_health: Some(health),
        }
    }
}

impl Command for ReviveCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "ReviveCommand: revived entity {} with health {:?}",
            self.entity_id, self.revive_health
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "revive_health": self.revive_health
        })))
    }

    fn name(&self) -> &'static str {
        "ReviveCommand"
    }
}

// =============================================================================
// Module Implementation
// =============================================================================

/// The Health Module provides health, damage, and death systems.
///
/// On each tick, the module checks all entities with Health components.
/// If health <= 0 and the entity doesn't have the Dead component, it adds Dead.
pub struct HealthModule {
    /// Number of entities marked dead this session.
    deaths_this_session: u64,
    /// Total damage dealt this session.
    total_damage_dealt: i64,
    /// Total healing done this session.
    total_healing_done: i64,
}

impl HealthModule {
    /// Create a new health module.
    #[must_use]
    pub fn new() -> Self {
        Self {
            deaths_this_session: 0,
            total_damage_dealt: 0,
            total_healing_done: 0,
        }
    }

    /// Get the number of deaths this session.
    #[must_use]
    pub const fn deaths_this_session(&self) -> u64 {
        self.deaths_this_session
    }

    /// Get the total damage dealt this session.
    #[must_use]
    pub const fn total_damage_dealt(&self) -> i64 {
        self.total_damage_dealt
    }

    /// Get the total healing done this session.
    #[must_use]
    pub const fn total_healing_done(&self) -> i64 {
        self.total_healing_done
    }

    /// Check health and mark dead entities.
    fn check_deaths(&mut self, world: &mut StormWorld) {
        let legion_world = world.legion_world_mut();

        // First pass: collect entities that need Dead component
        let mut entities_to_mark_dead = Vec::new();
        {
            // Query entities with Health but without Dead
            let mut query = <(legion::Entity, &Health)>::query();

            for (entity, health) in query.iter(legion_world) {
                if health.is_dead() {
                    entities_to_mark_dead.push(*entity);
                }
            }
        }

        // Second pass: add Dead component to collected entities
        for entity in entities_to_mark_dead {
            // Check if already dead (has Dead component)
            if let Some(entry) = legion_world.entry(entity) {
                if entry.get_component::<Dead>().is_ok() {
                    continue; // Already dead
                }
            }

            // Add Dead component
            if let Some(mut entry) = legion_world.entry(entity) {
                entry.add_component(Dead);
                self.deaths_this_session += 1;
                trace!("Entity {:?} marked as dead", entity);
            }
        }
    }
}

impl Default for HealthModule {
    fn default() -> Self {
        Self::new()
    }
}

impl Module for HealthModule {
    fn descriptor(&self) -> &'static ModuleDescriptor {
        static DESC: ModuleDescriptor =
            ModuleDescriptor::new("health-module", "1.0.0", "Health, damage, and death systems");
        &DESC
    }

    fn on_load(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        // Register component types
        ctx.world.register_component::<Health>();
        ctx.world.register_component::<MaxHealth>();
        ctx.world.register_component::<Dead>();

        debug!("HealthModule loaded - registered Health, MaxHealth, and Dead components");
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        self.check_deaths(ctx.world);
        Ok(())
    }

    fn on_unload(&mut self) -> Result<()> {
        debug!(
            "HealthModule unloading - deaths: {}, damage: {}, healing: {}",
            self.deaths_this_session, self.total_damage_dealt, self.total_healing_done
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use legion::IntoQuery;
    use stormstack_core::{CommandQueue, MatchId, UserId};
    use stormstack_ecs::EcsWorld;

    // =========================================================================
    // Health Component Tests
    // =========================================================================

    #[test]
    fn health_new() {
        let health = Health::new(50);
        assert_eq!(health.value, 50);
    }

    #[test]
    fn health_full() {
        let health = Health::full(100);
        assert_eq!(health.value, 100);
    }

    #[test]
    fn health_default() {
        let health = Health::default();
        assert_eq!(health.value, 100);
    }

    #[test]
    fn health_is_dead() {
        assert!(!Health::new(1).is_dead());
        assert!(Health::new(0).is_dead());
        assert!(Health::new(-10).is_dead());
    }

    #[test]
    fn health_is_full() {
        assert!(Health::new(100).is_full(100));
        assert!(Health::new(150).is_full(100)); // Over max is still "full"
        assert!(!Health::new(99).is_full(100));
    }

    #[test]
    fn health_percentage() {
        assert!((Health::new(50).percentage(100) - 0.5).abs() < 0.001);
        assert!((Health::new(100).percentage(100) - 1.0).abs() < 0.001);
        assert!((Health::new(0).percentage(100) - 0.0).abs() < 0.001);
        // Clamp above 1.0
        assert!((Health::new(150).percentage(100) - 1.0).abs() < 0.001);
        // Edge case: max health 0
        assert_eq!(Health::new(50).percentage(0), 0.0);
    }

    #[test]
    fn health_serialization() {
        let health = Health::new(75);
        let json = serde_json::to_string(&health).expect("serialize");
        let parsed: Health = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(health, parsed);
    }

    // =========================================================================
    // MaxHealth Component Tests
    // =========================================================================

    #[test]
    fn max_health_new() {
        let max = MaxHealth::new(200);
        assert_eq!(max.value, 200);
    }

    #[test]
    fn max_health_default() {
        let max = MaxHealth::default();
        assert_eq!(max.value, 100);
    }

    #[test]
    fn max_health_serialization() {
        let max = MaxHealth::new(150);
        let json = serde_json::to_string(&max).expect("serialize");
        let parsed: MaxHealth = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(max, parsed);
    }

    // =========================================================================
    // Dead Component Tests
    // =========================================================================

    #[test]
    fn dead_new() {
        let dead = Dead::new();
        assert_eq!(dead, Dead);
    }

    #[test]
    fn dead_default() {
        let dead = Dead::default();
        assert_eq!(dead, Dead);
    }

    #[test]
    fn dead_serialization() {
        let dead = Dead;
        let json = serde_json::to_string(&dead).expect("serialize");
        let parsed: Dead = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(dead, parsed);
    }

    // =========================================================================
    // Command Tests
    // =========================================================================

    #[test]
    fn damage_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = DamageCommand::new(entity, 50);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["damage"].as_i64().expect("damage"), 50);
    }

    #[test]
    fn damage_command_with_source() {
        let mut world = StormWorld::new();
        let target = world.spawn();
        let attacker = world.spawn();

        let cmd = DamageCommand::with_source(target, 25, attacker);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["source"].as_u64().expect("source"), attacker.0);
    }

    #[test]
    fn damage_command_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake = EntityId(9999);

        let cmd = DamageCommand::new(fake, 10);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
    }

    #[test]
    fn heal_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = HealCommand::new(entity, 30);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["healing"].as_i64().expect("healing"), 30);
    }

    #[test]
    fn heal_command_with_source() {
        let mut world = StormWorld::new();
        let target = world.spawn();
        let healer = world.spawn();

        let cmd = HealCommand::with_source(target, 20, healer);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["source"].as_u64().expect("source"), healer.0);
    }

    #[test]
    fn set_health_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = SetHealthCommand::new(entity, 75);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["health"].as_i64().expect("health"), 75);
    }

    #[test]
    fn set_max_health_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = SetMaxHealthCommand::new(entity, 200);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["max_health"].as_i64().expect("max_health"), 200);
    }

    #[test]
    fn kill_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = KillCommand::new(entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());
    }

    #[test]
    fn revive_command_full() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = ReviveCommand::full(entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert!(data["revive_health"].is_null());
    }

    #[test]
    fn revive_command_with_health() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = ReviveCommand::with_health(entity, 50);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["revive_health"].as_i64().expect("revive_health"), 50);
    }

    // =========================================================================
    // Module Lifecycle Tests
    // =========================================================================

    #[test]
    fn health_module_descriptor() {
        let module = HealthModule::new();
        let desc = module.descriptor();

        assert_eq!(desc.name, "health-module");
        assert_eq!(desc.version, "1.0.0");
        assert!(desc.is_abi_compatible());
    }

    #[test]
    fn health_module_on_load() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);

        module.on_load(&mut ctx).expect("on_load");

        // Verify components are registered
        assert!(world.component_type_id::<Health>().is_some());
        assert!(world.component_type_id::<MaxHealth>().is_some());
        assert!(world.component_type_id::<Dead>().is_some());
    }

    #[test]
    fn health_module_on_tick_no_entities() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);

        module.on_tick(&mut ctx).expect("on_tick");
        assert_eq!(module.deaths_this_session(), 0);
    }

    #[test]
    fn health_module_on_unload() {
        let mut module = HealthModule::new();
        module.on_unload().expect("on_unload");
    }

    #[test]
    fn health_module_default() {
        let module = HealthModule::default();
        assert_eq!(module.deaths_this_session(), 0);
        assert_eq!(module.total_damage_dealt(), 0);
        assert_eq!(module.total_healing_done(), 0);
    }

    // =========================================================================
    // Death System Tests
    // =========================================================================

    #[test]
    fn health_system_marks_dead() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with zero health
        world.spawn_with((Health::new(0),));

        // Run tick
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);
        module.on_tick(&mut ctx).expect("on_tick");

        // Check that Dead was added
        assert_eq!(module.deaths_this_session(), 1);

        // Verify Dead component exists
        let mut query = <&Dead>::query();
        let count = query.iter(world.legion_world()).count();
        assert_eq!(count, 1);
    }

    #[test]
    fn health_system_marks_negative_health_as_dead() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with negative health
        world.spawn_with((Health::new(-50),));

        // Run tick
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);
        module.on_tick(&mut ctx).expect("on_tick");

        assert_eq!(module.deaths_this_session(), 1);
    }

    #[test]
    fn health_system_does_not_mark_healthy() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with positive health
        world.spawn_with((Health::new(100),));

        // Run tick
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);
        module.on_tick(&mut ctx).expect("on_tick");

        assert_eq!(module.deaths_this_session(), 0);
    }

    #[test]
    fn health_system_does_not_double_mark() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with zero health
        world.spawn_with((Health::new(0),));

        // Run multiple ticks
        for tick in 1..=5 {
            let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
            module.on_tick(&mut ctx).expect("on_tick");
        }

        // Should only count one death, not five
        assert_eq!(module.deaths_this_session(), 1);
    }

    #[test]
    fn health_system_multiple_entities() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create mix of healthy and dead entities
        world.spawn_with((Health::new(100),)); // Healthy
        world.spawn_with((Health::new(0),)); // Dead
        world.spawn_with((Health::new(50),)); // Healthy
        world.spawn_with((Health::new(-10),)); // Dead

        // Run tick
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);
        module.on_tick(&mut ctx).expect("on_tick");

        // Should have marked 2 entities as dead
        assert_eq!(module.deaths_this_session(), 2);
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    #[test]
    fn health_module_full_lifecycle() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();

        // Load
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create some entities
        world.spawn_with((Health::new(100), MaxHealth::new(100)));
        world.spawn_with((Health::new(0), MaxHealth::new(100)));

        // Tick multiple times
        for tick in 1..=5 {
            let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
            module.on_tick(&mut ctx).expect("on_tick");
        }

        assert_eq!(module.deaths_this_session(), 1);

        // Unload
        module.on_unload().expect("on_unload");
    }

    #[test]
    fn health_module_with_command_queue() {
        let mut module = HealthModule::new();
        let mut world = StormWorld::new();
        let mut queue = CommandQueue::new();
        let match_id = MatchId::new();
        let user_id = UserId::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity
        let entity = world.spawn();

        // Queue health commands
        queue.push(Box::new(SetHealthCommand::new(entity, 100)), user_id);
        queue.push(Box::new(DamageCommand::new(entity, 30)), user_id);
        queue.push(Box::new(HealCommand::new(entity, 10)), user_id);

        // Execute commands
        let results = queue.execute_all(&mut world, match_id);
        assert_eq!(results.len(), 3);
        assert!(results.iter().all(|r| r.is_success()));
    }
}
