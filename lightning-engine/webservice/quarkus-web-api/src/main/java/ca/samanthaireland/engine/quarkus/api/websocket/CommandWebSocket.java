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


package ca.samanthaireland.engine.quarkus.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ca.samanthaireland.engine.core.GameSimulation;
import ca.samanthaireland.engine.core.command.CommandPayload;
import ca.samanthaireland.engine.ext.modules.SpawnPayload;
import ca.samanthaireland.engine.quarkus.api.dto.CommandRequest;
import ca.samanthaireland.api.proto.CommandProtos;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@WebSocket(path = "/ws/commands")
public class CommandWebSocket {
    private static final Logger log = LoggerFactory.getLogger(CommandWebSocket.class);

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
                return new SpawnPayload(
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
