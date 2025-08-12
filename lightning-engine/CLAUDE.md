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
├── engine-internal/      # Implementation details (stores, services)
├── rendering-core/       # GUI framework (OpenGL/NanoVG rendering)
├── gui/                  # Desktop GUI application
└── webservice/
    ├── quarkus-web-api/  # Quarkus REST/WebSocket endpoints
    └── web-api-adapter/  # API adapters
```

### Module Dependencies
```
gui → rendering-core → (LWJGL, NanoVG)
gui → engine-core (for SnapshotData types)
webservice → engine-core → engine-internal
```

## Key Architectural Patterns

### 1. GUI Abstraction Layer

The GUI module is decoupled from OpenGL implementation. Client code uses only abstractions:

```java
// CORRECT: Use abstractions in gui module
import com.lightningfirefly.engine.rendering.gui.Window;
import com.lightningfirefly.engine.rendering.gui.WindowBuilder;

Window window = WindowBuilder.create()
    .size(1200, 800)
    .title("My App")
    .build();
window.run();

// WRONG: Don't import OpenGL-specific classes in gui module
// import com.lightningfirefly.engine.rendering.gui.GUIWindow;  // NO!
```

**Key GUI Abstractions (in rendering-core):**
| Interface/Class | Purpose |
|-----------------|---------|
| `Window` | Abstract window interface |
| `WindowBuilder` | Factory for creating windows |
| `GUIComponent` | Base interface for all UI components |
| `AbstractGUIComponent` | Base class with common functionality |
| `Panel` | Container component with title bar |
| `Button`, `Label`, `TextField` | Basic widgets |
| `TreeView`, `ListView` | Data display widgets |

**Implementation (in rendering-core, internal use):**
| Class | Purpose |
|-------|---------|
| `GUIWindow` | OpenGL/GLFW Window implementation |
| `GUIContext` | Thread-local NanoVG context holder |

### 2. Component Hierarchy

```
GUIComponent (interface)
    └── AbstractGUIComponent (base class)
            ├── Label
            ├── Button
            ├── TextField
            ├── TreeView
            ├── ListView
            └── Panel (container with children)
                    ├── SnapshotPanel (gui module)
                    └── ResourcePanel (gui module)
```

### 3. Event Propagation

Events propagate from window to components in reverse z-order (top-most first):

```java
// In GUIWindow.setupInputCallbacks()
for (int i = components.size() - 1; i >= 0; i--) {
    if (components.get(i).onMouseClick(mx, my, button, action)) {
        break;  // Event consumed
    }
}
```

### 4. ECS Snapshot Format

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

### Working with REST Resources

```java
ResourceService service = new ResourceService(serverUrl);

// List resources
CompletableFuture<List<ResourceInfo>> future = service.listResources();

// Download to file
service.downloadResourceToFile(resourceId, targetPath)
    .thenAccept(bytes -> log.info("Downloaded {} bytes", bytes));
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

```bash
# Build all modules
./mvnw clean install

# Run tests for specific module
./mvnw test -pl lightning-engine/gui

# Run GUI application (requires display)
./mvnw exec:java -pl lightning-engine/gui \
    -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
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
