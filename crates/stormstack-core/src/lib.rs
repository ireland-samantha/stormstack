//! # `StormStack` Core
//!
//! Core types, traits, and error definitions shared across all `StormStack` crates.
//!
//! This crate provides:
//! - Strongly-typed identifiers (`EntityId`, `ContainerId`, `MatchId`, etc.)
//! - Common error types (`StormError`, `AuthError`, `WasmError`)
//! - Core traits and interfaces
//! - Shared DTOs and domain models
//! - Command system for game logic execution

#![warn(missing_docs)]
#![warn(clippy::all)]
#![warn(clippy::pedantic)]

pub mod command;
pub mod config;
pub mod error;
pub mod id;
pub mod snapshot;

pub use command::{
    Command, CommandContext, CommandQueue, CommandResult, CommandWorld, DespawnEntityCommand,
    QueuedCommand, SpawnEntityCommand,
};
pub use config::MatchConfig;
pub use error::{AuthError, ModuleError, StormError, WasmError};
pub use id::{ComponentTypeId, ConnectionId, ContainerId, EntityId, MatchId, ResourceId, TenantId, UserId};
pub use snapshot::{ComponentUpdate, EntitySnapshot, WorldDelta, WorldSnapshot};

/// Re-export common result type
pub type Result<T> = std::result::Result<T, StormError>;
