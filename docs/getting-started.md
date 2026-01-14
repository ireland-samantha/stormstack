# Getting Started

## Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for containerized deployment or integration tests)

## Option A: Run with Docker (Recommended)

```bash
# Build and start the server (includes MongoDB for persistence)
docker compose up -d

# Server runs at http://localhost:8080
```

The Docker image includes pre-built modules, the GUI JAR for download, and MongoDB for snapshot persistence.

## Authentication

All API endpoints require JWT authentication. First, obtain a token:

```bash
# Login with default admin credentials
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'

# Response: {"token": "eyJ...", "userId": 1, "username": "admin", "expiresAt": "..."}
```

Use the token in subsequent requests:

```bash
# Store token for convenience
TOKEN="eyJ..."

# Include in Authorization header
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/simulation/tick
```

### Default Roles

| Role | Permissions |
|------|-------------|
| `admin` | Full access (includes command_manager and view_only) |
| `command_manager` | Post commands, manage matches |
| `view_only` | Read-only access to snapshots |

**Security Note:** Change the default admin password in production!

### Offline Tokens for Automation

For automated services, CI/CD pipelines, or long-lived integrations, generate offline tokens using the `issue-api-token` CLI:

```bash
# Build the token issuer
./mvnw package -pl issue-api-token -DskipTests

# Generate admin token (24h default expiry)
java -jar issue-api-token/target/issue-api-token.jar \
  --roles=admin \
  --secret=your-jwt-secret

# Generate token with multiple roles and custom expiry
java -jar issue-api-token/target/issue-api-token.jar \
  --roles=command_manager,view_only \
  --user=ci-pipeline \
  --expiry=168  # 7 days

# View help
java -jar issue-api-token/target/issue-api-token.jar --help
```

The secret must match the `mp.jwt.verify.secret` configured in the backend.

## Option B: Run from Source

```bash
# 1. Build all modules
./mvnw clean install

# 2. Start server in dev mode
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

Server runs at `http://localhost:8080`. API docs at `/q/swagger-ui` (if Quarkus OpenAPI extension enabled).

## Create a Container, Match, and Spawn an Entity

Lightning Engine uses **Execution Containers** for isolated runtime environments. Each container has its own ClassLoader, ECS store, and game loop.

```bash
# Set your auth token (from login response)
TOKEN="eyJ..."

# 1. Create a container
curl -X POST http://localhost:8080/api/containers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "my-game-server"}'
# Response: {"id": 1, "name": "my-game-server", "status": "CREATED"}

# 2. Start the container
curl -X POST http://localhost:8080/api/containers/1/start \
  -H "Authorization: Bearer $TOKEN"

# 3. Create a match inside the container
curl -X POST http://localhost:8080/api/containers/1/matches \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabledModuleNames": ["EntityModule", "RigidBodyModule", "RenderingModule"]}'
# Response: {"id": 1, "containerId": 1, "enabledModuleNames": [...]}

# 4. Queue a spawn command (container-scoped)
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"commandName": "spawn", "payload": {"matchId": 1, "entityType": 1, "playerId": 1}}'

# 5. Advance tick (processes commands in container)
curl -X POST http://localhost:8080/api/containers/1/tick \
  -H "Authorization: Bearer $TOKEN"

# 6. View ECS state
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/matches/1/snapshot

# 7. Start auto-advance at 60 FPS
curl -X POST "http://localhost:8080/api/containers/1/play?intervalMs=16" \
  -H "Authorization: Bearer $TOKEN"

# 8. Stop auto-advance
curl -X POST http://localhost:8080/api/containers/1/stop-auto \
  -H "Authorization: Bearer $TOKEN"
```

## Spawn an Entity with a Sprite

Complete workflow for creating a visible entity with texture (assuming container 1 is running):

```bash
# 1. Upload a texture resource
curl -X POST http://localhost:8080/api/resources \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@my-sprite.png" \
  -F "name=player-sprite"
# Response: {"id": 1, "name": "player-sprite", "size": 1234, ...}

# 2. Spawn an entity
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "spawn",
    "payload": {
      "matchId": 1,
      "entityId": 1
    }
  }'

# 3. Attach rigid body
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "attachRigidBody",
    "payload": {
      "matchId": 1,
      "entityId": 1,
      "positionX": 0,
      "positionY": 0,
      "velocityX": 0,
      "velocityY": 0,
      "mass": 1.0
    }
  }'

# 4. Attach sprite with resource ID
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
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
curl -X POST http://localhost:8080/api/containers/1/tick \
  -H "Authorization: Bearer $TOKEN"

# 6. Verify snapshot includes sprite data
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/containers/1/matches/1/snapshot
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
