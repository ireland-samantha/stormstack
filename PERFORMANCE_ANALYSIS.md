# Performance Analysis Report

## Executive Summary

This document identifies performance anti-patterns, N+1 query issues, unnecessary re-renders, and inefficient algorithms in the Lightning Engine codebase. The findings are categorized by severity and include specific file locations with line numbers.

---

## Critical Issues

### 1. N+1 Query Pattern in Snapshot Generation

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/snapshot/SnapshotProviderImpl.java`

**Lines:** 52-58, 69-100

**Issue:** The `createForMatch()` method exhibits a classic N+1 query pattern with nested iterations that cause redundant entity lookups.

```java
// Lines 52-58: Multiple queries for flag components
for (BaseComponent flag : getFlagComponents()) {
    Set<Long> found = entityComponentStore.getEntitiesWithComponents(List.of(flag, CoreComponents.MATCH_ID));
    if (found != null && !found.isEmpty()) {
        entities.addAll(entityComponentStore.getEntitiesWithComponents(flag, CoreComponents.MATCH_ID));  // DUPLICATE CALL!
    }
}

// Lines 69-100: Triple-nested loop with individual component checks
for (ModuleComponentMapping mapping : mappings) {           // O(modules)
    for (Long entityId : entities) {                        // O(entities)
        // hasComponent and getComponent called separately
        if (entityComponentStore.hasComponent(entityId, CoreComponents.MATCH_ID) &&     // Lock acquire
                entityComponentStore.getComponent(entityId, CoreComponents.MATCH_ID)    // Lock acquire
        ...
    }
    for (BaseComponent component : mapping.components()) {  // O(components)
        for (Long entityId : entities) {                    // O(entities) again!
            if (entityComponentStore.hasComponent(entityId, component) && ...
```

**Complexity:** O(modules × components × entities) with lock contention on each call

**Impact:** For 100 entities, 20 components, and 8 modules = **16,000+ lock acquisitions** per snapshot

**Recommendations:**
1. Use batch `getComponents()` instead of individual `hasComponent()` + `getComponent()` calls
2. Combine flag component queries into a single union query
3. Pre-filter entities by match ID once, not in every inner loop
4. Consider columnar iteration (component-major) instead of entity-major

---

### 2. Full Snapshot Broadcasting Without Delta Compression

**File:** `webservice/quarkus-web-api/src/main/java/com/lightningfirefly/engine/quarkus/api/websocket/SnapshotWebSocket.java`

**Lines:** 34-40

**Issue:** Full snapshots are broadcast every 100ms regardless of whether data changed.

```java
@OnOpen
public Multi<SnapshotResponse> onOpen(@PathParam String matchId) {
    return Multi.createFrom().ticks().every(Duration.ofMillis(broadcastIntervalMs))
            .map(tick -> createSnapshotResponse(id));  // Full snapshot every tick
}
```

**Impact:**
- Unnecessary network bandwidth consumption
- Client-side processing of unchanged data
- CPU cycles wasted regenerating identical snapshots

**Recommendations:**
1. Implement change detection - only broadcast when ECS data changes
2. Add delta compression - send only changed component values
3. Use dirty flags in `EntityComponentStore` to track modifications

---

### 3. Lock Contention in CachedEntityComponentStore

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/store/CachedEntityComponentStore.java`

**Lines:** 216-244, 190-200

**Issue:** The double-checked locking pattern upgrades from read lock to write lock on cache misses, causing contention.

```java
// Lines 216-244: Lock upgrade pattern
public Set<Long> getEntitiesWithComponents(long... componentIds) {
    lock.readLock().lock();
    try {
        Set<Long> cached = cache.get(componentIds);
        if (cached != null) return cached;
    } finally {
        lock.readLock().unlock();
    }

    // Cache miss - must acquire write lock (blocks all readers!)
    lock.writeLock().lock();
    try {
        // Double-check...
        Set<Long> result = delegate.getEntitiesWithComponents(componentIds);
        cache.put(result, componentIds);
        return result;
    } finally {
        lock.writeLock().unlock();
    }
}

// Lines 190-200: Individual cache invalidations
public void attachComponents(long id, long[] componentIds, float[] values) {
    lock.writeLock().lock();
    try {
        delegate.attachComponents(id, componentIds, values);
        for (long componentId : componentIds) {
            cache.invalidateComponent(componentId);  // Called N times!
        }
    }
}
```

**Impact:** Under concurrent load, cache misses block all readers. Multiple invalidation calls compound the overhead.

**Recommendations:**
1. Batch cache invalidations - collect component IDs first, invalidate once
2. Consider `StampedLock` for optimistic reads
3. Use lock-free cache with `ConcurrentHashMap.computeIfAbsent()`

---

## High Priority Issues

### 4. Inefficient Entity Query (O(n) Full Scan)

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/store/ArrayEntityComponentStore.java`

**Lines:** 218-226

**Issue:** `findEntitiesWithAllComponents()` performs a full scan of all entities.

```java
private Set<Long> findEntitiesWithAllComponents(long... componentIds) {
    Set<Long> result = new LongArraySet();
    for (long entityId : index.entityIds()) {  // O(n) - iterates ALL entities
        if (entityHasAllComponents(entityId, componentIds)) {
            result.add(entityId);
        }
    }
    return result;
}
```

**Impact:** Query time grows linearly with entity count. 10,000 entities = 10,000 iterations per query.

**Recommendations:**
1. Maintain component-to-entity indices (inverted index)
2. Use bitmask-based archetype matching for O(1) lookups
3. The `QueryCache` helps but only for repeated identical queries

---

### 5. Memory Allocations in Hot Paths

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/snapshot/SnapshotProviderImpl.java`

**Lines:** 73, 86

**Issue:** New `ArrayList` allocations in inner loops during snapshot generation.

```java
// Line 73: Allocated per module
List<Float> entityIds = new ArrayList<>();

// Line 86: Allocated per component
List<Float> values = new ArrayList<>();
```

**Impact:** For 8 modules × 20 components = 160+ ArrayList allocations per snapshot (10 snapshots/second = 1,600 allocations/second)

**Recommendations:**
1. Pre-allocate lists with known capacity: `new ArrayList<>(entities.size())`
2. Use object pools for frequently created lists
3. Consider reusing buffer arrays across snapshots

---

### 6. QueryCache Defensive Copies

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/core/store/QueryCache.java`

**Lines:** 134-136, 150-152

**Issue:** Cache get and put operations create defensive copies.

```java
// Line 134-136: Copy on get (cache hit)
if (entry != null) {
    hits.incrementAndGet();
    return new LongOpenHashSet(entry.entityIds);  // Full copy!
}

// Line 150-152: Copy on put
public void put(Set<Long> result, long... componentIds) {
    LongSet copy = new LongOpenHashSet(result);  // Another copy
    cache.put(key, new CacheEntry(copy, version.get()));
}
```

**Impact:** Two full set copies per cache operation. For 1000 entities, that's 2000 long copies.

**Recommendations:**
1. Return immutable views instead of copies: `Collections.unmodifiableSet()`
2. Use copy-on-write semantics
3. Consider if callers actually modify returned sets (they likely don't)

---

## Medium Priority Issues

### 7. Tree Metrics Recalculated Every Frame

**File:** `rendering-core/src/main/java/com/lightningfirefly/engine/rendering/render2d/impl/opengl/GLTreeView.java`

**Lines:** 183, 307-323

**Issue:** `calculateTotalHeight()` traverses the entire tree on every frame render.

```java
// Line 183: Called every render()
float totalHeight = calculateTotalHeight();

// Lines 307-323: Recursive calculation
private float calculateTotalHeight() {
    float total = 0;
    for (TreeNode node : rootNodes) {
        total += calculateNodeHeight(node);  // Recursive tree traversal
    }
    return total;
}
```

**Impact:** O(nodes) computation every frame (60 FPS = 60 tree traversals/second)

**Recommendations:**
1. Cache the total height, invalidate only when tree structure changes
2. Use a dirty flag pattern: `if (heightDirty) { cachedHeight = calculateTotalHeight(); }`

---

### 8. Sprite List Copies and Stream Operations

**File:** `rendering-core/src/main/java/com/lightningfirefly/engine/rendering/render2d/impl/opengl/GLWindow.java`

**Lines:** 142-152, 452-453, 480-481, 499-500

**Issue:** Multiple sprite list operations create unnecessary copies and use inefficient patterns.

```java
// Line 143: Returns defensive copy every call
public List<Sprite> getSprites() {
    return new ArrayList<>(sprites);  // Copy entire list
}

// Line 147-152: Stream for single lookup
public Sprite getSprite(int spriteId) {
    return sprites.stream()
            .filter(s -> s.getId() == spriteId)
            .findFirst()
            .orElse(null);
}

// Lines 452, 480, 499: List copy + sort on every mouse event
List<Sprite> sortedSprites = new ArrayList<>(sprites);
sortedSprites.sort((a, b) -> Integer.compare(b.getZIndex(), a.getZIndex()));
```

**Impact:** Every mouse move triggers list copy + sort. At 60 FPS cursor tracking = 60 sorts/second.

**Recommendations:**
1. Maintain a `Map<Integer, Sprite>` for O(1) ID lookups
2. Keep sprites pre-sorted by z-index (sort on add/remove only)
3. Use insertion sort or maintain sorted order incrementally

---

### 9. Redundant Match List Retrieval

**File:** `engine-internal/src/main/java/com/lightningfirefly/engine/internal/ext/gamemaster/GameMasterTickService.java`

**Lines:** 41-42

**Issue:** `getAllMatches()` is called unconditionally every tick.

```java
public void onTick(long tick) {
    List<Match> matches = matchService.getAllMatches();  // Called every tick
    for (Match match : matches) {
        ...
    }
}
```

**Impact:** If `getAllMatches()` has any overhead (copy, query), it's incurred every tick (60+ times/second)

**Recommendations:**
1. Cache match list, invalidate on match create/delete
2. Use observer pattern - only iterate when matches change
3. The implementation already caches game masters per match, extend to match list

---

### 10. Parent Finding Algorithm in Tree

**File:** `gui/src/main/java/com/lightningfirefly/engine/gui/panel/SnapshotPanel.java`

**Lines:** 420-436

**Issue:** Finding a node's parent requires full tree traversal from root.

```java
private TreeNode findParent(TreeNode node) {
    for (TreeNode root : entityTree.getRootNodes()) {
        TreeNode parent = findParentRecursive(root, node);  // O(n) search
        if (parent != null) return parent;
    }
    return null;
}
```

**Impact:** O(n) traversal for each parent lookup. Multiple lookups per selection event.

**Recommendations:**
1. Store parent reference in `TreeNode` (make it bidirectional)
2. Use a `Map<TreeNode, TreeNode>` for parent lookups
3. Pass parent context through selection handler

---

## Low Priority / Optimization Opportunities

### 11. String Concatenation in Hot Path

**File:** `gui/src/main/java/com/lightningfirefly/engine/gui/panel/SnapshotPanel.java`

**Lines:** 158, 511-512

**Issue:** String concatenation in frequently called update methods.

```java
// Line 158
statusLabel.setText("Loaded " + snapshotList.size() + " match(es)");

// Line 511
String tickText = "Tick: " + latestSnapshot.tick();
```

**Impact:** Minor - creates intermediate String objects. Only significant at high frequency.

**Recommendations:**
1. Use `String.format()` or `StringBuilder` for complex formatting
2. Cache formatted strings when values don't change

---

### 12. WebSocket Client ObjectMapper Instance

**File:** `gui/src/main/java/com/lightningfirefly/engine/gui/service/SnapshotWebSocketClient.java`

**Line:** 60

**Issue:** Each client creates its own `ObjectMapper` instance.

```java
this.objectMapper = new ObjectMapper();
```

**Impact:** `ObjectMapper` is expensive to create. With multiple match connections, this adds up.

**Recommendations:**
1. Share a static `ObjectMapper` instance (it's thread-safe)
2. Use a factory pattern to provide shared resources

---

## Performance Characteristics Summary

| Operation | Current Complexity | Optimal | Location |
|-----------|-------------------|---------|----------|
| Snapshot Generation | O(modules × components × entities) | O(entities × components) | SnapshotProviderImpl:69-100 |
| Entity Query | O(entities) full scan | O(1) with indices | ArrayEntityComponentStore:218-226 |
| Cache Hit | O(entities) copy | O(1) view | QueryCache:134-136 |
| Tree Height | O(nodes) per frame | O(1) cached | GLTreeView:183 |
| Sprite Lookup | O(sprites) stream | O(1) map | GLWindow:147-152 |
| Parent Find | O(nodes) traversal | O(1) reference | SnapshotPanel:420-436 |

---

## Recommended Prioritization

1. **Immediate (Critical):** Fix N+1 queries in SnapshotProviderImpl - highest impact
2. **Short-term (High):** Add delta compression to WebSocket snapshots
3. **Short-term (High):** Optimize lock patterns in CachedEntityComponentStore
4. **Medium-term:** Add component-to-entity indices for O(1) queries
5. **Medium-term:** Cache tree metrics in rendering code
6. **As-needed:** Address memory allocations and string operations

---

## Testing Recommendations

1. Add performance benchmarks using JMH for critical paths
2. Profile with async-profiler during typical game scenarios
3. Monitor garbage collection pressure during gameplay
4. Load test WebSocket connections with multiple concurrent matches

---

*Generated: 2026-01-02*
*Analyzed by: Claude Code Performance Analyzer*
