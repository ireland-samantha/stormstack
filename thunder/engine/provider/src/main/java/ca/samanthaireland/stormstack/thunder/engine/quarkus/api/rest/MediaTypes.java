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

package ca.samanthaireland.stormstack.thunder.engine.quarkus.api.rest;

/**
 * Media type constants for API versioning.
 *
 * <p>Lightning Engine uses media type versioning (content negotiation) for API versioning.
 * Clients should specify the desired API version in the Accept header:
 * <pre>
 * Accept: application/vnd.lightning.v1+json
 * </pre>
 *
 * <p>The API also accepts plain {@code application/json} for backward compatibility,
 * which defaults to the latest version.
 */
public final class MediaTypes {

    private MediaTypes() {
        // Utility class
    }

    /**
     * Current API version number.
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Vendor prefix for Lightning Engine media types.
     */
    public static final String VENDOR_PREFIX = "application/vnd.lightning";

    /**
     * API version 1 media type.
     * Format: application/vnd.lightning.v1+json
     */
    public static final String V1_JSON = VENDOR_PREFIX + ".v1+json";

    /**
     * Standard JSON media type (for backward compatibility).
     * Requests with this type will use the latest API version.
     */
    public static final String JSON = "application/json";

    /**
     * All supported media types for API v1.
     * Resources should accept both versioned and plain JSON.
     */
    public static final String[] V1_TYPES = {V1_JSON, JSON};
}
