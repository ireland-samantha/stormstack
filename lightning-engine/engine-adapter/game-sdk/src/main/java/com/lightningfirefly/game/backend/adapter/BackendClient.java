package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fluent entry point for Lightning Engine backend operations.
 *
 * <p>Provides a slick, minimal API for all backend domains:
 * <pre>{@code
 * var client = BackendClient.connect("http://localhost:8080");
 *
 * // Matches
 * var match = client.matches().create()
 *     .withModule("SpawnModule")
 *     .withModule("RenderModule")
 *     .execute();
 *
 * // Commands
 * client.commands()
 *     .forMatch(match.id())
 *     .spawn().forPlayer(1).ofType(100).execute();
 *
 * // Simulation
 * client.simulation().tick();
 * client.simulation().play(10);  // 10ms interval
 *
 * // Snapshots
 * var snapshot = client.snapshots().forMatch(match.id()).fetch();
 *
 * // Modules
 * var modules = client.modules().list();
 *
 * // Resources
 * client.resources().upload("texture.png", bytes);
 * var texture = client.resources().download(resourceId);
 *
 * // Game Masters
 * var gameMasters = client.gameMasters().list();
 * }</pre>
 */
@Slf4j
public final class BackendClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    // Cached domain clients
    private volatile MatchesClient matchesClient;
    private volatile CommandsClient commandsClient;
    private volatile SimulationClient simulationClient;
    private volatile SnapshotsClient snapshotsClient;
    private volatile ModulesClient modulesClient;
    private volatile ResourcesClient resourcesClient;
    private volatile GameMastersClient gameMastersClient;

    private BackendClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Connect to a backend server.
     *
     * @param serverUrl the backend server URL (e.g., "http://localhost:8080")
     * @return a new BackendClient
     */
    public static BackendClient connect(String serverUrl) {
        return builder().serverUrl(serverUrl).build();
    }

    /**
     * Create a builder for customized configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Access match operations.
     */
    public MatchesClient matches() {
        if (matchesClient == null) {
            synchronized (this) {
                if (matchesClient == null) {
                    matchesClient = new MatchesClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return matchesClient;
    }

    /**
     * Access command operations.
     */
    public CommandsClient commands() {
        if (commandsClient == null) {
            synchronized (this) {
                if (commandsClient == null) {
                    commandsClient = new CommandsClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return commandsClient;
    }

    /**
     * Access simulation control operations.
     */
    public SimulationClient simulation() {
        if (simulationClient == null) {
            synchronized (this) {
                if (simulationClient == null) {
                    simulationClient = new SimulationClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return simulationClient;
    }

    /**
     * Access snapshot operations.
     */
    public SnapshotsClient snapshots() {
        if (snapshotsClient == null) {
            synchronized (this) {
                if (snapshotsClient == null) {
                    snapshotsClient = new SnapshotsClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return snapshotsClient;
    }

    /**
     * Access module operations.
     */
    public ModulesClient modules() {
        if (modulesClient == null) {
            synchronized (this) {
                if (modulesClient == null) {
                    modulesClient = new ModulesClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return modulesClient;
    }

    /**
     * Access resource operations.
     */
    public ResourcesClient resources() {
        if (resourcesClient == null) {
            synchronized (this) {
                if (resourcesClient == null) {
                    resourcesClient = new ResourcesClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return resourcesClient;
    }

    /**
     * Access game master operations.
     */
    public GameMastersClient gameMasters() {
        if (gameMastersClient == null) {
            synchronized (this) {
                if (gameMastersClient == null) {
                    gameMastersClient = new GameMastersClient(baseUrl, httpClient, requestTimeout);
                }
            }
        }
        return gameMastersClient;
    }

    /**
     * Get the base URL.
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Builder for customized BackendClient configuration.
     */
    public static class Builder {
        private String serverUrl = "http://localhost:8080";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private HttpClient httpClient;

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public BackendClient build() {
            HttpClient client = httpClient != null ? httpClient :
                    HttpClient.newBuilder()
                            .connectTimeout(connectTimeout)
                            .build();
            return new BackendClient(serverUrl, client, requestTimeout);
        }
    }
}
