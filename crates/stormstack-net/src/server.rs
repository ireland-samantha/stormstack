//! HTTP server setup.

use std::net::SocketAddr;

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

// TODO: Implement server builder and runner
