package com.lightningfirefly.engine.acceptance.test.domain;

import com.lightningfirefly.game.orchestrator.Snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Domain object representing a game match.
 *
 * <p>Provides a fluent API for match operations like spawning entities,
 * executing commands, and fetching snapshots.
 *
 * <p>Example usage:
 * <pre>{@code
 * Entity entity = match.spawnEntity().ofType(100).execute();
 * match.tick();
 *
 * entity.attachSprite()
 *     .atPosition(100, 200)
 *     .withSize(48, 48)
 *     .execute();
 *
 * match.snapshot()
 *     .assertModule("RenderModule")
 *     .hasComponent("SPRITE_X").withValue(100);
 * }</pre>
 */
public class Match {

    private final TestBackend backend;
    private final long matchId;
    private final List<String> modules;
    private final AtomicLong nextEntityId = new AtomicLong(1);
    private final Map<Long, Entity> entities = new HashMap<>();

    Match(TestBackend backend, long matchId, List<String> modules) {
        this.backend = backend;
        this.matchId = matchId;
        this.modules = List.copyOf(modules);
    }

    /**
     * Get the match ID.
     *
     * @return the server-assigned match ID
     */
    public long id() {
        return matchId;
    }

    /**
     * Get the enabled modules for this match.
     *
     * @return immutable list of module names
     */
    public List<String> modules() {
        return modules;
    }

    /**
     * Advance the simulation by one tick.
     *
     * @return this match for chaining
     */
    public Match tick() {
        backend.tick();
        return this;
    }

    /**
     * Advance the simulation by multiple ticks.
     *
     * @param count number of ticks to advance
     * @return this match for chaining
     */
    public Match tick(int count) {
        for (int i = 0; i < count; i++) {
            tick();
        }
        return this;
    }

    /**
     * Start spawning a new entity.
     *
     * @return a builder for configuring the entity spawn
     */
    public SpawnEntityBuilder spawnEntity() {
        return new SpawnEntityBuilder(this);
    }

    /**
     * Get an existing entity by ID.
     *
     * @param entityId the entity ID
     * @return the entity domain object
     */
    public Entity entity(long entityId) {
        return entities.computeIfAbsent(entityId, id -> new Entity(this, id));
    }

    /**
     * Fetch the current snapshot for this match.
     *
     * @return a SnapshotAssertions object for verification
     */
    public SnapshotAssertions assertThatSnapshot() {
        try {
            return backend.snapshotAdapter().getMatchSnapshot(matchId)
                    .map(response -> {
                        SnapshotParser parser = SnapshotParser.parse(
                                "{\"data\":" + response.snapshotData() + "}");
                        return new SnapshotAssertions(parser);
                    })
                    .orElseGet(() -> new SnapshotAssertions(SnapshotParser.parse("{}")));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch snapshot", e);
        }
    }

    /**
     * Fetch the snapshot as a Snapshot record for rendering.
     *
     * @return the Snapshot record
     */
    public Snapshot fetchSnapshotForRendering() {
        try {
            return backend.snapshotAdapter().getMatchSnapshot(matchId)
                    .map(response -> {
                        SnapshotParser parser = SnapshotParser.parse(
                                "{\"data\":" + response.snapshotData() + "}");
                        return parser.toSnapshot();
                    })
                    .orElseGet(() -> new Snapshot(Map.of()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch snapshot", e);
        }
    }

    /**
     * Delete this match from the server.
     */
    public void delete() {
        try {
            backend.matchAdapter().deleteMatch(matchId);
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    // Package-private accessors for child domain objects
    TestBackend backend() { return backend; }
    long matchId() { return matchId; }

    void registerEntity(long entityId, Entity entity) {
        entities.put(entityId, entity);
    }

    long allocateEntityId() {
        return nextEntityId.getAndIncrement();
    }

    /**
     * Fetch the current snapshot parser for this match.
     *
     * @return the SnapshotParser
     */
    SnapshotParser fetchSnapshot() {
        try {
            return backend.snapshotAdapter().getMatchSnapshot(matchId)
                    .map(response -> SnapshotParser.parse(
                            "{\"data\":" + response.snapshotData() + "}"))
                    .orElseGet(() -> SnapshotParser.parse("{}"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch snapshot", e);
        }
    }

    /**
     * Builder for spawning entities.
     */
    public static class SpawnEntityBuilder {
        private final Match match;
        private long entityType = 0;
        private long playerId = 1;

        private SpawnEntityBuilder(Match match) {
            this.match = match;
        }

        /**
         * Set the entity type.
         *
         * @param type the entity type ID
         * @return this builder for chaining
         */
        public SpawnEntityBuilder ofType(long type) {
            this.entityType = type;
            return this;
        }

        /**
         * Set the player ID.
         *
         * @param playerId the player ID
         * @return this builder for chaining
         */
        public SpawnEntityBuilder forPlayer(long playerId) {
            this.playerId = playerId;
            return this;
        }

        /**
         * Execute the spawn command.
         *
         * <p>This method sends the spawn command, ticks the simulation to process it,
         * then queries the snapshot to find the actual server-assigned entity ID.
         *
         * @return the created Entity domain object
         */
        public Entity execute() {
            // Get existing entity IDs before spawning
            SnapshotParser beforeSnapshot = match.fetchSnapshot();
            List<Float> existingIds = beforeSnapshot.getComponent("SpawnModule", "ENTITY_ID");

            Map<String, Object> payload = new HashMap<>();
            payload.put("matchId", match.matchId);
            payload.put("playerId", playerId);
            payload.put("entityType", entityType);

            try {
                match.backend().commandAdapter().submitCommand(
                        match.matchId, "spawn", 0, payload);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to spawn entity", e);
            }

            // Tick to process the spawn command
            match.tick();

            // Query snapshot to find the new entity ID
            SnapshotParser afterSnapshot = match.fetchSnapshot();
            List<Float> afterIds = afterSnapshot.getComponent("SpawnModule", "ENTITY_ID");

            // Find the new entity ID (one that wasn't in the before list)
            long entityId = 0;
            for (Float id : afterIds) {
                if (!existingIds.contains(id)) {
                    entityId = id.longValue();
                    break;
                }
            }

            if (entityId == 0 && !afterIds.isEmpty()) {
                // Fallback: use the last entity ID in the list
                entityId = afterIds.get(afterIds.size() - 1).longValue();
            }

            Entity entity = new Entity(match, entityId);
            match.registerEntity(entityId, entity);
            return entity;
        }
    }
}
