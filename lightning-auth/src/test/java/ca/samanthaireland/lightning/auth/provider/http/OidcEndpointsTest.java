/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.provider.MongoTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for OIDC Discovery and JWKS endpoints.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class OidcEndpointsTest {

    // =========================================================================
    // OIDC Discovery Endpoint Tests
    // =========================================================================

    @Test
    void discovery_returns200WithValidConfiguration() {
        given()
        .when()
            .get("/.well-known/openid-configuration")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("issuer", notNullValue())
            .body("token_endpoint", containsString("/oauth2/token"))
            .body("jwks_uri", containsString("/.well-known/jwks.json"))
            .body("userinfo_endpoint", containsString("/oauth2/userinfo"))
            .body("grant_types_supported", hasItems(
                    "client_credentials",
                    "password",
                    "refresh_token",
                    "urn:ietf:params:oauth:grant-type:token-exchange"
            ))
            .body("response_types_supported", hasItem("token"))
            .body("token_endpoint_auth_methods_supported", hasItems(
                    "client_secret_basic",
                    "client_secret_post"
            ));
    }

    @Test
    void discovery_includesSubjectTypesSupported() {
        given()
        .when()
            .get("/.well-known/openid-configuration")
        .then()
            .statusCode(200)
            .body("subject_types_supported", hasItem("public"));
    }

    @Test
    void discovery_includesIdTokenSigningAlgValues() {
        given()
        .when()
            .get("/.well-known/openid-configuration")
        .then()
            .statusCode(200)
            .body("id_token_signing_alg_values_supported", anyOf(
                    hasItem("RS256"),
                    hasItem("HS256")
            ));
    }

    // =========================================================================
    // JWKS Endpoint Tests
    // =========================================================================

    @Test
    void jwks_returns200WithKeys() {
        given()
        .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("keys", notNullValue())
            .body("keys.size()", greaterThan(0));
    }

    @Test
    void jwks_containsValidKeyFields() {
        given()
        .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            .body("keys[0].kty", anyOf(equalTo("RSA"), equalTo("oct")))
            .body("keys[0].alg", anyOf(equalTo("RS256"), equalTo("HS256")))
            .body("keys[0].use", equalTo("sig"));
    }

    @Test
    void jwks_rsaKeyHasRequiredFields() {
        given()
        .when()
            .get("/.well-known/jwks.json")
        .then()
            .statusCode(200)
            // If RSA key is present, it should have n and e
            // If HMAC, this test passes because the key won't have kty=RSA
            .body("keys.findAll { it.kty == 'RSA' }.every { it.n != null && it.e != null }", is(true));
    }

    // =========================================================================
    // UserInfo Endpoint Tests
    // =========================================================================

    @Test
    void userInfo_withValidToken_returns200WithClaims() {
        // Get a user token
        String accessToken = OAuth2TestHelper.getAdminToken();

        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/oauth2/userinfo")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("sub", notNullValue())
            .body("preferred_username", notNullValue());
    }

    @Test
    void userInfo_withoutToken_returns401Or400() {
        given()
        .when()
            .get("/oauth2/userinfo")
        .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    void userInfo_withInvalidToken_returns401Or400() {
        given()
            .header("Authorization", "Bearer invalid.token.here")
        .when()
            .get("/oauth2/userinfo")
        .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    void userInfo_postMethod_alsoWorks() {
        String accessToken = OAuth2TestHelper.getAdminToken();

        given()
            .header("Authorization", "Bearer " + accessToken)
            .contentType("application/x-www-form-urlencoded")
        .when()
            .post("/oauth2/userinfo")
        .then()
            .statusCode(200)
            .body("sub", notNullValue());
    }

    @Test
    void userInfo_includesUserRolesAndScopes() {
        String accessToken = OAuth2TestHelper.getAdminToken();

        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/oauth2/userinfo")
        .then()
            .statusCode(200)
            .body("roles", notNullValue())
            .body("scopes", notNullValue());
    }

    @Test
    void userInfo_includesEnabledStatus() {
        String accessToken = OAuth2TestHelper.getAdminToken();

        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/oauth2/userinfo")
        .then()
            .statusCode(200)
            .body("enabled", is(true));
    }

    // =========================================================================
    // Cross-Endpoint Integration Tests
    // =========================================================================

    @Test
    void discoveredJwksUri_isAccessible() {
        // Get the JWKS URI from discovery
        String jwksUri = given()
        .when()
            .get("/.well-known/openid-configuration")
        .then()
            .statusCode(200)
            .extract()
            .path("jwks_uri");

        // Extract just the path part for testing
        String jwksPath = jwksUri.substring(jwksUri.indexOf("/.well-known"));

        // Verify it's accessible
        given()
        .when()
            .get(jwksPath)
        .then()
            .statusCode(200)
            .body("keys", notNullValue());
    }

    @Test
    void discoveredTokenEndpoint_isAccessible() {
        // Get the token endpoint from discovery
        String tokenEndpoint = given()
        .when()
            .get("/.well-known/openid-configuration")
        .then()
            .statusCode(200)
            .extract()
            .path("token_endpoint");

        // Extract path
        String tokenPath = tokenEndpoint.substring(tokenEndpoint.indexOf("/oauth2"));

        // Verify it's accessible (will return 400 due to missing params, but proves endpoint exists)
        given()
            .contentType("application/x-www-form-urlencoded")
        .when()
            .post(tokenPath)
        .then()
            .statusCode(400); // Missing grant_type, but endpoint exists
    }
}
