package com.lightningfirefly.engine.quarkus.api.rest;

import com.lightningfirefly.engine.core.resources.Resource;
import com.lightningfirefly.engine.core.resources.ResourceType;
import com.lightningfirefly.engine.internal.core.resource.OnDiskResourceManager;
import com.lightningfirefly.engine.internal.core.resource.OnDiskResourceManager.ChunkedResource;
import com.lightningfirefly.engine.internal.core.resource.OnDiskResourceManager.StoredResource;
import com.lightningfirefly.engine.quarkus.api.dto.ChunkResponse;
import com.lightningfirefly.engine.quarkus.api.dto.ResourceResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * REST resource for managing binary resources with chunked upload/download support.
 */
@Path("/api/resources")
@Slf4j
public class ResourceResource {

    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024; // 64KB chunks

    @Inject
    OnDiskResourceManager resourceManager;

    /**
     * Upload a new resource via multipart form data.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadResource(
            @RestForm("file") FileUpload file,
            @RestForm("resourceName") String resourceName,
            @RestForm("resourceType") String resourceType) {

        if (file == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("File is required")
                    .build();
        }

        try {
            byte[] data = Files.readAllBytes(file.uploadedFile());
            String name = resourceName != null ? resourceName : file.fileName();
            ResourceType type = resourceType != null
                    ? ResourceType.valueOf(resourceType.toUpperCase())
                    : ResourceType.TEXTURE;

            StoredResource resource = new StoredResource(
                    0, // ID will be assigned by manager
                    name,
                    "",
                    data,
                    type
            );

            long assignedId = resourceManager.saveResource(resource);

            log.info("Uploaded resource: id={}, name={}, type={}, size={}", assignedId, name, type, data.length);

            return Response.status(Response.Status.CREATED)
                    .entity(new ResourceResponse(assignedId, name, type, data.length))
                    .build();
        } catch (IOException e) {
            log.error("Failed to upload resource", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to upload resource: " + e.getMessage())
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid resource type: " + resourceType)
                    .build();
        }
    }

    /**
     * Upload a resource chunk for large file uploads.
     */
    @POST
    @Path("/{resourceId}/chunks/{chunkIndex}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadChunk(
            @PathParam("resourceId") long resourceId,
            @PathParam("chunkIndex") int chunkIndex,
            @QueryParam("totalChunks") int totalChunks,
            @QueryParam("resourceName") String resourceName,
            @QueryParam("resourceType") String resourceType,
            InputStream chunkData) {

        try {
            byte[] data = chunkData.readAllBytes();

            // For chunked uploads, we need to handle assembly
            // This is a simplified implementation - a production system would use temp storage
            ResourceType type = resourceType != null
                    ? ResourceType.valueOf(resourceType.toUpperCase())
                    : ResourceType.TEXTURE;

            if (chunkIndex == 0 && totalChunks == 1) {
                // Single chunk upload
                StoredResource resource = new StoredResource(
                        resourceId > 0 ? resourceId : 0,
                        resourceName != null ? resourceName : "resource_" + resourceId,
                        "",
                        data,
                        type
                );
                resourceManager.saveResource(resource);
            }

            log.info("Received chunk {} of {} for resource {}, size={} bytes",
                    chunkIndex, totalChunks, resourceId, data.length);

            return Response.ok()
                    .entity(new ChunkResponse(resourceId, chunkIndex, totalChunks, data.length, null))
                    .build();
        } catch (IOException e) {
            log.error("Failed to process chunk", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to process chunk: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get resource metadata by ID.
     */
    @GET
    @Path("/{resourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResource(@PathParam("resourceId") long resourceId) {
        return resourceManager.getResource(resourceId)
                .map(r -> Response.ok(new ResourceResponse(
                        r.resourceId(),
                        r.resourceName(),
                        r.resourceType(),
                        r.blob().length
                )).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Download full resource data.
     */
    @GET
    @Path("/{resourceId}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadResource(@PathParam("resourceId") long resourceId) {
        return resourceManager.getResource(resourceId)
                .map(r -> Response.ok(r.blob())
                        .header("Content-Disposition", "attachment; filename=\"" + r.resourceName() + "\"")
                        .header("Content-Length", r.blob().length)
                        .build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Download a specific chunk of a resource for chunked retrieval.
     */
    @GET
    @Path("/{resourceId}/chunks/{chunkIndex}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadChunk(
            @PathParam("resourceId") long resourceId,
            @PathParam("chunkIndex") int chunkIndex,
            @QueryParam("chunkSize") @DefaultValue("65536") int chunkSize) {

        return resourceManager.getResourceChunked(resourceId, chunkSize)
                .map(chunked -> {
                    try {
                        if (chunkIndex < 0 || chunkIndex >= chunked.getTotalChunks()) {
                            return Response.status(Response.Status.BAD_REQUEST)
                                    .entity("Invalid chunk index")
                                    .build();
                        }

                        byte[] data = chunked.readChunk(chunkIndex);
                        return Response.ok(data)
                                .header("X-Chunk-Index", chunkIndex)
                                .header("X-Total-Chunks", chunked.getTotalChunks())
                                .header("X-Total-Size", chunked.totalSize())
                                .header("X-Chunk-Size", chunkSize)
                                .header("Content-Length", data.length)
                                .build();
                    } catch (IOException e) {
                        log.error("Failed to read chunk", e);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity("Failed to read chunk")
                                .build();
                    }
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Get chunk metadata for a resource.
     */
    @GET
    @Path("/{resourceId}/chunks")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChunkInfo(
            @PathParam("resourceId") long resourceId,
            @QueryParam("chunkSize") @DefaultValue("65536") int chunkSize) {

        return resourceManager.getResourceChunked(resourceId, chunkSize)
                .map(chunked -> Response.ok(new ChunkResponse(
                        chunked.resourceId(),
                        0,
                        chunked.getTotalChunks(),
                        chunked.totalSize(),
                        null
                )).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Stream resource data (for large files).
     */
    @GET
    @Path("/{resourceId}/stream")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response streamResource(@PathParam("resourceId") long resourceId) {
        return resourceManager.getResourceChunked(resourceId, DEFAULT_CHUNK_SIZE)
                .map(chunked -> {
                    StreamingOutput stream = output -> {
                        for (int i = 0; i < chunked.getTotalChunks(); i++) {
                            byte[] chunk = chunked.readChunk(i);
                            output.write(chunk);
                            output.flush();
                        }
                    };

                    return Response.ok(stream)
                            .header("Content-Disposition", "attachment; filename=\"" + chunked.resourceName() + "\"")
                            .header("Content-Length", chunked.totalSize())
                            .build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * List all resources.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ResourceResponse> listResources() {
        return resourceManager.requestResource()
                .map(r -> new ResourceResponse(
                        r.resourceId(),
                        r.resourceName(),
                        r.resourceType(),
                        r.blob().length
                ))
                .toList();
    }

    /**
     * Delete a resource.
     */
    @DELETE
    @Path("/{resourceId}")
    public Response deleteResource(@PathParam("resourceId") long resourceId) {
        if (!resourceManager.hasResource(resourceId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (resourceManager.deleteResource(resourceId)) {
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to delete resource")
                    .build();
        }
    }
}
