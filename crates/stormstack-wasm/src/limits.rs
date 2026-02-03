//! Resource limits for WASM execution.

use serde::{Deserialize, Serialize};

/// Resource limits for WASM execution.
///
/// These limits are critical for security - they prevent
/// malicious or buggy WASM modules from exhausting system resources.
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
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

    // =========================================================================
    // Casey's security tests - preset ordering and invariants
    // =========================================================================

    #[test]
    fn lightweight_is_more_constrained_than_default() {
        let lightweight = WasmResourceLimits::lightweight();
        let default = WasmResourceLimits::default();

        // All resource limits should be strictly less in lightweight
        assert!(
            lightweight.max_fuel < default.max_fuel,
            "lightweight fuel should be less than default"
        );
        assert!(
            lightweight.max_memory_bytes < default.max_memory_bytes,
            "lightweight memory should be less than default"
        );
        assert!(
            lightweight.epoch_deadline < default.epoch_deadline,
            "lightweight epoch should be less than default"
        );
        assert!(
            lightweight.max_stack_bytes < default.max_stack_bytes,
            "lightweight stack should be less than default"
        );
        assert!(
            lightweight.max_tables <= default.max_tables,
            "lightweight tables should be <= default"
        );
        assert!(
            lightweight.max_table_elements < default.max_table_elements,
            "lightweight table elements should be less than default"
        );
    }

    #[test]
    fn game_tick_is_more_generous_than_default() {
        let game_tick = WasmResourceLimits::game_tick();
        let default = WasmResourceLimits::default();

        // Game tick should allow more resources for complex game logic
        assert!(
            game_tick.max_fuel > default.max_fuel,
            "game_tick fuel should be greater than default"
        );
        assert!(
            game_tick.max_memory_bytes > default.max_memory_bytes,
            "game_tick memory should be greater than default"
        );
        assert!(
            game_tick.epoch_deadline > default.epoch_deadline,
            "game_tick epoch should be greater than default"
        );
        assert!(
            game_tick.max_stack_bytes > default.max_stack_bytes,
            "game_tick stack should be greater than default"
        );
    }

    #[test]
    fn preset_ordering_minimal_lightweight_default_game_tick() {
        let minimal = WasmResourceLimits::minimal();
        let lightweight = WasmResourceLimits::lightweight();
        let default = WasmResourceLimits::default();
        let game_tick = WasmResourceLimits::game_tick();

        // Fuel ordering: minimal < lightweight < default < game_tick
        assert!(minimal.max_fuel < lightweight.max_fuel);
        assert!(lightweight.max_fuel < default.max_fuel);
        assert!(default.max_fuel < game_tick.max_fuel);

        // Memory ordering
        assert!(minimal.max_memory_bytes < lightweight.max_memory_bytes);
        assert!(lightweight.max_memory_bytes < default.max_memory_bytes);
        assert!(default.max_memory_bytes < game_tick.max_memory_bytes);

        // Epoch ordering
        assert!(minimal.epoch_deadline < lightweight.epoch_deadline);
        assert!(lightweight.epoch_deadline < default.epoch_deadline);
        assert!(default.epoch_deadline < game_tick.epoch_deadline);
    }

    #[test]
    fn minimal_preset_values_are_truly_minimal() {
        let minimal = WasmResourceLimits::minimal();

        // Verify minimal is small enough for security testing
        assert!(minimal.max_fuel <= 1_000, "minimal fuel should be very small");
        assert!(
            minimal.max_memory_bytes <= 64 * 1024,
            "minimal memory should be <= 64KB"
        );
        assert!(minimal.epoch_deadline <= 10, "minimal epoch should be very short");
        assert!(minimal.max_tables == 1, "minimal should allow only 1 table");
        assert!(minimal.max_instances == 1, "minimal should allow only 1 instance");
        assert!(minimal.max_memories == 1, "minimal should allow only 1 memory");
    }

    #[test]
    fn all_presets_have_positive_limits() {
        for limits in [
            WasmResourceLimits::minimal(),
            WasmResourceLimits::lightweight(),
            WasmResourceLimits::default(),
            WasmResourceLimits::game_tick(),
        ] {
            assert!(limits.max_fuel > 0, "max_fuel must be positive");
            assert!(limits.max_memory_bytes > 0, "max_memory_bytes must be positive");
            assert!(limits.epoch_deadline > 0, "epoch_deadline must be positive");
            assert!(limits.max_stack_bytes > 0, "max_stack_bytes must be positive");
            assert!(limits.max_tables > 0, "max_tables must be positive");
            assert!(limits.max_table_elements > 0, "max_table_elements must be positive");
            assert!(limits.max_instances > 0, "max_instances must be positive");
            assert!(limits.max_memories > 0, "max_memories must be positive");
        }
    }

    #[test]
    fn limits_are_copy() {
        let original = WasmResourceLimits::default();
        let copied = original; // Copy, not move
        let _also_original = original; // Can still use original

        assert_eq!(copied.max_fuel, original.max_fuel);
    }

    // =========================================================================
    // Bailey's peer review improvements
    // =========================================================================

    #[test]
    fn all_presets_serialize_roundtrip() {
        // Security: all presets should survive JSON serialization without data loss
        let presets = [
            ("minimal", WasmResourceLimits::minimal()),
            ("lightweight", WasmResourceLimits::lightweight()),
            ("default", WasmResourceLimits::default()),
            ("game_tick", WasmResourceLimits::game_tick()),
        ];

        for (name, original) in presets {
            let json = serde_json::to_string(&original)
                .unwrap_or_else(|e| panic!("{} preset failed to serialize: {}", name, e));
            let parsed: WasmResourceLimits = serde_json::from_str(&json)
                .unwrap_or_else(|e| panic!("{} preset failed to deserialize: {}", name, e));

            assert_eq!(
                original.max_fuel, parsed.max_fuel,
                "{} max_fuel mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_memory_bytes, parsed.max_memory_bytes,
                "{} max_memory_bytes mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.epoch_deadline, parsed.epoch_deadline,
                "{} epoch_deadline mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_stack_bytes, parsed.max_stack_bytes,
                "{} max_stack_bytes mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_tables, parsed.max_tables,
                "{} max_tables mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_table_elements, parsed.max_table_elements,
                "{} max_table_elements mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_instances, parsed.max_instances,
                "{} max_instances mismatch after roundtrip",
                name
            );
            assert_eq!(
                original.max_memories, parsed.max_memories,
                "{} max_memories mismatch after roundtrip",
                name
            );
        }
    }

    #[test]
    fn preset_instances_and_memories_ordering() {
        // Security: verify max_instances and max_memories follow preset ordering
        let minimal = WasmResourceLimits::minimal();
        let lightweight = WasmResourceLimits::lightweight();
        let default = WasmResourceLimits::default();
        let game_tick = WasmResourceLimits::game_tick();

        // max_instances ordering: minimal <= lightweight <= default <= game_tick
        assert!(
            minimal.max_instances <= lightweight.max_instances,
            "minimal instances should be <= lightweight"
        );
        assert!(
            lightweight.max_instances <= default.max_instances,
            "lightweight instances should be <= default"
        );
        assert!(
            default.max_instances <= game_tick.max_instances,
            "default instances should be <= game_tick"
        );

        // max_memories: all presets use 1 (WASM limitation)
        assert_eq!(minimal.max_memories, 1, "minimal should have 1 memory");
        assert_eq!(lightweight.max_memories, 1, "lightweight should have 1 memory");
        assert_eq!(default.max_memories, 1, "default should have 1 memory");
        assert_eq!(game_tick.max_memories, 1, "game_tick should have 1 memory");
    }
}
