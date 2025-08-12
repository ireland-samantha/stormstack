package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests for the GUI download endpoint.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>ZIP download with auto-configuration</li>
 *   <li>JAR-only download</li>
 *   <li>Info endpoint</li>
 *   <li>Proper handling when GUI JAR is not available</li>
 * </ul>
 */
@QuarkusTest
class GuiDownloadResourceTest {

    @Test
    void getGuiInfo_shouldReturnInfo() {
        given()
            .when().get("/api/gui/info")
            .then()
                .statusCode(200)
                .body("fileName", is("lightning-gui.zip"))
                .body("downloadUrl", is("/api/gui/download"))
                .body("usageHint", notNullValue())
                .body("configuredServerUrl", notNullValue());
    }

    @Test
    void downloadGui_whenJarAvailable_shouldReturnZipWithConfig() throws IOException {
        Response response = given()
            .when().get("/api/gui/download")
            .then()
                .extract().response();

        // The test may return 404 if GUI JAR is not built
        if (response.statusCode() == 404) {
            // Expected in CI or when GUI module hasn't been built
            assertThat(response.getBody().asString()).contains("not available");
            return;
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/zip");
        assertThat(response.header("Content-Disposition")).contains("lightning-gui.zip");

        // Verify ZIP contents
        byte[] zipData = response.asByteArray();
        assertThat(zipData.length).isGreaterThan(0);

        boolean hasJar = false;
        boolean hasProperties = false;
        boolean hasReadme = false;
        Properties serverProps = new Properties();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("lightning-gui.jar")) {
                    hasJar = true;
                    // Verify it's a JAR (starts with PK zip header)
                    byte[] jarStart = new byte[4];
                    zis.read(jarStart);
                    assertThat(jarStart[0]).isEqualTo((byte) 0x50); // 'P'
                    assertThat(jarStart[1]).isEqualTo((byte) 0x4B); // 'K'
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

        // Verify server.properties has server URL
        String serverUrl = serverProps.getProperty("server.url");
        assertThat(serverUrl).isNotNull();
        assertThat(serverUrl).startsWith("http://");
    }

    @Test
    void downloadJarOnly_whenJarAvailable_shouldReturnJar() {
        Response response = given()
            .when().get("/api/gui/download/jar")
            .then()
                .extract().response();

        // The test may return 404 if GUI JAR is not built
        if (response.statusCode() == 404) {
            assertThat(response.getBody().asString()).contains("not available");
            return;
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/octet-stream");
        assertThat(response.header("Content-Disposition")).contains("lightning-gui.jar");

        // Verify it's a JAR file (ZIP format)
        byte[] jarData = response.asByteArray();
        assertThat(jarData.length).isGreaterThan(0);
        assertThat(jarData[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(jarData[1]).isEqualTo((byte) 0x4B); // 'K'
    }

    @Test
    void getGuiInfo_shouldShowAvailability() {
        Response response = given()
            .when().get("/api/gui/info")
            .then()
                .statusCode(200)
                .extract().response();

        boolean available = response.jsonPath().getBoolean("available");
        long size = response.jsonPath().getLong("jarSizeBytes");

        // If JAR is available, size should be positive
        if (available) {
            assertThat(size).isGreaterThan(0);
        }
    }
}
