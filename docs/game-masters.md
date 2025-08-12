# Game Masters

Game Masters provide server-side command execution, eliminating WebSocket roundtrips for game logic that doesn't need client input.

## Why Use Game Masters?

| Benefit | Description |
|---------|-------------|
| **Zero Latency** | Commands execute server-side in the same tick, no network roundtrip |
| **Game Logic Isolation** | Decouple game rules from infrastructure—no HTTP clients, WebSocket handling, or serialization |
| **AI & Automation** | Ideal for enemy AI, spawning waves, game rules, physics responses |

## How It Works

1. Game masters run on the server, invoked every tick
2. They observe game state via snapshots and queue commands directly
3. Commands execute in the same tick, no network delay

## Creating a Game Master

```java
public class AIGameMaster implements GameMaster {
    private final GameMasterContext context;

    public AIGameMaster(GameMasterContext context) {
        this.context = context;
    }

    @Override
    public void onTick() {
        // Read current state
        var snapshot = context.getSnapshot();

        // Queue commands based on game logic
        if (shouldSpawnEnemy(snapshot)) {
            context.queueCommand("spawn", Map.of(
                "entityType", 2,
                "playerId", 0,
                "x", randomX(),
                "y", 0
            ));
        }
    }
}
```

## GameMasterFactory

To make your game master discoverable, implement `GameMasterFactory`:

```java
public class AIGameMasterFactory implements GameMasterFactory {
    @Override
    public String getName() {
        return "AIGameMaster";
    }

    @Override
    public GameMaster create(GameMasterContext context) {
        return new AIGameMaster(context);
    }
}
```

## Deploying Game Masters

```bash
# Build JAR implementing GameMasterFactory
mvn clean package

# Upload to server
curl -X POST http://localhost:8080/api/gamemasters/upload \
  -F "file=@target/my-gamemaster.jar"

# Enable for a match
curl -X POST http://localhost:8080/api/matches \
  -H "Content-Type: application/json" \
  -d '{"enabledModuleNames": ["EntityModule"], "enabledGameMasters": ["AIGameMaster"]}'
```

## GameMasterContext API

| Method | Description |
|--------|-------------|
| `getSnapshot()` | Get current game state for the match |
| `queueCommand(name, payload)` | Queue a command for immediate execution |
| `getMatchId()` | Get the match this game master is running for |
| `getTick()` | Get the current tick number |

## Use Cases

### Enemy AI

```java
@Override
public void onTick() {
    var snapshot = context.getSnapshot();
    var enemies = getEnemies(snapshot);

    for (var enemy : enemies) {
        var player = findNearestPlayer(snapshot, enemy);
        if (player != null) {
            var direction = calculateDirection(enemy, player);
            context.queueCommand("setVelocity", Map.of(
                "entityId", enemy.id(),
                "velocityX", direction.x * ENEMY_SPEED,
                "velocityY", direction.y * ENEMY_SPEED
            ));
        }
    }
}
```

### Wave Spawning

```java
@Override
public void onTick() {
    if (context.getTick() % SPAWN_INTERVAL == 0) {
        for (int i = 0; i < ENEMIES_PER_WAVE; i++) {
            context.queueCommand("spawn", Map.of(
                "entityType", ENEMY_TYPE,
                "playerId", 0,
                "positionX", randomX(),
                "positionY", 0
            ));
        }
    }
}
```

### Collision Response

```java
@Override
public void onTick() {
    var collisions = getCollisions(context.getSnapshot());

    for (var collision : collisions) {
        if (isProjectileHittingEnemy(collision)) {
            context.queueCommand("damage", Map.of(
                "entityId", collision.enemyId(),
                "amount", PROJECTILE_DAMAGE
            ));
            context.queueCommand("destroy", Map.of(
                "entityId", collision.projectileId()
            ));
        }
    }
}
```
