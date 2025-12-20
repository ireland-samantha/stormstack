package com.lightningfirefly.engine.gui.service;

import com.lightningfirefly.engine.gui.service.CommandService.CommandInfo;
import com.lightningfirefly.engine.gui.service.CommandService.ParameterInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CommandService command signature parsing.
 *
 * The server returns command signatures in the "class java.lang.Type name" format,
 * e.g., "DeleteMoveable(class java.lang.Long id)".
 */
class CommandServiceTest {

    private CommandService commandService;
    private Method parseCommandSignatureMethod;
    private Method parseCommandListMethod;

    @BeforeEach
    void setUp() throws Exception {
        commandService = new CommandService("http://localhost:8080");

        // Access private methods for testing
        parseCommandSignatureMethod = CommandService.class.getDeclaredMethod("parseCommandSignature", String.class);
        parseCommandSignatureMethod.setAccessible(true);

        parseCommandListMethod = CommandService.class.getDeclaredMethod("parseCommandList", String.class);
        parseCommandListMethod.setAccessible(true);
    }

    private CommandInfo parseSignature(String signature) throws Exception {
        return (CommandInfo) parseCommandSignatureMethod.invoke(commandService, signature);
    }

    @SuppressWarnings("unchecked")
    private List<CommandInfo> parseList(String json) throws Exception {
        return (List<CommandInfo>) parseCommandListMethod.invoke(commandService, json);
    }

    @Nested
    @DisplayName("parseCommandSignature")
    class ParseCommandSignature {

        @Test
        @DisplayName("parses single Long parameter")
        void parsesSingleLongParam() throws Exception {
            CommandInfo info = parseSignature("DeleteMoveable(class java.lang.Long id)");

            assertThat(info.name()).isEqualTo("DeleteMoveable");
            assertThat(info.parameters()).hasSize(1);
            assertThat(info.parameters().get(0).name()).isEqualTo("id");
            assertThat(info.parameters().get(0).type()).isEqualTo("java.lang.Long");
        }

        @Test
        @DisplayName("parses multiple Long parameters")
        void parsesMultipleLongParams() throws Exception {
            CommandInfo info = parseSignature(
                "CreateMoveableCommand(class java.lang.Long positionX, class java.lang.Long positionY, " +
                "class java.lang.Long velocityX, class java.lang.Long velocityY)"
            );

            assertThat(info.name()).isEqualTo("CreateMoveableCommand");
            assertThat(info.parameters()).hasSize(4);
            assertThat(info.parameters().get(0)).isEqualTo(new ParameterInfo("positionX", "java.lang.Long"));
            assertThat(info.parameters().get(1)).isEqualTo(new ParameterInfo("positionY", "java.lang.Long"));
            assertThat(info.parameters().get(2)).isEqualTo(new ParameterInfo("velocityX", "java.lang.Long"));
            assertThat(info.parameters().get(3)).isEqualTo(new ParameterInfo("velocityY", "java.lang.Long"));
        }

        @Test
        @DisplayName("parses String parameter")
        void parsesStringParam() throws Exception {
            CommandInfo info = parseSignature("setName(class java.lang.String name)");

            assertThat(info.name()).isEqualTo("setName");
            assertThat(info.parameters()).hasSize(1);
            assertThat(info.parameters().get(0).name()).isEqualTo("name");
            assertThat(info.parameters().get(0).type()).isEqualTo("java.lang.String");
        }

        @Test
        @DisplayName("parses command with no parameters")
        void parsesNoParams() throws Exception {
            CommandInfo info = parseSignature("shutdown()");

            assertThat(info.name()).isEqualTo("shutdown");
            assertThat(info.parameters()).isEmpty();
        }

        @Test
        @DisplayName("parses command name without parentheses")
        void parsesNameOnly() throws Exception {
            CommandInfo info = parseSignature("simpleCommand");

            assertThat(info.name()).isEqualTo("simpleCommand");
            assertThat(info.parameters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseCommandList")
    class ParseCommandList {

        @Test
        @DisplayName("parses JSON array of command signatures")
        void parsesJsonArray() throws Exception {
            String json = "[\"DeleteMoveable(class java.lang.Long id)\", \"spawn(class java.lang.Long matchId)\"]";

            List<CommandInfo> commands = parseList(json);

            assertThat(commands).hasSize(2);
            assertThat(commands.get(0).name()).isEqualTo("DeleteMoveable");
            assertThat(commands.get(0).parameters().get(0).type()).isEqualTo("java.lang.Long");
            assertThat(commands.get(1).name()).isEqualTo("spawn");
            assertThat(commands.get(1).parameters().get(0).type()).isEqualTo("java.lang.Long");
        }

        @Test
        @DisplayName("parses empty JSON array")
        void parsesEmptyArray() throws Exception {
            List<CommandInfo> commands = parseList("[]");

            assertThat(commands).isEmpty();
        }
    }

    @Nested
    @DisplayName("CommandInfo.getSignature()")
    class GetSignature {

        @Test
        @DisplayName("generates signature string")
        void generatesSignature() throws Exception {
            CommandInfo info = parseSignature("DeleteMoveable(class java.lang.Long id)");

            assertThat(info.getSignature()).isEqualTo("DeleteMoveable(java.lang.Long id)");
        }

        @Test
        @DisplayName("generates signature with multiple parameters")
        void generatesMultiParamSignature() throws Exception {
            CommandInfo info = parseSignature("move(class java.lang.Long x, class java.lang.Long y)");

            assertThat(info.getSignature()).isEqualTo("move(java.lang.Long x, java.lang.Long y)");
        }
    }
}
