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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.client;

import ca.samanthaireland.stormstack.thunder.controlplane.client.LightningNodeClient;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.auth.AuthServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client implementation for communicating with Lightning Engine nodes.
 *
 * <p>Uses OAuth2 service tokens for authenticating requests to engine nodes.
 */
@ApplicationScoped
public class LightningNodeClientImpl implements LightningNodeClient {
    private static final Logger log = LoggerFactory.getLogger(LightningNodeClientImpl.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;

    /**
     * Default constructor for CDI.
     */
    @Inject
    public LightningNodeClientImpl(AuthServiceClient authServiceClient) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
        this.authServiceClient = authServiceClient;
    }

    /**
     * Constructor for testing with custom dependencies.
     *
     * @param httpClient       the HTTP client to use
     * @param authServiceClient the auth service client for obtaining tokens
     */
    public LightningNodeClientImpl(HttpClient httpClient, AuthServiceClient authServiceClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.authServiceClient = authServiceClient;
    }

    /**
     * Adds authentication header to request builder if service token is available.
     */
    private HttpRequest.Builder addAuthHeader(HttpRequest.Builder builder) {
        if (authServiceClient != null && authServiceClient.isRemoteValidationEnabled()) {
            try {
                // Get a service token from the auth service and use it for authorization
                // For internal service calls, we use token exchange to get a JWT
                String serviceToken = getServiceToken();
                if (serviceToken != null && !serviceToken.isEmpty()) {
                    builder.header("Authorization", "Bearer " + serviceToken);
                }
            } catch (Exception e) {
                log.warn("Failed to get service token for node request: {}", e.getMessage());
            }
        }
        return builder;
    }

    /**
     * Gets a service token for authenticating with engine nodes.
     */
    private String getServiceToken() {
        // Use OAuth2 client credentials to get a service access token
        // This calls the auth service's /oauth2/token endpoint
        return authServiceClient.getServiceAccessToken();
    }

    @Override
    public long createContainer(Node node, List<String> moduleNames) {
        String url = node.advertiseAddress() + "/api/containers";

        try {
            Map<String, Object> requestBody = Map.of(
                    "name", "cluster-container-" + System.currentTimeMillis(),
                    "moduleNames", moduleNames
            );

            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = addAuthHeader(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new RuntimeException("Failed to create container: " + response.statusCode() + " - " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            long containerId = json.get("id").asLong();

            log.info("Created container {} on node {}", containerId, node.nodeId());
            return containerId;

        } catch (Exception e) {
            log.error("Failed to create container on node {}: {}", node.nodeId(), e.getMessage());
            throw new RuntimeException("Failed to create container on node " + node.nodeId(), e);
        }
    }

    @Override
    public long createMatch(Node node, long containerId, List<String> moduleNames) {
        String url = node.advertiseAddress() + "/api/containers/" + containerId + "/matches";

        try {
            Map<String, Object> requestBody = Map.of(
                    "enabledModuleNames", moduleNames
            );

            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = addAuthHeader(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new RuntimeException("Failed to create match: " + response.statusCode() + " - " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            long matchId = json.get("id").asLong();

            log.info("Created match {} in container {} on node {}", matchId, containerId, node.nodeId());
            return matchId;

        } catch (Exception e) {
            log.error("Failed to create match on node {}: {}", node.nodeId(), e.getMessage());
            throw new RuntimeException("Failed to create match on node " + node.nodeId(), e);
        }
    }

    @Override
    public void deleteMatch(Node node, long containerId, long matchId) {
        String url = node.advertiseAddress() + "/api/containers/" + containerId + "/matches/" + matchId;

        try {
            HttpRequest request = addAuthHeader(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new RuntimeException("Failed to delete match: " + response.statusCode() + " - " + response.body());
            }

            log.info("Deleted match {} from container {} on node {}", matchId, containerId, node.nodeId());

        } catch (Exception e) {
            log.error("Failed to delete match {} from node {}: {}", matchId, node.nodeId(), e.getMessage());
            throw new RuntimeException("Failed to delete match from node " + node.nodeId(), e);
        }
    }

    @Override
    public void deleteContainer(Node node, long containerId) {
        String url = node.advertiseAddress() + "/api/containers/" + containerId;

        try {
            HttpRequest request = addAuthHeader(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new RuntimeException("Failed to delete container: " + response.statusCode() + " - " + response.body());
            }

            log.info("Deleted container {} from node {}", containerId, node.nodeId());

        } catch (Exception e) {
            log.error("Failed to delete container {} from node {}: {}", containerId, node.nodeId(), e.getMessage());
            throw new RuntimeException("Failed to delete container from node " + node.nodeId(), e);
        }
    }

    @Override
    public boolean isReachable(Node node) {
        String url = node.advertiseAddress() + "/api/health";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Node {} not reachable: {}", node.nodeId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void uploadModule(Node node, String name, String version, String fileName, byte[] jarData) {
        String url = node.advertiseAddress() + "/api/modules/upload";

        try {
            // Create multipart form data
            String boundary = "----ModuleBoundary" + System.currentTimeMillis();
            byte[] bodyBytes = createMultipartBody(boundary, name, version, fileName, jarData);

            HttpRequest request = addAuthHeader(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException(
                        "Failed to upload module: HTTP " + response.statusCode() + " - " + response.body()
                );
            }

            log.info("Uploaded module {}:{} to node {}", name, version, node.nodeId());

        } catch (IOException | InterruptedException e) {
            log.error("Failed to upload module {}:{} to node {}: {}", name, version, node.nodeId(), e.getMessage());
            throw new RuntimeException("Failed to upload module to node " + node.nodeId(), e);
        }
    }

    private byte[] createMultipartBody(String boundary, String name, String version, String fileName, byte[] jarData)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        // Module name
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"name\"\r\n\r\n");
        writer.append(name).append("\r\n");

        // Module version
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"version\"\r\n\r\n");
        writer.append(version).append("\r\n");

        // JAR file
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName).append("\"\r\n");
        writer.append("Content-Type: application/java-archive\r\n\r\n");
        writer.flush();
        baos.write(jarData);
        writer.append("\r\n");

        // End boundary
        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();

        return baos.toByteArray();
    }
}
