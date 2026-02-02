# Test Mapping

Maps Java tests to their Rust equivalents.

---

## Legend

| Status | Meaning |
|--------|---------|
| ⬜ | Not started |
| 🔄 | In progress |
| ✅ | Passing |
| ❌ | Failing |
| 🚫 | N/A (no Rust equivalent needed) |

---

## ECS Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `EcsWorldTest.testEntityCreation` | `ecs::world::tests::entity_creation` | ⬜ | |
| `EcsWorldTest.testComponentAccess` | `ecs::world::tests::component_access` | ⬜ | |
| `EcsWorldTest.testEntityDespawn` | `ecs::world::tests::entity_despawn` | ⬜ | |
| `EcsWorldTest.testSystemExecution` | `ecs::world::tests::system_execution` | ⬜ | |
| `EcsWorldTest.testSnapshot` | `ecs::world::tests::snapshot` | ⬜ | |
| `EcsWorldTest.testDelta` | `ecs::world::tests::delta_updates` | ⬜ | |

---

## WASM Security Tests (CRITICAL - WRITE FIRST)

| Test | Rust Test | Status | Notes |
|------|-----------|--------|-------|
| Memory escape attempt | `wasm::security::tests::memory_escape_blocked` | ⬜ | TDD - write before impl |
| Infinite loop termination | `wasm::security::tests::infinite_loop_terminated` | ⬜ | TDD - write before impl |
| Memory bomb prevention | `wasm::security::tests::memory_bomb_prevented` | ⬜ | TDD - write before impl |
| Stack overflow handling | `wasm::security::tests::stack_overflow_handled` | ⬜ | TDD - write before impl |
| Host function input validation | `wasm::security::tests::host_function_validation` | ⬜ | TDD - write before impl |
| Fuel exhaustion | `wasm::security::tests::fuel_exhausted` | ⬜ | TDD - write before impl |
| Epoch deadline exceeded | `wasm::security::tests::epoch_deadline_exceeded` | ⬜ | TDD - write before impl |
| Valid module execution | `wasm::security::tests::valid_module_executes` | ⬜ | TDD - write before impl |

---

## Auth Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `AuthServiceTest.testJwtValidation` | `auth::jwt::tests::validate_token` | ⬜ | |
| `AuthServiceTest.testJwtGeneration` | `auth::jwt::tests::generate_token` | ⬜ | |
| `AuthServiceTest.testJwtExpiration` | `auth::jwt::tests::token_expiration` | ⬜ | |
| `AuthServiceTest.testPasswordHashing` | `auth::password::tests::hash_verify` | ⬜ | |
| `RbacServiceTest.testPermissions` | `auth::rbac::tests::permissions` | ⬜ | |
| `RbacServiceTest.testRoles` | `auth::rbac::tests::role_permissions` | ⬜ | |

---

## Container Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `ContainerServiceTest.testCreate` | `container::tests::create_container` | ⬜ | |
| `ContainerServiceTest.testIsolation` | `container::tests::tenant_isolation` | ⬜ | |
| `ContainerServiceTest.testMatchCreate` | `container::tests::create_match` | ⬜ | |
| `ContainerServiceTest.testModuleInstall` | `container::tests::install_module` | ⬜ | |
| `ContainerServiceTest.testTickExecution` | `container::tests::tick_execution` | ⬜ | |

---

## Match Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `MatchServiceTest.testCreate` | `match_::tests::create_match` | ⬜ | |
| `MatchServiceTest.testPlayerJoin` | `match_::tests::player_join` | ⬜ | |
| `MatchServiceTest.testPlayerLeave` | `match_::tests::player_leave` | ⬜ | |
| `MatchServiceTest.testCommand` | `match_::tests::queue_command` | ⬜ | |
| `MatchServiceTest.testSnapshot` | `match_::tests::snapshot_generation` | ⬜ | |

---

## WebSocket Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `WebSocketTest.testConnect` | `ws::tests::connection` | ⬜ | |
| `WebSocketTest.testSubscribe` | `ws::tests::subscribe` | ⬜ | |
| `WebSocketTest.testSnapshot` | `ws::tests::receive_snapshot` | ⬜ | |
| `WebSocketTest.testDelta` | `ws::tests::receive_delta` | ⬜ | |
| `WebSocketTest.testCommand` | `ws::tests::send_command` | ⬜ | |
| `WebSocketTest.testAuth` | `ws::tests::authentication` | ⬜ | |

---

## Module System Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `ModuleLoaderTest.testLoad` | `modules::tests::load_module` | ⬜ | |
| `ModuleLoaderTest.testReload` | `modules::tests::reload_module` | ⬜ | |
| `ModuleLoaderTest.testUnload` | `modules::tests::unload_module` | ⬜ | |
| `ModuleLoaderTest.testDiscovery` | `modules::tests::module_discovery` | ⬜ | Uses inventory crate |

---

## Integration Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `E2ETest.testFullGameLoop` | `integration::tests::full_game_loop` | ⬜ | |
| `E2ETest.testMultiplayer` | `integration::tests::multiplayer_session` | ⬜ | |
| `E2ETest.testTenantIsolation` | `integration::tests::tenant_isolation` | ⬜ | |
| `E2ETest.testModuleHotReload` | `integration::tests::hot_reload` | ⬜ | |

---

## Performance Tests

| Test | Rust Test | Status | Target | Notes |
|------|-----------|--------|--------|-------|
| ECS 10k entities | `benches::ecs_10k_entities` | ⬜ | ≥746 ticks/sec | |
| WASM module execution | `benches::wasm_execution` | ⬜ | TBD | |
| Snapshot serialization | `benches::snapshot_serialization` | ⬜ | TBD | |
| WebSocket throughput | `benches::ws_throughput` | ⬜ | TBD | |

---

## Test File Locations

| Java Location | Rust Location |
|---------------|---------------|
| `thunder/engine/tests/` | `stormstack-ecs/src/tests/` |
| `thunder/auth/tests/` | `stormstack-auth/src/tests/` |
| `thunder/engine/tests/api-acceptance/` | `stormstack-server/tests/` |
| `thunder/engine/tests/playwright/` | `stormstack-server/tests/e2e/` |

---

## Statistics

| Category | Total | ✅ | 🔄 | ⬜ | ❌ |
|----------|-------|----|----|----|----|
| ECS | 6 | 0 | 0 | 6 | 0 |
| WASM Security | 8 | 0 | 0 | 8 | 0 |
| Auth | 6 | 0 | 0 | 6 | 0 |
| Container | 5 | 0 | 0 | 5 | 0 |
| Match | 5 | 0 | 0 | 5 | 0 |
| WebSocket | 6 | 0 | 0 | 6 | 0 |
| Module System | 4 | 0 | 0 | 4 | 0 |
| Integration | 4 | 0 | 0 | 4 | 0 |
| Performance | 4 | 0 | 0 | 4 | 0 |
| **Total** | **48** | **0** | **0** | **48** | **0** |

