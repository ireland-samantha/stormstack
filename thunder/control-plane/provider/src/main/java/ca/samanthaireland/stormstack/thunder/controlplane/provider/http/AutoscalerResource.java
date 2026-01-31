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

import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.controlplane.autoscaler.model.ScalingRecommendation;
import ca.samanthaireland.stormstack.thunder.controlplane.autoscaler.service.AutoscalerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.controlplane.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST resource for autoscaler operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/autoscaler/recommendation - Get current scaling recommendation</li>
 *   <li>POST /api/autoscaler/acknowledge - Acknowledge a scaling action (starts cooldown)</li>
 *   <li>GET /api/autoscaler/status - Get autoscaler status</li>
 * </ul>
 */
@Path("/api/autoscaler")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class AutoscalerResource {
    private static final Logger log = LoggerFactory.getLogger(AutoscalerResource.class);

    private final AutoscalerService autoscalerService;

    @Inject
    public AutoscalerResource(AutoscalerService autoscalerService) {
        this.autoscalerService = autoscalerService;
    }

    /**
     * Gets the current scaling recommendation based on cluster state.
     *
     * @return the scaling recommendation
     */
    @GET
    @Path("/recommendation")
    @Scopes("control-plane.autoscaler.read")
    public ScalingRecommendation getRecommendation() {
        return autoscalerService.getRecommendation();
    }

    /**
     * Acknowledges that a scaling action was taken.
     * This starts the cooldown timer to prevent rapid scaling.
     *
     * @return 204 No Content
     */
    @POST
    @Path("/acknowledge")
    @Scopes("control-plane.autoscaler.manage")
    public Response acknowledgeScalingAction() {
        log.info("Scaling action acknowledged");
        autoscalerService.recordScalingAction();
        return Response.noContent().build();
    }

    /**
     * Gets the autoscaler status including cooldown state.
     *
     * @return the autoscaler status
     */
    @GET
    @Path("/status")
    @Scopes("control-plane.autoscaler.read")
    public AutoscalerStatus getStatus() {
        return new AutoscalerStatus(
                autoscalerService.isInCooldown(),
                autoscalerService.getLastRecommendation().orElse(null)
        );
    }

    /**
     * Status response for the autoscaler.
     *
     * @param inCooldown         whether the autoscaler is in cooldown
     * @param lastRecommendation the most recent recommendation, if any
     */
    public record AutoscalerStatus(
            boolean inCooldown,
            ScalingRecommendation lastRecommendation
    ) {
    }
}
