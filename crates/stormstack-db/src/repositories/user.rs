//! User repository trait and implementations.

use async_trait::async_trait;
use parking_lot::RwLock;
use serde_json::Value as JsonValue;
use sqlx::Row;
use std::collections::HashMap;
use std::sync::Arc;
use stormstack_core::{TenantId, UserId};
use tracing::instrument;
use uuid::Uuid;

use crate::models::UserRecord;
use crate::pool::DbPool;
use crate::Result;

/// Repository trait for user persistence operations.
#[async_trait]
pub trait UserRepository: Send + Sync {
    /// Create a new user in the database.
    ///
    /// # Errors
    ///
    /// Returns an error if the insert fails.
    async fn create(&self, user: &UserRecord) -> Result<UserId>;

    /// Get a user by their ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn get(&self, id: UserId) -> Result<Option<UserRecord>>;

    /// Get a user by their email address.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn get_by_email(&self, email: &str) -> Result<Option<UserRecord>>;

    /// Update a user's password hash.
    ///
    /// # Errors
    ///
    /// Returns an error if the update fails.
    async fn update_password(&self, id: UserId, password_hash: &str) -> Result<()>;
}

/// `PostgreSQL` implementation of the user repository.
pub struct PostgresUserRepository {
    pool: DbPool,
}

impl PostgresUserRepository {
    /// Create a new `PostgreSQL` user repository.
    #[must_use]
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }

    fn parse_roles(json: JsonValue) -> Vec<String> {
        json.as_array()
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v.as_str())
                    .map(String::from)
                    .collect()
            })
            .unwrap_or_default()
    }

    fn roles_to_json(roles: &[String]) -> JsonValue {
        JsonValue::Array(roles.iter().map(|r| JsonValue::String(r.clone())).collect())
    }
}

#[async_trait]
impl UserRepository for PostgresUserRepository {
    #[instrument(skip(self, user))]
    async fn create(&self, user: &UserRecord) -> Result<UserId> {
        let roles_json = Self::roles_to_json(&user.roles);

        sqlx::query(
            r"
            INSERT INTO users (id, tenant_id, email, password_hash, roles, created_at)
            VALUES ($1, $2, $3, $4, $5, $6)
            ",
        )
        .bind(user.id.0)
        .bind(user.tenant_id.0)
        .bind(&user.email)
        .bind(&user.password_hash)
        .bind(roles_json)
        .bind(user.created_at)
        .execute(self.pool.inner())
        .await?;

        Ok(user.id)
    }

    #[instrument(skip(self))]
    async fn get(&self, id: UserId) -> Result<Option<UserRecord>> {
        let row = sqlx::query(
            r"
            SELECT id, tenant_id, email, password_hash, roles, created_at
            FROM users
            WHERE id = $1
            ",
        )
        .bind(id.0)
        .fetch_optional(self.pool.inner())
        .await?;

        Ok(row.map(|r| UserRecord {
            id: UserId(r.get::<Uuid, _>("id")),
            tenant_id: TenantId(r.get::<Uuid, _>("tenant_id")),
            email: r.get("email"),
            password_hash: r.get("password_hash"),
            roles: Self::parse_roles(r.get("roles")),
            created_at: r.get("created_at"),
        }))
    }

    #[instrument(skip(self))]
    async fn get_by_email(&self, email: &str) -> Result<Option<UserRecord>> {
        let row = sqlx::query(
            r"
            SELECT id, tenant_id, email, password_hash, roles, created_at
            FROM users
            WHERE email = $1
            ",
        )
        .bind(email)
        .fetch_optional(self.pool.inner())
        .await?;

        Ok(row.map(|r| UserRecord {
            id: UserId(r.get::<Uuid, _>("id")),
            tenant_id: TenantId(r.get::<Uuid, _>("tenant_id")),
            email: r.get("email"),
            password_hash: r.get("password_hash"),
            roles: Self::parse_roles(r.get("roles")),
            created_at: r.get("created_at"),
        }))
    }

    #[instrument(skip(self, password_hash))]
    async fn update_password(&self, id: UserId, password_hash: &str) -> Result<()> {
        sqlx::query("UPDATE users SET password_hash = $2 WHERE id = $1")
            .bind(id.0)
            .bind(password_hash)
            .execute(self.pool.inner())
            .await?;

        Ok(())
    }
}

/// In-memory implementation of the user repository for testing.
#[derive(Default)]
pub struct InMemoryUserRepository {
    users: Arc<RwLock<HashMap<UserId, UserRecord>>>,
}

impl InMemoryUserRepository {
    /// Create a new in-memory user repository.
    #[must_use]
    pub fn new() -> Self {
        Self {
            users: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}

#[async_trait]
impl UserRepository for InMemoryUserRepository {
    async fn create(&self, user: &UserRecord) -> Result<UserId> {
        let mut users = self.users.write();
        users.insert(user.id, user.clone());
        Ok(user.id)
    }

    async fn get(&self, id: UserId) -> Result<Option<UserRecord>> {
        let users = self.users.read();
        Ok(users.get(&id).cloned())
    }

    async fn get_by_email(&self, email: &str) -> Result<Option<UserRecord>> {
        let users = self.users.read();
        Ok(users.values().find(|u| u.email == email).cloned())
    }

    async fn update_password(&self, id: UserId, password_hash: &str) -> Result<()> {
        let mut users = self.users.write();
        if let Some(user) = users.get_mut(&id) {
            user.password_hash = password_hash.to_string();
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn user_crud_operations() {
        let repo = InMemoryUserRepository::new();
        let tenant_id = TenantId::new();

        // Create
        let user = UserRecord::with_roles(
            tenant_id,
            "test@example.com".to_string(),
            "hashed_password".to_string(),
            vec!["player".to_string(), "admin".to_string()],
        );
        let id = repo.create(&user).await.unwrap();
        assert_eq!(id, user.id);

        // Read by ID
        let fetched = repo.get(id).await.unwrap();
        assert!(fetched.is_some());
        let fetched = fetched.unwrap();
        assert_eq!(fetched.id, id);
        assert_eq!(fetched.tenant_id, tenant_id);
        assert_eq!(fetched.email, "test@example.com");
        assert_eq!(fetched.password_hash, "hashed_password");
        assert_eq!(fetched.roles, vec!["player", "admin"]);

        // Read by email
        let by_email = repo.get_by_email("test@example.com").await.unwrap();
        assert!(by_email.is_some());
        assert_eq!(by_email.unwrap().id, id);

        // Update password
        repo.update_password(id, "new_hashed_password").await.unwrap();
        let updated = repo.get(id).await.unwrap().unwrap();
        assert_eq!(updated.password_hash, "new_hashed_password");
    }

    #[tokio::test]
    async fn tenant_isolation() {
        let repo = InMemoryUserRepository::new();

        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create user for tenant1
        let user1 = UserRecord::new(
            tenant1,
            "user1@example.com".to_string(),
            "hash1".to_string(),
        );
        repo.create(&user1).await.unwrap();

        // Create user for tenant2 with same email (different tenant)
        let user2 = UserRecord::new(
            tenant2,
            "user2@example.com".to_string(),
            "hash2".to_string(),
        );
        repo.create(&user2).await.unwrap();

        // Both users should exist
        let fetched1 = repo.get(user1.id).await.unwrap();
        assert!(fetched1.is_some());
        assert_eq!(fetched1.unwrap().tenant_id, tenant1);

        let fetched2 = repo.get(user2.id).await.unwrap();
        assert!(fetched2.is_some());
        assert_eq!(fetched2.unwrap().tenant_id, tenant2);
    }

    #[tokio::test]
    async fn get_by_email_nonexistent() {
        let repo = InMemoryUserRepository::new();
        let result = repo.get_by_email("nonexistent@example.com").await.unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn get_nonexistent_returns_none() {
        let repo = InMemoryUserRepository::new();
        let result = repo.get(UserId::new()).await.unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn user_with_empty_roles() {
        let repo = InMemoryUserRepository::new();
        let tenant_id = TenantId::new();

        let user = UserRecord::new(
            tenant_id,
            "noroles@example.com".to_string(),
            "hash".to_string(),
        );
        repo.create(&user).await.unwrap();

        let fetched = repo.get(user.id).await.unwrap().unwrap();
        assert!(fetched.roles.is_empty());
    }

    #[tokio::test]
    async fn update_password_nonexistent_is_noop() {
        let repo = InMemoryUserRepository::new();
        // Should not error even if user doesn't exist
        let result = repo
            .update_password(UserId::new(), "new_hash")
            .await;
        assert!(result.is_ok());
    }
}
