package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class SimulationResourceTest {

    @AfterEach
    void tearDown() {
        // Ensure simulation is stopped after each test
        given().when().post("/api/simulation/stop");
    }

    @Test
    void getCurrentTick_shouldReturnTick() {
        given()
            .when().get("/api/simulation/tick")
            .then()
                .statusCode(200)
                .body("tick", greaterThanOrEqualTo(0));
    }

    @Test
    void advanceTick_shouldIncrementTick() {
        // Get current tick
        int currentTick = given()
            .when().get("/api/simulation/tick")
            .then()
                .statusCode(200)
                .extract().path("tick");

        // Advance tick
        given()
            .when().post("/api/simulation/tick")
            .then()
                .statusCode(200)
                .body("tick", is(currentTick + 1));
    }

    @Test
    void play_shouldStartAutoAdvance() {
        given()
            .when().post("/api/simulation/play?intervalMs=10")
            .then()
                .statusCode(200)
                .body("playing", is(true))
                .body("intervalMs", is(10));
    }

    @Test
    void stop_shouldStopAutoAdvance() {
        // First start playing
        given()
            .when().post("/api/simulation/play?intervalMs=10")
            .then()
                .statusCode(200);

        // Then stop
        given()
            .when().post("/api/simulation/stop")
            .then()
                .statusCode(200)
                .body("playing", is(false));
    }

    @Test
    void getStatus_shouldReturnPlayingStatus() {
        // Initially not playing
        given()
            .when().get("/api/simulation/status")
            .then()
                .statusCode(200)
                .body("playing", is(false));

        // Start playing
        given()
            .when().post("/api/simulation/play?intervalMs=10")
            .then()
                .statusCode(200);

        // Check status
        given()
            .when().get("/api/simulation/status")
            .then()
                .statusCode(200)
                .body("playing", is(true));

        // Stop
        given()
            .when().post("/api/simulation/stop")
            .then()
                .statusCode(200);

        // Check status again
        given()
            .when().get("/api/simulation/status")
            .then()
                .statusCode(200)
                .body("playing", is(false));
    }

    @Test
    void play_shouldAutoAdvanceTick() throws InterruptedException {
        int initialTick = given()
            .when().get("/api/simulation/tick")
            .then()
                .statusCode(200)
                .extract().path("tick");

        // Start playing with 10ms interval
        given()
            .when().post("/api/simulation/play?intervalMs=10")
            .then()
                .statusCode(200);

        // Wait for some ticks to advance
        Thread.sleep(100);

        // Stop playing
        given()
            .when().post("/api/simulation/stop")
            .then()
                .statusCode(200);

        // Check that tick has advanced
        given()
            .when().get("/api/simulation/tick")
            .then()
                .statusCode(200)
                .body("tick", greaterThan(initialTick));
    }

    @Test
    void play_withDefaultInterval_shouldUse10ms() {
        given()
            .when().post("/api/simulation/play")
            .then()
                .statusCode(200)
                .body("playing", is(true))
                .body("intervalMs", is(10));
    }
}
