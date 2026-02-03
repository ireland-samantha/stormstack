//! Repository traits and implementations for database access.
//!
//! This module defines the repository pattern interfaces and provides
//! both `PostgreSQL` and in-memory implementations.

mod container;
mod match_;
mod user;

pub use container::{ContainerRepository, InMemoryContainerRepository, PostgresContainerRepository};
pub use match_::{InMemoryMatchRepository, MatchRepository, PostgresMatchRepository};
pub use user::{InMemoryUserRepository, PostgresUserRepository, UserRepository};
