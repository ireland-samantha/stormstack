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
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link NodeMetricsResource}.
 */
@QuarkusTest
@DisplayName("NodeMetricsResource")
class NodeMetricsResourceTest {

    @Nested
    @DisplayName("GET /api/node/status")
    class GetNodeStatus {

        @Test
        @DisplayName("should return node status")
        @TestSecurity(user = "admin", roles = "admin")
        void shouldReturnNodeStatus() {
            given()
                    .when().get("/api/node/status")
                    .then()
                    .statusCode(200)
                    .body("nodeId", notNullValue())
                    .body("registered", notNullValue());
        }
    }

    @Nested
    @DisplayName("GET /api/node/metrics")
    class GetNodeMetrics {

        @Test
        @DisplayName("should return node metrics")
        @TestSecurity(user = "admin", roles = "admin")
        void shouldReturnNodeMetrics() {
            given()
                    .when().get("/api/node/metrics")
                    .then()
                    .statusCode(200)
                    .body("cpuUsage", notNullValue())
                    .body("containerCount", greaterThanOrEqualTo(0))
                    .body("memoryUsedMb", greaterThanOrEqualTo(0))
                    .body("memoryMaxMb", greaterThan(0));
        }
    }
}
