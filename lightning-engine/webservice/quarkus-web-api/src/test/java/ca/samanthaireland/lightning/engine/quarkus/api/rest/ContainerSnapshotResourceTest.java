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
 * Unit tests for {@link ContainerSnapshotResource}.
 */
@QuarkusTest
@DisplayName("ContainerSnapshotResource")
@TestSecurity(user = "admin", roles = "admin")
class ContainerSnapshotResourceTest {

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
                        "name": "test-snapshot-container"
                    }
                    """)
                .when().post("/api/containers")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        jsonRequest().when().post("/api/containers/" + containerId + "/start").then().statusCode(200);

        // Create a match for snapshot tests
        matchId = jsonRequest()
                .body("""
                    {
                        "id": 3001,
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
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}/snapshot")
    class GetSnapshot {

        @Test
        @DisplayName("should return current snapshot for match")
        void shouldReturnCurrentSnapshotForMatch() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId + "/snapshot")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/matches/1/snapshot")
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("should return 404 for non-existent match")
        void shouldReturn404ForNonExistentMatch() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/999999/snapshot")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/matches/{matchId}/snapshots/history-info")
    class GetSnapshotHistoryInfo {

        @Test
        @DisplayName("should return snapshot history info for match")
        void shouldReturnSnapshotHistoryInfoForMatch() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/matches/" + matchId + "/snapshots/history-info")
                    .then()
                    .statusCode(200)
                    .body("matchId", equalTo(matchId.intValue()))
                    .body("snapshotCount", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/matches/1/snapshots/history-info")
                    .then()
                    .statusCode(404);
        }
    }

}
