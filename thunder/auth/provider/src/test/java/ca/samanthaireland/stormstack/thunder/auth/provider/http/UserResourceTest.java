/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.provider.MongoTestResource;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.CreateUserRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class UserResourceTest {

    @Test
    void createUser_withValidRequest_returns201() {
        String uniqueUsername = "newuser_" + UUID.randomUUID().toString().substring(0, 8);

        given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest(uniqueUsername, "password123", Set.of(), Set.of()))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .header("Location", containsString("/api/users/"))
            .body("username", equalTo(uniqueUsername))
            .body("enabled", equalTo(true));
    }

    @Test
    void createUser_withDuplicateUsername_returns409() {
        String username = "duplicate_" + UUID.randomUUID().toString().substring(0, 8);

        // Create first user
        given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest(username, "password123", Set.of(), Set.of()))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);

        // Try to create duplicate
        given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest(username, "password456", Set.of(), Set.of()))
        .when()
            .post("/api/users")
        .then()
            .statusCode(409)
            .body("code", equalTo("USERNAME_TAKEN"));
    }

    @Test
    void listUsers_returnsAllUsers() {
        given()
        .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    void getUser_withValidId_returnsUser() {
        String uniqueUsername = "getuser_" + UUID.randomUUID().toString().substring(0, 8);

        // Create user and get ID
        String userId = given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest(uniqueUsername, "password123", Set.of(), Set.of()))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Get by ID
        given()
        .when()
            .get("/api/users/" + userId)
        .then()
            .statusCode(200)
            .body("id", equalTo(userId))
            .body("username", equalTo(uniqueUsername));
    }

    @Test
    void getUser_withInvalidId_returns404() {
        given()
        .when()
            .get("/api/users/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("code", equalTo("USER_NOT_FOUND"));
    }

    @Test
    void deleteUser_withValidId_returns204() {
        String uniqueUsername = "deleteuser_" + UUID.randomUUID().toString().substring(0, 8);

        // Create user
        String userId = given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest(uniqueUsername, "password123", Set.of(), Set.of()))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Delete
        given()
        .when()
            .delete("/api/users/" + userId)
        .then()
            .statusCode(204);

        // Verify deleted
        given()
        .when()
            .get("/api/users/" + userId)
        .then()
            .statusCode(404);
    }
}
