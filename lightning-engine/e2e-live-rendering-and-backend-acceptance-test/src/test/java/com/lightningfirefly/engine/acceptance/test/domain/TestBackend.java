package com.lightningfirefly.engine.acceptance.test.domain;

import com.lightningfirefly.engine.api.resource.adapter.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the test domain layer.
 *
 * <p>Provides a fluent API for interacting with the backend server in tests.
 * All HTTP communication is encapsulated, making tests readable and focused
 * on business logic rather than protocol details.
 *
 * <p>Example usage:
 * <pre>{@code
 * TestBackend backend = TestBackend.connectTo(backendUrl);
 *
 * Match match = backend.createMatch()
 *     .withModules("EntityModule", "RenderModule")
 *     .start();
 *
 * match.spawnEntity().ofType(100).execute();
 * match.tick();
 * }</pre>
 */
public class TestBackend {

    private final MatchAdapter matchAdapter;
    private final CommandAdapter commandAdapter;
    private final SnapshotAdapter snapshotAdapter;
    private final SimulationAdapter simulationAdapter;

    private TestBackend(String baseUrl) {
        this.matchAdapter = new MatchAdapter.HttpMatchAdapter(baseUrl);
        this.commandAdapter = new CommandAdapter.HttpCommandAdapter(baseUrl);
        this.snapshotAdapter = new SnapshotAdapter.HttpSnapshotAdapter(baseUrl);
        this.simulationAdapter = new SimulationAdapter.HttpSimulationAdapter(baseUrl);
    }

    /**
     * Connect to a backend server.
     *
     * @param baseUrl the server base URL (e.g., "http://localhost:8080")
     * @return a TestBackend instance connected to the server
     */
    public static TestBackend connectTo(String baseUrl) {
        return new TestBackend(baseUrl);
    }

    /**
     * Start creating a new match.
     *
     * @return a builder for configuring the match
     */
    public MatchBuilder createMatch() {
        return new MatchBuilder(this);
    }

    /**
     * Advance the simulation by one tick.
     *
     * @return this backend for chaining
     */
    public TestBackend tick() {
        try {
            simulationAdapter.advanceTick();
            Thread.sleep(50); // Allow processing time
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to advance tick", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tick interrupted", e);
        }
        return this;
    }

    // Accessors for domain classes and tests
    public MatchAdapter matchAdapter() { return matchAdapter; }
    public CommandAdapter commandAdapter() { return commandAdapter; }
    public SnapshotAdapter snapshotAdapter() { return snapshotAdapter; }
    public SimulationAdapter simulationAdapter() { return simulationAdapter; }

    /**
     * Builder for creating matches with a fluent API.
     */
    public static class MatchBuilder {
        private final TestBackend backend;
        private final List<String> modules = new ArrayList<>();
        private final List<String> gameMasters = new ArrayList<>();

        private MatchBuilder(TestBackend backend) {
            this.backend = backend;
        }

        /**
         * Add a module to the match.
         *
         * @param moduleName the module name (e.g., "EntityModule", "RenderModule")
         * @return this builder for chaining
         */
        public MatchBuilder withModule(String moduleName) {
            this.modules.add(moduleName);
            return this;
        }

        /**
         * Add multiple modules to the match.
         *
         * @param moduleNames the module names
         * @return this builder for chaining
         */
        public MatchBuilder withModules(String... moduleNames) {
            for (String name : moduleNames) {
                this.modules.add(name);
            }
            return this;
        }

        /**
         * Add a game master to the match.
         *
         * @param gameMasterName the game master name
         * @return this builder for chaining
         */
        public MatchBuilder withGameMaster(String gameMasterName) {
            this.gameMasters.add(gameMasterName);
            return this;
        }

        /**
         * Create the match on the server.
         *
         * @return the created Match domain object
         */
        public Match start() {
            try {
                MatchAdapter.MatchResponse response = gameMasters.isEmpty()
                        ? backend.matchAdapter().createMatch(modules)
                        : backend.matchAdapter().createMatchWithGameMasters(modules, gameMasters);
                return new Match(backend, response.id(), modules);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create match", e);
            }
        }
    }
}
