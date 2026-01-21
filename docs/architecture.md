# Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         Clients                              │
│    (Debug GUI / Web Dashboard / Game Client / Tests)         │
└─────────────────┬───────────────────────┬───────────────────┘
                  │ REST + JWT            │ WebSocket + Delta
                  ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Quarkus Web API                           │
│ /api/auth  /api/containers  /api/containers/{id}/matches ... │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │              JWT Authentication (SmallRye JWT)           │ │
│ │     • Dynamic RBAC: admin, command_manager, view_only   │ │
│ │     • Role hierarchy with includes                      │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Container Manager                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              Execution Container 1                      │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ ClassLoader  │  │  Game Loop   │  │ Command Queue│  │ │
│  │  │  (isolated)  │  │  (own thread)│  │              │  │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │ │
│  │         │                 │                  │          │ │
│  │         ▼                 ▼                  ▼          │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │     EntityComponentStore (container-scoped)      │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │  Matches: [1, 2, 3]  Status: RUNNING                   │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              Execution Container 2                      │ │
│  │  ... (independent ClassLoader, GameLoop, Store)        │ │
│  │  Matches: [4, 5]  Status: PAUSED                       │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    MongoDB (Optional)                        │
│         Time-series collection for snapshot history          │
└─────────────────────────────────────────────────────────────┘
```

## Execution Containers

Execution Containers provide complete runtime isolation for game instances:

```
ContainerManager
    │
    ├── Container 1 (name: "production")
    │       ├── ContainerClassLoader (isolated module JARs)
    │       ├── EntityComponentStore (container-scoped entities)
    │       ├── GameLoop (own thread, 60 FPS)
    │       ├── CommandQueue (container-scoped commands)
    │       ├── SnapshotProvider (match filtering)
    │       └── Matches [1, 2, 3]
    │
    └── Container 2 (name: "staging")
            ├── ContainerClassLoader (can have different module versions)
            ├── EntityComponentStore (completely separate)
            ├── GameLoop (own thread, 30 FPS)
            ├── CommandQueue
            └── Matches [4, 5]
```

### Container Lifecycle

| Status | Description |
|--------|-------------|
| `CREATED` | Container initialized but not started |
| `STARTING` | Container is starting up |
| `RUNNING` | Container is actively processing ticks |
| `PAUSED` | Container is paused (state preserved, no ticks) |
| `STOPPING` | Container is shutting down |
| `STOPPED` | Container has stopped |

### Key Benefits

- **ClassLoader Isolation** - Each container loads its own module JARs
- **Independent Game Loop** - Containers tick at their own rate
- **Separate ECS Store** - Entities and components are completely isolated
- **Container-Scoped Commands** - Commands execute within their container
- **Independent Lifecycle** - Start, stop, pause containers independently

For more information regarding classloader isolation, please see [classloader isolation](classloaders.md).

## Tick-Based Simulation

The engine uses discrete ticks. Each tick:
1. Processes all queued commands
2. Runs all systems from enabled modules (e.g., movement applies velocity to position)
3) Notifies any tick listenders or AI
3. Increments tick counter
4. Broadcasts snapshot to WebSocket clients

```bash
# Manual tick control
curl -X POST http://localhost:8080/api/simulation/tick

# Auto-advance at 60 FPS
curl -X POST "http://localhost:8080/api/simulation/play?intervalMs=16"

# Stop auto-advance
curl -X POST http://localhost:8080/api/simulation/stop
```

## WebSocket Streaming

Connect to `ws://localhost:8080/container/id/snapshots/{matchId}` to receive real-time snapshots:

```javascript
const ws = new WebSocket('ws://localhost:8080/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};
```

## WebSocket Commands

For high-performance command submission, use the container-scoped command WebSocket endpoint. This is recommended for game clients that need to submit many commands with low latency.

### Endpoint

`ws://localhost:8080/containers/{containerId}/commands?token=xxx`

### Authentication

JWT authentication is required via query parameter (since browser WebSocket API doesn't support custom headers). The token must have `admin` or `command_manager` role.

### Protocol

Commands are sent as Protocol Buffer binary messages. The schema is defined in `api-proto/src/main/proto/command.proto`:

```
Client                          Server
  │                               │
  │─── Connect with ?token=xxx ──▶│
  │                               │
  │◀── CommandResponse(ACCEPTED) ─│  (connection successful)
  │                               │
  │─── CommandRequest(spawn) ────▶│
  │                               │
  │◀── CommandResponse(ACCEPTED) ─│  (command queued)
  │                               │
  │─── CommandRequest(move) ─────▶│
  │                               │
  │◀── CommandResponse(ACCEPTED) ─│
  │                               │
```

### Supported Command Types

| Payload Type | Description |
|--------------|-------------|
| `SpawnPayload` | Create new entity with type and position |
| `AttachRigidBodyPayload` | Attach physics body to entity |
| `AttachSpritePayload` | Attach sprite rendering to entity |
| `GenericPayload` | Custom commands with arbitrary parameters |

### Error Handling

| Status | Description |
|--------|-------------|
| `ACCEPTED` | Command queued for next tick |
| `ERROR` | Command failed (see message for details) |
| `INVALID` | Malformed request or missing parameters |

### Benefits vs REST API

- **Lower latency**: No HTTP overhead per command
- **Binary protocol**: Smaller message size with Protocol Buffers
- **Persistent connection**: No connection setup per request
- **Ordered delivery**: Commands processed in order received

## Snapshot Format

```json
{
  "matchId": 1,
  "tick": 42,
  "data": {
    "MoveModule": {
      "POSITION_X": [100.0, 200.0, 150.0],
      "POSITION_Y": [50.0, 75.0, 100.0],
      "VELOCITY_X": [1.0, -1.0, 0.0]
    },
    "SpawnModule": {
      "ENTITY_TYPE": [1.0, 2.0, 1.0],
      "OWNER_ID": [1.0, 1.0, 2.0]
    }
  }
}
```

Each array is columnar: index 0 = entity 0's value, index 1 = entity 1's value, etc.

## Authentication & Authorization

The API uses JWT-based authentication with dynamic RBAC (Role-Based Access Control).

### Default Roles

| Role | Permissions | Includes |
|------|-------------|----------|
| `admin` | Full access to all endpoints | command_manager, view_only |
| `command_manager` | Can post commands and manage matches | view_only |
| `view_only` | Read-only access to snapshots and status | - |

### Role Hierarchy

Roles can include other roles, forming a hierarchy. When a user has `admin`, they automatically have permissions of `command_manager` and `view_only`.

```java
// Creating custom roles via API
POST /api/auth/roles
{
  "name": "game_operator",
  "description": "Can manage game sessions",
  "includedRoles": ["command_manager"]
}
```

### API Token CLI

The `issue-api-token` module provides a CLI for generating long-lived API tokens:

```bash
# Generate admin token
java -jar issue-api-token.jar --roles=admin --secret=your-jwt-secret

# Generate token with multiple roles
java -jar issue-api-token.jar --roles=command_manager,view_only --user=my-service

# View help
java -jar issue-api-token.jar --help
```

### Authentication Flow

1. **Login**: `POST /api/auth/login` with username/password returns JWT
2. **Refresh**: `POST /api/auth/refresh` with current token returns new JWT
3. **Use**: Include `Authorization: Bearer <token>` header in requests

## Delta Compression

For bandwidth-efficient real-time updates, use delta snapshots instead of full snapshots.

### Delta Format

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

## MongoDB Snapshot Persistence

When enabled, snapshots are automatically persisted to MongoDB after each tick.

### Configuration

```properties
# application.properties
snapshot.persistence.enabled=true
snapshot.persistence.database=lightningfirefly
snapshot.persistence.collection=snapshots
snapshot.persistence.tick-interval=1  # Persist every N ticks

# MongoDB connection
quarkus.mongodb.connection-string=mongodb://localhost:27017
```

## Async Tick Listeners

Tick listeners are notified asynchronously via a thread pool to avoid blocking the game loop. This is especially important for I/O-bound operations like MongoDB persistence.

```java
// GameLoop notifies listeners asynchronously (fire and forget)
private void notifyTickListeners(long tick) {
    for (TickListener listener : tickListeners) {
        tickListenerExecutor.submit(() -> {
            listener.onTickComplete(tick);
        });
    }
}
```


## Module Permission Scoping

Modules use `PermissionComponent` to control access levels for their data. Permissions are enforced via JWT tokens issued to each module.

**Permission Levels:**

| Level | Description |
|-------|-------------|
| `PRIVATE` | Only the owning module can read/write |
| `READ` | Other modules can read, only owner can write |
| `WRITE` | Any module can read and write |

```java
// Components with permission levels
public static final PermissionComponent POSITION_X =
    PermissionComponent.create("POSITION_X", PermissionLevel.READ);  // Others can read
public static final PermissionComponent INTERNAL_STATE =
    PermissionComponent.create("INTERNAL_STATE", PermissionLevel.PRIVATE);  // Only owner

// In another module - reading works, writing throws EcsAccessForbiddenException
float x = store.getComponent(entity, POSITION_X);  // OK
store.attachComponent(entity, POSITION_X, 100f);   // Throws!
```

For detailed documentation on module permissions, JWT authentication, and superuser modules, see [Module System - Permission Scoping](module-system.md#module-permission-scoping).

## Store Decorator Pattern

The ECS store uses a decorator pattern for layered functionality:

```
PermissionedEntityComponentStore  ← Module scoping
    └── LockingEntityComponentStore   ← Thread safety (ReentrantReadWriteLock)
            └── CachedEntityComponentStore    ← Query result caching
                    └── ArrayEntityComponentStore     ← Columnar storage
```

Each layer adds a specific concern without modifying the others
