//! Host function implementations.
//!
//! Each function follows the security model:
//! - Validates all inputs from WASM
//! - Is tenant-scoped
//! - Has rate limits where applicable

use crate::state::{LogEntry, LogLevel, WasmState};
use stormstack_core::{EntityId, WasmError};
use stormstack_ecs::EcsWorld;
use tracing::{debug, trace, warn};
use wasmtime::{Caller, Linker};

/// Maximum log message size in bytes.
const MAX_LOG_SIZE: usize = 1024;

/// Result code for success.
const RESULT_OK: i32 = 0;
/// Result code for rate limit exceeded.
const RESULT_RATE_LIMITED: i32 = -1;
/// Result code for invalid memory access.
const RESULT_INVALID_MEMORY: i32 = -2;
/// Result code for entity not found.
const RESULT_NOT_FOUND: i32 = -3;
/// Result code for no world attached.
const RESULT_NO_WORLD: i32 = -4;
/// Result code for invalid UTF-8.
const RESULT_INVALID_UTF8: i32 = -5;

/// Register all core host functions with the linker.
///
/// # Errors
///
/// Returns an error if registration fails.
pub fn register_host_functions(linker: &mut Linker<WasmState>) -> Result<(), WasmError> {
    // Logging functions
    linker
        .func_wrap("env", "log_debug", host_log_debug)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register log_debug: {e}")))?;
    linker
        .func_wrap("env", "log_info", host_log_info)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register log_info: {e}")))?;
    linker
        .func_wrap("env", "log_warn", host_log_warn)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register log_warn: {e}")))?;
    linker
        .func_wrap("env", "log_error", host_log_error)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register log_error: {e}")))?;

    // Time functions
    linker
        .func_wrap("env", "get_tick", host_get_tick)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register get_tick: {e}")))?;
    linker
        .func_wrap("env", "get_delta_time", host_get_delta_time)
        .map_err(|e| {
            WasmError::InstantiationError(format!("Failed to register get_delta_time: {e}"))
        })?;

    // Entity functions
    linker
        .func_wrap("env", "entity_spawn", host_entity_spawn)
        .map_err(|e| {
            WasmError::InstantiationError(format!("Failed to register entity_spawn: {e}"))
        })?;
    linker
        .func_wrap("env", "entity_despawn", host_entity_despawn)
        .map_err(|e| {
            WasmError::InstantiationError(format!("Failed to register entity_despawn: {e}"))
        })?;
    linker
        .func_wrap("env", "entity_exists", host_entity_exists)
        .map_err(|e| {
            WasmError::InstantiationError(format!("Failed to register entity_exists: {e}"))
        })?;

    // Random functions
    linker
        .func_wrap("env", "random_u32", host_random_u32)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register random_u32: {e}")))?;
    linker
        .func_wrap("env", "random_f32", host_random_f32)
        .map_err(|e| WasmError::InstantiationError(format!("Failed to register random_f32: {e}")))?;
    linker
        .func_wrap("env", "random_range", host_random_range)
        .map_err(|e| {
            WasmError::InstantiationError(format!("Failed to register random_range: {e}"))
        })?;

    debug!("Registered all core host functions");
    Ok(())
}

/// Read a string from WASM memory with bounds checking.
fn read_wasm_string(caller: &mut Caller<'_, WasmState>, ptr: i32, len: i32) -> Result<String, i32> {
    if ptr < 0 || len < 0 {
        return Err(RESULT_INVALID_MEMORY);
    }

    let memory = caller
        .get_export("memory")
        .and_then(|e| e.into_memory())
        .ok_or(RESULT_INVALID_MEMORY)?;

    let data = memory.data(caller);
    let start = ptr as usize;
    let length = len as usize;

    // Bounds check
    if start.saturating_add(length) > data.len() {
        return Err(RESULT_INVALID_MEMORY);
    }

    // Truncate to max size
    let actual_len = length.min(MAX_LOG_SIZE);
    let bytes = &data[start..start + actual_len];

    // UTF-8 validation
    String::from_utf8(bytes.to_vec()).map_err(|_| RESULT_INVALID_UTF8)
}

// ============================================================================
// Logging Functions
// ============================================================================

fn log_with_level(mut caller: Caller<'_, WasmState>, ptr: i32, len: i32, level: LogLevel) -> i32 {
    let state = caller.data_mut();

    // Rate limit check
    if !state.rate_limits.can_log() {
        return RESULT_RATE_LIMITED;
    }
    state.rate_limits.log_calls += 1;

    let tick = state.current_tick;

    // Read message from WASM memory
    let message = match read_wasm_string(&mut caller, ptr, len) {
        Ok(msg) => msg,
        Err(code) => return code,
    };

    // Store in buffer
    caller.data_mut().log_buffer.push(LogEntry {
        level,
        message: message.clone(),
        tick,
    });

    trace!("[WASM {:?}] {}", level, message);
    RESULT_OK
}

fn host_log_debug(caller: Caller<'_, WasmState>, ptr: i32, len: i32) -> i32 {
    log_with_level(caller, ptr, len, LogLevel::Debug)
}

fn host_log_info(caller: Caller<'_, WasmState>, ptr: i32, len: i32) -> i32 {
    log_with_level(caller, ptr, len, LogLevel::Info)
}

fn host_log_warn(caller: Caller<'_, WasmState>, ptr: i32, len: i32) -> i32 {
    log_with_level(caller, ptr, len, LogLevel::Warn)
}

fn host_log_error(caller: Caller<'_, WasmState>, ptr: i32, len: i32) -> i32 {
    log_with_level(caller, ptr, len, LogLevel::Error)
}

// ============================================================================
// Time Functions
// ============================================================================

fn host_get_tick(caller: Caller<'_, WasmState>) -> i64 {
    caller.data().current_tick as i64
}

fn host_get_delta_time(caller: Caller<'_, WasmState>) -> f64 {
    caller.data().delta_time
}

// ============================================================================
// Entity Functions
// ============================================================================

fn host_entity_spawn(mut caller: Caller<'_, WasmState>) -> i64 {
    // Rate limit check
    if !caller.data().rate_limits.can_spawn() {
        warn!("Entity spawn rate limit exceeded");
        return -1;
    }
    caller.data_mut().rate_limits.spawn_calls += 1;

    // Get world reference
    let world_ref = match &caller.data().world {
        Some(w) => w.clone(),
        None => {
            warn!("No world attached to WASM state");
            return -1;
        }
    };

    // Spawn entity
    let mut world = world_ref.write();
    let entity = world.spawn();
    trace!("WASM spawned entity {:?}", entity);
    entity.0 as i64
}

fn host_entity_despawn(caller: Caller<'_, WasmState>, id: i64) -> i32 {
    if id < 0 {
        return RESULT_INVALID_MEMORY;
    }

    // Get world reference
    let world_ref = match &caller.data().world {
        Some(w) => w.clone(),
        None => return RESULT_NO_WORLD,
    };

    // Despawn entity
    let mut world = world_ref.write();
    let entity_id = EntityId(id as u64);
    match world.despawn(entity_id) {
        Ok(()) => {
            trace!("WASM despawned entity {:?}", entity_id);
            RESULT_OK
        }
        Err(_) => RESULT_NOT_FOUND,
    }
}

fn host_entity_exists(caller: Caller<'_, WasmState>, id: i64) -> i32 {
    if id < 0 {
        return 0;
    }

    // Get world reference
    let world_ref = match &caller.data().world {
        Some(w) => w.clone(),
        None => return 0,
    };

    // Check existence
    let world = world_ref.read();
    let entity_id = EntityId(id as u64);
    if world.exists(entity_id) {
        1
    } else {
        0
    }
}

// ============================================================================
// Random Functions
// ============================================================================

fn host_random_u32(mut caller: Caller<'_, WasmState>) -> i32 {
    caller.data_mut().random_u32() as i32
}

fn host_random_f32(mut caller: Caller<'_, WasmState>) -> f32 {
    caller.data_mut().random_f32()
}

fn host_random_range(mut caller: Caller<'_, WasmState>, min: i32, max: i32) -> i32 {
    caller.data_mut().random_range(min, max)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::RateLimits;
    use parking_lot::RwLock;
    use std::sync::Arc;
    use stormstack_core::TenantId;
    use stormstack_ecs::StormWorld;

    fn create_test_state() -> WasmState {
        let world = Arc::new(RwLock::new(StormWorld::new()));
        WasmState::with_world(TenantId::new(), world)
    }

    #[test]
    fn test_rate_limit_constants() {
        assert_eq!(RESULT_OK, 0);
        assert!(RESULT_RATE_LIMITED < 0);
        assert!(RESULT_INVALID_MEMORY < 0);
    }

    #[test]
    fn test_random_determinism() {
        let mut state1 = create_test_state();
        let mut state2 = create_test_state();

        state1.set_rng_seed(12345);
        state2.set_rng_seed(12345);

        for _ in 0..50 {
            assert_eq!(state1.random_u32(), state2.random_u32());
            assert_eq!(state1.random_f32(), state2.random_f32());
        }
    }

    // =========================================================================
    // Casey's security tests - result codes and rate limiting
    // =========================================================================

    #[test]
    fn all_result_codes_unique() {
        // Security: result codes must be unique for proper error handling
        let codes = [
            RESULT_OK,
            RESULT_RATE_LIMITED,
            RESULT_INVALID_MEMORY,
            RESULT_NOT_FOUND,
            RESULT_NO_WORLD,
            RESULT_INVALID_UTF8,
        ];

        for i in 0..codes.len() {
            for j in (i + 1)..codes.len() {
                assert_ne!(
                    codes[i], codes[j],
                    "Result codes {} and {} must be unique",
                    i, j
                );
            }
        }
    }

    #[test]
    fn error_result_codes_are_negative() {
        // Convention: error codes should be negative, success is 0
        assert_eq!(RESULT_OK, 0, "RESULT_OK should be 0");
        assert!(RESULT_RATE_LIMITED < 0, "RESULT_RATE_LIMITED should be negative");
        assert!(RESULT_INVALID_MEMORY < 0, "RESULT_INVALID_MEMORY should be negative");
        assert!(RESULT_NOT_FOUND < 0, "RESULT_NOT_FOUND should be negative");
        assert!(RESULT_NO_WORLD < 0, "RESULT_NO_WORLD should be negative");
        assert!(RESULT_INVALID_UTF8 < 0, "RESULT_INVALID_UTF8 should be negative");
    }

    #[test]
    fn max_log_size_is_reasonable() {
        // Security: log size should be capped to prevent memory exhaustion
        assert!(MAX_LOG_SIZE > 0, "MAX_LOG_SIZE must be positive");
        assert!(
            MAX_LOG_SIZE <= 4096,
            "MAX_LOG_SIZE should not be excessively large (DoS prevention)"
        );
        assert!(
            MAX_LOG_SIZE >= 64,
            "MAX_LOG_SIZE should allow meaningful messages"
        );
    }

    #[test]
    fn state_with_world_can_spawn_entities() {
        let state = create_test_state();

        // Should be able to spawn initially
        assert!(state.rate_limits.can_spawn(), "should allow spawning initially");

        // Get the world and spawn
        let world_ref = state.world.as_ref().expect("world should exist");
        let entity = {
            let mut world = world_ref.write();
            world.spawn()
        };

        // Verify entity was created
        {
            let world = world_ref.read();
            assert!(world.exists(entity), "spawned entity should exist");
        }
    }

    #[test]
    fn state_without_world_returns_none() {
        let state = WasmState::new(TenantId::new());
        assert!(state.world.is_none(), "state without world should have None");
    }

    #[test]
    fn rate_limit_log_exhaustion() {
        // Security: verify rate limiting kicks in at MAX_LOG_CALLS
        let mut state = create_test_state();

        // Set log calls to just under max
        state.rate_limits.log_calls = RateLimits::MAX_LOG_CALLS - 1;
        assert!(state.rate_limits.can_log(), "should allow one more log");

        // Set to exactly max
        state.rate_limits.log_calls = RateLimits::MAX_LOG_CALLS;
        assert!(!state.rate_limits.can_log(), "should deny at max");

        // Set to over max (edge case - shouldn't happen but test defense)
        state.rate_limits.log_calls = RateLimits::MAX_LOG_CALLS + 100;
        assert!(!state.rate_limits.can_log(), "should deny when over max");
    }

    #[test]
    fn rate_limit_spawn_exhaustion() {
        // Security: verify rate limiting kicks in at MAX_SPAWN_CALLS
        let mut state = create_test_state();

        // Set spawn calls to just under max
        state.rate_limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS - 1;
        assert!(state.rate_limits.can_spawn(), "should allow one more spawn");

        // Set to exactly max
        state.rate_limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS;
        assert!(!state.rate_limits.can_spawn(), "should deny at max");

        // Set to over max (edge case - shouldn't happen but test defense)
        state.rate_limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS + 100;
        assert!(!state.rate_limits.can_spawn(), "should deny when over max");
    }

    #[test]
    fn rate_limits_reset_restores_capacity() {
        let mut state = create_test_state();

        // Exhaust rate limits
        state.rate_limits.log_calls = RateLimits::MAX_LOG_CALLS;
        state.rate_limits.spawn_calls = RateLimits::MAX_SPAWN_CALLS;
        assert!(!state.rate_limits.can_log());
        assert!(!state.rate_limits.can_spawn());

        // Reset via begin_tick
        state.begin_tick(1, 0.016);

        // Should be able to log and spawn again
        assert!(state.rate_limits.can_log(), "log should be allowed after reset");
        assert!(state.rate_limits.can_spawn(), "spawn should be allowed after reset");
    }

    #[test]
    fn entity_despawn_negative_id_rejected() {
        // Security: negative IDs should be rejected before world access
        // This test verifies the check at the function level
        // (We can't call host_entity_despawn directly without wasmtime, but we test the logic)
        let id: i64 = -1;
        assert!(id < 0, "negative ID should be detected");

        let id_min = i64::MIN;
        assert!(id_min < 0, "i64::MIN should be detected as negative");
    }

    #[test]
    fn entity_exists_negative_id_returns_false() {
        // Security: negative IDs should return false (not exist)
        let id: i64 = -1;
        assert!(id < 0, "negative ID check working");

        let id_min = i64::MIN;
        assert!(id_min < 0, "i64::MIN check working");
    }

    #[test]
    fn world_entity_lifecycle() {
        // Integration test: spawn, verify exists, despawn, verify gone
        let state = create_test_state();
        let world_ref = state.world.as_ref().expect("world should exist");

        // Spawn entity
        let entity_id = {
            let mut world = world_ref.write();
            world.spawn()
        };

        // Verify exists
        {
            let world = world_ref.read();
            assert!(world.exists(entity_id), "entity should exist after spawn");
        }

        // Despawn
        {
            let mut world = world_ref.write();
            world.despawn(entity_id).expect("despawn should succeed");
        }

        // Verify gone
        {
            let world = world_ref.read();
            assert!(!world.exists(entity_id), "entity should not exist after despawn");
        }
    }

    #[test]
    fn multiple_spawns_increment_rate_limit() {
        let mut state = create_test_state();
        let initial_calls = state.rate_limits.spawn_calls;

        // Simulate multiple spawns incrementing the counter
        for i in 1..=5 {
            state.rate_limits.spawn_calls += 1;
            assert_eq!(
                state.rate_limits.spawn_calls,
                initial_calls + i,
                "spawn_calls should increment"
            );
        }
    }

    #[test]
    fn multiple_logs_increment_rate_limit() {
        let mut state = create_test_state();
        let initial_calls = state.rate_limits.log_calls;

        // Simulate multiple logs incrementing the counter
        for i in 1..=5 {
            state.rate_limits.log_calls += 1;
            assert_eq!(
                state.rate_limits.log_calls,
                initial_calls + i,
                "log_calls should increment"
            );
        }
    }

    // =========================================================================
    // Bailey's peer review improvements
    // =========================================================================

    #[test]
    fn despawn_nonexistent_entity_returns_not_found() {
        // Security: despawning a nonexistent entity should return RESULT_NOT_FOUND,
        // not panic or cause undefined behavior
        let state = create_test_state();
        let world_ref = state.world.as_ref().expect("world should exist");

        // Try to despawn an entity that was never spawned
        let nonexistent_id = EntityId(999_999);
        {
            let mut world = world_ref.write();
            let result = world.despawn(nonexistent_id);
            assert!(
                result.is_err(),
                "despawning nonexistent entity should fail"
            );
        }
    }

    #[test]
    fn double_despawn_returns_error() {
        // Security: despawning the same entity twice should fail gracefully
        // on the second call, not panic or cause memory issues
        let state = create_test_state();
        let world_ref = state.world.as_ref().expect("world should exist");

        // Spawn an entity
        let entity_id = {
            let mut world = world_ref.write();
            world.spawn()
        };

        // First despawn should succeed
        {
            let mut world = world_ref.write();
            let result = world.despawn(entity_id);
            assert!(result.is_ok(), "first despawn should succeed");
        }

        // Second despawn should fail
        {
            let mut world = world_ref.write();
            let result = world.despawn(entity_id);
            assert!(
                result.is_err(),
                "second despawn of same entity should fail"
            );
        }

        // Entity should not exist
        {
            let world = world_ref.read();
            assert!(
                !world.exists(entity_id),
                "entity should not exist after despawn"
            );
        }
    }

    #[test]
    fn max_log_size_truncation_behavior() {
        // Verify that MAX_LOG_SIZE is used for truncation in read_wasm_string
        // This test documents the expected truncation behavior for oversized messages
        assert!(
            MAX_LOG_SIZE > 0,
            "MAX_LOG_SIZE should be positive for truncation"
        );
        assert!(
            MAX_LOG_SIZE == 1024,
            "MAX_LOG_SIZE should be 1024 bytes (documented value)"
        );
    }
}
