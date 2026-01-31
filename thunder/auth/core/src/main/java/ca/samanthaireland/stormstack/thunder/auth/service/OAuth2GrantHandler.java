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

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.model.GrantType;
import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;

import java.util.Map;

/**
 * Strategy interface for handling OAuth2 grant types.
 *
 * <p>Each implementation handles a specific grant type (client_credentials,
 * password, refresh_token, token_exchange) and is responsible for validating
 * the request parameters and issuing tokens.
 */
public interface OAuth2GrantHandler {

    /**
     * Gets the grant type this handler supports.
     *
     * @return the grant type
     */
    GrantType getGrantType();

    /**
     * Handles a token request for this grant type.
     *
     * @param client     the authenticated client (may be null for public clients)
     * @param parameters the request parameters from the token endpoint
     * @return the token response
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the request is invalid
     */
    OAuth2TokenResponse handle(ServiceClient client, Map<String, String> parameters);

    /**
     * Validates that the request has all required parameters for this grant type.
     *
     * @param parameters the request parameters
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if validation fails
     */
    void validateRequest(Map<String, String> parameters);
}
