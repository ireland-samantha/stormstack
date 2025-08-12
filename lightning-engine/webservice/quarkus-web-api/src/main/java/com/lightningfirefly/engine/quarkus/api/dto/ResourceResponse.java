package com.lightningfirefly.engine.quarkus.api.dto;

import com.lightningfirefly.engine.core.resources.ResourceType;

public record ResourceResponse(
    long resourceId,
    String resourceName,
    ResourceType resourceType,
    long size
) {}
