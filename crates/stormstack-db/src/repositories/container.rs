//! Container repository trait and implementations.

use async_trait::async_trait;
use chrono::Utc;
use parking_lot::RwLock;
use sqlx::Row;
use std::collections::HashMap;
use std::sync::Arc;
use stormstack_core::{ContainerId, TenantId};
use tracing::instrument;
use uuid::Uuid;

use crate::models::ContainerRecord;
use crate::pool::DbPool;
use crate::Result;

/// Repository trait for container persistence operations.
#[async_trait]
pub trait ContainerRepository: Send + Sync {
    /// Create a new container in the database.
    ///
    /// # Errors
    ///
    /// Returns an error if the insert fails.
    async fn create(&self, container: &ContainerRecord) -> Result<ContainerId>;

    /// Get a container by its ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn get(&self, id: ContainerId) -> Result<Option<ContainerRecord>>;

    /// List all containers belonging to a tenant.
    ///
    /// # Errors
    ///
    /// Returns an error if the query fails.
    async fn list_by_tenant(&self, tenant_id: TenantId) -> Result<Vec<ContainerRecord>>;

    /// Delete a container by its ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the delete fails.
    async fn delete(&self, id: ContainerId) -> Result<bool>;

    /// Update the current tick for a container.
    ///
    /// # Errors
    ///
    /// Returns an error if the update fails.
    async fn update_tick(&self, id: ContainerId, tick: u64) -> Result<()>;
}

/// `PostgreSQL` implementation of the container repository.
pub struct PostgresContainerRepository {
    pool: DbPool,
}

impl PostgresContainerRepository {
    /// Create a new `PostgreSQL` container repository.
    #[must_use]
    pub fn new(pool: DbPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ContainerRepository for PostgresContainerRepository {
    #[instrument(skip(self, container))]
    async fn create(&self, container: &ContainerRecord) -> Result<ContainerId> {
        sqlx::query(
            r"
            INSERT INTO containers (id, tenant_id, current_tick, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5)
            ",
        )
        .bind(container.id.0)
        .bind(container.tenant_id.0)
        .bind(i64::try_from(container.current_tick).unwrap_or(0))
        .bind(container.created_at)
        .bind(container.updated_at)
        .execute(self.pool.inner())
        .await?;

        Ok(container.id)
    }

    #[instrument(skip(self))]
    async fn get(&self, id: ContainerId) -> Result<Option<ContainerRecord>> {
        let row = sqlx::query(
            r"
            SELECT id, tenant_id, current_tick, created_at, updated_at
            FROM containers
            WHERE id = $1
            ",
        )
        .bind(id.0)
        .fetch_optional(self.pool.inner())
        .await?;

        Ok(row.map(|r| ContainerRecord {
            id: ContainerId(r.get::<Uuid, _>("id")),
            tenant_id: TenantId(r.get::<Uuid, _>("tenant_id")),
            current_tick: u64::try_from(r.get::<i64, _>("current_tick")).unwrap_or(0),
            created_at: r.get("created_at"),
            updated_at: r.get("updated_at"),
        }))
    }

    #[instrument(skip(self))]
    async fn list_by_tenant(&self, tenant_id: TenantId) -> Result<Vec<ContainerRecord>> {
        let rows = sqlx::query(
            r"
            SELECT id, tenant_id, current_tick, created_at, updated_at
            FROM containers
            WHERE tenant_id = $1
            ORDER BY created_at DESC
            ",
        )
        .bind(tenant_id.0)
        .fetch_all(self.pool.inner())
        .await?;

        Ok(rows
            .into_iter()
            .map(|r| ContainerRecord {
                id: ContainerId(r.get::<Uuid, _>("id")),
                tenant_id: TenantId(r.get::<Uuid, _>("tenant_id")),
                current_tick: u64::try_from(r.get::<i64, _>("current_tick")).unwrap_or(0),
                created_at: r.get("created_at"),
                updated_at: r.get("updated_at"),
            })
            .collect())
    }

    #[instrument(skip(self))]
    async fn delete(&self, id: ContainerId) -> Result<bool> {
        let result = sqlx::query("DELETE FROM containers WHERE id = $1")
            .bind(id.0)
            .execute(self.pool.inner())
            .await?;

        Ok(result.rows_affected() > 0)
    }

    #[instrument(skip(self))]
    async fn update_tick(&self, id: ContainerId, tick: u64) -> Result<()> {
        sqlx::query(
            r"
            UPDATE containers
            SET current_tick = $2, updated_at = NOW()
            WHERE id = $1
            ",
        )
        .bind(id.0)
        .bind(i64::try_from(tick).unwrap_or(0))
        .execute(self.pool.inner())
        .await?;

        Ok(())
    }
}

/// In-memory implementation of the container repository for testing.
#[derive(Default)]
pub struct InMemoryContainerRepository {
    containers: Arc<RwLock<HashMap<ContainerId, ContainerRecord>>>,
}

impl InMemoryContainerRepository {
    /// Create a new in-memory container repository.
    #[must_use]
    pub fn new() -> Self {
        Self {
            containers: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}

#[async_trait]
impl ContainerRepository for InMemoryContainerRepository {
    async fn create(&self, container: &ContainerRecord) -> Result<ContainerId> {
        let mut containers = self.containers.write();
        containers.insert(container.id, container.clone());
        Ok(container.id)
    }

    async fn get(&self, id: ContainerId) -> Result<Option<ContainerRecord>> {
        let containers = self.containers.read();
        Ok(containers.get(&id).cloned())
    }

    async fn list_by_tenant(&self, tenant_id: TenantId) -> Result<Vec<ContainerRecord>> {
        let containers = self.containers.read();
        let mut result: Vec<ContainerRecord> = containers
            .values()
            .filter(|c| c.tenant_id == tenant_id)
            .cloned()
            .collect();
        result.sort_by(|a, b| b.created_at.cmp(&a.created_at));
        Ok(result)
    }

    async fn delete(&self, id: ContainerId) -> Result<bool> {
        let mut containers = self.containers.write();
        Ok(containers.remove(&id).is_some())
    }

    async fn update_tick(&self, id: ContainerId, tick: u64) -> Result<()> {
        let mut containers = self.containers.write();
        if let Some(container) = containers.get_mut(&id) {
            container.current_tick = tick;
            container.updated_at = Utc::now();
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn container_crud_operations() {
        let repo = InMemoryContainerRepository::new();
        let tenant_id = TenantId::new();

        // Create
        let container = ContainerRecord::new(tenant_id);
        let id = repo.create(&container).await.unwrap();
        assert_eq!(id, container.id);

        // Read
        let fetched = repo.get(id).await.unwrap();
        assert!(fetched.is_some());
        let fetched = fetched.unwrap();
        assert_eq!(fetched.id, id);
        assert_eq!(fetched.tenant_id, tenant_id);
        assert_eq!(fetched.current_tick, 0);

        // Update tick
        repo.update_tick(id, 100).await.unwrap();
        let updated = repo.get(id).await.unwrap().unwrap();
        assert_eq!(updated.current_tick, 100);

        // Delete
        let deleted = repo.delete(id).await.unwrap();
        assert!(deleted);

        // Verify deleted
        let not_found = repo.get(id).await.unwrap();
        assert!(not_found.is_none());
    }

    #[tokio::test]
    async fn list_by_tenant_returns_only_tenant_containers() {
        let repo = InMemoryContainerRepository::new();

        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create containers for tenant1
        let c1 = ContainerRecord::new(tenant1);
        let c2 = ContainerRecord::new(tenant1);
        repo.create(&c1).await.unwrap();
        repo.create(&c2).await.unwrap();

        // Create container for tenant2
        let c3 = ContainerRecord::new(tenant2);
        repo.create(&c3).await.unwrap();

        // List tenant1 containers
        let tenant1_containers = repo.list_by_tenant(tenant1).await.unwrap();
        assert_eq!(tenant1_containers.len(), 2);
        assert!(tenant1_containers.iter().all(|c| c.tenant_id == tenant1));

        // List tenant2 containers
        let tenant2_containers = repo.list_by_tenant(tenant2).await.unwrap();
        assert_eq!(tenant2_containers.len(), 1);
        assert_eq!(tenant2_containers[0].tenant_id, tenant2);
    }

    #[tokio::test]
    async fn tenant_isolation() {
        let repo = InMemoryContainerRepository::new();

        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create container for tenant1
        let c1 = ContainerRecord::new(tenant1);
        repo.create(&c1).await.unwrap();

        // Tenant2 should see no containers
        let tenant2_containers = repo.list_by_tenant(tenant2).await.unwrap();
        assert!(tenant2_containers.is_empty());

        // Tenant1 should see its container
        let tenant1_containers = repo.list_by_tenant(tenant1).await.unwrap();
        assert_eq!(tenant1_containers.len(), 1);
    }

    #[tokio::test]
    async fn delete_nonexistent_returns_false() {
        let repo = InMemoryContainerRepository::new();
        let result = repo.delete(ContainerId::new()).await.unwrap();
        assert!(!result);
    }

    #[tokio::test]
    async fn get_nonexistent_returns_none() {
        let repo = InMemoryContainerRepository::new();
        let result = repo.get(ContainerId::new()).await.unwrap();
        assert!(result.is_none());
    }
}
