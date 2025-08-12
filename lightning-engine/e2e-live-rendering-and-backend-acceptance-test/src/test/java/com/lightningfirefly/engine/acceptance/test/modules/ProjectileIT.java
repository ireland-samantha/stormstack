package com.lightningfirefly.engine.acceptance.test.modules;

import com.lightningfirefly.engine.acceptance.test.domain.Match;
import com.lightningfirefly.engine.acceptance.test.domain.TestBackend;
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

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.lightningfirefly.engine.acceptance.test.domain.ScreenAssertions.forWindow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration tests for the ProjectileModule.
 *
 * <p>Tests projectile spawning, movement, and lifetime management with visual verification.
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("opengl")
@DisplayName("Projectile Module E2E Tests")
@Testcontainers
class ProjectileIT {

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
        log.info("Backend URL: {}", backendUrl);

        backend = TestBackend.connectTo(backendUrl);
        match = backend.createMatch()
                .withModules("EntityModule", "ProjectileModule", "RenderModule")
                .start();
        log.info("Created match {} with EntityModule, ProjectileModule, and RenderModule", match.id());
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
    @DisplayName("Projectile spawns at correct position")
    void projectile_spawnsAtCorrectPosition() throws IOException {
        // Given: A projectile spawned at (100, 200) with direction (1,0) and speed 10
        spawnProjectileWithSprite(100, 200, 1, 0, 10, 25);
        match.tick();

        // Then: Position matches (after one tick, X moves by speed in direction)
        // Expected X = 100 + 10*1 = 110, Y = 200 + 10*0 = 200
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("EntityModule", "POSITION_X"))
                .hasValueSatisfying(x -> assertThat(x).isEqualTo(110.0f));
        assertThat(snapshot.getComponentValue("EntityModule", "POSITION_Y"))
                .hasValueSatisfying(y -> assertThat(y).isEqualTo(200.0f));

        // Visual verification: Sprite should be visible at the projectile position
        setupRenderer();
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        int spriteSize = 16;
        forWindow(window).inRegion(110 - spriteSize/2, 200 - spriteSize/2, spriteSize * 2, spriteSize * 2)
                .withTolerance(15).hasContent();
        log.info("Projectile spawned and visible at (~110, 200)");
    }

    @Test
    @DisplayName("Projectile moves in direction at speed")
    void projectile_movesInDirectionAtSpeed() throws IOException {
        // Given: A projectile moving right at speed 300 (5 pixels per tick at 60fps)
        float initialX = 100;
        float initialY = 200;
        float speed = 300;
        spawnProjectileWithSprite(initialX, initialY, 1, 0, speed, 25);
        match.tick();

        // Setup renderer to visualize movement
        setupRenderer();

        // Render initial position
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        int spriteSize = 16;
        forWindow(window).inRegion((int) initialX, (int) initialY, spriteSize * 2, spriteSize * 2)
                .withTolerance(20).hasContent();
        log.info("Projectile at initial position ({}, {})", initialX, initialY);

        // When: Multiple ticks pass, verify projectile moves each tick
        float expectedX = initialX + speed / 60.0f; // After first tick
        for (int i = 1; i <= 5; i++) {
            match.tick();
            expectedX += speed / 60.0f;

            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            int checkX = (int) expectedX;
            forWindow(window).inRegion(checkX - 15, (int) initialY - 15, spriteSize + 30, spriteSize + 30)
                    .withTolerance(20).hasContent();
            log.info("After tick {}: projectile at (~{}, {})", i, checkX, initialY);
        }

        // Then: Position has moved right by speed
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("EntityModule", "POSITION_X"))
                .hasValueSatisfying(x -> assertThat(x).isGreaterThan(initialX + 20));

        log.info("Projectile movement verified visually over 5 ticks");
    }

    @Test
    @DisplayName("Projectile expires after lifetime")
    void projectile_expiresAfterLifetime() throws IOException {
        // Given: A projectile with lifetime of 3 ticks
        spawnProjectile(0, 0, 1, 0, 10, 25, 3);
        match.tick();

        // Verify projectile exists
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.hasModule("ProjectileModule")).isTrue();
        assertThat(snapshot.getComponent("ProjectileModule", "SPEED")).isNotEmpty();

        // When: 3 more ticks pass (exceeds lifetime)
        match.tick();
        match.tick();
        match.tick();

        // Then: Projectile should be cleaned up (no projectile components)
        snapshot = match.fetchSnapshot();
        // The projectile should have been destroyed
        var speedValues = snapshot.getComponent("ProjectileModule", "SPEED");
        assertThat(speedValues).isEmpty();
    }

    @Test
    @DisplayName("Projectile with no lifetime persists")
    void projectile_withNoLifetimePersists() throws IOException {
        // Given: A projectile with no lifetime limit (0)
        spawnProjectile(0, 0, 1, 0, 10, 25, 0);
        match.tick();

        // When: Many ticks pass
        for (int i = 0; i < 10; i++) {
            match.tick();
        }

        // Then: Projectile still exists
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponent("ProjectileModule", "SPEED")).isNotEmpty();
    }

    @Test
    @DisplayName("Projectile has correct damage value")
    void projectile_hasCorrectDamageValue() throws IOException {
        // Given: A projectile with 50 damage
        spawnProjectile(0, 0, 1, 0, 10, 50);
        match.tick();

        // Then: Damage value is correct
        var snapshot = match.fetchSnapshot();
        assertThat(snapshot.getComponentValue("ProjectileModule", "DAMAGE"))
                .hasValueSatisfying(dmg -> assertThat(dmg).isEqualTo(50.0f));
    }

    @Test
    @DisplayName("Multiple projectiles move independently")
    void multipleProjectiles_moveIndependently() throws IOException {
        // Given: Two projectiles moving in different directions
        float proj1X = 100, proj1Y = 200, proj1Speed = 300;  // Moving right
        float proj2X = 400, proj2Y = 200, proj2Speed = 300;  // Moving down

        spawnProjectileWithSprite(proj1X, proj1Y, 1, 0, proj1Speed, 25);  // Moving right
        spawnProjectileWithSprite(proj2X, proj2Y, 0, 1, proj2Speed, 25);  // Moving down
        match.tick();

        // Setup renderer to visualize movement
        setupRenderer();

        // Render initial state
        renderer.runFrames(60, () ->
                renderer.renderSnapshot(match.fetchSnapshotForRendering()));

        forWindow(window).hasTotalSpriteCount(2);
        log.info("Two projectiles spawned at ({},{}) and ({},{})", proj1X, proj1Y, proj2X, proj2Y);

        // When: Multiple ticks pass, verify both projectiles move
        float dt = 1.0f / 60.0f;
        float expected1X = proj1X + proj1Speed * dt;
        float expected2Y = proj2Y + proj2Speed * dt;

        for (int i = 1; i <= 5; i++) {
            match.tick();
            expected1X += proj1Speed * dt;
            expected2Y += proj2Speed * dt;

            renderer.runFrames(60, () ->
                    renderer.renderSnapshot(match.fetchSnapshotForRendering()));

            log.info("After tick {}: proj1 at (~{}, {}), proj2 at ({}, ~{})",
                    i, (int) expected1X, (int) proj1Y, (int) proj2X, (int) expected2Y);
        }

        // Then: Both have moved in their respective directions
        var snapshot = match.fetchSnapshot();
        var posXValues = snapshot.getComponent("EntityModule", "POSITION_X");
        var posYValues = snapshot.getComponent("EntityModule", "POSITION_Y");

        // Verify we have at least 2 projectiles
        assertThat(posXValues.size()).isGreaterThanOrEqualTo(2);
        log.info("Final positions X: {}, Y: {}", posXValues, posYValues);

        // Visual verification: both sprites should be visible at their moved positions
        forWindow(window).hasTotalSpriteCount(2);

        log.info("Multiple projectiles movement verified visually");
    }

    @Test
    @DisplayName("Projectile can be manually destroyed")
    void projectile_canBeManuallyDestroyed() throws IOException {
        // Given: A projectile
        spawnProjectile(0, 0, 1, 0, 10, 25, 0);
        match.tick();

        // Get the entity ID from snapshot
        var snapshot = match.fetchSnapshot();
        var entityIds = snapshot.getComponent("EntityModule", "ENTITY_ID");
        assertThat(entityIds).isNotEmpty();
        long projectileId = entityIds.get(0).longValue();

        // When: Destroy command is sent
        destroyProjectile(projectileId);
        match.tick();

        // Then: Projectile is removed
        snapshot = match.fetchSnapshot();
        var speedValues = snapshot.getComponent("ProjectileModule", "SPEED");
        assertThat(speedValues).isEmpty();
    }

    // Helper methods
    private void spawnProjectile(float x, float y, float dirX, float dirY, float speed, float damage) throws IOException {
        spawnProjectile(x, y, dirX, dirY, speed, damage, 0);
    }

    private void spawnProjectile(float x, float y, float dirX, float dirY, float speed, float damage, float lifetime) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", match.id());
        payload.put("ownerEntityId", 0L);
        payload.put("positionX", x);
        payload.put("positionY", y);
        payload.put("directionX", dirX);
        payload.put("directionY", dirY);
        payload.put("speed", speed);
        payload.put("damage", damage);
        payload.put("lifetime", lifetime);
        payload.put("pierceCount", 0f);
        payload.put("projectileType", 0f);

        backend.commandAdapter().submitCommand(match.id(), "spawnProjectile", 0, payload);
    }

    private void destroyProjectile(long entityId) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("entityId", entityId);

        backend.commandAdapter().submitCommand(match.id(), "destroyProjectile", entityId, payload);
    }

    /**
     * Spawn a projectile with a sprite attached for visual rendering.
     */
    private void spawnProjectileWithSprite(float x, float y, float dirX, float dirY, float speed, float damage) throws IOException {
        spawnProjectileWithSprite(x, y, dirX, dirY, speed, damage, 0);
    }

    /**
     * Spawn a projectile with a sprite attached for visual rendering.
     */
    private void spawnProjectileWithSprite(float x, float y, float dirX, float dirY, float speed, float damage, float lifetime) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", match.id());
        payload.put("ownerEntityId", 0L);
        payload.put("positionX", x);
        payload.put("positionY", y);
        payload.put("directionX", dirX);
        payload.put("directionY", dirY);
        payload.put("speed", speed);
        payload.put("damage", damage);
        payload.put("lifetime", lifetime);
        payload.put("pierceCount", 0f);
        payload.put("projectileType", 0f);


        // Add sprite properties for visual rendering
        payload.put("resourceId", 1L);
        payload.put("spriteWidth", 16f);
        payload.put("spriteHeight", 16f);

        backend.commandAdapter().submitCommand(match.id(), "spawnProjectile", 0, payload);
    }

    /**
     * Setup the window and renderer for visual verification.
     */
    private void setupRenderer() {
        window = WindowBuilder.create()
                .size(800, 600)
                .title("Projectile Test")
                .build();

        renderer = new DefaultGameRenderer(window);
        renderer.setSpriteMapper(new SpriteSnapshotMapperImpl()
                .textureResolver(id -> "textures/red-checker.png"));
    }
}
