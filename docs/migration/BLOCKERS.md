# Blockers

Active blockers and issues affecting the Rust rewrite.

---

## Active Blockers

<!-- Add blockers here in this format:

## BLOCKER: [Short description]
**Owner:** [Agent name]
**Status:** [Investigating | Waiting | Blocked]
**Affects:** [What is blocked by this]
**Workaround:** [If any]
**Details:**
[Detailed description of the issue]

-->

*No active blockers at this time.*

---

## Locks

<!-- Add locks here when coordinating between agents:

## LOCK: [Resource being locked]
**Owner:** [Agent name]
**Since:** [Timestamp]
**Reason:** [Why locked]
**Expected Release:** [When]

-->

*No active locks at this time.*

---

## Resolved Blockers

<!-- Move resolved blockers here for reference:

## RESOLVED: [Short description]
**Resolved:** [Date]
**Resolution:** [How it was resolved]

-->

*No resolved blockers yet.*

---

## Completion Notices

## COMPLETE: stormstack-wasm (WASM Sandbox)
**Date:** 2026-02-02
**Agent:** WASM Agent
**Interfaces:**
- `WasmSandbox::new()` - Create sandbox with epoch thread
- `WasmSandbox::load_module()` - Load WASM bytes
- `WasmSandbox::instantiate()` - Create instance with limits
- `WasmSandbox::execute()` - Run function with fuel/epoch protection
- `WasmResourceLimits` - Configure fuel, memory, epoch, stack limits
**Notes:**
- 13 security tests passing
- Fuel metering + epoch interruption for timeout
- Memory limits via StoreLimits
- Ready for host function integration

---

## COMPLETE: stormstack-auth (Authentication)
**Date:** 2026-02-02
**Agent:** Auth Agent
**Interfaces:**
- `JwtService::new(secret)` - Create JWT service
- `JwtService::generate_token()` / `validate_token()` / `refresh_token()`
- `PasswordService::new()` - Create password service (OWASP Argon2id)
- `PasswordService::hash_password()` / `verify_password()`
- `RbacService::has_permission()` / `role_permissions()`
- `Claims`, `Permission` types
**Notes:**
- 31 tests + 3 doc tests passing
- Uses HS256 for JWT (jsonwebtoken crate)
- Uses Argon2id for passwords (argon2 crate)
- Ready for integration with HTTP middleware

---

## COMPLETE: stormstack-ecs (ECS World)
**Date:** 2026-02-02
**Agent:** ECS Agent
**Interfaces:**
- `EcsWorld` trait - Entity-component management interface
- `StormWorld::new()` - Create legion-backed ECS world
- `StormWorld::spawn()` / `spawn_with()` - Entity creation
- `StormWorld::despawn()` - Entity deletion
- `StormWorld::add_component()` / `remove_component()` / `get_component()`
- `StormWorld::advance()` - Tick execution with system scheduling
- `StormWorld::snapshot()` / `delta_since()` - State streaming
- `SharedWorld` type alias for thread-safe access
- `Marker` component for empty entities
**Notes:**
- 15 tests passing
- Uses legion ECS with entity ID mapping (our EntityId vs legion Entity)
- Change tracking for efficient delta generation
- Component type registry for serialization
- System scheduling via `set_schedule()`
- Ready for integration with WASM host functions

