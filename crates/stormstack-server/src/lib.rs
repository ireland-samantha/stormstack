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
//!
//! ## Architecture
//!
//! The server is built around a few core types:
//!
//! - [`Server`]: Main server that holds all components and manages their lifecycle
//! - [`AppState`]: Shared state passed to axum handlers, implements [`AuthState`]
//! - [`ServerConfig`]: Configuration for the server
//!
//! ## Example
//!
//! ```rust,ignore
//! use stormstack_server::{Server, ServerConfig};
//!
//! #[tokio::main]
//! async fn main() -> anyhow::Result<()> {
//!     let config = ServerConfig::default();
//!     let server = Server::new(config)?;
//!     server.run().await
//! }
//! ```

#![warn(missing_docs)]
#![warn(clippy::all)]

pub mod container;
/// Game loop for tick-based simulation.
pub mod game_loop;
mod routes;
mod state;
/// WebSocket upgrade handler for real-time match streaming.
pub mod ws;

pub use container::{
    Container, ContainerService, LoadedModule, Match, MatchState, MatchSummary, SharedContainer,
    SharedContainerService, shared_container_service,
};
pub use game_loop::{GameLoop, GameLoopConfig};
pub use routes::create_router;
pub use state::{AppState, SharedAppState};

use anyhow::Result;
use std::net::SocketAddr;
use std::sync::Arc;
use stormstack_auth::JwtService;
use stormstack_ecs::{shared_world, SharedWorld};
use stormstack_net::ServerBuilder;
use stormstack_wasm::WasmSandbox;
use stormstack_ws::{shared_connection_manager, shared_subscriptions, SharedConnectionManager};
use tracing::{info, warn};

/// Server configuration.
#[derive(Debug, Clone)]
pub struct ServerConfig {
    /// Address to bind the server to.
    pub bind_addr: SocketAddr,
    /// JWT secret for authentication.
    pub jwt_secret: Vec<u8>,
    /// Enable CORS.
    pub cors_enabled: bool,
    /// Enable request tracing.
    pub tracing_enabled: bool,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            bind_addr: SocketAddr::from(([0, 0, 0, 0], 8080)),
            jwt_secret: b"default-dev-secret-change-in-production-32bytes!".to_vec(),
            cors_enabled: true,
            tracing_enabled: true,
        }
    }
}

impl ServerConfig {
    /// Create a config with a specific port.
    #[must_use]
    pub fn with_port(port: u16) -> Self {
        Self {
            bind_addr: SocketAddr::from(([0, 0, 0, 0], port)),
            ..Default::default()
        }
    }

    /// Set the JWT secret.
    #[must_use]
    pub fn with_jwt_secret(mut self, secret: impl AsRef<[u8]>) -> Self {
        self.jwt_secret = secret.as_ref().to_vec();
        self
    }

    /// Create config from environment variables.
    #[must_use]
    pub fn from_env() -> Self {
        let port = std::env::var("STORMSTACK_PORT")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(8080);

        let jwt_secret = std::env::var("STORMSTACK_JWT_SECRET")
            .map(|s| s.into_bytes())
            .unwrap_or_else(|_| {
                warn!("STORMSTACK_JWT_SECRET not set, using default (INSECURE)");
                b"default-dev-secret-change-in-production-32bytes!".to_vec()
            });

        Self {
            bind_addr: SocketAddr::from(([0, 0, 0, 0], port)),
            jwt_secret,
            cors_enabled: true,
            tracing_enabled: true,
        }
    }
}

/// Main StormStack server.
///
/// Holds all components and manages their lifecycle.
pub struct Server {
    /// Server configuration.
    config: ServerConfig,
    /// Shared ECS world.
    world: SharedWorld,
    /// WASM sandbox for executing modules.
    sandbox: Arc<WasmSandbox>,
    /// WebSocket connection manager.
    connections: SharedConnectionManager,
    /// JWT authentication service.
    jwt_service: Arc<JwtService>,
}

impl Server {
    /// Create a new server with the given configuration.
    ///
    /// # Errors
    ///
    /// Returns an error if initialization fails (e.g., WASM engine creation).
    pub fn new(config: ServerConfig) -> Result<Self> {
        info!("Initializing StormStack server");

        // Create ECS world
        let world = shared_world();
        info!("ECS world initialized");

        // Create WASM sandbox
        let sandbox = Arc::new(WasmSandbox::new()?);
        info!("WASM sandbox initialized");

        // Create WebSocket infrastructure
        let subscriptions = shared_subscriptions();
        let connections = shared_connection_manager(subscriptions);
        info!("WebSocket infrastructure initialized");

        // Create JWT service
        let jwt_service = Arc::new(JwtService::new(&config.jwt_secret));
        info!("JWT service initialized");

        Ok(Self {
            config,
            world,
            sandbox,
            connections,
            jwt_service,
        })
    }

    /// Get the server configuration.
    #[must_use]
    pub fn config(&self) -> &ServerConfig {
        &self.config
    }

    /// Get the shared ECS world.
    #[must_use]
    pub fn world(&self) -> &SharedWorld {
        &self.world
    }

    /// Get the WASM sandbox.
    #[must_use]
    pub fn sandbox(&self) -> &Arc<WasmSandbox> {
        &self.sandbox
    }

    /// Get the connection manager.
    #[must_use]
    pub fn connections(&self) -> &SharedConnectionManager {
        &self.connections
    }

    /// Create the application state for axum handlers.
    #[must_use]
    pub fn app_state(&self) -> SharedAppState {
        Arc::new(AppState::new(
            self.jwt_service.clone(),
            self.world.clone(),
            self.sandbox.clone(),
            self.connections.clone(),
        ))
    }

    /// Run the server.
    ///
    /// # Errors
    ///
    /// Returns an error if the server fails to start or bind to the address.
    pub async fn run(self) -> Result<()> {
        let state = self.app_state();
        let router = create_router(state);

        info!("Starting server on {}", self.config.bind_addr);

        ServerBuilder::new()
            .bind_addr(self.config.bind_addr)
            .cors(self.config.cors_enabled)
            .tracing(self.config.tracing_enabled)
            .router(router)
            .run_with_shutdown(stormstack_net::shutdown_signal())
            .await?;

        info!("Server shutdown complete");
        Ok(())
    }

    /// Run the server with a custom shutdown signal.
    ///
    /// # Errors
    ///
    /// Returns an error if the server fails to start.
    pub async fn run_with_shutdown<F>(self, shutdown: F) -> Result<()>
    where
        F: std::future::Future<Output = ()> + Send + 'static,
    {
        let state = self.app_state();
        let router = create_router(state);

        info!("Starting server on {}", self.config.bind_addr);

        ServerBuilder::new()
            .bind_addr(self.config.bind_addr)
            .cors(self.config.cors_enabled)
            .tracing(self.config.tracing_enabled)
            .router(router)
            .run_with_shutdown(shutdown)
            .await?;

        info!("Server shutdown complete");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_config() {
        let config = ServerConfig::default();
        assert_eq!(config.bind_addr.port(), 8080);
        assert!(config.cors_enabled);
        assert!(config.tracing_enabled);
    }

    #[test]
    fn config_with_port() {
        let config = ServerConfig::with_port(9090);
        assert_eq!(config.bind_addr.port(), 9090);
    }

    #[test]
    fn config_with_jwt_secret() {
        let config = ServerConfig::default().with_jwt_secret(b"my-secret-key-for-testing-32byte!");
        assert_eq!(config.jwt_secret.len(), 33);
    }

    #[test]
    fn server_creation() {
        let config = ServerConfig::default();
        let server = Server::new(config).expect("server creation");
        assert_eq!(server.config().bind_addr.port(), 8080);
    }

    #[test]
    fn server_components_accessible() {
        let server = Server::new(ServerConfig::default()).expect("server");

        // ECS world should be accessible
        let world = server.world();
        assert_eq!(world.read().entity_count(), 0);

        // Connection manager should be accessible
        assert_eq!(server.connections().connection_count(), 0);
    }

    #[test]
    fn app_state_creation() {
        let server = Server::new(ServerConfig::default()).expect("server");
        let _state = server.app_state();
    }
}
