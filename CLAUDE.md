# CLAUDE.md

# StormStack - Thunder Engine & Lightning Tools

A production-grade multi-match game server framework built on Java 25 and Quarkus, featuring ECS architecture, hot-reloadable modules, and real-time WebSocket streaming.

## Project Overview

**StormStack** is a game server platform consisting of:
- **Thunder** - Backend services (Engine, Auth, Control Plane)
- **Lightning** - Client tools (CLI, Rendering Engine, Web Panel)

### Thunder Engine Features
- Multi-game execution on a single JVM via isolated containers with separate ClassLoaders
- Tick-based simulation with independent game loops per container
- Entity-Component-System (ECS) for entity management with O(1) component access
- Hot-reloadable modules for runtime game logic updates
- WebSocket streaming of ECS snapshots (full or delta-compressed)
- REST API for resource, match, and container management

### Lightning Tools Features
- `lightning` CLI for cluster management and match operations
- Rendering engine for game visualization
- React-based admin web panel

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 25 (with preview features, virtual threads), Go 1.24+ |
| **Build** | Maven 3.9+ (multi-module) |
| **Backend** | Quarkus 3.x (REST + WebSocket) |
| **Frontend** | React 18+ with TypeScript, Material-UI |
| **CLI** | Go with Cobra |
| **Persistence** | MongoDB 6.0+ |
| **Testing** | JUnit 5, Mockito, Playwright, Testcontainers |
| **Auth** | JWT + BCrypt (role-based access control) |

## Package Structure

All Java packages use the base: `ca.samanthaireland.stormstack`

- Thunder (backend): `ca.samanthaireland.stormstack.thunder.*`
- Lightning (client): `ca.samanthaireland.stormstack.lightning.*`
- Shared utilities: `ca.samanthaireland.stormstack.shared.*`

## Project Structure

```
stormstack/
├── pom.xml                              # Root Maven POM
├── CLAUDE.md                            # Development guidelines
├── openapi.yaml                         # OpenAPI 3.0 specification
├── docker-compose.yml                   # Local development infrastructure
├── build.sh                             # Build automation
│
├── thunder/                             # Backend services (Java)
│   ├── shared/                          # Shared utilities
│   │
│   ├── engine/                          # Thunder Engine
│   │   ├── core/                        # Domain + implementation (merged)
│   │   │   ├── command/                 # Command queue system
│   │   │   ├── container/               # ExecutionContainer
│   │   │   ├── entity/                  # ECS domain models
│   │   │   ├── match/                   # Match service
│   │   │   ├── session/                 # Player sessions
│   │   │   ├── snapshot/                # ECS state serialization
│   │   │   └── store/                   # Component store
│   │   │
│   │   ├── provider/                    # Quarkus application
│   │   │   ├── rest/                    # REST endpoints
│   │   │   ├── websocket/               # WebSocket streaming
│   │   │   └── persistence/             # MongoDB integration
│   │   │
│   │   ├── adapters/                    # SDKs & clients
│   │   │   ├── web-api-adapter/         # REST client library
│   │   │   ├── game-sdk/                # Game development SDK
│   │   │   └── api-proto/               # API protobuf definitions
│   │   │
│   │   ├── extensions/                  # Game modules
│   │   │   ├── modules/                 # ECS modules
│   │   │   └── game-masters/            # AI backends
│   │   │
│   │   └── tests/                       # Test suites
│   │       ├── api-acceptance/          # API acceptance tests
│   │       └── playwright/              # E2E browser tests
│   │
│   ├── auth/                            # Thunder Auth
│   │   ├── core/                        # Domain (NO framework deps)
│   │   ├── provider/                    # Quarkus application
│   │   └── adapters/                    # Framework adapters
│   │       ├── spring/                  # Spring Boot adapter
│   │       └── quarkus/                 # Quarkus adapter
│   │
│   ├── control-plane/                   # Thunder Control Plane
│   │   ├── core/                        # Domain (NO framework deps)
│   │   └── provider/                    # Quarkus application
│   │
│   └── proxy/                           # Thunder Proxy (gateway)
│
├── lightning/                           # Client tools
│   ├── cli/                             # Lightning CLI (Go)
│   │   ├── cmd/lightning/               # Main entry point
│   │   └── internal/                    # CLI internals
│   │
│   ├── rendering/                       # Lightning Rendering Engine (Java)
│   │   ├── core/                        # NanoVG-based GUI framework
│   │   └── test-framework/              # GUI testing utilities
│   │
│   └── webpanel/                        # Lightning Web Panel (React)
│       └── src/                         # React components
│
├── docs/                                # Documentation
└── scripts/                             # Build & deployment scripts
```

## Core Principles

1. **Separation of Concerns (SoC)**: Each module has one clear responsibility.

2. **Single Responsibility Principle (SRP)**: A module should have only one reason to change.

3. **Dependency Injection (DI)**: All dependencies are injected, not instantiated internally.

4. **Depend on Abstractions**: Depend on contracts/interfaces, not implementations.

5. **Clean Architecture Layers**:
   - **Core**: Pure domain abstractions, no framework dependencies
   - **Provider**: Quarkus REST/WebSocket endpoints and MongoDB persistence
   - **Adapters**: Framework-specific adapters (REST clients, SDKs)

6. **API-First Design**: Define contracts before implementation

7. **Two-Module Pattern for Services**: Each service domain uses two Maven modules:
   - **Core Module** (`*/core/`): Contains interfaces, implementations, domain models, exceptions, DTOs - **NO framework annotations**
   - **Provider Module** (`*/provider/`): Contains Quarkus-specific code (REST resources, configuration bindings, persistence, framework integration)
   - Service implementations in core modules must be **completely framework-agnostic**
   - Framework integration happens via `ServiceProducer` classes in the provider module

## Naming Conventions

**Domain Models:**
- Domain model: `Match`, `ExecutionContainer` (in `thunder/engine/core/`)
- Strongly-typed ID: `MatchId`, `ContainerId`

**Services:**
- Service interface: `MatchService` (in `*/core/`)
- Service implementation: `MatchServiceImpl` (in `*/core/`)

**Repositories:**
- Repository interface: `MatchRepository` (in `*/core/`)
- MongoDB implementation: `MongoMatchRepository` (in `*/provider/persistence/`)

**DTOs:**
- Request: `CreateMatchRequest`, `UpdateMatchRequest` (in `*/provider/dto/`)
- Response: `MatchResponse`

**REST Resources:**
- Resource: `MatchResource` (in `*/provider/rest/`)

**Exceptions:**
- Not found: `MatchNotFoundException` (in `*/core/exception/`)
- Invalid state: `InvalidMatchStateException`

## Build Commands

```bash
./build.sh build              # Build all modules (skip tests)
./build.sh test               # Run unit tests
./build.sh docker             # Build Docker image
./build.sh integration-test   # Run integration tests with Docker
./build.sh e2e-test           # Run lightning-cli e2e tests
./build.sh all                # Full pipeline
```

**Development:**
```bash
mvn quarkus:dev               # Backend dev mode (port 8080)
npm run dev                   # Frontend dev server (port 5173)
```

**Admin Dashboard:** `http://localhost:8080/admin/dashboard`

## REST API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET/POST /api/containers` | Container lifecycle |
| `GET/POST /api/containers/{id}/matches` | Match management |
| `POST /api/containers/{id}/modules` | Module installation |
| `POST /api/containers/{id}/ticks` | Tick control |
| `POST /api/containers/{id}/commands` | Command queueing |
| `GET/POST /api/resources` | Resource upload/download |
| `WS /ws/snapshots/{matchId}` | WebSocket snapshot streaming |

## Docker Images

- `samanthacireland/thunder-engine` - Main game server
- `samanthacireland/thunder-auth` - Authentication service
- `samanthacireland/thunder-control-plane` - Cluster management

## Git and Version Control

### Commit Messages

Use conventional commits:
```
type(scope): subject

body

footer
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Branch Strategy

- `main`: Production-ready code
- `feature/*`: New features
- `fix/*`: Bug fixes

## Quality Gates

Before declaring a feature complete, verify:

- [ ] Interface has complete Javadoc
- [ ] DTOs use Java records with validation annotations
- [ ] Custom exceptions for all failure cases
- [ ] All layers implemented (resource -> service -> repository)
- [ ] **Service implementations in `*-core` modules have NO framework annotations**
- [ ] **No `@Inject`, `@ApplicationScoped`, `@Singleton` in core module services**
- [ ] **ServiceProducer pattern used for framework integration in provider modules**
- [ ] Unit tests for all classes (>80% coverage)
- [ ] Integration tests with Testcontainers (MongoDB)
- [ ] `./build.sh all` passes
- [ ] No security vulnerabilities
