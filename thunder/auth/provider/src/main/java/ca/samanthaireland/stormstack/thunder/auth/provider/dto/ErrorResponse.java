/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.dto;

import java.time.Instant;

/**
 * Standard error response DTO.
 *
 * @param code      the error code
 * @param message   the error message
 * @param timestamp when the error occurred
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
