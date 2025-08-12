package com.lightningfirefly.engine.quarkus.api.dto;

public record ChunkResponse(
    long resourceId,
    int chunkIndex,
    int totalChunks,
    long totalSize,
    byte[] data
) {}
