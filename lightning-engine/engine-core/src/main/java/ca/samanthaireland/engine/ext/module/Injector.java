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


package ca.samanthaireland.engine.ext.module;

import ca.samanthaireland.engine.core.store.EntityComponentStore;

/**
 * Legacy interface for module dependency injection.
 *
 * @deprecated Use {@link ModuleContext} instead. This interface is maintained
 * for backward compatibility and will be removed in a future release.
 *
 * <p>Migration guide:
 * <ul>
 *   <li>Replace {@code Injector context} with {@code ModuleContext context}</li>
 *   <li>Replace {@code context.getStoreRequired()} with {@code context.getEntityComponentStore()}</li>
 *   <li>Replace {@code context.getClass(SomeService.class)} with explicit methods like
 *       {@code context.getMatchService()}</li>
 * </ul>
 *
 * @see ModuleContext
 */
@Deprecated
public interface Injector extends ModuleContext {

    /**
     * Get the entity component store.
     *
     * @return the ECS store for managing entities and their components
     * @throws NullPointerException if no store is registered
     * @deprecated Use {@link ModuleContext#getEntityComponentStore()} instead
     */
    @Deprecated
    EntityComponentStore getStoreRequired();

    /**
     * {@inheritDoc}
     */
    @Override
    default EntityComponentStore getEntityComponentStore() {
        return getStoreRequired();
    }
}
