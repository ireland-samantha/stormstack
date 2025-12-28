package com.lightningfirefly.game.orchestrator;

import java.nio.file.Path;

/**
 * Event fired when a resource download completes or fails.
 *
 * @param resourceId the resource ID
 * @param localPath  the local file path (null if failed)
 * @param status     the download status
 * @param error      the error if failed (null if completed)
 */
public record ResourceDownloadEvent(
        long resourceId,
        Path localPath,
        Status status,
        Exception error
) {
    /**
     * Status of a resource download operation.
     */
    public enum Status {
        COMPLETED,
        FAILED
    }
}
