# Plan: Force Match ID Attachment for All Entities

## Problem Statement

Currently, modules can create entities in multiple ways:

1. **Via SpawnModule command** - Properly attaches `MATCH_ID` component (correct)
2. **Directly via EntityComponentStore** - No `MATCH_ID` attached (problematic)

Example of problematic code in `MoveModuleFactory.createSpawnSystem()`:

```java
long id = Math.abs(IdGeneratorV2.newId());
store.attachComponents(id, MOVE_COMPONENTS, values);
store.attachComponent(id, MODULE, 1);
// No MATCH_ID attached!
```

This creates entities that lack match isolation, making it impossible to:
- Query entities by match
- Clean up entities when a match ends
- Prevent cross-match entity leakage

---

## Proposed Solution: Entity Factory Pattern

### Core Concept

Introduce an `EntityFactory` interface in `ModuleContext` that all modules must use to create entities. The factory automatically attaches `MATCH_ID` based on the execution context.

### Architecture

```
ModuleContext
    ├── getEntityComponentStore()      // For reads and component updates
    └── getEntityFactory()             // For entity creation (NEW)
            │
            └── EntityFactory
                    ├── createEntity(matchId) → entityId
                    └── createEntity(matchId, components[]) → entityId
```

---

## Implementation Steps

### Phase 1: Define EntityFactory Interface

Create `EntityFactory.java` in `engine-core`:

```java
package com.lightningfirefly.engine.core.entity;

import com.lightningfirefly.engine.core.store.BaseComponent;
import java.util.List;

/**
 * Factory for creating entities with guaranteed match isolation.
 *
 * All entities must be created through this factory to ensure
 * proper MATCH_ID attachment.
 */
public interface EntityFactory {

    /**
     * Create an entity bound to a specific match.
     *
     * @param matchId the match this entity belongs to
     * @return the new entity ID
     */
    long createEntity(long matchId);

    /**
     * Create an entity with initial components.
     *
     * @param matchId the match this entity belongs to
     * @param components initial components to attach
     * @param values component values (must match components size)
     * @return the new entity ID
     */
    long createEntity(long matchId, List<BaseComponent> components, long[] values);

    /**
     * Delete an entity and all its components.
     *
     * @param entityId the entity to delete
     */
    void deleteEntity(long entityId);
}
```

### Phase 2: Implement EntityFactory

Create `DefaultEntityFactory.java` in `engine-internal`:

```java
package com.lightningfirefly.engine.internal.core.entity;

import static com.lightningfirefly.engine.ext.modules.SpawnModuleFactory.MATCH_ID;

public class DefaultEntityFactory implements EntityFactory {
    private final EntityComponentStore store;
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public long createEntity(long matchId) {
        long entityId = nextId.getAndIncrement();
        store.createEntity(entityId);
        store.attachComponent(entityId, MATCH_ID, matchId);
        return entityId;
    }

    @Override
    public long createEntity(long matchId, List<BaseComponent> components, long[] values) {
        long entityId = createEntity(matchId);
        store.attachComponents(entityId, components, values);
        return entityId;
    }

    @Override
    public void deleteEntity(long entityId) {
        store.deleteEntity(entityId);
    }
}
```

### Phase 3: Add to ModuleContext

Update `ModuleContext.java`:

```java
public interface ModuleContext {
    EntityComponentStore getEntityComponentStore();

    /**
     * Get the entity factory for creating match-bound entities.
     *
     * All entity creation should go through this factory to ensure
     * proper MATCH_ID attachment.
     *
     * @return the entity factory
     */
    EntityFactory getEntityFactory();

    // ... existing methods
}
```

### Phase 4: Migrate Modules

**MoveModuleFactory** - Replace direct entity creation:

```java
// BEFORE (problematic)
long id = Math.abs(IdGeneratorV2.newId());
store.attachComponents(id, MOVE_COMPONENTS, values);
store.attachComponent(id, MODULE, 1);

// AFTER (correct)
EntityFactory factory = context.getEntityFactory();
long id = factory.createEntity(matchId, MOVE_COMPONENTS, values);
store.attachComponent(id, MODULE, 1);
```

**Note**: The MoveModule's `CreateMoveableCommand` would need to accept `matchId` in its payload.

### Phase 5: Deprecate Direct Entity Creation

Add compile-time guidance:

```java
public interface EntityComponentStore {
    /**
     * @deprecated Use {@link EntityFactory#createEntity(long)} instead
     *             to ensure proper MATCH_ID attachment.
     */
    @Deprecated
    void createEntity(long id);
}
```

### Phase 6: Add Validation System (Optional Defense-in-Depth)

Create a validation system that runs at tick end:

```java
public class MatchIdValidationSystem implements EngineSystem {
    @Override
    public void tick() {
        Set<Long> allEntities = store.getAllEntities();
        for (long entity : allEntities) {
            if (!store.hasComponent(entity, MATCH_ID)) {
                log.error("Entity {} missing MATCH_ID component!", entity);
                // Option: auto-attach default, throw exception, or delete
            }
        }
    }
}
```

---

## Alternative Approaches Considered

### A. Wrapper Pattern (MatchAwareEntityStore)

Wrap `EntityComponentStore` to intercept all `attachComponent` calls and auto-attach `MATCH_ID`.

**Pros**: Transparent to modules, no code changes needed
**Cons**:
- Context propagation problem: How does the wrapper know which match ID to use?
- Thread-local state is fragile
- Hidden magic makes debugging harder

### B. Store-Level Enforcement

Modify `EntityComponentStore.createEntity()` to require match ID:

```java
void createEntity(long id, long matchId);
```

**Pros**: Compile-time enforcement
**Cons**:
- Breaking API change
- Not all callers have match context

### C. Command-Only Entity Creation

Force all entity creation through commands (like SpawnCommand).

**Pros**: Central control point
**Cons**:
- Async command execution complicates synchronous workflows
- Over-engineered for simple entity creation

---

## Recommended Approach

**Phase 1-4** provide the cleanest solution:
- Clear API with explicit match ID requirement
- Backward compatible (existing code continues to work)
- Gradual migration path with deprecation warnings
- No hidden magic or thread-local state

**Phase 5-6** provide defense-in-depth:
- Deprecation guides developers to correct pattern
- Validation catches any missed cases

---

## Files to Create/Modify

### New Files
1. `engine-core/src/main/java/com/lightningfirefly/engine/core/entity/EntityFactory.java`
2. `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/entity/DefaultEntityFactory.java`
3. `engine-internal/src/test/java/com/lightningfirefly/engine/internal/core/entity/DefaultEntityFactoryTest.java`

### Modified Files
1. `engine-core/src/main/java/com/lightningfirefly/engine/ext/module/ModuleContext.java` - Add `getEntityFactory()`
2. `engine-internal/src/main/java/com/lightningfirefly/engine/internal/ext/module/ModuleContextImpl.java` - Implement `getEntityFactory()`
3. `lightning-engine-extensions/modules/src/main/java/com/lightningfirefly/engine/ext/modules/MoveModuleFactory.java` - Use EntityFactory
4. `engine-core/src/main/java/com/lightningfirefly/engine/core/store/EntityComponentStore.java` - Deprecate `createEntity(long)`

---

## Migration Checklist

- [ ] Define EntityFactory interface
- [ ] Implement DefaultEntityFactory
- [ ] Add EntityFactory to ModuleContext
- [ ] Update MoveModuleFactory to use EntityFactory
- [ ] Update SpawnModuleFactory to use EntityFactory (optional, already correct)
- [ ] Deprecate EntityComponentStore.createEntity()
- [ ] Add validation system (optional)
- [ ] Update tests
- [ ] Update CLAUDE.md documentation

---

## Questions for Discussion

1. **Match ID source**: Should `EntityFactory` get match ID from a "current match" context, or should modules always pass it explicitly?
   - Recommendation: Explicit passing for clarity and testability

2. **Cross-match entities**: Are there legitimate use cases for entities without match ID (e.g., global entities)?
   - If yes: Add `createGlobalEntity()` method
   - If no: Strict enforcement

3. **Timing**: Should the validation system run on every tick, or only in debug/test mode?
   - Recommendation: Every tick in dev/test, disabled in production for performance
