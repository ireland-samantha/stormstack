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
