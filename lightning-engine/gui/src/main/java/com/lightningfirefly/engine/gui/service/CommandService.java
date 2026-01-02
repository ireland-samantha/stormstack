package com.lightningfirefly.engine.gui.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing commands via REST API.
 */
@Slf4j
public class CommandService {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final List<Consumer<CommandEvent>> listeners = new CopyOnWriteArrayList<>();

    public CommandService(String serverUrl) {
        this.baseUrl = serverUrl + "/api/commands";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Add a listener for command events.
     */
    public void addListener(Consumer<CommandEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(Consumer<CommandEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * List all available commands.
     */
    public CompletableFuture<List<CommandInfo>> listCommands() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseCommandList(response.body());
                    } else {
                        log.error("Failed to list commands: {} {}", response.statusCode(), response.body());
                        notifyListeners(new CommandEvent(CommandEventType.ERROR, null,
                                "Failed to list commands: " + response.statusCode()));
                        return new ArrayList<CommandInfo>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list commands", e);
                    notifyListeners(new CommandEvent(CommandEventType.ERROR, null, e.getMessage()));
                    return new ArrayList<CommandInfo>();
                });
    }

    /**
     * List available commands for a specific module.
     */
    public CompletableFuture<List<CommandInfo>> listCommands(String moduleName) {
        String url = baseUrl + "?module=" + moduleName;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseCommandList(response.body());
                    } else {
                        log.error("Failed to list commands for module {}: {} {}",
                                moduleName, response.statusCode(), response.body());
                        return new ArrayList<CommandInfo>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list commands for module {}", moduleName, e);
                    return new ArrayList<CommandInfo>();
                });
    }

    /**
     * Submit a command.
     */
    public CompletableFuture<Boolean> submitCommand(String commandName, Map<String, Object> payload) {
        String payloadJson = buildPayloadJson(payload);
        String body = String.format("{\"commandName\":\"%s\",\"payload\":{%s}}", commandName, payloadJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 201 || response.statusCode() == 202) {
                        log.info("Command {} submitted successfully", commandName);
                        notifyListeners(new CommandEvent(CommandEventType.SUBMITTED, commandName,
                                "Command accepted"));
                        return true;
                    } else {
                        log.error("Failed to submit command {}: {} {}", commandName, response.statusCode(), response.body());
                        notifyListeners(new CommandEvent(CommandEventType.ERROR, commandName,
                                "Failed: " + response.statusCode()));
                        return false;
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to submit command {}", commandName, e);
                    notifyListeners(new CommandEvent(CommandEventType.ERROR, commandName, e.getMessage()));
                    return false;
                });
    }

    /**
     * Submit a command with match and entity context.
     */
    public CompletableFuture<Boolean> submitCommand(long matchId, String commandName, long entityId,
                                                     Map<String, Object> additionalPayload) {
        Map<String, Object> fullPayload = new java.util.HashMap<>(additionalPayload != null ? additionalPayload : Map.of());
//        fullPayload.put("matchId", matchId);
//        fullPayload.put("entityId", entityId);
        return submitCommand(commandName, fullPayload);
    }

    private String buildPayloadJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private void notifyListeners(CommandEvent event) {
        for (Consumer<CommandEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in command event listener", e);
            }
        }
    }

    private List<CommandInfo> parseCommandList(String json) {
        List<CommandInfo> commands = new ArrayList<>();
        // Parse JSON array of command signatures like ["move(long targetX, long targetY)", "spawn(String entityType)"]
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String signature = matcher.group(1);
            CommandInfo info = parseCommandSignature(signature);
            if (info != null) {
                commands.add(info);
            }
        }
        return commands;
    }

    private CommandInfo parseCommandSignature(String signature) {
        // Parse "commandName(paramType paramName, ...)" format
        int parenOpen = signature.indexOf('(');
        int parenClose = signature.lastIndexOf(')');
        if (parenOpen == -1 || parenClose == -1) {
            // Simple command name without params
            return new CommandInfo(signature, List.of());
        }

        String name = signature.substring(0, parenOpen);
        String paramsStr = signature.substring(parenOpen + 1, parenClose);

        List<ParameterInfo> params = new ArrayList<>();
        if (!paramsStr.isBlank()) {
            String[] paramParts = paramsStr.split(",");
            for (String param : paramParts) {
                param = param.trim();
                String[] parts = param.split("\\s+");
                if (parts.length >= 2) {
                    // Format: "type name" (e.g., "long entityId")
                    String paramType = parts[0];
                    String paramName = parts[1];
                    params.add(new ParameterInfo(paramName, paramType));
                }
            }
        }

        return new CommandInfo(name, params);
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // HttpClient doesn't need explicit shutdown
    }

    /**
     * Command information record.
     */
    public record CommandInfo(String name, List<ParameterInfo> parameters) {
        public String getSignature() {
            if (parameters.isEmpty()) {
                return name;
            }
            StringBuilder sb = new StringBuilder(name).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                ParameterInfo p = parameters.get(i);
                sb.append(p.type()).append(" ").append(p.name());
            }
            return sb.append(")").toString();
        }
    }

    /**
     * Command parameter information.
     */
    public record ParameterInfo(String name, String type) {}

    /**
     * Command event types.
     */
    public enum CommandEventType {
        SUBMITTED, ERROR
    }

    /**
     * Command event record.
     */
    public record CommandEvent(CommandEventType type, String commandName, String message) {}

    /**
     * Module commands record for grouped display.
     */
    public record ModuleCommands(String moduleName, List<CommandInfo> commands) {}

    /**
     * List all commands grouped by module.
     */
    public CompletableFuture<List<ModuleCommands>> listGroupedCommands() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/grouped"))
                .GET()
                .header("Accept", "application/json")
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return parseGroupedCommandList(response.body());
                    } else {
                        log.error("Failed to list grouped commands: {} {}", response.statusCode(), response.body());
                        notifyListeners(new CommandEvent(CommandEventType.ERROR, null,
                                "Failed to list grouped commands: " + response.statusCode()));
                        return new ArrayList<ModuleCommands>();
                    }
                })
                .exceptionally(e -> {
                    log.error("Failed to list grouped commands", e);
                    notifyListeners(new CommandEvent(CommandEventType.ERROR, null, e.getMessage()));
                    return new ArrayList<ModuleCommands>();
                });
    }

    private List<ModuleCommands> parseGroupedCommandList(String json) {
        List<ModuleCommands> result = new ArrayList<>();
        // Parse JSON object: {"ModuleName": ["cmd1(params)", "cmd2(params)"], ...}
        // Simple parsing for the expected format
        Pattern modulePattern = Pattern.compile("\"([^\"]+)\":\\s*\\[([^\\]]+)\\]");
        Matcher moduleMatcher = modulePattern.matcher(json);
        while (moduleMatcher.find()) {
            String moduleName = moduleMatcher.group(1);
            String commandsStr = moduleMatcher.group(2);

            List<CommandInfo> commands = new ArrayList<>();
            Pattern cmdPattern = Pattern.compile("\"([^\"]+)\"");
            Matcher cmdMatcher = cmdPattern.matcher(commandsStr);
            while (cmdMatcher.find()) {
                String signature = cmdMatcher.group(1);
                CommandInfo info = parseCommandSignature(signature);
                if (info != null) {
                    commands.add(info);
                }
            }
            if (!commands.isEmpty()) {
                result.add(new ModuleCommands(moduleName, commands));
            }
        }
        return result;
    }
}
