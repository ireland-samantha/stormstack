package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class EngineModuleResourceTest {

    @Test
    void getAllModules_shouldReturnList() {
        given()
            .when().get("/api/modules")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void getModule_notFound_shouldReturn404() {
        given()
            .when().get("/api/modules/NonExistentModule")
            .then()
                .statusCode(404)
                .body("error", containsString("not found"));
    }

    @Test
    void reloadModules_shouldReturnModuleList() {
        given()
            .when().post("/api/modules/reload")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void uploadModule_withoutFile_shouldReturn400() {
        given()
            .contentType("multipart/form-data")
            .when().post("/api/modules/upload")
            .then()
                .statusCode(400);
    }

    @Test
    void uploadModule_withNonJarFile_shouldReturn400() throws IOException {
        // Create a temporary non-JAR file
        Path tempFile = Files.createTempFile("test", ".txt");
        Files.writeString(tempFile, "not a jar file");

        try {
            given()
                .multiPart("file", tempFile.toFile(), "application/octet-stream")
                .when().post("/api/modules/upload")
                .then()
                    .statusCode(400)
                    .body("error", containsString("JAR"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void uploadModule_withValidJar_shouldReturn201() throws IOException {
        // Create a minimal valid JAR file
        Path tempJar = Files.createTempFile("test-module", ".jar");

        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(tempJar.toFile()), new Manifest())) {
            // Empty JAR is sufficient for upload test
        }

        try {
            given()
                .multiPart("file", tempJar.toFile(), "application/java-archive")
                .when().post("/api/modules/upload")
                .then()
                    .statusCode(201)
                    .contentType(ContentType.JSON);
        } finally {
            Files.deleteIfExists(tempJar);
        }
    }

    @Test
    void deleteModule_notFound_shouldReturn404() {
        given()
            .when().delete("/api/modules/NonExistentModule123456")
            .then()
                .statusCode(404)
                .body("error", containsString("not found"));
    }
}
