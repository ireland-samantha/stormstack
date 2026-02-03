//! Match repository trait and implementations.

use async_trait::async_trait;
use parking_lot::RwLock;
use serde_json::Value as JsonValue;
use sqlx::Row;
use std::collections::HashMap;
use std::sync::Arc;
use stormstack_core::{ContainerId, MatchId, UserId};
use tracing::instrument;
use uuid::Uuid;

use crate::models::MatchRecord;
use crate::pool::DbPool;
use crate::Result;

/// Repository trait for match persistence operations.
#[async_trait]
pub trait MatchRepository: Send + Sync {
    /// Create a new match in the database.
    ///
    /// # Errors
    ///
    /// Returns an error if the insert fails.
    async fn create(&self, match_: &MatchRecord) -> Result<MatchId>;

    /// Get a match by its ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn get(&self, id: MatchId) -> Result<Option<MatchRecord>>;

    /// List all matches belonging to a container.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn list_by_container(&self, container_id: ContainerId) -> Result<Vec<MatchRecord>>;

    /// Delete a match by its ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the delete fails.
    async fn delete(&self, id: MatchId) -> Result<bool>;

    /// Update the state of a match.
    ///
    /// # Errors
    ///
    /// Returns an error if the update fails.
    async fn update_state(&self, id: MatchId, state: &str) -> Result<()>;

    /// Add a player to a match.
    ///
    /// # Errors
    ///
    /// Returns an error if the update fails.
    async fn add_player(&self, id: MatchId, user_id: UserId) -> Result<()>;

    /// Remove a player from a match.
    ///
    /// # Errors
    ///
    /// Returns an error if the update fails.
    async fn remove_player(&self, id: MatchId, user_id: UserId) -> Result<()>;
}

/// `PostgreSQL` implementation of the match repository.
pub struct PostgresMatchRepository {
    pool: DbPool,
}

impl PostgresMatchRepository {
    /// Create a new `PostgreSQL` match repository.
    #[must_use]
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    fn parse_players(json: JsonValue) -> Vec<UserId> {
        json.as_array()
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v.as_str())
                    .filter_map(|s| Uuid::parse_str(s).ok())
                    .map(UserId)
                    .collect()
            })
            .unwrap_or_default()
    }

    fn players_to_json(players: &[UserId]) -> JsonValue {
        JsonValue::Array(
            players
                .iter()
                .map(|id| JsonValue::String(id.0.to_string()))
                .collect(),
        )
    }
}

#[async_trait]
impl MatchRepository for PostgresMatchRepository {
    #[instrument(skip(self, match_))]
    async fn create(&self, match_: &MatchRecord) -> Result<MatchId> {
        let players_json = Self::players_to_json(&match_.players);

        sqlx::query(
            r"
            INSERT INTO matches (id, container_id, state, game_mode, max_players, current_tick, players, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
            ",
        )
        .bind(match_.id.0)
        .bind(match_.container_id.0)
        .bind(&match_.state)
        .bind(&match_.game_mode)
        .bind(match_.max_players)
        .bind(i64::try_from(match_.current_tick).unwrap_or(0))
        .bind(players_json)
        .bind(match_.created_at)
        .execute(self.pool.inner())
        .await?;

        Ok(match_.id)
    }

    #[instrument(skip(self))]
    async fn get(&self, id: MatchId) -> Result<Option<MatchRecord>> {
        let row = sqlx::query(
            r"
            SELECT id, container_id, state, game_mode, max_players, current_tick, players, created_at
            FROM matches
            WHERE id = $1
            ",
        )
        .bind(id.0)
        .fetch_optional(self.pool.inner())
        .await?;

        Ok(row.map(|r| MatchRecord {
            id: MatchId(r.get::<Uuid, _>("id")),
            container_id: ContainerId(r.get::<Uuid, _>("container_id")),
            state: r.get("state"),
            game_mode: r.get("game_mode"),
            max_players: r.get("max_players"),
            current_tick: u64::try_from(r.get::<i64, _>("current_tick")).unwrap_or(0),
            players: Self::parse_players(r.get("players")),
            created_at: r.get("created_at"),
        }))
    }

    #[instrument(skip(self))]
    async fn list_by_container(&self, container_id: ContainerId) -> Result<Vec<MatchRecord>> {
        let rows = sqlx::query(
            r"
            SELECT id, container_id, state, game_mode, max_players, current_tick, players, created_at
            FROM matches
            WHERE container_id = $1
            ORDER BY created_at DESC
            ",
        )
        .bind(container_id.0)
        .fetch_all(self.pool.inner())
        .await?;

        Ok(rows
            .into_iter()
            .map(|r| MatchRecord {
                id: MatchId(r.get::<Uuid, _>("id")),
                container_id: ContainerId(r.get::<Uuid, _>("container_id")),
                state: r.get("state"),
                game_mode: r.get("game_mode"),
                max_players: r.get("max_players"),
                current_tick: u64::try_from(r.get::<i64, _>("current_tick")).unwrap_or(0),
                players: Self::parse_players(r.get("players")),
                created_at: r.get("created_at"),
            })
            .collect())
    }

    #[instrument(skip(self))]
    async fn delete(&self, id: MatchId) -> Result<bool> {
        let result = sqlx::query("DELETE FROM matches WHERE id = $1")
            .bind(id.0)
            .execute(self.pool.inner())
            .await?;

        Ok(result.rows_affected() > 0)
    }

    #[instrument(skip(self))]
    async fn update_state(&self, id: MatchId, state: &str) -> Result<()> {
        sqlx::query("UPDATE matches SET state = $2 WHERE id = $1")
            .bind(id.0)
            .bind(state)
            .execute(self.pool.inner())
            .await?;

        Ok(())
    }

    #[instrument(skip(self))]
    async fn add_player(&self, id: MatchId, user_id: UserId) -> Result<()> {
        // Use JSONB array concatenation to add player
        sqlx::query(
            r"
            UPDATE matches
            SET players = players || to_jsonb($2::text)
            WHERE id = $1 AND NOT players ? $2::text
            ",
        )
        .bind(id.0)
        .bind(user_id.0.to_string())
        .execute(self.pool.inner())
        .await?;

        Ok(())
    }

    #[instrument(skip(self))]
    async fn remove_player(&self, id: MatchId, user_id: UserId) -> Result<()> {
        // Use JSONB array removal
        sqlx::query(
            r"
            UPDATE matches
            SET players = players - $2::text
            WHERE id = $1
            ",
        )
        .bind(id.0)
        .bind(user_id.0.to_string())
        .execute(self.pool.inner())
        .await?;

        Ok(())
    }
}

/// In-memory implementation of the match repository for testing.
#[derive(Default)]
pub struct InMemoryMatchRepository {
    matches: Arc<RwLock<HashMap<MatchId, MatchRecord>>>,
}

impl InMemoryMatchRepository {
    /// Create a new in-memory match repository.
    #[must_use]
    pub fn new() -> Self {
        Self {
            matches: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}

#[async_trait]
impl MatchRepository for InMemoryMatchRepository {
    async fn create(&self, match_: &MatchRecord) -> Result<MatchId> {
        let mut matches = self.matches.write();
        matches.insert(match_.id, match_.clone());
        Ok(match_.id)
    }

    async fn get(&self, id: MatchId) -> Result<Option<MatchRecord>> {
        let matches = self.matches.read();
        Ok(matches.get(&id).cloned())
    }

    async fn list_by_container(&self, container_id: ContainerId) -> Result<Vec<MatchRecord>> {
        let matches = self.matches.read();
        let mut result: Vec<MatchRecord> = matches
            .values()
            .filter(|m| m.container_id == container_id)
            .cloned()
            .collect();
        result.sort_by(|a, b| b.created_at.cmp(&a.created_at));
        Ok(result)
    }

    async fn delete(&self, id: MatchId) -> Result<bool> {
        let mut matches = self.matches.write();
        Ok(matches.remove(&id).is_some())
    }

    async fn update_state(&self, id: MatchId, state: &str) -> Result<()> {
        let mut matches = self.matches.write();
        if let Some(match_) = matches.get_mut(&id) {
            match_.state = state.to_string();
        }
        Ok(())
    }

    async fn add_player(&self, id: MatchId, user_id: UserId) -> Result<()> {
        let mut matches = self.matches.write();
        if let Some(match_) = matches.get_mut(&id)
            && !match_.players.contains(&user_id) {
                match_.players.push(user_id);
            }
        Ok(())
    }

    async fn remove_player(&self, id: MatchId, user_id: UserId) -> Result<()> {
        let mut matches = self.matches.write();
        if let Some(match_) = matches.get_mut(&id) {
            match_.players.retain(|&p| p != user_id);
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn match_crud_operations() {
        let repo = InMemoryMatchRepository::new();
        let container_id = ContainerId::new();

        // Create
        let match_ = MatchRecord::new(container_id, "deathmatch".to_string(), 8);
        let id = repo.create(&match_).await.unwrap();
        assert_eq!(id, match_.id);

        // Read
        let fetched = repo.get(id).await.unwrap();
        assert!(fetched.is_some());
        let fetched = fetched.unwrap();
        assert_eq!(fetched.id, id);
        assert_eq!(fetched.container_id, container_id);
        assert_eq!(fetched.state, "pending");
        assert_eq!(fetched.game_mode, "deathmatch");
        assert_eq!(fetched.max_players, 8);
        assert!(fetched.players.is_empty());

        // Update state
        repo.update_state(id, "active").await.unwrap();
        let updated = repo.get(id).await.unwrap().unwrap();
        assert_eq!(updated.state, "active");

        // Add player
        let player_id = UserId::new();
        repo.add_player(id, player_id).await.unwrap();
        let with_player = repo.get(id).await.unwrap().unwrap();
        assert_eq!(with_player.players.len(), 1);
        assert_eq!(with_player.players[0], player_id);

        // Add same player again (should not duplicate)
        repo.add_player(id, player_id).await.unwrap();
        let no_dup = repo.get(id).await.unwrap().unwrap();
        assert_eq!(no_dup.players.len(), 1);

        // Remove player
        repo.remove_player(id, player_id).await.unwrap();
        let without_player = repo.get(id).await.unwrap().unwrap();
        assert!(without_player.players.is_empty());

        // Delete
        let deleted = repo.delete(id).await.unwrap();
        assert!(deleted);

        // Verify deleted
        let not_found = repo.get(id).await.unwrap();
        assert!(not_found.is_none());
    }

    #[tokio::test]
    async fn list_by_container() {
        let repo = InMemoryMatchRepository::new();

        let container1 = ContainerId::new();
        let container2 = ContainerId::new();

        // Create matches for container1
        let m1 = MatchRecord::new(container1, "mode1".to_string(), 4);
        let m2 = MatchRecord::new(container1, "mode2".to_string(), 8);
        repo.create(&m1).await.unwrap();
        repo.create(&m2).await.unwrap();

        // Create match for container2
        let m3 = MatchRecord::new(container2, "mode3".to_string(), 16);
        repo.create(&m3).await.unwrap();

        // List container1 matches
        let container1_matches = repo.list_by_container(container1).await.unwrap();
        assert_eq!(container1_matches.len(), 2);
        assert!(container1_matches
            .iter()
            .all(|m| m.container_id == container1));

        // List container2 matches
        let container2_matches = repo.list_by_container(container2).await.unwrap();
        assert_eq!(container2_matches.len(), 1);
        assert_eq!(container2_matches[0].container_id, container2);
    }

    #[tokio::test]
    async fn state_transitions() {
        let repo = InMemoryMatchRepository::new();
        let container_id = ContainerId::new();

        let match_ = MatchRecord::new(container_id, "capture_the_flag".to_string(), 12);
        let id = repo.create(&match_).await.unwrap();

        // Initial state is pending
        let m = repo.get(id).await.unwrap().unwrap();
        assert_eq!(m.state, "pending");

        // Transition to active
        repo.update_state(id, "active").await.unwrap();
        let m = repo.get(id).await.unwrap().unwrap();
        assert_eq!(m.state, "active");

        // Transition to completed
        repo.update_state(id, "completed").await.unwrap();
        let m = repo.get(id).await.unwrap().unwrap();
        assert_eq!(m.state, "completed");
    }

    #[tokio::test]
    async fn multiple_players() {
        let repo = InMemoryMatchRepository::new();
        let container_id = ContainerId::new();

        let match_ = MatchRecord::new(container_id, "team_battle".to_string(), 4);
        let id = repo.create(&match_).await.unwrap();

        let player1 = UserId::new();
        let player2 = UserId::new();
        let player3 = UserId::new();

        // Add multiple players
        repo.add_player(id, player1).await.unwrap();
        repo.add_player(id, player2).await.unwrap();
        repo.add_player(id, player3).await.unwrap();

        let m = repo.get(id).await.unwrap().unwrap();
        assert_eq!(m.players.len(), 3);

        // Remove middle player
        repo.remove_player(id, player2).await.unwrap();
        let m = repo.get(id).await.unwrap().unwrap();
        assert_eq!(m.players.len(), 2);
        assert!(m.players.contains(&player1));
        assert!(!m.players.contains(&player2));
        assert!(m.players.contains(&player3));
    }

    #[tokio::test]
    async fn delete_nonexistent_returns_false() {
        let repo = InMemoryMatchRepository::new();
        let result = repo.delete(MatchId::new()).await.unwrap();
        assert!(!result);
    }

    #[tokio::test]
    async fn get_nonexistent_returns_none() {
        let repo = InMemoryMatchRepository::new();
        let result = repo.get(MatchId::new()).await.unwrap();
        assert!(result.is_none());
    }
}
