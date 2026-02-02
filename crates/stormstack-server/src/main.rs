//! StormStack Game Server
//!
//! Main entry point for the StormStack server.

use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing
    tracing_subscriber::registry()
        .with(tracing_subscriber::fmt::layer())
        .with(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    tracing::info!("StormStack server starting...");

    // TODO: Initialize services
    // - WASM sandbox
    // - Auth service
    // - Container manager
    // - WebSocket handler
    // - HTTP server

    tracing::info!("StormStack server ready");

    // TODO: Run server
    // For now, just wait forever
    std::future::pending::<()>().await;

    Ok(())
}
