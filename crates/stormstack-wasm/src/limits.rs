//! Resource limits for WASM execution.

use serde::{Deserialize, Serialize};

/// Resource limits for WASM execution.
///
/// These limits are critical for security - they prevent
/// malicious or buggy WASM modules from exhausting system resources.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WasmResourceLimits {
    /// Maximum fuel (instructions) per execution.
    ///
    /// When fuel is exhausted, execution stops with `WasmError::FuelExhausted`.
    /// Default: 1,000,000 (~1M instructions)
    pub max_fuel: u64,

    /// Maximum memory in bytes.
    ///
    /// WASM modules cannot allocate more than this.
    /// Default: 16 MB
    pub max_memory_bytes: usize,

    /// Epoch deadline for timeout.
    ///
    /// Each epoch is ~10ms. This is a backup timeout mechanism
    /// in case fuel metering fails.
    /// Default: 100 epochs (~1 second)
    pub epoch_deadline: u64,

    /// Maximum stack size in bytes.
    ///
    /// Prevents stack overflow attacks.
    /// Default: 1 MB
    pub max_stack_bytes: usize,

    /// Maximum number of WASM tables.
    ///
    /// Default: 10
    pub max_tables: u32,

    /// Maximum elements per table.
    ///
    /// Default: 10,000
    pub max_table_elements: u32,

    /// Maximum number of WASM instances.
    ///
    /// Default: 10
    pub max_instances: u32,

    /// Maximum number of WASM memories.
    ///
    /// Default: 1
    pub max_memories: u32,
}

impl Default for WasmResourceLimits {
    fn default() -> Self {
        Self {
            max_fuel: 1_000_000,
            max_memory_bytes: 16 * 1024 * 1024, // 16 MB
            epoch_deadline: 100,                 // ~1 second
            max_stack_bytes: 1024 * 1024,        // 1 MB
            max_tables: 10,
            max_table_elements: 10_000,
            max_instances: 10,
            max_memories: 1,
        }
    }
}

impl WasmResourceLimits {
    /// Create limits suitable for quick, lightweight operations.
    #[must_use]
    pub fn lightweight() -> Self {
        Self {
            max_fuel: 100_000,
            max_memory_bytes: 1024 * 1024, // 1 MB
            epoch_deadline: 10,             // ~100ms
            max_stack_bytes: 256 * 1024,    // 256 KB
            max_tables: 2,
            max_table_elements: 1_000,
            max_instances: 2,
            max_memories: 1,
        }
    }

    /// Create limits suitable for long-running game logic.
    #[must_use]
    pub fn game_tick() -> Self {
        Self {
            max_fuel: 10_000_000,
            max_memory_bytes: 64 * 1024 * 1024, // 64 MB
            epoch_deadline: 500,                 // ~5 seconds
            max_stack_bytes: 2 * 1024 * 1024,    // 2 MB
            max_tables: 10,
            max_table_elements: 50_000,
            max_instances: 10,
            max_memories: 1,
        }
    }

    /// Create minimal limits for testing security boundaries.
    #[must_use]
    pub fn minimal() -> Self {
        Self {
            max_fuel: 1_000,
            max_memory_bytes: 64 * 1024, // 64 KB
            epoch_deadline: 2,            // ~20ms
            max_stack_bytes: 64 * 1024,   // 64 KB
            max_tables: 1,
            max_table_elements: 100,
            max_instances: 1,
            max_memories: 1,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_limits_reasonable() {
        let limits = WasmResourceLimits::default();
        assert!(limits.max_fuel > 0);
        assert!(limits.max_memory_bytes > 0);
        assert!(limits.epoch_deadline > 0);
    }

    #[test]
    fn minimal_limits_constrained() {
        let minimal = WasmResourceLimits::minimal();
        let default = WasmResourceLimits::default();

        assert!(minimal.max_fuel < default.max_fuel);
        assert!(minimal.max_memory_bytes < default.max_memory_bytes);
        assert!(minimal.epoch_deadline < default.epoch_deadline);
    }

    #[test]
    fn limits_serialize_roundtrip() {
        let limits = WasmResourceLimits::default();
        let json = serde_json::to_string(&limits).expect("serialize");
        let parsed: WasmResourceLimits = serde_json::from_str(&json).expect("deserialize");

        assert_eq!(limits.max_fuel, parsed.max_fuel);
        assert_eq!(limits.max_memory_bytes, parsed.max_memory_bytes);
    }
}
