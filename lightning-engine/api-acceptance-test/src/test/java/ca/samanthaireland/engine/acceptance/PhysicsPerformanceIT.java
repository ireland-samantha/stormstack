/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.engine.acceptance;

import ca.samanthaireland.engine.api.resource.adapter.ContainerAdapter;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient;
import ca.samanthaireland.engine.api.resource.adapter.EngineClient.ContainerClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Physics performance and functionality acceptance test.
 *
 * <p>This test verifies:
 * <ul>
 *   <li>All RigidBodyModule commands work correctly</li>
 *   <li>Physics simulation performs well with 10k+ entities</li>
 *   <li>Entities move correctly with velocity and forces</li>
 *   <li>Sprites are properly attached to physics entities</li>
 * </ul>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker must be running</li>
 *   <li>The backend image must be built: {@code docker build -t lightning-backend .}</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * ./mvnw verify -pl lightning-engine/api-acceptance-test -Pacceptance-tests
 * </pre>
 */
@Slf4j
@Tag("acceptance")
@Tag("testcontainers")
@Tag("performance")
@DisplayName("Physics Performance Acceptance Test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class PhysicsPerformanceIT {

    private static final int BACKEND_PORT = 8080;
    private static final List<String> REQUIRED_MODULES = List.of(
            "EntityModule", "GridMapModule", "RigidBodyModule", "RenderModule"
    );

    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("lightning-backend:latest"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forLogMessage(".*started in.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private EngineClient client;
    private ContainerClient container;
    private long containerId = -1;
    private long matchId = -1;
    private long resourceId = -1;
    private final List<Long> createdEntityIds = new ArrayList<>();
    private final Random random = new Random(42); // Deterministic for reproducibility

    @BeforeEach
    void setUp() throws Exception {
        String host = backendContainer.getHost();
        Integer port = backendContainer.getMappedPort(BACKEND_PORT);
        String baseUrl = String.format("http://%s:%d", host, port);
        log.info("Backend URL: {}", baseUrl);

        // Authenticate to get JWT token (default admin user)
        String token = authenticate(baseUrl, "admin", "admin");
        log.info("Authenticated successfully");

        client = EngineClient.builder()
                .baseUrl(baseUrl)
                .withBearerToken(token)
                .build();
    }

    /**
     * Authenticate with the backend and return a JWT token.
     */
    private String authenticate(String baseUrl, String username, String password) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Authentication failed: " + response.statusCode() + " - " + response.body());
        }

        // Parse token from response (expecting {"token": "..."})
        String body = response.body();
        int tokenStart = body.indexOf("\"token\":\"") + 9;
        int tokenEnd = body.indexOf("\"", tokenStart);
        return body.substring(tokenStart, tokenEnd);
    }

    @AfterEach
    void tearDown() {
        // Clean up container (which cleans up matches within it)
        if (containerId > 0) {
            try {
                client.containers().stopContainer(containerId);
                client.containers().deleteContainer(containerId);
            } catch (Exception e) {
                log.warn("Failed to clean up container {}: {}", containerId, e.getMessage());
            }
        }
        if (resourceId > 0 && container != null) {
            try {
                container.deleteResource(resourceId);
            } catch (Exception e) {
                log.warn("Failed to delete resource {}: {}", resourceId, e.getMessage());
            }
        }
        createdEntityIds.clear();
        containerId = -1;
        matchId = -1;
        container = null;
    }

    // ========== RigidBodyModule Command Tests ==========

    @Test
    @Order(1)
    @DisplayName("attachRigidBody - creates rigid body with all properties")
    void testAttachRigidBody() throws Exception {
        setupMatchAndResource();

        // Spawn entity
        long entityId = spawnEntity();

        // Attach rigid body with all properties
        container.forMatch(matchId).send("attachRigidBody", Map.ofEntries(
                Map.entry("entityId", entityId),
                Map.entry("positionX", 100.0f),
                Map.entry("positionY", 200.0f),
                Map.entry("positionZ", 0.0f),
                Map.entry("velocityX", 5.0f),
                Map.entry("velocityY", 10.0f),
                Map.entry("velocityZ", 0.0f),
                Map.entry("mass", 2.0f),
                Map.entry("linearDrag", 0.1f),
                Map.entry("angularDrag", 0.05f),
                Map.entry("inertia", 1.5f)
        ));

        container.tick();

        // Verify via snapshot
        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        assertThat(rigidBody.has("VELOCITY_X")).isTrue();
        assertThat(rigidBody.has("MASS")).isTrue();
        assertThat(rigidBody.has("LINEAR_DRAG")).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("setVelocity - directly sets entity velocity")
    void testSetVelocity() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Set velocity
        container.forMatch(matchId).custom("setVelocity")
                .param("entityId", entityId)
                .param("velocityX", 50.0f)
                .param("velocityY", -30.0f)
                .param("velocityZ", 0.0f)
                .execute();

        container.tick();

        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        // Velocity should be applied (may have some drag applied)
        float vx = rigidBody.first("VELOCITY_X", 0);
        float vy = rigidBody.first("VELOCITY_Y", 0);

        assertThat(Math.abs(vx)).isGreaterThan(40); // Allow for drag
        assertThat(Math.abs(vy)).isGreaterThan(25);

        log.info("setVelocity test passed: vx={}, vy={}", vx, vy);
    }

    @Test
    @Order(3)
    @DisplayName("applyForce - accumulates force for physics integration")
    void testApplyForce() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Apply force (F = ma, so with mass=1, acceleration = force)
        container.forMatch(matchId).custom("applyForce")
                .param("entityId", entityId)
                .param("forceX", 100.0f)
                .param("forceY", 50.0f)
                .param("forceZ", 0.0f)
                .execute();

        // Multiple ticks to see accumulated effect
        for (int i = 0; i < 5; i++) {
            container.tick();
        }

        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        // Position should have changed due to force
        float px = rigidBody.first("POSITION_X", 0);
        float py = rigidBody.first("POSITION_Y", 0);

        assertThat(px).isGreaterThan(0);
        assertThat(py).isGreaterThan(0);

        log.info("applyForce test passed: px={}, py={}", px, py);
    }

    @Test
    @Order(4)
    @DisplayName("applyImpulse - instant velocity change based on mass")
    void testApplyImpulse() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Set a specific mass for predictable impulse effect
        container.forMatch(matchId).custom("attachRigidBody")
                .param("entityId", entityId)
                .param("positionX", 0.0f)
                .param("positionY", 0.0f)
                .param("mass", 2.0f)
                .execute();
        container.tick();

        // Apply impulse (deltaV = impulse / mass = 100 / 2 = 50)
        container.forMatch(matchId).custom("applyImpulse")
                .param("entityId", entityId)
                .param("impulseX", 100.0f)
                .param("impulseY", 0.0f)
                .param("impulseZ", 0.0f)
                .execute();

        container.tick();

        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        float vx = rigidBody.first("VELOCITY_X", 0);
        // With mass=2, impulse=100, expect velocity around 50 (minus any drag)
        assertThat(vx).isGreaterThan(40);

        log.info("applyImpulse test passed: vx={}", vx);
    }

    @Test
    @Order(5)
    @DisplayName("setPosition - teleports entity to new position")
    void testSetPosition() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Teleport to new position
        container.forMatch(matchId).custom("setPosition")
                .param("entityId", entityId)
                .param("positionX", 500.0f)
                .param("positionY", 300.0f)
                .param("positionZ", 0.0f)
                .execute();

        container.tick();

        var snapshot = getSnapshot();
        var gridMap = snapshot.module("GridMapModule");

        float px = gridMap.first("POSITION_X", 0);
        float py = gridMap.first("POSITION_Y", 0);

        assertThat(px).isEqualTo(500.0f);
        assertThat(py).isEqualTo(300.0f);

        log.info("setPosition test passed: px={}, py={}", px, py);
    }

    @Test
    @Order(6)
    @DisplayName("applyTorque - rotates entity")
    void testApplyTorque() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Apply torque for rotation
        container.forMatch(matchId).custom("applyTorque")
                .param("entityId", entityId)
                .param("torque", 50.0f)
                .execute();

        // Multiple ticks to see rotation
        for (int i = 0; i < 10; i++) {
            container.tick();
        }

        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        float rotation = rigidBody.first("ROTATION", 0);
        float angularVelocity = rigidBody.first("ANGULAR_VELOCITY", 0);

        assertThat(rotation).isNotEqualTo(0);
        assertThat(angularVelocity).isGreaterThan(0);

        log.info("applyTorque test passed: rotation={}, angularVelocity={}", rotation, angularVelocity);
    }

    @Test
    @Order(7)
    @DisplayName("deleteRigidBody - removes rigid body components")
    void testDeleteRigidBody() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(100, 100);
        container.tick();

        // Verify entity exists
        var snapshotBefore = getSnapshot();
        int countBefore = countEntitiesInModule(snapshotBefore, "RigidBodyModule");
        assertThat(countBefore).isEqualTo(1);

        // Delete rigid body
        container.forMatch(matchId).custom("deleteRigidBody")
                .param("entityId", entityId)
                .execute();

        container.tick();
        container.tick(); // Extra tick for cleanup system

        var snapshotAfter = getSnapshot();
        int countAfter = countEntitiesInModule(snapshotAfter, "RigidBodyModule");

        assertThat(countAfter).isEqualTo(0);

        log.info("deleteRigidBody test passed");
    }

    // ========== Performance Benchmarks ==========

    @Test
    @Order(10)
    @DisplayName("Benchmark: Spawn 10,000 entities with rigid bodies")
    void benchmarkSpawn10kEntitiesAutoadvance() throws Exception {
        setupMatchAndResource();

        int entityCount = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        log.info("Starting benchmark: spawning {} entities with rigid bodies and sprites (parallel)", entityCount);

        // ========== PHASE 1: Parallel spawn ==========
        long startTime = System.currentTimeMillis();
        parallelSpawn(entityCount).using(executor).execute();
        long spawnTime = System.currentTimeMillis() - startTime;
        log.info("Spawn phase completed in {}ms ({} entities/sec)", spawnTime, entityCount * 1000L / spawnTime);

        // Get entity IDs for attachment phase
        List<Float> entityIds = getSnapshot().entityIds();
        log.info("Got {} entity IDs for attachment", entityIds.size());

        // ========== PHASE 2: Parallel rigid body + sprite attachment ==========
        startTime = System.currentTimeMillis();

        // Pre-compute random values to avoid contention
        float[] posX = new float[entityIds.size()];
        float[] posY = new float[entityIds.size()];
        float[] velX = new float[entityIds.size()];
        float[] velY = new float[entityIds.size()];
        for (int i = 0; i < entityIds.size(); i++) {
            posX[i] = (i % 100) * 10;
            posY[i] = (i / 100) * 10;
            velX[i] = random.nextFloat() * 20 - 10;
            velY[i] = random.nextFloat() * 20 - 10;
        }

        // Fire ALL rigid body + sprite attachments in parallel
        List<CompletableFuture<Void>> attachFutures = new ArrayList<>(entityIds.size() * 2);

        parallelRigidBody(entityIds)
                .positions(posX, posY)
                .velocities(velX, velY)
                .linearDrag(0.01f)
                .using(executor)
                .addTo(attachFutures)
                .execute();

        parallelSprite(entityIds)
                .resource(resourceId)
                .size(32, 32)
                .using(executor)
                .addTo(attachFutures)
                .execute();

        CompletableFuture.allOf(attachFutures.toArray(new CompletableFuture[0])).join();
        long attachSendTime = System.currentTimeMillis() - startTime;
        log.info("All {} attachment commands (rigid body + sprite) sent in {}ms",
                attachFutures.size(), attachSendTime);

        // Poll until attachments are done
        pollUntilModuleCount("RigidBodyModule", entityCount);
        pollUntilModuleCount("RenderModule", entityCount);

        long attachTime = System.currentTimeMillis() - startTime;
        log.info("Attach phase completed in {}ms ({} entities/sec)",
                attachTime, entityCount * 1000L / Math.max(attachTime, 1));

        // Final verification
        EngineClient.Snapshot snapshot = getSnapshot();
        int rigidBodyCount = countEntitiesInModule(snapshot, "RigidBodyModule");
        int renderingCount = countEntitiesInModule(snapshot, "RenderModule");

        log.info("Final state: {} rigid bodies, {} sprites", rigidBodyCount, renderingCount);
        assertThat(rigidBodyCount).isGreaterThanOrEqualTo(entityCount);
        assertThat(renderingCount).isGreaterThanOrEqualTo(entityCount);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        log.info("Benchmark PASSED: {} entities with rigid bodies and sprites created (parallel)", entityCount);
    }

    @Test
    @Order(11)
    @DisplayName("Benchmark: Physics simulation tick rate with 10k entities")
    void benchmarkPhysicsTickRate() throws Exception {
        setupMatchAndResource();

        int entityCount = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        log.info("Setting up {} entities for tick rate benchmark (parallel)", entityCount);

        // ========== PHASE 1: Parallel spawn ==========
        long startTime = System.currentTimeMillis();
        parallelSpawn(entityCount).using(executor).execute();
        log.info("Spawn phase completed in {}ms", System.currentTimeMillis() - startTime);

        // Get entity IDs
        List<Float> entityIds = getSnapshot().entityIds();

        // ========== PHASE 2: Parallel rigid body attachment ==========
        startTime = System.currentTimeMillis();

        // Pre-compute random values
        float[] posX = new float[entityIds.size()];
        float[] posY = new float[entityIds.size()];
        float[] velX = new float[entityIds.size()];
        float[] velY = new float[entityIds.size()];
        for (int i = 0; i < entityIds.size(); i++) {
            posX[i] = (i % 100) * 10;
            posY[i] = (i / 100) * 10;
            velX[i] = random.nextFloat() * 100 - 50;
            velY[i] = random.nextFloat() * 100 - 50;
        }

        parallelRigidBody(entityIds)
                .positions(posX, posY)
                .velocities(velX, velY)
                .linearDrag(0.001f)
                .using(executor)
                .execute();

        pollUntilModuleCount("RigidBodyModule", entityCount);
        log.info("Rigid body attach phase completed in {}ms", System.currentTimeMillis() - startTime);

        // ========== PHASE 3: Benchmark tick rate ==========
        int tickCount = 100;
        log.info("Running {} physics ticks with {} entities", tickCount, entityCount);

        startTime = System.currentTimeMillis();
        for (int i = 0; i < tickCount; i++) {
            container.tick();
        }
        long elapsed = System.currentTimeMillis() - startTime;

        double ticksPerSecond = tickCount * 1000.0 / elapsed;
        double msPerTick = elapsed / (double) tickCount;

        log.info("Tick rate benchmark results:");
        log.info("  {} ticks in {}ms", tickCount, elapsed);
        log.info("  {:.1f} ticks/sec", ticksPerSecond);
        log.info("  {:.2f} ms/tick", msPerTick);

        // Verify entities moved
        var finalSnapshot = getSnapshot();
        var gridMap = finalSnapshot.module("GridMapModule");
        List<Float> finalPositionsX = gridMap.component("POSITION_X");

        long movedCount = finalPositionsX.stream()
                .filter(x -> Math.abs(x) > 50)
                .count();

        log.info("Entities that moved significantly: {}", movedCount);
        assertThat(movedCount).isGreaterThan(entityCount / 2);

        assertThat(ticksPerSecond)
                .as("Should achieve at least 60 ticks/sec")
                .isGreaterThan(60);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        log.info("Tick rate benchmark PASSED");
    }

    @Test
    @Order(13)
    @DisplayName("Test: Linear drag dampens velocity over time")
    void testLinearDragDampening() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntity();

        // Attach with high linear drag
        container.forMatch(matchId).custom("attachRigidBody")
                .param("entityId", entityId)
                .param("positionX", 0.0f)
                .param("positionY", 0.0f)
                .param("velocityX", 100.0f)
                .param("velocityY", 0.0f)
                .param("mass", 1.0f)
                .param("linearDrag", 0.5f) // 50% velocity reduction per tick
                .execute();

        container.tick();

        // Record initial velocity
        var snapshot1 = getSnapshot();
        float v1 = snapshot1.module("RigidBodyModule").first("VELOCITY_X", 0);

        // Tick several times
        for (int i = 0; i < 10; i++) {
            container.tick();
        }

        // Velocity should be much lower
        var snapshot2 = getSnapshot();
        float v2 = snapshot2.module("RigidBodyModule").first("VELOCITY_X", 0);

        log.info("Linear drag test: initial velocity={}, final velocity={}", v1, v2);

        assertThat(v2).isLessThan(v1 / 10); // Should be < 10% of initial
        log.info("Linear drag dampening test PASSED");
    }

    @Test
    @Order(14)
    @DisplayName("Test: Angular drag dampens rotation over time")
    void testAngularDragDampening() throws Exception {
        setupMatchAndResource();
        long entityId = spawnEntityWithRigidBody(0, 0);

        // Set high angular velocity with angular drag
        container.forMatch(matchId).custom("attachRigidBody")
                .param("entityId", entityId)
                .param("positionX", 0.0f)
                .param("positionY", 0.0f)
                .param("inertia", 1.0f)
                .param("angularDrag", 0.3f)
                .execute();

        container.tick();

        // Apply torque to start rotation
        container.forMatch(matchId).custom("applyTorque")
                .param("entityId", entityId)
                .param("torque", 100.0f)
                .execute();

        container.tick();

        var snapshot1 = getSnapshot();
        float av1 = snapshot1.module("RigidBodyModule").first("ANGULAR_VELOCITY", 0);

        // Let it dampen over time (no more torque applied)
        for (int i = 0; i < 20; i++) {
            container.tick();
        }

        var snapshot2 = getSnapshot();
        float av2 = snapshot2.module("RigidBodyModule").first("ANGULAR_VELOCITY", 0);

        log.info("Angular drag test: initial angularVelocity={}, final angularVelocity={}", av1, av2);

        assertThat(Math.abs(av2)).isLessThan(Math.abs(av1));
        log.info("Angular drag dampening test PASSED");
    }

    @Test
    @Order(15)
    @DisplayName("Test: Mass affects acceleration (F=ma)")
    void testMassAffectsAcceleration() throws Exception {
        setupMatchAndResource();

        // Create two entities with different masses
        long lightEntity = spawnEntity();
        long heavyEntity = spawnEntity();
        container.tick();

        // Light entity (mass = 1)
        container.forMatch(matchId).custom("attachRigidBody")
                .param("entityId", lightEntity)
                .param("positionX", 0.0f)
                .param("positionY", 0.0f)
                .param("mass", 1.0f)
                .param("linearDrag", 0.0f)
                .execute();

        // Heavy entity (mass = 10)
        container.forMatch(matchId).custom("attachRigidBody")
                .param("entityId", heavyEntity)
                .param("positionX", 100.0f)
                .param("positionY", 0.0f)
                .param("mass", 10.0f)
                .param("linearDrag", 0.0f)
                .execute();

        container.tick();

        // Apply same force to both
        container.forMatch(matchId).custom("applyForce")
                .param("entityId", lightEntity)
                .param("forceX", 100.0f)
                .param("forceY", 0.0f)
                .execute();

        container.forMatch(matchId).custom("applyForce")
                .param("entityId", heavyEntity)
                .param("forceX", 100.0f)
                .param("forceY", 0.0f)
                .execute();

        container.tick();

        var snapshot = getSnapshot();
        var rigidBody = snapshot.module("RigidBodyModule");

        List<Float> velocities = rigidBody.component("VELOCITY_X");
        List<Float> masses = rigidBody.component("MASS");

        // Find velocities by mass
        float lightVelocity = 0, heavyVelocity = 0;
        for (int i = 0; i < masses.size(); i++) {
            if (masses.get(i) < 5) {
                lightVelocity = velocities.get(i);
            } else {
                heavyVelocity = velocities.get(i);
            }
        }

        log.info("F=ma test: lightVelocity={}, heavyVelocity={}", lightVelocity, heavyVelocity);

        // Light entity should accelerate more (a = F/m)
        assertThat(lightVelocity).isGreaterThan(heavyVelocity * 5); // Should be ~10x faster

        log.info("Mass affects acceleration test PASSED");
    }

    // ========== Helper Methods ==========

    private void setupMatchAndResource() throws Exception {
        // Create container with required modules
        ContainerAdapter.ContainerResponse containerResponse = client.containers()
                .create()
                .name("physics-test-" + System.currentTimeMillis())
                .withModules(REQUIRED_MODULES.toArray(new String[0]))
                .execute();
        containerId = containerResponse.id();
        container = client.container(containerId);
        container.play(60);
        // Create match within container
        var match = container.createMatch(REQUIRED_MODULES);
        matchId = match.id();
        log.info("Created match {}", matchId);

        // Upload red-checker.png texture
        try (InputStream is = getClass().getResourceAsStream("/textures/red-checker.png")) {
            byte[] textureData;
            if (is != null) {
                textureData = is.readAllBytes();
            } else {
                // Fallback: create a simple red 32x32 PNG
                textureData = createSimpleRedPng();
            }

            resourceId = container.uploadResource()
                    .name("red-checker.png")
                    .type("TEXTURE")
                    .data(textureData)
                    .execute();
            log.info("Uploaded resource red-checker.png (id={})", resourceId);
        }
    }

    private long spawnEntity() throws IOException {
        container.forMatch(matchId).spawn()
                .forPlayer(1)
                .ofType(100)
                .execute();
        container.tick();

        var snapshotOpt = container.getSnapshot(matchId);
        if (snapshotOpt.isPresent()) {
            var snapshot = client.parseSnapshot(snapshotOpt.get().data());
            List<Float> entityIds = snapshot.entityIds();
            if (!entityIds.isEmpty()) {
                long entityId = entityIds.get(entityIds.size() - 1).longValue();
                createdEntityIds.add(entityId);
                return entityId;
            }
        }
        throw new IllegalStateException("Failed to spawn entity");
    }

    private long spawnEntityWithRigidBody(float x, float y) throws IOException {
        long entityId = spawnEntity();

        container.forMatch(matchId).send("attachRigidBody", Map.of(
                "entityId", entityId,
                "positionX", x,
                "positionY", y,
                "mass", 1.0f
        ));

        container.tick();
        return entityId;
    }

    private EngineClient.Snapshot getSnapshot() throws IOException {
        var snapshotOpt = container.getSnapshot(matchId);
        if (snapshotOpt.isEmpty()) {
            throw new IllegalStateException("No snapshot available for match " + matchId);
        }
        return client.parseSnapshot(snapshotOpt.get().data());
    }

    private int countEntitiesInModule(EngineClient.Snapshot snapshot, String moduleName) {
        var module = snapshot.module(moduleName);
        for (String component : module.componentNames()) {
            List<Float> values = module.component(component);
            if (!values.isEmpty()) {
                return values.size();
            }
        }
        return 0;
    }

    // ========== Parallel API Helper Methods ==========

    /**
     * Creates a parallel spawn builder.
     */
    private ParallelSpawnBuilder parallelSpawn(int entityCount) {
        return new ParallelSpawnBuilder(entityCount);
    }

    /**
     * Creates a parallel rigid body attachment builder.
     */
    private ParallelRigidBodyBuilder parallelRigidBody(List<Float> entityIds) {
        return new ParallelRigidBodyBuilder(entityIds);
    }

    /**
     * Creates a parallel sprite attachment builder.
     */
    private ParallelSpriteBuilder parallelSprite(List<Float> entityIds) {
        return new ParallelSpriteBuilder(entityIds);
    }

    /**
     * Polls until the specified module has at least the expected number of entities.
     */
    private void pollUntilModuleCount(String moduleName, int expected) throws Exception {
        int pollAttempts = 0;
        int count = 0;

        while (count < expected) {
//            container.tick();
            Thread.sleep(10);

            count = countEntitiesInModule(getSnapshot(), moduleName);

            pollAttempts++;
            if (pollAttempts % 100 == 0) {
                log.info("Polling {}: {} / {} (attempt {})", moduleName, count, expected, pollAttempts);
            }

            if (pollAttempts > 500) {
                log.warn("Timeout waiting for {} entities in {}. Got {}", expected, moduleName, count);
                break;
            }
        }
    }

    /**
     * Builder for parallel entity spawning.
     */
    private class ParallelSpawnBuilder {
        private final int entityCount;
        private ExecutorService executor;

        ParallelSpawnBuilder(int entityCount) {
            this.entityCount = entityCount;
        }

        ParallelSpawnBuilder using(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        void execute() throws Exception {
            // Warmup: ensure modules are loaded by doing a test spawn + tick
            container.forMatch(matchId).spawn()
                    .forPlayer(1)
                    .ofType(100)
                    .execute();
            container.tick();

            // Now fire all remaining spawn commands in parallel
            List<CompletableFuture<Void>> futures = new ArrayList<>(entityCount - 1);
            for (int i = 1; i < entityCount; i++) {
                futures.add(CompletableFuture.runAsync(() ->
                    container.forMatch(matchId).spawn()
                            .forPlayer(1)
                            .ofType(100)
                            .execute()
                , executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            container.play(60);
            while (getSnapshot().entityIds().size() < entityCount) {
                Thread.sleep(50);
            }
            container.stopAuto();
        }
    }

    /**
     * Builder for parallel rigid body attachment.
     */
    private class ParallelRigidBodyBuilder {
        private final List<Float> entityIds;
        private float[] posX, posY, velX, velY;
        private float mass = 1.0f;
        private float linearDrag = 0.01f;
        private ExecutorService executor;
        private List<CompletableFuture<Void>> targetFutures;

        ParallelRigidBodyBuilder(List<Float> entityIds) {
            this.entityIds = entityIds;
        }

        ParallelRigidBodyBuilder positions(float[] x, float[] y) {
            this.posX = x;
            this.posY = y;
            return this;
        }

        ParallelRigidBodyBuilder velocities(float[] vx, float[] vy) {
            this.velX = vx;
            this.velY = vy;
            return this;
        }

        ParallelRigidBodyBuilder mass(float mass) {
            this.mass = mass;
            return this;
        }

        ParallelRigidBodyBuilder linearDrag(float drag) {
            this.linearDrag = drag;
            return this;
        }

        ParallelRigidBodyBuilder using(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        ParallelRigidBodyBuilder addTo(List<CompletableFuture<Void>> futures) {
            this.targetFutures = futures;
            return this;
        }

        void execute() {
            List<CompletableFuture<Void>> futures = targetFutures != null ? targetFutures : new ArrayList<>();

            for (int i = 0; i < entityIds.size(); i++) {
                final int idx = i;
                final long entityId = entityIds.get(i).longValue();
                final float px = posX[idx];
                final float py = posY[idx];
                final float vx = velX != null ? velX[idx] : 0;
                final float vy = velY != null ? velY[idx] : 0;

                futures.add(CompletableFuture.runAsync(() ->
                    container.forMatch(matchId).custom("attachRigidBody")
                            .param("entityId", entityId)
                            .param("positionX", px)
                            .param("positionY", py)
                            .param("velocityX", vx)
                            .param("velocityY", vy)
                            .param("mass", mass)
                            .param("linearDrag", linearDrag)
                            .execute()
                , executor));
            }

            if (targetFutures == null) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    /**
     * Builder for parallel sprite attachment.
     */
    private class ParallelSpriteBuilder {
        private final List<Float> entityIds;
        private long resourceId;
        private int width = 32, height = 32;
        private ExecutorService executor;
        private List<CompletableFuture<Void>> targetFutures;

        ParallelSpriteBuilder(List<Float> entityIds) {
            this.entityIds = entityIds;
        }

        ParallelSpriteBuilder resource(long resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        ParallelSpriteBuilder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        ParallelSpriteBuilder using(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        ParallelSpriteBuilder addTo(List<CompletableFuture<Void>> futures) {
            this.targetFutures = futures;
            return this;
        }

        void execute() {
            List<CompletableFuture<Void>> futures = targetFutures != null ? targetFutures : new ArrayList<>();

            for (Float entityIdFloat : entityIds) {
                final long entityId = entityIdFloat.longValue();
                futures.add(CompletableFuture.runAsync(() ->
                    container.forMatch(matchId).attachSprite()
                            .toEntity(entityId)
                            .usingResource(resourceId)
                            .sized(width, height)
                            .visible(true)
                            .execute()
                , executor));
            }

            if (targetFutures == null) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    private byte[] createSimpleRedPng() {
        // Minimal valid 1x1 red PNG
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
                0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01,
                0x00, 0x05, 0x6D, (byte) 0xCD, (byte) 0xB2,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
