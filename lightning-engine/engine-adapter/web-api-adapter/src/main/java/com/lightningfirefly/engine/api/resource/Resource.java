package com.lightningfirefly.engine.api.resource;

public record Resource(long resourceId, byte[] blob, String resourceType, String resourceName) {

    public Resource(long resourceId, byte[] blob, String resourceType) {
        this(resourceId, blob, resourceType, "Resource-" + resourceId);
    }
}
