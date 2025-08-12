# Module System

Modules are self-contained units defining:
- **Components** - Float data attached to entities (e.g., `POSITION_X`, `HEALTH`)
- **Systems** - Logic that runs every tick (e.g., movement, collision)
- **Commands** - External API exposed via REST (e.g., `spawn`, `damage`)

## Built-in Modules

All modules are in separate Maven submodules under `lightning-engine-extensions/modules/`:

| Module | Components | Systems | Commands | Description |
|--------|------------|---------|----------|-------------|
| `EntityModule` | 3 | 0 | 1 | Core entity management, shared position (POSITION_X/Y) |
| `HealthModule` | 3 | 1 | 3 | HP tracking, damage/heal, death system |
| `RenderingModule` | 6 | 0 | 1 | Sprite attachment (width, height, rotation, z-index) |
| `RigidBodyModule` | 10 | 1 | 4 | Physics: velocity, force, mass, drag, inertia |
| `BoxColliderModule` | 12 | 2 | 2 | AABB collision detection, handler registration |
| `ProjectileModule` | 5 | 1 | 1 | Projectile spawning and lifetime management |
| `ItemsModule` | 8 | 0 | 4 | Item/inventory: pickup, drop, use, stack |
| `MoveModule` | 7 | 2 | 2 | Legacy movement (deprecated, use RigidBodyModule) |

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
