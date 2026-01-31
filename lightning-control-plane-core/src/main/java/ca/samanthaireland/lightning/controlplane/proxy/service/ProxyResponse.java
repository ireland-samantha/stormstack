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

package ca.samanthaireland.lightning.controlplane.proxy.service;

import java.util.Map;
import java.util.Objects;

/**
 * Response from a proxied request to a node.
 *
 * @param statusCode the HTTP status code from the upstream node
 * @param headers    the response headers from the upstream node
 * @param body       the response body from the upstream node
 */
public record ProxyResponse(
        int statusCode,
        Map<String, String> headers,
        byte[] body
) {

    public ProxyResponse {
        Objects.requireNonNull(headers, "headers cannot be null");
    }

    /**
     * Creates an empty successful response.
     *
     * @return a 200 OK response with no body
     */
    public static ProxyResponse ok() {
        return new ProxyResponse(200, Map.of(), new byte[0]);
    }

    /**
     * Creates a response with a status code and body.
     *
     * @param statusCode the HTTP status code
     * @param body       the response body
     * @return a new ProxyResponse
     */
    public static ProxyResponse of(int statusCode, byte[] body) {
        return new ProxyResponse(statusCode, Map.of(), body != null ? body : new byte[0]);
    }

    /**
     * Checks if this is a successful response (2xx status code).
     *
     * @return true if status code is between 200 and 299
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Gets the Content-Type header value if present.
     *
     * @return the content type or null
     */
    public String getContentType() {
        return headers.get("Content-Type");
    }

    /**
     * Checks if the response has a body.
     *
     * @return true if the body is non-null and non-empty
     */
    public boolean hasBody() {
        return body != null && body.length > 0;
    }
}
