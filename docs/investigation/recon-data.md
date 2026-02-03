# Reconnaissance Data: Branch Parity Analysis

**Generated:** 2026-02-02
**Current Branch:** rust-rewrite (Rust implementation)
**Comparison Branch:** main (Java implementation)

---

## 1. Rust (rust-rewrite) Structure

### Crate Inventory

| Crate | Source Files | Test Files | Lines of Code |
|-------|-------------|------------|---------------|
| stormstack-auth | 6 | 5 | 2,198 |
| stormstack-core | 6 | 5 | 1,887 |
| stormstack-db | 7 | 0 | 1,462 |
| stormstack-ecs | 2 | 1 | 841 |
| stormstack-game-modules | 4 | 4 | 2,745 |
| stormstack-modules | 5 | 5 | 1,554 |
| stormstack-net | 4 | 3 | 793 |
| stormstack-server | 10 | 7 | 7,706 |
| stormstack-test-utils | 0 | 1 | 90 |
| stormstack-wasm-host | 4 | 3 | 642 |
| stormstack-wasm | 3 | 2 | 928 |
| stormstack-ws | 5 | 4 | 897 |
| **TOTAL** | **56** | **40** | **21,743** |

### Rust File Listing

```
crates/stormstack-auth/src/jwt.rs
crates/stormstack-auth/src/claims.rs
crates/stormstack-auth/src/password.rs
crates/stormstack-auth/src/lib.rs
crates/stormstack-auth/src/rbac.rs
crates/stormstack-auth/src/oauth2.rs
crates/stormstack-db/src/repositories/mod.rs
crates/stormstack-db/src/repositories/container.rs
crates/stormstack-db/src/repositories/match_.rs
crates/stormstack-db/src/repositories/user.rs
crates/stormstack-db/src/pool.rs
crates/stormstack-db/src/models.rs
crates/stormstack-db/src/lib.rs
crates/stormstack-core/src/config.rs
crates/stormstack-core/src/command.rs
crates/stormstack-core/src/snapshot.rs
crates/stormstack-core/src/error.rs
crates/stormstack-core/src/lib.rs
crates/stormstack-core/src/id.rs
crates/stormstack-ecs/benches/ecs_benchmark.rs
crates/stormstack-ecs/src/world.rs
crates/stormstack-ecs/src/lib.rs
crates/stormstack-game-modules/src/entity.rs
crates/stormstack-game-modules/src/movement.rs
crates/stormstack-game-modules/src/health.rs
crates/stormstack-game-modules/src/lib.rs
crates/stormstack-modules/src/descriptor.rs
crates/stormstack-modules/src/module_trait.rs
crates/stormstack-modules/src/loader.rs
crates/stormstack-modules/src/lib.rs
crates/stormstack-modules/src/registry.rs
crates/stormstack-net/src/extractors.rs
crates/stormstack-net/src/server.rs
crates/stormstack-net/src/responses.rs
crates/stormstack-net/src/lib.rs
crates/stormstack-server/src/commands.rs
crates/stormstack-server/src/game_loop.rs
crates/stormstack-server/src/resources.rs
crates/stormstack-server/src/ws.rs
crates/stormstack-server/src/main.rs
crates/stormstack-server/src/state.rs
crates/stormstack-server/src/lib.rs
crates/stormstack-server/src/routes.rs
crates/stormstack-server/src/session.rs
crates/stormstack-server/src/container.rs
crates/stormstack-test-utils/src/harness.rs
crates/stormstack-test-utils/src/fixtures.rs
crates/stormstack-test-utils/src/lib.rs
crates/stormstack-wasm-host/src/state.rs
crates/stormstack-wasm-host/src/lib.rs
crates/stormstack-wasm-host/src/functions.rs
crates/stormstack-wasm-host/src/provider.rs
crates/stormstack-wasm/src/sandbox.rs
crates/stormstack-wasm/src/lib.rs
crates/stormstack-wasm/src/limits.rs
crates/stormstack-ws/src/connection.rs
crates/stormstack-ws/src/lib.rs
crates/stormstack-ws/src/subscription.rs
crates/stormstack-ws/src/handler.rs
crates/stormstack-ws/src/messages.rs
```

---

## 2. Java (main) Structure

### Module Inventory

| Module | Source Files | Test Files |
|--------|-------------|------------|
| thunder/auth/core | 46 | 18 |
| thunder/auth/provider | 30 | 10 |
| thunder/auth/adapters/quarkus | 21 | 11 |
| thunder/auth/adapters/spring | 12 | 7 |
| **thunder/auth subtotal** | **109** | **46** |
| thunder/control-plane/core | 35 | 0 |
| thunder/control-plane/provider | (included in core count) | 0 |
| **thunder/control-plane subtotal** | **35** | **0** |
| thunder/engine/core | 140 | 51 |
| thunder/engine/provider | 77 | 28 |
| thunder/engine/adapters | 39 | (included in provider) |
| thunder/engine/extensions | 162 | (included in provider) |
| thunder/engine/tests | 0 | 43 |
| **thunder/engine subtotal** | **418** | **122** |
| thunder/shared | 3 | 0 |
| **TOTAL thunder/** | **565** | **168** |

### Total Java Files on main

| Category | Count |
|----------|-------|
| Total Java files (all) | 299 |
| Total Java files (thunder/) | 205 |
| Java source files (non-test) | 239 |
| Java test files (*Test.java) | 60 |
| Total lines of code (thunder/) | 141,410 |

---

## 3. REST Endpoint Counts

### Rust (rust-rewrite)

| File | Route Count |
|------|-------------|
| crates/stormstack-server/src/routes.rs | 29 |

**Route Definitions (rust-rewrite):**
```
/health
/api/containers (GET, POST)
/api/containers/{id} (GET, DELETE)
/api/containers/{id}/tick (POST)
/api/containers/{id}/ticks/auto (POST)
/api/containers/{id}/players (GET)
/api/containers/{id}/commands/errors (GET)
/api/containers/{id}/metrics (GET)
/api/containers/{id}/matches (POST, GET)
/api/containers/{id}/matches/{match_id} (GET, DELETE)
/api/containers/{id}/matches/{match_id}/join (POST)
/api/containers/{id}/matches/{match_id}/leave (POST)
/api/containers/{id}/matches/{match_id}/start (POST)
/api/containers/{id}/matches/{match_id}/commands (POST)
/api/commands (GET)
/api/containers/{id}/sessions (GET)
/api/containers/{id}/sessions/{session_id} (GET, DELETE)
/api/resources (POST, GET)
/api/resources/{id} (GET, DELETE)
/api/resources/{id}/metadata (GET)
/auth/token (POST)
/ws/matches/{match_id} (WebSocket)
```

### Java (main)

| Category | Count |
|----------|-------|
| REST Resource/Endpoint classes | 37 |

**Resource Files (main):**
- thunder/auth/provider: 9 resources (ApiToken, MatchToken, Role, Token, UserInfo, User, Validation, Discovery, Jwks)
- thunder/control-plane/provider: 9 resources (AuthProxy, Autoscaler, Cluster, Dashboard, Deploy, Match, Module, NodeProxy, Node)
- thunder/engine/provider: 16 resources (ContainerCommand, ContainerHistory, ContainerLifecycle, ContainerMatch, ContainerMetrics, ContainerModule, ContainerPlayer, ContainerResourceManagement, ContainerRestore, ContainerSession, ContainerSnapshot, ControlPlaneProxy, Health, Module, NodeMetrics, SimulationControl)

---

## 4. WebSocket Handler Counts

### Rust (rust-rewrite)

| Category | Count |
|----------|-------|
| WebSocket-related files | 12 |
| WebSocket handler files | 5 (stormstack-ws crate) |

**WebSocket Files:**
```
crates/stormstack-ws/src/connection.rs
crates/stormstack-ws/src/handler.rs
crates/stormstack-ws/src/messages.rs
crates/stormstack-ws/src/subscription.rs
crates/stormstack-ws/src/lib.rs
crates/stormstack-server/src/ws.rs
```

### Java (main)

| Category | Count |
|----------|-------|
| WebSocket-related Java files | 35 |
| WebSocket handler classes | 9 (main source) |
| WebSocket test classes | 8 |
| WebSocket filter classes | 5 |

**WebSocket Handler Classes (main):**
- ContainerCommandWebSocket
- DeltaSnapshotWebSocket
- PlayerDeltaSnapshotWebSocket
- PlayerErrorWebSocket
- PlayerSnapshotWebSocket
- SimulationWebSocket
- SnapshotWebSocket
- CommandWebSocketClient (adapter)
- WebSocketConnectionLimiter

---

## 5. Test File Counts

### Rust (rust-rewrite)

| Category | Count |
|----------|-------|
| Files with #[test] | 40 |
| Total #[test] annotations | 381 |

### Java (main)

| Category | Count |
|----------|-------|
| Test files (*Test.java) | 60 |
| thunder/auth tests | 46 |
| thunder/engine tests | 122 (including IT/acceptance) |
| thunder/control-plane tests | 0 |

---

## 6. Summary Metrics

| Metric | Rust (rust-rewrite) | Java (main) |
|--------|---------------------|-------------|
| Crates/Modules | 12 | 3 services (auth, control-plane, engine) |
| Source Files | 56 | 239 |
| Test Files | 40 | 60 |
| Lines of Code | 21,743 | 141,410 |
| REST Routes | 29 | 37 resource classes |
| WebSocket Handlers | 6 | 9 |

---

## 7. Lightning Tools (main branch only)

| Component | File Count |
|-----------|------------|
| lightning/cli (Go) | 22 |
| lightning/rendering (Java) | 94 |
| lightning/webpanel (TS/JS) | 72 |

---

*Raw data collected for parity analysis. No interpretations included.*
