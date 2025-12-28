package com.lightningfirefly.game.backend.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent client for command operations.
 *
 * <p>Usage:
 * <pre>{@code
 * var commands = client.commands().forMatch(matchId);
 *
 * // Spawn an entity
 * commands.spawn().forPlayer(1).ofType(100).execute();
 *
 * // Attach a sprite
 * commands.attachSprite()
 *     .toEntity(entityId)
 *     .usingResource(resourceId)
 *     .at(100, 200)
 *     .sized(48, 48)
 *     .execute();
 *
 * // Move an entity
 * commands.move()
 *     .entity(entityId)
 *     .to(300, 400)
 *     .execute();
 *
 * // Custom command
 * commands.custom("myCommand")
 *     .param("key", value)
 *     .execute();
 * }</pre>
 */
@Slf4j
public final class CommandsClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    CommandsClient(String baseUrl, HttpClient httpClient, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Target a specific match for commands.
     */
    public MatchCommands forMatch(long matchId) {
        return new MatchCommands(this, matchId);
    }

    void sendCommand(long matchId, String commandName, Map<String, Object> payload) {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"matchId\":").append(matchId);
            json.append(",\"commandName\":\"").append(commandName).append("\"");
            json.append(",\"payload\":{");

            boolean first = true;
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else {
                    json.append(value);
                }
                first = false;
            }
            json.append("}}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/commands"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Command failed: " + response.statusCode() + " - " + response.body());
            }
            log.debug("Sent command {}: {}", commandName, payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to send command " + commandName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        }
    }

    /**
     * Commands for a specific match.
     */
    public static class MatchCommands {
        private final CommandsClient client;
        private final long matchId;

        MatchCommands(CommandsClient client, long matchId) {
            this.client = client;
            this.matchId = matchId;
        }

        /**
         * Spawn a new entity.
         */
        public SpawnBuilder spawn() {
            return new SpawnBuilder(this);
        }

        /**
         * Attach a sprite to an entity.
         */
        public AttachSpriteBuilder attachSprite() {
            return new AttachSpriteBuilder(this);
        }

        /**
         * Move an entity.
         */
        public MoveBuilder move() {
            return new MoveBuilder(this);
        }

        /**
         * Create a custom command.
         */
        public CustomCommandBuilder custom(String commandName) {
            return new CustomCommandBuilder(this, commandName);
        }

        void send(String commandName, Map<String, Object> payload) {
            client.sendCommand(matchId, commandName, payload);
        }

        long matchId() {
            return matchId;
        }
    }

    /**
     * Builder for spawn command.
     */
    public static class SpawnBuilder {
        private final MatchCommands commands;
        private long playerId = 1;
        private long entityType = 0;

        SpawnBuilder(MatchCommands commands) {
            this.commands = commands;
        }

        public SpawnBuilder forPlayer(long playerId) {
            this.playerId = playerId;
            return this;
        }

        public SpawnBuilder ofType(long entityType) {
            this.entityType = entityType;
            return this;
        }

        public void execute() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("matchId", commands.matchId());
            payload.put("playerId", playerId);
            payload.put("entityType", entityType);
            commands.send("spawn", payload);
        }
    }

    /**
     * Builder for attachSprite command.
     */
    public static class AttachSpriteBuilder {
        private final MatchCommands commands;
        private long entityId;
        private long resourceId;
        private float x, y;
        private float width = 32, height = 32;
        private float rotation = 0;
        private int zIndex = 0;
        private boolean visible = true;

        AttachSpriteBuilder(MatchCommands commands) {
            this.commands = commands;
        }

        public AttachSpriteBuilder toEntity(long entityId) {
            this.entityId = entityId;
            return this;
        }

        public AttachSpriteBuilder usingResource(long resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public AttachSpriteBuilder at(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public AttachSpriteBuilder sized(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public AttachSpriteBuilder rotatedBy(float degrees) {
            this.rotation = degrees;
            return this;
        }

        public AttachSpriteBuilder onLayer(int zIndex) {
            this.zIndex = zIndex;
            return this;
        }

        public AttachSpriteBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public void execute() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entityId);
            payload.put("resourceId", resourceId);
            payload.put("x", x);
            payload.put("y", y);
            payload.put("width", width);
            payload.put("height", height);
            payload.put("rotation", rotation);
            payload.put("zIndex", zIndex);
            payload.put("visible", visible ? 1.0 : 0.0);
            commands.send("attachSprite", payload);
        }
    }

    /**
     * Builder for move command.
     */
    public static class MoveBuilder {
        private final MatchCommands commands;
        private long entityId;
        private float x, y;
        private Float velocityX, velocityY;

        MoveBuilder(MatchCommands commands) {
            this.commands = commands;
        }

        public MoveBuilder entity(long entityId) {
            this.entityId = entityId;
            return this;
        }

        public MoveBuilder to(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public MoveBuilder withVelocity(float vx, float vy) {
            this.velocityX = vx;
            this.velocityY = vy;
            return this;
        }

        public void execute() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("entityId", entityId);
            payload.put("x", x);
            payload.put("y", y);
            if (velocityX != null) payload.put("velocityX", velocityX);
            if (velocityY != null) payload.put("velocityY", velocityY);
            commands.send("move", payload);
        }
    }

    /**
     * Builder for custom commands.
     */
    public static class CustomCommandBuilder {
        private final MatchCommands commands;
        private final String commandName;
        private final Map<String, Object> payload = new HashMap<>();

        CustomCommandBuilder(MatchCommands commands, String commandName) {
            this.commands = commands;
            this.commandName = commandName;
        }

        public CustomCommandBuilder param(String key, Object value) {
            payload.put(key, value);
            return this;
        }

        public CustomCommandBuilder params(Map<String, Object> params) {
            payload.putAll(params);
            return this;
        }

        public void execute() {
            commands.send(commandName, payload);
        }
    }
}
