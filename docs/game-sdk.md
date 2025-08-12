# Game SDK

The Game SDK (`lightning-engine/engine-adapter/game-sdk`) provides a client library for building games on top of Lightning Engine.

## BackendClient

Fluent API for all backend operations:

```java
var client = BackendClient.connect("http://localhost:8080");

// Create a match with modules
var match = client.matches().create()
    .withModule("EntityModule")
    .withModule("RigidBodyModule")
    .withModule("RenderingModule")
    .execute();

// Queue commands
client.commands()
    .forMatch(match.id())
    .spawn().forPlayer(1).ofType(100).execute();

// Control simulation
client.simulation().tick();
client.simulation().play(16);  // 16ms interval (60 FPS)

// Fetch snapshots
var snapshot = client.snapshots().forMatch(match.id()).fetch();

// Manage modules and resources
var modules = client.modules().list();
client.resources().upload("texture.png", bytes);
```

## GameRenderer

Abstraction for rendering game state:

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

Combines backend and renderer with automatic WebSocket streaming:

```java
var orchestrator = Orchestrator.create()
    .backend(client)
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

## Complete Example

```java
public class MyGame {
    public static void main(String[] args) {
        // Connect to backend
        var client = BackendClient.connect("http://localhost:8080");

        // Create match
        var match = client.matches().create()
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
            .backend(client)
            .renderer(renderer)
            .forMatch(match.id())
            .build();

        // Spawn initial entity
        client.commands()
            .forMatch(match.id())
            .spawn().forPlayer(1).ofType(1).execute();

        // Start simulation
        client.simulation().play(16);

        // Run game (blocks until window closes)
        orchestrator.start();
    }
}
```
