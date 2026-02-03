# Feature Comparison: main (Java) vs rust-rewrite

**Generated:** 2026-02-02
**Last Updated:** 2026-02-02 (comprehensive analysis)
**Purpose:** Identify gaps between Java and Rust implementations for complete parity

---

## Executive Summary

| Metric | Java (main) | Rust (rust-rewrite) | Parity |
|--------|-------------|---------------------|--------|
| Source Files | 917 | ~85 | 9% |
| Test Files | 279 | ~60 | 22% |
| Services | 47 | ~20 | 43% |
| REST Endpoints | 23 resource classes | 12 routes | 52% |
| WebSocket Handlers | 8 | 1 | 13% |
| Tests | ~600+ | 335 | 56% |
| **Core Feature Parity** | 100% | **78%** | |

### Completed Features
- ✅ ECS with Legion (15 tests)
- ✅ WASM sandbox with wasmtime (23 tests) - **BETTER than Java**
- ✅ Native module hot-reload (29 tests)
- ✅ JWT authentication with RBAC (51 tests)
- ✅ WebSocket infrastructure (15 tests)
- ✅ Container management (32 tests)
- ✅ Match management (17 tests)
- ✅ Command system (27 tests)
- ✅ Game loop (12 tests)
- ✅ REST API core endpoints (30 tests)
- ✅ PostgreSQL persistence (17 tests)
- ✅ OAuth2 token service (20 tests)
- ✅ Resource management (10 tests)

### Major Gaps Remaining
- ❌ Control Plane (95 Java files, 10 services)
- ❌ Player Sessions
- ❌ Snapshot Delta Compression
- ❌ Additional WebSocket handlers (7 of 8)
- ❌ OIDC/JWKS endpoints
- ❌ 9 Builtin Game Modules

---

## Detailed Gap Analysis

### 1. Control Plane (NOT STARTED - 95 Java files)

The entire control plane subsystem is not implemented in Rust.

**Java Services:**
| Service | Purpose | Rust Status |
|---------|---------|-------------|
| `ClusterService` | Cluster status aggregation | ❌ |
| `NodeRegistryService` | Node registration & heartbeats | ❌ |
| `SchedulerService` | Match placement across nodes | ❌ |
| `MatchRoutingService` | Route requests to nodes | ❌ |
| `AutoscalerService` | Scale cluster based on load | ❌ |
| `ModuleDistributionService` | Distribute modules to nodes | ❌ |
| `ModuleRegistryService` | Global module registry | ❌ |
| `NodeProxyService` | Proxy requests to nodes | ❌ |
| `DashboardService` | Dashboard metrics | ❌ |
| `ClusterMetricsService` | Real-time metrics | ❌ |

**Java REST Endpoints (Control Plane):**
- `POST /cluster/nodes/register` - Node heartbeat
- `GET /cluster/nodes` - List nodes
- `POST /cluster/matches/create` - Cluster-wide match
- `GET /cluster/matches/{id}` - Get match location
- `POST /cluster/matches/{id}/join` - Join via control plane
- `POST /cluster/deploy` - Module deployment
- `GET /cluster/metrics/dashboard` - Dashboard metrics
- `GET/POST /cluster/autoscale` - Autoscale settings

**Priority:** LOW (single-node deployment works without this)

---

### 2. Player Sessions (NOT STARTED)

**Java Implementation:**
| Class | Purpose | Rust Status |
|-------|---------|-------------|
| `PlayerSession` | Player-match session entity | ❌ |
| `PlayerSessionService` | Session lifecycle | ❌ |
| `PlayerSessionRepository` | Session persistence | ❌ |
| `InMemorySessionRepository` | In-memory store | ❌ |

**REST Endpoints:**
- `GET /api/containers/{id}/sessions` - List sessions
- `GET /api/containers/{id}/sessions/{sessionId}` - Get session

**Priority:** MEDIUM (needed for player state tracking)

---

### 3. Snapshot System (PARTIAL)

**Java Implementation:**
| Class | Rust Status | Notes |
|-------|-------------|-------|
| `Snapshot` | ✅ | `WorldSnapshot` |
| `DeltaSnapshot` | ⚠️ | `WorldDelta` exists but no compression |
| `SnapshotHistory` | ❌ | Not implemented |
| `DeltaCompressionService` | ❌ | Not implemented |
| `SnapshotRestoreService` | ❌ | Not implemented |
| `SnapshotFilter` | ❌ | Player filtering not implemented |
| `SnapshotPersistenceConfig` | ❌ | Not implemented |
| `MongoSnapshotPersistence` | ❌ | Not implemented |

**REST Endpoints:**
- `GET /api/containers/{id}/snapshots/{matchId}` - ⚠️ Partial
- `POST /api/containers/{id}/snapshots/restore` - ❌
- `GET /api/containers/{id}/history` - ❌

**Priority:** MEDIUM (delta compression improves bandwidth)

---

### 4. WebSocket Handlers (1 of 8 implemented)

**Java WebSocket Endpoints:**
| Endpoint | Purpose | Rust Status |
|----------|---------|-------------|
| `/ws/.../snapshot` | Full ECS snapshots | ✅ Basic |
| `/ws/.../snapshot/delta` | Delta-compressed | ❌ |
| `/ws/.../snapshot/player/{id}` | Player-scoped | ❌ |
| `/ws/.../snapshot/player/{id}/delta` | Player delta | ❌ |
| `/ws/.../commands` | Command streaming | ❌ |
| `/ws/.../simulation` | Simulation state | ❌ |
| `/ws/.../errors/{playerId}` | Error streaming | ❌ |

**WebSocket Infrastructure (Java):**
- `WebSocketMetrics` - Metrics per connection | ❌
- `WebSocketConnectionLimiter` - Connection pooling | ❌
- `WebSocketRateLimiter` - Rate limiting | ❌
- JWT/API/Match token filters | ⚠️ Partial

**Priority:** HIGH (needed for client communication)

---

### 5. Additional REST Endpoints (12 missing)

**Container Operations:**
| Endpoint | Java | Rust |
|----------|:----:|:----:|
| `POST /api/containers/{id}/ticks/auto` | ✅ | ❌ |
| `GET /api/containers/{id}/commands/errors` | ✅ | ❌ |
| `GET /api/containers/{id}/players` | ✅ | ❌ |
| `GET /api/containers/{id}/sessions` | ✅ | ❌ |
| `POST /api/containers/{id}/snapshots/restore` | ✅ | ❌ |
| `GET /api/containers/{id}/history` | ✅ | ❌ |
| `GET /api/containers/{id}/metrics` | ✅ | ❌ |

**Auth Endpoints:**
| Endpoint | Java | Rust |
|----------|:----:|:----:|
| `POST /auth/match-tokens` | ✅ | ❌ |
| `GET /.well-known/openid-configuration` | ✅ | ❌ |
| `GET /.well-known/jwks.json` | ✅ | ❌ |
| `GET /userinfo` | ✅ | ❌ |
| `POST /auth/token/exchange` | ✅ | ❌ |

**Priority:** MEDIUM

---

### 6. Auth Features (MOSTLY COMPLETE)

**JWT:**
| Feature | Java | Rust | Status |
|---------|:----:|:----:|--------|
| HMAC-256 signing | ✅ | ✅ | Complete |
| RSA-256 signing | ✅ | ❌ | Gap |
| Token validation | ✅ | ✅ | Complete |
| Token refresh | ✅ | ✅ | Complete |
| JWKS endpoint | ✅ | ❌ | Gap |
| OIDC discovery | ✅ | ❌ | Gap |

**OAuth2:**
| Grant Type | Java | Rust | Status |
|------------|:----:|:----:|--------|
| Password | ✅ | ✅ | Complete |
| Refresh token | ✅ | ✅ | Complete |
| Client credentials | ✅ | ✅ | Complete |
| Authorization code | ✅ | ✅ | Complete |
| Token exchange | ✅ | ❌ | Gap |

**Token Types:**
| Type | Java | Rust | Status |
|------|:----:|:----:|--------|
| Access token | ✅ | ✅ | Complete |
| Refresh token | ✅ | ✅ | Complete |
| API token | ✅ | ✅ | Complete |
| Match token | ✅ | ❌ | Gap |

**Priority:** LOW (core auth complete)

---

### 7. Game Modules (0 of 9 implemented)

**Java Builtin Modules:**
| Module | Purpose | Components | Rust Status |
|--------|---------|------------|-------------|
| `EntityModule` | Base entity lifecycle | ENTITY_ID, OWNER_ID | ❌ |
| `MovementModule` | Position/velocity | POSITION, VELOCITY | ❌ |
| `HealthModule` | Health/damage | HEALTH, MAX_HEALTH | ❌ |
| `BoxColliderModule` | AABB collision | BOX_COLLIDER, COLLISION | ❌ |
| `ProjectileModule` | Projectile mechanics | PROJECTILE, DAMAGE | ❌ |
| `ItemsModule` | Inventory system | ITEM, INVENTORY | ❌ |
| `RenderModule` | Sprite rendering | SPRITE, Z_INDEX | ❌ |
| `RigidBodyModule` | Physics simulation | MASS, FORCE | ❌ |
| `GridMapModule` | Map/grid management | GRID_POSITION, TILE | ❌ |

**Module Features:**
| Feature | Java | Rust | Status |
|---------|:----:|:----:|--------|
| Module interface | ✅ | ✅ | Complete |
| Module context/DI | ✅ | ⚠️ | Partial |
| Module exports | ✅ | ❌ | Gap |
| Compound modules | ✅ | ❌ | Gap |
| Module commands | ✅ | ❌ | Gap |

**Priority:** HIGH (needed for game functionality)

---

### 8. ECS Features (MOSTLY COMPLETE)

| Feature | Java | Rust | Status |
|---------|:----:|:----:|--------|
| Entity creation | ✅ | ✅ | Complete |
| Component storage | ✅ | ✅ | Complete (Legion) |
| O(1) access | ✅ | ✅ | Complete |
| Change tracking | ✅ | ✅ | Complete |
| Columnar storage | ✅ | ✅ | Complete (Legion) |
| Component permissions | ✅ | ❌ | Gap |
| Module-specific components | ✅ | ❌ | Gap |
| Player filtering | ✅ | ❌ | Gap |

**Priority:** MEDIUM

---

### 9. Command System (MOSTLY COMPLETE)

| Feature | Java | Rust | Status |
|---------|:----:|:----:|--------|
| Command queue | ✅ | ✅ | Complete |
| Command execution | ✅ | ✅ | Complete |
| Command builders | ✅ | ⚠️ | 2 of many |
| Module commands | ✅ | ❌ | Gap |
| Command errors | ✅ | ❌ | Gap |
| Error collection | ✅ | ❌ | Gap |

**Priority:** MEDIUM

---

## Implementation Priority

### Tier 1: CRITICAL (Blocks game functionality)
1. **Game Modules** - At least EntityModule, MovementModule, HealthModule
2. **Delta WebSocket** - For efficient state streaming
3. **Player-scoped snapshots** - For client filtering

### Tier 2: HIGH (Full feature parity)
4. **Player Sessions** - Session tracking
5. **Snapshot restoration** - State recovery
6. **Match tokens** - Match-scoped auth
7. **Auto-play toggle** - Continuous simulation

### Tier 3: MEDIUM (Production features)
8. **OIDC/JWKS** - Standard auth discovery
9. **Command errors** - Error reporting
10. **Metrics endpoints** - Observability
11. **Component permissions** - Multi-tenant security

### Tier 4: LOW (Cluster features)
12. **Control Plane** - Multi-node deployment
13. **Autoscaling** - Dynamic scaling
14. **Module distribution** - Cluster-wide modules

---

## Test Coverage Gap

| Category | Java Tests | Rust Tests | Gap |
|----------|-----------|------------|-----|
| ECS | ~50 | 22 | 28 |
| Auth | ~60 | 51 | 9 |
| Modules | ~30 | 29 | 1 |
| WebSocket | ~40 | 15 | 25 |
| Container | ~40 | 32 | 8 |
| Match | ~35 | 17 | 18 |
| Command | ~30 | 27 | 3 |
| Snapshot | ~25 | 5 | 20 |
| Control Plane | ~50 | 0 | 50 |
| API acceptance | ~50 | 30 | 20 |
| E2E | ~29 | 0 | 29 |
| **TOTAL** | ~439 | 228 | 211 |

**Note:** Rust has 107 additional tests (WASM security, core types, resources, OAuth2) not in Java.

---

## Recommended Implementation Order

### Phase 1: Game Functionality (Est: 50 tests)
```
1. EntityModule with basic components
2. MovementModule with position/velocity
3. HealthModule with damage system
4. Delta WebSocket endpoint
5. Player-scoped snapshot filtering
```

### Phase 2: Production Features (Est: 40 tests)
```
6. Player session management
7. Snapshot persistence and restore
8. Match tokens
9. OIDC discovery and JWKS
10. Command error collection
```

### Phase 3: Advanced Features (Est: 30 tests)
```
11. Remaining game modules (collision, projectiles, items)
12. Component permissions
13. Module exports/compound modules
14. Additional WebSocket handlers
```

### Phase 4: Cluster (Est: 50 tests)
```
15. Node registry and heartbeats
16. Match routing
17. Module distribution
18. Autoscaling
```

---

## Current Status Summary

| Component | Completion | Tests |
|-----------|------------|-------|
| stormstack-auth | 90% | 51 |
| stormstack-core | 95% | 36 |
| stormstack-db | 100% | 17 |
| stormstack-ecs | 85% | 22 |
| stormstack-modules | 80% | 29 |
| stormstack-net | 100% | 16 |
| stormstack-server | 75% | 122 |
| stormstack-test-utils | 100% | 1 |
| stormstack-wasm | 100% | 13 |
| stormstack-wasm-host | 100% | 10 |
| stormstack-ws | 70% | 15 |
| **OVERALL** | **78%** | **335** |

The Rust rewrite has solid infrastructure and exceeds Java in WASM security. Primary gaps are game modules, advanced WebSocket features, and the control plane.
