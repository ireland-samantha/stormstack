//! WebSocket connection handler.

use stormstack_core::{ConnectionId, MatchId, Result};
use crate::messages::{ClientMessage, ServerMessage};

/// WebSocket connection handler trait.
pub trait ConnectionHandler: Send + Sync {
    /// Handle new connection.
    fn on_connect(&self, conn_id: ConnectionId) -> Result<()>;

    /// Handle message from client.
    fn on_message(&self, conn_id: ConnectionId, message: ClientMessage) -> Result<()>;

    /// Handle connection close.
    fn on_disconnect(&self, conn_id: ConnectionId);

    /// Send message to connection.
    fn send(&self, conn_id: ConnectionId, message: ServerMessage) -> Result<()>;

    /// Broadcast to all subscribers of a match.
    fn broadcast_to_match(&self, match_id: MatchId, message: ServerMessage) -> Result<()>;
}

// TODO: Implement WebSocket handler with axum
