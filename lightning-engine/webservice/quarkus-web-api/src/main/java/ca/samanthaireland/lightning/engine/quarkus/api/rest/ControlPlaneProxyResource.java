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

package ca.samanthaireland.lightning.engine.quarkus.api.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Proxy resource for forwarding requests to the Lightning Control Plane.
 *
 * <p>This resource proxies all requests under /api/control-plane/* to the configured
 * control plane service, forwarding authentication headers.
 *
 * <p>Endpoints proxied:
 * <ul>
 *   <li>/api/control-plane/dashboard/** - Dashboard overview, nodes, matches</li>
 *   <li>/api/control-plane/cluster/** - Cluster status and node management</li>
 *   <li>/api/control-plane/matches/** - Match lifecycle management</li>
 *   <li>/api/control-plane/nodes/** - Node registration and heartbeats</li>
 *   <li>/api/control-plane/modules/** - Module registry</li>
 *   <li>/api/control-plane/deploy/** - Deployment operations</li>
 *   <li>/api/control-plane/autoscaler/** - Autoscaler status and recommendations</li>
 * </ul>
 */
@Path("/api/control-plane")
@PermitAll
public class ControlPlaneProxyResource {
    private static final Logger log = LoggerFactory.getLogger(ControlPlaneProxyResource.class);

    private final HttpClient httpClient;
    private final String controlPlaneUrl;

    @Inject
    public ControlPlaneProxyResource(
            @ConfigProperty(name = "control-plane.url", defaultValue = "http://localhost:8081") String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // =========================================================================
    // Dashboard Endpoints
    // =========================================================================

    @GET
    @Path("/dashboard/overview")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDashboardOverview(@Context HttpHeaders headers) {
        return proxyGet("/api/dashboard/overview", headers);
    }

    @GET
    @Path("/dashboard/nodes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDashboardNodes(
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("status") String status,
            @Context HttpHeaders headers) {
        String query = buildQueryString(Map.of(
                "page", page,
                "pageSize", pageSize,
                "status", status
        ));
        return proxyGet("/api/dashboard/nodes" + query, headers);
    }

    @GET
    @Path("/dashboard/matches")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDashboardMatches(
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("status") String status,
            @QueryParam("nodeId") String nodeId,
            @Context HttpHeaders headers) {
        String query = buildQueryString(Map.of(
                "page", page,
                "pageSize", pageSize,
                "status", status,
                "nodeId", nodeId
        ));
        return proxyGet("/api/dashboard/matches" + query, headers);
    }

    // =========================================================================
    // Cluster Endpoints
    // =========================================================================

    @GET
    @Path("/cluster/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterStatus(@Context HttpHeaders headers) {
        return proxyGet("/api/cluster/status", headers);
    }

    @GET
    @Path("/cluster/nodes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterNodes(@Context HttpHeaders headers) {
        return proxyGet("/api/cluster/nodes", headers);
    }

    @GET
    @Path("/cluster/nodes/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterNode(@PathParam("nodeId") String nodeId, @Context HttpHeaders headers) {
        return proxyGet("/api/cluster/nodes/" + nodeId, headers);
    }

    // =========================================================================
    // Match Endpoints
    // =========================================================================

    @GET
    @Path("/matches")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMatches(@QueryParam("status") String status, @Context HttpHeaders headers) {
        String query = status != null ? "?status=" + status : "";
        return proxyGet("/api/matches" + query, headers);
    }

    @GET
    @Path("/matches/{matchId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMatch(@PathParam("matchId") String matchId, @Context HttpHeaders headers) {
        return proxyGet("/api/matches/" + matchId, headers);
    }

    @POST
    @Path("/matches/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createMatch(String body, @Context HttpHeaders headers) {
        return proxyPost("/api/matches/create", body, headers);
    }

    @POST
    @Path("/matches/{matchId}/finish")
    @Produces(MediaType.APPLICATION_JSON)
    public Response finishMatch(@PathParam("matchId") String matchId, @Context HttpHeaders headers) {
        return proxyPost("/api/matches/" + matchId + "/finish", null, headers);
    }

    @DELETE
    @Path("/matches/{matchId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteMatch(@PathParam("matchId") String matchId, @Context HttpHeaders headers) {
        return proxyDelete("/api/matches/" + matchId, headers);
    }

    // =========================================================================
    // Node Management Endpoints
    // =========================================================================

    @POST
    @Path("/nodes/{nodeId}/drain")
    @Produces(MediaType.APPLICATION_JSON)
    public Response drainNode(@PathParam("nodeId") String nodeId, @Context HttpHeaders headers) {
        return proxyPost("/api/nodes/" + nodeId + "/drain", null, headers);
    }

    @DELETE
    @Path("/nodes/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deregisterNode(@PathParam("nodeId") String nodeId, @Context HttpHeaders headers) {
        return proxyDelete("/api/nodes/" + nodeId, headers);
    }

    // =========================================================================
    // Module Endpoints
    // =========================================================================

    @GET
    @Path("/modules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModules(@Context HttpHeaders headers) {
        return proxyGet("/api/modules", headers);
    }

    @GET
    @Path("/modules/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModule(@PathParam("name") String name, @Context HttpHeaders headers) {
        return proxyGet("/api/modules/" + name, headers);
    }

    @POST
    @Path("/modules/{name}/{version}/distribute")
    @Produces(MediaType.APPLICATION_JSON)
    public Response distributeModule(
            @PathParam("name") String name,
            @PathParam("version") String version,
            @Context HttpHeaders headers) {
        return proxyPost("/api/modules/" + name + "/" + version + "/distribute", null, headers);
    }

    // =========================================================================
    // Deploy Endpoints
    // =========================================================================

    @POST
    @Path("/deploy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deploy(String body, @Context HttpHeaders headers) {
        return proxyPost("/api/deploy", body, headers);
    }

    @GET
    @Path("/deploy/{matchId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeployment(@PathParam("matchId") String matchId, @Context HttpHeaders headers) {
        return proxyGet("/api/deploy/" + matchId, headers);
    }

    @DELETE
    @Path("/deploy/{matchId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response undeploy(@PathParam("matchId") String matchId, @Context HttpHeaders headers) {
        return proxyDelete("/api/deploy/" + matchId, headers);
    }

    // =========================================================================
    // Autoscaler Endpoints
    // =========================================================================

    @GET
    @Path("/autoscaler/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAutoscalerStatus(@Context HttpHeaders headers) {
        return proxyGet("/api/autoscaler/status", headers);
    }

    @GET
    @Path("/autoscaler/recommendation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAutoscalerRecommendation(@Context HttpHeaders headers) {
        return proxyGet("/api/autoscaler/recommendation", headers);
    }

    @POST
    @Path("/autoscaler/acknowledge")
    @Produces(MediaType.APPLICATION_JSON)
    public Response acknowledgeScalingAction(@Context HttpHeaders headers) {
        return proxyPost("/api/autoscaler/acknowledge", null, headers);
    }

    // =========================================================================
    // Proxy Helper Methods
    // =========================================================================

    private Response proxyGet(String path, HttpHeaders headers) {
        return proxyRequest("GET", path, null, headers);
    }

    private Response proxyPost(String path, String body, HttpHeaders headers) {
        return proxyRequest("POST", path, body, headers);
    }

    private Response proxyDelete(String path, HttpHeaders headers) {
        return proxyRequest("DELETE", path, null, headers);
    }

    private Response proxyRequest(String method, String path, String body, HttpHeaders headers) {
        try {
            String url = controlPlaneUrl + path;
            log.debug("Proxying {} {} to control plane", method, path);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");

            // Forward Authorization header
            String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                requestBuilder.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            // Set method and body
            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> {
                    if (body != null) {
                        requestBuilder.header("Content-Type", "application/json");
                        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                    }
                }
                case "DELETE" -> requestBuilder.DELETE();
            }

            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());

            // Forward content type header
            response.headers().firstValue("Content-Type")
                    .ifPresent(ct -> responseBuilder.header("Content-Type", ct));

            return responseBuilder.entity(response.body()).build();

        } catch (Exception e) {
            log.error("Failed to proxy request to control plane: {}", e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "Failed to connect to control plane: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    private String buildQueryString(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                if (first) {
                    sb.append("?");
                    first = false;
                } else {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return sb.toString();
    }
}
