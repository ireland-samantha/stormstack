//! Subscription management for WebSocket connections.

use dashmap::{DashMap, DashSet};
use std::sync::Arc;
use stormstack_core::{ConnectionId, MatchId};
use tracing::{debug, trace};

/// Manages subscriptions between connections and matches.
///
/// Thread-safe subscription tracker using DashMap for concurrent access.
#[derive(Debug, Default)]
pub struct SubscriptionManager {
    /// Map of match ID to set of subscribed connection IDs.
    match_subscribers: DashMap<MatchId, DashSet<ConnectionId>>,
    /// Map of connection ID to set of subscribed match IDs.
    connection_matches: DashMap<ConnectionId, DashSet<MatchId>>,
}

impl SubscriptionManager {
    /// Create a new subscription manager.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Subscribe a connection to a match.
    pub fn subscribe(&self, conn_id: ConnectionId, match_id: MatchId) {
        // Add to match -> connections map
        self.match_subscribers
            .entry(match_id)
            .or_default()
            .insert(conn_id);

        // Add to connection -> matches map
        self.connection_matches
            .entry(conn_id)
            .or_default()
            .insert(match_id);

        debug!("Connection {:?} subscribed to match {:?}", conn_id, match_id);
    }

    /// Unsubscribe a connection from a match.
    pub fn unsubscribe(&self, conn_id: ConnectionId, match_id: MatchId) {
        // Remove from match -> connections map
        if let Some(subscribers) = self.match_subscribers.get(&match_id) {
            subscribers.remove(&conn_id);
        }

        // Remove from connection -> matches map
        if let Some(matches) = self.connection_matches.get(&conn_id) {
            matches.remove(&match_id);
        }

        debug!(
            "Connection {:?} unsubscribed from match {:?}",
            conn_id, match_id
        );
    }

    /// Remove all subscriptions for a connection.
    pub fn remove_connection(&self, conn_id: ConnectionId) {
        // Get all matches this connection was subscribed to
        if let Some((_, matches)) = self.connection_matches.remove(&conn_id) {
            for match_id in matches.iter() {
                if let Some(subscribers) = self.match_subscribers.get(&*match_id) {
                    subscribers.remove(&conn_id);
                }
            }
        }

        trace!("Removed all subscriptions for connection {:?}", conn_id);
    }

    /// Get all connections subscribed to a match.
    pub fn get_match_subscribers(&self, match_id: MatchId) -> Vec<ConnectionId> {
        self.match_subscribers
            .get(&match_id)
            .map(|set| set.iter().map(|c| *c).collect())
            .unwrap_or_default()
    }

    /// Get all matches a connection is subscribed to.
    pub fn get_connection_subscriptions(&self, conn_id: ConnectionId) -> Vec<MatchId> {
        self.connection_matches
            .get(&conn_id)
            .map(|set| set.iter().map(|m| *m).collect())
            .unwrap_or_default()
    }

    /// Check if a connection is subscribed to a match.
    #[must_use]
    pub fn is_subscribed(&self, conn_id: ConnectionId, match_id: MatchId) -> bool {
        self.connection_matches
            .get(&conn_id)
            .is_some_and(|matches| matches.contains(&match_id))
    }

    /// Get the number of subscribers for a match.
    #[must_use]
    pub fn subscriber_count(&self, match_id: MatchId) -> usize {
        self.match_subscribers
            .get(&match_id)
            .map(|s| s.len())
            .unwrap_or(0)
    }

    /// Get the total number of active subscriptions.
    #[must_use]
    pub fn total_subscriptions(&self) -> usize {
        self.connection_matches
            .iter()
            .map(|entry| entry.value().len())
            .sum()
    }
}

/// Thread-safe wrapper for subscription manager.
pub type SharedSubscriptionManager = Arc<SubscriptionManager>;

/// Create a new shared subscription manager.
#[must_use]
pub fn shared_subscriptions() -> SharedSubscriptionManager {
    Arc::new(SubscriptionManager::new())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subscribe_and_unsubscribe() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match_id = MatchId::new();

        manager.subscribe(conn, match_id);
        assert!(manager.is_subscribed(conn, match_id));
        assert_eq!(manager.subscriber_count(match_id), 1);

        manager.unsubscribe(conn, match_id);
        assert!(!manager.is_subscribed(conn, match_id));
        assert_eq!(manager.subscriber_count(match_id), 0);
    }

    #[test]
    fn multiple_subscribers() {
        let manager = SubscriptionManager::new();
        let conn1 = ConnectionId::new();
        let conn2 = ConnectionId::new();
        let match_id = MatchId::new();

        manager.subscribe(conn1, match_id);
        manager.subscribe(conn2, match_id);

        assert_eq!(manager.subscriber_count(match_id), 2);

        let subscribers = manager.get_match_subscribers(match_id);
        assert!(subscribers.contains(&conn1));
        assert!(subscribers.contains(&conn2));
    }

    #[test]
    fn remove_connection_clears_all_subscriptions() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match1 = MatchId::new();
        let match2 = MatchId::new();

        manager.subscribe(conn, match1);
        manager.subscribe(conn, match2);
        assert_eq!(manager.total_subscriptions(), 2);

        manager.remove_connection(conn);

        assert!(!manager.is_subscribed(conn, match1));
        assert!(!manager.is_subscribed(conn, match2));
        assert_eq!(manager.subscriber_count(match1), 0);
        assert_eq!(manager.subscriber_count(match2), 0);
    }

    #[test]
    fn get_connection_subscriptions() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match1 = MatchId::new();
        let match2 = MatchId::new();

        manager.subscribe(conn, match1);
        manager.subscribe(conn, match2);

        let subs = manager.get_connection_subscriptions(conn);
        assert_eq!(subs.len(), 2);
        assert!(subs.contains(&match1));
        assert!(subs.contains(&match2));
    }

    // ===== Edge case tests =====

    #[test]
    fn double_subscribe_is_idempotent() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match_id = MatchId::new();

        manager.subscribe(conn, match_id);
        manager.subscribe(conn, match_id);

        // Should still only count as 1 subscription
        assert_eq!(manager.subscriber_count(match_id), 1);
        assert_eq!(manager.total_subscriptions(), 1);
    }

    #[test]
    fn unsubscribe_nonexistent_is_safe() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match_id = MatchId::new();

        // Unsubscribe from a match we never subscribed to - should not panic
        manager.unsubscribe(conn, match_id);

        assert!(!manager.is_subscribed(conn, match_id));
    }

    #[test]
    fn remove_nonexistent_connection_is_safe() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();

        // Remove a connection that was never added - should not panic
        manager.remove_connection(conn);
    }

    #[test]
    fn get_subscribers_empty_match_returns_empty() {
        let manager = SubscriptionManager::new();
        let match_id = MatchId::new();

        let subscribers = manager.get_match_subscribers(match_id);
        assert!(subscribers.is_empty());
    }

    #[test]
    fn get_subscriptions_unknown_connection_returns_empty() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();

        let subs = manager.get_connection_subscriptions(conn);
        assert!(subs.is_empty());
    }

    #[test]
    fn subscriber_count_unknown_match_returns_zero() {
        let manager = SubscriptionManager::new();
        let match_id = MatchId::new();

        assert_eq!(manager.subscriber_count(match_id), 0);
    }

    #[test]
    fn total_subscriptions_empty_returns_zero() {
        let manager = SubscriptionManager::new();
        assert_eq!(manager.total_subscriptions(), 0);
    }

    #[test]
    fn is_subscribed_unknown_connection_returns_false() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match_id = MatchId::new();

        assert!(!manager.is_subscribed(conn, match_id));
    }

    #[test]
    fn many_connections_one_match() {
        let manager = SubscriptionManager::new();
        let match_id = MatchId::new();

        // Subscribe 100 connections to the same match
        let mut conns = Vec::new();
        for _ in 0..100 {
            let conn = ConnectionId::new();
            manager.subscribe(conn, match_id);
            conns.push(conn);
        }

        assert_eq!(manager.subscriber_count(match_id), 100);

        // Verify all are subscribed
        for conn in &conns {
            assert!(manager.is_subscribed(*conn, match_id));
        }
    }

    #[test]
    fn one_connection_many_matches() {
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();

        // Subscribe one connection to 100 matches
        let mut matches = Vec::new();
        for _ in 0..100 {
            let match_id = MatchId::new();
            manager.subscribe(conn, match_id);
            matches.push(match_id);
        }

        assert_eq!(manager.total_subscriptions(), 100);

        let subs = manager.get_connection_subscriptions(conn);
        assert_eq!(subs.len(), 100);

        // Verify all subscriptions
        for match_id in &matches {
            assert!(manager.is_subscribed(conn, *match_id));
        }
    }

    #[test]
    fn unsubscribe_preserves_other_subscriptions() {
        let manager = SubscriptionManager::new();
        let conn1 = ConnectionId::new();
        let conn2 = ConnectionId::new();
        let match_id = MatchId::new();

        manager.subscribe(conn1, match_id);
        manager.subscribe(conn2, match_id);

        // Unsubscribe conn1 only
        manager.unsubscribe(conn1, match_id);

        assert!(!manager.is_subscribed(conn1, match_id));
        assert!(manager.is_subscribed(conn2, match_id));
        assert_eq!(manager.subscriber_count(match_id), 1);
    }

    #[test]
    fn remove_connection_preserves_other_connections() {
        let manager = SubscriptionManager::new();
        let conn1 = ConnectionId::new();
        let conn2 = ConnectionId::new();
        let match_id = MatchId::new();

        manager.subscribe(conn1, match_id);
        manager.subscribe(conn2, match_id);

        // Remove conn1
        manager.remove_connection(conn1);

        assert!(!manager.is_subscribed(conn1, match_id));
        assert!(manager.is_subscribed(conn2, match_id));
        assert_eq!(manager.subscriber_count(match_id), 1);
    }

    #[test]
    fn unsubscribe_one_match_preserves_other_match_subscriptions() {
        // Test that a single connection unsubscribing from one match
        // still remains subscribed to other matches
        let manager = SubscriptionManager::new();
        let conn = ConnectionId::new();
        let match1 = MatchId::new();
        let match2 = MatchId::new();
        let match3 = MatchId::new();

        // Subscribe to all three matches
        manager.subscribe(conn, match1);
        manager.subscribe(conn, match2);
        manager.subscribe(conn, match3);

        assert_eq!(manager.total_subscriptions(), 3);

        // Unsubscribe from match2 only
        manager.unsubscribe(conn, match2);

        // Should still be subscribed to match1 and match3
        assert!(manager.is_subscribed(conn, match1));
        assert!(!manager.is_subscribed(conn, match2));
        assert!(manager.is_subscribed(conn, match3));

        // Verify counts
        assert_eq!(manager.total_subscriptions(), 2);
        assert_eq!(manager.subscriber_count(match1), 1);
        assert_eq!(manager.subscriber_count(match2), 0);
        assert_eq!(manager.subscriber_count(match3), 1);

        // Verify get_connection_subscriptions returns correct matches
        let subs = manager.get_connection_subscriptions(conn);
        assert_eq!(subs.len(), 2);
        assert!(subs.contains(&match1));
        assert!(!subs.contains(&match2));
        assert!(subs.contains(&match3));
    }
}
