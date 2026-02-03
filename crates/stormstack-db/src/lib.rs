//! # `StormStack` Database
//!
//! Database persistence layer for `StormStack` using `SQLx` with `PostgreSQL`.
//!
//! This crate provides:
//! - Connection pool management
//! - Repository traits for containers, matches, and users
//! - `PostgreSQL` implementations of all repositories
//! - In-memory implementations for testing
//!
//! ## Example
//!
//! ```rust,ignore
//! use stormstack_db::{DbPool, PostgresContainerRepository, ContainerRepository};
//!
//! let pool = DbPool::connect("postgres://localhost/stormstack").await?;
//! let repo = PostgresContainerRepository::new(pool.clone());
//!
//! let containers = repo.list_by_tenant(tenant_id).await?;
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]
#![warn(clippy::pedantic)]

pub mod models;
pub mod pool;
pub mod repositories;

pub use models::{ContainerRecord, MatchRecord, UserRecord};
pub use pool::DbPool;
pub use repositories::{
    ContainerRepository, InMemoryContainerRepository, InMemoryMatchRepository,
    InMemoryUserRepository, MatchRepository, PostgresContainerRepository, PostgresMatchRepository,
    PostgresUserRepository, UserRepository,
};

/// Database error types.
#[derive(Debug, thiserror::Error)]
pub enum DbError {
    /// Connection pool error.
    #[error("Pool error: {0}")]
    Pool(String),

    /// Query execution error.
    #[error("Query error: {0}")]
    Query(#[from] sqlx::Error),

    /// Record not found.
    #[error("Record not found: {0}")]
    NotFound(String),

    /// Serialization/deserialization error.
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
}

/// Result type for database operations.
pub type Result<T> = std::result::Result<T, DbError>;
