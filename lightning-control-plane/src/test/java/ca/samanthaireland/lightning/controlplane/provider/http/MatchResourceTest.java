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

import ca.samanthaireland.lightning.controlplane.auth.AuthClient;
import ca.samanthaireland.lightning.controlplane.match.exception.MatchNotFoundException;
import ca.samanthaireland.lightning.controlplane.match.model.ClusterMatchId;
import ca.samanthaireland.lightning.controlplane.match.model.MatchRegistryEntry;
import ca.samanthaireland.lightning.controlplane.match.model.MatchStatus;
import ca.samanthaireland.lightning.controlplane.match.service.MatchRoutingService;
import ca.samanthaireland.lightning.controlplane.node.model.NodeId;
import ca.samanthaireland.lightning.controlplane.provider.dto.CreateMatchRequest;
import ca.samanthaireland.lightning.controlplane.provider.dto.MatchResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchResourceTest {

    @Mock
    private MatchRoutingService matchRoutingService;

    @Mock
    private AuthClient authClient;

    private MatchResource resource;

    private static final String MATCH_ID_STR = "node-1-42-7";
    private static final ClusterMatchId MATCH_ID = ClusterMatchId.fromString(MATCH_ID_STR);
    private static final String NODE_ID_STR = "node-1";
    private static final NodeId NODE_ID = NodeId.of(NODE_ID_STR);
    private static final java.util.List<String> MODULES = java.util.List.of("entity-module", "grid-map-module");

    @BeforeEach
    void setUp() {
        resource = new MatchResource(matchRoutingService, authClient);
    }

    private MatchRegistryEntry createTestEntry() {
        return createTestEntry(MatchStatus.RUNNING);
    }

    private MatchRegistryEntry createTestEntry(MatchStatus status) {
        return new MatchRegistryEntry(
                MATCH_ID,
                NODE_ID,
                42L,
                status,
                Instant.now(),
                MODULES,
                "http://localhost:8080",
                "ws://localhost:8080/ws/containers/42/matches/" + MATCH_ID_STR + "/snapshot",
                0,
                0  // playerLimit
        );
    }

    @Nested
    class Create {

        @Test
        void create_withValidRequest_returns201() {
            // Arrange
            MatchRegistryEntry entry = createTestEntry();
            when(matchRoutingService.createMatch(eq(MODULES), isNull(), eq(0))).thenReturn(entry);

            CreateMatchRequest request = new CreateMatchRequest(MODULES, null, null);

            // Act
            Response response = resource.create(request);

            // Assert
            assertThat(response.getStatus()).isEqualTo(201);
            assertThat(response.getLocation().toString()).contains(MATCH_ID_STR);

            MatchResponse body = (MatchResponse) response.getEntity();
            assertThat(body.matchId()).isEqualTo(MATCH_ID_STR);
            assertThat(body.nodeId()).isEqualTo(NODE_ID_STR);
            assertThat(body.status()).isEqualTo(MatchStatus.RUNNING);
            assertThat(body.moduleNames()).containsExactlyElementsOf(MODULES);
        }

        @Test
        void create_withPreferredNode_passesToService() {
            // Arrange
            NodeId preferredNode = NodeId.of("preferred-node");
            MatchRegistryEntry entry = createTestEntry();
            when(matchRoutingService.createMatch(MODULES, preferredNode, 0)).thenReturn(entry);

            CreateMatchRequest request = new CreateMatchRequest(MODULES, "preferred-node", null);

            // Act
            Response response = resource.create(request);

            // Assert
            verify(matchRoutingService).createMatch(MODULES, preferredNode, 0);
            assertThat(response.getStatus()).isEqualTo(201);
        }
    }

    @Nested
    class GetById {

        @Test
        void getById_existingMatch_returnsMatch() {
            // Arrange
            MatchRegistryEntry entry = createTestEntry();
            when(matchRoutingService.findById(MATCH_ID)).thenReturn(Optional.of(entry));

            // Act
            MatchResponse response = resource.getById(MATCH_ID_STR);

            // Assert
            assertThat(response.matchId()).isEqualTo(MATCH_ID_STR);
            assertThat(response.nodeId()).isEqualTo(NODE_ID_STR);
            assertThat(response.status()).isEqualTo(MatchStatus.RUNNING);
        }

        @Test
        void getById_nonExistentMatch_throwsException() {
            // Arrange
            ClusterMatchId unknownId = ClusterMatchId.fromString("unknown");
            when(matchRoutingService.findById(unknownId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> resource.getById("unknown"))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    class List {

        @Test
        void list_noFilter_returnsAllMatches() {
            // Arrange
            MatchRegistryEntry entry1 = createTestEntry();
            MatchRegistryEntry entry2 = new MatchRegistryEntry(
                    ClusterMatchId.fromString("node-2-1-1"), NodeId.of("node-2"), 1L, MatchStatus.RUNNING, Instant.now(),
                    MODULES, "http://localhost:8081", "ws://localhost:8081/ws", 0, 0
            );
            when(matchRoutingService.findAll()).thenReturn(java.util.List.of(entry1, entry2));

            // Act
            java.util.List<MatchResponse> response = resource.list(null);

            // Assert
            assertThat(response).hasSize(2);
            assertThat(response.get(0).matchId()).isEqualTo(MATCH_ID_STR);
            assertThat(response.get(1).matchId()).isEqualTo("node-2-1-1");
        }

        @Test
        void list_withStatusFilter_returnsFilteredMatches() {
            // Arrange
            MatchRegistryEntry entry = createTestEntry(MatchStatus.FINISHED);
            when(matchRoutingService.findByStatus(MatchStatus.FINISHED))
                    .thenReturn(java.util.List.of(entry));

            // Act
            java.util.List<MatchResponse> response = resource.list(MatchStatus.FINISHED);

            // Assert
            assertThat(response).hasSize(1);
            assertThat(response.get(0).status()).isEqualTo(MatchStatus.FINISHED);
            verify(matchRoutingService).findByStatus(MatchStatus.FINISHED);
        }
    }

    @Nested
    class Delete {

        @Test
        void delete_existingMatch_returns204() {
            // Arrange
            doNothing().when(matchRoutingService).deleteMatch(MATCH_ID);

            // Act
            Response response = resource.delete(MATCH_ID_STR);

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
            verify(matchRoutingService).deleteMatch(MATCH_ID);
        }

        @Test
        void delete_nonExistentMatch_throwsException() {
            // Arrange
            ClusterMatchId unknownId = ClusterMatchId.fromString("unknown");
            doThrow(new MatchNotFoundException(unknownId))
                    .when(matchRoutingService).deleteMatch(unknownId);

            // Act & Assert
            assertThatThrownBy(() -> resource.delete("unknown"))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    class Finish {

        @Test
        void finish_existingMatch_returnsFinishedMatch() {
            // Arrange
            doNothing().when(matchRoutingService).finishMatch(MATCH_ID);
            MatchRegistryEntry finishedEntry = createTestEntry(MatchStatus.FINISHED);
            when(matchRoutingService.findById(MATCH_ID)).thenReturn(Optional.of(finishedEntry));

            // Act
            MatchResponse response = resource.finish(MATCH_ID_STR);

            // Assert
            assertThat(response.matchId()).isEqualTo(MATCH_ID_STR);
            assertThat(response.status()).isEqualTo(MatchStatus.FINISHED);
            verify(matchRoutingService).finishMatch(MATCH_ID);
        }
    }

    @Nested
    class UpdatePlayerCount {

        @Test
        void updatePlayerCount_existingMatch_returnsUpdatedMatch() {
            // Arrange
            doNothing().when(matchRoutingService).updatePlayerCount(MATCH_ID, 10);
            MatchRegistryEntry updatedEntry = new MatchRegistryEntry(
                    MATCH_ID, NODE_ID, 42L, MatchStatus.RUNNING, Instant.now(),
                    MODULES, "http://localhost:8080",
                    "ws://localhost:8080/ws/containers/42/matches/" + MATCH_ID_STR + "/snapshot",
                    10, 0
            );
            when(matchRoutingService.findById(MATCH_ID)).thenReturn(Optional.of(updatedEntry));

            // Act
            MatchResponse response = resource.updatePlayerCount(MATCH_ID_STR, 10);

            // Assert
            assertThat(response.matchId()).isEqualTo(MATCH_ID_STR);
            assertThat(response.playerCount()).isEqualTo(10);
            verify(matchRoutingService).updatePlayerCount(MATCH_ID, 10);
        }
    }
}
