# Architecture

## System Overview

StormStack is a distributed game server platform with three core services:

```
                                    ┌─────────────────────────────────────┐
                                    │           Game Clients              │
                                    │  (Web Panel / Game Client / CLI)    │
                                    └──────────────┬──────────────────────┘
                                                   │
                              ┌────────────────────┼────────────────────┐
                              │                    │                    │
                              ▼                    ▼                    ▼
                    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
                    │  Thunder Auth   │  │ Thunder Control │  │ Thunder Engine  │
                    │   (port 8082)   │  │     Plane       │  │   (port 8080)   │
                    │                 │  │   (port 8081)   │  │                 │
                    │  • OAuth2/OIDC  │  │                 │  │  • Containers   │
                    │  • JWT tokens   │  │  • Node registry│  │  • ECS store    │
                    │  • User/Role    │  │  • Match routing│  │  • Game loop    │
                    │    management   │  │  • Autoscaling  │  │  • WebSocket    │
                    │  • Rate limiting│  │  • Module dist  │  │  • Hot-reload   │
                    └────────┬────────┘  └────────┬────────┘  └────────┬────────┘
                             │                    │                    │
                             │                    │                    │
                             ▼                    ▼                    ▼
                    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
                    │    MongoDB      │  │     Redis       │  │    MongoDB      │
                    │  (users, roles, │  │  (node registry,│  │   (snapshots,   │
                    │   tokens)       │  │   match state)  │  │    history)     │
                    └─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Service Boundaries

| Service | Purpose | Port | Storage |
|---------|---------|------|---------|
| **Thunder Auth** | OAuth2/OIDC authentication, user management, API tokens | 8082 | MongoDB |
| **Thunder Control Plane** | Cluster orchestration, node registry, match routing | 8081 | Redis |
| **Thunder Engine** | Game execution, ECS, WebSocket streaming | 8080 | MongoDB (optional) |

## Execution Containers

Each Thunder Engine node runs multiple isolated **Execution Containers**. A container is a complete runtime environment with:

- Its own **ClassLoader** (module isolation)
- Its own **EntityComponentStore** (ECS data)
- Its own **GameLoop** (tick processing)
- Its own **CommandQueue** (command execution)
- Multiple **Matches** (game instances sharing the container's modules)

```
Thunder Engine Node
    │
    ├── Container 1 (id: 1, name: "production")
    │       ├── ContainerClassLoader (isolated)
    │       │       └── Loaded JARs: EntityModule, RigidBodyModule, MyGameModule
    │       ├── EntityComponentStore (1M entity capacity)
    │       ├── GameLoop (tick thread, 60 FPS)
    │       ├── CommandQueue (per-tick execution)
    │       ├── SnapshotProvider (columnar format)
    │       └── Matches
    │               ├── Match 1 (players: 4, entities: 150)
    │               ├── Match 2 (players: 2, entities: 80)
    │               └── Match 3 (players: 6, entities: 200)
    │
    └── Container 2 (id: 2, name: "staging")
            ├── ContainerClassLoader (different module versions)
            ├── EntityComponentStore (separate)
            ├── GameLoop (30 FPS for testing)
            └── Matches
                    └── Match 4 (test match)
```

### Container Lifecycle

| Status | Description |
|--------|-------------|
| `CREATED` | Container initialized, modules loaded, not ticking |
| `STARTING` | Container starting up |
| `RUNNING` | Container actively processing ticks |
| `PAUSED` | Ticking stopped, state preserved |
| `STOPPING` | Container shutting down |
| `STOPPED` | Container stopped, resources released |

### Container Isolation

Each container provides:

- **ClassLoader Isolation**: Separate `ContainerClassLoader` per container with hybrid delegation (parent-first for engine APIs, child-first for module classes)
- **Independent Game Loop**: Containers tick at their own rate on their own thread
- **Separate ECS Store**: Entities and components are completely isolated between containers
- **Container-Scoped Commands**: Commands execute within their container context
- **Independent Lifecycle**: Start, stop, pause containers independently

See [ClassLoader Isolation](classloaders.md) for implementation details.

## ECS Architecture

The Entity Component System uses a columnar array-based storage with O(1) component access:

```
ArrayEntityComponentStore
    │
    ├── Entity Pool
    │       ├── Entity 0: [POSITION_X, POSITION_Y, HEALTH, ENTITY_TYPE, MATCH_ID]
    │       ├── Entity 1: [POSITION_X, POSITION_Y, VELOCITY_X, VELOCITY_Y, MATCH_ID]
    │       └── Entity 2: [POSITION_X, POSITION_Y, SPRITE_ID, MATCH_ID]
    │
    └── Component Arrays (columnar storage)
            ├── POSITION_X: [100.0, 200.0, 150.0, ...]  // Float array
            ├── POSITION_Y: [50.0, 75.0, 100.0, ...]
            ├── HEALTH: [100.0, Float.NaN, Float.NaN, ...]  // NaN = not present
            ├── MATCH_ID: [1.0, 1.0, 2.0, ...]  // Match isolation
            └── ...
```

### Store Decorator Pattern

The ECS store uses decorators for layered functionality:

```
ModuleScopedStore              ← Module-specific view with JWT auth
    └── LockingEntityComponentStore   ← Thread safety (ReentrantReadWriteLock)
            └── DirtyTrackingStore         ← Delta snapshot support
                    └── CachedEntityComponentStore    ← Query result caching
                            └── ArrayEntityComponentStore     ← Columnar storage
```

### Core Components

Every entity has these core components (defined in `CoreComponents.java`):

| Component | Purpose |
|-----------|---------|
| `ENTITY_ID` | Unique entity identifier |
| `MATCH_ID` | Match isolation - entities only visible within their match |
| `OWNER_ID` | Player ownership tracking |

## Tick-Based Simulation

The `GameLoop` processes ticks with this per-tick flow:

```
┌─────────────────────────────────────────────────────────────┐
│                         Game Tick                            │
├─────────────────────────────────────────────────────────────┤
│ 1. Execute Commands (up to maxCommandsPerTick, default 10k) │
│    └── CommandQueueExecutor.executeCommands()               │
├─────────────────────────────────────────────────────────────┤
│ 2. Run Systems (from all enabled modules)                   │
│    └── for each EngineSystem: system.updateEntities()       │
│    └── Errors logged but don't stop tick                    │
├─────────────────────────────────────────────────────────────┤
│ 3. Notify Tick Listeners (async, fire-and-forget)           │
│    └── Snapshot broadcasters, persistence, AI               │
├─────────────────────────────────────────────────────────────┤
│ 4. Record Metrics                                           │
│    └── Tick duration, system execution times                │
└─────────────────────────────────────────────────────────────┘
```

### Tick Control

```bash
# Manual tick control
curl -X POST http://localhost:8080/api/containers/1/tick \
  -H "Authorization: Bearer $TOKEN"

# Auto-advance at 60 FPS (16ms interval)
curl -X POST "http://localhost:8080/api/containers/1/play?intervalMs=16" \
  -H "Authorization: Bearer $TOKEN"

# Stop auto-advance
curl -X POST http://localhost:8080/api/containers/1/stop-auto \
  -H "Authorization: Bearer $TOKEN"
```

## WebSocket Streaming

Real-time game state streaming via WebSocket:

```
Client                                          Server
  │                                                │
  │─── Connect to /ws/.../snapshot?token=xxx ────▶│
  │                                                │
  │◀───────── Full Snapshot (JSON) ───────────────│  (on connect)
  │                                                │
  │◀───────── Snapshot Update ────────────────────│  (every broadcast interval)
  │◀───────── Snapshot Update ────────────────────│
  │◀───────── ...                                 │
```

### WebSocket Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/ws/containers/{id}/matches/{matchId}/snapshot` | Full snapshot stream |
| `/ws/containers/{id}/matches/{matchId}/delta` | Delta-compressed stream |
| `/ws/containers/{id}/matches/{matchId}/players/{playerId}/snapshot` | Player-scoped snapshot |
| `/ws/containers/{id}/matches/{matchId}/players/{playerId}/delta` | Player-scoped delta |
| `/ws/matches/{matchId}/players/{playerId}/errors` | Player error stream |
| `/containers/{id}/commands` | Command submission (subprotocol auth) |

### Snapshot Format

Snapshots use a columnar format for efficient JSON serialization:

```json
{
  "matchId": 1,
  "tick": 42,
  "data": {
    "EntityModule": {
      "ENTITY_TYPE": [1.0, 2.0, 1.0],
      "OWNER_ID": [1.0, 1.0, 2.0]
    },
    "MoveModule": {
      "POSITION_X": [100.0, 200.0, 150.0],
      "POSITION_Y": [50.0, 75.0, 100.0],
      "VELOCITY_X": [1.0, -1.0, 0.0]
    }
  }
}
```

Each array is columnar: index 0 = entity 0's value, index 1 = entity 1's value, etc.

### Delta Compression

For bandwidth efficiency, delta snapshots only transmit changes:

```json
{
  "matchId": 1,
  "fromTick": 100,
  "toTick": 105,
  "changedComponents": {
    "MoveModule": {
      "POSITION_X": {
        "42": 150.0,
        "43": 200.0
      }
    }
  },
  "addedEntities": [44, 45],
  "removedEntities": [41],
  "changeCount": 5
}
```

The delta algorithm:
1. Extract entity IDs using ENTITY_ID component
2. Compute added/removed entities (set difference)
3. For unchanged entities, compare component values
4. Record only changed values

## Authentication & Authorization

### OAuth2 Grant Types

Thunder Auth implements four OAuth2 grant types:

| Grant Type | Use Case |
|------------|----------|
| `password` | User login (username/password) |
| `client_credentials` | Service-to-service auth |
| `refresh_token` | Token refresh with rotation |
| `token_exchange` | Exchange API token for session JWT |

### Token Types

| Token | Purpose | Lifetime |
|-------|---------|----------|
| Session JWT | User authentication | 1 hour (configurable) |
| Refresh Token | Token renewal | 7 days (configurable) |
| API Token | Long-lived programmatic access | Custom (or never) |
| Match Token | Player match authorization | Match duration |
| Service Token | Service-to-service auth | 15 minutes |

### Scope-Based Authorization

Permissions use hierarchical scopes: `service.resource.operation`

Examples:
- `engine.container.create` - Create containers
- `auth.user.read` - Read users
- `engine.*` - All engine operations

Wildcard matching: `engine.*` matches `engine.container.create`

### Module Permission Scoping

Modules receive JWT tokens with component permissions:

| Permission | Description |
|------------|-------------|
| `OWNER` | Full read/write access to own components |
| `READ` | Read-only access to other modules' components |
| `WRITE` | Full read/write access to other modules' components |

See [Module System](module-system.md) for details.

## Control Plane Architecture

The Control Plane orchestrates Thunder Engine nodes:

```
                          ┌──────────────────────┐
                          │   Thunder Control    │
                          │       Plane          │
                          │                      │
                          │  ┌────────────────┐  │
                          │  │ Node Registry  │  │  ← Tracks all nodes
                          │  └────────────────┘  │
                          │  ┌────────────────┐  │
                          │  │   Scheduler    │  │  ← Selects best node
                          │  └────────────────┘  │
                          │  ┌────────────────┐  │
                          │  │ Match Router   │  │  ← Creates matches
                          │  └────────────────┘  │
                          │  ┌────────────────┐  │
                          │  │  Autoscaler    │  │  ← Scaling recommendations
                          │  └────────────────┘  │
                          │  ┌────────────────┐  │
                          │  │ Module Registry│  │  ← Module distribution
                          │  └────────────────┘  │
                          └──────────┬───────────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         │                           │                           │
         ▼                           ▼                           ▼
┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
│   Node 1        │        │   Node 2        │        │   Node 3        │
│ (Thunder Engine)│        │ (Thunder Engine)│        │ (Thunder Engine)│
│                 │        │                 │        │                 │
│ Containers: 3   │        │ Containers: 2   │        │ Containers: 1   │
│ Matches: 15     │        │ Matches: 8      │        │ Matches: 4      │
│ CPU: 45%        │        │ CPU: 30%        │        │ CPU: 15%        │
└─────────────────┘        └─────────────────┘        └─────────────────┘
```

### Node Registration Flow

1. Node starts and registers with Control Plane
2. Node sends heartbeats every 10 seconds (configurable)
3. Control Plane tracks node metrics and capacity
4. Nodes that miss heartbeats are expired (30 second TTL)

### Match Scheduling Algorithm

1. Filter to HEALTHY nodes only
2. Filter to nodes with available capacity
3. If preferred node specified and available, use it
4. Otherwise, select least-loaded node (lowest saturation)

Saturation = `activeContainers / maxContainers`

### Autoscaling

The autoscaler analyzes cluster saturation:

- **Scale Up**: When saturation exceeds 80% (configurable)
- **Scale Down**: When saturation falls below 30% (configurable)
- **Cooldown**: 300 seconds between scaling actions

See [Control Plane](control-plane.md) for configuration details.

## Data Flow: Match Creation

Complete flow for creating a match via the Control Plane:

```
Lightning CLI                Control Plane                    Node 1
      │                            │                            │
      │── POST /api/deploy ───────▶│                            │
      │   {modules: [...]}         │                            │
      │                            │                            │
      │                            │──── Scheduler selects ────▶│
      │                            │     least-loaded node      │
      │                            │                            │
      │                            │── POST /api/containers ───▶│
      │                            │   {modules: [...]}         │
      │                            │◀── 201 {containerId: 42} ──│
      │                            │                            │
      │                            │── POST /api/.../matches ──▶│
      │                            │   {modules: [...]}         │
      │                            │◀── 201 {matchId: 1} ───────│
      │                            │                            │
      │◀── 201 {matchId: node-1-42-1, ...} ─│                   │
      │                            │                            │
      │                            │                            │
      │═══════════════ WebSocket connection ═══════════════════▶│
      │                            │                            │
```

## Project Structure

```
stormstack/
├── thunder/                         # Backend services (Java)
│   ├── engine/                      # Thunder Engine
│   │   ├── core/                    # Domain + implementation
│   │   │   ├── container/           # ExecutionContainer, ClassLoader
│   │   │   ├── entity/              # ECS core components
│   │   │   ├── match/               # Match service
│   │   │   ├── command/             # Command queue
│   │   │   ├── snapshot/            # Snapshot/delta compression
│   │   │   └── store/               # EntityComponentStore
│   │   ├── provider/                # Quarkus REST/WebSocket
│   │   └── extensions/modules/      # Game modules
│   │
│   ├── auth/                        # Thunder Auth
│   │   ├── core/                    # OAuth2, JWT, RBAC
│   │   └── provider/                # Quarkus endpoints
│   │
│   └── control-plane/               # Thunder Control Plane
│       ├── core/                    # Scheduler, autoscaler
│       └── provider/                # Quarkus endpoints
│
├── lightning/                       # Client tools
│   ├── cli/                         # Go CLI
│   ├── webpanel/                    # React admin panel
│   └── rendering/                   # NanoVG GUI framework
│
└── docs/                            # Documentation
```

## Key Design Decisions

### Two-Module Pattern

Each Thunder service uses two Maven modules:
- **Core**: Pure domain logic, no framework annotations
- **Provider**: Quarkus-specific endpoints, persistence, DI

This enables:
- Framework-agnostic business logic
- Easier testing (no container needed for core)
- Potential future framework migration

### Float-Based ECS

All component values are stored as floats for:
- Cache efficiency (contiguous memory)
- Simple serialization
- O(1) access by entity ID
- `Float.NaN` as null sentinel

### Match Isolation via Component

Entities are isolated to matches via the `MATCH_ID` component rather than separate stores. This enables:
- Shared container resources
- Cross-match operations (admin tools)
- Efficient memory usage

### Async Tick Listeners

Tick listeners (snapshot broadcast, persistence) run asynchronously via a thread pool to avoid blocking the game loop. This is critical for I/O-bound operations.

### JWT-Based Module Auth

Modules authenticate during installation and receive JWT tokens encoding their component permissions. This enables stateless permission verification without central lookups during ECS operations.
