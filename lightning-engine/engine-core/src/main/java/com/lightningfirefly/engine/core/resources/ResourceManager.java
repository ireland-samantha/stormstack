package com.lightningfirefly.engine.core.resources;

import java.util.Optional;
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

    /**
     * Get all resources.
     *
     * @return stream of all resources
     */
    Stream<Resource> requestResource();

    /**
     * Find a resource by its name.
     *
     * @param name the resource name to search for
     * @return Optional containing the resource if found, or empty if not found
     */
    default Optional<Resource> findByName(String name) {
        return requestResource()
                .filter(r -> name.equals(r.resourceName()))
                .findFirst();
    }

    /**
     * Get the resource ID for a resource with the given name.
     *
     * @param name the resource name
     * @return the resource ID, or -1 if not found
     */
    default long getResourceIdByName(String name) {
        return findByName(name)
                .map(Resource::resourceId)
                .orElse(-1L);
    }
}
