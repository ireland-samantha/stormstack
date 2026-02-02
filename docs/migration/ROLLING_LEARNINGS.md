# Rolling Learnings

Cross-cutting insights discovered during the Java to Rust migration.

---

## Reflection Pattern Replacements

### Java Annotation Discovery → Rust inventory Crate

**Java Pattern:**
```java
Reflections r = new Reflections("com.stormstack");
Set<Class<?>> modules = r.getTypesAnnotatedWith(GameModule.class);
```

**Rust Equivalent:**
```rust
// In module definition file
use inventory;

pub struct ModuleDescriptor {
    pub name: &'static str,
    pub version: &'static str,
    pub init: fn() -> Box<dyn NativeModule>,
}

inventory::collect!(ModuleDescriptor);

// In each module
inventory::submit! {
    ModuleDescriptor {
        name: "my_module",
        version: "1.0.0",
        init: || Box::new(MyModule::new()),
    }
}

// At runtime
fn discover_modules() -> Vec<&'static ModuleDescriptor> {
    inventory::iter::<ModuleDescriptor>.collect()
}
```

**Notes:**
- inventory works at link time, not runtime
- Modules must be linked into the binary to be discovered
- For dynamic loading, use libloading + known entry point

---

## Mocking Strategies

### Java Mockito → Rust Trait Objects

**Java Pattern:**
```java
@Mock
WasmRuntime mockRuntime;

when(mockRuntime.execute(any())).thenReturn(result);
```

**Rust Equivalent:**
```rust
// Define trait for the interface
trait WasmRuntime: Send + Sync {
    fn execute(&mut self, module: &Module, func: &str) -> Result<Value, WasmError>;
}

// Production implementation
struct RealWasmRuntime { engine: Engine }

impl WasmRuntime for RealWasmRuntime {
    fn execute(&mut self, module: &Module, func: &str) -> Result<Value, WasmError> {
        // Real implementation
    }
}

// Test mock
struct MockWasmRuntime {
    execute_results: VecDeque<Result<Value, WasmError>>,
    calls: Vec<(String, String)>,
}

impl WasmRuntime for MockWasmRuntime {
    fn execute(&mut self, module: &Module, func: &str) -> Result<Value, WasmError> {
        self.calls.push((module.name().to_string(), func.to_string()));
        self.execute_results.pop_front().unwrap_or(Ok(Value::I32(0)))
    }
}
```

**Notes:**
- mockall crate can auto-generate mocks from traits
- Must design for testability upfront
- Every external dependency should have a trait

---

## Dependency Injection Patterns

### Java CDI @Inject → Rust Constructor Injection

**Java Pattern:**
```java
@Inject
public ContainerService(WasmRuntime runtime, AuthService auth) {
    this.runtime = runtime;
    this.auth = auth;
}
```

**Rust Equivalent:**
```rust
pub struct ContainerService<W: WasmRuntime, A: AuthService> {
    runtime: W,
    auth: A,
}

impl<W: WasmRuntime, A: AuthService> ContainerService<W, A> {
    pub fn new(runtime: W, auth: A) -> Self {
        Self { runtime, auth }
    }
}

// Or with trait objects for runtime polymorphism
pub struct ContainerService {
    runtime: Arc<dyn WasmRuntime>,
    auth: Arc<dyn AuthService>,
}
```

**Notes:**
- Generics = compile-time polymorphism (faster, but bigger binary)
- Trait objects = runtime polymorphism (smaller binary, slight overhead)
- Use trait objects at service boundaries, generics for internal code

---

## Error Handling

### Java Exceptions → Rust Result<T, E>

**Java Pattern:**
```java
try {
    container.execute(command);
} catch (ContainerNotFoundException e) {
    return Response.status(404).build();
} catch (InvalidStateException e) {
    return Response.status(400).entity(e.getMessage()).build();
}
```

**Rust Equivalent:**
```rust
match container.execute(command) {
    Ok(result) => Json(result).into_response(),
    Err(StormError::ContainerNotFound(_)) => StatusCode::NOT_FOUND.into_response(),
    Err(StormError::InvalidState(msg)) => {
        (StatusCode::BAD_REQUEST, msg).into_response()
    }
    Err(e) => {
        tracing::error!(?e, "Internal error");
        StatusCode::INTERNAL_SERVER_ERROR.into_response()
    }
}
```

**Notes:**
- thiserror for defining error types with derive
- anyhow for quick prototyping or where specific errors don't matter
- Define error types per crate, convert at boundaries

---

## Async Patterns

### Java CompletableFuture → Rust async/await

**Java Pattern:**
```java
CompletableFuture.supplyAsync(() -> {
    return container.execute(command);
}).thenApply(result -> {
    broadcast(result);
    return result;
});
```

**Rust Equivalent:**
```rust
async fn execute_and_broadcast(container: &Container, command: Command) -> Result<Value> {
    let result = container.execute(command).await?;
    broadcast(&result).await?;
    Ok(result)
}

// Or with spawn for true parallelism
tokio::spawn(async move {
    let result = container.execute(command).await?;
    broadcast(&result).await?;
    Ok::<_, StormError>(result)
});
```

**Notes:**
- tokio::spawn for fire-and-forget tasks
- JoinHandle for awaiting spawned tasks
- tokio::select! for racing futures
- Careful with Send + Sync bounds across await points

---

## Resource Management

### Java try-with-resources → Rust Drop

**Java Pattern:**
```java
try (var container = containerPool.acquire()) {
    container.execute(command);
} // auto-close
```

**Rust Equivalent:**
```rust
// Drop trait handles cleanup automatically
{
    let container = container_pool.acquire()?;
    container.execute(command)?;
} // container.drop() called here

// Or explicitly with guards
struct ContainerGuard<'a> {
    container: Container,
    pool: &'a ContainerPool,
}

impl Drop for ContainerGuard<'_> {
    fn drop(&mut self) {
        self.pool.release(std::mem::take(&mut self.container));
    }
}
```

**Notes:**
- Drop is always called, even on panic (unless abort)
- Use ManuallyDrop for explicit control
- Consider using parking_lot for better synchronization primitives

---

## Serialization

### Java Jackson → Rust serde

**Java Pattern:**
```java
@JsonProperty("match_id")
private MatchId matchId;

ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(match);
```

**Rust Equivalent:**
```rust
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Match {
    #[serde(rename = "match_id")]
    pub match_id: MatchId,
}

let json = serde_json::to_string(&match_)?;
let match_: Match = serde_json::from_str(&json)?;
```

**Notes:**
- serde_json for JSON
- bincode for binary (faster, smaller)
- rmp-serde for MessagePack
- Use #[serde(skip)] to exclude fields

---

## Concurrency

### Java synchronized → Rust Mutex/RwLock

**Java Pattern:**
```java
synchronized(container) {
    container.tick();
}
```

**Rust Equivalent:**
```rust
// std::sync for blocking
let container = container.lock().unwrap();
container.tick();

// tokio::sync for async
let mut container = container.write().await;
container.tick().await;
```

**Notes:**
- parking_lot::Mutex is faster than std
- tokio::sync::Mutex for async contexts
- RwLock for read-heavy workloads
- Avoid holding locks across await points

---

## Thread Pools

### Java ExecutorService → Rust rayon / tokio

**Java Pattern:**
```java
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> container.tick());
```

**Rust Equivalent:**
```rust
// CPU-bound: rayon
rayon::spawn(|| {
    container.tick();
});

// IO-bound: tokio
tokio::spawn(async move {
    container.tick().await
});

// Scoped threads
std::thread::scope(|s| {
    s.spawn(|| container.tick());
});
```

**Notes:**
- rayon for CPU-bound parallel iteration
- tokio for async I/O
- std::thread::scope for scoped threads (no 'static requirement)

---

## Java Architecture Insights (Phase 1 Analysis)

### ECS Store Architecture

**Java Pattern (ArrayEntityComponentStore):**
```java
// Backing storage: float[][] - entity ID × component ID
// NULL sentinel: Float.NaN for missing values
// Entity index tracks which entities exist
```

**Rust Approach:**
```rust
// Legion uses archetype-based storage (different model)
// For custom storage, use:
// - HashMap<EntityId, HashMap<ComponentTypeId, Component>>
// - Or sparse arrays with Option<T>
// NaN sentinel not needed - Rust's Option is idiomatic
```

**Key Insight:**
Java uses columnar float arrays for memory efficiency. Legion's archetype storage provides similar benefits through different means.

---

### ClassLoader Isolation → WASM Isolation

**Java Pattern:**
```java
// ContainerClassLoader with hybrid delegation:
// - PARENT_FIRST for engine APIs
// - CHILD_FIRST for game modules
// Enables per-container code isolation
```

**Rust WASM Approach:**
```rust
// Each container gets separate wasmtime Store
// Engine shared across containers (compiled code cache)
// Store isolation = ClassLoader isolation
// Host functions = shared engine APIs
```

**Key Insight:**
ClassLoader's parent-first packages map to WASM host functions. Game module code runs in isolated WASM instances.

---

### Columnar Snapshot Format

**Java Pattern:**
```java
// Snapshot structure:
// List<ModuleData> → List<ComponentData> → List<Float>
// Columnar: one list of values per component type
// Delta: Map<module> → Map<component> → Map<entityId> → value
```

**Rust Approach:**
```rust
// Keep columnar format for wire protocol compatibility
// WorldSnapshot { entities: Vec<EntitySnapshot> } for API
// Internal: Can use either format depending on use case
// Delta: Same map-based structure
```

---

### ExecutionContainer Fluent API

**Java Pattern:**
```java
// Container uses multiple operation interfaces:
// - ContainerModuleOperations
// - ContainerLifecycleOperations
// - ContainerTickOperations
// etc.
// Fluent API style
```

**Rust Approach:**
```rust
// Use builder pattern or method chaining
// Group operations in impl blocks by concern
// Consider trait-based composition for extension
impl Container {
    // Lifecycle operations
    pub fn start(&mut self) -> Result<()> { ... }
    pub fn stop(&mut self) -> Result<()> { ... }

    // Tick operations
    pub fn advance(&mut self, dt: f64) -> Result<()> { ... }

    // Match operations
    pub fn create_match(&mut self, config: MatchConfig) -> Result<MatchId> { ... }
}
```

---

### Command Queue Pattern

**Java Pattern:**
```java
// InMemoryCommandQueueManager:
// - Per-match command queues
// - Tick-based scheduling (future ticks)
// - Batch execution (limit 10k/tick)
```

**Rust Approach:**
```rust
pub struct CommandQueue {
    pending: BTreeMap<u64, Vec<Command>>,  // tick -> commands
}

impl CommandQueue {
    pub fn add(&mut self, tick: u64, cmd: Command) { ... }
    pub fn execute(&mut self, current_tick: u64, limit: usize) -> Vec<CommandResult> { ... }
}
```

---

### Scope-Based Authorization

**Java Pattern:**
```java
// Scopes: "service.resource.operation"
// e.g., "engine.container.create"
// User → Roles → Scopes (hierarchical)
```

**Rust Approach:**
```rust
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum Permission {
    ContainerCreate,
    ContainerRead,
    // ...
}

// Or string-based for flexibility
pub fn has_scope(claims: &Claims, scope: &str) -> bool {
    claims.scopes.iter().any(|s| scope_matches(s, scope))
}
```

---

### Module System Design

**Java EngineModule Interface:**
```java
interface EngineModule {
    createSystems() → List<EngineSystem>
    createCommands() → List<EngineCommand>
    createComponents() → List<BaseComponent>
    getName() → String
    getVersion() → ModuleVersion
}
```

**Rust WASM Module Interface:**
```rust
// WASM modules export functions, not interfaces
// Use naming conventions or component model:
pub trait WasmGameModule {
    fn on_init(&mut self, ctx: &mut WasmContext);
    fn on_tick(&mut self, ctx: &mut WasmContext, dt: f64);
    fn on_command(&mut self, ctx: &mut WasmContext, cmd: &Command);
}
// Host provides entity/component access via imported functions
```

---

### WebSocket Streaming Pattern

**Java Pattern:**
```java
// Snapshot streaming at configurable interval (default 100ms)
// Delta streaming: first full, then changes only
// Per-connection state tracking
// Auth via WebSocketAuthResultStore
```

**Rust Approach:**
```rust
// axum::extract::ws for WebSocket handling
// tokio::sync::broadcast for match updates
// Per-connection state in handler task
// Auth via tower middleware
```

---

<!-- Add new learnings above this line -->

