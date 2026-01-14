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

## Project Structure

```
lightning-engine/
├── engine-core/          # Interfaces: EntityComponentStore, ExecutionContainer, Snapshot
│   └── container/        # Container interfaces: ExecutionContainer, ContainerManager
├── engine-internal/      # Implementations: ArrayEntityComponentStore, QueryCache
│   └── container/        # InMemoryExecutionContainer, InMemoryContainerManager
├── engine-adapter/
│   ├── game-sdk/         # Orchestrator, GameRenderer, SpriteMapper
│   └── web-api-adapter/  # EngineClient, REST adapters, Jackson JSON
├── rendering-core/       # GUI framework: 68 files, NanoVG/OpenGL
├── rendering-test/       # Test framework: GuiDriver, HeadlessWindow, By locators
├── gui/                  # Debug GUI application
├── webservice/
│   └── quarkus-web-api/  # REST + WebSocket endpoints, React web dashboard
│       └── frontend/     # React TypeScript web dashboard
├── api-acceptance-test/  # REST integration tests
└── e2e-live-rendering-and-backend-acceptance-test/  # Full-stack E2E tests

auth/                     # JWT authentication module
issue-api-token/          # CLI for generating offline API tokens

lightning-engine-extensions/
├── modules/              # 9 module submodules (entity, grid-map, health, etc.)
└── games/checkers/       # CheckersModule (668 lines, 13 components)
```

## Interface-Driven Design

| Interface | Implementations | Purpose |
|-----------|-----------------|---------|
| `Window` | `GLWindow`, `HeadlessWindow` | GPU-free testing |
| `ComponentFactory` | `GLComponentFactory`, `HeadlessComponentFactory` | Mock UI components |
| `EntityComponentStore` | `ArrayEntityComponentStore` | Columnar storage (392 LOC) |
| `Renderer` | `NanoVGRenderer` | Rendering abstraction (migration in progress) |
| `QueryCache` | Default impl | Per-tick query caching with hit/miss stats |

## Multi-Match Isolation

- `EntityFactory.createEntity(matchId)` auto-attaches `MATCH_ID` component
- `SnapshotProvider` filters entities per match for WebSocket clients
- Match deletion cascades to all associated entities

## E2E Test Package

The `e2e-live-rendering-and-backend-acceptance-test` module provides full-stack integration tests that:

1. Start a real backend server via Testcontainers
2. Create matches and spawn entities
3. Render game state using GameRenderer
4. Verify pixel-level visual output

**Package Structure:**
```
e2e-live-rendering-and-backend-acceptance-test/
├── domain/              # Fluent test DSL
│   ├── Entity.java      # entity.attachSprite().sized(32,32).andApply()
│   ├── Match.java       # match.spawn().forPlayer(1).ofType(100)
│   ├── TestBackend.java # Backend container management
│   ├── SnapshotAssertions.java  # assertThat(snapshot).hasEntity(id)
│   └── ScreenAssertions.java    # assertThat(screen).hasPixel(x,y,color)
├── gui/                 # GUI integration tests
├── modules/             # Module-specific tests (PhysicsIT, CollisionIT, etc.)
└── ui/                  # GameRenderer tests
```

**Example Test:**
```java
@Test
void entityMovesAcrossScreen() {
    var entity = match.spawn().forPlayer(1).ofType(100);

    entity.attachRigidBody()
        .at(100, 100)
        .withVelocity(10, 0)
        .andApply();

    entity.attachSprite()
        .sized(32, 32)
        .andApply();

    backend.tick(10);  // Advance 10 ticks

    assertThat(match.snapshot())
        .entityHasComponent(entity.id(), "POSITION_X", 200f);
}
```

## Tick-Based Simulation

The engine uses discrete ticks. Each tick:
1. Processes all queued commands
2. Runs all systems from enabled modules (e.g., movement applies velocity to position)
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

Connect to `ws://localhost:8080/snapshots/{matchId}` to receive real-time snapshots:

```javascript
const ws = new WebSocket('ws://localhost:8080/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};
```

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

### Delta WebSocket

Connect to `/ws/snapshots/delta/{matchId}` to receive only changes between ticks:

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/snapshots/delta/1');
ws.onmessage = (event) => {
  const delta = JSON.parse(event.data);
  console.log(`Changes from tick ${delta.fromTick} to ${delta.toTick}:`);
  console.log(`- Changed components: ${delta.changeCount}`);
  console.log(`- Added entities: ${delta.addedEntities.length}`);
  console.log(`- Compression ratio: ${delta.compressionRatio}`);
};

// Reset to receive full snapshot on next tick
ws.send('reset');
```

### Delta REST API

```bash
# Get delta between two ticks
GET /api/snapshots/delta/{matchId}?fromTick=100&toTick=150

# Record current snapshot for future delta computation
POST /api/snapshots/delta/{matchId}/record

# Get snapshot history info
GET /api/snapshots/delta/{matchId}/history
```

### Delta Response Format

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
  "changeCount": 5,
  "compressionRatio": 0.15
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

### Docker Compose

```yaml
services:
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    volumes:
      - mongodb-data:/data/db

  backend:
    environment:
      - QUARKUS_MONGODB_CONNECTION_STRING=mongodb://mongodb:27017
      - SNAPSHOT_PERSISTENCE_ENABLED=true
    depends_on:
      - mongodb
```

### History REST API

```bash
# Get overall history summary
GET /api/history

# Get match-specific history
GET /api/history/{matchId}

# Get snapshots in tick range
GET /api/history/{matchId}/snapshots?fromTick=0&toTick=100&limit=50

# Get latest N snapshots
GET /api/history/{matchId}/snapshots/latest?limit=10

# Get specific snapshot by tick
GET /api/history/{matchId}/snapshot/{tick}

# Delete match history (admin only)
DELETE /api/history/{matchId}

# Delete snapshots older than tick (admin only)
DELETE /api/history/{matchId}/older-than/{tick}
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

### Implementing a Tick Listener

```java
public class MyTickListener implements TickListener {
    @Override
    public void onTickComplete(long tick) {
        // Called asynchronously after each tick
        // Safe to perform I/O operations here
    }
}

// Register in SimulationConfig
@Produces
@ApplicationScoped
public TickListener myListener() {
    return new MyTickListener();
}

// Add to GameLoop
gameLoop.addTickListener(myListener);
```

## Module Permission Scoping

Modules use `PermissionComponent` to control access levels for their data:

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

## Store Decorator Pattern

The ECS store uses a decorator pattern for layered functionality:

```
PermissionedEntityComponentStore  ← Module scoping
    └── LockingEntityComponentStore   ← Thread safety (ReentrantReadWriteLock)
            └── CachedEntityComponentStore    ← Query result caching
                    └── ArrayEntityComponentStore     ← Columnar storage
```

Each layer adds a specific concern without modifying the others
