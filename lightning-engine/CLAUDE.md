# Lightning Engine - LLM Developer Guide

This document provides context for LLMs to quickly understand and work with the Lightning Engine codebase.

## Project Overview

Lightning Engine is a modular Entity-Component-System (ECS) game engine with:
- **Real-time game simulation** via tick-based execution
- **WebSocket streaming** for live snapshot updates
- **REST API** for resource and match management
- **Desktop GUI client** for visualization and debugging

## Module Structure

```
lightning-engine/
├── engine-core/          # Core abstractions (interfaces, domain models)
├── engine-internal/      # Implementation details (stores, services) - NO MODULE IMPLEMENTATIONS
├── engine-adapter/
│   ├── game-sdk/         # Orchestrator, GameRenderer, SpriteMapper
│   └── web-api-adapter/  # EngineClient, REST adapters, Jackson JSON
├── rendering-core/       # GUI framework (OpenGL/NanoVG rendering)
├── gui/                  # Desktop GUI application
└── webservice/
    └── quarkus-web-api/  # Quarkus REST/WebSocket endpoints

lightning-engine-extensions/
└── modules/              # All module implementations go here
    ├── entity-module/    # Entity spawning and lifecycle
    ├── rendering-module/ # Sprite rendering
    └── physics-module/   # Compound physics module (parent pom)
        ├── grid-map-module/   # Grid-based positioning
        └── rigid-body-module/ # Physics rigid body simulation
```

### Module Location Guidelines

**IMPORTANT:** Module implementations (like PhysicsModule, GridMapModule, etc.) must NEVER be placed in `engine-internal`. The `engine-internal` module is for engine infrastructure only (stores, services, container management).

- **engine-core**: Interfaces, records, and abstractions (e.g., `EngineModule`, `CompoundModule`, `ModuleVersion`)
- **engine-internal**: Engine infrastructure implementations (e.g., `AbstractCompoundModule`, `CompoundModuleBuilder`, `ModuleDependencyResolver`)
- **lightning-engine-extensions/modules/**: All concrete module implementations

Compound modules (modules composed of other modules) should be organized as parent poms containing their sub-modules.

### 4. Execution Containers

Execution Containers provide complete runtime isolation for game instances. Each container has:
- **Isolated ClassLoader** - Module JARs loaded separately per container
- **Separate ECS Store** - Entities and components are container-scoped
- **Independent GameLoop** - Each container ticks at its own rate
- **Container-scoped Command Queue** - Commands execute within their container

```
ContainerManager
    │
    ├── Container 0 (default)
    │       ├── ContainerClassLoader
    │       ├── EntityComponentStore
    │       ├── GameLoop (own thread)
    │       ├── CommandQueue
    │       └── Matches [1, 2, 3]
    │
    └── Container 1
            ├── ContainerClassLoader
            ├── EntityComponentStore
            ├── GameLoop (own thread)
            ├── CommandQueue
            └── Matches [4, 5]
```

**Key Classes:**
| Class | Location | Purpose |
|-------|----------|---------|
| `ExecutionContainer` | `engine-core/.../container/` | Interface defining container operations |
| `ContainerConfig` | `engine-core/.../container/` | Configuration record with builder |
| `ContainerStatus` | `engine-core/.../container/` | Enum: CREATED, STARTING, RUNNING, PAUSED, STOPPING, STOPPED |
| `ContainerManager` | `engine-core/.../container/` | Interface for managing containers |
| `InMemoryExecutionContainer` | `engine-internal/.../container/` | Full container implementation |
| `InMemoryContainerManager` | `engine-internal/.../container/` | Registry and factory for containers |
| `ContainerClassLoader` | `engine-internal/.../container/` | URLClassLoader with isolation |

**REST API:**
```bash
# Create container
curl -X POST http://localhost:8080/api/containers \
  -H "Content-Type: application/json" \
  -d '{"name": "game-server-1"}'

# Start container
curl -X POST http://localhost:8080/api/containers/1/start

# Create match in container
curl -X POST http://localhost:8080/api/containers/1/matches \
  -H "Content-Type: application/json" \
  -d '{"enabledModuleNames": ["EntityModule"]}'

# Start auto-advance (60 FPS)
curl -X POST "http://localhost:8080/api/containers/1/play?intervalMs=16"

# Stop auto-advance
curl -X POST http://localhost:8080/api/containers/1/stop-auto
```

### 5. ECS Snapshot Format

Snapshots are organized by module, with columnar component storage:

```json
{
  "matchId": 1,
  "tick": 42,
  "data": {
    "ModuleName": {
      "COMPONENT_NAME": [value1, value2, ...],  // One value per entity
      "OTHER_COMPONENT": [value1, value2, ...]
    }
  }
}
```

## Common Tasks

### Adding a New GUI Component

1. Create class in `rendering-core/src/main/java/.../gui/`:
```java
public class MyWidget extends AbstractGUIComponent {
    public MyWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void render(long nvg) {
        // Use NanoVG for rendering
        nvgBeginPath(nvg);
        // ...
    }
}
```

2. Add tests in `rendering-core/src/test/java/.../gui/`.

### Adding a New Panel to GUI App

1. Create panel in `gui/src/main/java/.../panel/`:
```java
@Slf4j
public class MyPanel extends Panel {
    public MyPanel(int x, int y, int width, int height) {
        super(x, y, width, height);
        setTitle("My Panel");
        // Add child components
    }

    public void update() {
        // Called every frame
    }

    public void dispose() {
        // Cleanup resources
    }
}
```

2. Add to `EngineGuiApplication.setupUI()`:
```java
myPanel = new MyPanel(x, y, width, height, ...);
window.addComponent(myPanel);
```

3. Call `myPanel.update()` in the update callback.

### Working with WebSocket Snapshots

```java
// Create client
SnapshotWebSocketClient client = new SnapshotWebSocketClient(serverUrl, matchId);

// Add listener
client.addListener(snapshot -> {
    log.info("Received snapshot: tick={}", snapshot.tick());
    for (String moduleName : snapshot.getModuleNames()) {
        Map<String, List<Long>> moduleData = snapshot.getModuleData(moduleName);
        // Process components...
    }
});

// Connect
client.connect();

// Request snapshot manually
client.requestSnapshot();

// Disconnect when done
client.disconnect();
```

### Working with EngineClient

The unified client for all backend operations:

```java
var client = EngineClient.connect("http://localhost:8080");

// Create a match
var match = client.createMatch()
    .withModules("EntityModule", "RigidBodyModule")
    .execute();

// Spawn entities
client.forMatch(match.id())
    .spawn().forPlayer(1).ofType(100).execute();

// Control simulation
client.tick();
client.play(16);  // 60 FPS

// Fetch snapshots
var snapshot = client.fetchSnapshot(match.id());

// Manage resources
var resources = client.listResources();
byte[] data = client.downloadResource(resourceId);
client.uploadResource().name("sprite.png").data(bytes).execute();
```

## Testing Patterns

### GUI Component Tests (No OpenGL Required)

Most GUI tests don't need a real window. Use mock NanoVG context:

```java
@Test
void button_clickHandler_isCalled() {
    AtomicBoolean clicked = new AtomicBoolean(false);
    Button button = new Button(10, 10, 100, 30, "Click");
    button.setOnClick(() -> clicked.set(true));

    // Simulate click inside button bounds
    button.onMouseClick(50, 25, 0, 1);  // x, y, button, action

    assertThat(clicked.get()).isTrue();
}
```

### Panel Tests with Mock Data

```java
@Test
void snapshotPanel_displaysEntities() {
    SnapshotPanel panel = new SnapshotPanel(0, 0, 400, 300, "http://localhost", 1);

    // Create mock snapshot
    Map<String, Map<String, List<Long>>> data = Map.of(
        "GameModule", Map.of(
            "POSITION_X", List.of(100L, 200L),
            "POSITION_Y", List.of(50L, 60L)
        )
    );
    SnapshotData snapshot = new SnapshotData(1L, 42, data);

    // Set data and verify tree
    panel.setSnapshotData(snapshot);
    TreeView tree = panel.getEntityTree();
    assertThat(tree.getRootNodes()).hasSize(1);
}
```

## Build & Run

Use the `build.sh` script for all build operations:

```bash
./build.sh clean            # Clean build artifacts and secrets
./build.sh secrets          # Generate JWT keys (required before build)
./build.sh build            # Build all modules (skip tests)
./build.sh test             # Run unit tests
./build.sh frontend         # Install deps and build frontend
./build.sh frontend-test    # Run frontend tests with coverage
./build.sh docker           # Build Docker image
./build.sh integration-test # Build Docker + run integration tests
./build.sh all              # Full pipeline: clean → secrets → frontend → build → test → integration-test
```

**Before submitting changes, always verify:**
```bash
./build.sh all
```

Or use Maven directly:
```bash
mvn clean install           # Build with tests
mvn test -pl lightning-engine/gui  # Run tests for specific module
```

Run GUI application (requires display):
```bash
mvn exec:java -pl lightning-engine/gui \
    -Dexec.mainClass=ca.samanthaireland.engine.gui.EngineGuiApplication \
    -Dexec.args="-s http://localhost:8080 -m 1"
```

### macOS Note
LWJGL/GLFW requires `-XstartOnFirstThread` JVM argument on macOS. This is configured in the pom.xml surefire plugin.

## Logging

Uses SLF4J with Lombok `@Slf4j`:

```java
@Slf4j
public class MyClass {
    public void doSomething() {
        log.debug("Detailed info for debugging");
        log.info("Important events");
        log.warn("Potential issues");
        log.error("Errors", exception);
    }
}
```

## File Locations Quick Reference

| What | Where |
|------|-------|
| EngineClient | `engine-adapter/web-api-adapter/.../adapter/EngineClient.java` |
| REST adapters | `engine-adapter/web-api-adapter/.../adapter/*Adapter.java` |
| Orchestrator | `engine-adapter/game-sdk/.../orchestrator/Orchestrator.java` |
| GameRenderer | `engine-adapter/game-sdk/.../renderering/GameRenderer.java` |
| Window abstraction | `rendering-core/.../gui/Window.java` |
| Window builder | `rendering-core/.../gui/WindowBuilder.java` |
| OpenGL window impl | `rendering-core/.../gui/GUIWindow.java` |
| GUI components | `rendering-core/.../gui/*.java` |
| GUI app entry point | `gui/.../EngineGuiApplication.java` |
| Panels | `gui/.../panel/*.java` |
| Services | `gui/.../service/*.java` |
| REST endpoints | `webservice/quarkus-web-api/.../rest/*.java` |
| WebSocket endpoints | `webservice/quarkus-web-api/.../websocket/*.java` |
| ECS core | `engine-core/.../core/*.java` |
| ECS implementation | `engine-internal/.../internal/*.java` |
| Container interfaces | `engine-core/.../container/*.java` |
| Container implementation | `engine-internal/.../container/*.java` |

## Common Gotchas

1. **HiDPI Displays**: Window size != framebuffer size. Use `pixelRatio` for correct rendering.

2. **Font Loading**: System font paths vary by OS. Check `GUIWindow.getSystemFontPaths()`.

3. **Thread Safety**: WebSocket callbacks run on IO threads. Use volatile fields and update UI in render loop:
   ```java
   private volatile SnapshotData latestSnapshot;
   private volatile boolean needsUpdate = false;

   // In WebSocket callback
   void onMessage(SnapshotData data) {
       this.latestSnapshot = data;
       this.needsUpdate = true;
   }

   // In update() called from render loop
   void update() {
       if (needsUpdate && latestSnapshot != null) {
           needsUpdate = false;
           // Update UI here (safe)
       }
   }
   ```

4. **NanoVG Context**: Only valid during render. Use `GUIContext.get()` in render methods.

5. **Component Bounds**: Click detection uses `contains(x, y)`. Ensure width/height are set correctly.

## Code Quality Guidelines

1. **No Deprecation**: Deprecation is not acceptable in this codebase. When refactoring, always provide complete solutions that fully remove old APIs. Do not use `@Deprecated` annotations as a transitional measure - migrate all usages immediately.

2. **Fluent API Preference**: Prefer fluent builder patterns (e.g., `container.lifecycle().start()`) over direct method calls. The `ExecutionContainer` interface uses fluent operation accessors (`lifecycle()`, `ticks()`, `commands()`, etc.) - do not add non-fluent convenience methods to this interface.

3. **No Production Changes for Testing**: Never modify production classes to make them testable (e.g., making private methods package-private). Instead, update tests to use the public API. Tests should exercise code through its intended public interface.

4. **No Unnecessary Getters or Setters**: Do not add getter and setter methods unless they are absolutely required. Prefer immutable data structures and records. If access is needed, use the fluent API operations (e.g., `container.ticks().current()` instead of `container.getCurrentTick()`).

5. **Build Verification**: Before completing any task, run `./build.sh all` to verify the full build pipeline passes. This includes secrets generation, frontend build, unit tests, Docker image build, and integration tests. Do not leave the codebase in a broken state. At minimum, `./build.sh test` must pass for any code changes.

6. **Use web-api-adapter for API Calls**: In integration tests, always use the `web-api-adapter` classes (`EngineClient`, `ContainerAdapter`, etc.) instead of making direct HTTP calls. If a needed API method doesn't exist in the adapter, add it to the appropriate adapter class first.

7. **DTOs for Multiple Parameters**: When a method requires more than 3 parameters, create a DTO (Data Transfer Object) record to encapsulate the parameters. Place DTOs in the `dto` package of the relevant module. Example:
   ```java
   // Bad: too many parameters
   List<Snapshot> getSnapshots(long containerId, long matchId, long fromTick, long toTick, int limit);

   // Good: use a DTO
   record HistoryQueryParams(long fromTick, long toTick, int limit) {}
   List<Snapshot> getSnapshots(long containerId, long matchId, HistoryQueryParams params);
   ```

8. **Test Fixtures for Business Objects**: Create reusable test fixtures for business objects in acceptance tests. Place fixtures in the `fixture` package (e.g., `TestEngineContainer`, `TestMatch`). Fixtures should encapsulate setup logic and provide fluent APIs for common test operations.

9. **Always Run Tests**: After making code changes, always run the relevant tests to verify they pass. For acceptance tests that use Docker/Testcontainers, rebuild the Docker image before running tests if you modified backend code.

10. **Never Manually Parse JSON**: Always use Jackson (or another established JSON library) for JSON parsing. Never write manual string parsing for JSON data. Use `ObjectMapper` with appropriate `TypeReference` for complex types.
    ```java
    // Bad: manual JSON parsing
    int pos = json.indexOf("\"");
    String key = json.substring(pos + 1, json.indexOf("\"", pos + 1));

    // Good: use Jackson
    Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
    ```

11. **Follow SOLID Principles**: When adding functionality, follow SOLID principles:
    - **Single Responsibility**: Each class should have one reason to change. Extract parsing, validation, and domain logic into separate classes.
    - **Open/Closed**: Classes should be open for extension but closed for modification. Use interfaces and dependency injection.
    - **Dependency Inversion**: Depend on abstractions, not concretions. Inject dependencies via constructors rather than using `new` directly.

    For library code that needs to work without a DI container, provide a factory class:
    ```java
    // Production (with DI container)
    @Inject
    public MyService(Parser parser, Validator validator) { ... }

    // Library usage (no DI)
    var service = MyServiceFactory.create();
    ```

12. **No Magic Numbers**: Never use unexplained numeric literals in code. Always use named constants or derive values from context.
    ```java
    // Bad: magic numbers
    return new Snapshot(0, 0, data);

    // Good: use meaningful values or remove unnecessary parameters
    return new Snapshot(data);
    ```
