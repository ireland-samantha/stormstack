//! State available to WASM host functions.

use parking_lot::RwLock;
use rand::{Rng, SeedableRng};
use rand::rngs::SmallRng;
use std::sync::Arc;
use stormstack_core::TenantId;
use stormstack_ecs::StormWorld;

/// State available to WASM host functions during execution.
///
/// This struct is passed to the wasmtime Store and is accessible
/// from within host function implementations.
pub struct WasmState {
    /// Tenant context for the executing module.
    pub tenant_id: TenantId,

    /// Current game tick.
    pub current_tick: u64,

    /// Delta time for current tick (seconds).
    pub delta_time: f64,

    /// Log buffer for module output.
    pub log_buffer: Vec<LogEntry>,

    /// Rate limit counters.
    pub rate_limits: RateLimits,

    /// Deterministic random number generator.
    pub rng: SmallRng,

    /// Reference to the ECS world (optional during tests).
    pub world: Option<Arc<RwLock<StormWorld>>>,
}

/// Log entry from WASM module.
#[derive(Debug, Clone)]
pub struct LogEntry {
    /// Log level.
    pub level: LogLevel,
    /// Log message.
    pub message: String,
    /// Tick when logged.
    pub tick: u64,
}

/// Log level for WASM module output.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevel {
    /// Debug level.
    Debug,
    /// Info level.
    Info,
    /// Warning level.
    Warn,
    /// Error level.
    Error,
}

/// Rate limit counters for host functions.
#[derive(Debug, Default)]
pub struct RateLimits {
    /// Log calls this tick.
    pub log_calls: u32,
    /// Entity spawn calls this tick.
    pub spawn_calls: u32,
}

impl RateLimits {
    /// Maximum log calls per tick.
    pub const MAX_LOG_CALLS: u32 = 100;
    /// Maximum spawn calls per tick.
    pub const MAX_SPAWN_CALLS: u32 = 100;

    /// Reset all counters (called at start of each tick).
    pub fn reset(&mut self) {
        self.log_calls = 0;
        self.spawn_calls = 0;
    }

    /// Check if log rate limit exceeded.
    #[must_use]
    pub fn can_log(&self) -> bool {
        self.log_calls < Self::MAX_LOG_CALLS
    }

    /// Check if spawn rate limit exceeded.
    #[must_use]
    pub fn can_spawn(&self) -> bool {
        self.spawn_calls < Self::MAX_SPAWN_CALLS
    }
}

impl WasmState {
    /// Create new state for a tenant.
    #[must_use]
    pub fn new(tenant_id: TenantId) -> Self {
        Self {
            tenant_id,
            current_tick: 0,
            delta_time: 0.0,
            log_buffer: Vec::new(),
            rate_limits: RateLimits::default(),
            rng: SmallRng::seed_from_u64(0),
            world: None,
        }
    }

    /// Create state with an ECS world reference.
    #[must_use]
    pub fn with_world(tenant_id: TenantId, world: Arc<RwLock<StormWorld>>) -> Self {
        Self {
            tenant_id,
            current_tick: 0,
            delta_time: 0.0,
            log_buffer: Vec::new(),
            rate_limits: RateLimits::default(),
            rng: SmallRng::seed_from_u64(0),
            world: Some(world),
        }
    }

    /// Set the RNG seed for deterministic replay.
    pub fn set_rng_seed(&mut self, seed: u64) {
        self.rng = SmallRng::seed_from_u64(seed);
    }

    /// Prepare state for a new tick.
    pub fn begin_tick(&mut self, tick: u64, delta_time: f64) {
        self.current_tick = tick;
        self.delta_time = delta_time;
        self.rate_limits.reset();
    }

    /// Drain log buffer after tick.
    pub fn drain_logs(&mut self) -> Vec<LogEntry> {
        std::mem::take(&mut self.log_buffer)
    }

    /// Generate next random u32.
    pub fn random_u32(&mut self) -> u32 {
        self.rng.random()
    }

    /// Generate next random f32 in [0, 1).
    pub fn random_f32(&mut self) -> f32 {
        self.rng.random()
    }

    /// Generate random i32 in [min, max].
    pub fn random_range(&mut self, min: i32, max: i32) -> i32 {
        if min > max {
            min
        } else {
            self.rng.random_range(min..=max)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rate_limits_reset() {
        let mut limits = RateLimits {
            log_calls: 50,
            spawn_calls: 30,
        };
        limits.reset();
        assert_eq!(limits.log_calls, 0);
        assert_eq!(limits.spawn_calls, 0);
    }

    #[test]
    fn rate_limits_enforcement() {
        let mut limits = RateLimits::default();
        assert!(limits.can_log());

        limits.log_calls = RateLimits::MAX_LOG_CALLS;
        assert!(!limits.can_log());
    }

    #[test]
    fn state_begin_tick() {
        let mut state = WasmState::new(TenantId::new());
        state.rate_limits.log_calls = 50;

        state.begin_tick(100, 0.016);

        assert_eq!(state.current_tick, 100);
        assert!((state.delta_time - 0.016).abs() < f64::EPSILON);
        assert_eq!(state.rate_limits.log_calls, 0);
    }

    #[test]
    fn deterministic_rng() {
        let mut state1 = WasmState::new(TenantId::new());
        let mut state2 = WasmState::new(TenantId::new());

        state1.set_rng_seed(42);
        state2.set_rng_seed(42);

        for _ in 0..100 {
            assert_eq!(state1.random_u32(), state2.random_u32());
        }
    }

    #[test]
    fn random_range_bounds() {
        let mut state = WasmState::new(TenantId::new());

        for _ in 0..100 {
            let val = state.random_range(10, 20);
            assert!(val >= 10 && val <= 20);
        }
    }

    #[test]
    fn random_range_inverted() {
        let mut state = WasmState::new(TenantId::new());
        // When min > max, returns min
        let val = state.random_range(20, 10);
        assert_eq!(val, 20);
    }

    // =========================================================================
    // Casey's security tests - rate limits and drain_logs
    // =========================================================================

    #[test]
    fn rate_limits_spawn_enforcement() {
        let mut limits = RateLimits::default();
        assert!(limits.can_spawn());

        limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS;
        assert!(!limits.can_spawn());
    }

    #[test]
    fn rate_limits_just_under_max_allows() {
        let mut limits = RateLimits::default();

        limits.log_calls = RateLimits::MAX_LOG_CALLS - 1;
        assert!(limits.can_log(), "one under max should still allow");

        limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS - 1;
        assert!(limits.can_spawn(), "one under max should still allow");
    }

    #[test]
    fn rate_limits_constants_are_reasonable() {
        // Security: limits should not be too high (DoS) or too low (unusable)
        assert!(
            RateLimits::MAX_LOG_CALLS >= 10,
            "log limit should allow at least 10 calls per tick"
        );
        assert!(
            RateLimits::MAX_LOG_CALLS <= 1000,
            "log limit should not be excessively high"
        );
        assert!(
            RateLimits::MAX_SPAWN_CALLS >= 10,
            "spawn limit should allow at least 10 calls per tick"
        );
        assert!(
            RateLimits::MAX_SPAWN_CALLS <= 1000,
            "spawn limit should not be excessively high"
        );
    }

    #[test]
    fn drain_logs_returns_all_and_clears() {
        let mut state = WasmState::new(TenantId::new());

        // Add some log entries
        state.log_buffer.push(LogEntry {
            level: LogLevel::Info,
            message: "first".to_string(),
            tick: 0,
        });
        state.log_buffer.push(LogEntry {
            level: LogLevel::Debug,
            message: "second".to_string(),
            tick: 1,
        });
        state.log_buffer.push(LogEntry {
            level: LogLevel::Error,
            message: "third".to_string(),
            tick: 2,
        });

        assert_eq!(state.log_buffer.len(), 3);

        // Drain should return all entries
        let drained = state.drain_logs();
        assert_eq!(drained.len(), 3);
        assert_eq!(drained[0].message, "first");
        assert_eq!(drained[1].message, "second");
        assert_eq!(drained[2].message, "third");

        // Buffer should now be empty
        assert!(state.log_buffer.is_empty());

        // Second drain should return empty
        let drained_again = state.drain_logs();
        assert!(drained_again.is_empty());
    }

    #[test]
    fn drain_logs_empty_buffer_returns_empty() {
        let mut state = WasmState::new(TenantId::new());
        let drained = state.drain_logs();
        assert!(drained.is_empty());
    }

    #[test]
    fn random_f32_bounds() {
        let mut state = WasmState::new(TenantId::new());
        state.set_rng_seed(12345);

        // Test 1000 samples - all should be in [0, 1)
        for _ in 0..1000 {
            let val = state.random_f32();
            assert!(val >= 0.0, "random_f32 should be >= 0, got {}", val);
            assert!(val < 1.0, "random_f32 should be < 1, got {}", val);
        }
    }

    #[test]
    fn new_state_has_default_values() {
        let tenant_id = TenantId::new();
        let state = WasmState::new(tenant_id);

        assert_eq!(state.current_tick, 0);
        assert!((state.delta_time - 0.0).abs() < f64::EPSILON);
        assert!(state.log_buffer.is_empty());
        assert_eq!(state.rate_limits.log_calls, 0);
        assert_eq!(state.rate_limits.spawn_calls, 0);
        assert!(state.world.is_none());
    }

    #[test]
    fn state_with_world_has_world_reference() {
        let tenant_id = TenantId::new();
        let world = Arc::new(RwLock::new(StormWorld::new()));
        let state = WasmState::with_world(tenant_id, world.clone());

        assert!(state.world.is_some());

        // Verify we can access the world
        let world_ref = state.world.as_ref().unwrap();
        let w = world_ref.read();
        assert_eq!(w.entity_count(), 0);
    }

    #[test]
    fn log_level_equality() {
        assert_eq!(LogLevel::Debug, LogLevel::Debug);
        assert_eq!(LogLevel::Info, LogLevel::Info);
        assert_eq!(LogLevel::Warn, LogLevel::Warn);
        assert_eq!(LogLevel::Error, LogLevel::Error);
        assert_ne!(LogLevel::Debug, LogLevel::Info);
        assert_ne!(LogLevel::Warn, LogLevel::Error);
    }

    #[test]
    fn log_entry_clone() {
        let entry = LogEntry {
            level: LogLevel::Info,
            message: "test message".to_string(),
            tick: 42,
        };

        let cloned = entry.clone();
        assert_eq!(cloned.level, entry.level);
        assert_eq!(cloned.message, entry.message);
        assert_eq!(cloned.tick, entry.tick);
    }
}
