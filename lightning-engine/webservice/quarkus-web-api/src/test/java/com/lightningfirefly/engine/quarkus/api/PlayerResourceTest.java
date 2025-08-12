package com.lightningfirefly.engine.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class PlayerResourceTest {

    @Test
    void createPlayer_shouldReturnCreated() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 2001}")
            .when().post("/api/players")
            .then()
                .statusCode(201)
                .body("id", is(2001));
    }

    @Test
    void getPlayer_shouldReturnPlayer() {
        // Create player first
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 2002}")
            .when().post("/api/players")
            .then()
                .statusCode(201);

        // Get the player
        given()
            .when().get("/api/players/2002")
            .then()
                .statusCode(200)
                .body("id", is(2002));
    }

    @Test
    void getPlayer_notFound_shouldReturn404() {
        given()
            .when().get("/api/players/999999")
            .then()
                .statusCode(404);
    }

    @Test
    void getAllPlayers_shouldReturnList() {
        // Create a player
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 2003}")
            .when().post("/api/players");

        given()
            .when().get("/api/players")
            .then()
                .statusCode(200);
    }

    @Test
    void deletePlayer_shouldReturnNoContent() {
        // Create player first
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 2004}")
            .when().post("/api/players")
            .then()
                .statusCode(201);

        // Delete the player
        given()
            .when().delete("/api/players/2004")
            .then()
                .statusCode(204);

        // Verify it's deleted
        given()
            .when().get("/api/players/2004")
            .then()
                .statusCode(404);
    }

    @Test
    void deletePlayer_notFound_shouldReturn404() {
        given()
            .when().delete("/api/players/999998")
            .then()
                .statusCode(404);
    }
}
