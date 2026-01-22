# Testing

## Test Summary

| Module | Tests | Type |
|--------|-------|------|
| `quarkus-web-api` | 188 | REST API, WebSocket, security tests |
| `auth` | 97 | Authentication, authorization, roles |
| `gui` | 179 | Headless component/panel tests |
| `api-acceptance-test` | ~15 | REST API with Testcontainers |
| `gui-acceptance-test` | ~20 | E2E GUI + Docker backend |
| `engine-internal` | ~50 | ECS store, query cache |

31 additional tests require display and are skipped in CI.

## Security Test Coverage

The `EndpointSecurityTest` class provides comprehensive RBAC coverage:

- **Authentication endpoints** - Login, refresh, current user
- **User/Role management** - CRUD operations with admin-only restrictions
- **All REST endpoints** - Verifies role-based access (admin, command_manager, view_only)
- **Unauthenticated access** - Confirms 401 responses

## Running Tests

```bash
# All unit tests (fast, no Docker)
./mvnw test

# Specific module
./mvnw test -pl lightning-engine/gui

# Acceptance tests (requires Docker)
./mvnw verify -pl lightning-engine/api-acceptance-test

# macOS: GLFW requires main thread
JAVA_TOOL_OPTIONS="-XstartOnFirstThread" ./mvnw test
```

## Headless GUI Testing

The `rendering-test` module (3 core classes, ~1000 LOC) provides GPU-free testing:

```java
// Create headless window (no GPU)
HeadlessWindow window = new HeadlessWindow(800, 600);
Button button = new Button(factory, 10, 10, 100, 30, "Save");
window.addComponent(button);

// Connect test driver
GuiDriver driver = GuiDriver.connect(window);

// Find and interact with elements
driver.findElement(By.text("Save")).click();
driver.findElement(By.textContaining("Tick")).click();
driver.findElement(By.type(Button.class).within(By.title("Settings"))).click();

// Simulate input
driver.type("Hello World");
driver.pressKey(KeyCodes.ENTER);

// Wait for async conditions
driver.waitFor().until(ExpectedConditions.elementVisible(By.text("Done")));

// Debug component tree
driver.dumpComponentTree();
```

**Locators:** `By.text()`, `By.textContaining()`, `By.id()`, `By.type()`, `By.title()`, `By.and()`, `By.or()`, `.within()`

## Writing Headless Tests

```java
@Test
void entityInspector_showsSpawnedEntity() {
    // Arrange
    HeadlessWindow window = new HeadlessWindow(800, 600);
    SnapshotPanel panel = new SnapshotPanel(factory, 0, 0, 400, 300);
    window.addComponent(panel);

    GuiDriver driver = GuiDriver.connect(window);

    // Act
    panel.setSnapshotData(createMockSnapshot());
    window.runFrames(2);

    // Assert
    assertThat(driver.hasElement(By.textContaining("Entity 0"))).isTrue();
    assertThat(driver.hasElement(By.text("POSITION_X: 100.0"))).isTrue();
}
```

## E2E Tests with Testcontainers

The `e2e-live-rendering-and-backend-acceptance-test` module provides full-stack tests:

```java
@Testcontainers
class PhysicsIT {
    @Container
    static GenericContainer<?> backend = new GenericContainer<>("samanthacireland/lightning-engine:0.0.2")
        .withExposedPorts(8080);

    @Test
    void entityMovesWithVelocity() {
        var client = EngineClient.connect(backend.getHost() + ":" + backend.getMappedPort(8080));

        var match = client.createMatch()
            .withModule("EntityModule")
            .withModule("RigidBodyModule")
            .execute();

        // Spawn and configure entity
        client.forMatch(match.id())
            .spawn().forPlayer(1).ofType(1).execute();

        client.forMatch(match.id())
            .custom("attachRigidBody")
            .param("entityId", 1)
            .param("positionX", 0)
            .param("positionY", 0)
            .param("velocityX", 10)
            .param("velocityY", 0)
            .execute();

        // Advance 10 ticks
        client.tick(10);

        // Verify position
        var snapshot = client.fetchSnapshot(match.id());
        assertThat(snapshot.module("RigidBodyModule").first("POSITION_X", 0)).isEqualTo(100f);
    }
}
```

## Performance Testing

From `ArrayEntityComponentStorePerformanceTest` (10,000 entities, 60 FPS simulation):

| Operation | Throughput |
|-----------|------------|
| Entity creation | 20M+ ops/sec |
| Component read/write | 4M+ ops/sec |
| Batch operations | 200K+ entities/sec |
| hasComponent checks | 30M+ checks/sec |
| Entity queries | 100-500 queries/sec |

**Bottleneck:** Entity queries require full-scan of entity set. Mitigated by `QueryCache`:
- Caches query results by sorted component ID set
- Invalidated per-component when modified
- Hit/miss ratio tracking for tuning

## Platform Notes

| Platform | Issue | Solution |
|----------|-------|----------|
| **macOS** | GLFW requires main thread | Add `-XstartOnFirstThread` JVM arg |
| **macOS** | Multi-window not supported | Use embedded panels instead |
| **Docker 29** | Testcontainers API version | Use 1.21.3+, set `api.version=1.44` in `~/.docker-java.properties` |
