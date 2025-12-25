package com.lightningfirefly.engine.gui.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommandRequestBuilder")
class CommandRequestBuilderTest {

    @Nested
    @DisplayName("command creation")
    class CommandCreation {

        @Test
        @DisplayName("should create spawn command with all parameters")
        void createSpawnCommand() {
            String json = CommandRequestBuilder.command("spawn")
                    .param("matchId", 1L)
                    .param("playerId", 2L)
                    .param("entityType", 100L)
                    .build();

            assertThat(json).isEqualTo(
                    "{\"commandName\": \"spawn\", \"payload\": {\"matchId\": 1, \"playerId\": 2, \"entityType\": 100}}");
        }

        @Test
        @DisplayName("should create attachMovement command")
        void createAttachMovementCommand() {
            String json = CommandRequestBuilder.command("attachMovement")
                    .param("entityId", 1L)
                    .param("positionX", 200L)
                    .param("positionY", 150L)
                    .param("positionZ", 0L)
                    .param("velocityX", 0L)
                    .param("velocityY", 0L)
                    .param("velocityZ", 0L)
                    .build();

            assertThat(json).contains("\"commandName\": \"attachMovement\"");
            assertThat(json).contains("\"entityId\": 1");
            assertThat(json).contains("\"positionX\": 200");
            assertThat(json).contains("\"positionY\": 150");
        }

        @Test
        @DisplayName("should create attachSprite command")
        void createAttachSpriteCommand() {
            String json = CommandRequestBuilder.command("attachSprite")
                    .param("entityId", 1L)
                    .param("resourceId", 42L)
                    .build();

            assertThat(json).isEqualTo(
                    "{\"commandName\": \"attachSprite\", \"payload\": {\"entityId\": 1, \"resourceId\": 42}}");
        }

        @Test
        @DisplayName("should create command with no parameters")
        void createCommandWithNoParams() {
            String json = CommandRequestBuilder.command("tick").build();

            assertThat(json).isEqualTo("{\"commandName\": \"tick\", \"payload\": {}}");
        }
    }

    @Nested
    @DisplayName("parameter types")
    class ParameterTypes {

        @Test
        @DisplayName("should handle int parameters")
        void handleIntParams() {
            String json = CommandRequestBuilder.command("test")
                    .param("value", 42)
                    .build();

            assertThat(json).contains("\"value\": 42");
        }

        @Test
        @DisplayName("should handle long parameters")
        void handleLongParams() {
            String json = CommandRequestBuilder.command("test")
                    .param("value", 9999999999L)
                    .build();

            assertThat(json).contains("\"value\": 9999999999");
        }

        @Test
        @DisplayName("should handle double parameters")
        void handleDoubleParams() {
            String json = CommandRequestBuilder.command("test")
                    .param("value", 3.14159)
                    .build();

            assertThat(json).contains("\"value\": 3.14159");
        }

        @Test
        @DisplayName("should handle boolean parameters")
        void handleBooleanParams() {
            String json = CommandRequestBuilder.command("test")
                    .param("enabled", true)
                    .param("disabled", false)
                    .build();

            assertThat(json).contains("\"enabled\": true");
            assertThat(json).contains("\"disabled\": false");
        }

        @Test
        @DisplayName("should handle string parameters with quotes")
        void handleStringParams() {
            String json = CommandRequestBuilder.command("test")
                    .param("name", "hello world")
                    .build();

            assertThat(json).contains("\"name\": \"hello world\"");
        }

        @Test
        @DisplayName("should escape special characters in strings")
        void escapeSpecialCharacters() {
            String json = CommandRequestBuilder.command("test")
                    .param("text", "line1\nline2")
                    .build();

            assertThat(json).contains("\"text\": \"line1\\nline2\"");
        }

        @Test
        @DisplayName("should escape quotes in strings")
        void escapeQuotesInStrings() {
            String json = CommandRequestBuilder.command("test")
                    .param("text", "say \"hello\"")
                    .build();

            assertThat(json).contains("\"text\": \"say \\\"hello\\\"\"");
        }
    }

    @Nested
    @DisplayName("builder state")
    class BuilderState {

        @Test
        @DisplayName("should return command name")
        void returnCommandName() {
            CommandRequestBuilder builder = CommandRequestBuilder.command("myCommand");

            assertThat(builder.getCommandName()).isEqualTo("myCommand");
        }

        @Test
        @DisplayName("should allow chaining multiple parameters")
        void allowChaining() {
            String json = CommandRequestBuilder.command("test")
                    .param("a", 1L)
                    .param("b", 2L)
                    .param("c", 3L)
                    .param("d", 4L)
                    .param("e", 5L)
                    .build();

            assertThat(json).contains("\"a\": 1");
            assertThat(json).contains("\"b\": 2");
            assertThat(json).contains("\"c\": 3");
            assertThat(json).contains("\"d\": 4");
            assertThat(json).contains("\"e\": 5");
        }
    }
}
