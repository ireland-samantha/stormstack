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
 * Unit tests for {@link ContainerPlayerResource}.
 */
@QuarkusTest
@DisplayName("ContainerPlayerResource")
@TestSecurity(user = "admin", roles = "admin")
class ContainerPlayerResourceTest {

    private Long containerId;
    private Long matchId;

    private static RequestSpecification jsonRequest() {
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    @BeforeEach
    void setUp() {
        // Create and start a container
        containerId = jsonRequest()
                .body("""
                    {
                        "name": "test-player-container"
                    }
                    """)
                .when().post("/api/containers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        jsonRequest().when().post("/api/containers/" + containerId + "/start").then().statusCode(200);

        // Create a match
        matchId = jsonRequest()
                .body("""
                    {
                        "id": 2001,
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
    }

    @AfterEach
    void tearDown() {
        if (containerId != null && matchId != null) {
            jsonRequest().when().delete("/api/containers/" + containerId + "/matches/" + matchId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
        }
        if (containerId != null) {
            jsonRequest().when().post("/api/containers/" + containerId + "/stop").then().statusCode(anyOf(equalTo(200), equalTo(404)));
            jsonRequest().when().delete("/api/containers/" + containerId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/matches/{matchId}/players")
    class JoinMatch {

        @Test
        @DisplayName("should allow player to join match")
        void shouldAllowPlayerToJoinMatch() {
            // First create a player
            var createdPlayerId = jsonRequest()
                    .body("""
                        {
                            "id": 1001
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/players")
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getLong("id");

            // Then join the player to the match
            var playerId = jsonRequest()
                    .body("{\"playerId\": " + createdPlayerId + "}")
                    .when().post("/api/containers/" + containerId + "/matches/" + matchId + "/players")
                    .then()
                    .statusCode(201)
                    .body("playerId", equalTo((int) createdPlayerId))
                    .extract()
                    .jsonPath()
                    .getLong("playerId");

            // Cleanup
            jsonRequest().when().delete("/api/containers/" + containerId + "/matches/" + matchId + "/players/" + playerId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
            jsonRequest().when().delete("/api/containers/" + containerId + "/players/" + createdPlayerId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .body("""
                        {
                            "playerId": 1001
                        }
                        """)
                    .when().post("/api/containers/999999/matches/1/players")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}/players")
    class GetPlayers {

        @Test
        @DisplayName("should return list of players in match")
        void shouldReturnListOfPlayersInMatch() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId + "/players")
                    .then()
                    .statusCode(200)
                    .body("$", is(instanceOf(java.util.List.class)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/matches/1/players")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}/players/{playerId}")
    class GetPlayer {

        @Test
        @DisplayName("should return 404 for non-existent player")
        void shouldReturn404ForNonExistentPlayer() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId + "/players/999999")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("DELETE /api/containers/{containerId}/matches/{matchId}/players/{playerId}")
    class LeaveMatch {

        @Test
        @DisplayName("should allow player to leave match")
        void shouldAllowPlayerToLeaveMatch() {
            // First create a player
            var createdPlayerId = jsonRequest()
                    .body("""
                        {
                            "id": 1002
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/players")
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getLong("id");

            // Then join
            var playerId = jsonRequest()
                    .body("{\"playerId\": " + createdPlayerId + "}")
                    .when().post("/api/containers/" + containerId + "/matches/" + matchId + "/players")
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getLong("playerId");

            // Then leave
            jsonRequest()
                    .when().delete("/api/containers/" + containerId + "/matches/" + matchId + "/players/" + playerId)
                    .then()
                    .statusCode(204);

            // Cleanup player
            jsonRequest().when().delete("/api/containers/" + containerId + "/players/" + createdPlayerId).then().statusCode(anyOf(equalTo(204), equalTo(404)));
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/players")
    class GetAllPlayersInContainer {

        @Test
        @DisplayName("should return all players in container")
        void shouldReturnAllPlayersInContainer() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/players")
                    .then()
                    .statusCode(200)
                    .body("$", is(instanceOf(java.util.List.class)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/players")
                    .then()
                    .statusCode(404);
        }
    }
}
