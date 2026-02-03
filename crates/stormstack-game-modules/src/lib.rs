//! # StormStack Game Modules
//!
//! Core game modules providing fundamental ECS functionality for game development.
//!
//! This crate provides several modules that can be loaded into the StormStack
//! module system to add common game functionality:
//!
//! ## Modules
//!
//! - [`EntityModule`](entity::EntityModule) - Core entity management with ownership tracking
//! - [`MovementModule`](movement::MovementModule) - Position, velocity, and physics
//! - [`HealthModule`](health::HealthModule) - Health, damage, and death systems
//!
//! ## Quick Start
//!
//! ```ignore
//! use stormstack_game_modules::{EntityModule, MovementModule, HealthModule};
//! use stormstack_modules::{Module, ModuleContext};
//! use stormstack_ecs::StormWorld;
//!
//! // Create world and modules
//! let mut world = StormWorld::new();
//! let mut entity_module = EntityModule::new();
//! let mut movement_module = MovementModule::new();
//! let mut health_module = HealthModule::new();
//!
//! // Load modules
//! let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
//! entity_module.on_load(&mut ctx)?;
//! movement_module.on_load(&mut ctx)?;
//! health_module.on_load(&mut ctx)?;
//!
//! // Create an entity with components from all modules
//! use stormstack_game_modules::movement::{Position, Velocity};
//! use stormstack_game_modules::health::{Health, MaxHealth};
//!
//! world.spawn_with((
//!     Position::new(0.0, 0.0),
//!     Velocity::new(10.0, 5.0),
//!     Health::new(100),
//!     MaxHealth::new(100),
//! ));
//!
//! // Game loop
//! for tick in 1..=60 {
//!     let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
//!     entity_module.on_tick(&mut ctx)?;
//!     movement_module.on_tick(&mut ctx)?;  // Updates positions
//!     health_module.on_tick(&mut ctx)?;    // Checks for deaths
//! }
//! ```
//!
//! ## Components
//!
//! ### Entity Module Components
//! - [`EntityIdComponent`](entity::EntityIdComponent) - Unique entity identifier
//! - [`OwnerIdComponent`](entity::OwnerIdComponent) - Owner tracking for multi-tenant
//! - [`MatchIdComponent`](entity::MatchIdComponent) - Match association
//!
//! ### Movement Module Components
//! - [`Position`](movement::Position) - 2D position (x, y)
//! - [`Velocity`](movement::Velocity) - 2D velocity (vx, vy)
//!
//! ### Health Module Components
//! - [`Health`](health::Health) - Current health value
//! - [`MaxHealth`](health::MaxHealth) - Maximum health capacity
//! - [`Dead`](health::Dead) - Marker for dead entities
//!
//! ## Commands
//!
//! Each module provides commands for queuing operations:
//!
//! ### Entity Commands
//! - `SpawnEntityWithOwnerCommand` - Spawn with ownership
//! - `DespawnOwnedEntityCommand` - Despawn with ownership check
//! - `TransferOwnershipCommand` - Transfer entity ownership
//!
//! ### Movement Commands
//! - `SetPositionCommand` - Set absolute position
//! - `SetVelocityCommand` - Set velocity
//! - `MoveCommand` - Relative movement
//! - `StopCommand` - Stop movement
//!
//! ### Health Commands
//! - `DamageCommand` - Deal damage
//! - `HealCommand` - Heal damage
//! - `SetHealthCommand` - Set health directly
//! - `SetMaxHealthCommand` - Set max health
//! - `KillCommand` - Instantly kill
//! - `ReviveCommand` - Revive dead entity

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod entity;
pub mod health;
pub mod movement;

// Re-export main module structs for convenience
pub use entity::EntityModule;
pub use health::HealthModule;
pub use movement::MovementModule;

// Re-export commonly used components
pub use entity::{EntityIdComponent, MatchIdComponent, OwnerIdComponent};
pub use health::{Dead, Health, MaxHealth};
pub use movement::{Position, Velocity};

// Re-export commands
pub use entity::{DespawnOwnedEntityCommand, SpawnEntityWithOwnerCommand, TransferOwnershipCommand};
pub use health::{
    DamageCommand, HealCommand, KillCommand, ReviveCommand, SetHealthCommand, SetMaxHealthCommand,
};
pub use movement::{MoveCommand, SetPositionCommand, SetVelocityCommand, StopCommand};

#[cfg(test)]
mod tests {
    use super::*;
    use legion::IntoQuery;
    use stormstack_ecs::StormWorld;
    use stormstack_modules::{Module, ModuleContext};

    #[test]
    fn all_modules_load_successfully() {
        let mut world = StormWorld::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);

        let mut entity = EntityModule::new();
        let mut movement = MovementModule::new();
        let mut health = HealthModule::new();

        entity.on_load(&mut ctx).expect("entity on_load");
        movement.on_load(&mut ctx).expect("movement on_load");
        health.on_load(&mut ctx).expect("health on_load");
    }

    #[test]
    fn all_modules_tick_successfully() {
        let mut world = StormWorld::new();

        // Load modules
        let mut entity = EntityModule::new();
        let mut movement = MovementModule::new();
        let mut health = HealthModule::new();

        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        entity.on_load(&mut ctx).expect("entity on_load");
        movement.on_load(&mut ctx).expect("movement on_load");
        health.on_load(&mut ctx).expect("health on_load");

        // Tick all modules
        for tick in 1..=10 {
            let mut ctx = ModuleContext::new(&mut world, tick, 0.016);
            entity.on_tick(&mut ctx).expect("entity on_tick");
            movement.on_tick(&mut ctx).expect("movement on_tick");
            health.on_tick(&mut ctx).expect("health on_tick");
        }
    }

    #[test]
    fn all_modules_unload_successfully() {
        let mut entity = EntityModule::new();
        let mut movement = MovementModule::new();
        let mut health = HealthModule::new();

        entity.on_unload().expect("entity on_unload");
        movement.on_unload().expect("movement on_unload");
        health.on_unload().expect("health on_unload");
    }

    #[test]
    fn modules_have_unique_names() {
        let entity = EntityModule::new();
        let movement = MovementModule::new();
        let health = HealthModule::new();

        let names = vec![
            entity.descriptor().name,
            movement.descriptor().name,
            health.descriptor().name,
        ];

        // Check all names are unique
        for i in 0..names.len() {
            for j in (i + 1)..names.len() {
                assert_ne!(names[i], names[j], "Module names must be unique");
            }
        }
    }

    #[test]
    fn integration_entity_with_all_components() {
        let mut world = StormWorld::new();

        // Load all modules
        let mut entity_mod = EntityModule::new();
        let mut movement_mod = MovementModule::new();
        let mut health_mod = HealthModule::new();

        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        entity_mod.on_load(&mut ctx).expect("on_load");
        movement_mod.on_load(&mut ctx).expect("on_load");
        health_mod.on_load(&mut ctx).expect("on_load");

        // Create entity with all component types
        world.spawn_with((
            Position::new(10.0, 20.0),
            Velocity::new(1.0, 1.0),
            Health::new(100),
            MaxHealth::new(100),
        ));

        assert_eq!(world.entity_count(), 1);

        // Run game loop
        for tick in 1..=10 {
            let mut ctx = ModuleContext::new(&mut world, tick, 1.0);
            entity_mod.on_tick(&mut ctx).expect("on_tick");
            movement_mod.on_tick(&mut ctx).expect("on_tick");
            health_mod.on_tick(&mut ctx).expect("on_tick");
        }

        // Verify movement occurred
        assert!(movement_mod.total_distance_moved() > 0.0);
    }

    #[test]
    fn integration_damage_kills_entity() {
        let mut world = StormWorld::new();

        // Load health module
        let mut health_mod = HealthModule::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        health_mod.on_load(&mut ctx).expect("on_load");

        // Create entity with health that will die
        world.spawn_with((Health::new(0), MaxHealth::new(100)));

        // Run tick to check deaths
        let mut ctx = ModuleContext::new(&mut world, 1, 0.016);
        health_mod.on_tick(&mut ctx).expect("on_tick");

        // Entity should be marked dead
        assert_eq!(health_mod.deaths_this_session(), 1);
    }

    #[test]
    fn integration_movement_with_velocity() {
        let mut world = StormWorld::new();

        // Load movement module
        let mut movement_mod = MovementModule::new();
        let mut ctx = ModuleContext::new(&mut world, 0, 0.016);
        movement_mod.on_load(&mut ctx).expect("on_load");

        // Create moving entity
        world.spawn_with((Position::origin(), Velocity::new(60.0, 0.0)));

        // Run at 60 FPS for 60 frames (should move 60 units)
        for tick in 1..=60 {
            let mut ctx = ModuleContext::new(&mut world, tick, 1.0 / 60.0);
            movement_mod.on_tick(&mut ctx).expect("on_tick");
        }

        // Check final position (should be approximately 60)
        let mut query = <&Position>::query();
        for pos in query.iter(world.legion_world()) {
            assert!((pos.x - 60.0).abs() < 0.1);
        }
    }
}
