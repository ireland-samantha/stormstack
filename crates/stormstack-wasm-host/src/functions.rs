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
}
