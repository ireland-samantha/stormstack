0# Lightning Engine  
**A modular, hot-reloadable multiplayer game backend written in Java**.

Lightning Engine (aka **Lightning Multiplayer Server**) is an open-source **Battle.net–style multiplayer backend** built in **Java**, focused on **runtime isolation**, **modular game logic**, and **real-time ECS streaming**.

It allows **multiple games and matches** to run simultaneously on shared infrastructure, each inside an isolated *Execution Container* with its own game loop, ECS store, resources, and hot-reloadable modules.

> ⚠️ This is a **learning and hobby project**, not production software. Code was written via pair programming with Claude Code.

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

## Module System

Modules live in separate Maven submodules and can be enabled per match:

- `entity-module` – Core entity management  
- `grid-map-module` - Track an entity's position on a map
- `rigid-body-module` – Physics simulation  
- `rendering-module` – Sprite attachment and rendering data  
- `box-collider-module` – AABB collision detection  
- `health-module` – HP, damage, healing  
- `projectile-module` – Projectile spawning  
- `items-module` – Inventory & items  

All modules are hot-reloadable at runtime.

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

Then, open localhost:8080/admin/dashboard in your browser and authenticate with credentials admin/admin.

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
