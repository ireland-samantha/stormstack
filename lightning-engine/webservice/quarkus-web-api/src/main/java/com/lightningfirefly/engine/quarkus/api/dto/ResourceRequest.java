package com.lightningfirefly.engine.quarkus.api.dto;

import com.lightningfirefly.engine.core.resources.ResourceType;

public record ResourceRequest(
    String resourceName,
    ResourceType resourceType
) {}
