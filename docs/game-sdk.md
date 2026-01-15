# Game SDK

The Game SDK (`lightning-engine/engine-adapter`) provides client libraries for building games on top of Lightning Engine.

## EngineClient

The unified API for all backend operations. All operations are **container-scoped** - you create a container first, then perform operations within that container's context.

Located in `web-api-adapter`:

```java
// Connect to backend
var client = EngineClient.connect("http://localhost:8080");

// Create and start a container with modules
var container = client.createContainer()
    .name("my-game")
    .withModules("EntityModule", "RigidBodyModule", "RenderingModule")
    .execute();
client.startContainer(container.id());

// Get a scoped client for the container
var scope = client.container(container.id());

// Create a match with modules
var match = scope.createMatch(List.of("EntityModule", "RigidBodyModule"));

// Queue commands
scope.forMatch(match.id())
    .spawn().forPlayer(1).ofType(100).execute();

// Control simulation
scope.tick();
scope.play(16);  // 16ms interval (60 FPS)

// Fetch snapshots
var snapshot = scope.getSnapshot(match.id());
```

## Container Operations

Containers provide isolated execution environments. Each container has its own:
- Game loop (own thread, configurable tick rate)
- Entity-Component store
- Module instances
- Command queue

```java
// Create container
var container = client.createContainer()
    .name("game-server-1")
    .withModules("EntityModule", "RigidBodyModule")
    .execute();

// Start container
client.startContainer(container.id());

// List all containers
List<EngineClient.Container> containers = client.listContainers();

// Get container status
Optional<EngineClient.Container> container = client.getContainer(containerId);

// Stop and delete
client.stopContainer(containerId);
client.deleteContainer(containerId);
```

## Container Client (Scoped Operations)

Get a scoped client for container-specific operations:

```java
var scope = client.container(containerId);

// Match operations
var match = scope.createMatch(List.of("EntityModule", "RigidBodyModule"));
var matches = scope.listMatches();
scope.deleteMatch(matchId);

// Simulation control
scope.tick();           // Single tick
scope.play(16);         // Auto-advance at 60 FPS (16ms)
scope.stopAuto();       // Stop auto-advance
long tick = scope.currentTick();

// Snapshots
Optional<ContainerSnapshot> snapshot = scope.getSnapshot(matchId);

// Resources
var resources = scope.listResources();
long resourceId = scope.uploadResource()
    .name("texture.png")
    .type("TEXTURE")
    .data(imageBytes)
    .execute();
scope.deleteResource(resourceId);

// Players
long playerId = scope.createPlayer();
scope.deletePlayer(playerId);
```

### Fluent Command Builders

```java
var commands = scope.forMatch(matchId);

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

// Custom commands
commands.custom("myCommand")
    .param("key1", value1)
    .param("key2", value2)
    .execute();
```

### Resource Management

```java
var scope = client.container(containerId);

// List resources
List<ContainerResource> resources = scope.listResources();

// Upload resource
long resourceId = scope.uploadResource()
    .name("player-sprite.png")
    .type("TEXTURE")
    .data(imageBytes)
    .execute();

// Get resource info
Optional<ContainerResource> resource = scope.getResource(resourceId);

// Delete resource
scope.deleteResource(resourceId);
```

### Player Management

```java
var scope = client.container(containerId);

// Create player (auto-generated ID)
long playerId = scope.createPlayer();

// Create player with specific ID
long playerId = scope.createPlayer(42);

// List players
List<Long> players = scope.listPlayers();

// Delete player
scope.deletePlayer(playerId);

// Join match
scope.joinMatch(matchId, playerId);

// Connect/disconnect session
scope.connectSession(matchId, playerId);
scope.disconnectSession(matchId, playerId);
```

## Global Module Operations

Module operations are global (not container-scoped):

```java
// List modules
List<EngineClient.Module> modules = client.listModules();
Optional<EngineClient.Module> module = client.getModule("EntityModule");

// Reload modules
client.reloadModules();
```

## Authentication

```java
// Connect with authentication
var client = EngineClient.builder()
    .baseUrl("http://localhost:8080")
    .withBearerToken(jwtToken)
    .build();

// Or authenticate after connecting
AuthAdapter auth = client.auth();
String token = auth.login("admin", "password");
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
| `Container` | `id`, `name`, `status` |
| `ContainerMatch` | `id`, `enabledModules`, `enabledAIs` |
| `ContainerSnapshot` | `matchId`, `tick`, `data` |
| `ContainerResource` | `resourceId`, `resourceName`, `resourceType` |
| `Module` | `name`, `flagComponentName` |
| `Snapshot` | `matchId`, `tick`, `data` |

## Complete Example

```java
public class MyGame {
    public static void main(String[] args) {
        // Connect to backend
        var client = EngineClient.connect("http://localhost:8080");

        // Create and start container with modules
        var container = client.createContainer()
            .name("my-game")
            .withModules("EntityModule", "RigidBodyModule", "RenderingModule")
            .execute();
        client.startContainer(container.id());

        // Get scoped client
        var scope = client.container(container.id());

        // Create match
        var match = scope.createMatch(
            List.of("EntityModule", "RigidBodyModule", "RenderingModule")
        );

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
        scope.forMatch(match.id())
            .spawn().forPlayer(1).ofType(1).execute();

        // Start simulation
        scope.play(16);

        // Run game (blocks until window closes)
        orchestrator.start();

        // Cleanup
        client.stopContainer(container.id());
        client.deleteContainer(container.id());
    }
}
```

## Exception Handling

The fluent API on EngineClient throws `UncheckedIOException` for cleaner code:

```java
// No try-catch needed for common operations
var container = client.createContainer().name("test").execute();
client.startContainer(container.id());

var scope = client.container(container.id());
var match = scope.createMatch(List.of("EntityModule"));
scope.tick();

// Handle errors if needed
try {
    scope.forMatch(matchId).spawn().execute();
} catch (UncheckedIOException e) {
    log.error("Failed to spawn: {}", e.getCause().getMessage());
}
```

## Low-Level Adapter Access

For advanced use cases, access the underlying adapters directly:

```java
// Adapters throw checked IOException
ContainerAdapter containers = client.containers();
ModuleAdapter modules = client.modules();
AuthAdapter auth = client.auth();

// Container-scoped adapters
ContainerAdapter.ContainerScope scope = containers.forContainer(containerId);
```
