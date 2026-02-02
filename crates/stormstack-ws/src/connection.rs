//! WebSocket connection management.

use crate::messages::ServerMessage;
use crate::subscription::SharedSubscriptionManager;
use dashmap::DashMap;
use parking_lot::Mutex;
use std::sync::Arc;
use stormstack_core::{ConnectionId, MatchId, Result, StormError};
use tokio::sync::mpsc;
use tracing::{debug, trace};

/// Type alias for outbound message sender.
pub type MessageSender = mpsc::UnboundedSender<ServerMessage>;

/// Connection state.
#[derive(Debug)]
pub struct ConnectionState {
    /// Connection identifier.
    pub id: ConnectionId,
    /// User ID if authenticated.
    pub user_id: Option<stormstack_core::UserId>,
    /// Tenant ID if authenticated.
    pub tenant_id: Option<stormstack_core::TenantId>,
    /// Outbound message channel.
    sender: MessageSender,
}

impl ConnectionState {
    /// Create new connection state.
    pub fn new(id: ConnectionId, sender: MessageSender) -> Self {
        Self {
            id,
            user_id: None,
            tenant_id: None,
            sender,
        }
    }

    /// Set authenticated user info.
    pub fn set_auth(
        &mut self,
        user_id: stormstack_core::UserId,
        tenant_id: stormstack_core::TenantId,
    ) {
        self.user_id = Some(user_id);
        self.tenant_id = Some(tenant_id);
    }

    /// Check if connection is authenticated.
    #[must_use]
    pub fn is_authenticated(&self) -> bool {
        self.user_id.is_some()
    }

    /// Send message to this connection.
    pub fn send(&self, message: ServerMessage) -> Result<()> {
        self.sender
            .send(message)
            .map_err(|_| StormError::ConnectionClosed(self.id))
    }
}

/// Manages active WebSocket connections.
#[derive(Debug)]
pub struct ConnectionManager {
    /// Active connections by ID.
    connections: DashMap<ConnectionId, Arc<Mutex<ConnectionState>>>,
    /// Subscription manager.
    subscriptions: SharedSubscriptionManager,
}

impl ConnectionManager {
    /// Create a new connection manager.
    #[must_use]
    pub fn new(subscriptions: SharedSubscriptionManager) -> Self {
        Self {
            connections: DashMap::new(),
            subscriptions,
        }
    }

    /// Register a new connection.
    pub fn add_connection(&self, state: ConnectionState) -> ConnectionId {
        let id = state.id;
        self.connections.insert(id, Arc::new(Mutex::new(state)));
        debug!("Added connection {:?}", id);
        id
    }

    /// Remove a connection.
    pub fn remove_connection(&self, id: ConnectionId) {
        self.connections.remove(&id);
        self.subscriptions.remove_connection(id);
        debug!("Removed connection {:?}", id);
    }

    /// Get connection state.
    pub fn get_connection(&self, id: ConnectionId) -> Option<Arc<Mutex<ConnectionState>>> {
        self.connections.get(&id).map(|c| c.clone())
    }

    /// Send message to a specific connection.
    pub fn send(&self, conn_id: ConnectionId, message: ServerMessage) -> Result<()> {
        let conn = self
            .connections
            .get(&conn_id)
            .ok_or_else(|| StormError::ConnectionNotFound(conn_id))?;

        conn.lock().send(message)
    }

    /// Broadcast message to all subscribers of a match.
    pub fn broadcast_to_match(&self, match_id: MatchId, message: ServerMessage) -> Result<()> {
        let subscribers = self.subscriptions.get_match_subscribers(match_id);
        let mut send_count = 0;
        let mut error_count = 0;

        for conn_id in subscribers {
            if let Some(conn) = self.connections.get(&conn_id) {
                if conn.lock().send(message.clone()).is_ok() {
                    send_count += 1;
                } else {
                    error_count += 1;
                }
            }
        }

        trace!(
            "Broadcast to match {:?}: {} sent, {} errors",
            match_id,
            send_count,
            error_count
        );
        Ok(())
    }

    /// Subscribe connection to match.
    pub fn subscribe(&self, conn_id: ConnectionId, match_id: MatchId) -> Result<()> {
        if !self.connections.contains_key(&conn_id) {
            return Err(StormError::ConnectionNotFound(conn_id));
        }
        self.subscriptions.subscribe(conn_id, match_id);
        Ok(())
    }

    /// Unsubscribe connection from match.
    pub fn unsubscribe(&self, conn_id: ConnectionId, match_id: MatchId) {
        self.subscriptions.unsubscribe(conn_id, match_id);
    }

    /// Get count of active connections.
    #[must_use]
    pub fn connection_count(&self) -> usize {
        self.connections.len()
    }

    /// Check if connection exists.
    #[must_use]
    pub fn has_connection(&self, id: ConnectionId) -> bool {
        self.connections.contains_key(&id)
    }
}

/// Thread-safe wrapper for connection manager.
pub type SharedConnectionManager = Arc<ConnectionManager>;

/// Create a new shared connection manager.
#[must_use]
pub fn shared_connection_manager(subscriptions: SharedSubscriptionManager) -> SharedConnectionManager {
    Arc::new(ConnectionManager::new(subscriptions))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::subscription::shared_subscriptions;
    use tokio::sync::mpsc;

    fn create_test_connection() -> (ConnectionState, mpsc::UnboundedReceiver<ServerMessage>) {
        let (tx, rx) = mpsc::unbounded_channel();
        let state = ConnectionState::new(ConnectionId::new(), tx);
        (state, rx)
    }

    #[test]
    fn add_and_remove_connection() {
        let subscriptions = shared_subscriptions();
        let manager = ConnectionManager::new(subscriptions);

        let (state, _rx) = create_test_connection();
        let id = manager.add_connection(state);

        assert!(manager.has_connection(id));
        assert_eq!(manager.connection_count(), 1);

        manager.remove_connection(id);
        assert!(!manager.has_connection(id));
        assert_eq!(manager.connection_count(), 0);
    }

    #[tokio::test]
    async fn send_to_connection() {
        let subscriptions = shared_subscriptions();
        let manager = ConnectionManager::new(subscriptions);

        let (state, mut rx) = create_test_connection();
        let id = state.id;
        manager.add_connection(state);

        let message = ServerMessage::Pong {
            timestamp: 1000,
            server_time: 2000,
        };

        manager.send(id, message).expect("send");

        let received = rx.recv().await.expect("receive");
        match received {
            ServerMessage::Pong {
                timestamp,
                server_time,
            } => {
                assert_eq!(timestamp, 1000);
                assert_eq!(server_time, 2000);
            }
            _ => panic!("unexpected message type"),
        }
    }

    #[test]
    fn subscribe_and_broadcast() {
        let subscriptions = shared_subscriptions();
        let manager = ConnectionManager::new(subscriptions);

        let (state1, mut rx1) = create_test_connection();
        let id1 = state1.id;
        manager.add_connection(state1);

        let (state2, mut rx2) = create_test_connection();
        let id2 = state2.id;
        manager.add_connection(state2);

        let match_id = MatchId::new();
        manager.subscribe(id1, match_id).expect("subscribe");
        manager.subscribe(id2, match_id).expect("subscribe");

        let message = ServerMessage::Error {
            code: "TEST".to_string(),
            message: "test".to_string(),
        };
        manager.broadcast_to_match(match_id, message).expect("broadcast");

        // Both should receive
        assert!(rx1.try_recv().is_ok());
        assert!(rx2.try_recv().is_ok());
    }

    #[test]
    fn connection_authentication() {
        let (mut state, _rx) = create_test_connection();
        assert!(!state.is_authenticated());

        state.set_auth(stormstack_core::UserId::new(), stormstack_core::TenantId::new());
        assert!(state.is_authenticated());
    }
}
