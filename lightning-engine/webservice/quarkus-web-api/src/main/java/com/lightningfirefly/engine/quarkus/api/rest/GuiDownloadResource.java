package com.lightningfirefly.engine.quarkus.api.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * REST resource for downloading the GUI client.
 *
 * <p>The GUI client is served as a ZIP containing:
 * <ul>
 *   <li>lightning-gui.jar - The GUI application</li>
 *   <li>server.properties - Auto-configuration with server URL</li>
 * </ul>
 *
 * <p>This enables one-click download and run without needing to specify the server URL.
 */
@Path("/api/gui")
@Slf4j
public class GuiDownloadResource {

    private static final String GUI_JAR_NAME = "lightning-gui.jar";
    private static final String GUI_ZIP_NAME = "lightning-gui.zip";
    private static final String GUI_JAR_CLASSPATH = "/gui/" + GUI_JAR_NAME;
    private static final String SERVER_PROPERTIES_NAME = "server.properties";

    @ConfigProperty(name = "gui.jar.path", defaultValue = "")
    Optional<String> guiJarPath;

    @Context
    UriInfo uriInfo;

    /**
     * Download the GUI client as a ZIP with auto-configuration.
     *
     * <p>The ZIP contains:
     * <ul>
     *   <li>lightning-gui.jar - The GUI application</li>
     *   <li>server.properties - Pre-configured with this server's URL</li>
     * </ul>
     *
     * <p>Example usage:
     * <pre>
     * curl -O http://server:8080/api/gui/download
     * unzip lightning-gui.zip
     * java -XstartOnFirstThread -jar lightning-gui.jar
     * </pre>
     *
     * <p>The GUI will automatically connect to the server it was downloaded from.
     */
    @GET
    @Path("/download")
    @Produces("application/zip")
    public Response downloadGui() {
        byte[] jarData = loadGuiJar();
        if (jarData == null) {
            log.warn("GUI JAR not available. Configure gui.jar.path or include in classpath.");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("GUI client JAR not available. Contact administrator.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        // Determine server URL from request
        String serverUrl = getServerUrl();

        // Create ZIP with JAR and config
        try {
            byte[] zipData = createGuiZip(jarData, serverUrl);
            log.info("Serving GUI ZIP with auto-config for server: {}, size: {} bytes", serverUrl, zipData.length);

            return Response.ok(zipData)
                    .header("Content-Disposition", "attachment; filename=\"" + GUI_ZIP_NAME + "\"")
                    .header("Content-Length", zipData.length)
                    .build();
        } catch (IOException e) {
            log.error("Failed to create GUI ZIP", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to create GUI package")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    /**
     * Download just the JAR without auto-configuration.
     */
    @GET
    @Path("/download/jar")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadJarOnly() {
        byte[] jarData = loadGuiJar();
        if (jarData == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("GUI client JAR not available.")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        return Response.ok(jarData)
                .header("Content-Disposition", "attachment; filename=\"" + GUI_JAR_NAME + "\"")
                .header("Content-Length", jarData.length)
                .build();
    }

    /**
     * Get information about the GUI client.
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGuiInfo() {
        boolean available = isGuiAvailable();
        long size = getGuiSize();
        String serverUrl = getServerUrl();

        return Response.ok(new GuiInfo(
                available,
                size,
                GUI_ZIP_NAME,
                "/api/gui/download",
                serverUrl,
                "Download, unzip, and run: java -XstartOnFirstThread -jar " + GUI_JAR_NAME
        )).build();
    }

    private byte[] loadGuiJar() {
        // Try configured file path first
        if (guiJarPath.isPresent() && !guiJarPath.get().isBlank()) {
            java.nio.file.Path jarPath = Paths.get(guiJarPath.get());
            if (Files.exists(jarPath)) {
                try {
                    log.info("Loading GUI JAR from configured path: {}", jarPath.toAbsolutePath());
                    return Files.readAllBytes(jarPath);
                } catch (IOException e) {
                    log.error("Failed to read GUI JAR from path: {}", jarPath, e);
                }
            } else {
                log.debug("Configured GUI JAR path does not exist: {}", jarPath.toAbsolutePath());
            }
        }

        // Try common development paths (relative to quarkus-web-api or project root)
        String[] devPaths = {
            "../../gui/target/engine-gui-0.0.1-SNAPSHOT.jar",  // From quarkus-web-api
            "../gui/target/engine-gui-0.0.1-SNAPSHOT.jar",
            "lightning-engine/gui/target/engine-gui-0.0.1-SNAPSHOT.jar",  // From project root
            "../lightning-engine/gui/target/engine-gui-0.0.1-SNAPSHOT.jar",
            "target/gui/lightning-gui.jar"
        };

        for (String devPath : devPaths) {
            java.nio.file.Path path = Paths.get(devPath);
            if (Files.exists(path)) {
                try {
                    log.info("Loading GUI JAR from dev path: {}", path.toAbsolutePath());
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    log.debug("Failed to read from dev path: {}", devPath);
                }
            }
        }

        // Try classpath
        try (InputStream is = getClass().getResourceAsStream(GUI_JAR_CLASSPATH)) {
            if (is != null) {
                log.info("Loading GUI JAR from classpath");
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.error("Failed to read GUI JAR from classpath", e);
        }

        log.warn("GUI JAR not found. Searched paths: configured={}, dev paths, classpath",
                guiJarPath.orElse("not set"));
        return null;
    }

    private byte[] createGuiZip(byte[] jarData, String serverUrl) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add JAR
            ZipEntry jarEntry = new ZipEntry(GUI_JAR_NAME);
            zos.putNextEntry(jarEntry);
            zos.write(jarData);
            zos.closeEntry();

            // Add server.properties
            String propsContent = createServerProperties(serverUrl);
            ZipEntry propsEntry = new ZipEntry(SERVER_PROPERTIES_NAME);
            zos.putNextEntry(propsEntry);
            zos.write(propsContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add README
            String readmeContent = createReadme(serverUrl);
            ZipEntry readmeEntry = new ZipEntry("README.txt");
            zos.putNextEntry(readmeEntry);
            zos.write(readmeContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String createServerProperties(String serverUrl) {
        return """
                # Lightning Engine GUI Configuration
                # Auto-generated for server: %s
                # Place this file in the same directory as the JAR

                server.url=%s
                """.formatted(serverUrl, serverUrl);
    }

    private String createReadme(String serverUrl) {
        return """
                Lightning Engine GUI Client
                ===========================

                This GUI client is pre-configured to connect to:
                %s

                To run:
                  macOS:   java -XstartOnFirstThread -jar %s
                  Linux:   java -jar %s
                  Windows: java -jar %s

                The -XstartOnFirstThread flag is required on macOS for OpenGL.

                To connect to a different server:
                  java -XstartOnFirstThread -jar %s -s http://other-server:8080

                Or edit server.properties to change the default server URL.
                """.formatted(serverUrl, GUI_JAR_NAME, GUI_JAR_NAME, GUI_JAR_NAME, GUI_JAR_NAME);
    }

    private String getServerUrl() {
        // Build URL from request info
        String scheme = uriInfo.getBaseUri().getScheme();
        String host = uriInfo.getBaseUri().getHost();
        int port = uriInfo.getBaseUri().getPort();

        if (port == -1 || port == 80 || port == 443) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private boolean isGuiAvailable() {
        return findGuiJarPath() != null;
    }

    private long getGuiSize() {
        java.nio.file.Path path = findGuiJarPath();
        if (path != null) {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return -1;
            }
        }
        return -1;
    }

    private java.nio.file.Path findGuiJarPath() {
        // Try configured path
        if (guiJarPath.isPresent() && !guiJarPath.get().isBlank()) {
            java.nio.file.Path path = Paths.get(guiJarPath.get());
            if (Files.exists(path)) {
                return path;
            }
        }

        // Try common development paths (relative to quarkus-web-api or project root)
        String[] devPaths = {
            "../../gui/target/engine-gui-0.0.1-SNAPSHOT.jar",  // From quarkus-web-api
            "../gui/target/engine-gui-0.0.1-SNAPSHOT.jar",
            "lightning-engine/gui/target/engine-gui-0.0.1-SNAPSHOT.jar",  // From project root
            "../lightning-engine/gui/target/engine-gui-0.0.1-SNAPSHOT.jar",
            "target/gui/lightning-gui.jar"
        };

        for (String devPath : devPaths) {
            java.nio.file.Path path = Paths.get(devPath);
            if (Files.exists(path)) {
                return path;
            }
        }

        // Classpath check (can't return path, but indicates availability)
        if (getClass().getResource(GUI_JAR_CLASSPATH) != null) {
            return Paths.get("classpath:" + GUI_JAR_CLASSPATH); // Placeholder
        }

        return null;
    }

    public record GuiInfo(
            boolean available,
            long jarSizeBytes,
            String fileName,
            String downloadUrl,
            String configuredServerUrl,
            String usageHint
    ) {}
}
