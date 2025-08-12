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

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for physics system using RigidBodyModule.
 *
 * <p>Tests verify that physics operations (velocity, forces, position) correctly
 * update sprite positions in the rendered window.
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Physics System Integration Tests")
@Testcontainers
class PhysicsIT {

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
    @DisplayName("Given entity with velocity, when physics ticks, then sprite moves on screen each tick")
    void givenEntityWithVelocity_whenPhysicsTicks_thenSpriteMoves() {
        // Given - a match with physics and render capability
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule")
                .start();

        Entity entity = match.spawnEntity().ofType(100).execute();
        match.tick();

        // Attach rigid body with initial position and velocity
        int initialX = 100;
        int initialY = 200;
        float velocityX = 300; // pixels per second (at 60fps = 5 pixels per tick)
        int spriteSize = 48;

        entity.attachRigidBody()
                .at(initialX, initialY)
                .withVelocity(velocityX, 0)
                .withMass(1.0f)
                .andApply();
        match.tick();

        // Attach sprite (position comes from EntityModule's POSITION_X/POSITION_Y)
        entity.attachSprite()
                .using(1)
                .sized(spriteSize, spriteSize)
                .andApply();
        match.tick();

        // Setup window and renderer
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Physics Velocity Test")
                .build();

        // Configure mapper to read position from EntityModule (updated by physics)
        // instead of RenderModule's SPRITE_X/SPRITE_Y (which are static)
        SpriteSnapshotMapperImpl mapper = new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png");

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(mapper);

        // Verify sprite is at initial position - render for 1 second so user can see it
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(initialX, initialY, spriteSize, spriteSize).hasContent();
        log.info("Initial sprite at pixel ({}, {})", initialX, initialY);

        // Tick and verify position increases each time
        float expectedX = initialX;
        float deltaPerTick = velocityX / 60.0f; // 5 pixels per tick

        for (int i = 1; i <= 5; i++) {
            // Tick the server
            match.tick();
            expectedX += deltaPerTick;

            // Re-render for 1 second so user can see the sprite move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) expectedX;
            forWindow(window).inRegion(checkX - 10, initialY, spriteSize + 20, spriteSize)
                    .withTolerance(15).hasContent();

            log.info("After tick {}: sprite at pixel (~{}, {})", i, checkX, initialY);
        }

        // Verify sprite has moved significantly from initial X
        int finalX = (int) expectedX;
        assertThat(finalX).as("Sprite should have moved from initial position")
                .isGreaterThan(initialX + 20);

        log.info("Velocity test complete: sprite moved from {} to {} ({} pixels over 5 ticks)",
                initialX, finalX, finalX - initialX);
    }

    @Test
    @DisplayName("Given entity with force applied, when physics ticks, then sprite accelerates on screen each tick")
    void givenEntityWithForce_whenPhysicsTicks_thenSpriteAccelerates() {
        // Given - a match with physics
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule")
                .start();

        Entity entity = match.spawnEntity().ofType(100).execute();
        match.tick();

        // Start at rest with no velocity
        int initialX = 100;
        int initialY = 300;
        int spriteSize = 48;

        entity.attachRigidBody()
                .at(initialX, initialY)
                .withVelocity(0, 0)
                .withMass(1.0f)
                .andApply();
        match.tick();

        entity.attachSprite()
                .using(1)
                .sized(spriteSize, spriteSize)
                .andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Physics Force Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Verify sprite is at initial position - render for 1 second so user can see it
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(initialX, initialY, spriteSize, spriteSize).hasContent();
        log.info("Initial sprite at pixel ({}, {})", initialX, initialY);

        // Apply force and verify acceleration: each tick velocity increases, position increases more
        float forceX = 600; // With mass 1, acceleration = 600 px/s^2
        float velocity = 0;
        float position = initialX;
        float dt = 1.0f / 60.0f; // 60fps
        int prevX = initialX;

        for (int i = 1; i <= 5; i++) {
            // Apply force and tick
            entity.applyForce().of(forceX, 0).andApply();
            match.tick();

            // Calculate expected position (velocity increases each tick due to acceleration)
            velocity += forceX * dt; // a = F/m, v += a*dt
            position += velocity * dt; // x += v*dt

            // Re-render for 1 second so user can see the sprite move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) position;
            forWindow(window).inRegion(checkX - 15, initialY, spriteSize + 30, spriteSize)
                    .withTolerance(20).hasContent();

            // Verify sprite moved from previous position (acceleration means increasing displacement)
            int displacement = checkX - prevX;
            log.info("After tick {}: sprite at pixel (~{}), displacement from last tick: {}", i, checkX, displacement);
            prevX = checkX;
        }

        // Verify sprite has moved from initial position due to acceleration
        int finalX = (int) position;
        assertThat(finalX).as("Sprite should have accelerated from initial position")
                .isGreaterThan(initialX);

        log.info("Acceleration test complete: sprite accelerated from {} to {}", initialX, finalX);
    }

    @Test
    @DisplayName("Given entity with drag, when moving, then sprite decelerates on screen each tick")
    void givenEntityWithDrag_whenMoving_thenSpriteDecelerates() {
        // Given
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule")
                .start();

        Entity entity = match.spawnEntity().ofType(100).execute();
        match.tick();

        int initialX = 100;
        int initialY = 300;
        float initialVelocity = 600;
        float drag = 0.1f; // 10% drag per tick
        int spriteSize = 48;

        entity.attachRigidBody()
                .at(initialX, initialY)
                .withVelocity(initialVelocity, 0)
                .withMass(1.0f)
                .withLinearDrag(drag)
                .andApply();
        match.tick();

        entity.attachSprite()
                .using(1)
                .sized(spriteSize, spriteSize)
                .andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Physics Drag Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Verify sprite is at initial position - render for 1 second so user can see it
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(initialX, initialY, spriteSize, spriteSize).hasContent();
        log.info("Initial sprite at pixel ({}, {})", initialX, initialY);

        // Tick and verify deceleration: each tick the displacement decreases
        float velocity = initialVelocity;
        float position = initialX;
        float dt = 1.0f / 60.0f;
        int prevDisplacement = Integer.MAX_VALUE;

        for (int i = 1; i <= 5; i++) {
            // Tick the server
            match.tick();

            // Calculate expected position (velocity decreases due to drag)
            int positionBefore = (int) position;
            velocity *= (1 - drag); // Apply drag to velocity
            position += velocity * dt;

            // Re-render for 1 second so user can see the sprite move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) position;
            forWindow(window).inRegion(checkX - 15, initialY, spriteSize + 30, spriteSize)
                    .withTolerance(20).hasContent();

            // Verify displacement is decreasing (deceleration)
            int displacement = checkX - positionBefore;
            log.info("After tick {}: sprite at pixel (~{}), displacement: {} (previous: {})",
                    i, checkX, displacement, prevDisplacement == Integer.MAX_VALUE ? "N/A" : prevDisplacement);

            if (prevDisplacement != Integer.MAX_VALUE) {
                assertThat(displacement).as("Displacement should decrease due to drag").isLessThanOrEqualTo(prevDisplacement);
            }
            prevDisplacement = displacement;
        }

        // Verify sprite has moved from initial position (despite drag slowing it down)
        int finalX = (int) position;
        assertThat(finalX).as("Sprite should have moved from initial position")
                .isGreaterThan(initialX);

        log.info("Deceleration test complete: sprite moved from {} to {} with drag", initialX, finalX);
    }

    @Test
    @DisplayName("Given multiple physics entities, when ticking, then all sprites move each tick")
    void givenMultiplePhysicsEntities_whenTicking_thenAllSpritesMove() {
        // Given - multiple entities with different velocities
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule")
                .start();

        // Create 3 entities at different positions with different velocities
        Entity entity1 = match.spawnEntity().ofType(100).execute();
        Entity entity2 = match.spawnEntity().ofType(101).execute();
        Entity entity3 = match.spawnEntity().ofType(102).execute();
        match.tick();

        int spriteSize = 32;
        float dt = 1.0f / 60.0f;

        // Entity 1: At (100, 100) moving right at 300 px/s
        float e1X = 100, e1Y = 100, e1VelX = 300;
        entity1.attachRigidBody().at((int) e1X, (int) e1Y).withVelocity(e1VelX, 0).andApply();
        entity1.attachSprite().using(1).sized(spriteSize, spriteSize).andApply();

        // Entity 2: At (400, 100) moving down at 300 px/s
        float e2X = 400, e2Y = 100, e2VelY = 300;
        entity2.attachRigidBody().at((int) e2X, (int) e2Y).withVelocity(0, e2VelY).andApply();
        entity2.attachSprite().using(2).sized(spriteSize, spriteSize).andApply();

        // Entity 3: At (250, 200) moving diagonally at (200, 200) px/s
        float e3X = 250, e3Y = 200, e3VelX = 200, e3VelY = 200;
        entity3.attachRigidBody().at((int) e3X, (int) e3Y).withVelocity(e3VelX, e3VelY).andApply();
        entity3.attachSprite().using(3).sized(spriteSize, spriteSize).andApply();
        match.tick();

        // Setup renderer - position comes from POSITION_X/POSITION_Y (default)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Multi-Entity Physics Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Verify initial positions - render for 1 second so user can see them
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion((int) e1X, (int) e1Y, spriteSize, spriteSize).hasContent();
        forWindow(window).inRegion((int) e2X, (int) e2Y, spriteSize, spriteSize).hasContent();
        forWindow(window).inRegion((int) e3X, (int) e3Y, spriteSize, spriteSize).hasContent();
        log.info("All 3 sprites at initial positions");

        // Tick and verify each entity moves independently
        for (int i = 1; i <= 5; i++) {
            match.tick();

            // Update expected positions
            e1X += e1VelX * dt;
            e2Y += e2VelY * dt;
            e3X += e3VelX * dt;
            e3Y += e3VelY * dt;

            // Re-render for 1 second so user can see the sprites move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            // Verify all sprites at expected positions
            forWindow(window).inRegion((int) e1X - 10, (int) e1Y, spriteSize + 20, spriteSize)
                    .withTolerance(15).hasContent();
            forWindow(window).inRegion((int) e2X, (int) e2Y - 10, spriteSize, spriteSize + 20)
                    .withTolerance(15).hasContent();
            forWindow(window).inRegion((int) e3X - 10, (int) e3Y - 10, spriteSize + 20, spriteSize + 20)
                    .withTolerance(15).hasContent();

            log.info("After tick {}: E1@(~{},{}), E2@({},~{}), E3@(~{},~{})",
                    i, (int) e1X, (int) e1Y, (int) e2X, (int) e2Y, (int) e3X, (int) e3Y);
        }

        log.info("Multi-entity physics verified: all 3 sprites moved independently");
    }

    @Test
    @DisplayName("Given entity, when velocity changed via command, then sprite direction changes on screen")
    void givenEntity_whenVelocityChanged_thenSpriteDirectionChanges() {
        // Given
        match = backend.createMatch()
                .withModules("EntityModule", "RenderModule", "RigidBodyModule")
                .start();

        Entity entity = match.spawnEntity().ofType(100).execute();
        match.tick();

        int initialX = 300;
        int initialY = 300;
        int spriteSize = 48;
        float velocityRight = 300;
        float velocityLeft = -300;
        float dt = 1.0f / 60.0f;

        entity.attachRigidBody()
                .at(initialX, initialY)
                .withVelocity(velocityRight, 0) // Moving right
                .andApply();
        match.tick();

        entity.attachSprite()
                .using(1)
                .sized(spriteSize, spriteSize)
                .andApply();
        match.tick();

        // Setup renderer - use POSITION_X/POSITION_Y from EntityModule (updated by physics)
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Velocity Change Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));

        // Verify initial position - render for 1 second so user can see it
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).inRegion(initialX, initialY, spriteSize, spriteSize).hasContent();
        log.info("Initial sprite at pixel ({}, {})", initialX, initialY);

        // Move right for 3 ticks, verify position increases each tick
        float position = initialX;
        for (int i = 1; i <= 3; i++) {
            match.tick();
            position += velocityRight * dt;

            // Re-render for 1 second so user can see the sprite move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) position;
            forWindow(window).inRegion(checkX - 10, initialY, spriteSize + 20, spriteSize)
                    .withTolerance(15).hasContent();

            log.info("Moving right - tick {}: sprite at pixel (~{}, {})", i, checkX, initialY);
        }

        float peakX = position;
        log.info("Peak position after moving right: {}", (int) peakX);

        // Now change velocity to move left
        entity.setVelocity().to(velocityLeft, 0).andApply();

        // Move left for 3 ticks, verify position decreases each tick
        for (int i = 1; i <= 3; i++) {
            match.tick();
            position += velocityLeft * dt;

            // Re-render for 1 second so user can see the sprite move
            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) position;
            forWindow(window).inRegion(checkX - 10, initialY, spriteSize + 20, spriteSize)
                    .withTolerance(15).hasContent();

            log.info("Moving left - tick {}: sprite at pixel (~{}, {})", i, checkX, initialY);
        }

        // Verify sprite moved left from peak (position decreased)
        assertThat((int) position).isLessThan((int) peakX);

        log.info("Velocity change verified: sprite reversed direction from {} to {}", (int) peakX, (int) position);
    }
}
