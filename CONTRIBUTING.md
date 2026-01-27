# Contributing to Lightning Engine

Welcome! We're excited that you're interested in contributing to Lightning Engine. This document outlines our coding standards, architectural principles, and development practices.

## Table of Contents

- [Code Quality Principles](#code-quality-principles)
- [SOLID Principles](#solid-principles)
- [Layered Architecture](#layered-architecture)
- [Interface-Driven Design](#interface-driven-design)
- [Testing Requirements](#testing-requirements)
- [Dependency Injection](#dependency-injection)
- [Modular, Decoupled Code](#modular-decoupled-code)
- [Domain Logic](#domain-logic)
- [Entity-Component-System (ECS)](#entity-component-system-ecs)
- [Codebase Overview](#codebase-overview)

---

## Code Quality Principles

We follow principles from three foundational books: **Clean Code**, **Effective Java**, and **Clean Architecture**. Here's a summary of the key guidelines:

### From Clean Code (Robert C. Martin)

| Principle | Description |
|-----------|-------------|
| **Meaningful Names** | Choose clear, intention-revealing names. Well-named code is self-documenting and reduces the need for comments. |
| **Small Functions** | Functions should do one thing and do it well. Keep them under 20 lines when possible. |
| **Single Level of Abstraction** | Statements within a function should all be at the same level of abstraction. |
| **The Boy Scout Rule** | Leave code cleaner than you found it. Small, incremental improvements compound over time. |
| **DRY (Don't Repeat Yourself)** | Code should be reused, not copied. Extract common logic into shared utilities. |
| **Separation of Concerns** | Separate error handling from business logic. Use exceptions rather than return codes. |
| **Law of Demeter** | A class should only know its direct dependencies. Avoid method chains like `a.getB().getC().doThing()`. |

### From Effective Java (Joshua Bloch)

| Principle | Description |
|-----------|-------------|
| **Static Factory Methods** | Prefer static factory methods over constructors for improved readability and control. |
| **Builders for Complex Objects** | Use the Builder pattern for objects with many optional parameters. |
| **Favor Immutability** | Immutable objects are inherently thread-safe and simpler to reason about. |
| **Composition Over Inheritance** | Inheritance creates tight coupling. Prefer composition for flexibility. |
| **Interfaces Over Abstract Classes** | Interfaces allow multiple implementations and are more flexible. |
| **Minimize Accessibility** | Make classes and members as private as possible. Information hiding enables maintainability. |
| **Use Enums Over Int Constants** | Enums provide type safety, meaningful names, and can have methods and fields. |
| **Avoid Raw Types** | Always use parameterized generic types for compile-time type safety. |
| **Measure Before Optimizing** | Never optimize without profiling first. Premature optimization creates needless complexity. |
| **Avoid Serialization** | Prefer JSON or Protocol Buffers over Java serialization. |

### From Clean Architecture (Robert C. Martin)

| Principle | Description |
|-----------|-------------|
| **The Dependency Rule** | Source code dependencies must point inward, toward higher-level policies. Inner layers must not know about outer layers. |
| **Framework Independence** | Architecture should not depend on frameworks. Frameworks are tools, not the center of your design. |
| **Testability** | Business rules can be tested without UI, database, or external services. |
| **UI Independence** | The UI can change without changing business rules. |
| **Database Independence** | Business rules are not bound to a specific database. You can swap databases without changing core logic. |
| **Separation of Business Logic** | Keep domain logic isolated from technical implementation details (web frameworks, databases, etc.). |

---

## SOLID Principles

All contributions must adhere to the SOLID principles:

### Single Responsibility Principle (SRP)
> A class should have only one reason to change.

```java
// Good: Separate responsibilities
public class MatchService {
    public Match create(MatchRequest request) { /* ... */ }
}

public class MatchValidator {
    public void validate(MatchRequest request) { /* ... */ }
}

// Bad: Multiple responsibilities
public class MatchService {
    public Match create(MatchRequest request) { /* ... */ }
    public void sendNotificationEmail(Match match) { /* ... */ }
    public String generateReport(Match match) { /* ... */ }
}
```

### Open/Closed Principle (OCP)
> Software entities should be open for extension but closed for modification.

```java
// Good: Extend via new implementations
public interface EngineCommand {
    CommandResult execute(CommandPayload payload, EngineContext context);
}

public class SpawnCommand implements EngineCommand { /* ... */ }
public class MoveCommand implements EngineCommand { /* ... */ }

// Bad: Modifying existing code for new behavior
public class CommandHandler {
    public void handle(String type) {
        if (type.equals("spawn")) { /* ... */ }
        else if (type.equals("move")) { /* ... */ }
        // Must modify this class for every new command
    }
}
```

### Liskov Substitution Principle (LSP)
> Subtypes must be substitutable for their base types.

```java
// Good: Subtypes honor the contract
public interface EntityComponentStore {
    float getValue(int entityId, int componentId);
}

public class ArrayEntityComponentStore implements EntityComponentStore {
    @Override
    public float getValue(int entityId, int componentId) {
        // Behaves as expected
    }
}
```

### Interface Segregation Principle (ISP)
> Clients should not be forced to depend on interfaces they don't use.

```java
// Good: Small, focused interfaces
public interface Readable {
    float getValue(int entityId, int componentId);
}

public interface Writable {
    void setValue(int entityId, int componentId, float value);
}

// Bad: Fat interface
public interface EntityStore {
    float getValue(int entityId, int componentId);
    void setValue(int entityId, int componentId, float value);
    void delete(int entityId);
    List<Integer> query(Predicate<Entity> predicate);
    void snapshot();
    void restore(Snapshot snapshot);
    // Clients forced to implement everything
}
```

### Dependency Inversion Principle (DIP)
> High-level modules should not depend on low-level modules. Both should depend on abstractions.

```java
// Good: Depend on abstractions
public class MatchService {
    private final MatchRepository repository; // Interface

    public MatchService(MatchRepository repository) {
        this.repository = repository;
    }
}

// Bad: Depend on concrete implementations
public class MatchService {
    private final MongoMatchRepository repository = new MongoMatchRepository();
}
```

---

## Layered Architecture

Lightning Engine follows a strict layered architecture where dependencies flow in one direction: from outer layers inward toward the core.

### The Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                             │
│         REST Resources, WebSocket Endpoints, CLI                    │
│                (webservice/quarkus-web-api)                         │
├─────────────────────────────────────────────────────────────────────┤
│                      APPLICATION LAYER                              │
│         Services, Use Cases, Orchestration Logic                    │
│                    (engine-internal)                                │
├─────────────────────────────────────────────────────────────────────┤
│                        DOMAIN LAYER                                 │
│    Entities, Value Objects, Domain Services, Business Rules         │
│                      (engine-core)                                  │
├─────────────────────────────────────────────────────────────────────┤
│                     INFRASTRUCTURE LAYER                            │
│       Repositories, External APIs, Database, Messaging              │
│               (engine-internal, adapters)                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Responsibility | Can Depend On |
|-------|----------------|---------------|
| **Presentation** | Handle HTTP/WebSocket requests, serialize responses, validate input format | Application, Domain |
| **Application** | Orchestrate use cases, transaction management, call domain services | Domain, Infrastructure (via interfaces) |
| **Domain** | Business rules, entities, value objects. No framework dependencies. | Nothing (pure Java) |
| **Infrastructure** | Implement repository interfaces, external service adapters | Domain (implements interfaces) |

### The Dependency Rule

> Source code dependencies must point inward. Nothing in an inner circle can know about anything in an outer circle.

```java
// GOOD: Presentation depends on Application
@Path("/containers")
public class ContainerResource {
    private final ContainerService service;  // Application layer
}

// GOOD: Application depends on Domain
public class ContainerService {
    private final Container container;  // Domain object
    private final MatchRepository repository;  // Domain interface
}

// GOOD: Infrastructure implements Domain interfaces
public class MongoMatchRepository implements MatchRepository {
    // Implementation details hidden from domain
}

// BAD: Domain depends on Infrastructure
public class Match {
    private final MongoClient mongo;  // Never do this!
}
```

### Crossing Layer Boundaries

Use **interfaces** at layer boundaries. The domain defines what it needs; infrastructure provides the implementation.

```java
// Domain layer defines the contract
public interface MatchRepository {
    Optional<Match> findById(int id);
    Match save(Match match);
}

// Infrastructure layer implements it
public class MongoMatchRepository implements MatchRepository {
    private final MongoClient client;

    @Override
    public Optional<Match> findById(int id) {
        // MongoDB-specific implementation
    }
}

// Application layer uses the interface (injected at runtime)
public class MatchService {
    private final MatchRepository repository;  // Interface, not impl
}
```

### Data Transfer Between Layers

Use DTOs (Data Transfer Objects) at layer boundaries to prevent domain objects from leaking infrastructure concerns:

```java
// Presentation layer DTO
public record MatchResponse(int id, String status, List<PlayerDto> players) {
    public static MatchResponse from(Match match) {
        return new MatchResponse(
            match.getId(),
            match.getState().name(),
            match.getPlayers().stream().map(PlayerDto::from).toList()
        );
    }
}

// Resource converts domain to DTO
@GET
@Path("/{id}")
public MatchResponse getMatch(@PathParam("id") int id) {
    Match match = matchService.findById(id);
    return MatchResponse.from(match);
}
```

---

## Interface-Driven Design

We practice interface-driven design throughout the codebase. This approach improves testability, enables loose coupling, and allows implementations to evolve independently.

### Core Principles

1. **Define contracts first**: Start with interfaces that describe behavior, then implement.
2. **Depend on abstractions**: Classes should depend on interfaces, not concrete implementations.
3. **Program to interfaces**: Variable types, parameters, and return types should be interfaces when possible.

### When to Use Interfaces

| Use Case | Example |
|----------|---------|
| **Repository pattern** | `MatchRepository`, `PlayerRepository` |
| **Service layer** | `AuthService`, `TokenService` |
| **Strategy pattern** | `EngineSystem`, `EngineCommand` |
| **External integrations** | `NotificationSender`, `PaymentGateway` |
| **Cross-cutting concerns** | `Logger`, `MetricsCollector` |

### Interface Design Guidelines

**Keep interfaces focused (Interface Segregation Principle)**:
```java
// Good: Small, focused interfaces
public interface EntityReader {
    Optional<Entity> findById(int id);
    List<Entity> findAll();
}

public interface EntityWriter {
    Entity save(Entity entity);
    void delete(int id);
}

// Bad: Fat interface
public interface EntityRepository {
    Optional<Entity> findById(int id);
    List<Entity> findAll();
    List<Entity> findByStatus(Status status);
    List<Entity> findByCreatedDateBetween(Date start, Date end);
    Entity save(Entity entity);
    void delete(int id);
    void deleteAll();
    long count();
    boolean existsById(int id);
    // Forces implementers to provide all methods
}
```

**Use default methods sparingly**:
```java
public interface CommandExecutor {
    CommandResult execute(Command command);

    // Default methods for convenience, but keep them simple
    default CommandResult executeAll(List<Command> commands) {
        return commands.stream()
            .map(this::execute)
            .reduce(CommandResult.empty(), CommandResult::merge);
    }
}
```

### Interface Naming Conventions

| Convention | When to Use | Example |
|------------|-------------|---------|
| `*Repository` | Data access | `MatchRepository` |
| `*Service` | Business operations | `AuthService` |
| `*Factory` | Object creation | `ModuleFactory` |
| `*Handler` | Event/message processing | `CommandHandler` |
| `*Provider` | Supplying instances | `TokenProvider` |
| `*Strategy` | Interchangeable algorithms | `ScoringStrategy` |

### Example: Interface-Driven Module Design

The engine module system demonstrates interface-driven design:

```java
// engine-core: Interface definitions
public interface EngineModule {
    String name();
    List<BaseComponent> components();
    List<EngineSystem> systems();
    List<EngineCommand> commands();
}

public interface EngineSystem {
    void tick(EngineContext context);
}

public interface EngineCommand {
    String name();
    CommandResult execute(CommandPayload payload, EngineContext context);
}

// Extension module: Implementation
public class RigidBodyModule implements EngineModule {
    @Override
    public String name() { return "RigidBodyModule"; }

    @Override
    public List<BaseComponent> components() {
        return List.of(new VelocityComponent(), new MassComponent());
    }

    @Override
    public List<EngineSystem> systems() {
        return List.of(new PhysicsSystem(), new DragSystem());
    }

    @Override
    public List<EngineCommand> commands() {
        return List.of(new ApplyForceCommand(), new SetVelocityCommand());
    }
}
```

### Testing with Interfaces

Interfaces make testing straightforward with mocks:

```java
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {
    @Mock
    private MatchRepository repository;  // Interface - easily mocked

    @InjectMocks
    private MatchService service;

    @Test
    void shouldFindMatchById() {
        // Given
        Match expected = new Match(1, "test");
        when(repository.findById(1)).thenReturn(Optional.of(expected));

        // When
        Match result = service.findById(1);

        // Then
        assertThat(result).isEqualTo(expected);
    }
}
```

### The ECS Store Interface Hierarchy

The ECS store demonstrates layered interfaces with the decorator pattern:

```java
// Base read interface
public interface EntityComponentReader {
    float getValue(int entityId, int componentId);
    boolean hasComponent(int entityId, String componentName);
}

// Extended write interface
public interface EntityComponentStore extends EntityComponentReader {
    void setValue(int entityId, int componentId, float value);
    int createEntity();
    void deleteEntity(int entityId);
}

// Decorators implement the same interface, adding behavior
public class PermissionedStore implements EntityComponentStore {
    private final EntityComponentStore delegate;
    private final PermissionChecker permissions;

    @Override
    public void setValue(int entityId, int componentId, float value) {
        permissions.checkWriteAccess(entityId, componentId);
        delegate.setValue(entityId, componentId, value);
    }
}

public class CachingStore implements EntityComponentStore {
    private final EntityComponentStore delegate;
    private final QueryCache cache;

    // Adds caching on top of any store implementation
}
```

This allows composing behavior: `PermissionedStore → CachingStore → LockingStore → ArrayStore`

---

## Testing Requirements

**All changes must include tests.** We use different testing strategies for different layers:

### Unit Tests
For isolated logic testing with mocked dependencies:

```java
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {
    @Mock
    private MatchRepository repository;

    @InjectMocks
    private MatchService service;

    @Test
    void shouldCreateMatch() {
        // Given
        var request = new MatchRequest(/* ... */);

        // When
        var result = service.create(request);

        // Then
        assertThat(result).isNotNull();
        verify(repository).save(any(Match.class));
    }
}
```

### Integration Tests with Testcontainers
For API acceptance tests, use the `api-acceptance-test` module with:
- **Testcontainers** to spin up a Docker container with the backend
- **`EngineClient`** from `web-api-adapter` for API calls (never raw HTTP)
- **Test fixtures** (`TestEngineContainer`, `TestMatch`, `EntitySpawner`) for common operations

```java
@Tag("acceptance")
@Testcontainers
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PhysicsIT {

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("samanthacireland/lightning-engine:0.0.2-SNAPSHOT"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forLogMessage(".*started in.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineClient client;

    @BeforeEach
    void setUp() throws Exception {
        String baseUrl = String.format("http://%s:%d",
            backendContainer.getHost(),
            backendContainer.getMappedPort(8080));

        AuthAdapter auth = new AuthAdapter.HttpAuthAdapter(baseUrl);
        String token = auth.login("admin", "admin").token();

        client = EngineClient.builder()
                .baseUrl(baseUrl)
                .withBearerToken(token)
                .build();
    }

    @Test
    void shouldApplyForceToEntity() throws Exception {
        // Use test fixtures for setup
        TestEngineContainer container = TestEngineContainer.create(client)
                .withModules("EntityModule", "RigidBodyModule")
                .start();

        TestMatch match = container.createMatch()
                .withModules("EntityModule", "RigidBodyModule")
                .build();

        // Spawn entity using fixture helper
        long entityId = EntitySpawner.spawnEntityWithRigidBody(
            client, container.client(), match.id(), 0, 0);

        // Execute command via EngineClient
        container.client().forMatch(match.id()).custom("applyForce")
                .param("entityId", entityId)
                .param("forceX", 100.0f)
                .param("forceY", 0.0f)
                .execute();

        container.tick(10);

        // Verify via snapshot
        var snapshot = container.snapshot(match);
        float posX = snapshot.module("GridMapModule").first("POSITION_X", 0);
        assertThat(posX).isGreaterThan(0);

        container.cleanup();
    }
}
```

**Important**: Always use `EngineClient` and adapters from `web-api-adapter` instead of raw HTTP calls. If an API method doesn't exist in the adapter, add it to the appropriate adapter class first.

### Test Location

| Test Type | Location | Purpose |
|-----------|----------|---------|
| Unit Tests | Same module as source, under `src/test/java` | Isolated logic testing |
| API Acceptance Tests | `lightning-engine/api-acceptance-test` | REST API integration tests with Testcontainers |
| End-to-End Tests | `playwright-test` | Full browser-based testing |

### Test Naming Convention
Use descriptive names that explain the scenario:

```java
@Test
void shouldReturnErrorWhenContainerNotFound() { }

@Test
void shouldRejectCommandFromUnauthorizedUser() { }

@Test
void shouldBroadcastSnapshotAfterEachTick() { }
```

---

## Dependency Injection

We use **Quarkus CDI** (Contexts and Dependency Injection) for dependency management. Follow these guidelines:

### Constructor Injection (Preferred)
```java
@ApplicationScoped
public class MatchService {
    private final MatchRepository repository;
    private final EventBus eventBus;

    // Constructor injection - explicit dependencies
    public MatchService(MatchRepository repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }
}
```

### Field Injection (Avoid)
```java
// Avoid: Hidden dependencies, harder to test
@ApplicationScoped
public class MatchService {
    @Inject
    MatchRepository repository;
}
```

### Scope Annotations

| Annotation | Lifecycle | Use Case |
|------------|-----------|----------|
| `@ApplicationScoped` | Single instance per application | Services, repositories |
| `@RequestScoped` | New instance per HTTP request | Request-specific state |
| `@Dependent` | New instance per injection point | Stateless utilities |
| `@Singleton` | Single instance (eager) | Configuration holders |

### Interface-Based Injection
Always inject interfaces, not implementations:

```java
// Good
public MatchService(MatchRepository repository) { }

// Bad
public MatchService(MongoMatchRepository repository) { }
```

---

## Modular, Decoupled Code

### Module Boundaries
The codebase is organized into clearly separated modules:

```
lightning-engine/
├── engine-core/          # Interfaces and abstractions (no dependencies on impl)
├── engine-internal/      # Implementations (depends on core)
├── engine-adapter/       # Client libraries (depends on core)
└── webservice/           # REST/WebSocket layer (depends on internal)
```

### Coupling Guidelines

1. **Depend on abstractions**: Core modules define interfaces; internal modules provide implementations.

2. **No circular dependencies**: If module A depends on B, then B cannot depend on A.

3. **Minimize public API**: Only expose what's necessary. Use package-private where possible.

4. **Event-driven communication**: Use events for cross-module communication instead of direct calls.

```java
// Good: Event-based decoupling
eventBus.publish(new MatchCreatedEvent(match));

// Bad: Direct coupling
notificationService.sendMatchCreatedEmail(match);
analyticsService.trackMatchCreation(match);
```

### Package Organization
Organize by feature, not by layer:

```
// Good: Feature-based
ca.samanthaireland.engine.match/
├── Match.java
├── MatchService.java
├── MatchRepository.java
└── MatchResource.java

// Bad: Layer-based
ca.samanthaireland.engine/
├── model/Match.java
├── service/MatchService.java
├── repository/MatchRepository.java
└── controller/MatchResource.java
```

---

## Domain Logic

### Keep Domain Pure
Domain objects should contain business logic but no infrastructure concerns:

```java
public class Match {
    private final int id;
    private final List<Player> players;
    private MatchState state;

    // Business logic belongs here
    public void addPlayer(Player player) {
        if (state != MatchState.WAITING) {
            throw new IllegalStateException("Cannot add players to active match");
        }
        if (players.size() >= MAX_PLAYERS) {
            throw new MatchFullException();
        }
        players.add(player);
    }

    // No database calls, HTTP requests, or framework code here
}
```

### Service Layer
Services orchestrate domain objects and infrastructure:

```java
@ApplicationScoped
public class MatchService {
    private final MatchRepository repository;

    public Match addPlayer(int matchId, Player player) {
        Match match = repository.findById(matchId)
            .orElseThrow(() -> new MatchNotFoundException(matchId));

        match.addPlayer(player);  // Domain logic

        return repository.save(match);  // Infrastructure
    }
}
```

### Value Objects
Use immutable value objects for domain concepts:

```java
public record Position(float x, float y) {
    public Position move(float dx, float dy) {
        return new Position(x + dx, y + dy);
    }

    public float distanceTo(Position other) {
        float dx = other.x - x;
        float dy = other.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
```

---

## Entity-Component-System (ECS)

Lightning Engine uses an ECS architecture for game state management. Understanding this pattern is essential for contributing to the engine.

### Core Concepts

| Concept | Description |
|---------|-------------|
| **Entity** | A unique identifier (integer ID). Entities have no data or behavior themselves. |
| **Component** | Pure data attached to entities. Components have no behavior. |
| **System** | Logic that operates on entities with specific component combinations. |

### Component Definition
Components are defined as arrays of float values:

```java
public class PositionComponent extends BaseComponent {
    public static final int X = 0;
    public static final int Y = 1;

    public PositionComponent() {
        super("position", 2);  // name, number of float values
    }
}
```

### System Implementation
Systems run every tick and process entities:

```java
public class MovementSystem implements EngineSystem {
    @Override
    public void tick(EngineContext context) {
        EntityComponentStore store = context.store();

        // Query entities with both position and velocity
        for (int entityId : store.query(hasComponent("position", "velocity"))) {
            float x = store.getValue(entityId, POSITION_X);
            float y = store.getValue(entityId, POSITION_Y);
            float vx = store.getValue(entityId, VELOCITY_X);
            float vy = store.getValue(entityId, VELOCITY_Y);

            store.setValue(entityId, POSITION_X, x + vx * deltaTime);
            store.setValue(entityId, POSITION_Y, y + vy * deltaTime);
        }
    }
}
```

### ECS Best Practices

1. **Components are data-only**: No methods beyond getters/setters.
2. **Systems are stateless**: All state lives in components.
3. **Prefer composition**: Create behavior by combining components, not inheritance.
4. **Query efficiently**: Cache query results when iterating multiple times per tick.

---

## Codebase Overview

### Project Structure

```
lightningfirefly-backend/
├── lightning-engine/
│   ├── engine-core/           # Core interfaces and abstractions
│   ├── engine-internal/       # Implementation of core abstractions
│   ├── engine-adapter/        # Client libraries (SDK, REST adapter)
│   ├── rendering-core/        # GUI framework (OpenGL/NanoVG)
│   ├── webservice/            # Quarkus REST/WebSocket API
│   └── api-acceptance-test/   # API integration tests
│
├── lightning-engine-extensions/
│   ├── modules/               # Hot-reloadable game modules
│   │   ├── entity-module/     # Core entity creation
│   │   ├── rigid-body-module/ # Physics (velocity, force, mass)
│   │   ├── rendering-module/  # Sprite rendering
│   │   ├── health-module/     # HP, damage, healing
│   │   ├── box-collider/      # Collision detection
│   │   └── ...
│   └── game-masters/          # Server-side AI logic
│
├── auth/                      # Identity and access management
├── utils/                     # Shared utilities
└── docs/                      # Documentation
```

### Key Modules Explained

| Module | Purpose |
|--------|---------|
| `engine-core` | Defines interfaces (`ExecutionContainer`, `EntityComponentStore`, `EngineModule`, `EngineCommand`). No implementation details. |
| `engine-internal` | Provides implementations (`InMemoryExecutionContainer`, `ArrayEntityComponentStore`). |
| `webservice` | Quarkus-based REST and WebSocket endpoints. Handles auth, containers, matches, commands. |
| `engine-adapter` | Client libraries: `EngineClient` for REST/WebSocket, `GameRenderer` for rendering. |
| `modules/*` | Pluggable game logic loaded via custom ClassLoader. Each module defines components, systems, and commands. |
| `auth` | JWT generation/validation, BCrypt password hashing, role-based access control. |

### Architectural Layers

```
┌─────────────────────────────────────────┐
│         REST / WebSocket Layer          │  ← Quarkus endpoints
├─────────────────────────────────────────┤
│          Service Layer                  │  ← Business orchestration
├─────────────────────────────────────────┤
│          Domain Layer                   │  ← Pure business logic
├─────────────────────────────────────────┤
│     ECS Store / Container Runtime       │  ← Game state & execution
├─────────────────────────────────────────┤
│        Infrastructure Layer             │  ← Database, external APIs
└─────────────────────────────────────────┘
```

### Key Design Patterns

| Pattern | Usage |
|---------|-------|
| **Decorator** | ECS store layers: Permissions → Locking → Caching → Storage |
| **Factory** | `ModuleFactory`, `CommandBuilder` for object creation |
| **Builder** | `ContainerConfig`, `GameRendererBuilder` for complex objects |
| **Strategy** | `EngineSystem`, `EngineCommand` for pluggable behaviors |
| **Repository** | `MatchRepository`, `PlayerRepository` for data access |

### Tech Stack

- **Language**: Java 25
- **Framework**: Quarkus 3.x
- **Build**: Maven (multi-module)
- **Database**: MongoDB (optional)
- **Auth**: Auth0 JWT, BCrypt
- **Serialization**: Jackson (JSON), Protocol Buffers (binary)
- **Testing**: JUnit 5, Testcontainers, Playwright

---

## Getting Started

1. **Read the docs**: Start with `/docs/architecture.md` for system design.
2. **Run the tests**: `mvn clean test` to verify your setup.
3. **Create a branch**: `git checkout -b feature/your-feature`
4. **Write tests first**: Follow TDD when possible.
5. **Submit a PR**: Include a clear description of changes.

Thank you for contributing!
