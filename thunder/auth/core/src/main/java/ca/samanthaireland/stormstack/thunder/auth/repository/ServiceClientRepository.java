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

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClientId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OAuth2 service client persistence.
 *
 * <p>Implementations can be backed by configuration files, databases,
 * or in-memory stores.
 */
public interface ServiceClientRepository {

    /**
     * Finds a service client by its client ID.
     *
     * @param clientId the client ID
     * @return the client if found
     */
    Optional<ServiceClient> findByClientId(ServiceClientId clientId);

    /**
     * Finds a service client by its client ID string.
     *
     * @param clientId the client ID string
     * @return the client if found
     */
    default Optional<ServiceClient> findByClientId(String clientId) {
        try {
            return findByClientId(ServiceClientId.of(clientId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns all registered service clients.
     *
     * @return list of all clients
     */
    List<ServiceClient> findAll();

    /**
     * Returns all enabled service clients.
     *
     * @return list of enabled clients
     */
    List<ServiceClient> findAllEnabled();

    /**
     * Saves a service client (insert or update).
     *
     * <p>If the client ID already exists, updates the existing client.
     * Otherwise, inserts a new client.
     *
     * @param client the client to save
     * @return the saved client
     */
    ServiceClient save(ServiceClient client);

    /**
     * Deletes a service client by its ID.
     *
     * @param clientId the client ID
     * @return true if the client was deleted
     */
    boolean deleteByClientId(ServiceClientId clientId);

    /**
     * Checks if a client ID exists.
     *
     * @param clientId the client ID to check
     * @return true if the client exists
     */
    boolean existsByClientId(ServiceClientId clientId);

    /**
     * Returns the total count of registered clients.
     *
     * @return the client count
     */
    long count();
}
