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

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.repository.ServiceClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of OAuth2TokenService that coordinates token issuance.
 *
 * <p>This service:
 * <ul>
 *   <li>Authenticates clients via client_id/client_secret</li>
 *   <li>Validates grant type is supported and allowed for client</li>
 *   <li>Delegates to appropriate grant handler</li>
 *   <li>Logs all authentication decisions</li>
 * </ul>
 */
public class OAuth2TokenServiceImpl implements OAuth2TokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenServiceImpl.class);

    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";

    private final ServiceClientRepository clientRepository;
    private final PasswordService passwordService;
    private final Map<GrantType, OAuth2GrantHandler> grantHandlers;

    /**
     * Creates a new OAuth2TokenService.
     *
     * @param clientRepository the client repository
     * @param passwordService  the password service for verifying client secrets
     * @param grantHandlers    list of grant handlers for supported grant types
     */
    public OAuth2TokenServiceImpl(
            ServiceClientRepository clientRepository,
            PasswordService passwordService,
            List<OAuth2GrantHandler> grantHandlers) {
        this.clientRepository = clientRepository;
        this.passwordService = passwordService;
        this.grantHandlers = new HashMap<>();

        for (OAuth2GrantHandler handler : grantHandlers) {
            this.grantHandlers.put(handler.getGrantType(), handler);
            log.info("Registered OAuth2 grant handler: {}", handler.getGrantType().getValue());
        }
    }

    @Override
    public OAuth2TokenResponse processTokenRequest(Map<String, String> parameters) {
        String grantTypeValue = parameters.get(PARAM_GRANT_TYPE);
        if (grantTypeValue == null || grantTypeValue.isBlank()) {
            log.warn("Token request missing grant_type parameter");
            throw AuthException.invalidRequest("Missing required parameter: grant_type");
        }

        GrantType grantType = GrantType.fromValue(grantTypeValue);
        if (grantType == null) {
            log.warn("Token request with unsupported grant_type: {}", grantTypeValue);
            throw AuthException.unsupportedGrantType(grantTypeValue);
        }

        // Authenticate client if credentials provided
        String clientId = parameters.get(PARAM_CLIENT_ID);
        String clientSecret = parameters.get(PARAM_CLIENT_SECRET);

        ServiceClient client = null;
        if (clientId != null && !clientId.isBlank()) {
            if (clientSecret != null && !clientSecret.isBlank()) {
                client = authenticateClient(clientId, clientSecret);
            } else {
                // Public client or missing secret
                client = getClient(clientId);
                if (client.isConfidential()) {
                    log.warn("Confidential client {} attempted request without secret", clientId);
                    throw AuthException.invalidClient("Client authentication required");
                }
            }
        }

        // Client credentials grant requires authentication
        if (grantType == GrantType.CLIENT_CREDENTIALS && client == null) {
            log.warn("client_credentials grant attempted without client authentication");
            throw AuthException.invalidClient("Client authentication required for client_credentials grant");
        }

        return processTokenRequest(client, parameters);
    }

    @Override
    public OAuth2TokenResponse processTokenRequest(ServiceClient client, Map<String, String> parameters) {
        String grantTypeValue = parameters.get(PARAM_GRANT_TYPE);
        GrantType grantType = GrantType.fromValue(grantTypeValue);

        if (grantType == null) {
            throw AuthException.unsupportedGrantType(grantTypeValue);
        }

        // Check if grant type is supported
        OAuth2GrantHandler handler = grantHandlers.get(grantType);
        if (handler == null) {
            log.warn("No handler registered for grant type: {}", grantType);
            throw AuthException.unsupportedGrantType(grantTypeValue);
        }

        // Check if client is allowed to use this grant type
        if (client != null && !client.isGrantTypeAllowed(grantType)) {
            log.warn("Client {} attempted unauthorized grant type: {}",
                    client.clientId(), grantType);
            throw AuthException.unauthorizedClient(client.clientId().value(), grantTypeValue);
        }

        // Check if client is enabled
        if (client != null && !client.canAuthenticate()) {
            log.warn("Disabled client {} attempted token request", client.clientId());
            throw AuthException.clientDisabled(client.clientId().value());
        }

        // Validate request parameters
        handler.validateRequest(parameters);

        // Handle the grant
        log.info("Processing {} grant for client: {}",
                grantType, client != null ? client.clientId() : "anonymous");

        OAuth2TokenResponse response = handler.handle(client, parameters);

        log.info("Issued {} token for client: {}, scopes: {}",
                grantType, client != null ? client.clientId() : "anonymous", response.scope());

        return response;
    }

    @Override
    public ServiceClient authenticateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw AuthException.invalidRequest("Missing client_id");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw AuthException.invalidRequest("Missing client_secret");
        }

        ServiceClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: client not found: {}", clientId);
                    return AuthException.invalidClient();
                });

        if (!client.isConfidential()) {
            log.warn("Authentication attempted for public client: {}", clientId);
            throw AuthException.invalidClient("Public clients cannot authenticate with client_secret");
        }

        if (!passwordService.verifyPassword(clientSecret, client.clientSecretHash())) {
            log.warn("Authentication failed: invalid secret for client: {}", clientId);
            throw AuthException.invalidClient();
        }

        if (!client.canAuthenticate()) {
            log.warn("Authentication failed: client disabled: {}", clientId);
            throw AuthException.clientDisabled(clientId);
        }

        log.debug("Client authenticated successfully: {}", clientId);
        return client;
    }

    @Override
    public ServiceClient getClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw AuthException.invalidRequest("Missing client_id");
        }

        return clientRepository.findByClientId(clientId)
                .orElseThrow(() -> {
                    log.warn("Client not found: {}", clientId);
                    return AuthException.clientNotFound(clientId);
                });
    }
}
