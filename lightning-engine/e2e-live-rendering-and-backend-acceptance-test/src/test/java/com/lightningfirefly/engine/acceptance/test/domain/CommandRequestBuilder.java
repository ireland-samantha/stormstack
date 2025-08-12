package com.lightningfirefly.engine.acceptance.test.domain;

/**
 * Builder for creating command request JSON payloads.
 *
 * <p>Usage:
 * <pre>{@code
 * String json = CommandRequestBuilder.command("spawn")
 *     .param("matchId", 1L)
 *     .param("playerId", 1L)
 *     .param("entityType", 100L)
 *     .build();
 * // Result: {"commandName": "spawn", "payload": {"matchId": 1, "playerId": 1, "entityType": 100}}
 * }</pre>
 */
public class CommandRequestBuilder {
    private final String commandName;
    private final StringBuilder payload = new StringBuilder();
    private boolean firstParam = true;

    private CommandRequestBuilder(String commandName) {
        this.commandName = commandName;
    }

    /**
     * Create a new builder for a command.
     *
     * @param commandName the command name (e.g., "spawn", "attachMovement")
     * @return a new builder instance
     */
    public static CommandRequestBuilder command(String commandName) {
        return new CommandRequestBuilder(commandName);
    }

    /**
     * Add a long parameter.
     *
     * @param key   the parameter name
     * @param value the parameter value
     * @return this builder for chaining
     */
    public CommandRequestBuilder param(String key, long value) {
        appendParam(key, String.valueOf(value));
        return this;
    }

    /**
     * Add an int parameter.
     *
     * @param key   the parameter name
     * @param value the parameter value
     * @return this builder for chaining
     */
    public CommandRequestBuilder param(String key, int value) {
        appendParam(key, String.valueOf(value));
        return this;
    }

    /**
     * Add a double parameter.
     *
     * @param key   the parameter name
     * @param value the parameter value
     * @return this builder for chaining
     */
    public CommandRequestBuilder param(String key, double value) {
        appendParam(key, String.valueOf(value));
        return this;
    }

    /**
     * Add a string parameter.
     *
     * @param key   the parameter name
     * @param value the parameter value
     * @return this builder for chaining
     */
    public CommandRequestBuilder param(String key, String value) {
        appendParam(key, "\"" + escapeJson(value) + "\"");
        return this;
    }

    /**
     * Add a boolean parameter.
     *
     * @param key   the parameter name
     * @param value the parameter value
     * @return this builder for chaining
     */
    public CommandRequestBuilder param(String key, boolean value) {
        appendParam(key, String.valueOf(value));
        return this;
    }

    private void appendParam(String key, String value) {
        if (!firstParam) {
            payload.append(", ");
        }
        payload.append("\"").append(key).append("\": ").append(value);
        firstParam = false;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Build the JSON request string.
     *
     * @return the JSON string for the command request
     */
    public String build() {
        return String.format("{\"commandName\": \"%s\", \"payload\": {%s}}", commandName, payload);
    }

    /**
     * Get the command name.
     *
     * @return the command name
     */
    public String getCommandName() {
        return commandName;
    }
}
