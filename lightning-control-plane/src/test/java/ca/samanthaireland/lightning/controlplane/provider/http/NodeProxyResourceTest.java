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

package ca.samanthaireland.lightning.controlplane.provider.http;

import ca.samanthaireland.lightning.controlplane.node.exception.NodeNotFoundException;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.provider.auth.AuthServiceClient;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyDisabledException;
import ca.samanthaireland.lightning.controlplane.proxy.exception.ProxyException;
import ca.samanthaireland.lightning.controlplane.proxy.service.NodeProxyService;
import ca.samanthaireland.lightning.controlplane.proxy.service.ProxyResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NodeProxyResource}.
 *
 * <p>Tests focus on the resource's request handling logic including:
 * <ul>
 *   <li>Delegation to NodeProxyService</li>
 *   <li>Query parameter forwarding</li>
 *   <li>Header forwarding</li>
 *   <li>Response building</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NodeProxyResourceTest {

    @Mock
    private NodeProxyService nodeProxyService;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private HttpHeaders headers;

    private NodeProxyResource resource;

    private static final String NODE_ID_STR = "node-1";
    private static final NodeId NODE_ID = NodeId.of(NODE_ID_STR);

    @BeforeEach
    void setUp() {
        resource = new NodeProxyResource(nodeProxyService, authServiceClient);
    }

    private void setupMockUriInfo(MultivaluedHashMap<String, String> queryParams) {
        when(uriInfo.getQueryParameters()).thenReturn(queryParams != null ? queryParams : new MultivaluedHashMap<>());
    }

    private void setupMockHeaders(MultivaluedHashMap<String, String> headerMap) {
        when(headers.getRequestHeaders()).thenReturn(headerMap != null ? headerMap : new MultivaluedHashMap<>());
    }

    @Nested
    class ProxyDisabled {

        @Test
        void proxyGet_whenDisabled_throwsProxyDisabledException() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ProxyDisabledException());

            // Act & Assert
            assertThatThrownBy(() -> resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers))
                    .isInstanceOf(ProxyDisabledException.class);
        }

        @Test
        void proxyPost_whenDisabled_throwsProxyDisabledException() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ProxyDisabledException());

            // Act & Assert
            assertThatThrownBy(() -> resource.proxyPost(NODE_ID_STR, "api/test", uriInfo, headers, null))
                    .isInstanceOf(ProxyDisabledException.class);
        }
    }

    @Nested
    class NodeNotFound {

        @Test
        void proxyGet_nodeNotFound_throwsException() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            when(nodeProxyService.proxy(eq(NODE_ID), any(), any(), any(), any(), any()))
                    .thenThrow(new NodeNotFoundException(NODE_ID));

            // Act & Assert
            assertThatThrownBy(() -> resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers))
                    .isInstanceOf(NodeNotFoundException.class);
        }

        @Test
        void proxyPost_nodeNotFound_throwsException() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            when(nodeProxyService.proxy(eq(NODE_ID), any(), any(), any(), any(), any()))
                    .thenThrow(new NodeNotFoundException(NODE_ID));

            // Act & Assert
            assertThatThrownBy(() -> resource.proxyPost(NODE_ID_STR, "api/test", uriInfo, headers, null))
                    .isInstanceOf(NodeNotFoundException.class);
        }
    }

    @Nested
    class SuccessfulProxy {

        @Test
        void proxyGet_success_returnsProxiedResponse() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            byte[] responseBody = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            ProxyResponse proxyResponse = new ProxyResponse(200, Map.of("Content-Type", "application/json"), responseBody);
            when(nodeProxyService.proxy(eq(NODE_ID), eq("GET"), eq("api/test"), any(), any(), any()))
                    .thenReturn(proxyResponse);

            // Act
            Response response = resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers);

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getEntity()).isEqualTo(responseBody);
        }

        @Test
        void proxyPost_success_returnsProxiedResponse() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            byte[] responseBody = "{\"id\":123}".getBytes(StandardCharsets.UTF_8);
            ProxyResponse proxyResponse = new ProxyResponse(201, Map.of("Content-Type", "application/json"), responseBody);
            when(nodeProxyService.proxy(eq(NODE_ID), eq("POST"), eq("api/test"), any(), any(), any()))
                    .thenReturn(proxyResponse);

            // Act
            Response response = resource.proxyPost(NODE_ID_STR, "api/test", uriInfo, headers, null);

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
        }

        @Test
        void proxyDelete_success_returns204() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            ProxyResponse proxyResponse = new ProxyResponse(204, Map.of(), null);
            when(nodeProxyService.proxy(eq(NODE_ID), eq("DELETE"), eq("api/test/123"), any(), any(), any()))
                    .thenReturn(proxyResponse);

            // Act
            Response response = resource.proxyDelete(NODE_ID_STR, "api/test/123", uriInfo, headers);

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
        }
    }

    @Nested
    class QueryParameterForwarding {

        @Test
        void proxyGet_withQueryParams_forwardsToService() {
            // Arrange
            MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
            queryParams.add("foo", "bar");
            queryParams.add("baz", "qux");
            setupMockUriInfo(queryParams);
            setupMockHeaders(null);

            ProxyResponse proxyResponse = ProxyResponse.ok();
            ArgumentCaptor<Map<String, String>> queryCaptor = ArgumentCaptor.forClass(Map.class);
            when(nodeProxyService.proxy(any(), any(), any(), queryCaptor.capture(), any(), any()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers);

            // Assert
            Map<String, String> capturedParams = queryCaptor.getValue();
            assertThat(capturedParams).containsEntry("foo", "bar");
            assertThat(capturedParams).containsEntry("baz", "qux");
        }
    }

    @Nested
    class HeaderForwarding {

        @Test
        void proxyGet_forwardsAuthorizationHeader() {
            // Arrange
            setupMockUriInfo(null);
            MultivaluedHashMap<String, String> headerMap = new MultivaluedHashMap<>();
            headerMap.add("Authorization", "Bearer test-token");
            headerMap.add("Content-Type", "application/json");
            setupMockHeaders(headerMap);
            // Mock getHeaderString for Authorization header extraction
            when(headers.getHeaderString("Authorization")).thenReturn("Bearer test-token");
            when(headers.getHeaderString("X-Api-Token")).thenReturn(null);

            ProxyResponse proxyResponse = ProxyResponse.ok();
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            when(nodeProxyService.proxy(any(), any(), any(), any(), headersCaptor.capture(), any()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers);

            // Assert
            Map<String, String> capturedHeaders = headersCaptor.getValue();
            assertThat(capturedHeaders).containsEntry("Authorization", "Bearer test-token");
            assertThat(capturedHeaders).containsEntry("Content-Type", "application/json");
        }

        @Test
        void proxyGet_forwardsXHeaders() {
            // Arrange
            setupMockUriInfo(null);
            MultivaluedHashMap<String, String> headerMap = new MultivaluedHashMap<>();
            headerMap.add("X-Request-Id", "abc123");
            headerMap.add("X-Correlation-Id", "xyz789");
            setupMockHeaders(headerMap);

            ProxyResponse proxyResponse = ProxyResponse.ok();
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            when(nodeProxyService.proxy(any(), any(), any(), any(), headersCaptor.capture(), any()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers);

            // Assert
            Map<String, String> capturedHeaders = headersCaptor.getValue();
            assertThat(capturedHeaders).containsEntry("X-Request-Id", "abc123");
            assertThat(capturedHeaders).containsEntry("X-Correlation-Id", "xyz789");
        }
    }

    @Nested
    class ProxyFailure {

        @Test
        void proxyGet_connectionFailed_throwsProxyException() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ProxyException(NODE_ID, "api/test", "Connection refused"));

            // Act & Assert
            assertThatThrownBy(() -> resource.proxyGet(NODE_ID_STR, "api/test", uriInfo, headers))
                    .isInstanceOf(ProxyException.class)
                    .hasMessageContaining("Connection refused");
        }
    }

    @Nested
    class ProxyManagement {

        @Test
        void getProxyStatus_returnsCurrentStatus() {
            // Arrange
            when(nodeProxyService.isEnabled()).thenReturn(true);

            // Act
            Response response = resource.getProxyStatus();

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) response.getEntity();
            assertThat(entity).containsEntry("enabled", true);
        }

        @Test
        void enableProxy_setsEnabledTrue() {
            // Act
            Response response = resource.enableProxy();

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            verify(nodeProxyService).setEnabled(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) response.getEntity();
            assertThat(entity).containsEntry("enabled", true);
        }

        @Test
        void disableProxy_setsEnabledFalse() {
            // Act
            Response response = resource.disableProxy();

            // Assert
            assertThat(response.getStatus()).isEqualTo(200);
            verify(nodeProxyService).setEnabled(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) response.getEntity();
            assertThat(entity).containsEntry("enabled", false);
        }
    }

    @Nested
    class BodyForwarding {

        @Test
        void proxyPost_withBody_forwardsToService() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            byte[] requestBodyBytes = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            InputStream requestBody = new ByteArrayInputStream(requestBodyBytes);
            ProxyResponse proxyResponse = ProxyResponse.of(201, null);

            ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), bodyCaptor.capture()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyPost(NODE_ID_STR, "api/test", uriInfo, headers, requestBody);

            // Assert
            assertThat(bodyCaptor.getValue()).isEqualTo(requestBodyBytes);
        }

        @Test
        void proxyPut_withBody_forwardsToService() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            byte[] requestBodyBytes = "{\"id\":1,\"name\":\"updated\"}".getBytes(StandardCharsets.UTF_8);
            InputStream requestBody = new ByteArrayInputStream(requestBodyBytes);
            ProxyResponse proxyResponse = ProxyResponse.of(200, null);

            ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), bodyCaptor.capture()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyPut(NODE_ID_STR, "api/test/1", uriInfo, headers, requestBody);

            // Assert
            assertThat(bodyCaptor.getValue()).isEqualTo(requestBodyBytes);
        }

        @Test
        void proxyPatch_withBody_forwardsToService() {
            // Arrange
            setupMockUriInfo(null);
            setupMockHeaders(null);
            byte[] requestBodyBytes = "{\"name\":\"patched\"}".getBytes(StandardCharsets.UTF_8);
            InputStream requestBody = new ByteArrayInputStream(requestBodyBytes);
            ProxyResponse proxyResponse = ProxyResponse.of(200, null);

            ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
            when(nodeProxyService.proxy(any(), any(), any(), any(), any(), bodyCaptor.capture()))
                    .thenReturn(proxyResponse);

            // Act
            resource.proxyPatch(NODE_ID_STR, "api/test/1", uriInfo, headers, requestBody);

            // Assert
            assertThat(bodyCaptor.getValue()).isEqualTo(requestBodyBytes);
        }
    }
}
