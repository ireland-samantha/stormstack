package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class SnapshotResourceTest {

    @Test
    void getAllSnapshots_shouldReturnSnapshotsForAllMatches() {
        // Create two matches (IDs generated server-side)
        Response match1Response = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        Response match2Response = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        int matchId1 = match1Response.jsonPath().getInt("id");
        int matchId2 = match2Response.jsonPath().getInt("id");

        // Get all snapshots
        given()
            .when().get("/api/snapshots")
            .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2));

        // Cleanup
        given().delete("/api/matches/" + matchId1);
        given().delete("/api/matches/" + matchId2);
    }

    @Test
    void getSnapshot_shouldReturnSnapshot() {
        // Create a match (ID generated server-side)
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        int matchId = createResponse.jsonPath().getInt("id");

        // Get components for the match
        given()
            .when().get("/api/snapshots/match/" + matchId)
            .then()
                .statusCode(200)
                .body("matchId", is(matchId))
                .body("data", notNullValue());

        // Cleanup
        given().delete("/api/matches/" + matchId);
    }

    @Test
    void getSnapshot_nonExistentMatch_shouldReturn404() {
        // Snapshot endpoint returns 404 for non-existent matches
        given()
            .when().get("/api/snapshots/match/999999")
            .then()
                .statusCode(404);
    }
}
