# Module System

Modules are self-contained units defining:
- **Components** - Float data attached to entities (e.g., `POSITION_X`, `HEALTH`)
- **Systems** - Logic that runs every tick (e.g., movement, collision)
- **Commands** - External API exposed via REST (e.g., `spawn`, `damage`)

Typically, you put backend game logic in modules, and enable them in containers and matches to create instances of your game.

## Creating a Module

### ModuleFactory

```java
public class HealthModuleFactory implements ModuleFactory {
    // Components with unique IDs (float values stored in ECS)
    public static final BaseComponent MAX_HP =
        new BaseComponent(IdGeneratorV2.newId(), "MAX_HP") {};
    public static final BaseComponent CURRENT_HP =
        new BaseComponent(IdGeneratorV2.newId(), "CURRENT_HP") {};

    @Override
    public EngineModule create(ModuleContext context) {
        return new HealthModule(context);
    }
}
```

### EngineModule

```java
public class HealthModule implements EngineModule {
    private final ModuleContext context;

    public HealthModule(ModuleContext context) {
        this.context = context;
    }

    @Override
    public String getName() { return "HealthModule"; }

    @Override
    public List<BaseComponent> createComponents() {
        return List.of(MAX_HP, CURRENT_HP);
    }

    @Override
    public List<EngineSystem> createSystems() {
        // Systems run every tick
        return List.of(() -> {
            var store = context.getEntityComponentStore();
            for (long entity : store.getEntitiesWithComponents(CURRENT_HP)) {
                if (store.getComponent(entity, CURRENT_HP) <= 0) {
                    store.deleteEntity(entity); // Entity dies
                }
            }
        });
    }

    @Override
    public List<EngineCommand> createCommands() {
        return List.of(
            CommandBuilder.newCommand()
                .withName("damage")
                .withSchema(Map.of("entityId", Long.class, "amount", Float.class))
                .withExecution(payload -> {
                    long entityId = ((Number) payload.getPayload().get("entityId")).longValue();
                    float amount = ((Number) payload.getPayload().get("amount")).floatValue();
                    var store = context.getEntityComponentStore();
                    float current = store.getComponent(entityId, CURRENT_HP);
                    store.attachComponent(entityId, CURRENT_HP, current - amount);
                })
                .build()
        );
    }
}
```

## Deploying a Module

```bash
# 1. Add engine-core dependency (provided scope)
# 2. Build JAR
mvn clean package

# 3. Upload to running server
curl -X POST http://localhost:8080/api/modules/upload \
  -F "file=@target/my-module.jar"

# 4. Trigger reload
curl -X POST http://localhost:8080/api/modules/reload
```

## ModuleContext API

| Method | Description |
|--------|-------------|
| `getEntityComponentStore()` | Read/write entity components (float values) |
| `getEntityFactory()` | Create entities with automatic `MATCH_ID` binding |
| `getMatchService()` | Query match metadata and enabled modules |
| `getModuleResolver()` | Discover and access other modules at runtime |

## Key Benefits

- **Hot-Reloadable** - Upload JARs via `POST /api/modules/upload`, call `/api/modules/reload`
- **Per-Match Selection** - Different matches can enable different modules
- **Isolated** - Modules communicate through ECS, not direct imports
- **Permission-Scoped** - Modules can only access components they've registered
- **Exports** - Modules can expose typed APIs to other modules via `ModuleExports`

## Module Exports

Module exports allow inter-module communication through typed interfaces. While permission-scoped components control ECS-level access, exports provide a way for modules to expose service-level APIs to each other.

### Creating an Export Interface

Create a class implementing `ModuleExports`:

```java
public class GridMapExports implements ModuleExports {
    private final PositionService positionService;
    private final MapService mapService;

    public GridMapExports(PositionService positionService, MapService mapService) {
        this.positionService = positionService;
        this.mapService = mapService;
    }

    /**
     * Set an entity's position with bounds validation.
     */
    public void setPositionOnMap(long matchId, long entityId, Position position) {
        mapService.findMapByMatchId(matchId)
            .ifPresent(map -> positionService.setPosition(entityId, map.id(), position));
    }

    /**
     * Get an entity's current position.
     */
    public Optional<Position> getPosition(long entityId) {
        return positionRepository.findByEntityId(entityId);
    }
}
```

### Registering Exports

Return exports from the `getExports()` method in your module:

```java
public class GridMapModule implements EngineModule {
    private final GridMapExports exports;

    public GridMapModule(ModuleContext context) {
        this.exports = new GridMapExports(
            new PositionService(context.getEntityComponentStore()),
            new MapService(context.getEntityComponentStore())
        );
    }

    @Override
    public List<ModuleExports> getExports() {
        return List.of(exports);
    }

    // ... other methods
}
```

### Consuming Exports

Other modules can retrieve exports via the `ModuleContext`:

```java
public class RigidBodyModule implements EngineModule {
    private final ModuleContext context;

    @Override
    public List<EngineSystem> createSystems() {
        return List.of(() -> {
            // Get GridMapModule's exports
            GridMapExports gridMap = context.getModuleExports(GridMapExports.class);
            if (gridMap != null) {
                // Use the exported API
                gridMap.setPosition(entityId, x, y, z);
            }
        });
    }
}
```

### Note

Module exports still need to respect ECS permissions. Exports are authenticated and ran under the calling module's classloader.


## Module Permission Scoping

Modules are isolated from each other at the ECS level. Components can specify permission levels that control access from other modules.

### Permission Levels

| Level | Description |
|-------|-------------|
| `PRIVATE` | Only the owning module can read/write (default) |
| `READ` | Other modules can read, but only owner can write |
| `WRITE` | Any module can read and write |

### Declaring Permission Components

Use `PermissionComponent` instead of `BaseComponent` to specify access levels:

```java
public class HealthModuleFactory implements ModuleFactory {

    // Private - only this module can access
    public static final PermissionComponent INTERNAL_STATE =
        PermissionComponent.create("INTERNAL_STATE", PermissionLevel.PRIVATE);

    // Readable - other modules can read HP but not modify
    public static final PermissionComponent CURRENT_HP =
        PermissionComponent.create("CURRENT_HP", PermissionLevel.READ);

    // Writable - any module can modify (e.g., damage systems)
    public static final PermissionComponent DAMAGE_RECEIVED =
        PermissionComponent.create("DAMAGE_RECEIVED", PermissionLevel.WRITE);

    @Override
    public EngineModule create(ModuleContext context) {
        return new HealthModule(context);
    }
}
```

Components are automatically registered with their permission levels when the module is loaded.

### Permission Enforcement

When a module attempts unauthorized access, an exception is thrown:

```java
// In HealthModule - this works (own component)
store.attachComponent(entity, CURRENT_HP, 100f);

// In another module - reading CURRENT_HP works (READ level)
float hp = store.getComponent(entity, CURRENT_HP);

// In another module - writing CURRENT_HP throws EcsAccessForbiddenException!
store.attachComponent(entity, CURRENT_HP, 50f);
```

### Store Decorator Pattern

The ECS store uses a decorator pattern for layered functionality:

```
ModuleScopedStore             ← Module-specific view with JWT auth
    └── PermissionedEntityComponentStore  ← Permission enforcement
            └── LockingEntityComponentStore   ← Thread safety
                    └── CachedEntityComponentStore    ← Query caching
                            └── ArrayEntityComponentStore     ← Storage
```

### JWT-Based Module Authentication

Modules receive a JWT token that encodes their permissions. This enables stateless permission verification.

**Token Claims:**

| Claim | Description |
|-------|-------------|
| `module_name` | Name of the module |
| `component_permissions` | Map of component keys to permission levels |
| `superuser` | Whether this module bypasses permission checks |

**Permission Key Format:**

```
{ownerModuleName}.{componentName}
```

For example:
- `EntityModule.ENTITY_TYPE.owner` - Module owns this component
- `RigidBodyModule.VELOCITY_X.read` - Module can read VELOCITY_X
- `GridMapModule.POSITION_X.write` - Module can write POSITION_X

**Component Permission Values:**

| Value | Description |
|-------|-------------|
| `OWNER` | Full access - module owns the component |
| `READ` | Read-only access to another module's component |
| `WRITE` | Read and write access to another module's component |

**Superuser Modules:**

Some core modules (like `EntityModule`) have superuser privileges that bypass permission checks entirely. This is necessary for modules that need to manage entities across all other modules.

```java
// Example: EntityModule has superuser privileges
public class EntityModuleFactory implements ModuleFactory {
    @Override
    public boolean isSuperuser() {
        return true; // Bypasses all permission checks
    }
}
```

## Reference Implementations

- **Simple:** `MoveModuleFactory` (223 lines) - Position/velocity, movement system
- **Medium:** `SpawnModuleFactory` (271 lines) - Entity creation, module flag attachment
- **Complex:** `CheckersModuleFactory` (668 lines) - Full game with multi-jump validation

## Maven Structure

Each module is a separate Maven submodule:

```
lightning-engine-extensions/modules/
├── pom.xml (parent)
├── entity-module/
│   ├── pom.xml
│   └── src/main/java/.../EntityModuleFactory.java
├── health-module/
│   ├── pom.xml
│   └── src/main/java/.../HealthModuleFactory.java
└── ...
```

Each module depends only on:
- `engine` (core interfaces)
- `utils` (ID generation, etc.)
- `entity-module` (shared position components)
