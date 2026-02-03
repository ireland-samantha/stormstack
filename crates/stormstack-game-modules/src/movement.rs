//! Movement Module - Position and velocity components with physics system.
//!
//! This module provides basic 2D movement functionality:
//!
//! - Position component (x, y coordinates)
//! - Velocity component (vx, vy for speed)
//! - Movement system that applies velocity to position each tick
//! - Commands for setting position, velocity, and relative movement
//!
//! # Example
//!
//! ```ignore
//! use stormstack_game_modules::movement::{MovementModule, Position, Velocity};
//!
//! let mut module = MovementModule::new();
//! module.on_load(&mut ctx)?;
//!
//! // Create an entity with position and velocity
//! world.spawn_with((
//!     Position::new(0.0, 0.0),
//!     Velocity::new(1.0, 0.5),
//! ));
//!
//! // On each tick, position will be updated by velocity * delta_time
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

/// 2D position component.
///
/// Represents an entity's location in 2D space.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Position {
    /// X coordinate.
    pub x: f32,
    /// Y coordinate.
    pub y: f32,
}

impl Position {
    /// Create a new position at the given coordinates.
    #[must_use]
    pub const fn new(x: f32, y: f32) -> Self {
        Self { x, y }
    }

    /// Create a position at the origin (0, 0).
    #[must_use]
    pub const fn origin() -> Self {
        Self::new(0.0, 0.0)
    }

    /// Calculate the distance to another position.
    #[must_use]
    pub fn distance_to(&self, other: &Position) -> f32 {
        let dx = other.x - self.x;
        let dy = other.y - self.y;
        (dx * dx + dy * dy).sqrt()
    }

    /// Calculate the squared distance to another position (faster, no sqrt).
    #[must_use]
    pub fn distance_squared_to(&self, other: &Position) -> f32 {
        let dx = other.x - self.x;
        let dy = other.y - self.y;
        dx * dx + dy * dy
    }
}

impl Default for Position {
    fn default() -> Self {
        Self::origin()
    }
}

/// 2D velocity component.
///
/// Represents an entity's speed and direction of movement.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Velocity {
    /// Velocity in the X direction.
    pub vx: f32,
    /// Velocity in the Y direction.
    pub vy: f32,
}

impl Velocity {
    /// Create a new velocity with the given components.
    #[must_use]
    pub const fn new(vx: f32, vy: f32) -> Self {
        Self { vx, vy }
    }

    /// Create a zero velocity.
    #[must_use]
    pub const fn zero() -> Self {
        Self::new(0.0, 0.0)
    }

    /// Calculate the magnitude (speed) of this velocity.
    #[must_use]
    pub fn magnitude(&self) -> f32 {
        (self.vx * self.vx + self.vy * self.vy).sqrt()
    }

    /// Create a normalized (unit length) velocity.
    #[must_use]
    pub fn normalized(&self) -> Self {
        let mag = self.magnitude();
        if mag > 0.0 {
            Self::new(self.vx / mag, self.vy / mag)
        } else {
            Self::zero()
        }
    }

    /// Scale this velocity by a factor.
    #[must_use]
    pub fn scaled(&self, factor: f32) -> Self {
        Self::new(self.vx * factor, self.vy * factor)
    }
}

impl Default for Velocity {
    fn default() -> Self {
        Self::zero()
    }
}

// =============================================================================
// Commands
// =============================================================================

/// Command to set an entity's position directly.
#[derive(Debug, Clone)]
pub struct SetPositionCommand {
    /// The entity to modify.
    pub entity_id: EntityId,
    /// The new X coordinate.
    pub x: f32,
    /// The new Y coordinate.
    pub y: f32,
}

impl SetPositionCommand {
    /// Create a new set position command.
    #[must_use]
    pub const fn new(entity_id: EntityId, x: f32, y: f32) -> Self {
        Self { entity_id, x, y }
    }
}

impl Command for SetPositionCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "SetPositionCommand: set entity {} to ({}, {})",
            self.entity_id, self.x, self.y
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "x": self.x,
            "y": self.y
        })))
    }

    fn name(&self) -> &'static str {
        "SetPositionCommand"
    }
}

/// Command to set an entity's velocity.
#[derive(Debug, Clone)]
pub struct SetVelocityCommand {
    /// The entity to modify.
    pub entity_id: EntityId,
    /// The new X velocity.
    pub vx: f32,
    /// The new Y velocity.
    pub vy: f32,
}

impl SetVelocityCommand {
    /// Create a new set velocity command.
    #[must_use]
    pub const fn new(entity_id: EntityId, vx: f32, vy: f32) -> Self {
        Self { entity_id, vx, vy }
    }
}

impl Command for SetVelocityCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "SetVelocityCommand: set entity {} velocity to ({}, {})",
            self.entity_id, self.vx, self.vy
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "vx": self.vx,
            "vy": self.vy
        })))
    }

    fn name(&self) -> &'static str {
        "SetVelocityCommand"
    }
}

/// Command to move an entity by a relative offset.
#[derive(Debug, Clone)]
pub struct MoveCommand {
    /// The entity to move.
    pub entity_id: EntityId,
    /// The X offset to move by.
    pub dx: f32,
    /// The Y offset to move by.
    pub dy: f32,
}

impl MoveCommand {
    /// Create a new move command.
    #[must_use]
    pub const fn new(entity_id: EntityId, dx: f32, dy: f32) -> Self {
        Self { entity_id, dx, dy }
    }
}

impl Command for MoveCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!(
            "MoveCommand: moved entity {} by ({}, {})",
            self.entity_id, self.dx, self.dy
        );

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0,
            "dx": self.dx,
            "dy": self.dy
        })))
    }

    fn name(&self) -> &'static str {
        "MoveCommand"
    }
}

/// Command to stop an entity's movement (set velocity to zero).
#[derive(Debug, Clone)]
pub struct StopCommand {
    /// The entity to stop.
    pub entity_id: EntityId,
}

impl StopCommand {
    /// Create a new stop command.
    #[must_use]
    pub const fn new(entity_id: EntityId) -> Self {
        Self { entity_id }
    }
}

impl Command for StopCommand {
    fn execute(&self, ctx: &mut CommandContext) -> Result<CommandResult> {
        if !ctx.world.entity_exists(self.entity_id) {
            return Ok(CommandResult::failure(format!(
                "Entity {} does not exist",
                self.entity_id
            )));
        }

        debug!("StopCommand: stopped entity {}", self.entity_id);

        Ok(CommandResult::success_with_data(serde_json::json!({
            "entity_id": self.entity_id.0
        })))
    }

    fn name(&self) -> &'static str {
        "StopCommand"
    }
}

// =============================================================================
// Module Implementation
// =============================================================================

/// The Movement Module provides position, velocity, and movement systems.
///
/// On each tick, the module applies velocity to position for all entities
/// that have both components.
pub struct MovementModule {
    /// Total distance moved across all entities.
    total_distance_moved: f64,
    /// Number of entities processed per tick.
    last_tick_processed: usize,
}

impl MovementModule {
    /// Create a new movement module.
    #[must_use]
    pub fn new() -> Self {
        Self {
            total_distance_moved: 0.0,
            last_tick_processed: 0,
        }
    }

    /// Get the total distance moved across all entities.
    #[must_use]
    pub fn total_distance_moved(&self) -> f64 {
        self.total_distance_moved
    }

    /// Get the number of entities processed in the last tick.
    #[must_use]
    pub const fn last_tick_processed(&self) -> usize {
        self.last_tick_processed
    }

    /// Apply velocity to position for all entities.
    fn apply_movement(&mut self, world: &mut StormWorld, delta_time: f64) {
        let mut processed = 0;
        let dt = delta_time as f32;

        // Query all entities with Position and Velocity
        let legion_world = world.legion_world_mut();
        let mut query = <(&mut Position, &Velocity)>::query();

        for (pos, vel) in query.iter_mut(legion_world) {
            let dx = vel.vx * dt;
            let dy = vel.vy * dt;

            pos.x += dx;
            pos.y += dy;

            self.total_distance_moved += (dx * dx + dy * dy).sqrt() as f64;
            processed += 1;
        }

        self.last_tick_processed = processed;

        if processed > 0 {
            trace!(
                "MovementModule: applied movement to {} entities (dt: {})",
                processed,
                delta_time
            );
        }
    }
}

impl Default for MovementModule {
    fn default() -> Self {
        Self::new()
    }
}

impl Module for MovementModule {
    fn descriptor(&self) -> &'static ModuleDescriptor {
        static DESC: ModuleDescriptor = ModuleDescriptor::new(
            "movement-module",
            "1.0.0",
            "Position, velocity, and movement physics",
        );
        &DESC
    }

    fn on_load(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        // Register component types
        ctx.world.register_component::<Position>();
        ctx.world.register_component::<Velocity>();

        debug!("MovementModule loaded - registered Position and Velocity components");
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut ModuleContext) -> Result<()> {
        self.apply_movement(ctx.world, ctx.delta_time);
        Ok(())
    }

    fn on_unload(&mut self) -> Result<()> {
        debug!(
            "MovementModule unloading - total distance moved: {:.2}",
            self.total_distance_moved
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use legion::IntoQuery;
    use stormstack_core::{MatchId, UserId};
    use stormstack_ecs::EcsWorld;

    // =========================================================================
    // Position Tests
    // =========================================================================

    #[test]
    fn position_new() {
        let pos = Position::new(10.0, 20.0);
        assert_eq!(pos.x, 10.0);
        assert_eq!(pos.y, 20.0);
    }

    #[test]
    fn position_origin() {
        let pos = Position::origin();
        assert_eq!(pos.x, 0.0);
        assert_eq!(pos.y, 0.0);
    }

    #[test]
    fn position_default() {
        let pos = Position::default();
        assert_eq!(pos, Position::origin());
    }

    #[test]
    fn position_distance_to() {
        let p1 = Position::new(0.0, 0.0);
        let p2 = Position::new(3.0, 4.0);
        assert!((p1.distance_to(&p2) - 5.0).abs() < 0.001);
    }

    #[test]
    fn position_distance_squared_to() {
        let p1 = Position::new(0.0, 0.0);
        let p2 = Position::new(3.0, 4.0);
        assert!((p1.distance_squared_to(&p2) - 25.0).abs() < 0.001);
    }

    #[test]
    fn position_serialization() {
        let pos = Position::new(1.5, -2.5);
        let json = serde_json::to_string(&pos).expect("serialize");
        let parsed: Position = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(pos, parsed);
    }

    // =========================================================================
    // Velocity Tests
    // =========================================================================

    #[test]
    fn velocity_new() {
        let vel = Velocity::new(5.0, -3.0);
        assert_eq!(vel.vx, 5.0);
        assert_eq!(vel.vy, -3.0);
    }

    #[test]
    fn velocity_zero() {
        let vel = Velocity::zero();
        assert_eq!(vel.vx, 0.0);
        assert_eq!(vel.vy, 0.0);
    }

    #[test]
    fn velocity_default() {
        let vel = Velocity::default();
        assert_eq!(vel, Velocity::zero());
    }

    #[test]
    fn velocity_magnitude() {
        let vel = Velocity::new(3.0, 4.0);
        assert!((vel.magnitude() - 5.0).abs() < 0.001);
    }

    #[test]
    fn velocity_magnitude_zero() {
        let vel = Velocity::zero();
        assert_eq!(vel.magnitude(), 0.0);
    }

    #[test]
    fn velocity_normalized() {
        let vel = Velocity::new(3.0, 4.0);
        let norm = vel.normalized();
        assert!((norm.magnitude() - 1.0).abs() < 0.001);
        assert!((norm.vx - 0.6).abs() < 0.001);
        assert!((norm.vy - 0.8).abs() < 0.001);
    }

    #[test]
    fn velocity_normalized_zero() {
        let vel = Velocity::zero();
        let norm = vel.normalized();
        assert_eq!(norm, Velocity::zero());
    }

    #[test]
    fn velocity_scaled() {
        let vel = Velocity::new(2.0, 3.0);
        let scaled = vel.scaled(2.0);
        assert_eq!(scaled.vx, 4.0);
        assert_eq!(scaled.vy, 6.0);
    }

    #[test]
    fn velocity_serialization() {
        let vel = Velocity::new(1.5, -2.5);
        let json = serde_json::to_string(&vel).expect("serialize");
        let parsed: Velocity = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(vel, parsed);
    }

    // =========================================================================
    // Command Tests
    // =========================================================================

    #[test]
    fn set_position_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = SetPositionCommand::new(entity, 100.0, 200.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["x"].as_f64().expect("x"), 100.0);
        assert_eq!(data["y"].as_f64().expect("y"), 200.0);
    }

    #[test]
    fn set_position_command_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);

        let cmd = SetPositionCommand::new(fake_entity, 0.0, 0.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
    }

    #[test]
    fn set_velocity_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = SetVelocityCommand::new(entity, 5.0, -3.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["vx"].as_f64().expect("vx"), 5.0);
        assert_eq!(data["vy"].as_f64().expect("vy"), -3.0);
    }

    #[test]
    fn set_velocity_command_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);

        let cmd = SetVelocityCommand::new(fake_entity, 0.0, 0.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
    }

    #[test]
    fn move_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = MoveCommand::new(entity, 10.0, -5.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());

        let data = result.data.expect("data");
        assert_eq!(data["dx"].as_f64().expect("dx"), 10.0);
        assert_eq!(data["dy"].as_f64().expect("dy"), -5.0);
    }

    #[test]
    fn move_command_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);

        let cmd = MoveCommand::new(fake_entity, 0.0, 0.0);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_failure());
    }

    #[test]
    fn stop_command_success() {
        let mut world = StormWorld::new();
        let entity = world.spawn();

        let cmd = StopCommand::new(entity);
        let match_id = MatchId::new();
        let user_id = UserId::new();
        let mut ctx = CommandContext::new(&mut world, match_id, user_id, 0);

        let result = cmd.execute(&mut ctx).expect("execute");
        assert!(result.is_success());
    }

    #[test]
    fn stop_command_nonexistent_entity() {
        let mut world = StormWorld::new();
        let fake_entity = EntityId(9999);

        let cmd = StopCommand::new(fake_entity);
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
    fn movement_module_descriptor() {
        let module = MovementModule::new();
        let desc = module.descriptor();

        assert_eq!(desc.name, "movement-module");
        assert_eq!(desc.version, "1.0.0");
        assert!(desc.is_abi_compatible());
    }

    #[test]
    fn movement_module_on_load() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);

        module.on_load(&mut ctx).expect("on_load");

        // Verify components are registered
        assert!(world.component_type_id::<Position>().is_some());
        assert!(world.component_type_id::<Velocity>().is_some());
    }

    #[test]
    fn movement_module_on_tick_no_entities() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);

        module.on_tick(&mut ctx).expect("on_tick");
        assert_eq!(module.last_tick_processed(), 0);
    }

    #[test]
    fn movement_module_on_unload() {
        let mut module = MovementModule::new();
        module.on_unload().expect("on_unload");
    }

    #[test]
    fn movement_module_default() {
        let module = MovementModule::default();
        assert_eq!(module.total_distance_moved(), 0.0);
        assert_eq!(module.last_tick_processed(), 0);
    }

    // =========================================================================
    // Movement System Tests
    // =========================================================================

    #[test]
    fn movement_system_applies_velocity() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with position and velocity
        world.spawn_with((Position::new(0.0, 0.0), Velocity::new(100.0, 50.0)));

        // Run one tick at 1 second delta time for easy math
        let mut ctx = ModuleContext::new(&mut world, 1, 1.0);
        module.on_tick(&mut ctx).expect("on_tick");

        assert_eq!(module.last_tick_processed(), 1);

        // Check position was updated
        let mut query = <&Position>::query();
        for pos in query.iter(world.legion_world()) {
            assert!((pos.x - 100.0).abs() < 0.001);
            assert!((pos.y - 50.0).abs() < 0.001);
        }
    }

    #[test]
    fn movement_system_multiple_entities() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create multiple entities
        world.spawn_with((Position::new(0.0, 0.0), Velocity::new(1.0, 0.0)));
        world.spawn_with((Position::new(10.0, 10.0), Velocity::new(0.0, 1.0)));
        world.spawn_with((Position::new(-5.0, -5.0), Velocity::new(-1.0, -1.0)));

        // Run tick
        let mut ctx = ModuleContext::new(&mut world, 1, 1.0);
        module.on_tick(&mut ctx).expect("on_tick");

        assert_eq!(module.last_tick_processed(), 3);
    }

    #[test]
    fn movement_system_accumulates_distance() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity moving at unit speed
        world.spawn_with((Position::new(0.0, 0.0), Velocity::new(1.0, 0.0)));

        // Run 10 ticks
        for tick in 1..=10 {
            let mut ctx = ModuleContext::new(&mut world, tick, 1.0);
            module.on_tick(&mut ctx).expect("on_tick");
        }

        // Should have moved ~10 units
        assert!((module.total_distance_moved() - 10.0).abs() < 0.001);
    }

    #[test]
    fn movement_system_with_delta_time() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();

        // Load module
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create entity with velocity
        world.spawn_with((Position::new(0.0, 0.0), Velocity::new(60.0, 0.0)));

        // Run at 60 FPS (dt = 1/60)
        let mut ctx = ModuleContext::new(&mut world, 1, 1.0 / 60.0);
        module.on_tick(&mut ctx).expect("on_tick");

        // Position should be ~1.0 (60 * 1/60)
        let mut query = <&Position>::query();
        for pos in query.iter(world.legion_world()) {
            assert!((pos.x - 1.0).abs() < 0.001);
        }
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    #[test]
    fn movement_module_full_lifecycle() {
        let mut module = MovementModule::new();
        let mut world = StormWorld::new();

        // Load
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        module.on_load(&mut ctx).expect("on_load");

        // Create some entities
        world.spawn_with((Position::origin(), Velocity::new(10.0, 10.0)));

        // Tick multiple times
        for tick in 1..=100 {
            let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
            module.on_tick(&mut ctx).expect("on_tick");
        }

        // Verify movement occurred
        assert!(module.total_distance_moved() > 0.0);

        // Unload
        module.on_unload().expect("on_unload");
    }
}
