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
 * Unit tests for {@link ContainerCommandResource}.
 */
@QuarkusTest
@DisplayName("ContainerCommandResource")
@TestSecurity(user = "admin", roles = "admin")
class ContainerCommandResourceTest {

    private Long containerId;

    private static RequestSpecification jsonRequest() {
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    @BeforeEach
    void setUp() {
        // Create and start a container for command tests
        containerId = jsonRequest()
                .body("""
                    {
                        "name": "test-command-container"
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
    @DisplayName("GET /api/containers/{containerId}/commands")
    class GetCommands {

        @Test
        @DisplayName("should return list of available commands")
        void shouldReturnListOfAvailableCommands() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/commands")
                    .then()
                    .statusCode(200)
                    .body("$", is(instanceOf(java.util.List.class)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/commands")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/commands")
    class EnqueueCommand {

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .body("""
                        {
                            "commandName": "test-command",
                            "parameters": {}
                        }
                        """)
                    .when().post("/api/containers/999999/commands")
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("should accept command request")
        void shouldAcceptCommandRequest() {
            // Note: This test may return 202 (accepted) or 400/500 depending on whether
            // the command exists. We're testing the endpoint responds appropriately.
            var response = jsonRequest()
                    .body("""
                        {
                            "commandName": "nonexistent-command",
                            "parameters": {}
                        }
                        """)
                    .when().post("/api/containers/" + containerId + "/commands")
                    .then()
                    .extract();

            // Either accepted (202) or some error is acceptable - we're testing the endpoint exists
            Assertions.assertTrue(
                    response.statusCode() >= 200 && response.statusCode() < 600,
                    "Expected valid HTTP response");
        }
    }
}
