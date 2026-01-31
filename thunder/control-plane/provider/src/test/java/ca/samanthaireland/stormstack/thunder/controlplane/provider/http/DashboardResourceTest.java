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

package ca.samanthaireland.stormstack.thunder.controlplane.provider.http;

import ca.samanthaireland.stormstack.thunder.controlplane.autoscaler.model.ScalingAction;
import ca.samanthaireland.stormstack.thunder.controlplane.match.model.MatchStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeStatus;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.DashboardOverview;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.MatchResponse;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeCapacityDto;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeMetricsDto;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.NodeResponse;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.dto.PagedResponse;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardResourceTest {

    @Mock
    private DashboardService dashboardService;

    private DashboardResource resource;

    @BeforeEach
    void setUp() {
        resource = new DashboardResource(dashboardService);
    }

    private NodeResponse createNodeResponse(String nodeId, NodeStatus status) {
        return new NodeResponse(
                nodeId,
                "http://" + nodeId + ":8080",
                status,
                new NodeCapacityDto(100),
                new NodeMetricsDto(5, 3, 0.5, 1024, 4096),
                Instant.now(),
                Instant.now(),
                95
        );
    }

    private MatchResponse createMatchResponse(String matchId, MatchStatus status, int playerCount) {
        return new MatchResponse(
                matchId,
                "node-1",
                1L,
                status,
                Instant.now(),
                List.of("entity-module"),
                "http://localhost:8080",
                "ws://localhost:8080/ws",
                playerCount,
                0  // playerLimit
        );
    }

    @Nested
    class GetOverview {

        @Test
        void getOverview_returnsCompleteOverview() {
            // Arrange
            DashboardOverview overview = new DashboardOverview(
                    DashboardOverview.ClusterHealth.healthy(4, 5, 0.04),
                    new DashboardOverview.NodeSummary(5, 4, 1, 0),
                    new DashboardOverview.MatchSummary(15, 10, 5, 50),
                    new DashboardOverview.CapacitySummary(500, 20, 480, 0.04),
                    new DashboardOverview.AutoscalerSummary(true, ScalingAction.NONE, 0, false),
                    Instant.now()
            );
            when(dashboardService.getOverview()).thenReturn(overview);

            // Act
            DashboardOverview response = resource.getOverview();

            // Assert
            assertThat(response.clusterHealth().healthyNodes()).isEqualTo(4);
            assertThat(response.clusterHealth().totalNodes()).isEqualTo(5);
            assertThat(response.nodes().total()).isEqualTo(5);
            assertThat(response.matches().running()).isEqualTo(10);
            assertThat(response.autoscaler().inCooldown()).isFalse();
            assertThat(response.autoscaler().recommendedAction()).isEqualTo(ScalingAction.NONE);
        }
    }

    @Nested
    class GetNodes {

        @Test
        void getNodes_noFilter_returnsPaginatedNodes() {
            // Arrange
            List<NodeResponse> nodes = List.of(
                    createNodeResponse("node-1", NodeStatus.HEALTHY),
                    createNodeResponse("node-2", NodeStatus.HEALTHY)
            );
            PagedResponse<NodeResponse> pagedResponse = new PagedResponse<>(
                    nodes, 0, 20, 2, 1, false, false
            );
            when(dashboardService.getNodes(0, 20, null)).thenReturn(pagedResponse);

            // Act
            PagedResponse<NodeResponse> response = resource.getNodes(0, 20, null);

            // Assert
            assertThat(response.items()).hasSize(2);
            assertThat(response.page()).isZero();
            assertThat(response.pageSize()).isEqualTo(20);
            assertThat(response.totalItems()).isEqualTo(2);
        }

        @Test
        void getNodes_withStatusFilter_passesFilterToService() {
            // Arrange
            List<NodeResponse> nodes = List.of(
                    createNodeResponse("node-1", NodeStatus.DRAINING)
            );
            PagedResponse<NodeResponse> pagedResponse = new PagedResponse<>(
                    nodes, 0, 20, 1, 1, false, false
            );
            when(dashboardService.getNodes(0, 20, NodeStatus.DRAINING)).thenReturn(pagedResponse);

            // Act
            PagedResponse<NodeResponse> response = resource.getNodes(0, 20, NodeStatus.DRAINING);

            // Assert
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).status()).isEqualTo(NodeStatus.DRAINING);
            verify(dashboardService).getNodes(0, 20, NodeStatus.DRAINING);
        }

        @Test
        void getNodes_pageSizeExceedsMax_clampedTo100() {
            // Arrange
            PagedResponse<NodeResponse> pagedResponse = new PagedResponse<>(
                    List.of(), 0, 100, 0, 0, false, false
            );
            when(dashboardService.getNodes(eq(0), eq(100), any())).thenReturn(pagedResponse);

            // Act
            PagedResponse<NodeResponse> response = resource.getNodes(0, 500, null);

            // Assert
            verify(dashboardService).getNodes(0, 100, null);
        }

        @Test
        void getNodes_negativePage_clampedToZero() {
            // Arrange
            PagedResponse<NodeResponse> pagedResponse = new PagedResponse<>(
                    List.of(), 0, 20, 0, 0, false, false
            );
            when(dashboardService.getNodes(eq(0), eq(20), any())).thenReturn(pagedResponse);

            // Act
            PagedResponse<NodeResponse> response = resource.getNodes(-5, 20, null);

            // Assert
            verify(dashboardService).getNodes(0, 20, null);
        }
    }

    @Nested
    class GetMatches {

        @Test
        void getMatches_noFilter_returnsPaginatedMatches() {
            // Arrange
            List<MatchResponse> matches = List.of(
                    createMatchResponse("match-1", MatchStatus.RUNNING, 5),
                    createMatchResponse("match-2", MatchStatus.RUNNING, 3)
            );
            PagedResponse<MatchResponse> pagedResponse = new PagedResponse<>(
                    matches, 0, 20, 2, 1, false, false
            );
            when(dashboardService.getMatches(0, 20, null, null)).thenReturn(pagedResponse);

            // Act
            PagedResponse<MatchResponse> response = resource.getMatches(0, 20, null, null);

            // Assert
            assertThat(response.items()).hasSize(2);
            assertThat(response.totalItems()).isEqualTo(2);
        }

        @Test
        void getMatches_withStatusFilter_passesFilterToService() {
            // Arrange
            List<MatchResponse> matches = List.of(
                    createMatchResponse("match-1", MatchStatus.FINISHED, 0)
            );
            PagedResponse<MatchResponse> pagedResponse = new PagedResponse<>(
                    matches, 0, 20, 1, 1, false, false
            );
            when(dashboardService.getMatches(0, 20, MatchStatus.FINISHED, null)).thenReturn(pagedResponse);

            // Act
            PagedResponse<MatchResponse> response = resource.getMatches(0, 20, MatchStatus.FINISHED, null);

            // Assert
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).status()).isEqualTo(MatchStatus.FINISHED);
            verify(dashboardService).getMatches(0, 20, MatchStatus.FINISHED, null);
        }

        @Test
        void getMatches_withNodeFilter_passesFilterToService() {
            // Arrange
            List<MatchResponse> matches = List.of(
                    createMatchResponse("match-1", MatchStatus.RUNNING, 5)
            );
            PagedResponse<MatchResponse> pagedResponse = new PagedResponse<>(
                    matches, 0, 20, 1, 1, false, false
            );
            when(dashboardService.getMatches(0, 20, null, "node-1")).thenReturn(pagedResponse);

            // Act
            PagedResponse<MatchResponse> response = resource.getMatches(0, 20, null, "node-1");

            // Assert
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).nodeId()).isEqualTo("node-1");
            verify(dashboardService).getMatches(0, 20, null, "node-1");
        }

        @Test
        void getMatches_pageSizeExceedsMax_clampedTo100() {
            // Arrange
            PagedResponse<MatchResponse> pagedResponse = new PagedResponse<>(
                    List.of(), 0, 100, 0, 0, false, false
            );
            when(dashboardService.getMatches(eq(0), eq(100), any(), any())).thenReturn(pagedResponse);

            // Act
            PagedResponse<MatchResponse> response = resource.getMatches(0, 200, null, null);

            // Assert
            verify(dashboardService).getMatches(0, 100, null, null);
        }
    }
}
