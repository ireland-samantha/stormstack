package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.core.exception.EntityNotFoundException;
import com.lightningfirefly.engine.internal.core.command.CommandResolver;
import com.lightningfirefly.engine.quarkus.api.dto.CommandRequest;
import it.unimi.dsi.fastutil.Pair;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * REST resource for command operations.
 */
@Path("/api/commands")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommandResource {

    @Inject
    GameSimulation gameSimulation;

    @Inject
    CommandResolver commandResolver;

    @POST
    public Response enqueueCommand(CommandRequest request) {
        CommandPayload payload = new MapCommandPayload(request.payload());
        try {
            gameSimulation.enqueueCommand(request.commandName(), payload);
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return Response.status(404).build(); // todo ai: add an exception mapper
        }
        return Response.accepted()
                .entity(new CommandResponse("accepted", request.commandName()))
                .build();
    }

//     .map(pair -> Pair.of(pair.first(),
//            String.join(",", pair.second().keySet().stream()
//                                    .map(key -> String.format("%s (%s)", key, pair.second().get(key)))
//            .toList())))
//
//            .map(pair -> String.format("%s(%s)", pair.first(), pair.second()))


    @GET
    public Response getAllCommands() {
        return Response.ok(commandResolver.getAll().stream()
                .map(command -> Pair.of(command.getName(),
                        String.join(", ", command.schema().keySet().stream()
                                .map(key -> String.format("%s %s", getSimpleTypeName(command.schema().get(key)), key))
                                .toList())))

                .map(pair -> String.format("%s(%s)", pair.first(), pair.second())))
                .build();
    }

    /**
     * Get a simple type name for display (e.g., "long" instead of "class java.lang.Long").
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == null) return "unknown";
        if (type == Long.class || type == long.class) return "long";
        if (type == Integer.class || type == int.class) return "int";
        if (type == Double.class || type == double.class) return "double";
        if (type == Float.class || type == float.class) return "float";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == String.class) return "String";
        return type.getSimpleName();
    }
    /**
     * Response for command submission.
     */
    public record CommandResponse(String status, String commandName) {
    }

    /**
     * Simple command payload backed by a map.
     */
    private record MapCommandPayload(Map<String, Object> data) implements CommandPayload {
        @Override
        public Map<String, Object> getPayload() {
            return data;
        }
    }
}
