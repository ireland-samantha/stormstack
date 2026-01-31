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

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.model.User;
import ca.samanthaireland.lightning.auth.model.UserId;
import ca.samanthaireland.lightning.auth.provider.dto.CreateUserRequest;
import ca.samanthaireland.lightning.auth.provider.dto.UpdateUserRequest;
import ca.samanthaireland.lightning.auth.provider.dto.UserResponse;
import ca.samanthaireland.lightning.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.lightning.auth.service.UserService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.lightning.auth.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST resource for user management (admin only).
 */
@Path("/api/users")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class UserResource {

    @Inject
    UserService userService;

    /**
     * List all users.
     */
    @GET
    @Scopes("auth.user.read")
    public List<UserResponse> listUsers() {
        return userService.findAll().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Create a new user.
     */
    @POST
    @Scopes("auth.user.create")
    public Response createUser(@Valid CreateUserRequest request) {
        Set<RoleId> roleIds = request.roleIds() != null
                ? request.roleIds().stream().map(RoleId::fromString).collect(Collectors.toSet())
                : Set.of();

        Set<String> scopes = request.scopes() != null
                ? request.scopes()
                : Set.of();

        User user = userService.createUser(request.username(), request.password(), roleIds, scopes);

        return Response.created(URI.create("/api/users/" + user.id()))
                .entity(UserResponse.from(user))
                .build();
    }

    /**
     * Get a user by ID.
     */
    @GET
    @Path("/{id}")
    @Scopes("auth.user.read")
    public UserResponse getUser(@PathParam("id") String id) {
        UserId userId = UserId.fromString(id);
        return userService.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> AuthException.userNotFound(userId));
    }

    /**
     * Update a user.
     */
    @PUT
    @Path("/{id}")
    @Scopes("auth.user.update")
    public UserResponse updateUser(@PathParam("id") String id, @Valid UpdateUserRequest request) {
        UserId userId = UserId.fromString(id);
        User user = userService.findById(userId)
                .orElseThrow(() -> AuthException.userNotFound(userId));

        if (request.password() != null) {
            user = userService.updatePassword(userId, request.password());
        }

        if (request.roleIds() != null) {
            Set<RoleId> roleIds = request.roleIds().stream()
                    .map(RoleId::fromString)
                    .collect(Collectors.toSet());
            user = userService.updateRoles(userId, roleIds);
        }

        if (request.scopes() != null) {
            user = userService.updateScopes(userId, request.scopes());
        }

        if (request.enabled() != null) {
            user = userService.setEnabled(userId, request.enabled());
        }

        return UserResponse.from(user);
    }

    /**
     * Delete a user.
     */
    @DELETE
    @Path("/{id}")
    @Scopes("auth.user.delete")
    public Response deleteUser(@PathParam("id") String id) {
        UserId userId = UserId.fromString(id);
        userService.deleteUser(userId);
        return Response.noContent().build();
    }
}
