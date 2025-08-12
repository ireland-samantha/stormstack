package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.GameSimulation;
import com.lightningfirefly.engine.core.match.Match;
import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.ext.module.EngineModule;
import com.lightningfirefly.engine.internal.ext.module.ModuleManagementModuleImpl;
import com.lightningfirefly.engine.internal.ext.module.ModuleManager;
import com.lightningfirefly.engine.quarkus.api.dto.ModuleResponse;
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
 * REST resource for managing simulation modules.
 *
 * <p>Provides endpoints to:
 * <ul>
 *   <li>Upload JAR modules</li>
 *   <li>List available modules</li>
 *   <li>Get module details</li>
 *   <li>Delete/uninstall modules</li>
 * </ul>
 */
@Slf4j
@Path("/api/modules")
@Produces(MediaType.APPLICATION_JSON)
public class ModuleResource {

    @Inject
    ModuleManager moduleManager;

    @Inject
    GameSimulation gameSimulation;

    /**
     * Get all available modules.
     *
     * @return list of module information
     */
    @GET
    public List<ModuleResponse> getAllModules() {
        return moduleManager.getAvailableModules().stream()
                .map(this::toModuleResponse)
                .toList();
    }

    /**
     * Get a specific module by name.
     *
     * @param moduleName the module name
     * @return the module information
     */
    @GET
    @Path("/{moduleName}")
    public Response getModule(@PathParam("moduleName") String moduleName) {
        if (!moduleManager.hasModule(moduleName)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Module not found: " + moduleName + "\"}")
                    .build();
        }

        return Response.ok(toModuleResponse(moduleName)).build();
    }

    /**
     * Upload a JAR module.
     *
     * <p>The JAR file will be copied to the modules directory and loaded.
     *
     * @param file the JAR file to upload
     * @return the newly installed module information
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadModule(@RestForm("file") FileUpload file) {
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
            // Get the uploaded file path
            java.nio.file.Path uploadedPath = file.uploadedFile();

            // Create a temp file with .jar extension (required by module manager)
            java.nio.file.Path tempJar = Files.createTempFile("module-", ".jar");
            Files.copy(uploadedPath, tempJar, StandardCopyOption.REPLACE_EXISTING);

            // Install the module
            moduleManager.installModule(tempJar);

            // Clean up temp file
            Files.deleteIfExists(tempJar);

            log.info("Successfully uploaded module: {}", fileName);

            // Return the list of newly available modules
            return Response.status(Response.Status.CREATED)
                    .entity(getAllModules())
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload module: {}", fileName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to upload module: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Uninstall a module by name.
     *
     * <p>This removes the module from the active modules list.
     * Note: The JAR file is not deleted from disk.
     *
     * @param moduleName the module name to uninstall
     * @return success status
     */
    @DELETE
    @Path("/{moduleName}")
    public Response deleteModule(@PathParam("moduleName") String moduleName) {
        boolean removed = moduleManager.uninstallModule(moduleName);

        if (removed) {
            log.info("Uninstalled module: {}", moduleName);
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Module not found: " + moduleName + "\"}")
                    .build();
        }
    }

    /**
     * Reload all modules from disk.
     *
     * @return list of all available modules after reload
     */
    @POST
    @Path("/reload")
    public Response reloadModules() {
        try {
            moduleManager.reset();
            // Re-register built-in modules that are not loaded from JAR files
            moduleManager.installModule(ModuleManagementModuleImpl.class);
            moduleManager.reloadInstalled();
            log.info("Reloaded all modules");
            return Response.ok(getAllModules()).build();
        } catch (IOException e) {
            log.error("Failed to reload modules", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Failed to reload modules: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    private ModuleResponse toModuleResponse(String moduleName) {
        EngineModule module = moduleManager.resolveModule(moduleName);
        String flagComponentName = null;
        if (module != null) {
            BaseComponent flag = module.createFlagComponent();
            if (flag != null) {
                flagComponentName = flag.getName();
            }
        }

        // Count matches where this module is enabled
        int enabledMatches = countEnabledMatches(moduleName);

        return new ModuleResponse(moduleName, flagComponentName, enabledMatches);
    }

    private int countEnabledMatches(String moduleName) {
        return (int) gameSimulation.getAllMatches().stream()
                .filter(match -> isModuleEnabledInMatch(match, moduleName))
                .count();
    }

    private boolean isModuleEnabledInMatch(Match match, String moduleName) {
        if (match.enabledModules() == null) {
            return false;
        }

        EngineModule targetModule = moduleManager.resolveModule(moduleName);
        if (targetModule == null) {
            return false;
        }

        return match.enabledModules().stream()
                .anyMatch(m -> m.equals(targetModule.getName()));
    }
}
