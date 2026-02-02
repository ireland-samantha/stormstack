//! State available to WASM host functions.

use stormstack_core::TenantId;

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

    /// Random number generator state (deterministic).
    pub rng_seed: u64,
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
            rng_seed: 0,
        }
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
}
