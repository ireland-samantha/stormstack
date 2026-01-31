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

/**
 * OAuth2 client type as defined in RFC 6749 Section 2.1.
 *
 * <p>The client type determines how the client authenticates and what
 * grant types it can use.
 */
public enum ClientType {

    /**
     * Confidential clients are capable of maintaining the confidentiality
     * of their credentials (e.g., client secret).
     *
     * <p>Examples: server-side applications, control plane, game servers.
     * These clients can use client credentials grant.
     */
    CONFIDENTIAL,

    /**
     * Public clients cannot maintain the confidentiality of their credentials.
     *
     * <p>Examples: browser-based applications, mobile apps, game clients.
     * These clients cannot use client credentials grant and must use
     * PKCE for authorization code flow.
     */
    PUBLIC
}
