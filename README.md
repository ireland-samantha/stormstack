# Lightning Engine

A custom Entity Component System (ECS) game engine built in Java, featuring real-time state synchronization, a debugging GUI, and modular game logic.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Docker](#docker)
- [Strengths](#strengths)
- [Limitations](#limitations)
- [Architecture](#architecture)
- [Using the Engine](#using-the-engine)
- [Module System](#module-system)
- [REST API Reference](#rest-api-reference)
- [Testing](#testing)
- [Performance](#performance)
- [AI-Assisted Development: Learnings](#ai-assisted-development-learnings)
- [License](#license)

## Overview

Lightning Engine is a **learning/hobby project** exploring ECS architecture and AI-assisted development, 
built via pair programming with [Claude Code](https://claude.ai).

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **Columnar ECS** | `ArrayEntityComponentStore` with O(1) component access, float-based values, per-tick query caching |
| **Hot-Reload Modules** | Upload JAR files at runtime, reload without restart |
| **Real-Time Streaming** | WebSocket pushes ECS snapshots to clients every tick |
| **Debug GUI** | Desktop app for entity inspection, command execution, module management |
| **Headless Testing** | Selenium-inspired `GuiDriver` runs 179 tests without GPU |

### Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 |
| Build | Maven (multi-module) |
| Server | Quarkus 3.x (REST + WebSocket) |
| GUI | LWJGL 3 + NanoVG |
| Testing | JUnit 5, Testcontainers |
| API Docs | OpenAPI 3.0 ([openapi.yaml](openapi.yaml)) |

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for containerized deployment or integration tests)

### Option A: Run with Docker (Recommended)

```bash
# Build and start the server
docker compose up -d

# Server runs at http://localhost:8080
curl http://localhost:8080/api/simulation/tick
```

The Docker image includes pre-built modules and the GUI JAR for download.

### Option B: Run from Source

```bash
# 1. Build all modules
./mvnw clean install

# 2. Start server in dev mode
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

Server runs at `http://localhost:8080`. API docs at `/q/swagger-ui` (if Quarkus OpenAPI extension enabled).

### Create a Match and Spawn an Entity

```bash
# Create match with SpawnModule enabled
curl -X POST http://localhost:8080/api/matches \
  -H "Content-Type: application/json" \
  -d '{"id": 0, "enabledModuleNames": ["SpawnModule", "MoveModule"]}'

# Queue a spawn command
curl -X POST http://localhost:8080/api/commands \
  -H "Content-Type: application/json" \
  -d '{"commandName": "spawn", "payload": {"matchId": 1, "entityType": 1, "playerId": 1}}'

# Advance tick to process command
curl -X POST http://localhost:8080/api/simulation/tick

# View ECS state
curl http://localhost:8080/api/snapshots/match/1
```

### Run the Debug GUI

**Option 1: Download from Server** (when running via Docker)

```bash
# Download pre-configured GUI package
curl -O http://localhost:8080/api/gui/download
unzip lightning-gui.zip

# Run (macOS requires -XstartOnFirstThread)
java -XstartOnFirstThread -jar lightning-gui.jar   # macOS
java -jar lightning-gui.jar                         # Linux/Windows
```

The downloaded ZIP includes `server.properties` pre-configured with the server URL.

**Option 2: Run from Source**

```bash
./mvnw exec:java -pl lightning-engine/gui \
  -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
  -Dexec.args="-s http://localhost:8080"
```

### Import Postman Collection

Import [postman-collection.json](postman-collection.json) for pre-configured API requests.

## Docker

### Build the Image

```bash
# Full build (compiles from source inside container)
docker build -t lightning-backend .

# Or use pre-built JARs (faster, requires local mvn package first)
./mvnw package -DskipTests
docker build -f Dockerfile.prebuilt -t lightning-backend:prebuilt .
```

### Run with Docker Compose

```bash
# Start backend only
docker compose up -d

# Start with pre-built image
docker compose --profile prebuilt up -d backend-prebuilt

# View logs
docker compose logs -f backend

# Stop
docker compose down
```

### Container Details

| Image | Size | Contents |
|-------|------|----------|
| `lightning-backend` | ~350MB | Quarkus app, modules JAR, GUI JAR |
| `eclipse-temurin:25-jre-alpine` | Base runtime |

**Exposed Ports:**
- `8080` - REST API and WebSocket

**Health Check:** `GET /api/simulation/tick` (30s interval)

**Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Dquarkus.http.host=0.0.0.0` | JVM arguments |
| `QUARKUS_LOG_LEVEL` | `INFO` | Log verbosity |
| `GUI_JAR_PATH` | `/app/gui/lightning-gui.jar` | Path to GUI JAR for download endpoint |

### GUI Download Endpoint

When running in Docker, the GUI JAR is bundled and available for download:

```bash
# Get info about GUI availability
curl http://localhost:8080/api/gui/info

# Download ZIP with auto-configuration
curl -O http://localhost:8080/api/gui/download

# Download JAR only (no config)
curl -O http://localhost:8080/api/gui/download/jar
```

The `/api/gui/download` endpoint returns a ZIP containing:
- `lightning-gui.jar` - The GUI application
- `server.properties` - Pre-configured with the server URL
- `README.txt` - Usage instructions

## Strengths

### Modular Architecture

Modules are self-contained units defining:
- **Components** - Float data attached to entities (e.g., `POSITION_X`, `HEALTH`)
- **Systems** - Logic that runs every tick (e.g., movement, collision)
- **Commands** - External API exposed via REST (e.g., `spawn`, `damage`)

**Built-in Modules:**

| Module | Components | Systems | Commands | Description |
|--------|------------|---------|----------|-------------|
| `SpawnModule` | 4 | 0 | 1 | Entity creation with type/owner |
| `MoveModule` | 7 | 2 | 2 | Position/velocity with movement system |
| `RenderingModule` | 1 | 0 | 1 | Sprite attachment |
| `CheckersModule` | 13 | 2 | 3 | Complete checkers game with 668 LOC |

**Key Benefits:**
- **Hot-Reloadable** - Upload JARs via `POST /api/modules/upload`, call `/api/modules/reload`
- **Per-Match Selection** - Different matches can enable different modules
- **Isolated** - Modules communicate through ECS, not direct imports

### Interface-Driven Design

| Interface | Implementations | Purpose |
|-----------|-----------------|---------|
| `Window` | `GLWindow`, `HeadlessWindow` | GPU-free testing |
| `ComponentFactory` | `GLComponentFactory`, `HeadlessComponentFactory` | Mock UI components |
| `EntityComponentStore` | `ArrayEntityComponentStore` | Columnar storage (392 LOC) |
| `Renderer` | `NanoVGRenderer` | Rendering abstraction (migration in progress) |
| `QueryCache` | Default impl | Per-tick query caching with hit/miss stats |

### Headless GUI Testing

The `rendering-test` module (3 core classes, ~1000 LOC) provides:

```java
// Create headless window (no GPU)
HeadlessWindow window = new HeadlessWindow(800, 600);
Button button = new Button(factory, 10, 10, 100, 30, "Save");
window.addComponent(button);

// Connect test driver
GuiDriver driver = GuiDriver.connect(window);

// Find and interact with elements
driver.findElement(By.text("Save")).click();
driver.findElement(By.textContaining("Tick")).click();
driver.findElement(By.type(Button.class).within(By.title("Settings"))).click();

// Simulate input
driver.type("Hello World");
driver.pressKey(KeyCodes.ENTER);

// Wait for async conditions
driver.waitFor().until(ExpectedConditions.elementVisible(By.text("Done")));

// Debug component tree
driver.dumpComponentTree();
```

**Locators:** `By.text()`, `By.textContaining()`, `By.id()`, `By.type()`, `By.title()`, `By.and()`, `By.or()`, `.within()`

### Multi-Match Isolation

- `EntityFactory.createEntity(matchId)` auto-attaches `MATCH_ID` component
- `SnapshotProvider` filters entities per match for WebSocket clients
- Match deletion cascades to all associated entities

### Debug GUI

Desktop app built on `rendering-core` (55 Java files):

| Panel | Features |
|-------|----------|
| **Entity Inspector** | TreeView of entities/components, real-time WebSocket updates |
| **Command Console** | Send commands with auto-generated parameter forms |
| **Resource Browser** | Upload textures, preview images, attach to entities |
| **Module Manager** | View/upload/reload modules, see enabled match counts |
| **Match Manager** | Create/delete matches, select modules per match |

## Limitations

**This is not production software.** Known gaps:

| Category | Status |
|----------|--------|
| **Persistence** | In-memory only; no database |
| **Security** | No authentication/authorization |
| **Networking** | Full snapshots every tick; no delta compression |
| **Simulation** | No spatial partitioning, physics, or audio |
| **Rendering** | `Renderer` interface exists but components still call NanoVG directly |

### Code Quality Issues

Based on SOLID/Clean Code analysis (see `llm-learnings/`):

| Issue | Location | Impact |
|-------|----------|--------|
| **Single Responsibility** | `GLWindow` (577 lines, 8+ responsibilities) | Hard to test/extend |
| **Dependency Inversion** | `GLContext`, `GLColour` static globals | Hidden dependencies |
| **DRY Violations** | Scrollbar rendering in 3 components | Maintenance burden |
| **Incomplete Abstraction** | Components use `render(long nvg)` not `render(Renderer)` | Can't swap backends |

### Platform Quirks

| Platform | Issue | Solution |
|----------|-------|----------|
| **macOS** | GLFW requires main thread | Add `-XstartOnFirstThread` JVM arg |
| **macOS** | Multi-window not supported | Use embedded panels instead |
| **Docker 29** | Testcontainers API version | Use 1.21.3+, set `api.version=1.44` in `~/.docker-java.properties` |

## Architecture

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
│  │  (4 built-  │  │   Queue     │  │  (match filtering)  │  │
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

### Project Structure

```
lightning-engine/
├── engine-core/          # Interfaces: EntityComponentStore, BaseComponent, Snapshot
├── engine-internal/      # Implementations: ArrayEntityComponentStore, QueryCache
├── rendering-core/       # GUI framework: 55 files, 27 component types
├── rendering-test/       # Test framework: GuiDriver, HeadlessWindow, By locators
├── gui/                  # Debug GUI application
├── webservice/
│   └── quarkus-web-api/  # REST + WebSocket endpoints (10 resource classes)
├── api-acceptance-test/  # REST integration tests
└── gui-acceptance-test/  # E2E GUI tests with Testcontainers

lightning-engine-extensions/
├── modules/              # SpawnModule, MoveModule, RenderingModule
└── games/checkers/       # CheckersModule (668 lines, 13 components)
```

## Using the Engine

### Basic Workflow

1. **Create a match** with desired modules enabled
2. **Queue commands** via REST (spawn entities, apply actions)
3. **Advance ticks** to process commands and run systems
4. **Query snapshots** or stream via WebSocket

### Tick-Based Simulation

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

### WebSocket Streaming

Connect to `ws://localhost:8080/snapshots/{matchId}` to receive real-time snapshots:

```javascript
const ws = new WebSocket('ws://localhost:8080/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};
```

### Snapshot Format

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

## Module System

### Creating a Module

```java
public class HealthModuleFactory implements ModuleFactory {
    // Components with unique IDs (float values stored in ECS)
    public static final BaseComponent MAX_HP =
        new BaseComponent(IdGeneratorV2.newId(), "MAX_HP") {};
    public static final BaseComponent CURRENT_HP =
        new BaseComponent(IdGeneratorV2.newId(), "CURRENT_HP") {};

    @Override
    public EngineModule create(ModuleContext context) {
        return new HealthModule(context);
    }
}

public class HealthModule implements EngineModule {
    private final ModuleContext context;

    public HealthModule(ModuleContext context) {
        this.context = context;
    }

    @Override
    public String getName() { return "HealthModule"; }

    @Override
    public List<BaseComponent> createComponents() {
        return List.of(MAX_HP, CURRENT_HP);
    }

    @Override
    public List<EngineSystem> createSystems() {
        // Systems run every tick
        return List.of(() -> {
            var store = context.getEntityComponentStore();
            for (long entity : store.getEntitiesWithComponents(CURRENT_HP)) {
                if (store.getComponent(entity, CURRENT_HP) <= 0) {
                    store.deleteEntity(entity); // Entity dies
                }
            }
        });
    }

    @Override
    public List<EngineCommand> createCommands() {
        return List.of(
            CommandBuilder.newCommand()
                .withName("damage")
                .withSchema(Map.of("entityId", Long.class, "amount", Float.class))
                .withExecution(payload -> {
                    long entityId = ((Number) payload.getPayload().get("entityId")).longValue();
                    float amount = ((Number) payload.getPayload().get("amount")).floatValue();
                    var store = context.getEntityComponentStore();
                    float current = store.getComponent(entityId, CURRENT_HP);
                    store.attachComponent(entityId, CURRENT_HP, current - amount);
                })
                .build()
        );
    }
}
```

### Deploying a Module

```bash
# 1. Add engine-core dependency (provided scope)
# 2. Build JAR
mvn clean package

# 3. Upload to running server
curl -X POST http://localhost:8080/api/modules/upload \
  -F "file=@target/my-module.jar"

# 4. Trigger reload
curl -X POST http://localhost:8080/api/modules/reload
```

### ModuleContext API

| Method | Description |
|--------|-------------|
| `getEntityComponentStore()` | Read/write entity components (float values) |
| `getEntityFactory()` | Create entities with automatic `MATCH_ID` binding |
| `getMatchService()` | Query match metadata and enabled modules |
| `getModuleResolver()` | Discover and access other modules at runtime |

### Reference Implementations

- **Simple:** `MoveModuleFactory` (223 lines) - Position/velocity, movement system
- **Medium:** `SpawnModuleFactory` (271 lines) - Entity creation, module flag attachment
- **Complex:** `CheckersModuleFactory` (668 lines) - Full game with multi-jump validation

## REST API Reference

Full API documentation: [openapi.yaml](openapi.yaml)

Postman collection: [postman-collection.json](postman-collection.json)

### Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| **Simulation** |||
| GET | `/api/simulation/tick` | Get current tick |
| POST | `/api/simulation/tick` | Advance one tick |
| POST | `/api/simulation/play?intervalMs=16` | Start auto-advance |
| POST | `/api/simulation/stop` | Stop auto-advance |
| GET | `/api/simulation/status` | Get playback status |
| **Commands** |||
| GET | `/api/commands` | List available commands with schemas |
| POST | `/api/commands` | Queue command for next tick |
| **Matches** |||
| GET | `/api/matches` | List all matches |
| POST | `/api/matches` | Create match with enabled modules |
| GET | `/api/matches/{id}` | Get match details |
| DELETE | `/api/matches/{id}` | Delete match and entities |
| **Snapshots** |||
| GET | `/api/snapshots` | Get all match snapshots |
| GET | `/api/snapshots/match/{id}` | Get specific match snapshot |
| WS | `/snapshots/{matchId}` | Stream snapshots via WebSocket |
| **Modules** |||
| GET | `/api/modules` | List installed modules |
| GET | `/api/modules/{name}` | Get module details |
| POST | `/api/modules/upload` | Upload module JAR |
| POST | `/api/modules/reload` | Reload all modules |
| DELETE | `/api/modules/{name}` | Uninstall module |
| **Resources** |||
| GET | `/api/resources` | List resources |
| POST | `/api/resources` | Upload texture |
| GET | `/api/resources/{id}` | Get resource metadata |
| GET | `/api/resources/{id}/data` | Download resource |
| DELETE | `/api/resources/{id}` | Delete resource |
| **GUI** |||
| GET | `/api/gui/info` | Get GUI availability and download info |
| GET | `/api/gui/download` | Download GUI as ZIP with auto-config |
| GET | `/api/gui/download/jar` | Download GUI JAR only |

## Testing

### Test Summary

| Module | Tests | Type |
|--------|-------|------|
| `gui` | 179 | Headless component/panel tests |
| `api-acceptance-test` | ~15 | REST API with Testcontainers |
| `gui-acceptance-test` | ~20 | E2E GUI + Docker backend |
| `engine-internal` | ~50 | ECS store, query cache |

31 additional tests require display and are skipped in CI.

### Running Tests

```bash
# All unit tests (fast, no Docker)
./mvnw test

# Specific module
./mvnw test -pl lightning-engine/gui

# Acceptance tests (requires Docker)
./mvnw verify -pl lightning-engine/api-acceptance-test

# macOS: GLFW requires main thread
JAVA_TOOL_OPTIONS="-XstartOnFirstThread" ./mvnw test
```

### Writing Headless Tests

```java
@Test
void entityInspector_showsSpawnedEntity() {
    // Arrange
    HeadlessWindow window = new HeadlessWindow(800, 600);
    SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 300);
    window.addComponent(panel);

    GuiDriver driver = GuiDriver.connect(window);

    // Act
    panel.setSnapshotData(createMockSnapshot());
    window.runFrames(2);

    // Assert
    assertThat(driver.hasElement(By.textContaining("Entity 0"))).isTrue();
    assertThat(driver.hasElement(By.text("POSITION_X: 100.0"))).isTrue();
}
```

## Performance

From `ArrayEntityComponentStorePerformanceTest` (10,000 entities, 60 FPS simulation):

| Operation | Throughput |
|-----------|------------|
| Entity creation | 20M+ ops/sec |
| Component read/write | 4M+ ops/sec |
| Batch operations | 200K+ entities/sec |
| hasComponent checks | 30M+ checks/sec |
| Entity queries | 100-500 queries/sec |

**Bottleneck:** Entity queries require full-scan of entity set. Mitigated by `QueryCache`:
- Caches query results by sorted component ID set
- Invalidated per-component when modified
- Hit/miss ratio tracking for tuning

## AI-Assisted Development: Learnings

### What Worked

**Enforcing a workflow:** TDD kept the model on task. Writing interfaces and acceptance tests first, then having the model implement, produced reliable code.

**Continuous refactoring:** Regularly evaluating against SOLID and clean code principles prevented architectural drift (see `llm-learnings/*.md` for analyses).

**Knowledge persistence:** Model-generated session summaries in `llm-learnings/` created a reusable knowledge base. Key learnings documented:
- NanoVG font buffer retention (GC issues)
- macOS GLFW threading constraints
- Float vs Long ECS value migration
- Testcontainers Docker Engine 29 compatibility

**Defensive programming:** Immutable records, validation in constructors, and clear interfaces prevented misuse.

### What Didn't Work

**Trusting generated code blindly:** Uncorrected mistakes compounded. The model built on its own errors, eventually hallucinating entire modules.

**Ambiguous prompts:** Vague requests produced vague implementations. Specific acceptance criteria were essential.

**Skipping test reviews:** Generated tests sometimes asserted wrong behavior. Reviewing test logic was as critical as implementation.

### Key Insight

AI accelerates development but requires human rigor. The model produces code you must understand—if you can't explain why it works, you can't fix it when it breaks.

## License

MIT
