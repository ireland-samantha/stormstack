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

/**
 * Strongly-typed identifier for an OAuth2 service client.
 *
 * <p>Unlike other IDs which use UUIDs, client IDs use human-readable strings
 * for easier configuration and debugging (e.g., "control-plane", "game-server").
 *
 * @param value the client ID string (e.g., "control-plane")
 */
public record ServiceClientId(String value) {

    public ServiceClientId {
        Objects.requireNonNull(value, "ServiceClientId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ServiceClientId cannot be blank");
        }
        if (!value.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$") && value.length() > 1) {
            if (!value.matches("^[a-z0-9]$")) {
                throw new IllegalArgumentException(
                        "ServiceClientId must be lowercase alphanumeric with hyphens, " +
                        "starting and ending with alphanumeric: " + value);
            }
        }
    }

    /**
     * Creates a ServiceClientId from a string.
     *
     * @param value the client ID string
     * @return the ServiceClientId
     * @throws IllegalArgumentException if the value is invalid
     */
    public static ServiceClientId of(String value) {
        return new ServiceClientId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
