//! # StormStack ECS
//!
//! Entity-Component-System implementation for StormStack using legion.
//!
//! This crate provides:
//! - `EcsWorld` trait for world management
//! - `StormWorld` implementation wrapping legion
//! - Snapshot and delta serialization for streaming
//! - System scheduling and tick execution
//!
//! ## Performance Target
//! - ≥746 ticks/sec with 10k entities

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod world;

pub use world::{shared_world, EcsWorld, Marker, SharedWorld, StormWorld};
