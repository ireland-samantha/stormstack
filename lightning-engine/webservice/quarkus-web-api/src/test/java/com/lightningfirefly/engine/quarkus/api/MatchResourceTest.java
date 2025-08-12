package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class MatchResourceTest {

    @Test
    void createMatch_shouldReturnCreatedWithGeneratedId() {
        // Match ID is generated server-side
        given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .body("id", greaterThan(0));
    }

    @Test
    void getMatch_shouldReturnMatch() {
        // Create match first (ID is generated server-side)
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        int matchId = createResponse.jsonPath().getInt("id");

        // Get the match by generated ID
        given()
            .when().get("/api/matches/" + matchId)
            .then()
                .statusCode(200)
                .body("id", is(matchId));
    }

    @Test
    void getMatch_notFound_shouldReturn404() {
        given()
            .when().get("/api/matches/999999")
            .then()
                .statusCode(404);
    }

    @Test
    void getAllMatches_shouldReturnList() {
        // Create a match (ID is generated server-side)
        given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches");

        given()
            .when().get("/api/matches")
            .then()
                .statusCode(200);
    }

    @Test
    void deleteMatch_shouldReturnNoContent() {
        // Create match first (ID is generated server-side)
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"enabledModuleNames\": []}")
            .when().post("/api/matches")
            .then()
                .statusCode(201)
                .extract().response();

        int matchId = createResponse.jsonPath().getInt("id");

        // Delete the match
        given()
            .when().delete("/api/matches/" + matchId)
            .then()
                .statusCode(204);

        // Verify it's deleted
        given()
            .when().get("/api/matches/" + matchId)
            .then()
                .statusCode(404);
    }

    @Test
    void deleteMatch_notFound_shouldReturn404() {
        given()
            .when().delete("/api/matches/999998")
            .then()
                .statusCode(404);
    }
}
