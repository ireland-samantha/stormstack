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
| `EcsWorldTest.testEntityCreation` | `world::tests::spawn_increments_id` | ✅ | EntityId generation |
| `EcsWorldTest.testEntityCreation` | `world::tests::spawn_with_components` | ✅ | Spawn with tuple of components |
| `EcsWorldTest.testComponentAccess` | `world::tests::add_component_to_entity` | ✅ | Add component to existing entity |
| `EcsWorldTest.testComponentAccess` | `world::tests::remove_component_from_entity` | ✅ | Remove component from entity |
| `EcsWorldTest.testComponentAccess` | `world::tests::register_component_type` | ✅ | Type ID registration |
| `EcsWorldTest.testComponentAccess` | `world::tests::component_type_ids_are_unique` | ✅ | Unique type IDs |
| `EcsWorldTest.testEntityDespawn` | `world::tests::despawn_removes_entity` | ✅ | Entity removal |
| `EcsWorldTest.testEntityDespawn` | `world::tests::despawn_nonexistent_fails` | ✅ | Error on invalid despawn |
| `EcsWorldTest.testSystemExecution` | `world::tests::advance_increments_tick` | ✅ | Tick advancement |
| `EcsWorldTest.testSnapshot` | `world::tests::snapshot_includes_entities` | ✅ | Snapshot generation |
| `EcsWorldTest.testDelta` | `world::tests::delta_tracks_spawns` | ✅ | Delta spawns tracking |
| `EcsWorldTest.testDelta` | `world::tests::delta_tracks_despawns` | ✅ | Delta despawns tracking |
| N/A | `world::tests::entities_iterator` | ✅ | Entity iteration |
| N/A | `world::tests::cleanup_history` | ✅ | Change history cleanup |
| N/A | `world::tests::shared_world_works` | ✅ | Thread-safe SharedWorld |

---

## WASM Security Tests (CRITICAL - WRITE FIRST)

| Test | Rust Test | Status | Notes |
|------|-----------|--------|-------|
| Memory escape attempt | `sandbox::tests::test_memory_bomb_prevented` | ✅ | memory.grow returns -1 on limit |
| Infinite loop termination | `sandbox::tests::test_infinite_loop_terminated` | ✅ | Fuel/epoch terminates loop |
| Memory bomb prevention | `sandbox::tests::test_memory_bomb_prevented` | ✅ | StoreLimits enforced |
| Stack overflow handling | `sandbox::tests::test_stack_overflow_handled` | ✅ | Deep recursion caught |
| Host function input validation | `wasm_host::functions::tests::*` | ✅ | Rate limits, memory bounds checked |
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
| `WebSocketTest.testConnect` | `connection::tests::add_and_remove_connection` | ✅ | Connection lifecycle |
| `WebSocketTest.testSubscribe` | `handler::tests::subscribe_sends_snapshot` | ✅ | Subscription + snapshot |
| `WebSocketTest.testSubscribe` | `subscription::tests::subscribe_and_unsubscribe` | ✅ | Subscription tracking |
| `WebSocketTest.testSnapshot` | `handler::tests::subscribe_sends_snapshot` | ✅ | Initial snapshot on subscribe |
| `WebSocketTest.testBroadcast` | `connection::tests::subscribe_and_broadcast` | ✅ | Broadcast to subscribers |
| `WebSocketTest.testPing` | `handler::tests::ping_responds_with_pong` | ✅ | Ping/pong keepalive |
| `WebSocketTest.testDisconnect` | `handler::tests::disconnect_removes_subscriptions` | ✅ | Cleanup on disconnect |
| N/A | `connection::tests::send_to_connection` | ✅ | Direct message send |
| N/A | `connection::tests::connection_authentication` | ✅ | Auth state tracking |
| N/A | `subscription::tests::multiple_subscribers` | ✅ | Multi-subscriber |
| N/A | `subscription::tests::get_connection_subscriptions` | ✅ | Query subscriptions |
| N/A | `subscription::tests::remove_connection_clears_all_subscriptions` | ✅ | Cleanup all |
| N/A | `messages::tests::client_message_serialize` | ✅ | Message serialization |
| N/A | `messages::tests::server_message_serialize` | ✅ | Message serialization |
| N/A | `messages::tests::command_result_success` | ✅ | Command result |

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
| ECS | 15 | 15 | 0 | 0 | 0 |
| WASM Security | 11 | 11 | 0 | 0 | 0 |
| Auth | 9 | 9 | 0 | 0 | 0 |
| Container | 5 | 0 | 0 | 5 | 0 |
| Match | 5 | 0 | 0 | 5 | 0 |
| WebSocket | 15 | 15 | 0 | 0 | 0 |
| Module System | 4 | 0 | 0 | 4 | 0 |
| Integration | 4 | 0 | 0 | 4 | 0 |
| Performance | 4 | 0 | 0 | 4 | 0 |
| **Total** | **72** | **50** | **0** | **22** | **0** |

