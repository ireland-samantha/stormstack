# WASM Host Functions

Documents all host functions available to WASM modules.

**SECURITY NOTE:** Every function here expands the attack surface.
All host functions MUST be reviewed by Security Reviewer before implementation.

---

## Approved Host Functions

### Logging

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `log_debug` | `fn(ptr: i32, len: i32)` | Log debug message | Rate limited, max 1KB |
| `log_info` | `fn(ptr: i32, len: i32)` | Log info message | Rate limited, max 1KB |
| `log_warn` | `fn(ptr: i32, len: i32)` | Log warning message | Rate limited, max 1KB |
| `log_error` | `fn(ptr: i32, len: i32)` | Log error message | Rate limited, max 1KB |

**Validation:**
- Validate ptr + len is within WASM linear memory bounds
- UTF-8 validation on string content
- Rate limit: max 100 logs per tick
- Truncate to 1KB max

---

### Time

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `get_tick` | `fn() -> i64` | Get current game tick | Read-only |
| `get_delta_time` | `fn() -> f64` | Get delta time in seconds | Read-only |

**Validation:**
- No inputs to validate
- Returns game-controlled values only
- No real-time clock access (prevents timing attacks)

---

### Entity Access

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `entity_spawn` | `fn() -> i64` | Create new entity | Tenant-scoped, rate limited |
| `entity_despawn` | `fn(id: i64) -> i32` | Remove entity | Tenant-scoped only |
| `entity_exists` | `fn(id: i64) -> i32` | Check if entity exists | Read-only |

**Validation:**
- entity_id must belong to calling tenant's match
- Rate limit: max 100 spawns per tick
- Returns 0/1 for bool results

---

### Component Access

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `component_get` | `fn(entity: i64, type_id: i64, out_ptr: i32, out_len: i32) -> i32` | Read component data | Tenant-scoped |
| `component_set` | `fn(entity: i64, type_id: i64, ptr: i32, len: i32) -> i32` | Write component data | Tenant-scoped |
| `component_has` | `fn(entity: i64, type_id: i64) -> i32` | Check component presence | Read-only |

**Validation:**
- entity must belong to calling tenant
- type_id must be registered component type
- ptr + len must be within WASM memory bounds
- Component data is serialized/deserialized (no raw pointers)
- Max component size: 64KB

---

### Random Number Generation

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `random_u32` | `fn() -> i32` | Get random u32 | Deterministic in replay mode |
| `random_f32` | `fn() -> f32` | Get random f32 [0, 1) | Deterministic in replay mode |
| `random_range` | `fn(min: i32, max: i32) -> i32` | Get random in range | Validates min <= max |

**Validation:**
- Uses seeded RNG for deterministic replay
- No access to system entropy
- random_range: returns error if min > max

---

### Query System

| Function | Signature | Description | Security Notes |
|----------|-----------|-------------|----------------|
| `query_entities` | `fn(type_ids_ptr: i32, type_ids_len: i32, out_ptr: i32, out_max: i32) -> i32` | Query entities with components | Tenant-scoped |

**Validation:**
- type_ids must all be valid component types
- Only returns entities in calling tenant's match
- Max results limited by out_max
- Returns count of entities found

---

## Explicitly Denied Capabilities

These MUST NEVER be implemented as host functions:

| Capability | Reason |
|------------|--------|
| File system access | Sandbox escape risk |
| Network access | Sandbox escape risk |
| Environment variables | Information leak |
| Process spawning | Sandbox escape risk |
| System time | Timing attacks |
| Inter-tenant communication | Isolation violation |
| Direct memory access | Sandbox escape risk |
| Reflection | Type confusion attacks |

---

## Host Function Implementation Template

```rust
fn host_function_example(
    mut caller: Caller<'_, WasmState>,
    arg1: i32,
    arg2: i32,
) -> Result<i32, Trap> {
    // 1. Validate tenant context
    let tenant_id = caller.data().tenant_id;

    // 2. Validate all inputs
    let memory = caller.get_export("memory")
        .and_then(|e| e.into_memory())
        .ok_or_else(|| Trap::new("no memory export"))?;

    // 3. Bounds check memory access
    let data = memory.data(&caller);
    if arg1 < 0 || arg2 < 0 {
        return Err(Trap::new("negative pointer/length"));
    }
    let start = arg1 as usize;
    let len = arg2 as usize;
    if start.saturating_add(len) > data.len() {
        return Err(Trap::new("memory access out of bounds"));
    }

    // 4. Perform operation with tenant scoping
    let result = caller.data_mut().perform_operation(&data[start..start+len])?;

    // 5. Return result
    Ok(result)
}
```

---

## Host Function Checklist (for Security Review)

For each host function, verify:

- [ ] All pointer arguments bounds-checked
- [ ] All entity access tenant-scoped
- [ ] Rate limiting in place (if applicable)
- [ ] No raw pointer exposure
- [ ] No information leakage across tenants
- [ ] Input validation on all parameters
- [ ] Deterministic behavior documented
- [ ] Memory allocation limits enforced
- [ ] Error handling returns Trap (not panic)
- [ ] Fuel consumption accounted for

---

## Provider Registration

```rust
impl HostFunctionProvider for CoreHostFunctions {
    fn register(&self, linker: &mut Linker<WasmState>) -> Result<(), WasmError> {
        // Logging
        linker.func_wrap("env", "log_debug", host_log_debug)?;
        linker.func_wrap("env", "log_info", host_log_info)?;
        linker.func_wrap("env", "log_warn", host_log_warn)?;
        linker.func_wrap("env", "log_error", host_log_error)?;

        // Time
        linker.func_wrap("env", "get_tick", host_get_tick)?;
        linker.func_wrap("env", "get_delta_time", host_get_delta_time)?;

        // Entity
        linker.func_wrap("env", "entity_spawn", host_entity_spawn)?;
        linker.func_wrap("env", "entity_despawn", host_entity_despawn)?;
        linker.func_wrap("env", "entity_exists", host_entity_exists)?;

        // Components
        linker.func_wrap("env", "component_get", host_component_get)?;
        linker.func_wrap("env", "component_set", host_component_set)?;
        linker.func_wrap("env", "component_has", host_component_has)?;

        // Random
        linker.func_wrap("env", "random_u32", host_random_u32)?;
        linker.func_wrap("env", "random_f32", host_random_f32)?;
        linker.func_wrap("env", "random_range", host_random_range)?;

        // Query
        linker.func_wrap("env", "query_entities", host_query_entities)?;

        Ok(())
    }

    fn name(&self) -> &'static str {
        "core"
    }
}
```

---

## Change Log

| Date | Function | Change | Reviewer |
|------|----------|--------|----------|
| 2026-02-02 | All | Initial specification | Pending review |

