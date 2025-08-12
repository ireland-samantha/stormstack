# Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         Clients                              │
│              (Debug GUI / Game Client / Tests)               │
└─────────────────┬───────────────────────┬───────────────────┘
                  │ REST                  │ WebSocket
                  ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Quarkus Web API                           │
│    /api/commands  /api/matches  /api/snapshots  /api/modules │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Engine Core                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Modules   │  │  Command    │  │  Snapshot Provider  │  │
│  │  (8 built-  │  │   Queue     │  │  (match filtering)  │  │
│  │    in)      │  │             │  │                     │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│         ▼                ▼                     ▼             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │      CachedEntityComponentStore (decorator)             ││
│  │  ┌───────────────────────────────────────────────────┐  ││
│  │  │     ArrayEntityComponentStore (392 lines)         │  ││
│  │  │  • Entity ID → row index (O(1) lookup)            │  ││
│  │  │  • Component ID → column offset                   │  ││
│  │  │  • Single float[] pool for cache efficiency       │  ││
│  │  │  • ReadWriteLock for thread safety                │  ││
│  │  │  • Slot reuse via FIFO reclaim queue              │  ││
│  │  └───────────────────────────────────────────────────┘  ││
│  │  ┌───────────────────────────────────────────────────┐  ││
│  │  │     QueryCache (255 lines)                        │  ││
│  │  │  • ConcurrentHashMap with sorted component keys   │  ││
│  │  │  • Version-based invalidation per component       │  ││
│  │  │  • Hit/miss statistics                            │  ││
│  │  └───────────────────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
lightning-engine/
├── engine-core/          # Interfaces: EntityComponentStore, BaseComponent, Snapshot
├── engine-internal/      # Implementations: ArrayEntityComponentStore, QueryCache
├── engine-adapter/
│   ├── game-sdk/         # BackendClient, Orchestrator, GameRenderer
│   └── web-api-adapter/  # REST client adapters
├── rendering-core/       # GUI framework: 68 files, NanoVG/OpenGL
├── rendering-test/       # Test framework: GuiDriver, HeadlessWindow, By locators
├── gui/                  # Debug GUI application
├── webservice/
│   └── quarkus-web-api/  # REST + WebSocket endpoints (10 resource classes)
├── api-acceptance-test/  # REST integration tests
└── e2e-live-rendering-and-backend-acceptance-test/  # Full-stack E2E tests

lightning-engine-extensions/
├── modules/              # 8 module submodules (entity, health, rendering, etc.)
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
