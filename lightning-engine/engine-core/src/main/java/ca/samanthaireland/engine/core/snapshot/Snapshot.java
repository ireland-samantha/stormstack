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


package ca.samanthaireland.engine.core.snapshot;

import java.util.List;
import java.util.Map;

/**
 * Represents a snapshot of entity component data.
 *
 * <p>Structure: moduleName -> componentName -> [values...]
 *
 * <pre>{@code
 * {
 *     "MovementModule": {
 *         "POSITION_X": [100.0, 200.0, 300.0],
 *         "POSITION_Y": [50.0, 60.0, 70.0],
 *         "VELOCITY_X": [1.5, 2.5, 3.5],
 *         "VELOCITY_Y": [0.0, 0.0, 0.0]
 *     },
 *     "SpawnModule": {
 *         "ENTITY_TYPE": [1.0, 1.0, 2.0],
 *         "OWNER_ID": [100.0, 100.0, 200.0]
 *     }
 * }
 * }</pre>
 *
 * @param snapshot map of module name to component data
 */
public record Snapshot(Map<String, Map<String, List<Float>>> snapshot) {

    private static final Snapshot EMPTY = new Snapshot(Map.of());

    /**
     * Returns an empty snapshot with no entity data.
     *
     * @return an immutable empty snapshot
     */
    public static Snapshot empty() {
        return EMPTY;
    }

    /**
     * Returns true if this snapshot contains no entity data.
     */
    public boolean isEmpty() {
        return snapshot == null || snapshot.isEmpty();
    }

    /**
     * Returns the number of modules in this snapshot.
     */
    public int moduleCount() {
        return snapshot == null ? 0 : snapshot.size();
    }

    /**
     * Returns the data for a specific module.
     *
     * @param moduleName the module name
     * @return the component data for the module, or null if not present
     */
    public Map<String, List<Float>> getModuleData(String moduleName) {
        return snapshot == null ? null : snapshot.get(moduleName);
    }
}
