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
import ca.samanthaireland.stormstack.thunder.controlplane.node.model.NodeId;
import ca.samanthaireland.stormstack.thunder.controlplane.provider.auth.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.controlplane.proxy.service.NodeProxyService;
import ca.samanthaireland.stormstack.thunder.controlplane.proxy.service.ProxyResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * REST resource for proxying requests to engine nodes.
 *
 * <p>This endpoint allows external clients (like Thunder CLI) to reach engine nodes
 * through the control plane when direct network access is not available (e.g., when
 * nodes are on Docker-internal networks).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/nodes/{nodeId}/proxy/{path:.*} - Proxy GET request to node</li>
 *   <li>POST /api/nodes/{nodeId}/proxy/{path:.*} - Proxy POST request to node</li>
 *   <li>PUT /api/nodes/{nodeId}/proxy/{path:.*} - Proxy PUT request to node</li>
 *   <li>DELETE /api/nodes/{nodeId}/proxy/{path:.*} - Proxy DELETE request to node</li>
 *   <li>PATCH /api/nodes/{nodeId}/proxy/{path:.*} - Proxy PATCH request to node</li>
 *   <li>GET /api/nodes/proxy/status - Get proxy status</li>
 *   <li>POST /api/nodes/proxy/enable - Enable proxy</li>
 *   <li>POST /api/nodes/proxy/disable - Disable proxy</li>
 * </ul>
 */
@Path("/api/nodes")
public class NodeProxyResource {
    private static final Logger log = LoggerFactory.getLogger(NodeProxyResource.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_TOKEN_HEADER = "X-Api-Token";

    private final NodeProxyService nodeProxyService;
    private final AuthServiceClient authServiceClient;

    @Inject
    public NodeProxyResource(NodeProxyService nodeProxyService, AuthServiceClient authServiceClient) {
        this.nodeProxyService = nodeProxyService;
        this.authServiceClient = authServiceClient;
    }

    /**
     * Proxy a GET request to the target node.
     */
    @GET
    @Path("/{nodeId}/proxy/{path:.*}")
    @Scopes("control-plane.node.proxy")
    public Response proxyGet(
            @PathParam("nodeId") String nodeId,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers
    ) {
        return doProxy(nodeId, "GET", path, uriInfo, headers, null);
    }

    /**
     * Proxy a POST request to the target node.
     */
    @POST
    @Path("/{nodeId}/proxy/{path:.*}")
    @Scopes("control-plane.node.proxy")
    public Response proxyPost(
            @PathParam("nodeId") String nodeId,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            InputStream body
    ) {
        return doProxy(nodeId, "POST", path, uriInfo, headers, body);
    }

    /**
     * Proxy a PUT request to the target node.
     */
    @PUT
    @Path("/{nodeId}/proxy/{path:.*}")
    @Scopes("control-plane.node.proxy")
    public Response proxyPut(
            @PathParam("nodeId") String nodeId,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            InputStream body
    ) {
        return doProxy(nodeId, "PUT", path, uriInfo, headers, body);
    }

    /**
     * Proxy a DELETE request to the target node.
     */
    @DELETE
    @Path("/{nodeId}/proxy/{path:.*}")
    @Scopes("control-plane.node.proxy")
    public Response proxyDelete(
            @PathParam("nodeId") String nodeId,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers
    ) {
        return doProxy(nodeId, "DELETE", path, uriInfo, headers, null);
    }

    /**
     * Proxy a PATCH request to the target node.
     */
    @PATCH
    @Path("/{nodeId}/proxy/{path:.*}")
    @Scopes("control-plane.node.proxy")
    public Response proxyPatch(
            @PathParam("nodeId") String nodeId,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            InputStream body
    ) {
        return doProxy(nodeId, "PATCH", path, uriInfo, headers, body);
    }

    /**
     * Get the proxy status.
     */
    @GET
    @Path("/proxy/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Scopes("control-plane.node.proxy")
    public Response getProxyStatus() {
        boolean enabled = nodeProxyService.isEnabled();
        return Response.ok(Map.of("enabled", enabled)).build();
    }

    /**
     * Enable the proxy.
     */
    @POST
    @Path("/proxy/enable")
    @Produces(MediaType.APPLICATION_JSON)
    @Scopes("control-plane.node.proxy.manage")
    public Response enableProxy() {
        nodeProxyService.setEnabled(true);
        log.info("Node proxy enabled");
        return Response.ok(Map.of("enabled", true, "message", "Proxy enabled")).build();
    }

    /**
     * Disable the proxy.
     */
    @POST
    @Path("/proxy/disable")
    @Produces(MediaType.APPLICATION_JSON)
    @Scopes("control-plane.node.proxy.manage")
    public Response disableProxy() {
        nodeProxyService.setEnabled(false);
        log.info("Node proxy disabled");
        return Response.ok(Map.of("enabled", false, "message", "Proxy disabled")).build();
    }

    private Response doProxy(
            String nodeId,
            String method,
            String path,
            UriInfo uriInfo,
            HttpHeaders httpHeaders,
            InputStream bodyStream
    ) {
        log.debug("Proxying {} request to node {} path {}", method, nodeId, path);

        // Build query parameters
        Map<String, String> queryParams = new HashMap<>();
        uriInfo.getQueryParameters().forEach((key, values) -> {
            if (!values.isEmpty()) {
                queryParams.put(key, values.get(0));
            }
        });

        // Build headers to forward
        Map<String, String> headers = new HashMap<>();
        httpHeaders.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                String lowerName = name.toLowerCase();
                // Forward specific headers (except authorization - handled separately)
                if (lowerName.equals("content-type") ||
                        lowerName.equals("accept") ||
                        lowerName.startsWith("x-")) {
                    headers.put(name, values.get(0));
                }
            }
        });

        // Exchange token for JWT before forwarding to engine node
        // API tokens (lat_...) are sent in X-Api-Token header and take priority
        // The engine expects JWTs in Authorization header
        String apiToken = httpHeaders.getHeaderString(API_TOKEN_HEADER);
        String authHeader = httpHeaders.getHeaderString("Authorization");

        if (apiToken != null && !apiToken.isEmpty()) {
            // API token - must exchange for JWT
            String jwtToForward = exchangeTokenForJwt(apiToken);
            if (jwtToForward != null) {
                headers.put("Authorization", BEARER_PREFIX + jwtToForward);
            } else {
                log.warn("Failed to exchange API token, request may fail at engine");
            }
        } else if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            // JWT in Authorization header - forward as-is or exchange if needed
            String token = authHeader.substring(BEARER_PREFIX.length());
            String jwtToForward = exchangeTokenForJwt(token);
            if (jwtToForward != null) {
                headers.put("Authorization", BEARER_PREFIX + jwtToForward);
            } else {
                // Forward original token (should be a valid JWT)
                headers.put("Authorization", authHeader);
            }
        }

        // Read body if present
        byte[] body = null;
        if (bodyStream != null) {
            try {
                body = bodyStream.readAllBytes();
            } catch (IOException e) {
                log.error("Failed to read request body: {}", e.getMessage());
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Failed to read request body\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }
        }

        // Proxy the request
        ProxyResponse proxyResponse = nodeProxyService.proxy(
                NodeId.of(nodeId),
                method,
                path,
                queryParams,
                headers,
                body
        );

        // Build JAX-RS response
        Response.ResponseBuilder responseBuilder = Response.status(proxyResponse.statusCode());

        // Forward response headers
        proxyResponse.headers().forEach(responseBuilder::header);

        // Set body if present
        if (proxyResponse.hasBody()) {
            responseBuilder.entity(proxyResponse.body());
        }

        return responseBuilder.build();
    }

    /**
     * Exchange a token (API token or JWT) for a JWT suitable for forwarding.
     *
     * <p>API tokens (lat_...) are opaque session tokens that only the auth service
     * can validate. When proxying requests to engine nodes, we need to exchange
     * them for JWTs that the engine can validate locally.
     *
     * @param token the incoming token (may be API token or JWT)
     * @return the JWT to use for forwarding, or null if exchange fails
     */
    private String exchangeTokenForJwt(String token) {
        // If token already looks like a JWT (starts with eyJ), return as-is
        if (token.startsWith("eyJ")) {
            log.debug("Token is already a JWT, forwarding as-is");
            return token;
        }

        // API token - need to exchange via auth service
        if (!authServiceClient.isRemoteValidationEnabled()) {
            log.warn("Cannot exchange API token - remote auth validation not configured");
            return null;
        }

        log.debug("Exchanging API token for JWT via auth service");
        var result = authServiceClient.validateToken(token);

        return switch (result) {
            case AuthServiceClient.ValidationResult.Success success -> {
                log.debug("Token exchange successful for user: {}", success.username());
                yield success.jwtToken();
            }
            case AuthServiceClient.ValidationResult.Failure failure -> {
                log.warn("Token exchange failed: {}", failure.message());
                yield null;
            }
        };
    }
}
