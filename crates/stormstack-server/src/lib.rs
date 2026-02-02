//! # StormStack Server
//!
//! Main server binary and integration crate.
//!
//! This crate wires together all StormStack components:
//! - ECS world management
//! - WASM sandbox for user modules
//! - Authentication and authorization
//! - WebSocket streaming
//! - REST API
//! - Container management

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod container;

// TODO: Implement server components
