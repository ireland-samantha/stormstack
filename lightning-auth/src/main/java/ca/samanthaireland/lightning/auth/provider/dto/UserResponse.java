/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.provider.dto;

import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response DTO for a user.
 *
 * @param id        the user ID
 * @param username  the username
 * @param roleIds   the role IDs
 * @param scopes    the permission scopes (format: service.resource.operation)
 * @param createdAt when the user was created
 * @param enabled   whether the user is enabled
 */
public record UserResponse(
        String id,
        String username,
        Set<String> roleIds,
        Set<String> scopes,
        Instant createdAt,
        boolean enabled
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id().toString(),
                user.username(),
                user.roleIds().stream().map(RoleId::toString).collect(Collectors.toSet()),
                user.scopes(),
                user.createdAt(),
                user.enabled()
        );
    }
}
