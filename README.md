# Lightning Engine

A multitenant game server built in Java, featuring real-time state synchronization, match/module-level isolation, a web dashboard, and modular game logic.

## Documentation

| Documentation | Description |
|---------------|-------------|
| [Docker](docs/docker.md) | Container deployment and configuration |
| [Game SDK](docs/game-sdk.md) | EngineClient, Orchestrator, GameRenderer |
| [AI System](docs/ai.md) | Server-side game logic (formerly Game Masters) |
| [Module System](docs/module-system.md) | Creating and deploying modules |
| [Rendering Library](docs/rendering-library.md) | NanoVG/OpenGL GUI framework |
| [Architecture](docs/architecture.md) | System design, project structure |
| [API Reference](docs/api-reference.md) | REST endpoints |
| [Testing](docs/testing.md) | Headless testing, performance |

## Overview

Lightning Engine is a **learning/hobby project** exploring ECS architecture and AI-assisted development,
built via pair programming with [Claude Code](https://claude.ai).

### Core Capabilities

| Feature | Description |
|---------|-------------|
| **Execution Containers** | Isolated runtime environments with ClassLoader isolation, independent game loops, and container-scoped matches |
| **Columnar ECS** | `ArrayEntityComponentStore` with O(1) component access, float-based values, per-tick query caching |
| **Hot-Reload Modules** | Upload JAR files at runtime, reload without restart |
| **Real-Time Streaming** | WebSocket pushes ECS snapshots (full or delta) to clients every tick |
| **Web Dashboard** | React-based admin UI for container management, entity inspection, command execution |
| **Headless Testing** | Playwright E2E tests, JUnit integration tests with Testcontainers |
| **JWT Authentication** | Role-based access control with hierarchical permissions |

### Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 |
| Build | Maven (multi-module) |
| Server | Quarkus 3.x (REST + WebSocket) |
| Frontend | React + Material-UI + Redux Toolkit |
| Testing | JUnit 5, Playwright, Testcontainers |
| API Docs | OpenAPI 3.0 ([openapi.yaml](openapi.yaml)) |

---

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for containerized deployment or integration tests)

### Option A: Run with Docker (Recommended)

```bash
# Build and start the server (includes MongoDB for persistence)
docker compose up -d

# Server runs at http://localhost:8080
```

The Docker image includes pre-built modules, the React frontend, and MongoDB for snapshot persistence.

### Option B: Run from Source

```bash
# 1. Build all modules
./mvnw clean install

# 2. Start server in dev mode
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

Server runs at `http://localhost:8080`.

---

## Web Dashboard

Open the React admin dashboard at:

```
http://localhost:8080/admin/dashboard
```
![demo.png](docs/demo.png)
**Default credentials:**
- Username: `admin`
- Password: `admin`

The dashboard provides:
- **Container Management** - Create, start, stop, delete execution containers
- **Match Control** - Create matches with selected modules, view match state
- **Live Snapshots** - Real-time ECS state visualization via WebSocket
- **Command Console** - Execute commands with parameter templates
- **Resource Browser** - Upload and manage textures and assets
- **Module/AI Management** - View installed modules and AI per container

---

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
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers
```

### Default Roles

| Role | Permissions |
|------|-------------|
| `admin` | Full access (includes command_manager and view_only) |
| `command_manager` | Post commands, manage matches |
| `view_only` | Read-only access to snapshots |

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

---

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
  -d '{"commandName": "spawn", "parameters": {"matchId": 1, "entityType": 1, "playerId": 1}}'

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

---

## Spawn an Entity with a Sprite

Complete workflow for creating a visible entity with texture (assuming container 1 is running):

```bash
# 1. Upload a texture resource to the container
curl -X POST http://localhost:8080/api/containers/1/resources \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@my-sprite.png" \
  -F "resourceName=player-sprite" \
  -F "resourceType=TEXTURE"
# Response: {"resourceId": 1, "resourceName": "player-sprite", "resourceType": "TEXTURE"}

# 2. Spawn an entity
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "spawn",
    "parameters": {
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
    "parameters": {
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
    "parameters": {
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

---

## Import Postman Collection

Import [postman-collection.json](postman-collection.json) for pre-configured API requests.

---

## Project Status

### Completed Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Core ECS** | Done | ArrayEntityComponentStore with O(1) access, query caching |
| **Module System** | Done | 8 modules in separate Maven submodules, hot-reload via JAR upload |
| **REST API** | Done | Full CRUD for matches, commands, snapshots, modules, resources |
| **WebSocket Streaming** | Done | Real-time snapshot updates per match |
| **Web Dashboard** | Done | React admin UI with container management |
| **Headless Testing** | Done | Playwright E2E tests, JUnit integration tests |
| **Game SDK** | Done | EngineClient, Orchestrator, GameRenderer for game development |
| **AI System** | Done | Server-side command execution without WebSocket roundtrip |
| **Collision Detection** | Done | AABB collision with handler registration |
| **Physics Simulation** | Done | Rigid body with velocity, force, drag |
| **Execution Containers** | Done | Isolated runtime environments with ClassLoader isolation |
| **Authentication** | Done | JWT-based with dynamic RBAC |
| **Persistence** | Done | MongoDB snapshot persistence |
| **Delta Compression** | Done | Bandwidth-efficient WebSocket streaming |
| **Module Isolation** | Done | JWT-based ECS access control |

### In Progress / Planned

| Feature | Status | Notes |
|---------|--------|-------|
| **Spatial Partitioning** | Not Started | Full-scan queries for collision |
| **Audio System** | Not Started | No audio support |

### Module Status

All modules are in separate Maven submodules under `lightning-engine-extensions/modules/`:

| Module | Description |
|--------|-------------|
| `entity-module` | Core entity management |
| `grid-map-module` | Position management with map boundaries |
| `health-module` | HP tracking, damage/heal commands |
| `rendering-module` | Sprite attachment and display |
| `rigid-body-module` | Physics: velocity, force, mass, drag |
| `box-collider-module` | AABB collision detection and handlers |
| `projectile-module` | Projectile spawning and management |
| `items-module` | Item/inventory system |
| `move-module` | Legacy movement (use RigidBodyModule instead) |

---

## Strengths

- **Modular Architecture** - Hot-reloadable modules with per-match selection
- **Interface-Driven Design** - GPU-free testing via headless mode
- **Multi-Match Isolation** - Entities filtered per match for WebSocket clients
- **Web Dashboard** - Real-time entity inspection, command console, resource browser

## Limitations

**This is not production software.** Known gaps:

| Category | Status |
|----------|--------|
| **Spatial Partitioning** | Full-scan queries for collision detection |
| **Audio** | No audio system |

See [Architecture](docs/architecture.md) for more details.

---

## AI-Assisted Development: Learnings

### What Worked

- **Enforcing a workflow:** TDD kept the model on task
- **Continuous refactoring:** Regular SOLID/clean code evaluations
- **Knowledge persistence:** Session summaries in `llm-learnings/`
- **Defensive programming:** Immutable records, validation in constructors

### What Didn't Work

- **Trusting generated code blindly:** Uncorrected mistakes compounded
- **Ambiguous prompts:** Vague requests produced vague implementations
- **Skipping test reviews:** Generated tests sometimes asserted wrong behavior

### Key Insight

AI accelerates development but requires human rigor. The model produces code you must understand—if you can't explain why it works, you can't fix it when it breaks.

---

## Next Steps

- [Game SDK](docs/game-sdk.md) - Build games with the client library
- [Module System](docs/module-system.md) - Create custom modules
- [API Reference](docs/api-reference.md) - Full REST API documentation

## License

MIT
