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

import ca.samanthaireland.lightning.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.lightning.controlplane.node.model.Node;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.service.NodeRegistryService;
import ca.samanthaireland.lightning.controlplane.proxy.config.ProxyConfiguration;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyDisabledException;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of {@link NodeProxyService} using java.net.http.HttpClient.
 */
public class NodeProxyServiceImpl implements NodeProxyService {

    private static final Logger log = LoggerFactory.getLogger(NodeProxyServiceImpl.class);

    // Headers that should not be forwarded to upstream
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host"
    );

    private final NodeRegistryService nodeRegistryService;
    private final ProxyConfiguration config;
    private final HttpClient httpClient;
    private final AtomicBoolean enabled;

    /**
     * Creates a new NodeProxyServiceImpl.
     *
     * @param nodeRegistryService the node registry service
     * @param config              the proxy configuration
     */
    public NodeProxyServiceImpl(NodeRegistryService nodeRegistryService, ProxyConfiguration config) {
        this.nodeRegistryService = Objects.requireNonNull(nodeRegistryService, "nodeRegistryService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.enabled = new AtomicBoolean(config.enabled());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Creates a new NodeProxyServiceImpl with a custom HttpClient (for testing).
     *
     * @param nodeRegistryService the node registry service
     * @param config              the proxy configuration
     * @param httpClient          the HTTP client to use
     */
    public NodeProxyServiceImpl(NodeRegistryService nodeRegistryService, ProxyConfiguration config, HttpClient httpClient) {
        this.nodeRegistryService = Objects.requireNonNull(nodeRegistryService, "nodeRegistryService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.enabled = new AtomicBoolean(config.enabled());
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
    }

    @Override
    public ProxyResponse proxy(
            NodeId nodeId,
            String method,
            String path,
            Map<String, String> queryParams,
            Map<String, String> headers,
            byte[] body
    ) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(path, "path cannot be null");

        if (!enabled.get()) {
            throw new ProxyDisabledException();
        }

        // Look up the node
        Node node = nodeRegistryService.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        // Build the target URL
        String targetUrl = buildTargetUrl(node.advertiseAddress(), path, queryParams);
        log.debug("Proxying {} {} to node {} at {}", method, path, nodeId, targetUrl);

        try {
            // Build the request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()));

            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = body != null && body.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody();

            requestBuilder.method(method.toUpperCase(), bodyPublisher);

            // Forward headers (filtering hop-by-hop headers)
            if (headers != null) {
                headers.forEach((name, value) -> {
                    if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                        requestBuilder.header(name, value);
                    }
                });
            }

            // Execute the request
            HttpResponse<byte[]> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            // Build response headers map
            // Filter out hop-by-hop headers and HTTP/2 pseudo-headers (which start with ':')
            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!name.startsWith(":") && !HOP_BY_HOP_HEADERS.contains(name.toLowerCase()) && !values.isEmpty()) {
                    responseHeaders.put(name, values.getFirst());
                }
            });

            log.debug("Proxy response from node {}: {} {}", nodeId, response.statusCode(),
                    response.body() != null ? response.body().length + " bytes" : "no body");

            return new ProxyResponse(response.statusCode(), responseHeaders, response.body());

        } catch (IOException e) {
            log.error("IO error proxying to node {}: {}", nodeId, e.getMessage());
            throw new ProxyException(nodeId, path, "Connection failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyException(nodeId, path, "Request interrupted", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        log.info("Node proxy {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Builds the target URL from the node address, path, and query parameters.
     */
    private String buildTargetUrl(String advertiseAddress, String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder();

        // Normalize advertise address
        String baseUrl = advertiseAddress;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        url.append(baseUrl);

        // Normalize and append path
        String normalizedPath = path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        url.append(normalizedPath);

        // Append query parameters
        if (queryParams != null && !queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                            URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            url.append("?").append(queryString);
        }

        return url.toString();
    }
}
