package com.lightningfirefly.game.backend.adapter;

import com.lightningfirefly.game.domain.GameMasterContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Adapter for sending GameMasterCommands to the server via HTTP.
 *
 * <p>This class bridges the domain layer (GameMaster) with the server infrastructure,
 * allowing game logic to execute commands on the ECS backend.
 */
@Slf4j
public class GameMasterCommandAdapter {

    private final String serverUrl;
    private final long matchId;
    private final HttpClient httpClient;

    public GameMasterCommandAdapter(String serverUrl, long matchId) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.matchId = matchId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Execute a command on the server.
     *
     * @param command the command to execute
     * @throws IOException if the command fails
     */
    public void execute(GameMasterCommand command) throws IOException {
        String json = buildCommandJson(command);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/commands/" + command.commandName()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Command failed with status " + response.statusCode() + ": " + response.body());
            }
            log.debug("Executed command {}: {}", command.commandName(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command interrupted", e);
        }
    }

    /**
     * Execute a command via the GameMasterContext.
     * <p>This is the preferred way to execute commands from game master logic,
     * as it properly integrates with the context's lifecycle management.
     *
     * @param context the game master context
     * @param command the command to execute
     */
    public static void executeViaContext(GameMasterContext context, GameMasterCommand command) {
        context.executeCommand(command);
    }

    /**
     * Build JSON for a command payload.
     */
    private String buildCommandJson(GameMasterCommand command) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"matchId\":").append(matchId);

        Map<String, Object> payload = command.payload();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            json.append(",\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Create a simple command record.
     */
    public static GameMasterCommand command(String name, Map<String, Object> payload) {
        return new GameMasterCommand() {
            @Override
            public String commandName() {
                return name;
            }

            @Override
            public Map<String, Object> payload() {
                return payload;
            }
        };
    }
}
