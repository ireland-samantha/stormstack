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


package ca.samanthaireland.engine.quarkus.api.rest;

import ca.samanthaireland.engine.core.exception.EntityNotFoundException;
import ca.samanthaireland.engine.ext.module.ModuleFactory;
import ca.samanthaireland.game.backend.installation.AIFactory;
import ca.samanthaireland.engine.internal.ext.module.ModuleManager;
import ca.samanthaireland.engine.internal.ext.ai.AIManager;
import ca.samanthaireland.engine.core.match.Match;
import ca.samanthaireland.engine.core.match.Player;
import ca.samanthaireland.engine.core.match.PlayerMatch;
import ca.samanthaireland.engine.core.match.PlayerMatchService;
import ca.samanthaireland.engine.core.match.PlayerService;
import ca.samanthaireland.engine.core.container.ExecutionContainer;
import ca.samanthaireland.engine.core.container.ContainerConfig;
import ca.samanthaireland.engine.core.container.ContainerManager;
import ca.samanthaireland.engine.core.session.PlayerSession;
import ca.samanthaireland.engine.core.session.PlayerSessionService;
import ca.samanthaireland.engine.core.snapshot.DeltaCompressionService;
import ca.samanthaireland.engine.core.snapshot.DeltaSnapshot;
import ca.samanthaireland.engine.core.snapshot.Snapshot;
import ca.samanthaireland.engine.core.snapshot.SnapshotHistory;
import ca.samanthaireland.engine.core.snapshot.SnapshotRestoreService;
import ca.samanthaireland.engine.core.snapshot.SnapshotRestoreService.RestoreResult;
import ca.samanthaireland.engine.internal.core.snapshot.SnapshotProvider;
import ca.samanthaireland.engine.quarkus.api.config.RestoreConfig;
import ca.samanthaireland.engine.quarkus.api.dto.DeltaSnapshotResponse;
import ca.samanthaireland.engine.quarkus.api.dto.SnapshotResponse;
import ca.samanthaireland.engine.quarkus.api.dto.JoinMatchResponse;
import ca.samanthaireland.engine.quarkus.api.dto.MatchRequest;
import ca.samanthaireland.engine.quarkus.api.dto.MatchResponse;
import ca.samanthaireland.engine.quarkus.api.dto.ContainerRequest;
import ca.samanthaireland.engine.quarkus.api.dto.ContainerResponse;
import ca.samanthaireland.engine.quarkus.api.dto.PlayerMatchResponse;
import ca.samanthaireland.engine.quarkus.api.dto.PlayerRequest;
import ca.samanthaireland.engine.quarkus.api.dto.PlayerResponse;
import ca.samanthaireland.engine.quarkus.api.dto.PlayStatusResponse;
import ca.samanthaireland.engine.quarkus.api.dto.SessionResponse;
import ca.samanthaireland.engine.quarkus.api.dto.TickResponse;
import ca.samanthaireland.engine.quarkus.api.persistence.SnapshotDocument;
import ca.samanthaireland.engine.quarkus.api.persistence.SnapshotHistoryService;
import ca.samanthaireland.engine.quarkus.api.persistence.SnapshotPersistenceConfig;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST resource for managing execution containers.
 *
 * <p>Containers provide complete runtime isolation with separate classloaders,
 * entity stores, game loops, and command queues.
 */
@Path("/api/containers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContainerResource {
    private static final Logger log = LoggerFactory.getLogger(ContainerResource.class);

    @Inject
    ContainerManager containerManager;

    @Inject
    ModuleManager globalModuleManager;

    @Inject
    AIManager globalAIManager;

    @Inject
    PlayerSessionService sessionService;

    @Inject
    PlayerService playerService;

    @Inject
    PlayerMatchService playerMatchService;

    @Inject
    SnapshotProvider snapshotProvider;

    @Inject
    SnapshotHistory snapshotHistory;

    @Inject
    DeltaCompressionService deltaCompressionService;

    @Inject
    SnapshotHistoryService historyService;

    @Inject
    SnapshotPersistenceConfig persistenceConfig;

    @Inject
    SnapshotRestoreService restoreService;

    @Inject
    RestoreConfig restoreConfig;

    // =========================================================================
    // CONTAINER CRUD
    // =========================================================================

    /**
     * Create a new execution container.
     */
    @POST
    @RolesAllowed("admin")
    public Response createContainer(ContainerRequest request) {
        log.info("Creating container: {}", request.name());

        ContainerConfig.Builder builder = ContainerConfig.builder(request.name());

        if (request.maxEntities() != null) {
            builder.maxEntities(request.maxEntities());
        }
        if (request.maxComponents() != null) {
            builder.maxComponents(request.maxComponents());
        }
        if (request.maxCommandsPerTick() != null) {
            builder.maxCommandsPerTick(request.maxCommandsPerTick());
        }
        if (request.maxMemoryMb() != null) {
            // Bound memory by JVM max heap
            long jvmMaxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
            long requestedMb = request.maxMemoryMb();
            if (requestedMb > jvmMaxMb) {
                log.warn("Requested memory {}MB exceeds JVM max {}MB, using JVM max", requestedMb, jvmMaxMb);
                requestedMb = jvmMaxMb;
            }
            builder.maxMemoryMb(requestedMb);
        }
        if (request.moduleJars() != null) {
            builder.moduleJarPaths(request.moduleJars());
        }
        if (request.moduleScanDirectory() != null) {
            builder.moduleScanDirectory(java.nio.file.Path.of(request.moduleScanDirectory()));
        }

        ExecutionContainer container = containerManager.createContainer(builder.build());

        // Start container first so moduleManager is initialized
        boolean hasModulesToInstall = !request.moduleNames().isEmpty() || !request.aiNames().isEmpty();
        if (hasModulesToInstall) {
            container.lifecycle().start();
        }

        // Install selected modules from global pool
        for (String moduleName : request.moduleNames()) {
            ModuleFactory factory = globalModuleManager.getFactory(moduleName);
            if (factory != null) {
                container.modules().install(factory.getClass());
                log.info("Installed module '{}' into container '{}'", moduleName, request.name());
            } else {
                log.warn("Module '{}' not found in global pool, skipping", moduleName);
            }
        }

        // Install selected AI from global pool
        for (String aiName : request.aiNames()) {
            AIFactory factory = globalAIManager.getFactory(aiName);
            if (factory != null) {
                container.ai().install(factory.getClass().getName());
                log.info("Installed AI '{}' into container '{}'", aiName, request.name());
            } else {
                log.warn("AI '{}' not found in global pool, skipping", aiName);
            }
        }

        return Response.status(Response.Status.CREATED)
                .entity(ContainerResponse.from(container))
                .build();
    }

    /**
     * Get all containers.
     */
    @GET
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<ContainerResponse> getAllContainers() {
        return containerManager.getAllContainers().stream()
                .map(ContainerResponse::from)
                .toList();
    }

    /**
     * Get a specific container by ID.
     */
    @GET
    @Path("/{containerId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getContainer(@PathParam("containerId") long containerId) {
        return containerManager.getContainer(containerId)
                .map(p -> Response.ok(ContainerResponse.from(p)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Delete a container. The container must be stopped first.
     */
    @DELETE
    @Path("/{containerId}")
    @RolesAllowed("admin")
    public Response deleteContainer(@PathParam("containerId") long containerId) {
        log.info("Deleting container: {}", containerId);
        containerManager.deleteContainer(containerId);
        return Response.noContent().build();
    }

    // =========================================================================
    // CONTAINER LIFECYCLE
    // =========================================================================

    /**
     * Start a container.
     */
    @POST
    @Path("/{containerId}/start")
    @RolesAllowed("admin")
    public Response startContainer(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.lifecycle().start();
        return Response.ok(ContainerResponse.from(container)).build();
    }

    /**
     * Stop a container.
     */
    @POST
    @Path("/{containerId}/stop")
    @RolesAllowed("admin")
    public Response stopContainer(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.lifecycle().stop();
        return Response.ok(ContainerResponse.from(container)).build();
    }

    /**
     * Pause a running container.
     */
    @POST
    @Path("/{containerId}/pause")
    @RolesAllowed("admin")
    public Response pauseContainer(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.lifecycle().pause();
        return Response.ok(ContainerResponse.from(container)).build();
    }

    /**
     * Resume a paused container.
     */
    @POST
    @Path("/{containerId}/resume")
    @RolesAllowed("admin")
    public Response resumeContainer(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.lifecycle().resume();
        return Response.ok(ContainerResponse.from(container)).build();
    }

    // =========================================================================
    // TICK CONTROL
    // =========================================================================

    /**
     * Get the current tick of a container.
     */
    @GET
    @Path("/{containerId}/tick")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public TickResponse getTick(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return new TickResponse(container.ticks().current());
    }

    /**
     * Advance the container by one tick.
     */
    @POST
    @Path("/{containerId}/tick")
    @RolesAllowed({"admin", "command_manager"})
    public TickResponse advanceTick(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.ticks().advance();
        return new TickResponse(container.ticks().current());
    }

    /**
     * Start auto-advancing the container at the specified interval.
     */
    @POST
    @Path("/{containerId}/play")
    @RolesAllowed({"admin", "command_manager"})
    public Response startAutoAdvance(
            @PathParam("containerId") long containerId,
            @QueryParam("intervalMs") @DefaultValue("16") long intervalMs) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.ticks().play(intervalMs);
        return Response.ok(ContainerResponse.from(container)).build();
    }

    /**
     * Stop auto-advancing the container.
     */
    @POST
    @Path("/{containerId}/stop-auto")
    @RolesAllowed({"admin", "command_manager"})
    public Response stopAutoAdvance(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.ticks().stop();
        return Response.ok(ContainerResponse.from(container)).build();
    }

    /**
     * Get the current play status of a container.
     */
    @GET
    @Path("/{containerId}/status")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public PlayStatusResponse getStatus(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return new PlayStatusResponse(
                container.ticks().isPlaying(),
                container.ticks().current(),
                container.ticks().interval()
        );
    }

    /**
     * Get container statistics including entity counts and memory usage.
     */
    @GET
    @Path("/{containerId}/stats")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getContainerStats(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        ExecutionContainer.ContainerStats stats = container.getStats();
        return Response.ok(new ContainerStatsResponse(
                stats.entityCount(),
                stats.maxEntities(),
                stats.usedMemoryBytes(),
                stats.maxMemoryBytes(),
                stats.jvmMaxMemoryBytes(),
                stats.jvmUsedMemoryBytes(),
                stats.matchCount(),
                stats.moduleCount()
        )).build();
    }

    /**
     * Container statistics response DTO.
     */
    public record ContainerStatsResponse(
            int entityCount,
            int maxEntities,
            long usedMemoryBytes,
            long maxMemoryBytes,
            long jvmMaxMemoryBytes,
            long jvmUsedMemoryBytes,
            int matchCount,
            int moduleCount
    ) {}

    // =========================================================================
    // MATCH MANAGEMENT WITHIN CONTAINER
    // =========================================================================

    /**
     * Get all matches in a container.
     */
    @GET
    @Path("/{containerId}/matches")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<MatchResponse> getMatches(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return container.matches().all().stream()
                .map(MatchResponse::from)
                .toList();
    }

    /**
     * Create a match in a container.
     */
    @POST
    @Path("/{containerId}/matches")
    @RolesAllowed("admin")
    public Response createMatch(
            @PathParam("containerId") long containerId,
            MatchRequest request) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        // Default null lists to empty lists
        List<String> modules = request.enabledModuleNames() != null
                ? request.enabledModuleNames()
                : List.of();
        List<String> ais = request.enabledAINames() != null
                ? request.enabledAINames()
                : List.of();
        Match match = new Match(
                request.id(),
                containerId,
                modules,
                ais
        );
        Match created = container.matches().create(match);
        return Response.status(Response.Status.CREATED)
                .entity(MatchResponse.from(created))
                .build();
    }

    /**
     * Get a specific match in a container.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return container.matches().get(matchId)
                .map(m -> Response.ok(MatchResponse.from(m)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Delete a match from a container.
     */
    @DELETE
    @Path("/{containerId}/matches/{matchId}")
    @RolesAllowed("admin")
    public Response deleteMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.matches().delete(matchId);
        return Response.noContent().build();
    }

    // =========================================================================
    // COMMAND EXECUTION
    // =========================================================================

    /**
     * Get all available commands in a container.
     * Commands are provided by the modules loaded in this container.
     */
    @GET
    @Path("/{containerId}/commands")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<CommandResponse> getCommands(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return container.getAvailableCommands().stream()
                .map(cmd -> new CommandResponse(
                        cmd.name(),
                        cmd.description(),
                        cmd.module(),
                        cmd.parameters() != null
                                ? cmd.parameters().stream()
                                        .map(p -> new ParameterResponse(p.name(), p.type(), p.required(), p.description()))
                                        .toList()
                                : List.of()
                ))
                .toList();
    }

    /**
     * Command response DTO.
     */
    public record CommandResponse(String name, String description, String module, List<ParameterResponse> parameters) {}

    /**
     * Parameter response DTO.
     */
    public record ParameterResponse(String name, String type, boolean required, String description) {}

    /**
     * Enqueue a command in a container.
     */
    @POST
    @Path("/{containerId}/commands")
    @RolesAllowed({"admin", "command_manager"})
    public Response enqueueCommand(
            @PathParam("containerId") long containerId,
            CommandRequest request) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        container.commands()
                .named(request.commandName())
                .withParams(request.parameters() != null ? request.parameters() : java.util.Map.of())
                .execute();
        return Response.accepted().build();
    }

    /**
     * Command request DTO.
     * <p>Note: Uses a Map for parameters since Jackson cannot deserialize interfaces.
     */
    public record CommandRequest(String commandName, java.util.Map<String, Object> parameters) {}

    // =========================================================================
    // MODULE MANAGEMENT
    // =========================================================================

    /**
     * Get all loaded modules in a container.
     */
    @GET
    @Path("/{containerId}/modules")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<String> getModules(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        return container.modules().available();
    }

    /**
     * Reload modules in a container.
     */
    @POST
    @Path("/{containerId}/modules/reload")
    @RolesAllowed("admin")
    public Response reloadModules(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        try {
            container.modules().reload();
        } catch (java.io.IOException e) {
            log.error("Failed to reload modules for container {}", containerId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to reload modules: " + e.getMessage()))
                    .build();
        }
        return Response.ok(container.modules().available()).build();
    }

    // =========================================================================
    // AI MANAGEMENT
    // =========================================================================

    /**
     * Get all available AI in a container.
     */
    @GET
    @Path("/{containerId}/ai")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getContainerAI(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        var aiOps = container.ai();
        if (aiOps == null) {
            return Response.ok(List.of()).build();
        }
        return Response.ok(aiOps.available()).build();
    }

    // =========================================================================
    // RESOURCE MANAGEMENT
    // =========================================================================

    /**
     * Get all resources in a container.
     */
    @GET
    @Path("/{containerId}/resources")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getContainerResources(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        var resourceOps = container.resources();
        if (resourceOps == null) {
            return Response.ok(List.of()).build();
        }
        List<Map<String, Object>> resources = resourceOps.all().stream()
                .map(r -> Map.<String, Object>of(
                        "resourceId", r.resourceId(),
                        "resourceName", r.resourceName(),
                        "resourceType", r.resourceType().name()
                ))
                .toList();
        return Response.ok(resources).build();
    }

    /**
     * Get a specific resource from a container.
     */
    @GET
    @Path("/{containerId}/resources/{resourceId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getContainerResource(
            @PathParam("containerId") long containerId,
            @PathParam("resourceId") long resourceId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        var resourceOps = container.resources();
        if (resourceOps == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Resource management not available for this container"))
                    .build();
        }
        return resourceOps.get(resourceId)
                .map(r -> Response.ok(Map.of(
                        "resourceId", r.resourceId(),
                        "resourceName", r.resourceName(),
                        "resourceType", r.resourceType().name()
                )).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Resource not found: " + resourceId))
                        .build());
    }

    /**
     * Delete a resource from a container.
     */
    @DELETE
    @Path("/{containerId}/resources/{resourceId}")
    @RolesAllowed("admin")
    public Response deleteContainerResource(
            @PathParam("containerId") long containerId,
            @PathParam("resourceId") long resourceId) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        var resourceOps = container.resources();
        if (resourceOps == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Resource management not available for this container"))
                    .build();
        }
        if (resourceOps.delete(resourceId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Resource not found: " + resourceId))
                .build();
    }

    /**
     * Upload a resource to a container.
     */
    @POST
    @Path("/{containerId}/resources")
    @Consumes(jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("admin")
    public Response uploadContainerResource(
            @PathParam("containerId") long containerId,
            @org.jboss.resteasy.reactive.RestForm("file") org.jboss.resteasy.reactive.multipart.FileUpload file,
            @org.jboss.resteasy.reactive.RestForm("resourceName") String resourceName,
            @org.jboss.resteasy.reactive.RestForm("resourceType") String resourceType) {
        ExecutionContainer container = getContainerOrThrow(containerId);
        var resourceOps = container.resources();
        if (resourceOps == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Resource management not available for this container"))
                    .build();
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.uploadedFile());
            ca.samanthaireland.engine.core.resources.ResourceType type =
                    ca.samanthaireland.engine.core.resources.ResourceType.valueOf(resourceType);
            var resource = resourceOps.upload()
                    .name(resourceName)
                    .type(type)
                    .data(data)
                    .execute();
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of(
                            "resourceId", resource.resourceId(),
                            "resourceName", resource.resourceName(),
                            "resourceType", resource.resourceType().name()
                    ))
                    .build();
        } catch (java.io.IOException e) {
            log.error("Failed to upload resource to container {}", containerId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to upload resource: " + e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid resource type: " + resourceType))
                    .build();
        }
    }

    // =========================================================================
    // SESSION MANAGEMENT WITHIN CONTAINER
    // =========================================================================

    /**
     * Connect a player to a match in this container.
     * Creates or reactivates a session.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/sessions")
    @RolesAllowed({"admin", "command_manager"})
    public Response connectSession(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            SessionConnectRequest request) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        // Create or reactivate session (skip match validation - already done above)
        PlayerSession session = sessionService.createSessionForContainer(request.playerId(), matchId);
        log.info("Created session for player {} in match {} (container {})", request.playerId(), matchId, containerId);

        return Response.status(Response.Status.CREATED)
                .entity(SessionResponse.from(session))
                .build();
    }

    /**
     * Get all sessions for a match in this container.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/sessions")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<SessionResponse> getMatchSessions(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        return sessionService.findMatchSessions(matchId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    /**
     * Session connect request DTO.
     */
    public record SessionConnectRequest(long playerId) {}

    /**
     * Reconnect a player's session in a match.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/sessions/{playerId}/reconnect")
    @RolesAllowed({"admin", "command_manager"})
    public Response reconnectSession(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        PlayerSession session = sessionService.reconnect(playerId, matchId);
        return Response.ok(SessionResponse.from(session)).build();
    }

    /**
     * Disconnect a player's session in a match.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/sessions/{playerId}/disconnect")
    @RolesAllowed({"admin", "command_manager"})
    public Response disconnectSession(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        sessionService.disconnect(playerId, matchId);
        return Response.noContent().build();
    }

    /**
     * Abandon a player's session in a match.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/sessions/{playerId}/abandon")
    @RolesAllowed({"admin", "command_manager"})
    public Response abandonSession(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        sessionService.abandon(playerId, matchId);
        return Response.noContent().build();
    }

    /**
     * Get a specific player's session in a match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/sessions/{playerId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getSession(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        return sessionService.findSession(playerId, matchId)
                .map(session -> Response.ok(SessionResponse.from(session)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Check if a player can reconnect to a match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/sessions/{playerId}/can-reconnect")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response canReconnect(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        boolean canReconnect = sessionService.canReconnect(playerId, matchId);
        return Response.ok(Map.of("canReconnect", canReconnect)).build();
    }

    /**
     * Get active sessions for a match in this container.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/sessions/active")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<SessionResponse> getActiveMatchSessions(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        return sessionService.findActiveMatchSessions(matchId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    /**
     * Get all sessions across all matches in a container.
     */
    @GET
    @Path("/{containerId}/sessions")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<SessionResponse> getAllContainerSessions(@PathParam("containerId") long containerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Get all sessions for matches in this container
        return container.matches().all().stream()
                .flatMap(match -> sessionService.findMatchSessions(match.id()).stream())
                .map(SessionResponse::from)
                .toList();
    }

    // =========================================================================
    // PLAYER MANAGEMENT
    // =========================================================================

    /**
     * Create a player.
     */
    @POST
    @Path("/{containerId}/players")
    @RolesAllowed("admin")
    public Response createPlayer(
            @PathParam("containerId") long containerId,
            PlayerRequest request) {
        getContainerOrThrow(containerId); // Validate container exists

        // Auto-generate ID if not provided
        long playerId = (request.id() != null && request.id() > 0)
                ? request.id()
                : System.currentTimeMillis();
        Player player = new Player(playerId);
        playerService.createPlayer(player);
        return Response.status(Response.Status.CREATED)
                .entity(new PlayerResponse(player.id()))
                .build();
    }

    /**
     * Get a player by ID.
     */
    @GET
    @Path("/{containerId}/players/{playerId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getPlayer(
            @PathParam("containerId") long containerId,
            @PathParam("playerId") long playerId) {
        getContainerOrThrow(containerId); // Validate container exists

        return playerService.getPlayer(playerId)
                .map(player -> Response.ok(new PlayerResponse(player.id())).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Get all players.
     */
    @GET
    @Path("/{containerId}/players")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<PlayerResponse> getAllPlayers(@PathParam("containerId") long containerId) {
        getContainerOrThrow(containerId); // Validate container exists

        return playerService.getAllPlayers().stream()
                .map(player -> new PlayerResponse(player.id()))
                .toList();
    }

    /**
     * Delete a player.
     */
    @DELETE
    @Path("/{containerId}/players/{playerId}")
    @RolesAllowed("admin")
    public Response deletePlayer(
            @PathParam("containerId") long containerId,
            @PathParam("playerId") long playerId) {
        getContainerOrThrow(containerId); // Validate container exists

        if (playerService.getPlayer(playerId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        playerService.deletePlayer(playerId);
        return Response.noContent().build();
    }

    // =========================================================================
    // PLAYER-MATCH MANAGEMENT
    // =========================================================================

    /**
     * Join a player to a match in this container.
     * Returns WebSocket and REST endpoint URLs for receiving player-scoped snapshots.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/players")
    @RolesAllowed("admin")
    public Response joinMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            PlayerMatchRequest request) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        PlayerMatch playerMatch = playerMatchService.joinMatchValidated(request.playerId(), matchId);
        JoinMatchResponse response = JoinMatchResponse.create(
                playerMatch.playerId(),
                playerMatch.matchId()
        );

        return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
    }

    /**
     * Player-match join request DTO.
     */
    public record PlayerMatchRequest(long playerId) {}

    /**
     * Get a player-match association.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/players/{playerId}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getPlayerMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        return playerMatchService.getPlayerMatch(playerId, matchId)
                .map(pm -> Response.ok(new PlayerMatchResponse(pm.playerId(), pm.matchId())).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Get all players in a match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/players")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public List<PlayerMatchResponse> getPlayersInMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        return playerMatchService.getPlayersInMatch(matchId).stream()
                .map(pm -> new PlayerMatchResponse(pm.playerId(), pm.matchId()))
                .toList();
    }

    /**
     * Remove a player from a match (leave match).
     */
    @DELETE
    @Path("/{containerId}/matches/{matchId}/players/{playerId}")
    @RolesAllowed("admin")
    public Response leaveMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("playerId") long playerId) {
        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (playerMatchService.getPlayerMatch(playerId, matchId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        playerMatchService.leaveMatch(playerId, matchId);
        return Response.noContent().build();
    }

    // =========================================================================
    // SNAPSHOT MANAGEMENT
    // =========================================================================

    /**
     * Get a snapshot for a match in a container.
     *
     * <p>Returns the current state of all entities in the match with their component data.
     * Optionally filter by player to get only entities owned by that player.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/snapshot")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getMatchSnapshot(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @QueryParam("playerId") Long playerId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        // Get container-scoped snapshot
        if (container.snapshots() == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Container not started - snapshots unavailable"))
                    .build();
        }

        Snapshot snapshot = playerId != null
                ? container.snapshots().forMatchAndPlayer(matchId, playerId)
                : container.snapshots().forMatch(matchId);

        return Response.ok(new SnapshotResponse(
                matchId,
                container.ticks().current(),
                snapshot.snapshot()
        )).build();
    }

    // =========================================================================
    // DELTA SNAPSHOT MANAGEMENT
    // =========================================================================

    /**
     * Get a delta snapshot between two ticks for a match.
     *
     * <p>If toTick is not specified, returns delta from fromTick to current state.
     * If fromTick is not in history, returns 404.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/snapshots/delta")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getDeltaSnapshot(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @QueryParam("fromTick") Long fromTick,
            @QueryParam("toTick") Long toTick) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        // fromTick is required
        if (fromTick == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "fromTick query parameter is required"))
                    .build();
        }

        // Get the from snapshot from history
        Optional<Snapshot> fromSnapshotOpt = snapshotHistory.getSnapshot(matchId, fromTick);
        if (fromSnapshotOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(
                            "error", "Snapshot not found for tick: " + fromTick,
                            "hint", "Use POST .../snapshots/record to record snapshots"))
                    .build();
        }

        // Determine the target tick and get/create the snapshot
        long targetTick;
        Snapshot toSnapshot;

        if (toTick != null) {
            targetTick = toTick;
            Optional<Snapshot> toSnapshotOpt = snapshotHistory.getSnapshot(matchId, toTick);
            if (toSnapshotOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Snapshot not found for tick: " + toTick))
                        .build();
            }
            toSnapshot = toSnapshotOpt.get();
        } else {
            // Use current state
            targetTick = container.ticks().current();
            toSnapshot = snapshotProvider.createForMatch(matchId);
        }

        // Compute delta
        Snapshot fromSnapshot = fromSnapshotOpt.get();
        DeltaSnapshot delta = deltaCompressionService.computeDelta(
                matchId, fromTick, fromSnapshot, targetTick, toSnapshot);

        // Calculate compression ratio
        double compressionRatio = calculateCompressionRatio(fromSnapshot, toSnapshot, delta);

        DeltaSnapshotResponse response = new DeltaSnapshotResponse(
                delta.matchId(),
                delta.fromTick(),
                delta.toTick(),
                delta.changedComponents(),
                delta.addedEntities(),
                delta.removedEntities(),
                delta.changeCount(),
                compressionRatio
        );

        return Response.ok(response).build();
    }

    /**
     * Record the current snapshot for a match at the current tick.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/snapshots/record")
    @RolesAllowed({"admin", "command_manager"})
    public Response recordSnapshot(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        long tick = container.ticks().current();
        Snapshot snapshot = snapshotProvider.createForMatch(matchId);
        snapshotHistory.recordSnapshot(matchId, tick, snapshot);

        return Response.ok(Map.of(
                "matchId", matchId,
                "tick", tick,
                "recorded", true,
                "historySize", snapshotHistory.getSnapshotCount(matchId)
        )).build();
    }

    /**
     * Get snapshot history info for a match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/snapshots/history-info")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getSnapshotHistoryInfo(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        var oldest = snapshotHistory.getOldestSnapshot(matchId);
        var latest = snapshotHistory.getLatestSnapshot(matchId);
        int count = snapshotHistory.getSnapshotCount(matchId);

        return Response.ok(Map.of(
                "matchId", matchId,
                "snapshotCount", count,
                "oldestTick", oldest.map(s -> s.tick()).orElse(-1L),
                "latestTick", latest.map(s -> s.tick()).orElse(-1L),
                "currentTick", container.ticks().current()
        )).build();
    }

    /**
     * Clear the snapshot history for a match.
     */
    @DELETE
    @Path("/{containerId}/matches/{matchId}/snapshots/history")
    @RolesAllowed("admin")
    public Response clearSnapshotHistory(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        snapshotHistory.clearHistory(matchId);
        return Response.ok(Map.of(
                "matchId", matchId,
                "cleared", true
        )).build();
    }

    // =========================================================================
    // MONGODB PERSISTED HISTORY
    // =========================================================================

    /**
     * Get overall MongoDB history summary.
     */
    @GET
    @Path("/{containerId}/history")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getHistorySummary(@PathParam("containerId") long containerId) {
        getContainerOrThrow(containerId); // Validate container exists

        if (!persistenceConfig.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of(
                            "error", "Snapshot persistence is not enabled",
                            "hint", "Set snapshot.persistence.enabled=true in configuration"))
                    .build();
        }

        SnapshotHistoryService.HistorySummary summary = historyService.getSummary();
        return Response.ok(Map.of(
                "totalSnapshots", summary.totalSnapshots(),
                "matchCount", summary.matchCount(),
                "matchIds", summary.matchIds(),
                "database", persistenceConfig.database(),
                "collection", persistenceConfig.collection(),
                "tickInterval", persistenceConfig.tickInterval()
        )).build();
    }

    /**
     * Get MongoDB history summary for a specific match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/history")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getMatchHistorySummary(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        SnapshotHistoryService.MatchHistorySummary summary = historyService.getMatchSummary(matchId);
        return Response.ok(Map.of(
                "matchId", summary.matchId(),
                "snapshotCount", summary.snapshotCount(),
                "firstTick", summary.firstTick() != null ? summary.firstTick() : -1,
                "lastTick", summary.lastTick() != null ? summary.lastTick() : -1,
                "firstTimestamp", summary.firstTimestamp() != null ? summary.firstTimestamp().toString() : null,
                "lastTimestamp", summary.lastTimestamp() != null ? summary.lastTimestamp().toString() : null
        )).build();
    }

    /**
     * Get persisted snapshots for a match within a tick range.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/history/snapshots")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getHistorySnapshots(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @QueryParam("fromTick") @DefaultValue("0") long fromTick,
            @QueryParam("toTick") @DefaultValue("9223372036854775807") long toTick,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        if (limit > 1000) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Limit cannot exceed 1000"))
                    .build();
        }

        List<SnapshotDocument> snapshots = historyService.getSnapshotsInRange(matchId, fromTick, toTick, limit);
        return Response.ok(Map.of(
                "matchId", matchId,
                "fromTick", fromTick,
                "toTick", toTick,
                "count", snapshots.size(),
                "snapshots", snapshots.stream().map(this::toHistoryDto).toList()
        )).build();
    }

    /**
     * Get the latest persisted snapshots for a match.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/history/snapshots/latest")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getLatestHistorySnapshots(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @QueryParam("limit") @DefaultValue("10") int limit) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        if (limit > 100) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Limit cannot exceed 100"))
                    .build();
        }

        List<SnapshotDocument> snapshots = historyService.getLatestSnapshots(matchId, limit);
        return Response.ok(Map.of(
                "matchId", matchId,
                "count", snapshots.size(),
                "snapshots", snapshots.stream().map(this::toHistoryDto).toList()
        )).build();
    }

    /**
     * Get a specific persisted snapshot by tick.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/history/snapshots/{tick}")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getHistorySnapshot(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("tick") long tick) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        Optional<SnapshotDocument> snapshot = historyService.getSnapshot(matchId, tick);
        if (snapshot.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Snapshot not found for match " + matchId + " at tick " + tick))
                    .build();
        }

        return Response.ok(toHistoryDto(snapshot.get())).build();
    }

    /**
     * Delete all persisted snapshots for a match.
     */
    @DELETE
    @Path("/{containerId}/matches/{matchId}/history")
    @RolesAllowed("admin")
    public Response deleteMatchHistory(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        long deleted = historyService.deleteSnapshots(matchId);
        return Response.ok(Map.of(
                "matchId", matchId,
                "deletedCount", deleted
        )).build();
    }

    /**
     * Delete persisted snapshots older than a specific tick.
     */
    @DELETE
    @Path("/{containerId}/matches/{matchId}/history/older-than/{tick}")
    @RolesAllowed("admin")
    public Response deleteOlderHistorySnapshots(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @PathParam("tick") long olderThanTick) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return persistenceNotEnabled();
        }

        long deleted = historyService.deleteSnapshotsOlderThan(matchId, olderThanTick);
        return Response.ok(Map.of(
                "matchId", matchId,
                "olderThanTick", olderThanTick,
                "deletedCount", deleted
        )).build();
    }

    // =========================================================================
    // STATE RESTORATION
    // =========================================================================

    /**
     * Restore a match from its persisted snapshot.
     */
    @POST
    @Path("/{containerId}/matches/{matchId}/restore")
    @RolesAllowed("admin")
    public Response restoreMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId,
            @QueryParam("tick") @DefaultValue("-1") long tick) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Snapshot persistence is not enabled"))
                    .build();
        }

        if (!restoreConfig.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Snapshot restoration is not enabled"))
                    .build();
        }

        RestoreResult result = restoreService.restoreMatch(matchId, tick);

        if (result.success()) {
            return Response.ok(result).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(result)
                    .build();
        }
    }

    /**
     * Restore all matches in a container from their persisted snapshots.
     */
    @POST
    @Path("/{containerId}/restore/all")
    @RolesAllowed("admin")
    public Response restoreAllMatches(@PathParam("containerId") long containerId) {
        getContainerOrThrow(containerId); // Validate container exists

        if (!persistenceConfig.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Snapshot persistence is not enabled"))
                    .build();
        }

        if (!restoreConfig.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Snapshot restoration is not enabled"))
                    .build();
        }

        List<RestoreResult> results = restoreService.restoreAllMatches();

        long successCount = results.stream().filter(RestoreResult::success).count();
        long failedCount = results.stream().filter(r -> !r.success()).count();

        return Response.ok(Map.of(
                "total", results.size(),
                "success", successCount,
                "failed", failedCount,
                "results", results
        )).build();
    }

    /**
     * Check if a match can be restored.
     */
    @GET
    @Path("/{containerId}/matches/{matchId}/restore/available")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response canRestoreMatch(
            @PathParam("containerId") long containerId,
            @PathParam("matchId") long matchId) {

        ExecutionContainer container = getContainerOrThrow(containerId);

        // Verify match exists in this container
        container.matches().get(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match " + matchId + " not found in container " + containerId));

        if (!persistenceConfig.enabled()) {
            return Response.ok(Map.of(
                    "matchId", matchId,
                    "canRestore", false,
                    "reason", "Snapshot persistence is not enabled"
            )).build();
        }

        boolean canRestore = restoreService.canRestore(matchId);
        return Response.ok(Map.of(
                "matchId", matchId,
                "canRestore", canRestore
        )).build();
    }

    /**
     * Get the current restoration configuration.
     */
    @GET
    @Path("/{containerId}/restore/config")
    @RolesAllowed({"admin", "command_manager", "view_only"})
    public Response getRestoreConfig(@PathParam("containerId") long containerId) {
        getContainerOrThrow(containerId); // Validate container exists

        return Response.ok(Map.of(
                "persistenceEnabled", persistenceConfig.enabled(),
                "restoreEnabled", restoreConfig.enabled(),
                "autoRestoreOnStartup", restoreConfig.autoRestoreOnStartup()
        )).build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private ExecutionContainer getContainerOrThrow(long containerId) {
        return containerManager.getContainer(containerId)
                .orElseThrow(() -> new EntityNotFoundException("Container not found: " + containerId));
    }

    private Response persistenceNotEnabled() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of(
                        "error", "Snapshot persistence is not enabled",
                        "hint", "Set snapshot.persistence.enabled=true in configuration"))
                .build();
    }

    private Map<String, Object> toHistoryDto(SnapshotDocument doc) {
        return Map.of(
                "matchId", doc.matchId(),
                "tick", doc.tick(),
                "timestamp", doc.timestamp() != null ? doc.timestamp().toString() : null,
                "data", doc.data()
        );
    }

    /**
     * Calculate the compression ratio of the delta vs full snapshot.
     * Lower values indicate better compression.
     */
    private double calculateCompressionRatio(Snapshot from, Snapshot to, DeltaSnapshot delta) {
        // Estimate full snapshot size by counting all values
        int fullSnapshotSize = countValues(to);
        if (fullSnapshotSize == 0) {
            return 1.0;
        }

        // Delta size is the number of changed values plus entity changes
        int deltaSize = delta.changeCount()
                + (delta.addedEntities() != null ? delta.addedEntities().size() : 0)
                + (delta.removedEntities() != null ? delta.removedEntities().size() : 0);

        return (double) deltaSize / fullSnapshotSize;
    }

    private int countValues(Snapshot snapshot) {
        if (snapshot == null || snapshot.snapshot() == null) {
            return 0;
        }
        return snapshot.snapshot().values().stream()
                .flatMap(moduleData -> moduleData.values().stream())
                .mapToInt(List::size)
                .sum();
    }
}
