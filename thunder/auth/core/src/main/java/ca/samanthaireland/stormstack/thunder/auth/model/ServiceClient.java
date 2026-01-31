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

package ca.samanthaireland.stormstack.thunder.auth.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an OAuth2 service client registered with the authorization server.
 *
 * <p>Service clients are applications that can request access tokens from
 * the auth service. This includes backend services (control plane, game servers)
 * and frontend applications (admin tools, game clients).
 *
 * <p>Client secrets are stored as BCrypt hashes for security.
 *
 * @param clientId          unique client identifier (e.g., "control-plane")
 * @param clientSecretHash  BCrypt hash of the client secret (null for public clients)
 * @param clientType        whether the client is confidential or public
 * @param displayName       human-readable name for the client
 * @param allowedScopes     scopes this client is permitted to request
 * @param allowedGrantTypes grant types this client can use
 * @param createdAt         when the client was registered
 * @param enabled           whether the client can request tokens
 */
public record ServiceClient(
        ServiceClientId clientId,
        String clientSecretHash,
        ClientType clientType,
        String displayName,
        Set<String> allowedScopes,
        Set<GrantType> allowedGrantTypes,
        Instant createdAt,
        boolean enabled
) {

    public ServiceClient {
        Objects.requireNonNull(clientId, "Client ID cannot be null");
        Objects.requireNonNull(clientType, "Client type cannot be null");
        Objects.requireNonNull(displayName, "Display name cannot be null");
        Objects.requireNonNull(allowedScopes, "Allowed scopes cannot be null");
        Objects.requireNonNull(allowedGrantTypes, "Allowed grant types cannot be null");
        Objects.requireNonNull(createdAt, "Created at cannot be null");

        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be blank");
        }

        if (clientType == ClientType.CONFIDENTIAL && (clientSecretHash == null || clientSecretHash.isBlank())) {
            throw new IllegalArgumentException("Confidential clients must have a client secret");
        }

        if (allowedGrantTypes.isEmpty()) {
            throw new IllegalArgumentException("Client must have at least one allowed grant type");
        }

        // Validate grant types for client type
        if (clientType == ClientType.PUBLIC && allowedGrantTypes.contains(GrantType.CLIENT_CREDENTIALS)) {
            throw new IllegalArgumentException("Public clients cannot use client_credentials grant");
        }

        // Defensive copies
        allowedScopes = Set.copyOf(allowedScopes);
        allowedGrantTypes = Set.copyOf(allowedGrantTypes);
    }

    /**
     * Creates a new confidential service client.
     *
     * @param clientId         the client ID
     * @param clientSecretHash BCrypt hash of the client secret
     * @param displayName      human-readable name
     * @param allowedScopes    permitted scopes
     * @param allowedGrantTypes permitted grant types
     * @return the new ServiceClient
     */
    public static ServiceClient createConfidential(
            String clientId,
            String clientSecretHash,
            String displayName,
            Set<String> allowedScopes,
            Set<GrantType> allowedGrantTypes) {
        return new ServiceClient(
                ServiceClientId.of(clientId),
                clientSecretHash,
                ClientType.CONFIDENTIAL,
                displayName,
                allowedScopes,
                allowedGrantTypes,
                Instant.now(),
                true
        );
    }

    /**
     * Creates a new public client (no secret).
     *
     * @param clientId          the client ID
     * @param displayName       human-readable name
     * @param allowedScopes     permitted scopes
     * @param allowedGrantTypes permitted grant types
     * @return the new ServiceClient
     */
    public static ServiceClient createPublic(
            String clientId,
            String displayName,
            Set<String> allowedScopes,
            Set<GrantType> allowedGrantTypes) {
        return new ServiceClient(
                ServiceClientId.of(clientId),
                null,
                ClientType.PUBLIC,
                displayName,
                allowedScopes,
                allowedGrantTypes,
                Instant.now(),
                true
        );
    }

    /**
     * Checks if this client is allowed to use the specified grant type.
     *
     * @param grantType the grant type to check
     * @return true if the grant type is allowed
     */
    public boolean isGrantTypeAllowed(GrantType grantType) {
        return allowedGrantTypes.contains(grantType);
    }

    /**
     * Checks if this client is allowed to request the specified scope.
     *
     * @param scope the scope to check
     * @return true if the scope is allowed
     */
    public boolean isScopeAllowed(String scope) {
        return allowedScopes.contains(scope) || allowedScopes.contains("*");
    }

    /**
     * Checks if this client is allowed to request all specified scopes.
     *
     * @param scopes the scopes to check
     * @return true if all scopes are allowed
     */
    public boolean areAllScopesAllowed(Set<String> scopes) {
        if (allowedScopes.contains("*")) {
            return true;
        }
        return scopes.stream().allMatch(this::isScopeAllowed);
    }

    /**
     * Filters requested scopes to only those allowed for this client.
     *
     * @param requestedScopes the scopes requested
     * @return the intersection of requested and allowed scopes
     */
    public Set<String> filterAllowedScopes(Set<String> requestedScopes) {
        if (allowedScopes.contains("*")) {
            return requestedScopes;
        }
        return requestedScopes.stream()
                .filter(this::isScopeAllowed)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Checks if this client is a confidential client.
     *
     * @return true if the client type is CONFIDENTIAL
     */
    public boolean isConfidential() {
        return clientType == ClientType.CONFIDENTIAL;
    }

    /**
     * Checks if this client can currently request tokens.
     *
     * @return true if enabled
     */
    public boolean canAuthenticate() {
        return enabled;
    }

    /**
     * Creates a copy of this client with enabled status changed.
     *
     * @param newEnabled the new enabled status
     * @return the updated ServiceClient
     */
    public ServiceClient withEnabled(boolean newEnabled) {
        return new ServiceClient(
                clientId, clientSecretHash, clientType, displayName,
                allowedScopes, allowedGrantTypes, createdAt, newEnabled
        );
    }

    /**
     * Creates a copy of this client with a new secret hash.
     *
     * @param newSecretHash the new BCrypt hash
     * @return the updated ServiceClient
     */
    public ServiceClient withSecretHash(String newSecretHash) {
        return new ServiceClient(
                clientId, newSecretHash, clientType, displayName,
                allowedScopes, allowedGrantTypes, createdAt, enabled
        );
    }
}
