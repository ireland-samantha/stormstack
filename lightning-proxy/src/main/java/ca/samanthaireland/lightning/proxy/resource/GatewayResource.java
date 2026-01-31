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

package ca.samanthaireland.lightning.proxy.resource;

import ca.samanthaireland.lightning.proxy.config.GatewayConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * API Gateway resource that routes requests to backend microservices.
 *
 * <p>Routes:
 * <ul>
 *   <li>/api/auth/** → lightning-auth</li>
 *   <li>/api/users/** → lightning-auth</li>
 *   <li>/api/roles/** → lightning-auth</li>
 *   <li>/api/tokens/** → lightning-auth</li>
 *   <li>/api/match-tokens/** → lightning-auth</li>
 *   <li>/api/validate/** → lightning-auth</li>
 *   <li>/api/deploy/** → lightning-control-plane</li>
 *   <li>/api/cluster/** → lightning-control-plane</li>
 *   <li>/api/nodes/** → lightning-control-plane</li>
 *   <li>/api/matches/** → lightning-control-plane</li>
 *   <li>/api/modules/** → lightning-control-plane</li>
 *   <li>/api/autoscaler/** → lightning-control-plane</li>
 *   <li>/api/dashboard/** → lightning-control-plane</li>
 *   <li>/api/containers/** → lightning-engine</li>
 *   <li>/api/ai/** → lightning-engine</li>
 *   <li>/api/node/** → lightning-engine</li>
 *   <li>/api/health/** → lightning-engine</li>
 * </ul>
 */
@Path("/api")
@ApplicationScoped
public class GatewayResource {
    private static final Logger log = LoggerFactory.getLogger(GatewayResource.class);

    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "X-Request-ID",
            "X-Correlation-ID"
    );

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "transfer-encoding",
            "content-length"
    );

    private final GatewayConfig config;
    private final HttpClient httpClient;

    @Inject
    public GatewayResource(GatewayConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
    }

    // =========================================================================
    // Auth Service Routes (GET)
    // =========================================================================

    @GET @Path("/auth/{path:.*}")
    public Response authGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/auth/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/auth/{path:.*}")
    public Response authPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/auth/" + path, uriInfo, headers, "POST", body);
    }

    @PUT @Path("/auth/{path:.*}")
    public Response authPut(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/auth/" + path, uriInfo, headers, "PUT", body);
    }

    @DELETE @Path("/auth/{path:.*}")
    public Response authDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/auth/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/users/{path:.*}")
    public Response usersGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/users/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/users/{path:.*}")
    public Response usersPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/users/" + path, uriInfo, headers, "POST", body);
    }

    @PUT @Path("/users/{path:.*}")
    public Response usersPut(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/users/" + path, uriInfo, headers, "PUT", body);
    }

    @DELETE @Path("/users/{path:.*}")
    public Response usersDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/users/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/roles/{path:.*}")
    public Response rolesGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/roles/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/roles/{path:.*}")
    public Response rolesPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/roles/" + path, uriInfo, headers, "POST", body);
    }

    @PUT @Path("/roles/{path:.*}")
    public Response rolesPut(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/roles/" + path, uriInfo, headers, "PUT", body);
    }

    @DELETE @Path("/roles/{path:.*}")
    public Response rolesDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/roles/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/tokens/{path:.*}")
    public Response tokensGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/tokens/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/tokens/{path:.*}")
    public Response tokensPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/tokens/" + path, uriInfo, headers, "POST", body);
    }

    @DELETE @Path("/tokens/{path:.*}")
    public Response tokensDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/tokens/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/match-tokens/{path:.*}")
    public Response matchTokensGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/match-tokens/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/match-tokens/{path:.*}")
    public Response matchTokensPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/match-tokens/" + path, uriInfo, headers, "POST", body);
    }

    @DELETE @Path("/match-tokens/{path:.*}")
    public Response matchTokensDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.authUrl(), "/api/match-tokens/" + path, uriInfo, headers, "DELETE", null);
    }

    @POST @Path("/validate/{path:.*}")
    public Response validatePost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.authUrl(), "/api/validate/" + path, uriInfo, headers, "POST", body);
    }

    // =========================================================================
    // Control Plane Routes
    // =========================================================================

    @GET @Path("/deploy/{path:.*}")
    public Response deployGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/deploy/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/deploy/{path:.*}")
    public Response deployPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/deploy/" + path, uriInfo, headers, "POST", body);
    }

    @DELETE @Path("/deploy/{path:.*}")
    public Response deployDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/deploy/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/cluster/{path:.*}")
    public Response clusterGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/cluster/" + path, uriInfo, headers, "GET", null);
    }

    @GET @Path("/nodes/{path:.*}")
    public Response nodesGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/nodes/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/nodes/{path:.*}")
    public Response nodesPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/nodes/" + path, uriInfo, headers, "POST", body);
    }

    @PUT @Path("/nodes/{path:.*}")
    public Response nodesPut(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/nodes/" + path, uriInfo, headers, "PUT", body);
    }

    @DELETE @Path("/nodes/{path:.*}")
    public Response nodesDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/nodes/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/matches/{path:.*}")
    public Response matchesGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/matches/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/matches/{path:.*}")
    public Response matchesPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/matches/" + path, uriInfo, headers, "POST", body);
    }

    @DELETE @Path("/matches/{path:.*}")
    public Response matchesDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/matches/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/modules/{path:.*}")
    public Response modulesGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/modules/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/modules/{path:.*}")
    public Response modulesPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/modules/" + path, uriInfo, headers, "POST", body);
    }

    @GET @Path("/autoscaler/{path:.*}")
    public Response autoscalerGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/autoscaler/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/autoscaler/{path:.*}")
    public Response autoscalerPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.controlPlaneUrl(), "/api/autoscaler/" + path, uriInfo, headers, "POST", body);
    }

    @GET @Path("/dashboard/{path:.*}")
    public Response dashboardGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.controlPlaneUrl(), "/api/dashboard/" + path, uriInfo, headers, "GET", null);
    }

    // =========================================================================
    // Engine Routes
    // =========================================================================

    @GET @Path("/containers/{path:.*}")
    public Response containersGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.engineUrl(), "/api/containers/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/containers/{path:.*}")
    public Response containersPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.engineUrl(), "/api/containers/" + path, uriInfo, headers, "POST", body);
    }

    @PUT @Path("/containers/{path:.*}")
    public Response containersPut(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.engineUrl(), "/api/containers/" + path, uriInfo, headers, "PUT", body);
    }

    @DELETE @Path("/containers/{path:.*}")
    public Response containersDelete(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.engineUrl(), "/api/containers/" + path, uriInfo, headers, "DELETE", null);
    }

    @GET @Path("/ai/{path:.*}")
    public Response aiGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.engineUrl(), "/api/ai/" + path, uriInfo, headers, "GET", null);
    }

    @POST @Path("/ai/{path:.*}")
    public Response aiPost(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo, InputStream body) {
        return proxy(config.engineUrl(), "/api/ai/" + path, uriInfo, headers, "POST", body);
    }

    @GET @Path("/node/{path:.*}")
    public Response nodeGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.engineUrl(), "/api/node/" + path, uriInfo, headers, "GET", null);
    }

    @GET @Path("/health/{path:.*}")
    public Response healthGet(@PathParam("path") String path, @Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return proxy(config.engineUrl(), "/api/health/" + path, uriInfo, headers, "GET", null);
    }

    // =========================================================================
    // Proxy Implementation
    // =========================================================================

    private Response proxy(String baseUrl, String path, UriInfo uriInfo, HttpHeaders headers, String method, InputStream body) {
        try {
            String queryString = uriInfo.getRequestUri().getRawQuery();
            String url = baseUrl + path;
            if (queryString != null && !queryString.isEmpty()) {
                url += "?" + queryString;
            }

            log.debug("Proxying {} {} → {}", method, uriInfo.getRequestUri(), url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()));

            for (String header : FORWARDED_HEADERS) {
                String value = headers.getHeaderString(header);
                if (value != null) {
                    requestBuilder.header(header, value);
                }
            }

            byte[] bodyBytes = body != null ? body.readAllBytes() : new byte[0];
            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                case "DELETE" -> requestBuilder.DELETE();
                default -> requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            }

            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());

            response.headers().map().forEach((name, values) -> {
                if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    values.forEach(value -> responseBuilder.header(name, value));
                }
            });

            return responseBuilder.entity(response.body()).build();

        } catch (Exception e) {
            log.error("Proxy error for {} {}: {}", method, path, e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "Gateway error: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
