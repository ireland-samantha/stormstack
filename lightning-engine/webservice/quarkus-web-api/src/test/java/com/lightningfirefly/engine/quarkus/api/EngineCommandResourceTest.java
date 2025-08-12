package com.lightningfirefly.engine.quarkus.api;

import com.lightningfirefly.engine.core.command.EngineCommand;
import com.lightningfirefly.engine.core.command.CommandPayload;
import com.lightningfirefly.engine.internal.core.command.CommandResolver;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class EngineCommandResourceTest {

    // submitCommand needs to return 404 when not found

    @InjectMock
    CommandResolver commandResolver;

    @BeforeEach
    void setUp() {
        Mockito.when(commandResolver.resolveByName("testCommand"))
                .thenReturn(new EngineCommand() {
                    @Override
                    public String getName() {
                        return "testCommand";
                    }

                    @Override
                    public Map<String, Class<?>> schema() {
                        return Map.of();
                    }

                    @Override
                    public void executeCommand(CommandPayload payload) {

                    }
                });
    }

    @Test
    void submitCommand_shouldReturnAccepted() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"commandName\": \"testCommand\", \"payload\": {\"key\": \"value\"}}")
            .when().post("/api/commands")
            .then()
                .statusCode(202)
                .body("status", is("accepted"))
                .body("commandName", is("testCommand"));
    }

    @Test
    void submitCommand_withUnknownCommand_shouldReturnNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"commandName\": \"emptyCommand\", \"payload\": {}}")
            .when().post("/api/commands")
            .then()
                .statusCode(404);
    }
}
