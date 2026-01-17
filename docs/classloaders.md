# ClassLoader Isolation

Lightning Engine uses custom classloaders to provide complete runtime isolation between containers.

## Container Isolation Model

Each container is a self-contained runtime environment:

```
Container 1                          Container 2
├── ContainerClassLoader             ├── ContainerClassLoader
├── EntityComponentStore             ├── EntityComponentStore
├── ModuleManager                    ├── ModuleManager
├── MatchService                     ├── MatchService
├── GameLoop                         ├── GameLoop
└── CommandQueue                     └── CommandQueue
```

## ContainerClassLoader

Located at: `engine-internal/.../container/ContainerClassLoader.java`

### Hybrid Delegation Strategy

The classloader uses a hybrid parent-first/child-first strategy:

```java
protected Class<?> loadClass(String name, boolean resolve) {
    // Parent-first for engine APIs and JDK classes
    if (shouldLoadFromParent(name)) {
        return getParent().loadClass(name);  // Shared APIs
    }

    // Child-first for module classes
    try {
        return findClass(name);  // Container-local modules
    } catch (ClassNotFoundException e) {
        return getParent().loadClass(name);  // Fallback to parent
    }
}
```

### Parent-First Packages

These packages are always loaded from the parent (shared across all containers):

| Package | Reason |
|---------|--------|
| `ca.samanthaireland.engine.core` | Engine API interfaces |
| `ca.samanthaireland.engine.ext.module` | Module SPI |
| `ca.samanthaireland.engine.ext.ai` | AI SPI |
| `java.`, `javax.`, `jakarta.` | JDK classes |
| `org.slf4j`, `ch.qos.logback` | Logging |
| `lombok` | Compile-time annotations |

### Child-First (Module Classes)

All other classes (module implementations) are loaded from the container's own classloader first. This means:

- Container 1 can have `EntityModule` v1.0
- Container 2 can have `EntityModule` v2.0
- Both run simultaneously without conflict

## Request Flow

When a request hits `/api/containers/1/matches/5`:

```
HTTP Request: GET /api/containers/1/matches/5
                    │
                    ▼
┌──────────────────────────────────────────────────┐
│ ContainerResource                                │
│                                                  │
│ 1. getContainerOrThrow(containerId=1)            │
│         │                                        │
│         ▼                                        │
│    ContainerManager.getContainer(1)              │
│    (ConcurrentHashMap lookup)                    │
│         │                                        │
│         ▼                                        │
│    Returns ExecutionContainer for ID 1           │
│         │                                        │
│         ▼                                        │
│ 2. container.matches().get(5)                    │
│    (Uses container 1's MatchService)             │
│         │                                        │
│         ▼                                        │
│    Returns Match from container 1's store        │
└──────────────────────────────────────────────────┘
```

The container ID in the URL path determines which container handles the request. All subsequent operations use that container's isolated components.

## Container Initialization

When a container starts (`InMemoryExecutionContainer.startInternal()`):

```java
private void initializeComponents() {
    // 1. Create isolated classloader
    containerClassLoader = new ContainerClassLoader(id, getClass().getClassLoader());

    // 2. Create isolated ECS store
    entityStore = new ArrayEntityComponentStore(ecsProperties);

    // 3. Create module manager with container's classloader
    moduleManager = new OnDiskModuleManager(
            injector,
            permissionRegistry,
            modulePath.toString(),
            containerClassLoader  // <-- Uses container's classloader
    );

    // 4. Create other isolated components...
}
```

## Module Loading

The `OnDiskModuleManager` loads module JARs using the container's classloader:

```java
// For each module JAR file:
URL jarUrl = jarFile.toURI().toURL();
URLClassLoader classLoader = new URLClassLoader(
    new URL[]{jarUrl},
    parentClassLoader  // ContainerClassLoader
);

// Load ModuleFactory class from JAR
Class<?> clazz = classLoader.loadClass(className);
ModuleFactory factory = (ModuleFactory) clazz.newInstance();
```

This ensures module classes are isolated per-container while sharing the engine API interfaces.

## Key Files

| File | Purpose |
|------|---------|
| `ContainerClassLoader.java` | Custom URLClassLoader with hybrid delegation |
| `InMemoryExecutionContainer.java` | Container implementation, creates classloader on start |
| `InMemoryContainerManager.java` | Registry of containers (ConcurrentHashMap by ID) |
| `OnDiskModuleManager.java` | Loads module JARs using container's classloader |
| `ContainerResource.java` | REST endpoints, routes requests to containers by ID |

## Why Not Thread-Local or Request-Scoped?

The isolation is **structural** (per-container) not **per-request** because:

1. **Simplicity**: No context switching needed on each request
2. **Performance**: No ThreadLocal overhead
3. **Correctness**: A container's state is consistent across all requests to it
4. **Lifecycle**: Classloader lifecycle matches container lifecycle (start/stop)

The URL path (`/api/containers/{id}/...`) explicitly selects which isolated environment handles each request.
