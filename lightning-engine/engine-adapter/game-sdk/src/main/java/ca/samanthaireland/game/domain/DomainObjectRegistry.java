/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package ca.samanthaireland.game.domain;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Registry for domain objects that are subscribed to ECS components updates.
 *
 * <p>Domain objects register themselves when created and unregister when disposed.
 * The registry notifies listeners when domain objects are added or removed.
 *
 * <p>This is a thread-safe singleton that can be used from any thread.
 */
public final class DomainObjectRegistry {

    private static final DomainObjectRegistry INSTANCE = new DomainObjectRegistry();

    private final Map<Long, DomainObject> objectsByEntityId = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<DomainObject>> addListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<DomainObject>> removeListeners = new CopyOnWriteArrayList<>();

    private DomainObjectRegistry() {
    }

    /**
     * Get the singleton instance.
     *
     * @return the registry instance
     */
    public static DomainObjectRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a domain object.
     *
     * <p>This is called automatically by {@link DomainObject} constructor.
     *
     * @param domainObject the domain object to register
     */
    public void register(DomainObject domainObject) {
        long entityId = domainObject.getEntityId();
        objectsByEntityId.put(entityId, domainObject);
        notifyAddListeners(domainObject);
    }

    /**
     * Unregister a domain object.
     *
     * <p>This is called automatically by {@link DomainObject#dispose()}.
     *
     * @param domainObject the domain object to unregister
     */
    public void unregister(DomainObject domainObject) {
        long entityId = domainObject.getEntityId();
        DomainObject removed = objectsByEntityId.remove(entityId);
        if (removed != null) {
            notifyRemoveListeners(removed);
        }
    }

    /**
     * Get a domain object by entity ID.
     *
     * @param entityId the entity ID
     * @return the domain object, or null if not found
     */
    public DomainObject get(long entityId) {
        return objectsByEntityId.get(entityId);
    }

    /**
     * Get all registered domain objects.
     *
     * @return unmodifiable collection of all domain objects
     */
    public Collection<DomainObject> getAll() {
        return objectsByEntityId.values();
    }

    /**
     * Get the number of registered domain objects.
     *
     * @return the count
     */
    public int size() {
        return objectsByEntityId.size();
    }

    /**
     * Check if a domain object is registered for the given entity ID.
     *
     * @param entityId the entity ID
     * @return true if registered
     */
    public boolean contains(long entityId) {
        return objectsByEntityId.containsKey(entityId);
    }

    /**
     * Add a listener for when domain objects are added.
     *
     * @param listener the listener
     */
    public void addOnRegisterListener(Consumer<DomainObject> listener) {
        addListeners.add(listener);
    }

    /**
     * Remove a registration listener.
     *
     * @param listener the listener to remove
     */
    public void removeOnRegisterListener(Consumer<DomainObject> listener) {
        addListeners.remove(listener);
    }

    /**
     * Add a listener for when domain objects are removed.
     *
     * @param listener the listener
     */
    public void addOnUnregisterListener(Consumer<DomainObject> listener) {
        removeListeners.add(listener);
    }

    /**
     * Remove an unregistration listener.
     *
     * @param listener the listener to remove
     */
    public void removeOnUnregisterListener(Consumer<DomainObject> listener) {
        removeListeners.remove(listener);
    }

    /**
     * Clear all registered domain objects.
     * Useful for testing or reset scenarios.
     */
    public void clear() {
        for (DomainObject obj : objectsByEntityId.values()) {
            notifyRemoveListeners(obj);
        }
        objectsByEntityId.clear();
    }

    private void notifyAddListeners(DomainObject domainObject) {
        for (Consumer<DomainObject> listener : addListeners) {
            try {
                listener.accept(domainObject);
            } catch (Exception e) {
                // Log but don't propagate
            }
        }
    }

    private void notifyRemoveListeners(DomainObject domainObject) {
        for (Consumer<DomainObject> listener : removeListeners) {
            try {
                listener.accept(domainObject);
            } catch (Exception e) {
                // Log but don't propagate
            }
        }
    }
}
