/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.provider.MongoTestResource;
import ca.samanthaireland.lightning.auth.provider.dto.CreateApiTokenRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class ApiTokenResourceTest {

    private String adminToken;

    @BeforeAll
    static void setupParser() {
        RestAssured.defaultParser = Parser.JSON;
    }

    @BeforeEach
    void setUp() {
        // Login as admin using OAuth2 password grant
        adminToken = OAuth2TestHelper.getAdminToken();
        assertNotNull(adminToken, "Admin token should not be null");
    }

    @Test
    void create_asAdmin_returnsTokenWithPlaintext() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new CreateApiTokenRequest("test-token", Set.of("read"), null))
        .when()
            .post("/api/tokens")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("test-token"))
            .body("plaintextToken", notNullValue())
            .body("scopes", hasItem("read"));
    }

    @Test
    void list_asAdmin_returnsTokens() {
        // First create a token
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new CreateApiTokenRequest("list-test-token", Set.of("read"), null))
        .when()
            .post("/api/tokens")
        .then()
            .statusCode(201);

        // Then list tokens
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .get("/api/tokens")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    void delete_existingToken_returns204() {
        // First create a token
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new CreateApiTokenRequest("delete-test-token", Set.of("read"), null))
        .when()
            .post("/api/tokens");

        createResponse.then().statusCode(201);
        String tokenId = createResponse.path("id");
        assertNotNull(tokenId, "Token ID should not be null");

        // Then delete it
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .delete("/api/tokens/" + tokenId)
        .then()
            .statusCode(204);
    }

    // Note: Authentication is now handled by @Scopes annotation with custom filter.
    // Security matrix tests are defined separately in SecurityMatrixTest.
}
