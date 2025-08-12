package com.lightningfirefly.engine.core.resources;

import java.util.stream.Stream;

public interface ResourceManager {
    /**
     * Save a resource and return the assigned resource ID.
     * If the resource has ID 0 or negative, a new ID will be assigned.
     *
     * @param resource the resource to save
     * @return the assigned resource ID
     */
    long saveResource(Resource resource);
    Stream<Resource> requestResource();
}
