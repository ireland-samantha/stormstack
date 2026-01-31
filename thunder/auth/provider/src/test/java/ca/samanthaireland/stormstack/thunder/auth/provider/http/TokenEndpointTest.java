/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.provider.MongoTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the OAuth2 Token Endpoint.
 *
 * <p>Tests all supported grant types:
 * <ul>
 *   <li>client_credentials</li>
 *   <li>password</li>
 *   <li>refresh_token</li>
 * </ul>
 *
 * <p>Uses the admin user created by AuthBootstrap for password grant tests.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class TokenEndpointTest {

    // Admin user is created by AuthBootstrap with username "admin" and password "admin"
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "admin";

    // =========================================================================
    // Client Credentials Grant Tests
    // =========================================================================

    @Test
    void clientCredentials_withValidCredentials_returns200WithToken() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .auth().preemptive().basic("control-plane", "control-plane-secret")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("expires_in", greaterThan(0))
            .body("scope", notNullValue());
    }

    @Test
    void clientCredentials_withFormAuth_returns200WithToken() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .formParam("client_id", "control-plane")
            .formParam("client_secret", "control-plane-secret")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"));
    }

    @Test
    void clientCredentials_withInvalidSecret_returns401() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .auth().preemptive().basic("control-plane", "wrong-secret")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", containsString("Basic"))
            .body("error", equalTo("invalid_client"));
    }

    @Test
    void clientCredentials_withUnknownClient_returns401() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .auth().preemptive().basic("unknown-client", "secret")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    @Test
    void clientCredentials_withNoAuth_returns401() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    @Test
    void clientCredentials_withRequestedScopes_filtersToAllowed() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .formParam("scope", "service.match-token.issue")
            .auth().preemptive().basic("control-plane", "control-plane-secret")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .body("scope", containsString("service.match-token.issue"));
    }

    // =========================================================================
    // Password Grant Tests
    // =========================================================================

    @Test
    void password_withValidCredentials_returns200WithTokens() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("refresh_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("expires_in", greaterThan(0));
    }

    @Test
    void password_withInvalidUsername_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", "nonexistent-user")
            .formParam("password", "password")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void password_withInvalidPassword_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", TEST_USERNAME)
            .formParam("password", "wrong-password")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void password_withMissingUsername_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("password", "password")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void password_withMissingPassword_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", TEST_USERNAME)
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    // =========================================================================
    // Refresh Token Grant Tests
    // =========================================================================

    @Test
    void refreshToken_withValidToken_returns200WithNewTokens() {
        // First get a refresh token via password grant
        String refreshToken = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", TEST_USERNAME)
            .formParam("password", TEST_PASSWORD)
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .extract()
            .path("refresh_token");

        // Use the refresh token
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("client_id", "admin-cli")
            .formParam("refresh_token", refreshToken)
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("refresh_token", notNullValue())
            .body("token_type", equalTo("Bearer"));
    }

    @Test
    void refreshToken_withInvalidToken_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", "invalid.refresh.token")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_grant"));
    }

    @Test
    void refreshToken_withMissingToken_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "refresh_token")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    // =========================================================================
    // General Error Tests
    // =========================================================================

    @Test
    void token_withMissingGrantType_returns400() {
        given()
            .contentType(ContentType.URLENC)
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("invalid_request"));
    }

    @Test
    void token_withUnsupportedGrantType_returns400() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "unsupported_grant")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    void token_withWrongContentType_returns415() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grant_type\": \"client_credentials\"}")
        .when()
            .post("/oauth2/token")
        .then()
            .statusCode(415);
    }

    // =========================================================================
    // Token Validation Tests
    // =========================================================================

    @Test
    void issuedToken_canBeUsedToAccessProtectedEndpoint() {
        // Get a token
        String accessToken = OAuth2TestHelper.getAdminToken();

        // Use it to access a protected endpoint
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/api/users")
        .then()
            .statusCode(200);
    }

    @Test
    void issuedClientCredentialsToken_hasCorrectFormat() {
        // Get a service token
        String accessToken = OAuth2TestHelper.getClientCredentialsToken("control-plane", "control-plane-secret");

        // Verify the token is a valid JWT (3 parts separated by dots)
        assertThat(accessToken).isNotNull();
        assertThat(accessToken.split("\\.")).hasSize(3);
    }
}
