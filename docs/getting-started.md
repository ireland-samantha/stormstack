# Getting Started

## Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for containerized deployment or integration tests)

## Option A: Run with Docker (Recommended)

```bash
# Build and start the server
docker compose up -d

# Server runs at http://localhost:8080
curl http://localhost:8080/api/simulation/tick
```

The Docker image includes pre-built modules and the GUI JAR for download.

## Option B: Run from Source

```bash
# 1. Build all modules
./mvnw clean install

# 2. Start server in dev mode
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

Server runs at `http://localhost:8080`. API docs at `/q/swagger-ui` (if Quarkus OpenAPI extension enabled).

## Create a Match and Spawn an Entity

```bash
# Create match with modules enabled
curl -X POST http://localhost:8080/api/matches \
  -H "Content-Type: application/json" \
  -d '{"id": 0, "enabledModuleNames": ["EntityModule", "RigidBodyModule", "RenderingModule"]}'

# Queue a spawn command
curl -X POST http://localhost:8080/api/commands \
  -H "Content-Type: application/json" \
  -d '{"commandName": "spawn", "payload": {"matchId": 1, "entityType": 1, "playerId": 1}}'

# Advance tick to process command
curl -X POST http://localhost:8080/api/simulation/tick

# View ECS state
curl http://localhost:8080/api/snapshots/match/1
```

## Spawn an Entity with a Sprite

Complete workflow for creating a visible entity with texture:

```bash
# 1. Upload a texture resource
curl -X POST http://localhost:8080/api/resources \
  -F "file=@my-sprite.png" \
  -F "name=player-sprite"

# Response: {"id": 1, "name": "player-sprite", "size": 1234, ...}

# 2. Spawn an entity with (x,y) coordinates (position is a shared component)
curl -X POST http://localhost:8080/api/commands \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "spawn",
    "payload": {
      "matchId": 1,
      "entityId": 1,
      "positionX": 0,
      "positionY": 0
    }
  }'

# 3. Attach rigid body (rigid body uses position from the above command)
curl -X POST http://localhost:8080/api/commands \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "attachRigidBody",
    "payload": {
      "matchId": 1,
      "entityId": 1,
      "velocityX": 0,
      "velocityY": 0,
      "mass": 1.0
    }
  }'

# 4. Attach sprite with resource ID
curl -X POST http://localhost:8080/api/commands \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "attachSprite",
    "payload": {
      "matchId": 1,
      "entityId": 1,
      "resourceId": 1,
      "width": 32,
      "height": 32,
      "rotation": 0,
      "zIndex": 0,
      "visible": 1
    }
  }'

# 5. Process commands
curl -X POST http://localhost:8080/api/simulation/tick

# 6. Verify snapshot includes sprite data
curl http://localhost:8080/api/snapshots/match/1
```

**Key Points:**
- `POSITION_X`/`POSITION_Y` come from RigidBodyModule (or EntityModule)
- `RESOURCE_ID` links to uploaded texture via `/api/resources`
- Sprite mapper reads position from physics components (shared data)

## Run the Debug GUI

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

## Import Postman Collection

Import [postman-collection.json](../postman-collection.json) for pre-configured API requests.

## Next Steps

- [Game SDK](game-sdk.md) - Build games with the client library
- [Module System](module-system.md) - Create custom modules
- [API Reference](api-reference.md) - Full REST API documentation
