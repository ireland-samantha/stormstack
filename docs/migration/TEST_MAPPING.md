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
| Memory escape attempt | `sandbox::tests::test_memory_bomb_prevented` | ✅ | memory.grow returns -1 on limit |
| Infinite loop termination | `sandbox::tests::test_infinite_loop_terminated` | ✅ | Fuel/epoch terminates loop |
| Memory bomb prevention | `sandbox::tests::test_memory_bomb_prevented` | ✅ | StoreLimits enforced |
| Stack overflow handling | `sandbox::tests::test_stack_overflow_handled` | ✅ | Deep recursion caught |
| Host function input validation | `wasm::security::tests::host_function_validation` | ⬜ | Pending host function impl |
| Fuel exhaustion | `sandbox::tests::test_fuel_exhausted` | ✅ | Fuel metering works |
| Epoch deadline exceeded | `sandbox::tests::test_infinite_loop_terminated` | ✅ | Epoch interruption backup |
| Valid module execution | `sandbox::tests::test_valid_module_executes` | ✅ | Add, factorial, get_answer work |
| Fuel tracking | `sandbox::tests::test_fuel_tracking` | ✅ | Fuel consumption tracked |
| Memory usage tracking | `sandbox::tests::test_memory_usage_tracking` | ✅ | Memory size tracked |
| Function not found | `sandbox::tests::test_function_not_found` | ✅ | FunctionNotFound error |

---

## Auth Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `AuthServiceTest.testJwtValidation` | `jwt::tests::validate_and_generate_roundtrip` | ✅ | HS256 with jsonwebtoken |
| `AuthServiceTest.testJwtGeneration` | `jwt::tests::generate_token` | ✅ | |
| `AuthServiceTest.testJwtExpiration` | `jwt::tests::expired_token_rejected` | ✅ | Default 60s leeway |
| `AuthServiceTest.testJwtRefresh` | `jwt::tests::refresh_token_creates_new_token` | ✅ | Refresh within window |
| `AuthServiceTest.testPasswordHashing` | `password::tests::hash_and_verify_roundtrip` | ✅ | Argon2id OWASP params |
| `AuthServiceTest.testPasswordSalting` | `password::tests::different_hashes_for_same_password` | ✅ | Random salt per hash |
| `RbacServiceTest.testPermissions` | `rbac::tests::admin_has_all_permissions` | ✅ | |
| `RbacServiceTest.testRoles` | `rbac::tests::role_permissions_list` | ✅ | |
| `RbacServiceTest.testMultipleRoles` | `rbac::tests::multiple_roles_combine_permissions` | ✅ | |

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
| WASM Security | 11 | 10 | 0 | 1 | 0 |
| Auth | 9 | 9 | 0 | 0 | 0 |
| Container | 5 | 0 | 0 | 5 | 0 |
| Match | 5 | 0 | 0 | 5 | 0 |
| WebSocket | 6 | 0 | 0 | 6 | 0 |
| Module System | 4 | 0 | 0 | 4 | 0 |
| Integration | 4 | 0 | 0 | 4 | 0 |
| Performance | 4 | 0 | 0 | 4 | 0 |
| **Total** | **54** | **19** | **0** | **35** | **0** |

