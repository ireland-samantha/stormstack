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
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link ContainerAIResource}.
 */
@QuarkusTest
@DisplayName("ContainerAIResource")
@TestSecurity(user = "admin", roles = "admin")
class ContainerAIResourceTest {

    private Long containerId;

    private static io.restassured.specification.RequestSpecification jsonRequest() {
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
                        "name": "test-ai-container"
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
    @DisplayName("GET /api/containers/{containerId}/ai")
    class GetAIs {

        @Test
        @DisplayName("should return list of installed AIs")
        void shouldReturnListOfInstalledAIs() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/ai")
                    .then()
                    .statusCode(200)
                    .body("$", is(instanceOf(java.util.List.class)));
        }

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().get("/api/containers/999999/ai")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("GET /api/containers/{containerId}/ai/{aiName}")
    class GetAI {

        @Test
        @DisplayName("should return 404 for non-existent AI")
        void shouldReturn404ForNonExistentAI() {
            jsonRequest()
                    .when().get("/api/containers/" + containerId + "/ai/NonExistentAI")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/ai/{aiName}/install")
    class InstallAI {

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/ai/SomeAI/install")
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("should return 404 for non-existent AI")
        void shouldReturn404ForNonExistentAI() {
            jsonRequest()
                    .when().post("/api/containers/" + containerId + "/ai/NonExistentAI/install")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/ai/{aiName}/enable")
    class EnableAI {

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/ai/SomeAI/enable")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("POST /api/containers/{containerId}/ai/{aiName}/disable")
    class DisableAI {

        @Test
        @DisplayName("should return 404 for non-existent container")
        void shouldReturn404ForNonExistentContainer() {
            jsonRequest()
                    .when().post("/api/containers/999999/ai/SomeAI/disable")
                    .then()
                    .statusCode(404);
        }
    }
}
