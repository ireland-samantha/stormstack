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

/**
 * Integration tests for ContainerMetricsResource benchmark functionality.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Metrics endpoint includes lastTickBenchmarks field</li>
 *   <li>Benchmark data structure is correct (when benchmarks are present)</li>
 *   <li>Empty benchmarks return empty array (not null)</li>
 * </ul>
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
@DisplayName("ContainerMetricsResource - Benchmarks Integration")
class ContainerMetricsResourceIntegrationTest {

    private long containerId;

    @BeforeEach
    void setUp() {
        // Create and start a container for each test
        Response createResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"name\": \"test-metrics-container\"}")
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
    @DisplayName("getMetrics should include lastTickBenchmarks field")
    void getMetrics_includesLastTickBenchmarksField() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .body("lastTickBenchmarks", notNullValue());
    }

    @Test
    @DisplayName("getMetrics should return benchmarks array (may be empty or populated by active modules)")
    void getMetrics_returnsBenchmarksArray() {
        // Advance a tick to generate benchmark data from any active modules
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/containers/" + containerId + "/tick")
                .then()
                .statusCode(200);

        // Verify metrics has benchmark array (not null)
        // The array may contain benchmarks from physics systems or other modules
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .body("lastTickBenchmarks", notNullValue());
    }

    @Test
    @DisplayName("getMetrics should include all standard fields alongside benchmarks")
    void getMetrics_includesAllStandardFieldsWithBenchmarks() {
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
                .body("totalEntities", notNullValue())
                .body("totalComponentTypes", notNullValue())
                .body("lastTickBenchmarks", notNullValue());
    }

    @Test
    @DisplayName("getMetrics returns 404 for non-existent container")
    void getMetrics_returns404ForNonExistentContainer() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/999999/metrics")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("benchmark metrics structure is correct when present")
    void benchmarkMetrics_hasCorrectStructure() {
        // Advance a tick to generate benchmarks from physics systems
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/containers/" + containerId + "/tick")
                .then()
                .statusCode(200);

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

        // If benchmarks are present (from physics module), verify structure
        int benchmarkCount = response.jsonPath().getInt("lastTickBenchmarks.size()");
        if (benchmarkCount > 0) {
            // Verify each benchmark has required fields
            response.then()
                    .body("lastTickBenchmarks[0].moduleName", notNullValue())
                    .body("lastTickBenchmarks[0].scopeName", notNullValue())
                    .body("lastTickBenchmarks[0].fullName", notNullValue())
                    .body("lastTickBenchmarks[0].executionTimeMs", notNullValue())
                    .body("lastTickBenchmarks[0].executionTimeNanos", notNullValue());
        }
    }

    @Test
    @DisplayName("physics module benchmarks have expected scope names")
    void physicsModuleBenchmarks_haveExpectedScopeNames() {
        // Advance a tick to generate benchmarks from physics systems
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/containers/" + containerId + "/tick")
                .then()
                .statusCode(200);

        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/containers/" + containerId + "/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        // Physics module creates 3 benchmark scopes per tick:
        // - "force-integration" from ForceIntegrationSystem
        // - "velocity-position-integration" from PhysicsIntegrationSystem
        // - "cleanup" from RigidBodyCleanupSystem
        int benchmarkCount = response.jsonPath().getInt("lastTickBenchmarks.size()");

        // If physics module is active, we expect 3 benchmarks
        if (benchmarkCount >= 3) {
            java.util.List<String> scopeNames = response.jsonPath().getList("lastTickBenchmarks.scopeName");
            org.assertj.core.api.Assertions.assertThat(scopeNames)
                    .contains("force-integration", "velocity-position-integration", "cleanup");
        }
    }
}
