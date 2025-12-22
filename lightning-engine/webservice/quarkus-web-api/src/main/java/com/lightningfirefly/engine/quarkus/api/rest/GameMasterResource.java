package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.internal.ext.gamemaster.GameMasterManager;
import com.lightningfirefly.engine.quarkus.api.dto.GameMasterResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * REST resource for managing game masters.
 *
 * <p>Provides endpoints to:
 * <ul>
 *   <li>Upload JAR game masters</li>
 *   <li>List available game masters</li>
 *   <li>Get game master details</li>
 *   <li>Delete/uninstall game masters</li>
 * </ul>
 */
@Slf4j
@Path("/api/gamemasters")
@Produces(MediaType.APPLICATION_JSON)
public class GameMasterResource {

    @Inject
    GameMasterManager gameMasterManager;

    @Inject
    GameSimulation gameSimulation;

    /**
     * Get all available game masters.
     *
     * @return list of game master information
     */
    @GET
    public List<GameMasterResponse> getAllGameMasters() {
        return gameMasterManager.getAvailableGameMasters().stream()
                .map(this::toGameMasterResponse)
                .toList();
    }

    /**
     * Get a specific game master by name.
     *
     * @param gameMasterName the game master name
     * @return the game master information
     */
    @GET
    @Path("/{gameMasterName}")
    public Response getGameMaster(@PathParam("gameMasterName") String gameMasterName) {
        if (!gameMasterManager.hasGameMaster(gameMasterName)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Game master not found: " + gameMasterName + "\"}")
                    .build();
        }

        return Response.ok(toGameMasterResponse(gameMasterName)).build();
    }

    /**
     * Upload a JAR game master.
     *
     * <p>The JAR file will be copied to the game masters directory and loaded.
     *
     * @param file the JAR file to upload
     * @return the newly installed game master information
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadGameMaster(@RestForm("file") FileUpload file) {
        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"No file provided\"}")
                    .build();
        }

        String fileName = file.fileName();
        if (fileName == null || !fileName.endsWith(".jar")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"File must be a JAR file\"}")
                    .build();
        }

        try {
            java.nio.file.Path uploadedPath = file.uploadedFile();
            java.nio.file.Path tempJar = Files.createTempFile("gamemaster-", ".jar");
            Files.copy(uploadedPath, tempJar, StandardCopyOption.REPLACE_EXISTING);

            gameMasterManager.installGameMaster(tempJar);

            Files.deleteIfExists(tempJar);

            log.info("Successfully uploaded game master: {}", fileName);

            return Response.status(Response.Status.CREATED)
                    .entity(getAllGameMasters())
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload game master: {}", fileName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to upload game master: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Uninstall a game master by name.
     *
     * <p>This removes the game master from the active game masters list.
     * Note: The JAR file is not deleted from disk.
     *
     * @param gameMasterName the game master name to uninstall
     * @return success status
     */
    @DELETE
    @Path("/{gameMasterName}")
    public Response deleteGameMaster(@PathParam("gameMasterName") String gameMasterName) {
        boolean removed = gameMasterManager.uninstallGameMaster(gameMasterName);

        if (removed) {
            log.info("Uninstalled game master: {}", gameMasterName);
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Game master not found: " + gameMasterName + "\"}")
                    .build();
        }
    }

    /**
     * Reload all game masters from disk.
     *
     * @return list of all available game masters after reload
     */
    @POST
    @Path("/reload")
    public Response reloadGameMasters() {
        try {
            gameMasterManager.reset();
            gameMasterManager.reloadInstalled();
            log.info("Reloaded all game masters");
            return Response.ok(getAllGameMasters()).build();
        } catch (IOException e) {
            log.error("Failed to reload game masters", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to reload game masters: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    private GameMasterResponse toGameMasterResponse(String gameMasterName) {
        int enabledMatches = countEnabledMatches(gameMasterName);
        return new GameMasterResponse(gameMasterName, enabledMatches);
    }

    private int countEnabledMatches(String gameMasterName) {
        return (int) gameSimulation.getAllMatches().stream()
                .filter(match -> isGameMasterEnabledInMatch(match, gameMasterName))
                .count();
    }

    private boolean isGameMasterEnabledInMatch(Match match, String gameMasterName) {
        if (match.enabledGameMasters() == null) {
            return false;
        }
        return match.enabledGameMasters().contains(gameMasterName);
    }
}
