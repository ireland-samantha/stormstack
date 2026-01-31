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

import ca.samanthaireland.lightning.auth.config.OAuth2Configuration;
import ca.samanthaireland.lightning.auth.model.ClientType;
import ca.samanthaireland.lightning.auth.model.GrantType;
import ca.samanthaireland.lightning.auth.model.ServiceClient;
import ca.samanthaireland.lightning.auth.model.ServiceClientId;
import ca.samanthaireland.lightning.auth.repository.ServiceClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration-driven implementation of ServiceClientRepository.
 *
 * <p>Loads service clients from application configuration at startup.
 * Client secrets in config are hashed using the provided password service.
 *
 * <p>This implementation is read-only for config-loaded clients but allows
 * runtime additions via {@link #save(ServiceClient)}.
 */
public class ConfigBasedServiceClientRepository implements ServiceClientRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfigBasedServiceClientRepository.class);

    private final Map<ServiceClientId, ServiceClient> clients = new ConcurrentHashMap<>();
    private final Function<String, String> passwordHasher;

    /**
     * Creates a new config-based repository.
     *
     * @param config         the OAuth2 configuration containing client definitions
     * @param passwordHasher function to hash client secrets (e.g., BCrypt)
     */
    public ConfigBasedServiceClientRepository(
            OAuth2Configuration config,
            Function<String, String> passwordHasher) {
        this.passwordHasher = passwordHasher;
        loadClientsFromConfig(config);
    }

    private void loadClientsFromConfig(OAuth2Configuration config) {
        var clientList = config.clients();
        if (clientList == null || clientList.isEmpty()) {
            log.warn("No OAuth2 clients configured");
            return;
        }

        for (OAuth2Configuration.ServiceClientConfig clientConfig : clientList) {
            try {
                ServiceClient client = createClientFromConfig(clientConfig);
                clients.put(client.clientId(), client);
                log.info("Loaded OAuth2 client: {} ({})",
                        client.clientId(), client.clientType());
            } catch (Exception e) {
                log.error("Failed to load OAuth2 client '{}': {}",
                        clientConfig.clientId(), e.getMessage());
            }
        }

        log.info("Loaded {} OAuth2 clients from configuration", clients.size());
    }

    private ServiceClient createClientFromConfig(OAuth2Configuration.ServiceClientConfig config) {
        ServiceClientId clientId = ServiceClientId.of(config.clientId());

        ClientType clientType = "public".equalsIgnoreCase(config.clientType())
                ? ClientType.PUBLIC
                : ClientType.CONFIDENTIAL;

        String secretHash = null;
        if (clientType == ClientType.CONFIDENTIAL) {
            if (config.clientSecret() == null || config.clientSecret().isBlank()) {
                throw new IllegalArgumentException(
                        "Confidential client '" + config.clientId() + "' must have a client_secret");
            }
            secretHash = passwordHasher.apply(config.clientSecret());
        }

        Set<String> allowedScopes = new HashSet<>(config.allowedScopes());

        Set<GrantType> allowedGrantTypes = config.allowedGrantTypes().stream()
                .map(GrantType::fromValue)
                .filter(gt -> gt != null)
                .collect(Collectors.toSet());

        if (allowedGrantTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Client '" + config.clientId() + "' has no valid grant types");
        }

        return new ServiceClient(
                clientId,
                secretHash,
                clientType,
                config.displayName(),
                allowedScopes,
                allowedGrantTypes,
                Instant.now(),
                config.enabled()
        );
    }

    @Override
    public Optional<ServiceClient> findByClientId(ServiceClientId clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    @Override
    public List<ServiceClient> findAll() {
        return List.copyOf(clients.values());
    }

    @Override
    public List<ServiceClient> findAllEnabled() {
        return clients.values().stream()
                .filter(ServiceClient::enabled)
                .toList();
    }

    @Override
    public ServiceClient save(ServiceClient client) {
        clients.put(client.clientId(), client);
        log.debug("Saved OAuth2 client: {}", client.clientId());
        return client;
    }

    @Override
    public boolean deleteByClientId(ServiceClientId clientId) {
        ServiceClient removed = clients.remove(clientId);
        if (removed != null) {
            log.debug("Deleted OAuth2 client: {}", clientId);
            return true;
        }
        return false;
    }

    @Override
    public boolean existsByClientId(ServiceClientId clientId) {
        return clients.containsKey(clientId);
    }

    @Override
    public long count() {
        return clients.size();
    }
}
