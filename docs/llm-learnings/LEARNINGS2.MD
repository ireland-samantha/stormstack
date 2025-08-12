# Learnings

Notes and lessons learned during development of the Lightning Engine.

## GUI Module Selection Bug (2025-12-19)

**Issue:** Matches created via `MatchPanel.createMatchWithSelectedModules()` had empty `enabledModuleNames` despite calling `selectModule()` beforehand.

**Root Cause:** The `selectModule(int index)` method correctly populated an internal `selectedModules` list, but `createMatchWithSelectedModules()` was calling `moduleList.getSelectedItems()` (the ListView's UI selection state) instead of using the `selectedModules` list.

**Lesson:** When GUI components have both visual selection state (ListView) and programmatic selection state (internal list), ensure the correct state is used for business logic. Programmatic selection via `setSelectedIndex()` may not update `getSelectedItems()` the same way user clicks do.

**Fix:** Changed `createMatchWithSelectedModules()` to use the internal `selectedModules` list:
```java
// Before (broken):
List<String> moduleNames = moduleList.getSelectedItems();

// After (working):
return matchService.createMatch(new ArrayList<>(selectedModules));
```

## macOS GLFW Thread Requirements

**Issue:** Tests using LWJGL/GLFW fail on macOS with `IllegalStateException: GLFW may only be used on the main thread`.

**Solution:** Add `-XstartOnFirstThread` JVM argument in Maven surefire/failsafe plugin:
```xml
<configuration>
    <argLine>-XstartOnFirstThread --enable-native-access=ALL-UNNAMED</argLine>
</configuration>
```

## Testcontainers Docker Host on macOS

**Issue:** Testcontainers may fail to connect to Docker on macOS.

**Solution:** Set `DOCKER_HOST` environment variable:
```bash
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test ...
```

## Entity ID Generation

**Issue:** Entity IDs are generated globally via `AtomicLong`, so hardcoding `entityId=1L` in tests fails when multiple tests run in sequence.

**Solution:** Always fetch actual entity IDs from snapshots rather than assuming values:
```java
long entityId = getEntityIdFromSnapshot(matchId);
```

## CoreComponents vs Module-Specific Components

**Issue:** SpawnModuleFactory was using its own MATCH_ID component instead of `CoreComponents.MATCH_ID`, causing snapshot filtering by match to fail.

**Solution:** Always use `CoreComponents.MATCH_ID` and `CoreComponents.ENTITY_ID` for entity metadata. These are the components that `SnapshotProviderImpl` filters on.

## Async Module Loading in GUI

**Issue:** When testing GUI panels, async operations like `refreshModules()` may not complete before the test proceeds.

**Solution:** Use polling loops to wait for data to be available:
```java
private void waitForModulesLoaded() throws InterruptedException {
    for (int i = 0; i < 20; i++) {
        Thread.sleep(100);
        app.getMatchPanel().update();
        window.runFrames(2);
        if (!app.getMatchPanel().getAvailableModules().isEmpty()) {
            break;
        }
    }
}
```

## GLFW Multiple Windows on macOS (2025-12-19)

**Issue:** Opening a second GLFW window from a button click fails on macOS with `IllegalStateException: GLFW may only be used on the main thread`. Even with `-XstartOnFirstThread`, you cannot create new windows from background threads.

**Root Cause:** GLFW on macOS requires ALL window creation, destruction, and event handling to happen on the main thread. When the main window is running its event loop, spawning a new window from a button click handler (which runs on the main thread during event processing) or a background thread both fail.

**Solution:** Use embedded panels instead of separate windows. Create a right-side panel (like `TexturePreviewPanel` or `VisualizationPanel`) that shares the main window's OpenGL context:

```java
// Instead of:
VisualizationWindow vizWindow = new VisualizationWindow(matchId, serverUrl);
vizWindow.startAsync();  // FAILS on macOS

// Use an embedded panel:
visualizationPanel.setMatch(matchId);
visualizationPanel.setVisible(true);  // Works everywhere
```

**Lesson:** For cross-platform GUI applications using GLFW, prefer single-window architectures with switchable panels over multi-window approaches.

## Dynamic Grid Layout for Variable Entity Counts

**Issue:** Fixed grid layouts (e.g., `ENTITIES_PER_ROW = 4`, `ENTITY_SIZE = 64`) don't adapt well to varying numbers of entities or different panel sizes.

**Solution:** Calculate layout dynamically based on entity count and available space:
```java
int entitiesPerRow = calculateEntitiesPerRow(entityCount, availableWidth, availableHeight);
int rows = (int) Math.ceil((double) entityCount / entitiesPerRow);

int entitySize = Math.min(MAX_SIZE,
    Math.min(cellWidth - PADDING, cellHeight - LABEL_HEIGHT - PADDING));
entitySize = Math.max(MIN_SIZE, entitySize);
```

This ensures sprites stay within panel bounds and scale appropriately for the content.

## ECS Component Values: Long to Float Migration (2025-12-19)

**Issue:** Component values in the ECS were stored as `long`, but game engines typically need floating-point values for positions, velocities, and other continuous data.

**Migration Scope:**
- `EntityComponentStore` interface: `getComponent()` returns `float`, `attachComponent()` takes `float`
- `ArrayEntityComponentStore`: Internal storage changed from `long[]` to `float[]`
- `Snapshot` record: `Map<String, Map<String, List<Long>>>` → `Map<String, List<Float>>`
- `SnapshotResponse` DTO: Same type change for REST API
- GUI `SnapshotData`: All component lists use `Float` instead of `Long`

**Key Decisions:**

1. **Null Sentinel:** Changed from `Integer.MAX_VALUE` (as long) to `Float.NaN`:
   ```java
   // Before:
   long NULL = Integer.MAX_VALUE;

   // After:
   float NULL = Float.NaN;
   boolean isNull(float value) { return Float.isNaN(value); }
   ```

2. **NaN Comparison Gotcha:** `Float.NaN != Float.NaN` is always `true` per IEEE 754. Cannot use `assertEquals(NULL, value)` in tests:
   ```java
   // WRONG - will always fail:
   assertThat(store.getComponent(1, X)).isEqualTo(EntityComponentStore.NULL);

   // CORRECT:
   assertThat(store.isNull(store.getComponent(1, X))).isTrue();
   // Or:
   assertTrue(Float.isNaN(store.getComponent(1, X)));
   ```

3. **Component IDs vs Component Values:** Entity IDs and component IDs remain `long` (they are identifiers). Only component *values* became `float`:
   ```java
   // Entity ID and component ID are still long:
   void attachComponent(long entityId, long componentId, float value);

   // Batch operations: component IDs are long[], values are float[]:
   void attachComponents(long entityId, long[] componentIds, float[] values);
   ```

4. **JSON Parsing:** Jackson automatically handles `Float` vs `Long` in JSON. The REST API sends numbers without type hints, so ensure client-side parsing uses correct types.

**Files Modified (33 total):**
- Core: `EntityComponentStore`, `ArrayEntityComponentStore`, `Snapshot`, `EntityFactory`
- Internal: `SnapshotProviderImpl`, `CachedEntityComponentStore`, `DefaultEntityFactory`
- GUI: `SnapshotPanel`, `SnapshotData`, `SnapshotWebSocketClient`, `RenderingPanel`, etc.
- Tests: All ECS tests, snapshot tests, GUI panel tests

**Lesson:** When migrating numeric types in a data-heavy system, trace the value through all layers: storage → provider → DTO → serialization → client parsing → display formatting.
