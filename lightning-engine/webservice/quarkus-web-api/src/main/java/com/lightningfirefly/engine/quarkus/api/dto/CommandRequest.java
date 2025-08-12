package com.lightningfirefly.engine.quarkus.api.dto;

import java.util.Map;

public record CommandRequest(String commandName, Map<String, Object> payload) {
}
