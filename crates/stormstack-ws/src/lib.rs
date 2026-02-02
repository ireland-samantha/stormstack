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

pub mod connection;
pub mod handler;
pub mod messages;
pub mod subscription;

pub use connection::{
    shared_connection_manager, ConnectionManager, ConnectionState, MessageSender,
    SharedConnectionManager,
};
pub use handler::{ConnectionHandler, MatchStateProvider, WsHandler};
pub use messages::{ClientMessage, Command, CommandResult, ServerMessage};
pub use subscription::{shared_subscriptions, SharedSubscriptionManager, SubscriptionManager};
