package com.lightningfirefly.engine.quarkus.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.ext.modules.SpawnModuleFactory;
import com.lightningfirefly.engine.quarkus.api.dto.CommandRequest;
import com.lightningfirefly.api.proto.CommandProtos;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * WebSocket endpoint for submitting commands.
 *
 * <p>Clients connect to /ws/commands and can send commands as:
 * <ul>
 *   <li>JSON text messages (legacy)</li>
 *   <li>Protocol Buffer binary messages (preferred)</li>
 * </ul>
 */
@Slf4j
@WebSocket(path = "/ws/commands")
public class CommandWebSocket {

    @Inject
    GameSimulation gameSimulation;

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public String onOpen() {
        return "{\"status\":\"connected\",\"message\":\"Send commands as JSON or Protobuf binary\"}";
    }

    /**
     * Handle JSON text messages (legacy format).
     */
    @OnTextMessage
    public String onTextMessage(String message) {
        try {
            CommandRequest request = objectMapper.readValue(message, CommandRequest.class);
            CommandPayload payload = new MapCommandPayload(request.payload());
            gameSimulation.enqueueCommand(request.commandName(), payload);
            return "{\"status\":\"accepted\",\"command\":\"" + request.commandName() + "\"}";
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse command: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"Invalid JSON format\"}";
        }
    }

    /**
     * Handle Protocol Buffer binary messages (preferred format).
     */
    @OnBinaryMessage
    public Buffer onBinaryMessage(Buffer message) {
        try {
            byte[] bytes = message.getBytes();
            CommandProtos.CommandRequest request = CommandProtos.CommandRequest.parseFrom(bytes);

            String commandName = request.getCommandName();
            CommandPayload payload = toCommandPayload(request);

            gameSimulation.enqueueCommand(commandName, payload);

            CommandProtos.CommandResponse response = CommandProtos.CommandResponse.newBuilder()
                    .setStatus(CommandProtos.CommandResponse.Status.ACCEPTED)
                    .setCommandName(commandName)
                    .setMessage("Command accepted")
                    .build();

            return Buffer.buffer(response.toByteArray());

        } catch (Exception e) {
            log.warn("Failed to parse protobuf command: {}", e.getMessage());

            CommandProtos.CommandResponse response = CommandProtos.CommandResponse.newBuilder()
                    .setStatus(CommandProtos.CommandResponse.Status.ERROR)
                    .setMessage("Failed to parse command: " + e.getMessage())
                    .build();

            return Buffer.buffer(response.toByteArray());
        }
    }

    /**
     * Convert protobuf request to CommandPayload.
     */
    private CommandPayload toCommandPayload(CommandProtos.CommandRequest request) {
        switch (request.getPayloadCase()) {
            case SPAWN:
                CommandProtos.SpawnPayload spawn = request.getSpawn();
                return new SpawnModuleFactory.SpawnPayload(
                        request.getMatchId(),
                        request.getPlayerId(),
                        spawn.getEntityType(),
                        spawn.getPositionX(),
                        spawn.getPositionY()
                );

            case MOVE:
                CommandProtos.MovePayload move = request.getMove();
                return new MoveCommandPayload(
                        move.getEntityId(),
                        move.getTargetX(),
                        move.getTargetY()
                );

            default:
                // Return a generic payload with match/player context
                return new ContextCommandPayload(
                        request.getMatchId(),
                        request.getPlayerId()
                );
        }
    }

    /**
     * Generic payload from JSON map.
     */
    private record MapCommandPayload(Map<String, Object> data) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return data;
        }
    }

    /**
     * Payload for move commands.
     */
    private record MoveCommandPayload(long entityId, long targetX, long targetY) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return Map.of("id", entityId, "targetX", targetX, "targetY", targetY);
        }
    }

    /**
     * Payload with just match/player context.
     */
    private record ContextCommandPayload(long matchId, long playerId) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return Map.of();
        }
    }
}
