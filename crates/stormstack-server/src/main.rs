//! StormStack Game Server
//!
//! Main entry point for the StormStack server.

use stormstack_server::{Server, ServerConfig};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing
    tracing_subscriber::registry()
        .with(tracing_subscriber::fmt::layer())
        .with(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    tracing::info!("StormStack server starting...");

    // Load configuration from environment
    let config = ServerConfig::from_env();
    tracing::info!("Binding to {}", config.bind_addr);

    // Create and run server
    let server = Server::new(config)?;
    server.run().await?;

    Ok(())
}
