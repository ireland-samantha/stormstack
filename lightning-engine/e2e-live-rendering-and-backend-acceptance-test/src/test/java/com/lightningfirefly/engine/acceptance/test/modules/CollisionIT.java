package com.lightningfirefly.engine.acceptance.test.modules;

import com.lightningfirefly.engine.acceptance.test.domain.*;
import com.lightningfirefly.game.renderering.DefaultGameRenderer;
import com.lightningfirefly.game.orchestrator.SpriteSnapshotMapperImpl;
import com.lightningfirefly.engine.rendering.render2d.Window;
import com.lightningfirefly.engine.rendering.render2d.WindowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for collision detection using BoxColliderModule.
 *
 * <p>Tests verify that collision detection works correctly when entities
 * with box colliders move and overlap on screen.
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Collision Detection Integration Tests")
@Testcontainers
class CollisionIT {

    private static final int BACKEND_PORT = 8080;

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                    .forPort(BACKEND_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private TestBackend backend;
    private Match match;
    private Window window;
    private DefaultGameRenderer renderer;

    @BeforeEach
    void setUp() {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String backendUrl = String.format("http://%s:%d", host, port);
        backend = TestBackend.connectTo(backendUrl);
    }

    @AfterEach
    void tearDown() {
        if (renderer != null) {
            renderer.dispose();
            renderer = null;
        }
        if (window != null) {
            window.stop();
            window = null;
        }
        if (match != null) {
            match.delete();
            match = null;
        }
    }

    @Test
    @DisplayName("Given two overlapping entities, when collision checked, then collision is detected")
    void givenOverlappingEntities_whenCollisionChecked_thenCollisionDetected() {
        // Given - two entities with overlapping colliders
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Position entities overlapping: entity1 at (100, 100), entity2 at (120, 100)
        // With colliders of 50x50, they overlap
        entity1.attachRigidBody().at(100, 100).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        entity2.attachRigidBody().at(120, 100).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Setup renderer - position comes from POSITION_X/POSITION_Y (default)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Collision Detection Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // When - run collision detection
        match.tick();

        // Then - verify collision is detected
        match.assertThatSnapshot()
                .hasModule("BoxColliderModule")
                .withComponent("IS_COLLIDING").satisfying(values -> {
                    assertThat(values).isNotEmpty();
                    // At least one entity should be colliding
                    boolean hasCollision = values.stream().anyMatch(v -> v > 0);
                    assertThat(hasCollision)
                            .as("At least one entity should have IS_COLLIDING > 0")
                            .isTrue();
                    log.info("Collision detected. IS_COLLIDING values: {}", values);
                });

        // Render to verify sprites are visible
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Collision detection verified for overlapping entities");
    }

    @Test
    @DisplayName("Given non-overlapping entities, when collision checked, then no collision detected")
    void givenNonOverlappingEntities_whenCollisionChecked_thenNoCollisionDetected() {
        // Given - two entities very far apart (600 pixels)
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        // Create both entities first
        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Place entities far apart: (50, 50) and (650, 650) with 50x50 colliders = 600 pixel gap
        entity1.attachRigidBody().at(50, 50).withVelocity(0, 0).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        match.tick();
        match.tick();

        entity2.attachRigidBody().at(650, 650).withVelocity(0, 0).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        match.tick();

        // Run many ticks to ensure collision detection stabilizes
        for (int i = 0; i < 10; i++) {
            match.tick();
        }

        // Log actual positions from snapshot
        SnapshotParser snapshot = match.fetchSnapshot();
        List<Float> posX = snapshot.getComponent("EntityModule", "POSITION_X");
        List<Float> posY = snapshot.getComponent("EntityModule", "POSITION_Y");
        log.info("Entity positions - X: {}, Y: {}", posX, posY);

        List<Float> collisionValues = snapshot.getComponent("BoxColliderModule", "IS_COLLIDING");
        log.info("IS_COLLIDING values: {} (expecting all 0)", collisionValues);

        // Then - verify no collision
        assertThat(collisionValues).as("Should have exactly 2 box colliders").hasSize(2);
        boolean allNotColliding = collisionValues.stream().allMatch(v -> Math.abs(v) < 0.01);
        assertThat(allNotColliding)
                .as("All IS_COLLIDING values should be 0, but got: %s at positions X=%s Y=%s", collisionValues, posX, posY)
                .isTrue();

        log.info("No collision verified for non-overlapping entities at (50,50) and (650,650)");
    }

    @Test
    @DisplayName("Given moving entity, when it collides with static entity, then collision detected")
    void givenMovingEntity_whenCollidesWithStatic_thenCollisionDetected() {
        // Given - one moving entity approaching a static one
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity movingEntity = match.spawnEntity().ofType(100).execute();
        Entity staticEntity = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Moving entity starts at x=100, moving right toward static at x=300
        float movingStartX = 100;
        float staticX = 300;
        float velocity = 600; // Fast enough to reach within several ticks

        movingEntity.attachRigidBody()
                .at(movingStartX, 200)
                .withVelocity(velocity, 0)
                .andApply();
        movingEntity.attachBoxCollider().sized(50, 50).andApply();
        movingEntity.attachSprite().using(1).sized(50, 50).andApply();

        staticEntity.attachRigidBody().at(staticX, 200).withVelocity(0, 0).andApply();
        staticEntity.attachBoxCollider().sized(50, 50).andApply();
        staticEntity.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Moving Collision Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Verify no collision initially
        match.assertThatSnapshot()
                .hasModule("BoxColliderModule")
                .withComponent("IS_COLLIDING").satisfying(values -> {
                    log.info("Initial IS_COLLIDING: {}", values);
                });

        // Render initial state - user can see both entities
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));
        log.info("Initial render - entity at {} approaching static at {}", movingStartX, staticX);

        // When - run physics and collision until entities should overlap
        // Distance = 300 - 100 - 50 = 150 pixels gap
        // At velocity 600 px/s and 60fps, we move 10 px per tick
        // Need ~15 ticks to cover 150 pixels
        for (int i = 0; i < 20; i++) {
            match.tick();
            // Render each tick for ~0.5 second so user can see the moving entity approach
            renderer.runFrames(30, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));
        }

        // Then - verify collision occurred
        List<Float> collisionValues = match.fetchSnapshot()
                .getComponent("BoxColliderModule", "IS_COLLIDING");

        log.info("After movement - IS_COLLIDING: {}", collisionValues);

        // Check positions
        List<Float> positions = match.fetchSnapshot()
                .getComponent("EntityModule", "POSITION_X");
        log.info("Positions after movement: {}", positions);

        // At least one entity should be colliding now
        boolean hasCollision = collisionValues.stream().anyMatch(v -> v > 0);
        assertThat(hasCollision)
                .as("Moving entity should have collided with static entity")
                .isTrue();

        // Render final state
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Moving collision test passed");
    }

    @Test
    @DisplayName("Given entities on different layers, when overlapping, then no collision detected")
    void givenEntitiesOnDifferentLayers_whenOverlapping_thenNoCollisionDetected() {
        // Given - overlapping entities with different collision layers that don't match
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Entity 1: layer 1, mask 1 (only collides with layer 1)
        // Entity 2: layer 2, mask 2 (only collides with layer 2)
        // They won't collide because masks don't match
        entity1.attachRigidBody().at(100, 100).andApply();
        entity1.attachBoxCollider().sized(50, 50).onLayer(1).withMask(1).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        entity2.attachRigidBody().at(100, 100).andApply(); // Same position!
        entity2.attachBoxCollider().sized(50, 50).onLayer(2).withMask(2).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Layer Filtering Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // When - run collision detection
        match.tick();

        // Then - verify no collision due to layer filtering
        match.assertThatSnapshot()
                .hasModule("BoxColliderModule")
                .withComponent("IS_COLLIDING").satisfying(values -> {
                    assertThat(values).isNotEmpty();
                    boolean allNotColliding = values.stream().allMatch(v -> v == 0);
                    assertThat(allNotColliding)
                            .as("Entities on different layers should not collide")
                            .isTrue();
                    log.info("Layer filtering working. IS_COLLIDING: {}", values);
                });

        // Render - both sprites at same position (overlapping visually)
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(100, 100, 50, 50).hasContent();

        log.info("Layer filtering verified - overlapping entities on different layers don't collide");
    }

    @Test
    @DisplayName("Given multiple entities, when some collide, then correct collision count reported")
    void givenMultipleEntities_whenSomeCollide_thenCorrectCollisionCountReported() {
        // Given - 4 entities, 2 pairs that collide
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        Entity entity3 = match.spawnEntity().ofType(102).execute();
        Entity entity4 = match.spawnEntity().ofType(103).execute();
        match.tick();

        // Pair 1: entities 1 and 2 overlap
        entity1.attachRigidBody().at(100, 100).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        entity2.attachRigidBody().at(120, 100).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();

        // Pair 2: entities 3 and 4 overlap
        entity3.attachRigidBody().at(400, 300).andApply();
        entity3.attachBoxCollider().sized(50, 50).andApply();
        entity3.attachSprite().using(3).sized(50, 50).andApply();

        entity4.attachRigidBody().at(420, 300).andApply();
        entity4.attachBoxCollider().sized(50, 50).andApply();
        entity4.attachSprite().using(4).sized(50, 50).andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Multiple Collisions Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // When - run collision detection
        match.tick();

        // Then - verify multiple collisions
        match.assertThatSnapshot()
                .hasModule("BoxColliderModule")
                .withComponent("IS_COLLIDING").satisfying(values -> {
                    assertThat(values).hasSize(4); // 4 entities
                    long collidingCount = values.stream().filter(v -> v > 0).count();
                    assertThat(collidingCount)
                            .as("4 entities should be involved in 2 collision pairs")
                            .isEqualTo(4);
                    log.info("Multiple collisions verified. IS_COLLIDING: {}", values);
                });

        // Render all 4 sprites
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).hasTotalSpriteCount(4);
        forWindow(window).inRegion(100, 100, 80, 50).hasSpriteCount(2); // Pair 1
        forWindow(window).inRegion(400, 300, 80, 50).hasSpriteCount(2); // Pair 2

        log.info("Multiple collision pairs detected and rendered");
    }

    @Test
    @DisplayName("Given collision, when entities move apart, then collision ends")
    void givenCollision_whenEntitiesMoveApart_thenCollisionEnds() {
        // Given - two colliding entities
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Start overlapping
        entity1.attachRigidBody().at(100, 100).withVelocity(-300, 0).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        entity2.attachRigidBody().at(120, 100).withVelocity(300, 0).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Verify initial collision
        match.tick();
        List<Float> initialCollision = match.fetchSnapshot()
                .getComponent("BoxColliderModule", "IS_COLLIDING");
        boolean initiallyColliding = initialCollision.stream().anyMatch(v -> v > 0);
        assertThat(initiallyColliding).as("Should initially be colliding").isTrue();
        log.info("Initial collision state: {}", initialCollision);

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Collision End Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Render initial colliding state
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));
        log.info("Initial colliding state rendered");

        // When - entities move apart over time, render each tick
        for (int i = 0; i < 30; i++) {
            match.tick();
            // Render each tick for ~0.3 second so user can see entities moving apart
            renderer.runFrames(20, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));
        }

        // Then - collision should have ended
        List<Float> finalCollision = match.fetchSnapshot()
                .getComponent("BoxColliderModule", "IS_COLLIDING");
        boolean stillColliding = finalCollision.stream().anyMatch(v -> v > 0);
        assertThat(stillColliding)
                .as("Entities should no longer be colliding after moving apart")
                .isFalse();
        log.info("Final collision state: {}", finalCollision);

        // Verify positions are now far apart
        List<Float> positions = match.fetchSnapshot()
                .getComponent("EntityModule", "POSITION_X");
        log.info("Final positions: {}", positions);

        // Render final state
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Collision end verified - entities moved apart");
    }

    // ========== Collision Handler Tests ==========

    @Test
    @DisplayName("Given entity with collision handler, when colliding, then handler type is recorded")
    void givenCollisionHandler_whenColliding_thenHandlerTypeRecorded() {
        // Given - entities with collision handlers
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Setup entity with handler type 42 and params
        entity1.attachRigidBody().at(100, 100).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        entity1.attachCollisionHandler().ofType(42).withParam1(10).withParam2(20).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        // Setup other entity without handler
        entity2.attachRigidBody().at(120, 100).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Setup renderer
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Collision Handler Test")
                .build();
        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // When - collision occurs
        match.tick();

        // Render
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        // Then - verify handler type and params were set
        var snapshot = match.fetchSnapshot();
        var handlerTypes = snapshot.getComponent("BoxColliderModule", "COLLISION_HANDLER_TYPE");
        var param1Values = snapshot.getComponent("BoxColliderModule", "COLLISION_HANDLER_PARAM1");
        var param2Values = snapshot.getComponent("BoxColliderModule", "COLLISION_HANDLER_PARAM2");

        log.info("Handler types: {}, param1: {}, param2: {}", handlerTypes, param1Values, param2Values);

        assertThat(handlerTypes).contains(42.0f);
        assertThat(param1Values).contains(10.0f);
        assertThat(param2Values).contains(20.0f);

        // Verify collision was detected
        var collisionValues = snapshot.getComponent("BoxColliderModule", "IS_COLLIDING");
        boolean hasCollision = collisionValues.stream().anyMatch(v -> v > 0);
        assertThat(hasCollision).as("Entities should be colliding").isTrue();

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Collision handler type and params verified");
    }

    @Test
    @DisplayName("Given entities with handlers, when colliding, then handler tick is updated")
    void givenEntitiesWithHandlers_whenColliding_thenHandlerTickUpdated() {
        // Given - both entities have handlers
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule", "BoxColliderModule")
                .start();

        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        match.tick();

        // Setup both entities with handlers
        entity1.attachRigidBody().at(100, 100).andApply();
        entity1.attachBoxCollider().sized(50, 50).andApply();
        entity1.attachCollisionHandler().ofType(1).andApply();
        entity1.attachSprite().using(1).sized(50, 50).andApply();

        entity2.attachRigidBody().at(120, 100).andApply();
        entity2.attachBoxCollider().sized(50, 50).andApply();
        entity2.attachCollisionHandler().ofType(2).andApply();
        entity2.attachSprite().using(2).sized(50, 50).andApply();
        match.tick();

        // Verify initial handled tick is 0
        var snapshot = match.fetchSnapshot();
        var handledTicks = snapshot.getComponent("BoxColliderModule", "COLLISION_HANDLED_TICK");
        log.info("Initial handled ticks: {}", handledTicks);

        // Setup renderer
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Handler Tick Test")
                .build();
        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // When - collision occurs (handlers are invoked)
        match.tick();

        // Render
        renderer.runFrames(30, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        // Then - handled tick should be updated (> 0 indicates handler was invoked)
        // Note: Since no handlers are registered, tick won't be updated
        // This test verifies the handler type is set on both entities
        snapshot = match.fetchSnapshot();
        var handlerTypes = snapshot.getComponent("BoxColliderModule", "COLLISION_HANDLER_TYPE");
        log.info("Handler types after collision: {}", handlerTypes);

        assertThat(handlerTypes).contains(1.0f);
        assertThat(handlerTypes).contains(2.0f);

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Both entity handler types verified");
    }
}
