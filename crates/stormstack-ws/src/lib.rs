//! # StormStack WebSocket
//!
//! WebSocket handling for real-time game updates.
//!
//! This crate provides:
//! - WebSocket connection handling
//! - Client/server message types
//! - Subscription management
//! - Snapshot and delta streaming
//!
//! See `docs/migration/PROTOCOL.md` for protocol specification.

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod handler;
pub mod messages;

pub use messages::{ClientMessage, ServerMessage};

// TODO: Implement WebSocket handling
