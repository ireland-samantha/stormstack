/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.http;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/**
 * Test helper for OAuth2 authentication in integration tests.
 *
 * <p>Provides methods to authenticate using OAuth2 grant types and extract
 * access tokens for use in subsequent API calls.
 */
public final class OAuth2TestHelper {

    private OAuth2TestHelper() {
        // Utility class
    }

    /**
     * Authenticate using the OAuth2 password grant and return the access token.
     *
     * <p>Uses the "admin-cli" public client which is configured to allow password grant.
     *
     * @param username the username
     * @param password the password
     * @return the access token (JWT)
     */
    public static String getAccessToken(String username, String password) {
        Response response = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "password")
            .formParam("client_id", "admin-cli")
            .formParam("username", username)
            .formParam("password", password)
        .when()
            .post("/oauth2/token");

        response.then().statusCode(200);
        return response.path("access_token");
    }

    /**
     * Authenticate as admin using the OAuth2 password grant.
     *
     * @return the admin access token (JWT)
     */
    public static String getAdminToken() {
        return getAccessToken("admin", "admin");
    }

    /**
     * Authenticate using client credentials grant.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret
     * @return the access token (JWT)
     */
    public static String getClientCredentialsToken(String clientId, String clientSecret) {
        Response response = given()
            .contentType(ContentType.URLENC)
            .formParam("grant_type", "client_credentials")
            .auth().preemptive().basic(clientId, clientSecret)
        .when()
            .post("/oauth2/token");

        response.then().statusCode(200);
        return response.path("access_token");
    }
}
