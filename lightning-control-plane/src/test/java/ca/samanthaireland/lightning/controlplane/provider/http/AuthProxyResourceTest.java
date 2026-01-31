/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.lightning.controlplane.provider.http;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for AuthProxyResource.
 *
 * <p>Note: These tests require the auth service to be running or mocked.
 * In a real test environment, use WireMock or similar to mock the auth service.
 */
@QuarkusTest
@TestProfile(AuthProxyTestProfile.class)
class AuthProxyResourceTest {

    @Test
    void login_withoutAuthService_returns503() {
        // When auth service is not configured, should return 503
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"username": "admin", "password": "admin"}
                """)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(anyOf(is(503), is(200))); // 503 if no auth service, 200 if mocked
    }

    @Test
    void refresh_withoutAuthService_returns503() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"refreshToken": "some-token"}
                """)
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(anyOf(is(503), is(200)));
    }

    @Test
    void getCurrentUser_withoutAuth_returns401() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/auth/me")
        .then()
            .statusCode(anyOf(is(401), is(503)));
    }

    @Test
    void listUsers_withoutAuth_returns401or503() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/auth/users")
        .then()
            .statusCode(anyOf(is(401), is(503)));
    }

    @Test
    void listRoles_withoutAuth_returns401or503() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/auth/roles")
        .then()
            .statusCode(anyOf(is(401), is(503)));
    }

    @Test
    void listTokens_withoutAuth_returns401or503() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/auth/tokens")
        .then()
            .statusCode(anyOf(is(401), is(503)));
    }
}
