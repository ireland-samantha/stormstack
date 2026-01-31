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

package ca.samanthaireland.lightning.engine.quarkus.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link ContainerMatchResource}.
 */
@QuarkusTest
@DisplayName("ContainerMatchResource")
@TestSecurity(user = "admin", roles = "admin")
class ContainerMatchResourceTest {

    private Long containerId;

    private static RequestSpecification jsonRequest() {
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    @BeforeEach
    void setUp() {
        // Create and start a container for match tests
        containerId = jsonRequest()
                .body("""
                    {
                        "name": "test-match-container"
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
    @DisplayName("GET /api/containers/{containerId}/matches")
    class GetMatches {

        @Test
        @DisplayName("should return empty list when no matches exist")
        void shouldReturnEmptyListWhenNoMatchesExist() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches")
                    .then()
                    .statusCode(200)
                    .body("$", is(instanceOf(java.util.List.class)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/matches")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/matches")
    class CreateMatch {

        @Test
        @DisplayName("should create a match in a container")
        void shouldCreateMatchInContainer() {
            var matchId = jsonRequest()
                    .body("""
                        {
                            "id": 1001,
                            "enabledModuleNames": [],
                            "enabledAINames": []
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/matches")
                    .then()
                    .statusCode(201)
                    .body("id", equalTo(1001))
                    .extract()
                    .jsonPath()
                    .getLong("id");

            // Cleanup
            jsonRequest()
                    .when().delete("/api/containers/" + containerId + "/matches/" + matchId)
                    .then()
                    .statusCode(204);
        }

        @Test
        @DisplayName("should return 404 when creating match in non-existent container")
        void shouldReturn404WhenCreatingMatchInNonExistentContainer() {
            jsonRequest()
                    .body("""
                        {
                            "id": 1,
                            "enabledModuleNames": [],
                            "enabledAINames": []
                        }
                        """)
                    .when().post("/api/containers/999999/matches")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}")
    class GetMatch {

        @Test
        @DisplayName("should return a specific match")
        void shouldReturnSpecificMatch() {
            // Create a match
            var matchId = jsonRequest()
                    .body("""
                        {
                            "id": 1002,
                            "enabledModuleNames": [],
                            "enabledAINames": []
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/matches")
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getLong("id");

            // Get it
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(1002));

            // Cleanup
            jsonRequest().when().delete("/api/containers/" + containerId + "/matches/" + matchId).then().statusCode(204);
        }

        @Test
        @DisplayName("should return 404 for non-existent match")
        void shouldReturn404ForNonExistentMatch() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/999999")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("DELETE /api/containers/{containerId}/matches/{matchId}")
    class DeleteMatch {

        @Test
        @DisplayName("should delete a match from container")
        void shouldDeleteMatchFromContainer() {
            // Create a match
            var matchId = jsonRequest()
                    .body("""
                        {
                            "id": 1003,
                            "enabledModuleNames": [],
                            "enabledAINames": []
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/matches")
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getLong("id");

            // Delete it
            jsonRequest()
                    .when().delete("/api/containers/" + containerId + "/matches/" + matchId)
                    .then()
                    .statusCode(204);

            // Verify it's gone
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId)
                    .then()
                    .statusCode(404);
        }
    }
}
