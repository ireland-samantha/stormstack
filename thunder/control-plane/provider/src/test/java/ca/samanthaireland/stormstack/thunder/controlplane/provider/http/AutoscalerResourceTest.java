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
import ca.samanthaireland.stormstack.thunder.controlplane.autoscaler.model.ScalingRecommendation;
import ca.samanthaireland.stormstack.thunder.controlplane.autoscaler.service.AutoscalerService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoscalerResourceTest {

    @Mock
    private AutoscalerService autoscalerService;

    private AutoscalerResource resource;

    @BeforeEach
    void setUp() {
        resource = new AutoscalerResource(autoscalerService);
    }

    @Nested
    class GetRecommendation {

        @Test
        void getRecommendation_scaleUp_returnsRecommendation() {
            // Arrange
            ScalingRecommendation recommendation = ScalingRecommendation.scaleUp(
                    5, 7, 0.85, 0.60, "Cluster saturation is above threshold (85%)"
            );
            when(autoscalerService.getRecommendation()).thenReturn(recommendation);

            // Act
            ScalingRecommendation response = resource.getRecommendation();

            // Assert
            assertThat(response.action()).isEqualTo(ScalingAction.SCALE_UP);
            assertThat(response.nodeDelta()).isEqualTo(2);
            assertThat(response.reason()).contains("saturation");
        }

        @Test
        void getRecommendation_scaleDown_returnsRecommendation() {
            // Arrange
            ScalingRecommendation recommendation = ScalingRecommendation.scaleDown(
                    5, 4, 0.10, 0.20, "Cluster saturation is below minimum threshold (10%)"
            );
            when(autoscalerService.getRecommendation()).thenReturn(recommendation);

            // Act
            ScalingRecommendation response = resource.getRecommendation();

            // Assert
            assertThat(response.action()).isEqualTo(ScalingAction.SCALE_DOWN);
            assertThat(response.nodeDelta()).isEqualTo(-1);
        }

        @Test
        void getRecommendation_noAction_returnsNone() {
            // Arrange
            ScalingRecommendation recommendation = ScalingRecommendation.none(
                    5, 0.50, "Cluster load is within normal range"
            );
            when(autoscalerService.getRecommendation()).thenReturn(recommendation);

            // Act
            ScalingRecommendation response = resource.getRecommendation();

            // Assert
            assertThat(response.action()).isEqualTo(ScalingAction.NONE);
            assertThat(response.nodeDelta()).isZero();
        }
    }

    @Nested
    class AcknowledgeScalingAction {

        @Test
        void acknowledgeScalingAction_returns204() {
            // Arrange
            doNothing().when(autoscalerService).recordScalingAction();

            // Act
            Response response = resource.acknowledgeScalingAction();

            // Assert
            assertThat(response.getStatus()).isEqualTo(204);
            verify(autoscalerService).recordScalingAction();
        }
    }

    @Nested
    class GetStatus {

        @Test
        void getStatus_notInCooldown_returnsStatus() {
            // Arrange
            when(autoscalerService.isInCooldown()).thenReturn(false);
            when(autoscalerService.getLastRecommendation()).thenReturn(Optional.empty());

            // Act
            AutoscalerResource.AutoscalerStatus status = resource.getStatus();

            // Assert
            assertThat(status.inCooldown()).isFalse();
            assertThat(status.lastRecommendation()).isNull();
        }

        @Test
        void getStatus_inCooldown_returnsStatus() {
            // Arrange
            ScalingRecommendation lastRecommendation = ScalingRecommendation.scaleUp(
                    3, 5, 0.85, 0.60, "Previous scale up"
            );
            when(autoscalerService.isInCooldown()).thenReturn(true);
            when(autoscalerService.getLastRecommendation()).thenReturn(Optional.of(lastRecommendation));

            // Act
            AutoscalerResource.AutoscalerStatus status = resource.getStatus();

            // Assert
            assertThat(status.inCooldown()).isTrue();
            assertThat(status.lastRecommendation()).isNotNull();
            assertThat(status.lastRecommendation().action()).isEqualTo(ScalingAction.SCALE_UP);
        }

        @Test
        void getStatus_withLastRecommendation_includesIt() {
            // Arrange
            ScalingRecommendation lastRecommendation = ScalingRecommendation.scaleDown(
                    5, 4, 0.10, 0.20, "Low utilization"
            );
            when(autoscalerService.isInCooldown()).thenReturn(false);
            when(autoscalerService.getLastRecommendation()).thenReturn(Optional.of(lastRecommendation));

            // Act
            AutoscalerResource.AutoscalerStatus status = resource.getStatus();

            // Assert
            assertThat(status.inCooldown()).isFalse();
            assertThat(status.lastRecommendation()).isNotNull();
            assertThat(status.lastRecommendation().action()).isEqualTo(ScalingAction.SCALE_DOWN);
            assertThat(status.lastRecommendation().nodeDelta()).isEqualTo(-1);
        }
    }
}
