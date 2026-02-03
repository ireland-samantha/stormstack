//! Player session management.
//!
//! This module provides session tracking for players connected to matches.
//! Sessions track connection state, activity timestamps, and enable
//! features like inactive session expiration.

use chrono::{DateTime, Utc};
use dashmap::DashMap;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::sync::Arc;
use std::time::Duration;
use stormstack_core::{ContainerId, MatchId, SessionId, UserId};
use tracing::{debug, trace};

/// Player session state.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SessionState {
    /// Session is active and connected.
    Active,
    /// Session is disconnected but not yet expired.
    Disconnected,
    /// Session has expired due to inactivity.
    Expired,
}

/// A player session representing a connection to a match.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlayerSession {
    /// Unique session identifier.
    pub id: SessionId,
    /// User who owns this session.
    pub user_id: UserId,
    /// Match this session is connected to.
    pub match_id: MatchId,
    /// Container hosting the match.
    pub container_id: ContainerId,
    /// When the session was created.
    pub connected_at: DateTime<Utc>,
    /// Last activity timestamp.
    pub last_activity: DateTime<Utc>,
    /// Current session state.
    pub state: SessionState,
}

impl PlayerSession {
    /// Create a new active session.
    #[must_use]
    pub fn new(
        user_id: UserId,
        match_id: MatchId,
        container_id: ContainerId,
    ) -> Self {
        let now = Utc::now();
        Self {
            id: SessionId::new(),
            user_id,
            match_id,
            container_id,
            connected_at: now,
            last_activity: now,
            state: SessionState::Active,
        }
    }

    /// Check if the session is active.
    #[must_use]
    pub fn is_active(&self) -> bool {
        self.state == SessionState::Active
    }

    /// Check if the session is disconnected.
    #[must_use]
    pub fn is_disconnected(&self) -> bool {
        self.state == SessionState::Disconnected
    }

    /// Check if the session has expired.
    #[must_use]
    pub fn is_expired(&self) -> bool {
        self.state == SessionState::Expired
    }

    /// Get the duration since last activity.
    #[must_use]
    pub fn time_since_activity(&self) -> chrono::Duration {
        Utc::now() - self.last_activity
    }
}

/// Service for managing player sessions.
///
/// Provides thread-safe operations for:
/// - Creating and retrieving sessions
/// - Tracking sessions by user and match
/// - Updating activity timestamps
/// - Expiring inactive sessions
#[derive(Debug)]
pub struct SessionService {
    /// Sessions indexed by session ID.
    sessions: DashMap<SessionId, PlayerSession>,
    /// Session IDs indexed by user ID for fast lookup.
    user_sessions: DashMap<UserId, HashSet<SessionId>>,
    /// Session IDs indexed by match ID for fast lookup.
    match_sessions: DashMap<MatchId, HashSet<SessionId>>,
    /// Session IDs indexed by container ID for fast lookup.
    container_sessions: DashMap<ContainerId, HashSet<SessionId>>,
}

impl SessionService {
    /// Create a new session service.
    #[must_use]
    pub fn new() -> Self {
        debug!("Creating SessionService");
        Self {
            sessions: DashMap::new(),
            user_sessions: DashMap::new(),
            match_sessions: DashMap::new(),
            container_sessions: DashMap::new(),
        }
    }

    /// Create a new session for a user joining a match.
    ///
    /// Returns the session ID for the newly created session.
    pub fn create(
        &self,
        user_id: UserId,
        match_id: MatchId,
        container_id: ContainerId,
    ) -> SessionId {
        let session = PlayerSession::new(user_id, match_id, container_id);
        let session_id = session.id;

        // Add to main sessions map
        self.sessions.insert(session_id, session);

        // Add to user index
        self.user_sessions
            .entry(user_id)
            .or_default()
            .insert(session_id);

        // Add to match index
        self.match_sessions
            .entry(match_id)
            .or_default()
            .insert(session_id);

        // Add to container index
        self.container_sessions
            .entry(container_id)
            .or_default()
            .insert(session_id);

        debug!(
            "Created session {:?} for user {:?} in match {:?}",
            session_id, user_id, match_id
        );

        session_id
    }

    /// Get a session by ID.
    #[must_use]
    pub fn get(&self, session_id: SessionId) -> Option<PlayerSession> {
        self.sessions.get(&session_id).map(|r| r.clone())
    }

    /// Get all sessions for a user.
    #[must_use]
    pub fn get_by_user(&self, user_id: UserId) -> Vec<PlayerSession> {
        self.user_sessions
            .get(&user_id)
            .map(|session_ids| {
                session_ids
                    .iter()
                    .filter_map(|id| self.sessions.get(id).map(|r| r.clone()))
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Get all sessions for a match.
    #[must_use]
    pub fn get_by_match(&self, match_id: MatchId) -> Vec<PlayerSession> {
        self.match_sessions
            .get(&match_id)
            .map(|session_ids| {
                session_ids
                    .iter()
                    .filter_map(|id| self.sessions.get(id).map(|r| r.clone()))
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Get all sessions for a container.
    #[must_use]
    pub fn get_by_container(&self, container_id: ContainerId) -> Vec<PlayerSession> {
        self.container_sessions
            .get(&container_id)
            .map(|session_ids| {
                session_ids
                    .iter()
                    .filter_map(|id| self.sessions.get(id).map(|r| r.clone()))
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Update the last activity timestamp for a session.
    ///
    /// Returns true if the session was found and updated.
    pub fn update_activity(&self, session_id: SessionId) -> bool {
        if let Some(mut session) = self.sessions.get_mut(&session_id) {
            session.last_activity = Utc::now();
            trace!("Updated activity for session {:?}", session_id);
            true
        } else {
            false
        }
    }

    /// Mark a session as disconnected.
    ///
    /// # Errors
    ///
    /// Returns an error if the session is not found.
    pub fn disconnect(&self, session_id: SessionId) -> Result<(), SessionError> {
        if let Some(mut session) = self.sessions.get_mut(&session_id) {
            session.state = SessionState::Disconnected;
            session.last_activity = Utc::now();
            debug!("Session {:?} disconnected", session_id);
            Ok(())
        } else {
            Err(SessionError::NotFound(session_id))
        }
    }

    /// Expire sessions that have been inactive for longer than the timeout.
    ///
    /// Returns the IDs of sessions that were expired.
    pub fn expire_inactive(&self, timeout: Duration) -> Vec<SessionId> {
        let timeout_chrono = chrono::Duration::from_std(timeout)
            .unwrap_or_else(|_| chrono::Duration::seconds(300));
        let now = Utc::now();
        let mut expired = Vec::new();

        for mut entry in self.sessions.iter_mut() {
            let session = entry.value_mut();
            if session.state != SessionState::Expired {
                let inactive_duration = now - session.last_activity;
                if inactive_duration > timeout_chrono {
                    session.state = SessionState::Expired;
                    expired.push(session.id);
                    debug!(
                        "Session {:?} expired after {:?} of inactivity",
                        session.id, inactive_duration
                    );
                }
            }
        }

        expired
    }

    /// Remove a session completely.
    ///
    /// This removes the session from all indexes.
    pub fn remove(&self, session_id: SessionId) {
        if let Some((_, session)) = self.sessions.remove(&session_id) {
            // Remove from user index
            if let Some(mut user_sessions) = self.user_sessions.get_mut(&session.user_id) {
                user_sessions.remove(&session_id);
            }

            // Remove from match index
            if let Some(mut match_sessions) = self.match_sessions.get_mut(&session.match_id) {
                match_sessions.remove(&session_id);
            }

            // Remove from container index
            if let Some(mut container_sessions) = self.container_sessions.get_mut(&session.container_id) {
                container_sessions.remove(&session_id);
            }

            debug!("Removed session {:?}", session_id);
        }
    }

    /// Get the total number of sessions.
    #[must_use]
    pub fn session_count(&self) -> usize {
        self.sessions.len()
    }

    /// Get the number of active sessions.
    #[must_use]
    pub fn active_session_count(&self) -> usize {
        self.sessions
            .iter()
            .filter(|r| r.state == SessionState::Active)
            .count()
    }

    /// Check if a session exists.
    #[must_use]
    pub fn has_session(&self, session_id: SessionId) -> bool {
        self.sessions.contains_key(&session_id)
    }

    /// Get all sessions (for administrative purposes).
    #[must_use]
    pub fn all_sessions(&self) -> Vec<PlayerSession> {
        self.sessions.iter().map(|r| r.clone()).collect()
    }
}

impl Default for SessionService {
    fn default() -> Self {
        Self::new()
    }
}

/// Thread-safe shared session service.
pub type SharedSessionService = Arc<SessionService>;

/// Create a new shared session service.
#[must_use]
pub fn shared_session_service() -> SharedSessionService {
    Arc::new(SessionService::new())
}

/// Session-related errors.
#[derive(Debug, Clone, thiserror::Error)]
pub enum SessionError {
    /// Session not found.
    #[error("Session not found: {0}")]
    NotFound(SessionId),
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_session() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);

        assert!(service.has_session(session_id));
        assert_eq!(service.session_count(), 1);
    }

    #[test]
    fn get_session_by_id() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);

        let session = service.get(session_id);
        assert!(session.is_some());
        let session = session.unwrap();
        assert_eq!(session.id, session_id);
        assert_eq!(session.user_id, user_id);
        assert_eq!(session.match_id, match_id);
        assert_eq!(session.container_id, container_id);
        assert_eq!(session.state, SessionState::Active);
    }

    #[test]
    fn get_sessions_by_user() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match1 = MatchId::new();
        let match2 = MatchId::new();
        let container_id = ContainerId::new();

        let s1 = service.create(user_id, match1, container_id);
        let s2 = service.create(user_id, match2, container_id);

        // Different user
        let other_user = UserId::new();
        service.create(other_user, match1, container_id);

        let user_sessions = service.get_by_user(user_id);
        assert_eq!(user_sessions.len(), 2);

        let ids: Vec<SessionId> = user_sessions.iter().map(|s| s.id).collect();
        assert!(ids.contains(&s1));
        assert!(ids.contains(&s2));
    }

    #[test]
    fn get_sessions_by_match() {
        let service = SessionService::new();
        let user1 = UserId::new();
        let user2 = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let s1 = service.create(user1, match_id, container_id);
        let s2 = service.create(user2, match_id, container_id);

        // Different match
        let other_match = MatchId::new();
        service.create(user1, other_match, container_id);

        let match_sessions = service.get_by_match(match_id);
        assert_eq!(match_sessions.len(), 2);

        let ids: Vec<SessionId> = match_sessions.iter().map(|s| s.id).collect();
        assert!(ids.contains(&s1));
        assert!(ids.contains(&s2));
    }

    #[test]
    fn update_activity_extends_session() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);

        let initial_activity = service.get(session_id).unwrap().last_activity;

        // Small delay to ensure time difference
        std::thread::sleep(std::time::Duration::from_millis(10));

        let updated = service.update_activity(session_id);
        assert!(updated);

        let new_activity = service.get(session_id).unwrap().last_activity;
        assert!(new_activity >= initial_activity);
    }

    #[test]
    fn disconnect_marks_state() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);

        // Initially active
        assert_eq!(service.get(session_id).unwrap().state, SessionState::Active);

        // Disconnect
        let result = service.disconnect(session_id);
        assert!(result.is_ok());

        // Now disconnected
        let session = service.get(session_id).unwrap();
        assert_eq!(session.state, SessionState::Disconnected);
        assert!(session.is_disconnected());
    }

    #[test]
    fn expire_inactive_sessions() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);

        // With a very short timeout, the session should expire immediately
        let expired = service.expire_inactive(Duration::from_nanos(1));

        // Session should be in the expired list
        assert!(expired.contains(&session_id));

        // Session state should be Expired
        let session = service.get(session_id).unwrap();
        assert_eq!(session.state, SessionState::Expired);
        assert!(session.is_expired());
    }

    #[test]
    fn remove_session() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let session_id = service.create(user_id, match_id, container_id);
        assert!(service.has_session(session_id));

        service.remove(session_id);

        assert!(!service.has_session(session_id));
        assert_eq!(service.session_count(), 0);

        // Should also be removed from indexes
        assert!(service.get_by_user(user_id).is_empty());
        assert!(service.get_by_match(match_id).is_empty());
        assert!(service.get_by_container(container_id).is_empty());
    }

    #[test]
    fn session_lifecycle_with_match() {
        let service = SessionService::new();
        let user_id = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        // Player joins match - create session
        let session_id = service.create(user_id, match_id, container_id);
        assert!(service.get(session_id).unwrap().is_active());

        // Player sends activity
        service.update_activity(session_id);

        // Player disconnects
        service.disconnect(session_id).unwrap();
        assert!(service.get(session_id).unwrap().is_disconnected());

        // Player leaves match - remove session
        service.remove(session_id);
        assert!(!service.has_session(session_id));
    }

    #[test]
    fn get_sessions_by_container() {
        let service = SessionService::new();
        let user1 = UserId::new();
        let user2 = UserId::new();
        let match_id = MatchId::new();
        let container_id = ContainerId::new();

        let s1 = service.create(user1, match_id, container_id);
        let s2 = service.create(user2, match_id, container_id);

        // Different container
        let other_container = ContainerId::new();
        service.create(user1, match_id, other_container);

        let container_sessions = service.get_by_container(container_id);
        assert_eq!(container_sessions.len(), 2);

        let ids: Vec<SessionId> = container_sessions.iter().map(|s| s.id).collect();
        assert!(ids.contains(&s1));
        assert!(ids.contains(&s2));
    }

    #[test]
    fn active_session_count() {
        let service = SessionService::new();
        let container_id = ContainerId::new();
        let match_id = MatchId::new();

        let s1 = service.create(UserId::new(), match_id, container_id);
        let _s2 = service.create(UserId::new(), match_id, container_id);
        service.create(UserId::new(), match_id, container_id);

        assert_eq!(service.active_session_count(), 3);

        service.disconnect(s1).unwrap();
        assert_eq!(service.active_session_count(), 2);

        service.expire_inactive(Duration::from_nanos(1));
        assert_eq!(service.active_session_count(), 0);
    }

    #[test]
    fn disconnect_nonexistent_session_returns_error() {
        let service = SessionService::new();
        let fake_id = SessionId::new();

        let result = service.disconnect(fake_id);
        assert!(result.is_err());
    }

    #[test]
    fn update_activity_nonexistent_returns_false() {
        let service = SessionService::new();
        let fake_id = SessionId::new();

        let updated = service.update_activity(fake_id);
        assert!(!updated);
    }

    #[test]
    fn shared_session_service_creation() {
        let service = shared_session_service();
        assert_eq!(service.session_count(), 0);
    }

    #[test]
    fn player_session_helpers() {
        let session = PlayerSession::new(
            UserId::new(),
            MatchId::new(),
            ContainerId::new(),
        );

        assert!(session.is_active());
        assert!(!session.is_disconnected());
        assert!(!session.is_expired());
    }

    #[test]
    fn all_sessions_returns_all() {
        let service = SessionService::new();
        let container_id = ContainerId::new();
        let match_id = MatchId::new();

        service.create(UserId::new(), match_id, container_id);
        service.create(UserId::new(), match_id, container_id);
        service.create(UserId::new(), match_id, container_id);

        let all = service.all_sessions();
        assert_eq!(all.len(), 3);
    }
}
