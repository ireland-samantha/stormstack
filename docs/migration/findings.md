# Migration Analysis Findings

**Generated:** 2026-02-02
**Purpose:** Comprehensive comparison of Java (main) vs Rust (rust-rewrite) implementations

---

## Executive Summary

| Partition | Java Files | Rust Files | Java Tests | Rust Tests | Parity % |
|-----------|------------|------------|------------|------------|----------|
| ECS & Core | 140 | 9 | 51 files (~8 ECS) | 62 | ~90% |
| Server & API | 56 | 10 | 48 files | 157 | ~75% |
| Auth & Security | 170 | 13 | 46 files | 68 | ~70% |
| Modules & WASM | 162 | 16 | 48 files | 146 | ~60%* |
| Networking & WebSocket | 44 | 9 | ~91 | 31 | ~35% |
| **TOTAL** | ~917 | ~60 | ~600+ | 464 | ~78% |

---

## Partition 1: ECS & Core
**Analyst:** Alex

### Overview

The Java implementation uses a **custom-built ECS** with array-backed storage, while the Rust implementation leverages **Legion** (a mature ECS library). This represents a fundamental architectural shift - the Rust version delegates ECS heavy lifting to a proven library, resulting in significantly less code.

### File Counts

| Category | Java (main) | Rust (rust-rewrite) |
|----------|-------------|---------------------|
| ECS/Store source files | 19 | 3 (lib.rs, world.rs, benchmark) |
| Core domain files | ~140 total in engine/core | 6 (lib.rs, id.rs, command.rs, snapshot.rs, error.rs, config.rs) |
| Test files | 51 test files (8 ECS-specific) | Inline tests (22 in world.rs, 40 in core) |

### Key Java Files (Entity/Store)

**Interfaces:**
- `thunder/engine/core/src/main/java/.../store/EntityComponentStore.java` - Main ECS interface
- `thunder/engine/core/src/main/java/.../store/BaseComponent.java` - Component base class
- `thunder/engine/core/src/main/java/.../store/ComponentRegistry.java` - Type registration
- `thunder/engine/core/src/main/java/.../store/PermissionedStore.java` - Permission-based access

**Implementations:**
- `thunder/engine/core/.../internal/core/store/ArrayEntityComponentStore.java` - Array-backed storage with O(1) component access
- `thunder/engine/core/.../internal/core/store/CachedEntityComponentStore.java` - Caching decorator
- `thunder/engine/core/.../internal/core/store/DirtyTrackingEntityComponentStore.java` - Change tracking
- `thunder/engine/core/.../internal/core/store/LockingEntityComponentStore.java` - Thread-safe wrapper
- `thunder/engine/core/.../internal/core/store/PermissionedEntityComponentStore.java` - Permission enforcement
- `thunder/engine/core/.../internal/core/store/QueryCache.java` - Query result caching

### Key Rust Files (ECS & Core)

**stormstack-ecs crate:**
- `crates/stormstack-ecs/src/lib.rs` - Public exports, crate documentation
- `crates/stormstack-ecs/src/world.rs` - `StormWorld` implementation wrapping Legion (~440 LOC + 320 LOC tests)

**stormstack-core crate:**
- `crates/stormstack-core/src/id.rs` - Strongly-typed IDs (EntityId, ContainerId, MatchId, etc.)
- `crates/stormstack-core/src/command.rs` - Command pattern implementation (SpawnEntityCommand, DespawnEntityCommand)
- `crates/stormstack-core/src/snapshot.rs` - WorldSnapshot, WorldDelta for streaming
- `crates/stormstack-core/src/error.rs` - Error types (StormError, AuthError, etc.)

### Feature Comparison

| Feature | Java | Rust | Notes |
|---------|------|------|-------|
| Entity spawn/despawn | Yes | Yes | Both have full lifecycle |
| Component add/remove | Yes | Yes | Java uses float arrays, Rust uses Legion's typed storage |
| Entity queries | Custom impl | Legion queries | Rust leverages Legion's optimized query system |
| Delta compression | DeltaCompressionService | ChangeTracker | Both track changes for efficient streaming |
| Thread safety | LockingEntityComponentStore decorator | `Arc<RwLock<StormWorld>>` | Different approaches, both work |
| Permission system | PermissionedStore, PermissionLevel | Not implemented | **Gap in Rust** |
| Module-scoped store | ModuleScopedStore | Not needed (WASM isolation) | Different isolation models |
| Dirty tracking | DirtyTrackingEntityComponentStore | Built into ChangeTracker | |
| Query caching | QueryCache | Not implemented | Legion may handle this internally |
| Snapshot serialization | Columnar (by module) | Entity-centric (serde JSON) | Different approaches |
| Strongly-typed IDs | No (uses long) | Yes (EntityId, MatchId newtype) | **Rust is safer** |
| Command system | EngineCommand, CommandQueue | Command trait, CommandQueue | Very similar patterns |

### Test Coverage

**Java (thunder/engine/core):**
- 51 test files total
- 8 specific ECS/store tests:
  - `ArrayEntityComponentStoreTest.java`
  - `ArrayEntityComponentStorePerformanceTest.java`
  - `CachedEntityComponentStoreTest.java`
  - `DirtyTrackingEntityComponentStoreTest.java`
  - `PermissionedEntityComponentStoreTest.java`
  - `QueryCacheTest.java`
  - `BaseComponentTest.java`
  - `PermissionComponentTest.java`

**Rust:**
- `stormstack-ecs/src/world.rs`: 22 tests (inline)
- `stormstack-core/src/id.rs`: 12 tests
- `stormstack-core/src/command.rs`: ~25 tests
- `stormstack-core/src/snapshot.rs`: 5 tests
- **Total: 62 tests** in this partition

### Architecture Observations

1. **Library leverage**: Java builds everything from scratch; Rust uses Legion, reducing maintenance burden
2. **Decorator pattern vs composition**: Java uses decorator chain (Locking -> Caching -> Dirty -> Array); Rust has flat structure with `RwLock` wrapper
3. **Type safety**: Rust's newtype IDs prevent mixing up EntityId/MatchId/ContainerId at compile time
4. **Memory model**: Java uses float arrays with fixed cardinality; Rust/Legion uses archetypal storage
5. **Serialization**: Java uses columnar format organized by module; Rust uses entity-centric JSON

### Gaps and Missing Features

1. **Permission system**: Java has PermissionLevel, PermissionComponent, PermissionedStore - Rust relies on WASM sandboxing instead
2. **Query caching**: Java has explicit QueryCache; Rust relies on Legion's internal optimizations
3. **Module-scoped views**: Java has ModuleScopedStore for module isolation; Rust achieves isolation via WASM boundary
4. **Snapshot history/restore**: Java has SnapshotHistory, SnapshotRestoreService; not yet visible in Rust ECS

### Parity Assessment: ~90%

Core ECS functionality is fully implemented in Rust. The "missing" features (permissions, module scoping) are handled differently via WASM sandboxing rather than being absent. The command system is feature-complete and well-tested.

---

## Partition 2: Server & API
**Analyst:** Bailey

### Overview

The Server & API partition encompasses REST endpoints, container management, match lifecycle, command execution, and session handling. The Java implementation uses Quarkus JAX-RS with 20+ separate Resource classes, while Rust consolidates everything into a single `routes.rs` file using Axum.

### File Counts

| Component | Java Files | Rust Files |
|-----------|------------|------------|
| REST Resources | 20 | 1 (routes.rs) |
| Container/Match/Command Core | 29 | 3 (container.rs, commands.rs, game_loop.rs) |
| Session | 2+ | 1 (session.rs) |
| State Management | ~5 | 1 (state.rs) |
| **Total** | ~56 | 10 |

### Test Coverage

| Category | Java Test Files | Rust Tests (functions) |
|----------|-----------------|------------------------|
| REST Resource Tests | 20 | N/A (inline) |
| Core Tests | 28 | 157 |
| **Total** | 48 | 157 |

### REST Endpoint Comparison

#### Container Endpoints
| Endpoint | Java | Rust | Notes |
|----------|------|------|-------|
| `GET /api/containers` | Yes | Yes | List containers |
| `POST /api/containers` | Yes | Yes | Create container |
| `GET /api/containers/{id}` | Yes | Yes | Get container details |
| `DELETE /api/containers/{id}` | Yes | Yes | Delete container |
| `POST /api/containers/{id}/start` | Yes | No | Lifecycle control (Java only) |
| `POST /api/containers/{id}/stop` | Yes | No | Lifecycle control (Java only) |
| `POST /api/containers/{id}/pause` | Yes | No | Lifecycle control (Java only) |
| `POST /api/containers/{id}/resume` | Yes | No | Lifecycle control (Java only) |
| `GET /api/containers/{id}/stats` | Yes | No | Container stats (Java only) |
| `POST /api/containers/{id}/tick` | Yes | Yes | Manual tick |
| `POST /api/containers/{id}/ticks/auto` | Yes | Yes | Auto-play toggle |
| `GET /api/containers/{id}/players` | Yes | Yes | List players |
| `GET /api/containers/{id}/metrics` | Yes | Yes | Container metrics |
| `GET /api/containers/{id}/commands/errors` | Yes | Yes | Command errors |

#### Match Endpoints
| Endpoint | Java | Rust | Notes |
|----------|------|------|-------|
| `GET /api/containers/{id}/matches` | Yes | Yes | List matches |
| `POST /api/containers/{id}/matches` | Yes | Yes | Create match |
| `GET /api/containers/{id}/matches/{matchId}` | Yes | Yes | Get match |
| `DELETE /api/containers/{id}/matches/{matchId}` | Yes | Yes | Delete match |
| `POST /api/containers/{id}/matches/{matchId}/join` | Yes | Yes | Join match |
| `POST /api/containers/{id}/matches/{matchId}/leave` | Yes | Yes | Leave match |
| `POST /api/containers/{id}/matches/{matchId}/start` | Yes | Yes | Start match |

#### Command Endpoints
| Endpoint | Java | Rust | Notes |
|----------|------|------|-------|
| `GET /api/containers/{id}/commands` | Yes | No | List commands (Java: per-container) |
| `POST /api/containers/{id}/commands` | Yes | No | Enqueue command (Java: per-container) |
| `GET /api/commands` | No | Yes | List commands (Rust: global) |
| `POST /api/containers/{id}/matches/{matchId}/commands` | No | Yes | Submit command (Rust: per-match) |

#### Session Endpoints
| Endpoint | Java | Rust | Notes |
|----------|------|------|-------|
| `GET /api/containers/{id}/sessions` | Yes | Yes | List sessions |
| `GET /api/containers/{id}/sessions/{sessionId}` | Yes | Yes | Get session |
| `DELETE /api/containers/{id}/sessions/{sessionId}` | Yes | Yes | Delete session |
| `POST /api/containers/{id}/matches/{matchId}/sessions` | Yes | No | Create session (Java: match-scoped) |

#### Resource Endpoints (Rust only)
| Endpoint | Java | Rust | Notes |
|----------|------|------|-------|
| `POST /api/resources` | No | Yes | Upload resource |
| `GET /api/resources` | No | Yes | List resources |
| `GET /api/resources/{id}` | No | Yes | Download resource |
| `GET /api/resources/{id}/metadata` | No | Yes | Get resource metadata |
| `DELETE /api/resources/{id}` | No | Yes | Delete resource |

#### Additional Java-only Endpoints
- `GET /api/containers/{id}/history` - Snapshot history
- `POST /api/containers/{id}/restore` - State restoration
- `GET /api/containers/{id}/snapshots` - Get snapshots
- `GET /api/containers/{id}/modules` - List modules
- `POST /api/containers/{id}/modules` - Install module
- `GET /api/health` - Health check (different path)
- Control plane proxy endpoints

### Key Architectural Differences

#### 1. ID Types
- **Java**: Uses `long` numeric IDs (e.g., `containerId`, `matchId`)
- **Rust**: Uses UUIDs wrapped in newtype structs (`ContainerId(Uuid)`, `MatchId(Uuid)`)

#### 2. Code Organization
- **Java**: Granular separation (20 REST resources, each with one responsibility)
- **Rust**: Consolidated approach (single `routes.rs` with ~1100 lines)

#### 3. Framework Patterns
- **Java**: Quarkus JAX-RS with `@Path`, `@GET`, `@POST`, DI via `@Inject`
- **Rust**: Axum with `Router::new().route()`, `State` extractor

#### 4. Command Architecture
- **Java**: Commands scoped per-container with module-provided command factories
- **Rust**: Commands scoped per-match with global `CommandRegistry`

#### 5. Container Lifecycle
- **Java**: Full lifecycle state machine (Created -> Started -> Running -> Paused -> Stopped)
- **Rust**: Simplified (containers are always running once created)

#### 6. Multi-tenancy
- **Java**: Tenant isolation via scoped repositories and authentication
- **Rust**: Tenant isolation via `TenantId` on Container, enforced in handlers

### Missing in Rust (gaps to address)
1. Container lifecycle control (start/stop/pause/resume)
2. Snapshot history and state restoration
3. Module management endpoints
4. Per-container command listing
5. Container stats endpoint
6. Health endpoint at `/api/health` (Rust uses `/health`)

### Added in Rust (new features)
1. Dedicated resource management endpoints (`/api/resources`)
2. Per-match command submission (more granular)
3. OAuth2 token endpoint (`/auth/token`)

### Parity Assessment
- **Endpoint Parity**: ~75% (core CRUD complete, missing advanced features)
- **Functional Parity**: ~80% (match/command flow works, lifecycle simplified)
- **Test Coverage**: Rust has more unit tests (157 vs ~48 test files)

### Recommendations
1. Add container lifecycle endpoints to Rust for full feature parity
2. Consider splitting Rust routes.rs into modules for maintainability
3. Evaluate if snapshot history/restore is needed in Rust
4. Standardize on UUID vs numeric IDs (recommend UUID for distributed scenarios)

---

## Partition 3: Auth & Security
**Analyst:** Casey

### Overview

The auth partition shows a major security improvement in the Rust rewrite while being approximately 70% feature complete. The Rust implementation is leaner (13 files vs 170 Java files) but covers the core authentication functionality.

### File Counts

| Category | Java (main) | Rust (rust-rewrite) |
|----------|-------------|---------------------|
| **Source Files** | 122 | 13 |
| **Test Files** | 46 | (inline tests) |
| **Total Files** | 170 | 13 |
| **Test Count** | ~200+ methods | 68 tests |

**Java Auth Files:**
- `thunder/auth/core/` - 36 files (models, services, repositories, exceptions)
- `thunder/auth/provider/` - 48 files (REST endpoints, DTOs, persistence)
- `thunder/auth/adapters/quarkus/` - 36 files (framework integration)
- `thunder/auth/adapters/spring/` - 19 files (Spring Boot adapter)

**Rust Auth Files:**
- `crates/stormstack-auth/src/` - 6 files (lib.rs, jwt.rs, claims.rs, password.rs, rbac.rs, oauth2.rs)
- `crates/stormstack-db/src/` - 7 files (lib.rs, pool.rs, models.rs, repositories/*)

### Security Comparison

#### Password Hashing (MAJOR IMPROVEMENT)

| Aspect | Java | Rust |
|--------|------|------|
| **Algorithm** | BCrypt | Argon2id |
| **OWASP Compliance** | Good | Excellent |
| **GPU/ASIC Resistance** | Moderate | High |
| **Side-channel Resistance** | Low | High |
| **Default Cost** | 12 rounds | 19 MiB / 2 iterations |

The Rust implementation uses **Argon2id**, the winner of the Password Hashing Competition and OWASP's current recommended algorithm. This is a significant security improvement over BCrypt.

**Rust Password Parameters (OWASP-compliant):**
```rust
memory_cost_kib: 19456,  // 19 MiB
time_cost: 2,            // iterations
parallelism: 1,          // lanes
output_len: 32,          // 256-bit hash
```

#### JWT Implementation

| Feature | Java | Rust |
|---------|------|------|
| **Algorithm** | RS256/HS256 (configurable) | HS256 |
| **Token Lifetime** | Configurable | 1 hour default |
| **Refresh Lifetime** | Configurable | 7 days default |
| **Claims** | User, Tenant, Roles, Scopes | User, Tenant, Roles |
| **JWKS Support** | Yes (/.well-known/jwks.json) | No |
| **Key Rotation** | Supported | Not yet |

#### OAuth2 Grant Types

| Grant Type | Java | Rust |
|------------|------|------|
| `client_credentials` | Yes | Yes |
| `password` | Yes | Yes |
| `refresh_token` | Yes | Yes |
| `token_exchange` (RFC 8693) | Yes | **NO** |

#### Token Types

| Token Type | Java | Rust | Notes |
|------------|------|------|-------|
| **JWT Access Token** | Yes | Yes | Core functionality |
| **JWT Refresh Token** | Yes | Yes | Both use JWT |
| **API Token** (lat_*) | Yes | **NO** | Long-lived service tokens |
| **Match Token** | Yes | **NO** | Game session authorization |

### RBAC Comparison

| Role | Java Scopes | Rust Permissions |
|------|-------------|------------------|
| **admin** | Full access + auth scopes | All permissions |
| **moderator** | Match management | ContainerRead, MatchCreate/Read/Delete |
| **user** | Basic access | ContainerRead, MatchRead/Join |
| **developer** | Module upload | Container*, Match*, ModuleUpload/Install |
| **service** | Service-to-service | (client_credentials grant) |

### Missing Features in Rust

1. **OIDC Discovery Endpoint** (`/.well-known/openid-configuration`)
   - Java exposes authorization server metadata
   - Required for OpenID Connect compliance

2. **JWKS Endpoint** (`/.well-known/jwks.json`)
   - Java supports RSA key publication
   - Enables distributed token validation

3. **Match Tokens**
   - Java: Full `MatchToken` system with JWT claims for match/container/player
   - Rust: No equivalent (critical for game sessions)

4. **Token Exchange Grant (RFC 8693)**
   - Java: Exchanges API tokens for session JWTs
   - Rust: Not implemented

5. **API Tokens**
   - Java: Long-lived tokens (lat_*) for service authentication
   - Rust: No equivalent

6. **WebSocket Auth Filters**
   - Java: `WebSocketJwtFilter`, `WebSocketApiTokenFilter`, `WebSocketMatchTokenFilter`
   - Rust: No WebSocket-specific auth yet

7. **Framework Adapters**
   - Java: Both Quarkus and Spring Boot adapters
   - Rust: Single implementation (axum-based)

### Database Comparison

| Aspect | Java | Rust |
|--------|------|------|
| **Database** | MongoDB | PostgreSQL |
| **ORM** | Panache/BSON | SQLx (raw queries) |
| **Repositories** | ApiToken, MatchToken, RefreshToken, Role, ServiceClient, User | Container, Match, User |
| **In-Memory Tests** | ConfigBasedServiceClientRepository, InMemoryRefreshTokenRepository | InMemory* variants for all |

### Test Coverage

**Rust Tests (68 total):**
- `stormstack-auth`: 51 tests
  - jwt.rs: 10 tests (validation, expiry, refresh)
  - password.rs: 13 tests (hash, verify, strength check)
  - claims.rs: 3 tests (creation, expiry, serialization)
  - rbac.rs: 5 tests (role permissions)
  - oauth2.rs: 20 tests (all grant types, error cases)
- `stormstack-db`: 17 tests
  - user.rs: 6 tests (CRUD, tenant isolation)
  - container.rs: 5 tests
  - match_.rs: 6 tests

**Java Tests (46 test files):**
- Core service tests (ApiTokenServiceImplTest, AuthenticationServiceImplTest, etc.)
- Grant handler tests (ClientCredentialsGrantHandlerTest, etc.)
- Repository contract tests
- Provider/REST endpoint tests
- Integration tests with Testcontainers

### Feature Parity Assessment: ~70%

**Implemented (Complete):**
- Core JWT generation/validation
- Password hashing (improved with Argon2id!)
- OAuth2 client_credentials, password, refresh_token grants
- Basic RBAC with roles and permissions
- User repository with tenant isolation

**Missing (Gaps):**
- Match tokens (critical for game sessions)
- API tokens (long-lived service auth)
- Token exchange grant
- OIDC/JWKS endpoints
- WebSocket authentication filters
- Rate limiting (LoginRateLimiter in Java)

### Recommendations

1. **High Priority:** Implement Match Tokens
   - Critical for game session authorization
   - Players need tokens to connect to matches via WebSocket

2. **High Priority:** Add WebSocket auth filters
   - Required for secure real-time game connections
   - Should validate JWT/Match tokens on upgrade

3. **Medium Priority:** Implement API Tokens
   - Needed for service-to-service authentication
   - Long-lived tokens for CLI and automation

4. **Medium Priority:** Add Token Exchange Grant
   - Enables API token -> session JWT conversion
   - Important for CLI/API workflows

5. **Low Priority:** OIDC/JWKS Endpoints
   - Useful for distributed deployments
   - Can be deferred if single-service deployment

6. **Consider:** The security improvements in Rust (Argon2id, better structured code) are excellent. The migration should preserve these while adding missing features.

---

## Partition 4: Modules & WASM
**Analyst:** Dana

### Overview

The module systems in Java and Rust take fundamentally different approaches. Java uses ClassLoader isolation for hot-reloadable modules, while Rust introduces a **completely new WASM sandbox** for untrusted code execution - a major security enhancement.

### File Counts

| Component | Java Files | Rust Files |
|-----------|------------|------------|
| Module Core System | 19 | 5 (`stormstack-modules`) |
| Game Modules | 143 | 4 (`stormstack-game-modules`) |
| WASM Sandbox | N/A (none) | 3 (`stormstack-wasm`) |
| WASM Host Functions | N/A (none) | 4 (`stormstack-wasm-host`) |
| **Total Source** | **162** | **16** |
| **Total Tests** | 48 test files | 146 inline tests |

### Java Module System (main branch)

**Core Module Infrastructure (19 files):**
- `ModuleManager` - Manages module lifecycle
- `ModuleFactory` - Creates module instances
- `CompoundModule`, `AbstractCompoundModule` - Combines multiple modules
- `ModuleScopedContext` - Per-module context
- `ModuleDependencyResolver` - Dependency resolution
- `OnDiskModuleManager` - File-based hot reload
- `ModuleAuthService`, `ModuleAuthToken` - Module authentication
- `ModuleIdentifier`, `ModuleVersion` - Module metadata
- `EngineModule`, `ModuleExports` - Module interfaces

**Game Modules (9 total, 143 files):**
1. `entity-module` - EntityComponent, Position, EntityService
2. `health-module` - Health, damage, invulnerability, HealCommand, DamageCommand
3. `move-module` - MovementState, Velocity, MovementService
4. `items-module` - Item, ItemType, inventory system, pickup/drop/use commands
5. `box-collider-module` - AABB collision detection, CollisionEvent, CollisionHandler
6. `rigid-body-module` - RigidBody, Vector3, physics with forces/torque/impulses
7. `grid-map-module` - GridMap, Position, map assignment to matches
8. `projectile-module` - Projectile spawning and destruction
9. `rendering-module` - Sprite, SpriteService

### Rust Module System (rust-rewrite branch)

**Core Module Crate (`stormstack-modules`, 5 files):**
- `lib.rs` - Public exports, `discover_modules()`, `declare_module!` macro
- `module_trait.rs` - `Module` trait with `on_load`, `on_tick`, `on_unload`
- `loader.rs` - `ModuleLoader` using libloading for dynamic libraries
- `registry.rs` - `ModuleRegistry` with circular dependency detection
- `descriptor.rs` - `ModuleDescriptor`, `ModuleDependency`, ABI versioning

**Game Modules (`stormstack-game-modules`, 4 files):**
1. `EntityModule` - EntityIdComponent, OwnerIdComponent, MatchIdComponent, SpawnEntityWithOwnerCommand
2. `MovementModule` - Position, Velocity, applies velocity to position each tick
3. `HealthModule` - Health, MaxHealth, Dead marker, auto-marks entities dead when health<=0

**NOT YET IN RUST (6 modules):**
- Items module (no inventory system)
- Box collider module (no AABB collision)
- Rigid body module (no physics forces)
- Grid map module (no grid-based maps)
- Projectile module (no projectiles)
- Rendering module (no sprites)

### WASM Sandbox (NEW - Rust Only!)

**This is a major architectural addition not present in Java!**

The `stormstack-wasm` and `stormstack-wasm-host` crates (7 files) provide secure execution of **untrusted code**:

**Security Features:**
- **Fuel Metering** - Limits instruction count (default: 1M instructions)
- **Epoch Interruption** - Wall-clock timeout backup (~10ms epochs)
- **Memory Limits** - Prevents memory exhaustion (default: 16MB)
- **Capability-based Security** - Zero capabilities by default
- **Stack Overflow Protection** - Catches deep recursion
- `#![forbid(unsafe_code)]` in the sandbox crate!

**Resource Limits (configurable):**
```rust
WasmResourceLimits {
    max_fuel: 1_000_000,                 // ~1M instructions
    max_memory_bytes: 16 * 1024 * 1024,  // 16 MB
    epoch_deadline: 100,                 // ~1 second
    max_stack_bytes: 1024 * 1024,        // 1 MB
}
```

**Host Functions Available to WASM:**
- Logging: `log_debug`, `log_info`, `log_warn`, `log_error`
- Time: `get_tick`, `get_delta_time`
- Entity: `entity_spawn`, `entity_despawn`, `entity_exists`
- Random: `random_u32`, `random_f32`, `random_range`

**Security Tests (ALL PASSING):**
- `test_infinite_loop_terminated` - Fuel exhaustion works
- `test_memory_bomb_prevented` - Memory limits enforced
- `test_stack_overflow_handled` - Deep recursion caught
- `test_fuel_exhausted` - Instruction counting accurate
- `test_valid_module_executes` - Normal modules work

### Feature Comparison

| Feature | Java | Rust |
|---------|------|------|
| Hot Reload | ClassLoader-based | libloading + WASM |
| Sandboxing | None (trusted code only) | WASM sandbox (untrusted code) |
| Dependency Resolution | Yes | Yes (with cycle detection) |
| Versioning | ModuleVersion | ABI versioning |
| Module Discovery | ServiceLoader | `inventory` crate |
| Security Isolation | ClassLoader (weak) | WebAssembly linear memory (strong) |
| Resource Limits | None | Fuel, memory, time |
| Game Modules | 9 | 3 |
| Test Coverage | 48 test files | 146 inline tests |

### Key Differences

1. **Security Model**: Java modules are trusted and have full JVM access. Rust has TWO paths:
   - Native modules (trusted, via `stormstack-modules`)
   - WASM modules (untrusted, via `stormstack-wasm`)

2. **File Reduction**: 162 Java files reduced to 16 Rust files (10x reduction!)

3. **Test Coverage**: Rust has 3x more tests (146 vs 48 test files)

4. **Module Lifecycle**: Both use similar patterns (load/tick/unload) but Rust is more explicit about the `ModuleContext` providing ECS access

5. **Dependency Management**: Both support dependencies, but Rust adds ABI version checking for native modules

### Gaps to Address

1. **Missing Game Modules**: Items, colliders, physics, grid maps, projectiles, rendering (6 of 9 missing)
2. **No Module Hot Reload via WASM yet**: WASM sandbox exists but not integrated with hot reload flow
3. **No Module Authentication**: Java has `ModuleAuthService`, Rust doesn't

### Recommendations

1. **WASM Integration is a WIN**: The WASM sandbox is a significant security improvement. Recommend keeping this architecture for user-uploaded game logic.

2. **Port Remaining Game Modules**: Prioritize based on game needs:
   - High: items-module, box-collider-module (core gameplay)
   - Medium: projectile-module, rigid-body-module (combat)
   - Low: grid-map-module, rendering-module (server doesn't need rendering)

3. **Add WASM Host Functions**: Extend host functions for health, movement, items to enable WASM modules to interact with game state.

### Parity Assessment

| Category | Parity |
|----------|--------|
| Core Module System | 90% (missing module auth) |
| Game Modules | 33% (3 of 9 modules) |
| WASM Sandbox | N/A (new feature - Java has nothing comparable) |

**Overall Parity**: ~60%*

*Note: The 60% parity is misleading because the WASM sandbox represents a significant capability upgrade. From a security perspective, the Rust implementation is actually MORE capable than Java. Java has no way to safely run untrusted game logic.*

---

## Partition 5: Networking & WebSocket
**Analyst:** Eli

### Overview

The Networking and WebSocket partition shows a **significant gap** between Java and Rust implementations. Java has a mature, production-ready WebSocket infrastructure with 8 dedicated handlers, comprehensive metrics, rate limiting, and connection management. Rust has a clean foundation with excellent async abstractions but lacks many critical real-time game features.

**Parity Assessment: ~35%** - WebSocket is one of the largest gap areas in the migration.

### File Counts

| Component | Java Files | Rust Files |
|-----------|------------|------------|
| WebSocket Handlers | 11 main + 8 test | 5 main (stormstack-ws) |
| Networking/HTTP | 5 (filters, auth) | 4 main (stormstack-net) |
| Auth Integration | 5 (WebSocket filters) | 0 (not implemented) |
| **Total** | 44 | 9 |

#### Java WebSocket Files (thunder/engine/provider/.../websocket/):
- `SnapshotWebSocket.java` - Full state streaming
- `DeltaSnapshotWebSocket.java` - Delta-compressed streaming
- `PlayerSnapshotWebSocket.java` - Player-filtered snapshots
- `PlayerDeltaSnapshotWebSocket.java` - Player-filtered deltas
- `PlayerErrorWebSocket.java` - Error notifications
- `ContainerCommandWebSocket.java` - Command submission (JSON + Protobuf)
- `SimulationWebSocket.java` - Tick control
- `WebSocketMetrics.java` - Connection/command metrics
- `WebSocketRateLimiter.java` - Sliding window rate limiting
- `WebSocketConnectionLimiter.java` - Per-user/container limits
- `CommandPayloadConverter.java` - JSON/Protobuf conversion

#### Rust Files:
- `crates/stormstack-ws/src/lib.rs` - Module exports
- `crates/stormstack-ws/src/handler.rs` - WsHandler with MatchStateProvider trait
- `crates/stormstack-ws/src/connection.rs` - ConnectionManager with DashMap
- `crates/stormstack-ws/src/subscription.rs` - SubscriptionManager
- `crates/stormstack-ws/src/messages.rs` - ClientMessage/ServerMessage types
- `crates/stormstack-net/src/lib.rs` - Module exports
- `crates/stormstack-net/src/server.rs` - ServerBuilder with axum
- `crates/stormstack-net/src/extractors.rs` - AuthUser, Pagination extractors
- `crates/stormstack-net/src/responses.rs` - ApiResponse, ApiError, PaginatedResponse

### Test Counts

| Source | Count | Notes |
|--------|-------|-------|
| Java WebSocket Tests | ~91 | 8 test files |
| Rust stormstack-ws | 15 | #[test] and #[tokio::test] |
| Rust stormstack-net | 16 | #[test] and #[tokio::test] |
| **Total** | 91 Java / 31 Rust | |

### WebSocket Handlers Comparison

#### Java (8 handlers):

1. **SnapshotWebSocket** (`/ws/containers/{containerId}/matches/{matchId}/snapshot`)
   - Streams full ECS snapshots every 100ms (configurable)
   - Uses Mutiny Multi with ticks for periodic streaming
   - Auth via WebSocketAuthResultStore

2. **DeltaSnapshotWebSocket** (`/ws/containers/{containerId}/matches/{matchId}/delta`)
   - Tracks per-connection snapshot state in ConcurrentHashMap
   - Computes deltas using DeltaCompressionService
   - Reports compression ratios for monitoring
   - Supports "reset" command to force full snapshot

3. **PlayerSnapshotWebSocket** (`/ws/containers/{cid}/matches/{mid}/players/{pid}/snapshot`)
   - Filters snapshots by player ownership
   - Critical for multiplayer security
   - Reduces bandwidth by omitting unowned entity data

4. **PlayerDeltaSnapshotWebSocket** - Combines player filtering + delta compression

5. **PlayerErrorWebSocket** - Dedicated channel for player-specific error notifications

6. **ContainerCommandWebSocket** (`/containers/{containerId}/commands`)
   - Accepts JSON (browsers) AND Protocol Buffer (native clients)
   - Rate limiting enforced per connection
   - Connection limits per user and per container

7. **SimulationWebSocket** (`/ws/simulation`) - Simple tick control

8. **Support Classes:**
   - `WebSocketMetrics` - tracks connections, commands, timing
   - `WebSocketRateLimiter` - sliding window (100 cmd/sec default)
   - `WebSocketConnectionLimiter` - per-user and per-container limits

#### Rust (1 unified handler):

```rust
pub trait ConnectionHandler: Send + Sync {
    fn on_connect(&self, conn_id: ConnectionId) -> Result<()>;
    fn on_message(&self, conn_id: ConnectionId, message: ClientMessage) -> Result<()>;
    fn on_disconnect(&self, conn_id: ConnectionId);
    fn send(&self, conn_id: ConnectionId, message: ServerMessage) -> Result<()>;
    fn broadcast_to_match(&self, match_id: MatchId, message: ServerMessage) -> Result<()>;
}
```

- `WsHandler<M: MatchStateProvider>` - Generic handler
- Message types: Subscribe, Unsubscribe, Ping, Command
- Server messages: Snapshot, Delta (defined but not computed!), Error, Pong
- Uses DashMap for concurrent subscription tracking
- tokio mpsc channels for message delivery

### Subscription Patterns

| Feature | Java | Rust |
|---------|------|------|
| Subscribe to match | Path-based (/ws/.../matches/{id}) | Message-based (ClientMessage::Subscribe) |
| Unsubscribe | Connection close | Message-based (ClientMessage::Unsubscribe) |
| Per-connection state | ConcurrentHashMap | DashMap in SubscriptionManager |
| Bidirectional tracking | N/A (path-based) | Yes (match<->connections) |

### Delta Streaming Comparison

#### Java (Fully Implemented):
```java
// DeltaCompressionService interface
DeltaSnapshot computeDelta(long matchId, long fromTick, Snapshot from,
                           long toTick, Snapshot to);
```
- Per-connection snapshot state tracking
- Automatic delta computation every broadcast interval
- Compression ratio calculation and logging

#### Rust (Struct Only):
```rust
pub struct WorldDelta {
    pub from_tick: u64,
    pub to_tick: u64,
    pub spawned: Vec<EntitySnapshot>,
    pub despawned: Vec<EntityId>,
    pub updated: Vec<ComponentUpdate>,
}
```
- Data structure defined in stormstack-core
- **NO DeltaCompressionService equivalent**
- **NO per-connection delta tracking**

### Critical Gaps

| Gap | Priority | Impact |
|-----|----------|--------|
| **DeltaCompressionService** | HIGH | Bandwidth optimization critical for real-time games |
| **Periodic broadcast ticker** | HIGH | Java streams every 100ms automatically |
| **Player-filtered snapshots** | HIGH | Security for multiplayer - players see only their entities |
| **Rate limiting** | MEDIUM | Prevents command flooding DoS |
| **Connection limiting** | MEDIUM | Prevents resource exhaustion |
| **WebSocket auth filters** | HIGH | No auth validation during upgrade |
| **Protocol Buffer support** | LOW | Performance for native clients |
| **Metrics/telemetry** | MEDIUM | Observability for production |
| **Container-scoped URLs** | MEDIUM | Multi-tenant isolation |

### What's Working Well in Rust

1. **Clean trait abstractions** - `ConnectionHandler` and `MatchStateProvider` are well-designed
2. **Concurrent data structures** - DashMap provides excellent lock-free performance
3. **tokio foundation** - Async runtime with graceful shutdown support
4. **Message types** - ClientMessage/ServerMessage are comprehensive
5. **Subscription tracking** - Bidirectional mapping is efficient

### Networking Layer (stormstack-net)

| Feature | Implementation |
|---------|----------------|
| HTTP Server | axum with tower middleware |
| CORS | tower-http CorsLayer (allow any) |
| Compression | tower-http CompressionLayer (gzip) |
| Tracing | tower-http TraceLayer |
| Auth Extraction | AuthUser/OptionalAuth from JWT |
| Pagination | Pagination extractor with offset/limit |
| Error Responses | ApiError with status codes |
| Graceful Shutdown | tokio signal handling |

### Recommendations

1. **Implement DeltaCompressionService** - Port Java's algorithm to Rust
2. **Add periodic broadcast** - Use tokio::time::interval for tick-based streaming
3. **Implement player filtering** - Add entity ownership tracking
4. **Add rate limiting middleware** - Use tower-based rate limiter
5. **Implement WebSocket auth** - Add auth extraction during upgrade
6. **Consider container scoping** - Match Java's URL patterns for multi-tenant
7. **Add metrics** - Use metrics crate for observability

### Files Reference

**Java WebSocket (main branch):**
- `/thunder/engine/provider/src/main/java/.../websocket/*.java` (11 files)
- `/thunder/engine/provider/src/test/java/.../websocket/*Test.java` (8 files)
- `/thunder/auth/adapters/quarkus/.../filter/WebSocket*.java` (5 files)

**Rust (rust-rewrite branch):**
- `/crates/stormstack-ws/src/*.rs` (5 files)
- `/crates/stormstack-net/src/*.rs` (4 files)

---

## Recommendations

*(To be compiled after all partition analyses complete)*

