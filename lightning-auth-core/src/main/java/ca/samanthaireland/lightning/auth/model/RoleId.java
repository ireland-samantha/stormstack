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

package ca.samanthaireland.lightning.auth.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for a Role.
 *
 * @param value the UUID value of the role ID
 */
public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "RoleId value cannot be null");
    }

    /**
     * Creates a new random RoleId.
     *
     * @return a new RoleId with a random UUID
     */
    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    /**
     * Creates a RoleId from a string representation.
     *
     * @param value the string UUID
     * @return the RoleId
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static RoleId fromString(String value) {
        return new RoleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
