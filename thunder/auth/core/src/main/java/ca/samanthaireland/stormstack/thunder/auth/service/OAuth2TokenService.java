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

import ca.samanthaireland.stormstack.thunder.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;

import java.util.Map;

/**
 * Service for handling OAuth2 token endpoint requests.
 *
 * <p>This service orchestrates the token issuance process by:
 * <ol>
 *   <li>Authenticating the client (if required)</li>
 *   <li>Validating the grant type</li>
 *   <li>Delegating to the appropriate grant handler</li>
 *   <li>Returning the token response</li>
 * </ol>
 */
public interface OAuth2TokenService {

    /**
     * Processes a token request from the OAuth2 token endpoint.
     *
     * @param parameters the form-encoded request parameters
     * @return the token response
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the request is invalid
     */
    OAuth2TokenResponse processTokenRequest(Map<String, String> parameters);

    /**
     * Processes a token request with pre-authenticated client.
     *
     * <p>This method is used when the client has already been authenticated
     * via HTTP Basic auth or another mechanism.
     *
     * @param client     the authenticated client
     * @param parameters the form-encoded request parameters
     * @return the token response
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the request is invalid
     */
    OAuth2TokenResponse processTokenRequest(ServiceClient client, Map<String, String> parameters);

    /**
     * Authenticates a client using client_id and client_secret.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret
     * @return the authenticated client
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if authentication fails
     */
    ServiceClient authenticateClient(String clientId, String clientSecret);

    /**
     * Looks up a client by ID without authentication.
     *
     * <p>This is used for public clients that don't have a secret.
     *
     * @param clientId the client ID
     * @return the client
     * @throws ca.samanthaireland.stormstack.thunder.auth.exception.AuthException if the client is not found
     */
    ServiceClient getClient(String clientId);
}
