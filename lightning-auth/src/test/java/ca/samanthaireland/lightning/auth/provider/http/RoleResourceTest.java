/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.provider.MongoTestResource;
import ca.samanthaireland.lightning.auth.provider.dto.CreateRoleRequest;
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
class RoleResourceTest {

    @Test
    void listRoles_returnsDefaultRoles() {
        given()
        .when()
            .get("/api/roles")
        .then()
            .statusCode(200)
            .body("$", not(empty()))
            .body("name", hasItems("admin", "command_submit", "view_only"));
    }

    @Test
    void createRole_withValidRequest_returns201() {
        String uniqueName = "testrole_" + UUID.randomUUID().toString().substring(0, 8);

        given()
            .contentType(ContentType.JSON)
            .body(new CreateRoleRequest(uniqueName, "A test role", Set.of(), Set.of()))
        .when()
            .post("/api/roles")
        .then()
            .statusCode(201)
            .header("Location", containsString("/api/roles/"))
            .body("name", equalTo(uniqueName))
            .body("description", equalTo("A test role"));
    }

    @Test
    void createRole_withDuplicateName_returns409() {
        String roleName = "duplicate_" + UUID.randomUUID().toString().substring(0, 8);

        // Create first role
        given()
            .contentType(ContentType.JSON)
            .body(new CreateRoleRequest(roleName, "First role", Set.of(), Set.of()))
        .when()
            .post("/api/roles")
        .then()
            .statusCode(201);

        // Try to create duplicate
        given()
            .contentType(ContentType.JSON)
            .body(new CreateRoleRequest(roleName, "Second role", Set.of(), Set.of()))
        .when()
            .post("/api/roles")
        .then()
            .statusCode(409)
            .body("code", equalTo("ROLE_NAME_TAKEN"));
    }

    @Test
    void getRole_withValidId_returnsRole() {
        String uniqueName = "getrole_" + UUID.randomUUID().toString().substring(0, 8);

        // Create role
        String roleId = given()
            .contentType(ContentType.JSON)
            .body(new CreateRoleRequest(uniqueName, "Test role", Set.of(), Set.of()))
        .when()
            .post("/api/roles")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Get by ID
        given()
        .when()
            .get("/api/roles/" + roleId)
        .then()
            .statusCode(200)
            .body("id", equalTo(roleId))
            .body("name", equalTo(uniqueName));
    }

    @Test
    void getRole_withInvalidId_returns404() {
        given()
        .when()
            .get("/api/roles/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("code", equalTo("ROLE_NOT_FOUND"));
    }

    @Test
    void deleteRole_withValidId_returns204() {
        String uniqueName = "deleterole_" + UUID.randomUUID().toString().substring(0, 8);

        // Create role
        String roleId = given()
            .contentType(ContentType.JSON)
            .body(new CreateRoleRequest(uniqueName, "Test role", Set.of(), Set.of()))
        .when()
            .post("/api/roles")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Delete
        given()
        .when()
            .delete("/api/roles/" + roleId)
        .then()
            .statusCode(204);

        // Verify deleted
        given()
        .when()
            .get("/api/roles/" + roleId)
        .then()
            .statusCode(404);
    }
}
