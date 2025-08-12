package com.lightningfirefly.engine.core.resources;

public interface Resource {
    long resourceId();
    String resourceName();
    String resourcePath();
    byte[] blob();
    ResourceType resourceType();
}
