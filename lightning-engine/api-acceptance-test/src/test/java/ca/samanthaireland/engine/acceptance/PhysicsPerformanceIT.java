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
import org.junit.jupiter.api.Disabled;
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
@Disabled("Performance tests disabled - use PhysicsIT for functional tests")
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

    // ========== Performance Benchmarks ==========
    // Note: Functional tests have been moved to PhysicsIT.java

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
