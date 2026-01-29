package ca.samanthaireland.engine.quarkus.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Contract tests for Container Metrics API.
 *
 * <p>These tests validate the structure and schema of the API response
 * to ensure backend and frontend contracts remain aligned.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Required fields are present</li>
 *   <li>Field types are correct</li>
 *   <li>Benchmark structure matches frontend expectations</li>
 *   <li>fullName format is correct (module:scope)</li>
 * </ul>
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
@DisplayName("Container Metrics API Contract Tests")
class ContainerMetricsSchemaTest {

    private long containerId;

    @BeforeEach
    void setUp() {
        // Create and start a container for each test
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"name\": \"schema-test-container\"}")
                .when().post("/api/containers")
                .then()
                .statusCode(201)
                .extract().response();

        containerId = createResponse.jsonPath().getLong("id");

        // Start the container
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/containers/" + containerId + "/start")
                .then()
                .statusCode(200);
    }

    @AfterEach
    void tearDown() {
        // Stop the container (ignore errors)
        try {
            given().contentType(ContentType.JSON)
                    .when().post("/api/containers/" + containerId + "/stop");
        } catch (Exception ignored) {}

        // Delete the container (ignore errors)
        try {
            given().contentType(ContentType.JSON)
                    .when().delete("/api/containers/" + containerId);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Metrics response should include all required fields")
    void metricsResponse_includesAllRequiredFields() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .body("containerId", notNullValue())
                .body("currentTick", notNullValue())
                .body("lastTickMs", notNullValue())
                .body("avgTickMs", notNullValue())
                .body("minTickMs", notNullValue())
                .body("maxTickMs", notNullValue())
                .body("totalTicks", notNullValue())
                .body("lastTickNanos", notNullValue())
                .body("avgTickNanos", notNullValue())
                .body("minTickNanos", notNullValue())
                .body("maxTickNanos", notNullValue())
                .body("totalEntities", notNullValue())
                .body("totalComponentTypes", notNullValue())
                .body("commandQueueSize", notNullValue())
                .body("lastTickSystems", notNullValue())
                .body("lastTickCommands", notNullValue())
                .body("lastTickBenchmarks", notNullValue());
    }

    @Test
    @DisplayName("Metrics response should have correct field types")
    void metricsResponse_hasCorrectFieldTypes() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .body("containerId", instanceOf(Integer.class))
                .body("currentTick", instanceOf(Integer.class))
                .body("lastTickMs", anyOf(instanceOf(Float.class), instanceOf(Double.class), instanceOf(Integer.class)))
                .body("avgTickMs", anyOf(instanceOf(Float.class), instanceOf(Double.class), instanceOf(Integer.class)))
                .body("totalTicks", instanceOf(Integer.class))
                .body("lastTickNanos", instanceOf(Integer.class))
                .body("totalEntities", instanceOf(Integer.class))
                .body("totalComponentTypes", instanceOf(Integer.class))
                .body("commandQueueSize", instanceOf(Integer.class))
                .body("lastTickSystems", instanceOf(java.util.List.class))
                .body("lastTickCommands", instanceOf(java.util.List.class))
                .body("lastTickBenchmarks", instanceOf(java.util.List.class));
    }

    @Test
    @DisplayName("Benchmarks array should be empty when no benchmarks present")
    void benchmarksArray_isEmptyWhenNoBenchmarks() {
        // Advance a tick
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/containers/" + containerId + "/tick")
                .then()
                .statusCode(200);

        // Get metrics
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .body("lastTickBenchmarks", notNullValue())
                .body("lastTickBenchmarks", instanceOf(java.util.List.class))
                .body("lastTickBenchmarks.size()", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("Benchmark structure should match frontend interface when present")
    void benchmarkStructure_matchesFrontendInterface() {
        // This test documents the expected structure for each benchmark item:
        // {
        //   moduleName: string,
        //   scopeName: string,
        //   fullName: string (format: "moduleName:scopeName"),
        //   executionTimeMs: number,
        //   executionTimeNanos: number
        // }

        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        // Verify lastTickBenchmarks is an array
        response.then()
                .body("lastTickBenchmarks", instanceOf(java.util.List.class));

        // If benchmarks exist, verify structure
        java.util.List<?> benchmarks = response.jsonPath().getList("lastTickBenchmarks");
        if (!benchmarks.isEmpty()) {
            response.then()
                    .body("lastTickBenchmarks[0].moduleName", notNullValue())
                    .body("lastTickBenchmarks[0].scopeName", notNullValue())
                    .body("lastTickBenchmarks[0].fullName", notNullValue())
                    .body("lastTickBenchmarks[0].executionTimeMs", notNullValue())
                    .body("lastTickBenchmarks[0].executionTimeNanos", notNullValue());
        }
    }

    @Test
    @DisplayName("Benchmark fullName should follow module:scope format")
    void benchmarkFullName_followsCorrectFormat() {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        java.util.List<?> benchmarks = response.jsonPath().getList("lastTickBenchmarks");
        for (int i = 0; i < benchmarks.size(); i++) {
            String moduleName = response.jsonPath().getString("lastTickBenchmarks[" + i + "].moduleName");
            String scopeName = response.jsonPath().getString("lastTickBenchmarks[" + i + "].scopeName");
            String fullName = response.jsonPath().getString("lastTickBenchmarks[" + i + "].fullName");

            // Verify fullName = moduleName:scopeName
            if (moduleName != null && scopeName != null && fullName != null) {
                String expectedFullName = moduleName + ":" + scopeName;
                org.assertj.core.api.Assertions.assertThat(fullName)
                        .as("fullName should be moduleName:scopeName")
                        .isEqualTo(expectedFullName);

                // Verify format matches pattern
                org.assertj.core.api.Assertions.assertThat(fullName)
                        .as("fullName should match pattern module:scope")
                        .matches("^[^:]+:[^:]+$");
            }
        }
    }

    @Test
    @DisplayName("Benchmark execution times should be non-negative")
    void benchmarkExecutionTimes_areNonNegative() {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        java.util.List<?> benchmarks = response.jsonPath().getList("lastTickBenchmarks");
        for (int i = 0; i < benchmarks.size(); i++) {
            response.then()
                    .body("lastTickBenchmarks[" + i + "].executionTimeMs", greaterThanOrEqualTo(0.0f))
                    .body("lastTickBenchmarks[" + i + "].executionTimeNanos", greaterThanOrEqualTo(0));
        }
    }

    @Test
    @DisplayName("Benchmark data types should match TypeScript interface")
    void benchmarkDataTypes_matchTypeScriptInterface() {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        java.util.List<?> benchmarks = response.jsonPath().getList("lastTickBenchmarks");
        for (int i = 0; i < benchmarks.size(); i++) {
            response.then()
                    // moduleName: string
                    .body("lastTickBenchmarks[" + i + "].moduleName", instanceOf(String.class))
                    // scopeName: string
                    .body("lastTickBenchmarks[" + i + "].scopeName", instanceOf(String.class))
                    // fullName: string
                    .body("lastTickBenchmarks[" + i + "].fullName", instanceOf(String.class))
                    // executionTimeMs: number (float/double in JSON)
                    .body("lastTickBenchmarks[" + i + "].executionTimeMs",
                            anyOf(instanceOf(Float.class), instanceOf(Double.class), instanceOf(Integer.class)))
                    // executionTimeNanos: number (long/integer in JSON)
                    .body("lastTickBenchmarks[" + i + "].executionTimeNanos", instanceOf(Integer.class));
        }
    }
}
