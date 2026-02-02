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
| `ModuleLoaderTest.testLoad` | `loader::tests::load_valid_module` | ✅ | libloading |
| `ModuleLoaderTest.testReload` | `loader::tests::reload_module` | ✅ | Unload + load |
| `ModuleLoaderTest.testUnload` | `loader::tests::unload_module` | ✅ | |
| `ModuleLoaderTest.testDiscovery` | `registry::tests::*` | ✅ | Uses module registry |
| N/A | `descriptor::tests::descriptor_creation` | ✅ | Module metadata |
| N/A | `descriptor::tests::descriptor_with_dependencies` | ✅ | Dependency tracking |
| N/A | `descriptor::tests::default_descriptor` | ✅ | Default values |
| N/A | `descriptor::tests::abi_version_display` | ✅ | ABI formatting |
| N/A | `module_trait::tests::module_lifecycle` | ✅ | on_load/tick/unload |
| N/A | `module_trait::tests::context_access` | ✅ | Context in callbacks |
| N/A | `loader::tests::load_invalid_path_fails` | ✅ | Error handling |
| N/A | `loader::tests::symbol_not_found` | ✅ | Missing symbol |
| N/A | `loader::tests::abi_version_mismatch` | ✅ | Version check |
| N/A | `loader::tests::shared_loader_thread_safety` | ✅ | Thread-safe |
| N/A | `registry::tests::register_and_resolve` | ✅ | Basic registration |
| N/A | `registry::tests::dependency_resolution` | ✅ | Topological sort |
| N/A | `registry::tests::circular_dependency_detected` | ✅ | Cycle detection |
| N/A | `registry::tests::missing_dependency` | ✅ | Error handling |
| N/A | `registry::tests::topological_sort_order` | ✅ | Load order |
| N/A | `registry::tests::unregister_module` | ✅ | Removal |
| N/A | `registry::tests::unregister_with_dependents_fails` | ✅ | Safety check |
| N/A | `registry::tests::get_module_info` | ✅ | Query metadata |
| N/A | `registry::tests::list_all_modules` | ✅ | Enumeration |
| N/A | `registry::tests::clear_registry` | ✅ | Reset |
| N/A | `registry::tests::duplicate_registration_fails` | ✅ | Unique names |
| N/A | `declare_module::tests::macro_creates_descriptor` | ✅ | declare_module! |
| N/A | `declare_module::tests::macro_with_dependencies` | ✅ | With deps |
| N/A | `declare_module::tests::macro_default_version` | ✅ | Defaults |

---

## Integration Tests

| Java Test | Rust Test | Status | Notes |
|-----------|-----------|--------|-------|
| `E2ETest.testFullGameLoop` | `integration::tests::full_game_loop` | ⬜ | |
| `E2ETest.testMultiplayer` | `integration::tests::multiplayer_session` | ⬜ | |
| `E2ETest.testTenantIsolation` | `integration::tests::tenant_isolation` | ⬜ | |
| `E2ETest.testModuleHotReload` | `integration::tests::hot_reload` | ⬜ | |

### Server Integration Tests (stormstack-server)

| Rust Test | Status | Notes |
|-----------|--------|-------|
| `state::tests::app_state_creation` | ✅ | AppState setup |
| `state::tests::app_state_auth_trait` | ✅ | AuthState implementation |
| `state::tests::jwt_service_access` | ✅ | JWT via trait |
| `server::tests::server_creation` | ✅ | Server setup |
| `server::tests::server_with_config` | ✅ | Custom config |
| `server::tests::server_routes_health` | ✅ | Health endpoint |
| `routes::tests::health_returns_ok` | ✅ | GET /health |
| `routes::tests::containers_returns_empty` | ✅ | GET /api/containers |
| `routes::tests::not_found_returns_404` | ✅ | 404 handling |
| `routes::tests::api_response_format` | ✅ | JSON response |
| `routes::tests::auth_required_without_token` | ✅ | 401 without auth |
| `routes::tests::auth_works_with_token` | ✅ | Auth extraction |
| `routes::tests::invalid_token_rejected` | ✅ | Bad token |
| `routes::tests::health_no_auth_required` | ✅ | Public endpoint |
| `routes::tests::cors_headers_present` | ✅ | CORS middleware |

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
| WASM Security | 13 | 13 | 0 | 0 | 0 |
| WASM Host | 10 | 10 | 0 | 0 | 0 |
| Auth | 31 | 31 | 0 | 0 | 0 |
| Container | 5 | 0 | 0 | 5 | 0 |
| Match | 5 | 0 | 0 | 5 | 0 |
| WebSocket | 15 | 15 | 0 | 0 | 0 |
| Module System | 29 | 29 | 0 | 0 | 0 |
| Net | 16 | 16 | 0 | 0 | 0 |
| Server (Integration) | 15 | 15 | 0 | 0 | 0 |
| Core | 13 | 13 | 0 | 0 | 0 |
| Performance | 4 | 0 | 0 | 4 | 0 |
| **Total** | **171** | **157** | **0** | **14** | **0** |

### Rust Test Counts by Crate

| Crate | Tests | Status |
|-------|-------|--------|
| stormstack-auth | 31 | ✅ |
| stormstack-core | 13 | ✅ |
| stormstack-ecs | 15 | ✅ |
| stormstack-modules | 29 | ✅ |
| stormstack-net | 16 | ✅ |
| stormstack-server | 15 | ✅ |
| stormstack-test-utils | 1 | ✅ |
| stormstack-wasm | 13 | ✅ |
| stormstack-wasm-host | 10 | ✅ |
| stormstack-ws | 15 | ✅ |
| **Total** | **161** | ✅ |

