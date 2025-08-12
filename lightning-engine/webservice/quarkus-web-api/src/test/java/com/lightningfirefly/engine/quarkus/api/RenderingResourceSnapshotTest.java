package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;

/**
 * API acceptance test for rendering resource attachment.
 *
 * <p>This test verifies:
 * <ol>
 *   <li>RenderModule is installed and available</li>
 *   <li>attachSprite command is available and accepted</li>
 *   <li>Resource upload works correctly</li>
 *   <li>The command execution doesn't crash the simulation</li>
 * </ol>
 */
@QuarkusTest
class RenderingResourceSnapshotTest {

    private Integer matchId;
    private Long resourceId;

    @AfterEach
    void tearDown() {
        // Clean up match
        if (matchId != null) {
            given().delete("/api/matches/" + matchId);
            matchId = null;
        }

        // Clean up resource
        if (resourceId != null) {
            given().delete("/api/resources/" + resourceId);
            resourceId = null;
        }
    }

    @Test
    void renderModule_isAvailable() {
        // Verify RenderModule is installed
        given()
            .when().get("/api/modules")
            .then()
                .statusCode(200)
                .body("name", hasItem("RenderModule"));
    }

    @Test
    void attachSpriteCommand_isAvailable() {
        // Verify attachSprite command is available
        Response response = given()
            .when().get("/api/commands")
            .then()
                .statusCode(200)
                .extract().response();

        List<String> commands = response.jsonPath().getList("$");
        assertThat(commands.toString()).contains("attachSprite");
    }

    @Test
    void attachSprite_commandIsAccepted() throws IOException {
        // Create a match with RenderModule enabled
        Response matchResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": [\"SpawnModule\", \"RenderModule\"]}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        matchId = matchResponse.jsonPath().getInt("id");
        assertThat(matchId).isGreaterThan(0);

        // Upload a resource
        File tempTexture = createTempTextureFile();
        try {
            Response uploadResponse = given()
                .multiPart("file", tempTexture)
                .multiPart("resourceName", "test-texture.png")
                .multiPart("resourceType", "TEXTURE")
                .when().post("/api/resources")
                .then()
                    .statusCode(201)
                    .extract().response();

            resourceId = uploadResponse.jsonPath().getLong("resourceId");
            assertThat(resourceId).isGreaterThan(0);

            // Send attachSprite command - should be accepted
            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "commandName": "attachSprite",
                        "payload": {
                            "entityId": 1,
                            "resourceId": %d
                        }
                    }
                    """.formatted(resourceId))
                .when().post("/api/commands")
                .then()
                    .statusCode(202);

            // Tick should process the command without error
            given()
                .when().post("/api/simulation/tick")
                .then()
                    .statusCode(200);

            // Simulation should still be healthy
            given()
                .when().get("/api/simulation/status")
                .then()
                    .statusCode(200);

        } finally {
            tempTexture.delete();
        }
    }

    @Test
    void attachSprite_withoutEntity_shouldNotCrash() throws IOException {
        // Create a match with RenderModule enabled
        Response matchResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": [\"RenderModule\"]}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        matchId = matchResponse.jsonPath().getInt("id");

        // Upload a resource
        File tempTexture = createTempTextureFile();
        try {
            Response uploadResponse = given()
                .multiPart("file", tempTexture)
                .multiPart("resourceName", "test-texture.png")
                .multiPart("resourceType", "TEXTURE")
                .when().post("/api/resources")
                .then()
                    .statusCode(201)
                    .extract().response();

            resourceId = uploadResponse.jsonPath().getLong("resourceId");

            // Try to attach sprite to non-existent entity (should not crash)
            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "commandName": "attachSprite",
                        "payload": {
                            "entityId": 999999,
                            "resourceId": %d
                        }
                    }
                    """.formatted(resourceId))
                .when().post("/api/commands")
                .then()
                    .statusCode(202);

            // Tick should not crash
            given()
                .when().post("/api/simulation/tick")
                .then()
                    .statusCode(200);

        } finally {
            tempTexture.delete();
        }
    }

    /**
     * Create a temporary PNG file for testing.
     */
    private File createTempTextureFile() throws IOException {
        File tempFile = File.createTempFile("test-texture", ".png");
        // Write a minimal valid PNG file (1x1 red pixel)
        byte[] minimalPng = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,  // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,  // 1x1 dimensions
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,  // 8-bit RGB
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,  // IDAT chunk
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,  // IEND chunk
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(minimalPng);
        }
        return tempFile;
    }
}
