package com.lightningfirefly.engine.internal.core.store;

import com.lightningfirefly.engine.core.store.BaseComponent;
import com.lightningfirefly.engine.core.store.EntityComponentStore;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Decorator that adds query caching to an {@link EntityComponentStore}.
 *
 * <p>This decorator wraps any EntityComponentStore implementation and caches the results
 * of {@link #getEntitiesWithComponents} queries. The cache is automatically
 * invalidated when mutations occur that could affect query results.
 *
 * <p><b>Cache Invalidation Strategy:</b>
 * <ul>
 *   <li>{@link #createEntity(long)} - invalidates all cached queries</li>
 *   <li>{@link #deleteEntity(long)} - invalidates all cached queries</li>
 *   <li>{@link #attachComponent} - invalidates queries containing that component</li>
 *   <li>{@link #attachComponents} - invalidates queries containing any of those components</li>
 *   <li>{@link #removeComponent} - invalidates queries containing that component</li>
 *   <li>{@link #reset()} - clears all cached queries</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * EntityComponentStore baseStore = new ArrayEntityComponentStore(10);
 * EntityComponentStore cachedStore = new CachedEntityComponentStore(baseStore);
 *
 * // First query - cache miss, delegates to base store
 * Set<Long> result1 = cachedStore.getEntitiesWithComponents(0, 1);
 *
 * // Second query - cache hit, returns cached result
 * Set<Long> result2 = cachedStore.getEntitiesWithComponents(0, 1);
 *
 * // Mutation invalidates relevant cache entries
 * cachedStore.attachComponent(entityId, 0, value);
 *
 * // Next query for component 0 will be a cache miss
 * Set<Long> result3 = cachedStore.getEntitiesWithComponents(0, 1);
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This decorator is fully thread-safe. It uses a
 * {@link ReadWriteLock} to allow concurrent read operations while ensuring
 * exclusive access for write operations. Multiple threads can safely:
 * <ul>
 *   <li>Query entities concurrently (read lock)</li>
 *   <li>Mutate entities (write lock ensures exclusive access)</li>
 * </ul>
 *
 * @see EntityComponentStore
 * @see QueryCache
 */
public class CachedEntityComponentStore implements EntityComponentStore {

    private final EntityComponentStore delegate;
    private final QueryCache cache;
    private final ReadWriteLock lock;

    /**
     * Create a cached entity component store wrapping the given delegate.
     *
     * @param delegate the underlying store to wrap
     */
    public CachedEntityComponentStore(EntityComponentStore delegate) {
        this(delegate, new QueryCache(), new ReentrantReadWriteLock());
    }

    /**
     * Create a cached entity component store with a custom cache instance.
     *
     * @param delegate the underlying store to wrap
     * @param cache the query cache to use
     */
    public CachedEntityComponentStore(EntityComponentStore delegate, QueryCache cache) {
        this(delegate, cache, new ReentrantReadWriteLock());
    }

    /**
     * Create a cached entity component store with custom cache and lock.
     * Primarily for testing purposes.
     *
     * @param delegate the underlying store to wrap
     * @param cache the query cache to use
     * @param lock the read-write lock to use
     */
    public CachedEntityComponentStore(EntityComponentStore delegate, QueryCache cache, ReadWriteLock lock) {
        this.delegate = delegate;
        this.cache = cache;
        this.lock = lock;
    }

    /**
     * Get the underlying query cache for diagnostics or manual control.
     *
     * @return the query cache
     */
    public QueryCache getCache() {
        return cache;
    }

    /**
     * Get the underlying delegate store.
     *
     * @return the delegate store
     */
    public EntityComponentStore getDelegate() {
        return delegate;
    }

    /**
     * Get the read-write lock used for thread safety.
     *
     * @return the read-write lock
     */
    public ReadWriteLock getLock() {
        return lock;
    }

    @Override
    public void reset() {
        lock.writeLock().lock();
        try {
            delegate.reset();
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void createEntity(long id) {
        lock.writeLock().lock();
        try {
            delegate.createEntity(id);
            cache.invalidateAll();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteEntity(long id) {
        lock.writeLock().lock();
        try {
            delegate.deleteEntity(id);
            cache.invalidateAll();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeComponent(long id, long componentId) {
        lock.writeLock().lock();
        try {
            delegate.removeComponent(id, componentId);
            cache.invalidateComponent(componentId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeComponent(long id, BaseComponent component) {
        removeComponent(id, component.getId());
    }

    @Override
    public void attachComponent(long id, long componentId, float value) {
        lock.writeLock().lock();
        try {
            delegate.attachComponent(id, componentId, value);
            cache.invalidateComponent(componentId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void attachComponent(long id, BaseComponent component, float value) {
        attachComponent(id, component.getId(), value);
    }

    @Override
    public void attachComponents(long id, long[] componentIds, float[] values) {
        lock.writeLock().lock();
        try {
            delegate.attachComponents(id, componentIds, values);
            for (long componentId : componentIds) {
                cache.invalidateComponent(componentId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void attachComponents(long id, List<BaseComponent> components, float[] values) {
        lock.writeLock().lock();
        try {
            delegate.attachComponents(id, components, values);
            for (BaseComponent component : components) {
                cache.invalidateComponent(component.getId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<Long> getEntitiesWithComponents(long... componentIds) {
        // First try with read lock (allows concurrent reads)
        lock.readLock().lock();
        try {
            Set<Long> cached = cache.get(componentIds);
            if (cached != null) {
                return cached;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Cache miss - need write lock to update cache
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock (another thread may have populated)
            Set<Long> cached = cache.get(componentIds);
            if (cached != null) {
                return cached;
            }

            // Query delegate and cache result
            Set<Long> result = delegate.getEntitiesWithComponents(componentIds);
            cache.put(result, componentIds);
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<Long> getEntitiesWithComponents(BaseComponent... components) {
        long[] ids = new long[components.length];
        for (int i = 0; i < components.length; i++) {
            ids[i] = components[i].getId();
        }
        return getEntitiesWithComponents(ids);
    }

    @Override
    public Set<Long> getEntitiesWithComponents(Collection<BaseComponent> components) {
        return getEntitiesWithComponents(components.toArray(new BaseComponent[0]));
    }

    @Override
    public float[] newBuffer() {
        // No locking needed - this is a factory method with no shared state
        return delegate.newBuffer();
    }

    @Override
    public boolean isNull(float value) {
        // No locking needed - stateless check
        return delegate.isNull(value);
    }

    @Override
    public boolean hasComponent(long id, long componentId) {
        lock.readLock().lock();
        try {
            return delegate.hasComponent(id, componentId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean hasComponent(long id, BaseComponent component) {
        return hasComponent(id, component.getId());
    }

    @Override
    public float getComponent(long id, long componentId) {
        lock.readLock().lock();
        try {
            return delegate.getComponent(id, componentId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public float getComponent(long id, BaseComponent component) {
        return getComponent(id, component.getId());
    }

    @Override
    public void getComponents(long id, long[] componentIds, float[] buf) {
        lock.readLock().lock();
        try {
            delegate.getComponents(id, componentIds, buf);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void getComponents(long id, List<BaseComponent> components, float[] buf) {
        lock.readLock().lock();
        try {
            delegate.getComponents(id, components, buf);
        } finally {
            lock.readLock().unlock();
        }
    }
}
