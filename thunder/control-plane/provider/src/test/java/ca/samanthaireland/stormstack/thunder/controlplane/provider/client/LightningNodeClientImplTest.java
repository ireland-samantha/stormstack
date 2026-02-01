/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.controlplane.provider.client;

import ca.samanthaireland.stormstack.thunder.controlplane.node.model.Node;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeCapacity;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeMetrics;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.auth.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LightningNodeClientImplTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private LightningNodeClientImpl client;
    private Node testNode;

    @BeforeEach
    void setUp() {
        client = new LightningNodeClientImpl(httpClient, authServiceClient);

        testNode = Node.register(
                NodeId.of("node-1"),
                "http://localhost:8080",
                new NodeCapacity(100)
        );
    }

    @Nested
    class CreateContainer {

        @Test
        void createContainer_withSuccessfulResponse_returnsContainerId() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42, \"name\": \"test-container\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            long containerId = client.createContainer(testNode, List.of("physics"));

            assertThat(containerId).isEqualTo(42);
        }

        @Test
        void createContainer_withAuthEnabled_includesAuthHeader() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
            when(authServiceClient.getServiceAccessToken()).thenReturn("service-jwt-token");
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.createContainer(testNode, List.of("physics"));

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            HttpRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.headers().firstValue("Authorization"))
                    .isPresent()
                    .hasValue("Bearer service-jwt-token");
        }

        @Test
        void createContainer_withFailedResponse_throwsRuntimeException() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn("{\"error\": \"Internal server error\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatThrownBy(() -> client.createContainer(testNode, List.of("physics")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create container");
        }

        @Test
        void createContainer_withNetworkError_throwsRuntimeException() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Connection refused"));

            assertThatThrownBy(() -> client.createContainer(testNode, List.of("physics")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create container on node");
        }

        @Test
        void createContainer_withMultipleModules_sendsAllModulesInRequest() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.createContainer(testNode, List.of("physics", "combat", "inventory"));

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            HttpRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.uri().toString())
                    .isEqualTo("http://localhost:8080/api/containers");
        }
    }

    @Nested
    class CreateMatch {

        @Test
        void createMatch_withSuccessfulResponse_returnsMatchId() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 123}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            long matchId = client.createMatch(testNode, 42, List.of("physics"));

            assertThat(matchId).isEqualTo(123);
        }

        @Test
        void createMatch_sendsCorrectUrl() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 123}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.createMatch(testNode, 42, List.of("physics"));

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().uri().toString())
                    .isEqualTo("http://localhost:8080/api/containers/42/matches");
        }

        @Test
        void createMatch_withFailedResponse_throwsRuntimeException() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(400);
            when(httpResponse.body()).thenReturn("{\"error\": \"Bad request\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatThrownBy(() -> client.createMatch(testNode, 42, List.of("physics")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create match");
        }
    }

    @Nested
    class DeleteMatch {

        @Test
        void deleteMatch_withSuccessfulResponse_completes() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(204);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatCode(() -> client.deleteMatch(testNode, 42, 123))
                    .doesNotThrowAnyException();
        }

        @Test
        void deleteMatch_sendsDeleteRequest() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(204);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.deleteMatch(testNode, 42, 123);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            HttpRequest request = requestCaptor.getValue();
            assertThat(request.uri().toString())
                    .isEqualTo("http://localhost:8080/api/containers/42/matches/123");
            assertThat(request.method()).isEqualTo("DELETE");
        }

        @Test
        void deleteMatch_withFailedResponse_throwsRuntimeException() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn("{\"error\": \"Not found\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatThrownBy(() -> client.deleteMatch(testNode, 42, 123))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to delete match");
        }
    }

    @Nested
    class DeleteContainer {

        @Test
        void deleteContainer_withSuccessfulResponse_completes() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(204);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            assertThatCode(() -> client.deleteContainer(testNode, 42))
                    .doesNotThrowAnyException();
        }

        @Test
        void deleteContainer_sendsDeleteRequest() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(204);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.deleteContainer(testNode, 42);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            HttpRequest request = requestCaptor.getValue();
            assertThat(request.uri().toString())
                    .isEqualTo("http://localhost:8080/api/containers/42");
            assertThat(request.method()).isEqualTo("DELETE");
        }
    }

    @Nested
    class IsReachable {

        @Test
        void isReachable_withSuccessfulHealthCheck_returnsTrue() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            boolean result = client.isReachable(testNode);

            assertThat(result).isTrue();
        }

        @Test
        void isReachable_withNon200Response_returnsFalse() throws Exception {
            when(httpResponse.statusCode()).thenReturn(503);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            boolean result = client.isReachable(testNode);

            assertThat(result).isFalse();
        }

        @Test
        void isReachable_withConnectionError_returnsFalse() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Connection refused"));

            boolean result = client.isReachable(testNode);

            assertThat(result).isFalse();
        }

        @Test
        void isReachable_callsHealthEndpoint() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.isReachable(testNode);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().uri().toString())
                    .isEqualTo("http://localhost:8080/api/health");
        }
    }

    @Nested
    class UploadModule {

        @Test
        void uploadModule_withSuccessfulResponse_completes() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            byte[] jarData = "fake jar content".getBytes();

            assertThatCode(() -> client.uploadModule(testNode, "physics", "1.0.0", "physics-1.0.0.jar", jarData))
                    .doesNotThrowAnyException();
        }

        @Test
        void uploadModule_sendsMultipartRequest() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            byte[] jarData = "fake jar content".getBytes();
            client.uploadModule(testNode, "physics", "1.0.0", "physics-1.0.0.jar", jarData);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            HttpRequest request = requestCaptor.getValue();
            assertThat(request.uri().toString())
                    .isEqualTo("http://localhost:8080/api/modules/upload");
            assertThat(request.headers().firstValue("Content-Type"))
                    .isPresent()
                    .get()
                    .asString()
                    .contains("multipart/form-data");
        }

        @Test
        void uploadModule_withFailedResponse_throwsRuntimeException() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn("{\"error\": \"Failed to store module\"}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            byte[] jarData = "fake jar content".getBytes();

            assertThatThrownBy(() -> client.uploadModule(testNode, "physics", "1.0.0", "physics-1.0.0.jar", jarData))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload module");
        }

        @Test
        void uploadModule_withAuthEnabled_includesAuthHeader() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
            when(authServiceClient.getServiceAccessToken()).thenReturn("service-jwt-token");
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            byte[] jarData = "fake jar content".getBytes();
            client.uploadModule(testNode, "physics", "1.0.0", "physics-1.0.0.jar", jarData);

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
                    .isPresent()
                    .hasValue("Bearer service-jwt-token");
        }
    }

    @Nested
    class AuthTokenHandling {

        @Test
        void addAuthHeader_whenAuthDisabled_doesNotAddHeader() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.createContainer(testNode, List.of("physics"));

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
                    .isEmpty();
        }

        @Test
        void addAuthHeader_whenTokenEmpty_doesNotAddHeader() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
            when(authServiceClient.getServiceAccessToken()).thenReturn("");
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            client.createContainer(testNode, List.of("physics"));

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
                    .isEmpty();
        }

        @Test
        void addAuthHeader_whenTokenRetrievalFails_continuesWithoutAuth() throws Exception {
            when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
            when(authServiceClient.getServiceAccessToken()).thenThrow(new RuntimeException("Auth service unavailable"));
            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            // Should not throw, should continue without auth header
            long containerId = client.createContainer(testNode, List.of("physics"));

            assertThat(containerId).isEqualTo(42);
            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), any());

            assertThat(requestCaptor.getValue().headers().firstValue("Authorization"))
                    .isEmpty();
        }

        @Test
        void addAuthHeader_whenAuthClientNull_continuesWithoutAuth() throws Exception {
            // Create client with null auth service
            client = new LightningNodeClientImpl(httpClient, null);

            when(httpResponse.statusCode()).thenReturn(201);
            when(httpResponse.body()).thenReturn("{\"id\": 42}");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(httpResponse);

            long containerId = client.createContainer(testNode, List.of("physics"));

            assertThat(containerId).isEqualTo(42);
        }
    }
}
