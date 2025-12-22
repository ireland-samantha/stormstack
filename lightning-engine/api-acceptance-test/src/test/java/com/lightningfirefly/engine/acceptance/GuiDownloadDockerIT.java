package com.lightningfirefly.engine.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GUI download endpoint using Docker container.
 *
 * <p>These tests verify that the GUI JAR is properly packaged and served
 * from a Docker container, matching production deployment.
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>Docker must be running</li>
 *   <li>The Dockerfile must be present at project root</li>
 * </ul>
 *
 * <p>Note: These tests are slow as they build the Docker image.
 * They are skipped by default in unit test phase (use -DskipITs=false to run).
 */
@Testcontainers
@EnabledIf("isDockerAvailable")
class GuiDownloadDockerIT {

    private static final int CONTAINER_PORT = 8080;
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static GenericContainer<?> container;
    private static HttpClient httpClient;

    @BeforeAll
    static void setup() {
        // Build Docker image from project root
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent().getParent().getParent();
        Path dockerfile = projectRoot.resolve("Dockerfile");

        if (!dockerfile.toFile().exists()) {
            throw new IllegalStateException("Dockerfile not found at: " + dockerfile);
        }

        container = new GenericContainer<>(
            new ImageFromDockerfile("lightning-backend:latest", false)
                .withDockerfile(dockerfile)
        )
            .withExposedPorts(CONTAINER_PORT)
            .waitingFor(Wait.forHttp("/api/simulation/tick")
                .forPort(CONTAINER_PORT)
                .withStartupTimeout(STARTUP_TIMEOUT));

        container.start();

        httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    @AfterAll
    static void teardown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void downloadGui_shouldReturnZipWithGuiJar() throws Exception {
        String baseUrl = getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/gui/download"))
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/zip");

        // Verify ZIP contents
        byte[] zipData = response.body();
        assertThat(zipData.length).isGreaterThan(1000); // ZIP with JAR should be substantial

        boolean hasJar = false;
        boolean hasProperties = false;
        boolean hasReadme = false;
        long jarSize = 0;
        Properties serverProps = new Properties();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("lightning-gui.jar")) {
                    hasJar = true;
                    // Read the JAR to get its size
                    jarSize = readEntrySize(zis);
                } else if (name.equals("server.properties")) {
                    hasProperties = true;
                    serverProps.load(zis);
                } else if (name.equals("README.txt")) {
                    hasReadme = true;
                }
                zis.closeEntry();
            }
        }

        assertThat(hasJar).as("ZIP should contain lightning-gui.jar").isTrue();
        assertThat(hasProperties).as("ZIP should contain server.properties").isTrue();
        assertThat(hasReadme).as("ZIP should contain README.txt").isTrue();
        assertThat(jarSize).as("JAR should be larger than 1KB").isGreaterThan(1000);

        // Verify server.properties contains the container's URL
        String serverUrl = serverProps.getProperty("server.url");
        assertThat(serverUrl).isNotNull();
        assertThat(serverUrl).startsWith("http://");
    }

    @Test
    void downloadJarOnly_shouldReturnJar() throws Exception {
        String baseUrl = getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/gui/download/jar"))
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/octet-stream");

        // Verify it's a JAR file (ZIP format)
        byte[] jarData = response.body();
        assertThat(jarData.length).isGreaterThan(1000);
        assertThat(jarData[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(jarData[1]).isEqualTo((byte) 0x4B); // 'K'
    }

    @Test
    void getGuiInfo_shouldShowAvailable() throws Exception {
        String baseUrl = getBaseUrl();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/gui/info"))
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("\"available\":true");
        assertThat(body).contains("\"jarSizeBytes\":");
        assertThat(body).contains("lightning-gui.zip");
    }

    private String getBaseUrl() {
        return "http://" + container.getHost() + ":" + container.getMappedPort(CONTAINER_PORT);
    }

    private long readEntrySize(ZipInputStream zis) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            total += read;
        }
        return total;
    }

    static boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
