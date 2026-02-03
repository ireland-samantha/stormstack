//! Connection pool management for `PostgreSQL`.
//!
//! Provides a wrapper around `SQLx`'s connection pool with
//! configuration options and health checking.

use sqlx::postgres::{PgPool, PgPoolOptions};
use std::time::Duration;
use tracing::{info, instrument};

use crate::{DbError, Result};

/// Database connection pool wrapper.
///
/// Wraps `SQLx`'s `PgPool` with additional functionality for
/// connection management and health checking.
#[derive(Clone)]
pub struct DbPool {
    inner: PgPool,
}

impl DbPool {
    /// Connect to the database with default settings.
    ///
    /// Uses sensible defaults for connection pool settings:
    /// - Max connections: 10
    /// - Min connections: 1
    /// - Acquire timeout: 30 seconds
    /// - Idle timeout: 10 minutes
    ///
    /// # Errors
    ///
    /// Returns an error if the connection cannot be established.
    #[instrument(skip(database_url))]
    pub async fn connect(database_url: &str) -> Result<Self> {
        Self::connect_with_options(database_url, DbPoolOptions::default()).await
    }

    /// Connect to the database with custom options.
    ///
    /// # Errors
    ///
    /// Returns an error if the connection cannot be established.
    #[instrument(skip(database_url))]
    pub async fn connect_with_options(database_url: &str, options: DbPoolOptions) -> Result<Self> {
        info!(
            max_connections = options.max_connections,
            min_connections = options.min_connections,
            "Connecting to database"
        );

        let pool = PgPoolOptions::new()
            .max_connections(options.max_connections)
            .min_connections(options.min_connections)
            .acquire_timeout(options.acquire_timeout)
            .idle_timeout(options.idle_timeout)
            .connect(database_url)
            .await
            .map_err(|e| DbError::Pool(e.to_string()))?;

        info!("Database connection pool established");

        Ok(Self { inner: pool })
    }

    /// Get a reference to the inner `SQLx` pool.
    #[must_use]
    pub fn inner(&self) -> &PgPool {
        &self.inner
    }

    /// Check if the database connection is healthy.
    ///
    /// # Errors
    ///
    /// Returns an error if the health check fails.
    pub async fn health_check(&self) -> Result<()> {
        sqlx::query("SELECT 1")
            .execute(&self.inner)
            .await
            .map_err(|e| DbError::Pool(format!("Health check failed: {e}")))?;
        Ok(())
    }

    /// Run pending migrations.
    ///
    /// # Errors
    ///
    /// Returns an error if migrations fail.
    pub async fn run_migrations(&self) -> Result<()> {
        sqlx::migrate!("./migrations")
            .run(&self.inner)
            .await
            .map_err(|e| DbError::Pool(format!("Migration failed: {e}")))?;
        info!("Database migrations completed");
        Ok(())
    }

    /// Close the connection pool.
    pub async fn close(&self) {
        self.inner.close().await;
        info!("Database connection pool closed");
    }
}

/// Configuration options for the database pool.
#[derive(Debug, Clone)]
pub struct DbPoolOptions {
    /// Maximum number of connections in the pool.
    pub max_connections: u32,
    /// Minimum number of connections to maintain.
    pub min_connections: u32,
    /// Maximum time to wait for a connection.
    pub acquire_timeout: Duration,
    /// Time before an idle connection is closed.
    pub idle_timeout: Option<Duration>,
}

impl Default for DbPoolOptions {
    fn default() -> Self {
        Self {
            max_connections: 10,
            min_connections: 1,
            acquire_timeout: Duration::from_secs(30),
            idle_timeout: Some(Duration::from_secs(600)),
        }
    }
}

impl DbPoolOptions {
    /// Create a new options builder with default values.
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Set the maximum number of connections.
    #[must_use]
    pub fn max_connections(mut self, max: u32) -> Self {
        self.max_connections = max;
        self
    }

    /// Set the minimum number of connections.
    #[must_use]
    pub fn min_connections(mut self, min: u32) -> Self {
        self.min_connections = min;
        self
    }

    /// Set the acquire timeout.
    #[must_use]
    pub fn acquire_timeout(mut self, timeout: Duration) -> Self {
        self.acquire_timeout = timeout;
        self
    }

    /// Set the idle timeout.
    #[must_use]
    pub fn idle_timeout(mut self, timeout: Option<Duration>) -> Self {
        self.idle_timeout = timeout;
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_options_have_sensible_values() {
        let options = DbPoolOptions::default();

        assert_eq!(options.max_connections, 10);
        assert_eq!(options.min_connections, 1);
        assert_eq!(options.acquire_timeout, Duration::from_secs(30));
        assert_eq!(options.idle_timeout, Some(Duration::from_secs(600)));
    }

    #[test]
    fn new_returns_default() {
        let options = DbPoolOptions::new();
        let default = DbPoolOptions::default();

        assert_eq!(options.max_connections, default.max_connections);
        assert_eq!(options.min_connections, default.min_connections);
        assert_eq!(options.acquire_timeout, default.acquire_timeout);
        assert_eq!(options.idle_timeout, default.idle_timeout);
    }

    #[test]
    fn max_connections_builder() {
        let options = DbPoolOptions::new().max_connections(50);
        assert_eq!(options.max_connections, 50);
        // Other fields should remain default
        assert_eq!(options.min_connections, 1);
    }

    #[test]
    fn min_connections_builder() {
        let options = DbPoolOptions::new().min_connections(5);
        assert_eq!(options.min_connections, 5);
        // Other fields should remain default
        assert_eq!(options.max_connections, 10);
    }

    #[test]
    fn acquire_timeout_builder() {
        let timeout = Duration::from_secs(60);
        let options = DbPoolOptions::new().acquire_timeout(timeout);
        assert_eq!(options.acquire_timeout, timeout);
    }

    #[test]
    fn idle_timeout_builder() {
        let timeout = Duration::from_secs(300);
        let options = DbPoolOptions::new().idle_timeout(Some(timeout));
        assert_eq!(options.idle_timeout, Some(timeout));
    }

    #[test]
    fn idle_timeout_none() {
        let options = DbPoolOptions::new().idle_timeout(None);
        assert_eq!(options.idle_timeout, None);
    }

    #[test]
    fn builder_chaining() {
        let options = DbPoolOptions::new()
            .max_connections(100)
            .min_connections(10)
            .acquire_timeout(Duration::from_secs(5))
            .idle_timeout(Some(Duration::from_secs(120)));

        assert_eq!(options.max_connections, 100);
        assert_eq!(options.min_connections, 10);
        assert_eq!(options.acquire_timeout, Duration::from_secs(5));
        assert_eq!(options.idle_timeout, Some(Duration::from_secs(120)));
    }

    #[test]
    fn options_are_cloneable() {
        let original = DbPoolOptions::new().max_connections(20);
        let cloned = original.clone();

        assert_eq!(original.max_connections, cloned.max_connections);
        assert_eq!(original.min_connections, cloned.min_connections);
    }

    #[test]
    fn options_are_debuggable() {
        let options = DbPoolOptions::new();
        let debug_str = format!("{:?}", options);

        assert!(debug_str.contains("DbPoolOptions"));
        assert!(debug_str.contains("max_connections"));
    }

    // =========================================================================
    // Bailey's peer review improvements
    // =========================================================================

    #[test]
    fn min_greater_than_max_connections_allowed() {
        // Note: The builder allows this configuration - validation happens at
        // connection time in PgPoolOptions. We document this behavior.
        let options = DbPoolOptions::new()
            .min_connections(20)
            .max_connections(5);

        // Builder doesn't prevent invalid configuration
        assert_eq!(options.min_connections, 20);
        assert_eq!(options.max_connections, 5);
        assert!(
            options.min_connections > options.max_connections,
            "Invalid config should be detectable: min ({}) > max ({})",
            options.min_connections,
            options.max_connections
        );
    }

    #[test]
    fn zero_max_connections_allowed_by_builder() {
        // Zero connections is technically allowed by the builder.
        // SQLx will reject this at pool creation time.
        let options = DbPoolOptions::new().max_connections(0);
        assert_eq!(options.max_connections, 0);
    }

    #[test]
    fn boundary_values_for_connections() {
        // Test with u32::MAX - should not panic
        let options = DbPoolOptions::new()
            .max_connections(u32::MAX)
            .min_connections(u32::MAX);

        assert_eq!(options.max_connections, u32::MAX);
        assert_eq!(options.min_connections, u32::MAX);
    }
}
