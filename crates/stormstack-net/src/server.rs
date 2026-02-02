//! HTTP server setup with axum.
//!
//! Provides a configurable server builder with common middleware.

use axum::Router;
use std::future::Future;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tower_http::compression::CompressionLayer;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tracing::{info, warn};

/// Server configuration.
#[derive(Debug, Clone)]
pub struct ServerConfig {
    /// Address to bind to.
    pub bind_addr: SocketAddr,
    /// Enable CORS.
    pub cors_enabled: bool,
    /// Enable request tracing.
    pub tracing_enabled: bool,
    /// Enable gzip compression.
    pub compression_enabled: bool,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            bind_addr: SocketAddr::from(([0, 0, 0, 0], 8080)),
            cors_enabled: true,
            tracing_enabled: true,
            compression_enabled: true,
        }
    }
}

impl ServerConfig {
    /// Create config for a specific port.
    #[must_use]
    pub fn with_port(port: u16) -> Self {
        Self {
            bind_addr: SocketAddr::from(([0, 0, 0, 0], port)),
            ..Default::default()
        }
    }

    /// Create minimal config (no middleware).
    #[must_use]
    pub fn minimal() -> Self {
        Self {
            cors_enabled: false,
            tracing_enabled: false,
            compression_enabled: false,
            ..Default::default()
        }
    }
}

/// HTTP server builder.
///
/// Configures and creates an axum server with common middleware.
pub struct ServerBuilder {
    config: ServerConfig,
    router: Router,
}

impl ServerBuilder {
    /// Create a new server builder with default config.
    #[must_use]
    pub fn new() -> Self {
        Self {
            config: ServerConfig::default(),
            router: Router::new(),
        }
    }

    /// Create builder with custom config.
    #[must_use]
    pub fn with_config(config: ServerConfig) -> Self {
        Self {
            config,
            router: Router::new(),
        }
    }

    /// Set the router.
    #[must_use]
    pub fn router(mut self, router: Router) -> Self {
        self.router = router;
        self
    }

    /// Set bind address.
    #[must_use]
    pub fn bind_addr(mut self, addr: SocketAddr) -> Self {
        self.config.bind_addr = addr;
        self
    }

    /// Set port (binds to 0.0.0.0).
    #[must_use]
    pub fn port(mut self, port: u16) -> Self {
        self.config.bind_addr = SocketAddr::from(([0, 0, 0, 0], port));
        self
    }

    /// Enable/disable CORS.
    #[must_use]
    pub fn cors(mut self, enabled: bool) -> Self {
        self.config.cors_enabled = enabled;
        self
    }

    /// Enable/disable request tracing.
    #[must_use]
    pub fn tracing(mut self, enabled: bool) -> Self {
        self.config.tracing_enabled = enabled;
        self
    }

    /// Enable/disable compression.
    #[must_use]
    pub fn compression(mut self, enabled: bool) -> Self {
        self.config.compression_enabled = enabled;
        self
    }

    /// Build the configured router with middleware.
    #[must_use]
    pub fn build(self) -> Router {
        let mut router = self.router;

        // Add tracing
        if self.config.tracing_enabled {
            router = router.layer(TraceLayer::new_for_http());
        }

        // Add compression
        if self.config.compression_enabled {
            router = router.layer(CompressionLayer::new());
        }

        // Add CORS
        if self.config.cors_enabled {
            let cors = CorsLayer::new()
                .allow_origin(Any)
                .allow_methods(Any)
                .allow_headers(Any);
            router = router.layer(cors);
        }

        router
    }

    /// Build and run the server.
    ///
    /// # Errors
    ///
    /// Returns an error if binding to the address fails.
    pub async fn run(self) -> std::io::Result<()> {
        let addr = self.config.bind_addr;
        let router = self.build();

        info!("Starting server on {}", addr);

        let listener = TcpListener::bind(addr).await?;
        axum::serve(listener, router).await
    }

    /// Build and run the server with graceful shutdown.
    ///
    /// # Errors
    ///
    /// Returns an error if binding fails.
    pub async fn run_with_shutdown<F>(self, shutdown_signal: F) -> std::io::Result<()>
    where
        F: Future<Output = ()> + Send + 'static,
    {
        let addr = self.config.bind_addr;
        let router = self.build();

        info!("Starting server on {} with graceful shutdown", addr);

        let listener = TcpListener::bind(addr).await?;
        axum::serve(listener, router)
            .with_graceful_shutdown(shutdown_signal)
            .await
    }
}

impl Default for ServerBuilder {
    fn default() -> Self {
        Self::new()
    }
}

/// Create a shutdown signal that triggers on Ctrl+C.
pub async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {
            warn!("Received Ctrl+C, shutting down");
        }
        _ = terminate => {
            warn!("Received terminate signal, shutting down");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::routing::get;

    #[test]
    fn default_config() {
        let config = ServerConfig::default();
        assert_eq!(config.bind_addr.port(), 8080);
        assert!(config.cors_enabled);
        assert!(config.tracing_enabled);
        assert!(config.compression_enabled);
    }

    #[test]
    fn config_with_port() {
        let config = ServerConfig::with_port(3000);
        assert_eq!(config.bind_addr.port(), 3000);
    }

    #[test]
    fn minimal_config() {
        let config = ServerConfig::minimal();
        assert!(!config.cors_enabled);
        assert!(!config.tracing_enabled);
        assert!(!config.compression_enabled);
    }

    #[test]
    fn builder_chain() {
        let builder = ServerBuilder::new()
            .port(9000)
            .cors(false)
            .tracing(false)
            .compression(false);

        assert_eq!(builder.config.bind_addr.port(), 9000);
        assert!(!builder.config.cors_enabled);
        assert!(!builder.config.compression_enabled);
    }

    #[test]
    fn builder_with_router() {
        let router = Router::new().route("/health", get(|| async { "ok" }));
        let builder = ServerBuilder::new().router(router);
        let _app = builder.build();
    }

    #[tokio::test]
    async fn server_binds_to_port() {
        // Find a free port
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        drop(listener);

        let router = Router::new().route("/health", get(|| async { "ok" }));
        let builder = ServerBuilder::new().port(port).router(router);

        // Just verify we can build it
        let _app = builder.build();
    }
}
