//! Game loop implementation for tick-based simulation.
//!
//! The game loop runs at a configurable tick rate, advancing all containers
//! and broadcasting world snapshots to subscribed WebSocket connections.
//!
//! # Example
//!
//! ```rust,ignore
//! use stormstack_server::{GameLoop, GameLoopConfig, shared_container_service};
//! use tokio_util::sync::CancellationToken;
//!
//! #[tokio::main]
//! async fn main() {
//!     let containers = shared_container_service();
//!     let config = GameLoopConfig::default(); // 60 ticks/sec
//!     let (game_loop, mut rx) = GameLoop::new(config, containers);
//!
//!     let shutdown = CancellationToken::new();
//!
//!     // Run the game loop in a background task
//!     let shutdown_clone = shutdown.clone();
//!     tokio::spawn(async move {
//!         game_loop.run(shutdown_clone).await;
//!     });
//!
//!     // Receive snapshots
//!     while let Ok((match_id, snapshot)) = rx.recv().await {
//!         println!("Match {:?} at tick {}", match_id, snapshot.tick);
//!     }
//! }
//! ```

use std::time::{Duration, Instant};
use tokio::sync::broadcast;
use tokio_util::sync::CancellationToken;
use tracing::{debug, error, trace, warn};

use stormstack_core::{MatchId, WorldSnapshot};
use stormstack_ecs::EcsWorld;

use crate::SharedContainerService;

/// Configuration for the game loop.
#[derive(Debug, Clone)]
pub struct GameLoopConfig {
    /// Target tick rate in ticks per second.
    ///
    /// Default is 60 ticks/sec (~16.67ms per tick).
    pub tick_rate: u32,

    /// Size of the broadcast channel for snapshots.
    ///
    /// Default is 256. If receivers fall behind by more than this,
    /// they will start missing snapshots.
    pub channel_capacity: usize,
}

impl Default for GameLoopConfig {
    fn default() -> Self {
        Self {
            tick_rate: 60,
            channel_capacity: 256,
        }
    }
}

impl GameLoopConfig {
    /// Create a config with a specific tick rate.
    #[must_use]
    pub fn with_tick_rate(mut self, tick_rate: u32) -> Self {
        self.tick_rate = tick_rate;
        self
    }

    /// Create a config with a specific channel capacity.
    #[must_use]
    pub fn with_channel_capacity(mut self, capacity: usize) -> Self {
        self.channel_capacity = capacity;
        self
    }

    /// Get the duration of a single tick.
    #[must_use]
    pub fn tick_duration(&self) -> Duration {
        Duration::from_secs_f64(1.0 / f64::from(self.tick_rate))
    }
}

/// The main game loop that drives the simulation.
///
/// The game loop:
/// 1. Runs at a configurable tick rate (default 60 ticks/sec)
/// 2. Iterates through all containers and calls `tick(delta_time)`
/// 3. For each active match, generates a snapshot
/// 4. Broadcasts snapshots to all subscribed receivers
/// 5. Supports graceful shutdown via `CancellationToken`
pub struct GameLoop {
    config: GameLoopConfig,
    containers: SharedContainerService,
    snapshot_tx: broadcast::Sender<(MatchId, WorldSnapshot)>,
}

impl GameLoop {
    /// Create a new game loop with the given configuration.
    ///
    /// Returns the game loop and a receiver for snapshots.
    ///
    /// # Arguments
    ///
    /// * `config` - Game loop configuration
    /// * `containers` - Shared container service to tick
    #[must_use]
    pub fn new(
        config: GameLoopConfig,
        containers: SharedContainerService,
    ) -> (Self, broadcast::Receiver<(MatchId, WorldSnapshot)>) {
        let (snapshot_tx, snapshot_rx) = broadcast::channel(config.channel_capacity);

        let game_loop = Self {
            config,
            containers,
            snapshot_tx,
        };

        (game_loop, snapshot_rx)
    }

    /// Subscribe to snapshot broadcasts.
    ///
    /// Returns a new receiver that will receive all future snapshots.
    #[must_use]
    pub fn subscribe(&self) -> broadcast::Receiver<(MatchId, WorldSnapshot)> {
        self.snapshot_tx.subscribe()
    }

    /// Get the game loop configuration.
    #[must_use]
    pub fn config(&self) -> &GameLoopConfig {
        &self.config
    }

    /// Get the number of active subscribers.
    #[must_use]
    pub fn subscriber_count(&self) -> usize {
        self.snapshot_tx.receiver_count()
    }

    /// Run the game loop until shutdown is signaled.
    ///
    /// This method will block until the cancellation token is cancelled.
    ///
    /// # Arguments
    ///
    /// * `shutdown` - Cancellation token to signal graceful shutdown
    pub async fn run(&self, shutdown: CancellationToken) {
        let tick_duration = self.config.tick_duration();
        let mut interval = tokio::time::interval(tick_duration);
        interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);

        debug!(
            "Game loop starting with tick rate {} Hz ({}ms per tick)",
            self.config.tick_rate,
            tick_duration.as_millis()
        );

        let mut last_tick = Instant::now();
        let mut tick_count: u64 = 0;

        loop {
            tokio::select! {
                biased;

                () = shutdown.cancelled() => {
                    debug!("Game loop received shutdown signal after {} ticks", tick_count);
                    break;
                }

                _ = interval.tick() => {
                    let now = Instant::now();
                    let delta_time = now.duration_since(last_tick).as_secs_f64();
                    last_tick = now;

                    self.tick(delta_time).await;
                    tick_count += 1;

                    trace!("Game loop tick {} completed (dt: {:.4}s)", tick_count, delta_time);
                }
            }
        }

        debug!("Game loop shut down gracefully");
    }

    /// Execute a single tick of the game loop.
    ///
    /// This method:
    /// 1. Ticks all containers with the given delta time
    /// 2. Generates snapshots for all active matches
    /// 3. Broadcasts snapshots to subscribers
    async fn tick(&self, delta_time: f64) {
        // Tick all containers
        if let Err(e) = self.containers.tick_all(delta_time) {
            error!("Failed to tick containers: {}", e);
            return;
        }

        // Generate and broadcast snapshots for active matches
        self.broadcast_snapshots();
    }

    /// Generate and broadcast snapshots for all active matches.
    fn broadcast_snapshots(&self) {
        // Skip if no subscribers
        if self.snapshot_tx.receiver_count() == 0 {
            return;
        }

        // Iterate through all containers
        for container in self.containers.all_containers() {

            // Get active matches
            let active_matches = container.active_matches();

            for match_summary in active_matches {
                // Generate snapshot from the container's world
                let snapshot = {
                    let world = container.world().read();
                    match world.snapshot() {
                        Ok(s) => s,
                        Err(e) => {
                            warn!(
                                "Failed to generate snapshot for match {:?}: {}",
                                match_summary.id, e
                            );
                            continue;
                        }
                    }
                };

                // Broadcast the snapshot
                if let Err(e) = self.snapshot_tx.send((match_summary.id, snapshot)) {
                    trace!(
                        "No receivers for snapshot (match {:?}): {}",
                        match_summary.id,
                        e
                    );
                }
            }
        }
    }
}

impl std::fmt::Debug for GameLoop {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("GameLoop")
            .field("config", &self.config)
            .field("subscriber_count", &self.subscriber_count())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::shared_container_service;
    use std::time::Duration;
    use stormstack_core::{MatchConfig, TenantId};

    #[tokio::test]
    async fn game_loop_ticks_containers() {
        let containers = shared_container_service();
        let tenant_id = TenantId::new();

        // Create a container with an active match
        let container_id = containers.create_container(tenant_id);
        let container = containers.get_container(container_id).expect("container");
        let match_id = container.create_match(MatchConfig::default()).expect("create match");
        container.start_match(match_id).expect("start match");

        // Verify initial state
        {
            let match_ref = container.get_match(match_id).expect("match");
            assert_eq!(match_ref.current_tick, 0);
        }

        let config = GameLoopConfig::default().with_tick_rate(100); // 100 Hz for faster testing
        let (game_loop, _rx) = GameLoop::new(config, containers.clone());
        let shutdown = CancellationToken::new();

        // Run game loop for a short time
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        // Wait for some ticks
        tokio::time::sleep(Duration::from_millis(50)).await;

        // Signal shutdown
        shutdown.cancel();
        loop_handle.await.expect("join");

        // Verify ticks occurred
        {
            let match_ref = container.get_match(match_id).expect("match");
            assert!(match_ref.current_tick > 0, "Match should have ticked");
        }

        // Container world should also have advanced
        assert!(container.current_tick() > 0, "World should have ticked");
    }

    #[tokio::test]
    async fn game_loop_respects_tick_rate() {
        let containers = shared_container_service();
        let tenant_id = TenantId::new();

        // Create a container
        let container_id = containers.create_container(tenant_id);
        let container = containers.get_container(container_id).expect("container");

        // Use a low tick rate for easier measurement
        let tick_rate = 20; // 20 Hz = 50ms per tick
        let config = GameLoopConfig::default().with_tick_rate(tick_rate);
        let (game_loop, _rx) = GameLoop::new(config, containers.clone());
        let shutdown = CancellationToken::new();

        let start = Instant::now();

        // Run game loop for a controlled time
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        // Run for ~100ms, expect ~2 ticks
        tokio::time::sleep(Duration::from_millis(100)).await;
        shutdown.cancel();
        loop_handle.await.expect("join");

        let elapsed = start.elapsed();
        let ticks = container.current_tick();

        // With 20 Hz and 100ms, we expect approximately 2 ticks
        // Allow some margin for timing variations
        assert!(
            ticks >= 1 && ticks <= 4,
            "Expected 1-4 ticks in 100ms at 20 Hz, got {}",
            ticks
        );

        // Verify we didn't run significantly faster than expected
        let expected_duration = Duration::from_millis((ticks * 50) as u64);
        assert!(
            elapsed >= expected_duration.saturating_sub(Duration::from_millis(20)),
            "Game loop ran too fast: {:?} elapsed for {} ticks",
            elapsed,
            ticks
        );
    }

    #[tokio::test]
    async fn game_loop_shuts_down_gracefully() {
        let containers = shared_container_service();
        let config = GameLoopConfig::default().with_tick_rate(100);
        let (game_loop, _rx) = GameLoop::new(config, containers);
        let shutdown = CancellationToken::new();

        // Start the loop
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        // Give it a moment to start
        tokio::time::sleep(Duration::from_millis(10)).await;

        // Cancel and verify it shuts down quickly
        shutdown.cancel();

        let result = tokio::time::timeout(Duration::from_millis(100), loop_handle).await;

        assert!(
            result.is_ok(),
            "Game loop should shut down within 100ms of cancellation"
        );
        assert!(result.expect("join").is_ok(), "Game loop should not panic");
    }

    #[tokio::test]
    async fn game_loop_broadcasts_snapshots() {
        let containers = shared_container_service();
        let tenant_id = TenantId::new();

        // Create a container with an active match
        let container_id = containers.create_container(tenant_id);
        let container = containers.get_container(container_id).expect("container");
        let match_id = container.create_match(MatchConfig::default()).expect("create match");
        container.start_match(match_id).expect("start match");

        let config = GameLoopConfig::default().with_tick_rate(100);
        let (game_loop, mut rx) = GameLoop::new(config, containers.clone());
        let shutdown = CancellationToken::new();

        // Start the game loop
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        // Try to receive a snapshot with timeout
        let snapshot_result =
            tokio::time::timeout(Duration::from_millis(200), rx.recv()).await;

        // Shutdown
        shutdown.cancel();
        loop_handle.await.expect("join");

        // Verify we received a snapshot
        assert!(
            snapshot_result.is_ok(),
            "Should receive snapshot within timeout"
        );

        let (received_match_id, snapshot) = snapshot_result
            .expect("timeout")
            .expect("recv");

        assert_eq!(received_match_id, match_id);
        // Verify we got a valid snapshot (tick is u64, so always >= 0)
        let _ = snapshot.tick;
    }

    #[test]
    fn game_loop_config_default() {
        let config = GameLoopConfig::default();
        assert_eq!(config.tick_rate, 60);
        assert_eq!(config.channel_capacity, 256);
    }

    #[test]
    fn game_loop_config_tick_duration() {
        let config = GameLoopConfig::default();
        let duration = config.tick_duration();

        // 60 Hz = ~16.67ms
        assert!(duration.as_millis() >= 16);
        assert!(duration.as_millis() <= 17);
    }

    #[test]
    fn game_loop_config_with_tick_rate() {
        let config = GameLoopConfig::default().with_tick_rate(120);
        assert_eq!(config.tick_rate, 120);

        let duration = config.tick_duration();
        // 120 Hz = ~8.33ms
        assert!(duration.as_millis() >= 8);
        assert!(duration.as_millis() <= 9);
    }

    #[test]
    fn game_loop_config_with_channel_capacity() {
        let config = GameLoopConfig::default().with_channel_capacity(512);
        assert_eq!(config.channel_capacity, 512);
    }

    #[test]
    fn game_loop_subscribe_creates_receiver() {
        let containers = shared_container_service();
        let config = GameLoopConfig::default();
        let (game_loop, _rx) = GameLoop::new(config, containers);

        // Initial receiver count includes the one returned from new()
        assert_eq!(game_loop.subscriber_count(), 1);

        let _rx2 = game_loop.subscribe();
        assert_eq!(game_loop.subscriber_count(), 2);

        let _rx3 = game_loop.subscribe();
        assert_eq!(game_loop.subscriber_count(), 3);
    }

    #[test]
    fn game_loop_debug_format() {
        let containers = shared_container_service();
        let config = GameLoopConfig::default();
        let (game_loop, _rx) = GameLoop::new(config, containers);

        let debug_str = format!("{:?}", game_loop);
        assert!(debug_str.contains("GameLoop"));
        assert!(debug_str.contains("config"));
        assert!(debug_str.contains("subscriber_count"));
    }

    #[tokio::test]
    async fn game_loop_no_snapshots_without_active_matches() {
        let containers = shared_container_service();
        let tenant_id = TenantId::new();

        // Create a container with a pending match (not started)
        let container_id = containers.create_container(tenant_id);
        let container = containers.get_container(container_id).expect("container");
        let _match_id = container.create_match(MatchConfig::default()).expect("create match");
        // Don't start the match - leave it pending

        let config = GameLoopConfig::default().with_tick_rate(100);
        let (game_loop, mut rx) = GameLoop::new(config, containers);
        let shutdown = CancellationToken::new();

        // Start the game loop
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        // Try to receive - should timeout since no active matches
        let snapshot_result =
            tokio::time::timeout(Duration::from_millis(50), rx.recv()).await;

        shutdown.cancel();
        loop_handle.await.expect("join");

        // Should timeout because no active matches to generate snapshots for
        assert!(
            snapshot_result.is_err(),
            "Should not receive snapshots for pending matches"
        );
    }

    #[tokio::test]
    async fn game_loop_multiple_containers() {
        let containers = shared_container_service();
        let tenant1 = TenantId::new();
        let tenant2 = TenantId::new();

        // Create two containers with active matches
        let c1_id = containers.create_container(tenant1);
        let c1 = containers.get_container(c1_id).expect("container");
        let m1_id = c1.create_match(MatchConfig::default()).expect("create");
        c1.start_match(m1_id).expect("start");

        let c2_id = containers.create_container(tenant2);
        let c2 = containers.get_container(c2_id).expect("container");
        let m2_id = c2.create_match(MatchConfig::default()).expect("create");
        c2.start_match(m2_id).expect("start");

        let config = GameLoopConfig::default().with_tick_rate(100);
        let (game_loop, _rx) = GameLoop::new(config, containers.clone());
        let shutdown = CancellationToken::new();

        // Run briefly
        let shutdown_clone = shutdown.clone();
        let loop_handle = tokio::spawn(async move {
            game_loop.run(shutdown_clone).await;
        });

        tokio::time::sleep(Duration::from_millis(50)).await;
        shutdown.cancel();
        loop_handle.await.expect("join");

        // Both containers should have been ticked
        assert!(c1.current_tick() > 0, "Container 1 should have ticked");
        assert!(c2.current_tick() > 0, "Container 2 should have ticked");
    }
}
