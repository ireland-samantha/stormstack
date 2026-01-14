# Game SDK

The Game SDK (`lightning-engine/engine-adapter`) provides client libraries for building games on top of Lightning Engine.

## EngineClient

The unified API for all backend operations. Located in `web-api-adapter`:

```java
var client = EngineClient.connect("http://localhost:8080");

// Create a match with modules
var match = client.createMatch()
    .withModule("EntityModule")
    .withModule("RigidBodyModule")
    .withModule("RenderingModule")
    .execute();

// Queue commands
client.forMatch(match.id())
    .spawn().forPlayer(1).ofType(100).execute();

// Control simulation
client.tick();
client.play(16);  // 16ms interval (60 FPS)

// Fetch snapshots
var snapshot = client.fetchSnapshot(match.id());

// Manage modules and resources
var modules = client.listModules();
client.uploadResource().name("texture.png").data(bytes).execute();
```

### Fluent Command Builders

```java
var commands = client.forMatch(matchId);

// Spawn entities
commands.spawn()
    .forPlayer(1)
    .ofType(100)
    .execute();

// Attach sprites
commands.attachSprite()
    .toEntity(entityId)
    .usingResource(resourceId)
    .at(100, 200)
    .sized(48, 48)
    .rotatedBy(45)
    .onLayer(5)
    .visible(true)
    .execute();

// Move entities
commands.move()
    .entity(entityId)
    .to(300, 400)
    .withVelocity(10, 0)
    .execute();

// Custom commands
commands.custom("myCommand")
    .param("key1", value1)
    .param("key2", value2)
    .execute();
```

### Simulation Control

```java
// Get current tick
long tick = client.currentTick();

// Advance by one tick
client.tick();

// Advance by multiple ticks
client.tick(10);

// Start auto-advancing (returns status)
var status = client.play(16);  // 16ms interval
boolean isPlaying = status.playing();

// Stop auto-advancing
client.stop();

// Check if playing
if (client.isPlaying()) { ... }
```

### Resource Management

```java
// List resources
List<EngineClient.Resource> resources = client.listResources();

// Upload resource
var resource = client.uploadResource()
    .name("player-sprite.png")
    .type("TEXTURE")
    .data(imageBytes)
    .execute();

// Download resource
byte[] data = client.downloadResource(resourceId);

// Delete resource
client.deleteResource(resourceId);
```

### Module and AI Management

```java
// List modules
List<EngineClient.Module> modules = client.listModules();
Optional<EngineClient.Module> module = client.getModule("EntityModule");
client.reloadModules();

// List AIs
List<EngineClient.AI> ais = client.listAIs();
Optional<EngineClient.AI> gm = client.getAI("AIAI");
client.reloadAIs();
```

### Low-Level Adapter Access

For advanced use cases, access the underlying adapters directly:

```java
// Adapters throw checked IOException
CommandAdapter commands = client.commands();
MatchAdapter matches = client.matches();
ResourceAdapter resources = client.resources();
SimulationAdapter simulation = client.simulation();
SnapshotAdapter snapshots = client.snapshots();
ModuleAdapter modules = client.modules();
AIAdapter ais = client.ais();
PlayerAdapter players = client.players();
```

## GameRenderer

Abstraction for rendering game state (in `game-sdk`):

```java
GameRenderer renderer = GameRendererBuilder.create()
    .windowSize(800, 600)
    .title("My Game")
    .build();

renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
    .positionComponents("POSITION_X", "POSITION_Y")
    .textureResolver(resourceId -> "/textures/" + resourceId + ".png"));

renderer.start(() -> {
    Snapshot snapshot = fetchLatestSnapshot();
    renderer.renderSnapshot(snapshot);
});
```

## Orchestrator

Combines EngineClient and GameRenderer with automatic WebSocket streaming (in `game-sdk/orchestrator`):

```java
var orchestrator = Orchestrator.create()
    .client(client)
    .renderer(renderer)
    .forMatch(matchId)
    .build();

// Blocks until window closes, auto-renders on snapshot updates
orchestrator.start();

// Or for testing - run specific number of frames
orchestrator.runFrames(60);
```

## SpriteSnapshotMapper

Converts ECS snapshots to renderable sprites. Default configuration reads:
- `POSITION_X`, `POSITION_Y` - display position (shared with physics)
- `SPRITE_WIDTH`, `SPRITE_HEIGHT` - dimensions
- `SPRITE_ROTATION` - rotation in degrees
- `SPRITE_Z_INDEX` - render order
- `RESOURCE_ID` - texture reference

## Domain Records

EngineClient provides these domain records:

| Record | Fields |
|--------|--------|
| `Match` | `id`, `enabledModules` |
| `SimulationStatus` | `playing`, `tick` |
| `Resource` | `id`, `name`, `type` |
| `Module` | `name`, `flagComponentName` |
| `AI` | `name` |
| `Snapshot` | `matchId`, `tick`, `data` |
| `ModuleData` | `components` |

## Complete Example

```java
public class MyGame {
    public static void main(String[] args) {
        // Connect to backend
        var client = EngineClient.connect("http://localhost:8080");

        // Create match
        var match = client.createMatch()
            .withModule("EntityModule")
            .withModule("RigidBodyModule")
            .withModule("RenderingModule")
            .execute();

        // Create renderer
        var renderer = GameRendererBuilder.create()
            .windowSize(800, 600)
            .title("My Game")
            .build();

        // Setup orchestrator
        var orchestrator = Orchestrator.create()
            .client(client)
            .renderer(renderer)
            .forMatch(match.id())
            .build();

        // Spawn initial entity
        client.forMatch(match.id())
            .spawn().forPlayer(1).ofType(1).execute();

        // Start simulation
        client.play(16);

        // Run game (blocks until window closes)
        orchestrator.start();
    }
}
```

## Exception Handling

The fluent API on EngineClient throws `UncheckedIOException` for cleaner code:

```java
// No try-catch needed for common operations
var match = client.createMatch().withModule("EntityModule").execute();
client.tick();

// Handle errors if needed
try {
    client.forMatch(matchId).spawn().execute();
} catch (UncheckedIOException e) {
    log.error("Failed to spawn: {}", e.getCause().getMessage());
}
```

For checked exceptions, use the low-level adapters:

```java
try {
    client.matches().createMatch(modules, ais);
} catch (IOException e) {
    // Handle network error
}
```
