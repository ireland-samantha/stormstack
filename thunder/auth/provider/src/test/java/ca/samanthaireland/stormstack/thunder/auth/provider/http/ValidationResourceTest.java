/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.provider.MongoTestResource;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.ValidateTokenRequest;
import ca.samanthaireland.stormstack.thunder.auth.service.RoleService;
import ca.samanthaireland.stormstack.thunder.auth.service.UserService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class ValidationResourceTest {

    @Inject
    UserService userService;

    @Inject
    RoleService roleService;

    @BeforeAll
    static void setupParser() {
        RestAssured.defaultParser = Parser.JSON;
    }

    @BeforeEach
    void setUp() {
        // Ensure test user exists
        if (userService.findByUsername("validationuser").isEmpty()) {
            var viewOnlyRole = roleService.findByName("view_only").orElseThrow();
            userService.createUser("validationuser", "password123", Set.of(viewOnlyRole.id()));
        }
    }

    @Test
    void validate_withValidToken_returnsClaims() {
        // Login using OAuth2 password grant (admin-cli is a public client)
        Response loginResponse = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", "validationuser")
            .formParam("password", "password123")
        .when()
            .post("/oauth2/token");

        loginResponse.then().statusCode(200);
        String token = loginResponse.path("access_token");
        assertNotNull(token, "Token should not be null");

        // Validate the token
        given()
            .contentType(ContentType.JSON)
            .body(new ValidateTokenRequest(token))
        .when()
            .post("/api/validate")
        .then()
            .statusCode(200)
            .body("username", equalTo("validationuser"))
            .body("userId", notNullValue())
            .body("expiresAt", notNullValue())
            // OAuth2 tokens include scopes instead of roles
            .body("scopes", notNullValue());
    }

    @Test
    void validate_withInvalidToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(new ValidateTokenRequest("invalid.jwt.token"))
        .when()
            .post("/api/validate")
        .then()
            .statusCode(401)
            .body("code", equalTo("INVALID_TOKEN"));
    }

    @Test
    void validate_withEmptyToken_returnsError() {
        // Empty token should return an error (400, 401, or 500 depending on validation)
        int status = given()
            .contentType(ContentType.JSON)
            .body(new ValidateTokenRequest(""))
        .when()
            .post("/api/validate")
        .then()
            .extract()
            .statusCode();

        // Accept any of these as valid error responses
        assertThat(status, anyOf(equalTo(400), equalTo(401), equalTo(500)));
    }

    @Test
    void validate_withMissingToken_returnsError() {
        // Missing token field should return an error
        int status = given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/api/validate")
        .then()
            .extract()
            .statusCode();

        // Accept any of these as valid error responses
        assertThat(status, anyOf(equalTo(400), equalTo(401), equalTo(500)));
    }
}
