# Interface Contracts

This document defines all cross-crate interfaces. **Update BEFORE implementation.**

---

## Core Types (stormstack-core)

### Entity Identifiers

```rust
/// Strongly-typed entity identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct EntityId(pub u64);

/// Strongly-typed container identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ContainerId(pub Uuid);

/// Strongly-typed match identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MatchId(pub Uuid);

/// Strongly-typed tenant identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TenantId(pub Uuid);

/// Strongly-typed user identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct UserId(pub Uuid);
```

**Status:** DEFINED
**Owner:** Core

---

### Error Types

```rust
/// Top-level error type for StormStack
#[derive(Debug, thiserror::Error)]
pub enum StormError {
    #[error("Entity not found: {0}")]
    EntityNotFound(EntityId),

    #[error("Container not found: {0}")]
    ContainerNotFound(ContainerId),

    #[error("Match not found: {0}")]
    MatchNotFound(MatchId),

    #[error("Authentication failed: {0}")]
    AuthError(#[from] AuthError),

    #[error("WASM execution failed: {0}")]
    WasmError(#[from] WasmError),

    #[error("Invalid state: {0}")]
    InvalidState(String),

    #[error("Resource exhausted: {0}")]
    ResourceExhausted(String),

    #[error("Internal error: {0}")]
    Internal(#[from] anyhow::Error),
}

pub type Result<T> = std::result::Result<T, StormError>;
```

**Status:** DEFINED
**Owner:** Core

---

## ECS Interface (stormstack-ecs)

### World Management

```rust
/// ECS world wrapper providing safe access to entity-component data
pub trait EcsWorld: Send + Sync {
    /// Create a new entity with the given components
    fn spawn(&mut self, components: impl DynamicBundle) -> EntityId;

    /// Remove an entity and all its components
    fn despawn(&mut self, entity: EntityId) -> Result<()>;

    /// Add a component to an existing entity
    fn insert_component<C: Component>(&mut self, entity: EntityId, component: C) -> Result<()>;

    /// Remove a component from an entity
    fn remove_component<C: Component>(&mut self, entity: EntityId) -> Result<Option<C>>;

    /// Get a reference to a component
    fn get_component<C: Component>(&self, entity: EntityId) -> Result<Option<&C>>;

    /// Get a mutable reference to a component
    fn get_component_mut<C: Component>(&mut self, entity: EntityId) -> Result<Option<&mut C>>;

    /// Query entities with specific components
    fn query<Q: Query>(&self) -> QueryResult<Q>;

    /// Execute a tick, running all registered systems
    fn tick(&mut self, delta_time: f64) -> Result<()>;

    /// Serialize world state to snapshot
    fn snapshot(&self) -> Result<WorldSnapshot>;

    /// Apply delta to world state
    fn apply_delta(&mut self, delta: WorldDelta) -> Result<()>;
}
```

**Status:** DEFINED
**Owner:** ECS Agent
**Consumers:** WASM Agent, Integration Agent

---

### Snapshot Types

```rust
/// Full world state snapshot for initial sync
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorldSnapshot {
    pub tick: u64,
    pub timestamp: i64,
    pub entities: Vec<EntitySnapshot>,
}

/// Single entity snapshot
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EntitySnapshot {
    pub id: EntityId,
    pub components: HashMap<ComponentTypeId, Vec<u8>>,
}

/// Delta update for incremental sync
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorldDelta {
    pub from_tick: u64,
    pub to_tick: u64,
    pub spawned: Vec<EntitySnapshot>,
    pub despawned: Vec<EntityId>,
    pub updated: Vec<ComponentUpdate>,
}

/// Component type identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ComponentTypeId(pub u64);

/// Single component update
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ComponentUpdate {
    pub entity: EntityId,
    pub component_type: ComponentTypeId,
    pub data: Vec<u8>,
}
```

**Status:** DEFINED
**Owner:** ECS Agent
**Consumers:** Networking Agent, WASM Agent

---

## WASM Interface (stormstack-wasm)

### WASM Runtime

```rust
/// WASM module handle
pub struct WasmModule {
    // Internal wasmtime::Module
}

/// WASM instance with execution state
pub struct WasmInstance {
    // Internal wasmtime::Instance + Store
}

/// Resource limits for WASM execution
#[derive(Debug, Clone)]
pub struct WasmResourceLimits {
    /// Maximum fuel (instructions) per call
    pub max_fuel: u64,
    /// Maximum memory in bytes
    pub max_memory_bytes: usize,
    /// Epoch deadline for timeout
    pub epoch_deadline: u64,
}

impl Default for WasmResourceLimits {
    fn default() -> Self {
        Self {
            max_fuel: 1_000_000,
            max_memory_bytes: 16 * 1024 * 1024, // 16MB
            epoch_deadline: 100, // 100 epochs = ~1 second with 10ms epoch
        }
    }
}

/// WASM sandbox for executing untrusted code
pub trait WasmSandbox: Send + Sync {
    /// Load a WASM module from bytes
    fn load_module(&self, wasm_bytes: &[u8]) -> Result<WasmModule, WasmError>;

    /// Create an instance from a module with given resource limits
    fn instantiate(
        &self,
        module: &WasmModule,
        limits: WasmResourceLimits,
    ) -> Result<WasmInstance, WasmError>;

    /// Execute a function in the instance
    fn execute(
        &self,
        instance: &mut WasmInstance,
        func_name: &str,
        args: &[WasmValue],
    ) -> Result<Vec<WasmValue>, WasmError>;

    /// Get remaining fuel in instance
    fn fuel_remaining(&self, instance: &WasmInstance) -> u64;

    /// Get memory usage of instance
    fn memory_usage(&self, instance: &WasmInstance) -> usize;
}
```

**Status:** DEFINED
**Owner:** WASM Agent
**Consumers:** Integration Agent, Module System Agent

---

### WASM Errors

```rust
#[derive(Debug, thiserror::Error)]
pub enum WasmError {
    #[error("Failed to compile module: {0}")]
    CompilationError(String),

    #[error("Failed to instantiate module: {0}")]
    InstantiationError(String),

    #[error("Fuel exhausted after {consumed} fuel units")]
    FuelExhausted { consumed: u64 },

    #[error("Epoch deadline exceeded")]
    EpochDeadlineExceeded,

    #[error("Memory limit exceeded: requested {requested}, limit {limit}")]
    MemoryLimitExceeded { requested: usize, limit: usize },

    #[error("Function not found: {0}")]
    FunctionNotFound(String),

    #[error("Type mismatch: expected {expected}, got {actual}")]
    TypeMismatch { expected: String, actual: String },

    #[error("Trap: {0}")]
    Trap(String),

    #[error("Invalid input from WASM: {0}")]
    InvalidInput(String),
}
```

**Status:** DEFINED
**Owner:** WASM Agent

---

### WASM Host Functions

```rust
/// Provider of host functions to WASM modules
pub trait HostFunctionProvider: Send + Sync {
    /// Register host functions with the linker
    fn register(&self, linker: &mut wasmtime::Linker<WasmState>) -> Result<(), WasmError>;

    /// Name of this provider (for debugging/logging)
    fn name(&self) -> &'static str;
}

/// State available to WASM host functions
pub struct WasmState {
    /// Tenant context
    pub tenant_id: TenantId,
    /// Resource limiter
    pub limiter: StoreLimiter,
    /// ECS world access (if granted)
    pub ecs_access: Option<Arc<RwLock<dyn EcsWorld>>>,
    /// Logging buffer
    pub log_buffer: Vec<String>,
}
```

**Status:** DEFINED
**Owner:** WASM Agent
**Consumers:** Module System Agent

---

## Auth Interface (stormstack-auth)

### Authentication

```rust
/// JWT claims structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    /// Subject (user ID)
    pub sub: UserId,
    /// Tenant ID
    pub tenant_id: TenantId,
    /// Roles
    pub roles: Vec<String>,
    /// Expiration time (Unix timestamp)
    pub exp: i64,
    /// Issued at (Unix timestamp)
    pub iat: i64,
}

/// Authentication service
pub trait AuthService: Send + Sync {
    /// Validate a JWT token and extract claims
    fn validate_token(&self, token: &str) -> Result<Claims, AuthError>;

    /// Generate a new token for a user
    fn generate_token(&self, user: &User, tenant: TenantId) -> Result<String, AuthError>;

    /// Refresh an existing token
    fn refresh_token(&self, token: &str) -> Result<String, AuthError>;

    /// Verify password against stored hash
    fn verify_password(&self, password: &str, hash: &str) -> Result<bool, AuthError>;

    /// Hash a password for storage
    fn hash_password(&self, password: &str) -> Result<String, AuthError>;
}

#[derive(Debug, thiserror::Error)]
pub enum AuthError {
    #[error("Invalid token: {0}")]
    InvalidToken(String),

    #[error("Token expired")]
    TokenExpired,

    #[error("Invalid credentials")]
    InvalidCredentials,

    #[error("Access denied: {0}")]
    AccessDenied(String),

    #[error("User not found: {0}")]
    UserNotFound(UserId),
}
```

**Status:** DEFINED
**Owner:** Auth Agent
**Consumers:** WASM Agent, Networking Agent, Integration Agent

---

### Authorization

```rust
/// Role-based access control
pub trait RbacService: Send + Sync {
    /// Check if user has permission
    fn has_permission(&self, claims: &Claims, permission: Permission) -> bool;

    /// Get all permissions for a role
    fn role_permissions(&self, role: &str) -> Vec<Permission>;
}

/// Permission definition
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum Permission {
    ContainerCreate,
    ContainerRead,
    ContainerDelete,
    MatchCreate,
    MatchRead,
    MatchJoin,
    MatchDelete,
    ModuleUpload,
    ModuleInstall,
    AdminAccess,
    TenantManage,
}
```

**Status:** DEFINED
**Owner:** Auth Agent

---

## Networking Interface (stormstack-net, stormstack-ws)

### WebSocket Messages

```rust
/// Client -> Server messages
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ClientMessage {
    /// Subscribe to match updates
    Subscribe { match_id: MatchId },
    /// Unsubscribe from match updates
    Unsubscribe { match_id: MatchId },
    /// Send command to match
    Command { match_id: MatchId, command: Command },
    /// Ping for keepalive
    Ping { timestamp: i64 },
}

/// Server -> Client messages
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ServerMessage {
    /// Full world snapshot
    Snapshot { match_id: MatchId, snapshot: WorldSnapshot },
    /// Delta update
    Delta { match_id: MatchId, delta: WorldDelta },
    /// Command result
    CommandResult { match_id: MatchId, result: CommandResult },
    /// Error
    Error { code: String, message: String },
    /// Pong response
    Pong { timestamp: i64, server_time: i64 },
}
```

**Status:** DEFINED
**Owner:** Networking Agent
**Consumers:** Integration Agent

---

### Connection Management

```rust
/// WebSocket connection handler
pub trait ConnectionHandler: Send + Sync {
    /// Handle new connection
    fn on_connect(&self, conn_id: ConnectionId, claims: Claims) -> Result<()>;

    /// Handle message from client
    fn on_message(&self, conn_id: ConnectionId, message: ClientMessage) -> Result<()>;

    /// Handle connection close
    fn on_disconnect(&self, conn_id: ConnectionId);

    /// Send message to connection
    fn send(&self, conn_id: ConnectionId, message: ServerMessage) -> Result<()>;

    /// Broadcast to all subscribers of a match
    fn broadcast_to_match(&self, match_id: MatchId, message: ServerMessage) -> Result<()>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ConnectionId(pub u64);
```

**Status:** DEFINED
**Owner:** Networking Agent

---

## Module System Interface (stormstack-modules)

### Native Module Loading

```rust
/// Native module for hot-reload (TRUSTED code only)
pub trait NativeModule: Send + Sync {
    /// Module name
    fn name(&self) -> &str;

    /// Module version
    fn version(&self) -> &str;

    /// Initialize the module
    fn init(&mut self, ctx: &ModuleContext) -> Result<()>;

    /// Shutdown the module
    fn shutdown(&mut self) -> Result<()>;

    /// Register systems with the ECS world
    fn register_systems(&self, world: &mut dyn EcsWorld) -> Result<()>;

    /// Register host functions for WASM
    fn register_host_functions(&self) -> Option<Box<dyn HostFunctionProvider>>;
}

/// Context provided to modules during initialization
pub struct ModuleContext {
    pub tenant_id: TenantId,
    pub config: serde_json::Value,
}

/// Module loader for native hot-reload
pub trait ModuleLoader: Send + Sync {
    /// Load a module from a dynamic library
    fn load(&self, path: &Path) -> Result<Box<dyn NativeModule>>;

    /// Reload a module (unload old, load new)
    fn reload(&self, name: &str, path: &Path) -> Result<Box<dyn NativeModule>>;

    /// Unload a module
    fn unload(&self, name: &str) -> Result<()>;

    /// List loaded modules
    fn list(&self) -> Vec<&dyn NativeModule>;
}
```

**Status:** DEFINED
**Owner:** Module System Agent
**Consumers:** Integration Agent

---

## Server Interface (stormstack-server)

### Container Management

```rust
/// Execution container for isolated game instances
pub trait Container: Send + Sync {
    /// Container identifier
    fn id(&self) -> ContainerId;

    /// Tenant owning this container
    fn tenant_id(&self) -> TenantId;

    /// Create a new match in this container
    fn create_match(&self, config: MatchConfig) -> Result<MatchId>;

    /// Get match by ID
    fn get_match(&self, match_id: MatchId) -> Result<&Match>;

    /// List all matches
    fn list_matches(&self) -> Vec<MatchId>;

    /// Install a WASM module
    fn install_module(&self, module: WasmModule) -> Result<()>;

    /// Execute a tick for all matches
    fn tick(&self) -> Result<()>;

    /// Shutdown the container
    fn shutdown(&self) -> Result<()>;
}

/// Match configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchConfig {
    pub max_players: u32,
    pub tick_rate: f64,
    pub game_mode: String,
    pub custom_config: serde_json::Value,
}
```

**Status:** DEFINED
**Owner:** Integration Agent

---

<!-- Add new interfaces above this line -->

## Interface Change Log

| Date | Interface | Change | Agent |
|------|-----------|--------|-------|
| 2026-02-02 | All | Initial definitions | Architecture |

