# Lightning Engine

A custom Entity Component System (ECS) game engine built in Java, featuring real-time state synchronization, a debugging GUI, and modular game logic.

## Quick Links

| Documentation | Description |
|---------------|-------------|
| [Getting Started](docs/getting-started.md) | Installation, first steps, spawn an entity |
| [Docker](docs/docker.md) | Container deployment and configuration |
| [Game SDK](docs/game-sdk.md) | BackendClient, Orchestrator, GameRenderer |
| [Game Masters](docs/game-masters.md) | Server-side game logic |
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
| **Columnar ECS** | `ArrayEntityComponentStore` with O(1) component access, float-based values, per-tick query caching |
| **Hot-Reload Modules** | Upload JAR files at runtime, reload without restart |
| **Real-Time Streaming** | WebSocket pushes ECS snapshots to clients every tick |
| **Debug GUI** | Desktop app for entity inspection, command execution, module management |
| **Headless Testing** | Selenium-inspired `GuiDriver` runs 179 tests without GPU |

### Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 25 |
| Build | Maven (multi-module) |
| Server | Quarkus 3.x (REST + WebSocket) |
| GUI | LWJGL 3 + NanoVG |
| Testing | JUnit 5, Testcontainers |
| API Docs | OpenAPI 3.0 ([openapi.yaml](openapi.yaml)) |

## Project Status

### Completed Features

| Feature | Status | Notes |
|---------|--------|-------|
| **Core ECS** | Done | ArrayEntityComponentStore with O(1) access, query caching |
| **Module System** | Done | 8 modules in separate Maven submodules, hot-reload via JAR upload |
| **REST API** | Done | Full CRUD for matches, commands, snapshots, modules, resources |
| **WebSocket Streaming** | Done | Real-time snapshot updates per match |
| **Debug GUI** | Done | Entity inspector, command console, resource browser |
| **Headless Testing** | Done | GuiDriver + HeadlessWindow for GPU-free tests |
| **Game SDK** | Done | BackendClient, Orchestrator, GameRenderer for game development |
| **Game Masters** | Done | Server-side command execution without WebSocket roundtrip |
| **Collision Detection** | Done | AABB collision with handler registration |
| **Physics Simulation** | Done | Rigid body with velocity, force, drag |

### In Progress / Planned

| Feature | Status | Notes |
|---------|--------|-------|
| **Rendering Engine** | In Progress | Custom NanoVG/OpenGL framework, 68 files |
| **Renderer Abstraction** | Partial | Components still call NanoVG directly |
| **Persistence** | Not Started | In-memory only; no database |
| **Authentication** | Not Started | No security layer |
| **Delta Compression** | Not Started | Full snapshots every tick |
| **Spatial Partitioning** | Not Started | Full-scan queries for collision |
| **Audio System** | Not Started | No audio support |
| **Full Module Isolation** | Not Started | A module should not be able to write components it does not own |

### Module Status

All modules are in separate Maven submodules under `lightning-engine-extensions/modules/`:

| Module | Description |
|--------|-------------|
| `entity-module` | Core entity management, position components |
| `health-module` | HP tracking, damage/heal commands |
| `rendering-module` | Sprite attachment and display |
| `rigid-body-module` | Physics: velocity, force, mass, drag |
| `box-collider-module` | AABB collision detection and handlers |
| `projectile-module` | Projectile spawning and management |
| `items-module` | Item/inventory system |
| `move-module` | Legacy movement (use RigidBodyModule instead) |

## Quick Start
See [Getting Started](docs/getting-started.md) for instructions.

## Strengths

- **Modular Architecture** - Hot-reloadable modules with per-match selection
- **Interface-Driven Design** - GPU-free testing via `HeadlessWindow`
- **Multi-Match Isolation** - Entities filtered per match for WebSocket clients
- **Debug GUI** - Real-time entity inspection, command console, resource browser

## Limitations

**This is not production software.** Known gaps:

| Category | Status |
|----------|--------|
| **Persistence** | In-memory only; no database |
| **Security** | No authentication/authorization |
| **Networking** | Full snapshots every tick; no delta compression |
| **Rendering** | Components still call NanoVG directly |

See [Architecture](docs/architecture.md) for more details.

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

## License

MIT
