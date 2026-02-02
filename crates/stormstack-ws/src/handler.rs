//! WebSocket connection handler.

use crate::connection::{ConnectionManager, SharedConnectionManager};
use crate::messages::{ClientMessage, ServerMessage};
use crate::subscription::{shared_subscriptions, SharedSubscriptionManager};
use std::sync::Arc;
use stormstack_core::{ConnectionId, MatchId, Result};
use tracing::{debug, error, info, warn};

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

/// Match state provider trait.
///
/// Implementations provide match state (snapshots) when clients subscribe.
pub trait MatchStateProvider: Send + Sync {
    /// Get current snapshot for a match.
    fn get_snapshot(&self, match_id: MatchId) -> Result<stormstack_core::WorldSnapshot>;

    /// Check if match exists.
    fn match_exists(&self, match_id: MatchId) -> bool;
}

/// Default WebSocket handler implementation.
pub struct WsHandler<M: MatchStateProvider> {
    /// Connection manager.
    connections: SharedConnectionManager,
    /// Subscription manager.
    subscriptions: SharedSubscriptionManager,
    /// Match state provider.
    match_provider: Arc<M>,
}

impl<M: MatchStateProvider + 'static> WsHandler<M> {
    /// Create a new WebSocket handler.
    pub fn new(match_provider: Arc<M>) -> Self {
        let subscriptions = shared_subscriptions();
        let connections = Arc::new(ConnectionManager::new(subscriptions.clone()));
        Self {
            connections,
            subscriptions,
            match_provider,
        }
    }

    /// Get the connection manager.
    pub fn connection_manager(&self) -> &SharedConnectionManager {
        &self.connections
    }

    /// Get the subscription manager.
    pub fn subscription_manager(&self) -> &SharedSubscriptionManager {
        &self.subscriptions
    }

    /// Handle subscribe message.
    fn handle_subscribe(&self, conn_id: ConnectionId, match_id: MatchId) -> Result<()> {
        // Check if match exists
        if !self.match_provider.match_exists(match_id) {
            self.send(
                conn_id,
                ServerMessage::Error {
                    code: "MATCH_NOT_FOUND".to_string(),
                    message: format!("Match {:?} not found", match_id),
                },
            )?;
            return Ok(());
        }

        // Subscribe
        self.connections.subscribe(conn_id, match_id)?;

        // Send initial snapshot
        match self.match_provider.get_snapshot(match_id) {
            Ok(snapshot) => {
                self.send(conn_id, ServerMessage::Snapshot { match_id, snapshot })?;
                info!("Connection {:?} subscribed to match {:?}", conn_id, match_id);
            }
            Err(e) => {
                error!("Failed to get snapshot for match {:?}: {}", match_id, e);
                self.send(
                    conn_id,
                    ServerMessage::Error {
                        code: "SNAPSHOT_FAILED".to_string(),
                        message: "Failed to get match state".to_string(),
                    },
                )?;
            }
        }

        Ok(())
    }

    /// Handle unsubscribe message.
    fn handle_unsubscribe(&self, conn_id: ConnectionId, match_id: MatchId) -> Result<()> {
        self.connections.unsubscribe(conn_id, match_id);
        info!(
            "Connection {:?} unsubscribed from match {:?}",
            conn_id, match_id
        );
        Ok(())
    }

    /// Handle ping message.
    fn handle_ping(&self, conn_id: ConnectionId, timestamp: i64) -> Result<()> {
        let server_time = chrono::Utc::now().timestamp_millis();
        self.send(
            conn_id,
            ServerMessage::Pong {
                timestamp,
                server_time,
            },
        )
    }
}

impl<M: MatchStateProvider + 'static> ConnectionHandler for WsHandler<M> {
    fn on_connect(&self, conn_id: ConnectionId) -> Result<()> {
        debug!("New connection: {:?}", conn_id);
        Ok(())
    }

    fn on_message(&self, conn_id: ConnectionId, message: ClientMessage) -> Result<()> {
        match message {
            ClientMessage::Subscribe { match_id } => self.handle_subscribe(conn_id, match_id),
            ClientMessage::Unsubscribe { match_id } => self.handle_unsubscribe(conn_id, match_id),
            ClientMessage::Ping { timestamp } => self.handle_ping(conn_id, timestamp),
            ClientMessage::Command {
                match_id,
                command: _,
            } => {
                // Commands are handled by the game server
                // For now, just acknowledge
                warn!(
                    "Command handling not implemented for match {:?}",
                    match_id
                );
                Ok(())
            }
        }
    }

    fn on_disconnect(&self, conn_id: ConnectionId) {
        self.connections.remove_connection(conn_id);
        debug!("Connection disconnected: {:?}", conn_id);
    }

    fn send(&self, conn_id: ConnectionId, message: ServerMessage) -> Result<()> {
        self.connections.send(conn_id, message)
    }

    fn broadcast_to_match(&self, match_id: MatchId, message: ServerMessage) -> Result<()> {
        self.connections.broadcast_to_match(match_id, message)
    }
}

/// Null match provider for testing.
#[cfg(test)]
pub struct NullMatchProvider;

#[cfg(test)]
impl MatchStateProvider for NullMatchProvider {
    fn get_snapshot(&self, _match_id: MatchId) -> Result<stormstack_core::WorldSnapshot> {
        Ok(stormstack_core::WorldSnapshot::new(0))
    }

    fn match_exists(&self, _match_id: MatchId) -> bool {
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::connection::ConnectionState;
    use tokio::sync::mpsc;

    fn create_handler() -> WsHandler<NullMatchProvider> {
        WsHandler::new(Arc::new(NullMatchProvider))
    }

    fn add_test_connection(
        handler: &WsHandler<NullMatchProvider>,
    ) -> (ConnectionId, mpsc::UnboundedReceiver<ServerMessage>) {
        let (tx, rx) = mpsc::unbounded_channel();
        let state = ConnectionState::new(ConnectionId::new(), tx);
        let id = state.id;
        handler.connections.add_connection(state);
        (id, rx)
    }

    #[test]
    fn handler_creation() {
        let handler = create_handler();
        assert_eq!(handler.connections.connection_count(), 0);
    }

    #[test]
    fn subscribe_sends_snapshot() {
        let handler = create_handler();
        let (conn_id, mut rx) = add_test_connection(&handler);
        let match_id = MatchId::new();

        handler
            .on_message(conn_id, ClientMessage::Subscribe { match_id })
            .expect("subscribe");

        let msg = rx.try_recv().expect("receive snapshot");
        match msg {
            ServerMessage::Snapshot {
                match_id: recv_match,
                snapshot,
            } => {
                assert_eq!(recv_match, match_id);
                assert_eq!(snapshot.tick, 0);
            }
            _ => panic!("expected snapshot"),
        }
    }

    #[test]
    fn ping_responds_with_pong() {
        let handler = create_handler();
        let (conn_id, mut rx) = add_test_connection(&handler);

        handler
            .on_message(conn_id, ClientMessage::Ping { timestamp: 12345 })
            .expect("ping");

        let msg = rx.try_recv().expect("receive pong");
        match msg {
            ServerMessage::Pong {
                timestamp,
                server_time,
            } => {
                assert_eq!(timestamp, 12345);
                assert!(server_time > 0);
            }
            _ => panic!("expected pong"),
        }
    }

    #[test]
    fn disconnect_removes_subscriptions() {
        let handler = create_handler();
        let (conn_id, _rx) = add_test_connection(&handler);
        let match_id = MatchId::new();

        handler
            .on_message(conn_id, ClientMessage::Subscribe { match_id })
            .expect("subscribe");
        assert!(handler.subscriptions.is_subscribed(conn_id, match_id));

        handler.on_disconnect(conn_id);
        assert!(!handler.subscriptions.is_subscribed(conn_id, match_id));
    }
}
