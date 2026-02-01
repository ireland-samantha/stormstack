# Stormstack (formerly Lightning Engine)
![Status](https://img.shields.io/badge/status-experimental-blueviolet)
![Java](https://img.shields.io/badge/java-25-blue)
![License](https://img.shields.io/github/license/ireland-samantha/lightning-engine)

## What is this?
StormStack is a Java-based authoritative multiplayer game server platform.
It enables hot-deployment of untrusted game logic at runtime, using JVM ClassLoader isolation and JWT-scoped ECS access to safely run multiple matches on shared infrastructure.

I built StormStack to explore modular and secure backend architecture within a game dev context.

The project has two main components:
- **Thunder** (Backend): Game engine, control panel, and IAM service
- **Lightning** (Client Tools): CLI, web admin panel, and, (eventually), rendering engine

### How It Works

1. **Write game modules** - Implement interfaces and build JAR files containing your backend game logic (ECS components, systems, commands)
3. **Install module in cluster** - Upload modules to the control plane and distribute to engine nodes

```
$ lightning module upload MyGameModule 1.0.0 ./target/my-module.jar
✓ Module MyModule@1.0.0 uploaded successfully
```
```
$ lightning module distribute MyModule 1.0.0
✓ Module MyGameModule@1.0.0 distributed to 3 nodes
```

4. **Deploy matches** 
```
$ lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

# Output:
✓ Match deployed successfully!
    Match ID:     node-1-42-1
    Node:         node-1
    Container:    42
    Status:       RUNNING
 
  Endpoints:
    HTTP:         http://backend:8080/api/containers/42
    WebSocket:    ws://backend:8080/ws/containers/42/matches/1/snapshots
    Commands:     ws://backend:8080/ws/containers/42/matches/1/commands
```
5. **Stream game state** - Clients receive real-time ECS snapshots via WebSocket.

```
[CMD] lightning snapshot get -o json
[INFO] {
  "matchId": 1,
  "tick": 129,
  "modules": [
    {
      "name": "EntityModule",
      "version": "1.0",
      "components": [
        {
          "name": "ENTITY_ID",
          "values": [
            1,
            2,
            3,... ]
        }
      ]
    }
  ]
}
```
## Status

StormStack is an **experimental hobby project**, not production software. It exists for fun, learning, and the pure joy of over-engineering.

**What works:**
- Multi-container game execution with ClassLoader isolation
- Hot-reloadable modules with JWT-based permission scoping
- Real-time WebSocket snapshot streaming with delta compression
- OAuth2/OIDC authentication with scope-based authorization
- Cluster orchestration with autoscaling recommendations
- Full CLI for cluster management and game operations

**What's experimental:**
- Performance at scale (tested with hundreds of entities, not millions)
- Production deployment patterns
- Multi-region clustering

If you enjoy complexity, puzzles, and well-executed over-engineering, welcome aboard.

## Key Capabilities

### Execution Containers
- Isolated runtime environments using **ClassLoader isolation**
- Independent game loops, ECS stores, and resources
- Multiple games running safely on the same server

### ECS (Entity Component System)
- Array-based ECS storage with **O(1) component access** (because lightning and all)
- Float-based values for cache efficiency
- Per-tick query caching
- Match-scoped entity filtering

### Hot-Reloadable Modules
- Upload JARs at runtime
- Enable/disable modules per match
- Reload game logic **without restarting the server**

### Real-Time Streaming
- WebSocket streaming of ECS snapshots
- Full or delta-compressed updates every tick
- Designed for web or native clients

### Web Dashboard
- React-based admin UI
- Container & match management
- Live ECS inspection
- Command console
- Resource browser

### Testing & Automation
- Headless mode for GPU-free testing
- JUnit integration tests
- Playwright E2E tests
- Testcontainers for infrastructure tests

### Security
- JWT-based authentication
- Hierarchical, role-based access control
- Offline token generation for CI/CD
- Modules authenticate during installation and are issued a JWT identifying what components they can modify.


---

## Core Concepts

| Concept | Description |
|------|------------|
| **Execution Container** | Isolated runtime with its own ECS, modules, resources, and game loop |
| **Match** | A game session running inside a container |
| **Module** | Hot-reloadable game logic packaged as a JAR |
| **Command** | Server-side action queued and executed during ticks |
| **Snapshot** | Serialized ECS state streamed to clients |

---

## Tech Stack

| Layer | Technology |
|-----|-----------|
| Language | Java 25 |
| Backend | Quarkus 3.x (REST + WebSocket) |
| Build | Maven (multi-module) |
| Frontend | React + TypeScript + Material-UI |
| Testing | JUnit 5, Playwright, Testcontainers |
| Persistence | MongoDB |
| API Docs | OpenAPI 3.0 |

---

## Quick Start

### Option 1: Docker Compose + Lightning CLI (Recommended)

The full stack includes MongoDB, Redis, Auth service, Control Plane, and the game engine:

```bash
git clone https://github.com/ireland-samantha/lightning-engine.git
cd lightning-engine

# Copy and configure environment variables
cp .env.example .env
# Edit .env to set AUTH_JWT_SECRET and ADMIN_INITIAL_PASSWORD

docker compose up -d
```

This starts:
- **mongodb** (port 27017) - Shared database for all services
- **redis** (port 6379) - Control plane node registry
- **auth** (port 8082) - Authentication service (JWT, user management)
- **control-plane** (port 8081) - Cluster management and node orchestration
- **backend** (port 8080) - Thunder Engine game server API

For a multi-node cluster:
```bash
docker compose --profile cluster up -d
```

### Using the Lightning CLI

Build and use the CLI to manage your cluster:

```bash
# Build the CLI (requires Go 1.24+)
cd lightning/cli && go build -o lightning ./cmd/lightning && cd ../..

# Configure and authenticate
./lightning/cli/lightning config set control_plane_url http://localhost:8081
./lightning/cli/lightning auth login --username admin --password admin

# Check cluster health
./lightning/cli/lightning cluster status

# Deploy a game match
./lightning/cli/lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

# List running matches
./lightning/cli/lightning match list

# Join a match and get WebSocket credentials
./lightning/cli/lightning match join node-1-1-1 --player-name "Player1" --player-id "p1"

# Set context for match operations
./lightning/cli/lightning node context match node-1-1-1

# Start the simulation
./lightning/cli/lightning node simulation play --interval-ms 16

# Send game commands
./lightning/cli/lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

# Get game state
./lightning/cli/lightning snapshot get
```

See [CLI Quickstart](docs/cli-quickstart.md) for the complete guide.

### Option 2: Build from Source

```bash
git clone https://github.com/ireland-samantha/lightning-engine.git
cd lightning-engine

./build.sh build            # Build all modules
./build.sh docker           # Build all Docker images
./build.sh integration-test # Run integration tests
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ADMIN_INITIAL_PASSWORD` | Yes | Password for the admin user |
| `AUTH_JWT_SECRET` | Yes | Secret for JWT signing (use a long random string, 32+ chars) |
| `CONTROL_PLANE_TOKEN` | No | Token for control plane API access (default: `dev-token`) |
| `AUTH_IMAGE` | No | Override auth service Docker image |
| `CONTROL_PLANE_IMAGE` | No | Override control plane Docker image |
| `ENGINE_IMAGE` | No | Override engine Docker image |
| `CORS_ORIGINS` | Production | Allowed CORS origins (e.g., `https://yourdomain.com`) |

Open the React admin dashboard at:

```
http://localhost:8080/admin/dashboard
```
![demo.png](docs/demo.png)
**Credentials:**
- Username: `admin`
- Password: The value you set in `ADMIN_INITIAL_PASSWORD` (or check logs for the generated password)

---

## Local Development Setup

### Prerequisites

- Java 25 (with preview features enabled)
- Maven 3.9+
- Node.js 18+ and npm (for frontend)
- MongoDB 6.0+ (running locally or via Docker)
- Docker (for running integration tests)

### Build from Source
First, take a deep breath.

```bash
# Clone the repository
git clone https://github.com/ireland-samantha/lightning-engine.git
cd lightning-engine
```

Use the `build.sh` script for common tasks:

```bash
./build.sh clean            # Clean build artifacts
./build.sh build            # Build all modules (skip tests)
./build.sh test             # Run unit tests
./build.sh docker           # Build Docker image
./build.sh integration-test # Build Docker + run integration tests
./build.sh all              # Full pipeline: clean → build → test → integration-test
```

Or use Maven directly:

```bash
# Build all modules (skip tests for faster initial build)
mvn clean install -DskipTests

# Build with tests
mvn clean install
```

### Run MongoDB

```bash
# Using Docker
docker run -d --name mongodb -p 27017:27017 mongo:6.0

# Or use your local MongoDB installation
```

### Run the Backend

```bash
# Set required environment variables
export ADMIN_INITIAL_PASSWORD=dev-password
export QUARKUS_MONGODB_CONNECTION_STRING=mongodb://localhost:27017

# Run in development mode (with hot reload)
cd lightning-engine/webservice/quarkus-web-api
mvn quarkus:dev
```

### Run the Frontend (Development)

```bash
cd lightning-engine/webservice/quarkus-web-api/src/main/frontend
npm install
npm run dev
```

The frontend dev server runs at `http://localhost:5173` and proxies API requests to the backend.

### Running Tests

```bash
# Run unit tests
./build.sh test

# Run integration tests (requires Docker)
./build.sh integration-test

# Or use Maven directly
mvn test                                    # Unit tests
mvn verify -Pacceptance-tests               # Integration tests
```

### IDE Setup

**IntelliJ IDEA** (Recommended):
1. Open the root `pom.xml` as a project
2. Enable annotation processing for Lombok (Settings → Build → Compiler → Annotation Processors)
3. Set Project SDK to Java 25
4. Enable preview features in compiler settings

**VS Code**:
1. Install "Extension Pack for Java"
2. Open the project folder
3. The extensions will auto-detect the Maven project

---

## Documentation

| Documentation                                  | Description                                    |
|------------------------------------------------|------------------------------------------------|
| [CLI Quickstart](docs/cli-quickstart.md)       | Lightning CLI commands and workflows           |
| [Control Plane](docs/control-plane.md)         | Cluster orchestration and node management      |
| [Docker](docs/docker.md)                       | Container deployment and configuration         |
| [Frontend](docs/frontend.md)                   | Web dashboard (React/TypeScript)               |
| [Game SDK](docs/game-sdk.md)                   | EngineClient, Orchestrator, GameRenderer       |
| [AI System](docs/ai.md)                        | Server-side game logic (formerly Game Masters) |
| [Module System](docs/module-system.md)         | Creating and deploying modules                 |
| [ClassLoader Isolation](docs/classloaders.md)  | Container runtime isolation                    |
| [Rendering Library](docs/rendering-library.md) | NanoVG/OpenGL GUI framework                    |
| [Architecture](docs/architecture.md)           | System design, project structure               |
| [API Reference](docs/api-reference.md)         | REST endpoints                                 |
| [Testing](docs/testing.md)                     | Headless testing, performance                  |
| [Performance](docs/performance.md)             | Performance notes + results                    |

## Next Steps

- [CLI Quickstart](docs/cli-quickstart.md) - Deploy your first game with the CLI
- [Control Plane](docs/control-plane.md) - Manage multi-node clusters
- [Module System](docs/module-system.md) - Create custom game modules
- [Game SDK](docs/game-sdk.md) - Build games with the Java client library
- [Architecture](docs/architecture.md) - Understand the system design
- [API Reference](docs/api-reference.md) - Full REST API documentation

## Project Goals
- Have fun
- Maybe ship a game 

## Project Non-Goals
- Being simple  
- Being trendy  
- Protecting you from your own ideas
  
## License

MIT — Use this however you want (just not for evil tho)
