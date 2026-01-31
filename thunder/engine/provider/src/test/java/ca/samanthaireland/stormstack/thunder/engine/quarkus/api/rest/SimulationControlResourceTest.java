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

package ca.samanthaireland.stormstack.thunder.engine.quarkus.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link SimulationControlResource}.
 */
@QuarkusTest
@DisplayName("SimulationControlResource")
@TestSecurity(user = "admin", roles = "admin")
class SimulationControlResourceTest {

    private Long containerId;

    private static RequestSpecification jsonRequest() {
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    @BeforeEach
    void setUp() {
        // Create and start a container for simulation tests
        containerId = jsonRequest()
                .body("""
                    {
                        "name": "test-simulation-container"
                    }
                    """)
                .when().post("/api/containers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        jsonRequest().when().post("/api/containers/" + containerId + "/start").then().statusCode(200);
    }

    @AfterEach
    void tearDown() {
        if (containerId != null) {
            jsonRequest().when().post("/api/containers/" + containerId + "/stop").then().statusCode(anyOf(equalTo(200), equalTo(404)));
            jsonRequest().when().delete("/api/containers/" + containerId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/tick")
    class GetTick {

        @Test
        @DisplayName("should return current tick")
        void shouldReturnCurrentTick() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/tick")
                    .then()
                    .statusCode(200)
                    .body("tick", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/tick")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/tick")
    class AdvanceTick {

        @Test
        @DisplayName("should advance tick by one")
        void shouldAdvanceTickByOne() {
            // Get current tick
            int tick = jsonRequest()
                    .when().get("/api/containers/" + containerId + "/tick")
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getInt("tick");

            // Advance tick
            jsonRequest()
                    .when().post("/api/containers/" + containerId + "/tick")
                    .then()
                    .statusCode(200)
                    .body("tick", equalTo(tick + 1));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/tick")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/play")
    class StartAutoAdvance {

        @Test
        @DisplayName("should start auto-advance with default interval")
        void shouldStartAutoAdvanceWithDefaultInterval() {
            jsonRequest()
                    .when().post("/api/containers/" + containerId + "/play")
                    .then()
                    .statusCode(200);

            // Stop auto-advance
            jsonRequest().when().post("/api/containers/" + containerId + "/stop-auto").then().statusCode(200);
        }

        @Test
        @DisplayName("should start auto-advance with custom interval")
        void shouldStartAutoAdvanceWithCustomInterval() {
            jsonRequest()
                    .queryParam("intervalMs", 100)
                    .when().post("/api/containers/" + containerId + "/play")
                    .then()
                    .statusCode(200);

            // Stop auto-advance
            jsonRequest().when().post("/api/containers/" + containerId + "/stop-auto").then().statusCode(200);
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/play")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/stop-auto")
    class StopAutoAdvance {

        @Test
        @DisplayName("should stop auto-advance")
        void shouldStopAutoAdvance() {
            // Start auto-advance first
            jsonRequest().when().post("/api/containers/" + containerId + "/play").then().statusCode(200);

            // Stop auto-advance
            jsonRequest()
                    .when().post("/api/containers/" + containerId + "/stop-auto")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/stop-auto")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/status")
    class GetStatus {

        @Test
        @DisplayName("should return play status when not playing")
        void shouldReturnPlayStatusWhenNotPlaying() {
            // Ensure not playing
            jsonRequest().when().post("/api/containers/" + containerId + "/stop-auto").then().statusCode(200);

            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/status")
                    .then()
                    .statusCode(200)
                    .body("playing", equalTo(false))
                    .body("tick", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("should return play status when playing")
        void shouldReturnPlayStatusWhenPlaying() {
            // Start auto-advance
            jsonRequest().when().post("/api/containers/" + containerId + "/play").then().statusCode(200);

            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/status")
                    .then()
                    .statusCode(200)
                    .body("playing", equalTo(true))
                    .body("intervalMs", greaterThan(0));

            // Stop auto-advance
            jsonRequest().when().post("/api/containers/" + containerId + "/stop-auto").then().statusCode(200);
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/status")
                    .then()
                    .statusCode(404);
        }
    }
}
