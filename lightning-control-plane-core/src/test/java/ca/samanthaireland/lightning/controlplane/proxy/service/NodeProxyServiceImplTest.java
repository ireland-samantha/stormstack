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
import ca.samanthaireland.lightning.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.node.service.NodeRegistryService;
import ca.samanthaireland.lightning.controlplane.proxy.config.ProxyConfiguration;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyDisabledException;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Unit tests for {@link NodeProxyServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeProxyServiceImplTest {

    @Mock
    private NodeRegistryService nodeRegistryService;

    @Mock
    private ProxyConfiguration config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<byte[]> httpResponse;

    private NodeProxyServiceImpl proxyService;

    private static final NodeId NODE_ID = NodeId.of("node-1");
    private static final String ADVERTISE_ADDRESS = "http://backend:8080";

    @BeforeEach
    void setUp() {
        when(config.enabled()).thenReturn(true);
        when(config.timeoutSeconds()).thenReturn(30);
        when(config.maxRequestBodyBytes()).thenReturn(10485760L);

        proxyService = new NodeProxyServiceImpl(nodeRegistryService, config, httpClient);
    }

    private Node createTestNode() {
        return Node.register(NODE_ID, ADVERTISE_ADDRESS, new NodeCapacity(10));
    }

    @Nested
    class EnabledDisabled {

        @Test
        void isEnabled_returnsTrue_whenEnabled() {
            assertThat(proxyService.isEnabled()).isTrue();
        }

        @Test
        void setEnabled_changesState() {
            proxyService.setEnabled(false);
            assertThat(proxyService.isEnabled()).isFalse();

            proxyService.setEnabled(true);
            assertThat(proxyService.isEnabled()).isTrue();
        }

        @Test
        void proxy_throwsProxyDisabledException_whenDisabled() {
            proxyService.setEnabled(false);

            assertThatThrownBy(() -> proxyService.proxy(
                    NODE_ID, "GET", "/api/test", Map.of(), Map.of(), null))
                    .isInstanceOf(ProxyDisabledException.class);
        }
    }

    @Nested
    class NodeLookup {

        @Test
        void proxy_throwsNodeNotFoundException_whenNodeNotFound() {
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> proxyService.proxy(
                    NODE_ID, "GET", "/api/test", Map.of(), Map.of(), null))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    class SuccessfulProxy {

        @Test
        @SuppressWarnings("unchecked")
        void proxy_get_success() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));

            byte[] responseBody = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(responseBody);
            when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                    Map.of("Content-Type", java.util.List.of("application/json")),
                    (a, b) -> true
            ));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Act
            ProxyResponse result = proxyService.proxy(
                    NODE_ID, "GET", "/api/test", Map.of(), Map.of(), null);

            // Assert
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).isEqualTo(responseBody);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @SuppressWarnings("unchecked")
        void proxy_post_withBody_success() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));

            byte[] requestBody = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            byte[] responseBody = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn(responseBody);
            when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Act
            ProxyResponse result = proxyService.proxy(
                    NODE_ID, "POST", "/api/test", Map.of(), Map.of("Content-Type", "application/json"), requestBody);

            // Assert
            assertThat(result.statusCode()).isEqualTo(201);
            assertThat(result.body()).isEqualTo(responseBody);
        }

        @Test
        @SuppressWarnings("unchecked")
        void proxy_withQueryParams_success() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(new byte[0]);
            when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Act
            ProxyResponse result = proxyService.proxy(
                    NODE_ID, "GET", "/api/test",
                    Map.of("foo", "bar", "baz", "qux"),
                    Map.of(), null);

            // Assert
            assertThat(result.statusCode()).isEqualTo(200);

            // Verify that the request was made (we can't easily inspect the URL in this test)
            verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Nested
    class ProxyFailure {

        @Test
        @SuppressWarnings("unchecked")
        void proxy_ioException_throwsProxyException() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Connection refused"));

            // Act & Assert
            assertThatThrownBy(() -> proxyService.proxy(
                    NODE_ID, "GET", "/api/test", Map.of(), Map.of(), null))
                    .isInstanceOf(ProxyException.class)
                    .hasMessageContaining("Connection failed");
        }

        @Test
        @SuppressWarnings("unchecked")
        void proxy_interruptedException_throwsProxyException() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new InterruptedException("Request interrupted"));

            // Act & Assert
            assertThatThrownBy(() -> proxyService.proxy(
                    NODE_ID, "GET", "/api/test", Map.of(), Map.of(), null))
                    .isInstanceOf(ProxyException.class)
                    .hasMessageContaining("interrupted");
        }
    }

    @Nested
    class UpstreamErrors {

        @Test
        @SuppressWarnings("unchecked")
        void proxy_upstream404_returns404() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));

            byte[] errorBody = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn(errorBody);
            when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Act
            ProxyResponse result = proxyService.proxy(
                    NODE_ID, "GET", "/api/test/999", Map.of(), Map.of(), null);

            // Assert
            assertThat(result.statusCode()).isEqualTo(404);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.body()).isEqualTo(errorBody);
        }

        @Test
        @SuppressWarnings("unchecked")
        void proxy_upstream500_returns500() throws Exception {
            // Arrange
            when(nodeRegistryService.findById(NODE_ID)).thenReturn(Optional.of(createTestNode()));

            byte[] errorBody = "{\"error\":\"internal error\"}".getBytes(StandardCharsets.UTF_8);
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn(errorBody);
            when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Act
            ProxyResponse result = proxyService.proxy(
                    NODE_ID, "POST", "/api/test", Map.of(), Map.of(), null);

            // Assert
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
