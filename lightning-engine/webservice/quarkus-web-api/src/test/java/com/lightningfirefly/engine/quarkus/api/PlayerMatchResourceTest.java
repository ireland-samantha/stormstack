package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class PlayerMatchResourceTest {

    @Test
    void joinMatch_shouldReturnCreated() {
        // Create match first
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3001, \"enabledModuleNames\": []}")
            .when().post("/api/matches");

        // Create player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3001}")
            .when().post("/api/players");

        // Join match
        given()
            .contentType(ContentType.JSON)
            .body("{\"playerId\": 3001, \"matchId\": 3001}")
            .when().post("/api/player-matches")
            .then()
                .statusCode(201)
                .body("playerId", is(3001))
                .body("matchId", is(3001));
    }

    @Test
    void getPlayerMatch_shouldReturnPlayerMatch() {
        // Create match
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3002, \"enabledModuleNames\": []}")
            .when().post("/api/matches");

        // Create player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3002}")
            .when().post("/api/players");

        // Join match
        given()
            .contentType(ContentType.JSON)
            .body("{\"playerId\": 3002, \"matchId\": 3002}")
            .when().post("/api/player-matches");

        // Get player-match (note: path is /player/{playerId}/match/{matchId})
        given()
            .when().get("/api/player-matches/player/3002/match/3002")
            .then()
                .statusCode(200)
                .body("playerId", is(3002))
                .body("matchId", is(3002));
    }

    @Test
    void getPlayerMatch_notFound_shouldReturn404() {
        given()
            .when().get("/api/player-matches/player/999999/match/999999")
            .then()
                .statusCode(404);
    }

    @Test
    void getPlayerMatchesByMatch_shouldReturnList() {
        // Create match
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3003, \"enabledModuleNames\": []}")
            .when().post("/api/matches");

        // Create player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3003}")
            .when().post("/api/players");

        // Join match
        given()
            .contentType(ContentType.JSON)
            .body("{\"playerId\": 3003, \"matchId\": 3003}")
            .when().post("/api/player-matches");

        // Get by match
        given()
            .when().get("/api/player-matches/match/3003")
            .then()
                .statusCode(200);
    }

    @Test
    void getPlayerMatchesByPlayer_shouldReturnList() {
        // Create match
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3004, \"enabledModuleNames\": []}")
            .when().post("/api/matches");

        // Create player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3004}")
            .when().post("/api/players");

        // Join match
        given()
            .contentType(ContentType.JSON)
            .body("{\"playerId\": 3004, \"matchId\": 3004}")
            .when().post("/api/player-matches");

        // Get by player
        given()
            .when().get("/api/player-matches/player/3004")
            .then()
                .statusCode(200);
    }

    @Test
    void leaveMatch_shouldReturnNoContent() {
        // Create match
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3005, \"enabledModuleNames\": []}")
            .when().post("/api/matches");

        // Create player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 3005}")
            .when().post("/api/players");

        // Join match
        given()
            .contentType(ContentType.JSON)
            .body("{\"playerId\": 3005, \"matchId\": 3005}")
            .when().post("/api/player-matches");

        // Leave match (note: path is /player/{playerId}/match/{matchId})
        given()
            .when().delete("/api/player-matches/player/3005/match/3005")
            .then()
                .statusCode(204);

        // Verify left
        given()
            .when().get("/api/player-matches/player/3005/match/3005")
            .then()
                .statusCode(404);
    }
}
