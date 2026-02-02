//! # StormStack Networking
//!
//! Networking primitives for StormStack services.
//!
//! This crate provides:
//! - HTTP server setup with axum (`ServerBuilder`)
//! - Common middleware (CORS, tracing, compression, timeout)
//! - Request extractors (`AuthUser`, `Pagination`)
//! - Response types (`ApiResponse`, `ApiError`, `PaginatedResponse`)
//!
//! ## Example
//!
//! ```rust,ignore
//! use stormstack_net::{ServerBuilder, ApiResponse};
//! use axum::{Router, routing::get};
//!
//! async fn health() -> ApiResponse<&'static str> {
//!     ApiResponse::ok("healthy")
//! }
//!
//! #[tokio::main]
//! async fn main() {
//!     let router = Router::new().route("/health", get(health));
//!
//!     ServerBuilder::new()
//!         .port(8080)
//!         .router(router)
//!         .run()
//!         .await
//!         .unwrap();
//! }
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod extractors;
pub mod responses;
pub mod server;

pub use extractors::{AuthState, AuthUser, OptionalAuth, Pagination};
pub use responses::{ApiError, ApiResponse, FieldError, PaginatedResponse};
pub use server::{shutdown_signal, ServerBuilder, ServerConfig};
