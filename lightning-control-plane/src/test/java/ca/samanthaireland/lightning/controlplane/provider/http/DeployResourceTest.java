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

import ca.samanthaireland.lightning.controlplane.match.exception.MatchNotFoundException;
import ca.samanthaireland.lightning.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.lightning.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.lightning.controlplane.match.model.MatchStatus;
import ca.samanthaireland.lightning.controlplane.match.service.MatchRoutingService;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.provider.auth.AuthServiceClient;
import ca.samanthaireland.lightning.controlplane.provider.dto.DeployRequest;
import ca.samanthaireland.lightning.controlplane.provider.dto.DeployResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeployResourceTest {

    @Mock
    private MatchRoutingService matchRoutingService;

    @Mock
    private AuthServiceClient authServiceClient;

    private DeployResource resource;

    @BeforeEach
    void setUp() {
        resource = new DeployResource(matchRoutingService, authServiceClient);
    }

    @Test
    void deploy_success_returns201WithEndpoints() {
        // Arrange
        List<String> modules = List.of("entity-module", "grid-map-module");
        DeployRequest request = new DeployRequest(modules, null, true);

        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.of("node-1", 42L, 7L),
                NodeId.of("node-1"),
                42L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/42/matches/node-1-42-7/snapshot",
                0,
                0  // playerLimit
        );

        when(matchRoutingService.createMatch(eq(modules), isNull())).thenReturn(entry);
        when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);

        // Act
        Response response = resource.deploy(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getLocation().toString()).contains("node-1-42-7");

        DeployResponse body = (DeployResponse) response.getEntity();
        assertThat(body.matchId()).isEqualTo("node-1-42-7");
        assertThat(body.nodeId()).isEqualTo("node-1");
        assertThat(body.containerId()).isEqualTo(42L);
        assertThat(body.status()).isEqualTo(MatchStatus.RUNNING);
        assertThat(body.modules()).containsExactlyElementsOf(modules);
        assertThat(body.endpoints().http()).isEqualTo("http://localhost:8080/api/containers/42");
        assertThat(body.endpoints().websocket()).contains("ws://localhost:8080/ws/containers/42/snapshots/node-1-42-7");
        assertThat(body.endpoints().commands()).contains("ws://localhost:8080/containers/42/commands");
        assertThat(body.matchToken()).isNull();
    }

    @Test
    void deploy_withAuthServiceEnabled_returnsMatchToken() {
        // Arrange
        List<String> modules = List.of("entity-module");
        DeployRequest request = new DeployRequest(modules, null, true);
        Instant tokenExpiry = Instant.now().plusSeconds(86400);

        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.of("node-1", 42L, 7L),
                NodeId.of("node-1"),
                42L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/42/matches/node-1-42-7/snapshot",
                0,
                0  // playerLimit
        );

        when(matchRoutingService.createMatch(eq(modules), isNull())).thenReturn(entry);
        when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
        when(authServiceClient.issueMatchToken(
                eq("node-1-42-7"),
                eq(42L),
                eq("system"),
                eq("Deploy Token"),
                any()
        )).thenReturn(new AuthServiceClient.MatchTokenResult.Success(
                "token-id-123",
                "node-1-42-7",
                "system",
                "jwt.token.here",
                tokenExpiry
        ));

        // Act
        Response response = resource.deploy(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(201);
        DeployResponse body = (DeployResponse) response.getEntity();
        assertThat(body.matchToken()).isEqualTo("jwt.token.here");
        assertThat(body.tokenExpiresAt()).isEqualTo(tokenExpiry);
    }

    @Test
    void deploy_withAuthServiceFailure_continuesWithoutToken() {
        // Arrange
        List<String> modules = List.of("entity-module");
        DeployRequest request = new DeployRequest(modules, null, true);

        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.of("node-1", 42L, 7L),
                NodeId.of("node-1"),
                42L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/42/matches/node-1-42-7/snapshot",
                0,
                0  // playerLimit
        );

        when(matchRoutingService.createMatch(eq(modules), isNull())).thenReturn(entry);
        when(authServiceClient.isRemoteValidationEnabled()).thenReturn(true);
        when(authServiceClient.issueMatchToken(any(), anyLong(), any(), any(), any()))
                .thenReturn(new AuthServiceClient.MatchTokenResult.Failure(503, "Auth service unavailable"));

        // Act
        Response response = resource.deploy(request);

        // Assert - deployment should still succeed without token
        assertThat(response.getStatus()).isEqualTo(201);
        DeployResponse body = (DeployResponse) response.getEntity();
        assertThat(body.matchToken()).isNull();
        assertThat(body.tokenExpiresAt()).isNull();
    }

    @Test
    void deploy_withPreferredNode_passesToService() {
        // Arrange
        List<String> modules = List.of("entity-module");
        String preferredNode = "preferred-node";
        DeployRequest request = new DeployRequest(modules, preferredNode, true);

        MatchRegistryEntry entry = new MatchRegistryEntry(
                ClusterMatchId.of(preferredNode, 1L, 1L),
                NodeId.of(preferredNode),
                1L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/1/matches/preferred-node-1-1/snapshot",
                0,
                0  // playerLimit
        );

        when(matchRoutingService.createMatch(eq(modules), eq(NodeId.of(preferredNode)))).thenReturn(entry);
        when(authServiceClient.isRemoteValidationEnabled()).thenReturn(false);

        // Act
        Response response = resource.deploy(request);

        // Assert
        verify(matchRoutingService).createMatch(eq(modules), eq(NodeId.of(preferredNode)));
        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void getStatus_existingMatch_returnsDeployResponse() {
        // Arrange
        String matchId = "node-1-42-7";
        List<String> modules = List.of("entity-module");

        ClusterMatchId clusterMatchId = ClusterMatchId.fromString(matchId);
        MatchRegistryEntry entry = new MatchRegistryEntry(
                clusterMatchId,
                NodeId.of("node-1"),
                42L,
                MatchStatus.RUNNING,
                Instant.now(),
                modules,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/42/matches/node-1-42-7/snapshot",
                5,
                0  // playerLimit
        );

        when(matchRoutingService.findById(clusterMatchId)).thenReturn(Optional.of(entry));

        // Act
        DeployResponse response = resource.getStatus(matchId);

        // Assert
        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.nodeId()).isEqualTo("node-1");
        assertThat(response.status()).isEqualTo(MatchStatus.RUNNING);
    }

    @Test
    void getStatus_nonExistingMatch_throwsNotFoundException() {
        // Arrange
        String matchId = "non-existent";
        ClusterMatchId clusterMatchId = ClusterMatchId.fromString(matchId);
        when(matchRoutingService.findById(clusterMatchId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> resource.getStatus(matchId))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void undeploy_existingMatch_returns204() {
        // Arrange
        String matchId = "node-1-42-7";
        ClusterMatchId clusterMatchId = ClusterMatchId.fromString(matchId);
        doNothing().when(matchRoutingService).deleteMatch(clusterMatchId);

        // Act
        Response response = resource.undeploy(matchId);

        // Assert
        assertThat(response.getStatus()).isEqualTo(204);
        verify(matchRoutingService).deleteMatch(clusterMatchId);
    }

    @Test
    void deployRequest_isAutoStart_defaultsToTrue() {
        // Arrange
        DeployRequest requestWithNull = new DeployRequest(List.of("module"), null, null);
        DeployRequest requestWithTrue = new DeployRequest(List.of("module"), null, true);
        DeployRequest requestWithFalse = new DeployRequest(List.of("module"), null, false);

        // Assert
        assertThat(requestWithNull.isAutoStart()).isTrue();
        assertThat(requestWithTrue.isAutoStart()).isTrue();
        assertThat(requestWithFalse.isAutoStart()).isFalse();
    }
}
