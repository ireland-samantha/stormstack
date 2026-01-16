# Lightning Engine  

Lightning Engine is an open source, multi-game multiplayer server and backend framework built in Java. The core idea: multiple games and matches running concurrently on the same node, each inside an isolated execution container with its own ClassLoader, game loop, ECS store, and hot-reloadable game modules. Deploying a backend for a game is as easy as building a JAR with your game logic, uploading it to a Lightning instance, triggering hot reload, and creating containers and matches with your module enabled. 


> ⚠️ This is a personal hobby project, not production software, for fun and to learn. Pre-alpha and unstable. Built via pair programming with Claude Code. 

---

## Key Capabilities

### Execution Containers
- Isolated runtime environments using **ClassLoader isolation**
- Independent game loops, ECS stores, and resources
- Multiple games running safely on the same server

### ECS (Entity Component System)
- Array-based storage with **O(1) component access**
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

## 🏁 Quick Start (Docker)

### Prerequisites
- Docker

### Run
```bash
docker compose up -d
```
Open the React admin dashboard at:

```
http://localhost:8080/admin/dashboard
```
![demo.png](docs/demo.png)
**Default credentials:**
- Username: `admin`
- Password: `admin`
## Documentation

| Documentation | Description |
|---------------|-------------|
| [Docker](docs/docker.md) | Container deployment and configuration |
| [Frontend](docs/frontend.md) | Web dashboard (React/TypeScript) |
| [Game SDK](docs/game-sdk.md) | EngineClient, Orchestrator, GameRenderer |
| [AI System](docs/ai.md) | Server-side game logic (formerly Game Masters) |
| [Module System](docs/module-system.md) | Creating and deploying modules |
| [ClassLoader Isolation](docs/classloaders.md) | Container runtime isolation |
| [Rendering Library](docs/rendering-library.md) | NanoVG/OpenGL GUI framework |
| [Architecture](docs/architecture.md) | System design, project structure |
| [API Reference](docs/api-reference.md) | REST endpoints |
| [Testing](docs/testing.md) | Headless testing, performance |

## Next Steps

- [Game SDK](docs/game-sdk.md) - Build games with the client library
- [Architecture](docs/architecture.md) - Read the architecture docs
- [Module System](docs/module-system.md) - Create custom modules
- [API Reference](docs/api-reference.md) - Full REST API documentation

## License

MIT
