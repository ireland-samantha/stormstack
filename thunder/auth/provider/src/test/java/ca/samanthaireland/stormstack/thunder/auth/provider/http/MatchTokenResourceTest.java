/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.provider.MongoTestResource;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.IssueMatchTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.ValidateMatchTokenRequest;
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
class MatchTokenResourceTest {

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
    void create_asAdmin_returnsMatchToken() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new IssueMatchTokenRequest(
                "match-456",
                "container-1",
                "player-123",
                null,
                "TestPlayer",
                Set.of("submit_commands", "view_snapshots"),
                8
            ))
        .when()
            .post("/api/match-tokens")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("playerId", equalTo("player-123"))
            .body("matchId", equalTo("match-456"))
            .body("playerName", equalTo("TestPlayer"))
            .body("token", notNullValue())
            .body("scopes", hasItems("submit_commands", "view_snapshots"));
    }

    @Test
    void validate_withValidToken_returnsClaims() {
        // First create a match token
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new IssueMatchTokenRequest(
                "match-validate",
                null,
                "player-validate",
                null,
                "ValidatePlayer",
                Set.of("submit_commands"),
                8
            ))
        .when()
            .post("/api/match-tokens");

        createResponse.then().statusCode(201);
        String matchToken = createResponse.path("token");
        assertNotNull(matchToken, "Match token should not be null");

        // Validate it
        given()
            .contentType(ContentType.JSON)
            .body(new ValidateMatchTokenRequest(matchToken, "match-validate", null))
        .when()
            .post("/api/match-tokens/validate")
        .then()
            .statusCode(200)
            .body("playerId", equalTo("player-validate"))
            .body("matchId", equalTo("match-validate"))
            .body("playerName", equalTo("ValidatePlayer"))
            .body("scopes", hasItem("submit_commands"));
    }

    @Test
    void validate_withWrongMatchId_returns403() {
        // First create a match token
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new IssueMatchTokenRequest(
                "match-correct",
                null,
                "player-wrong-match",
                null,
                "Player",
                Set.of("submit_commands"),
                8
            ))
        .when()
            .post("/api/match-tokens");

        createResponse.then().statusCode(201);
        String matchToken = createResponse.path("token");
        assertNotNull(matchToken, "Match token should not be null");

        // Try to validate with wrong match ID
        given()
            .contentType(ContentType.JSON)
            .body(new ValidateMatchTokenRequest(matchToken, "match-wrong", null))
        .when()
            .post("/api/match-tokens/validate")
        .then()
            .statusCode(403);
    }

    @Test
    void validate_withInvalidToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(new ValidateMatchTokenRequest("invalid-token", "match-123", null))
        .when()
            .post("/api/match-tokens/validate")
        .then()
            .statusCode(401);
    }

    @Test
    void revoke_existingToken_returns200() {
        // First create a match token
        Response createResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new IssueMatchTokenRequest(
                "match-revoke",
                null,
                "player-revoke",
                null,
                "RevokePlayer",
                Set.of("submit_commands"),
                8
            ))
        .when()
            .post("/api/match-tokens");

        createResponse.then().statusCode(201);
        String tokenId = createResponse.path("id");
        assertNotNull(tokenId, "Token ID should not be null");

        // Revoke it (using POST to /revoke endpoint)
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .post("/api/match-tokens/" + tokenId + "/revoke")
        .then()
            .statusCode(200)
            .body("revokedCount", equalTo(1));
    }

    @Test
    void listByMatch_asAdmin_returnsTokens() {
        // First create a match token
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(new IssueMatchTokenRequest(
                "match-list-test",
                null,
                "player-list",
                null,
                "ListPlayer",
                Set.of("submit_commands"),
                8
            ))
        .when()
            .post("/api/match-tokens")
        .then()
            .statusCode(201);

        // List tokens for that match
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .get("/api/match-tokens/match/match-list-test")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    // Note: Authentication is now handled by @Scopes annotation with custom filter.
    // Security matrix tests are defined separately in SecurityMatrixTest.
}
