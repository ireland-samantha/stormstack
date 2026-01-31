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

package ca.samanthaireland.stormstack.thunder.auth.provider.http;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.CreateRoleRequest;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.RoleResponse;
import ca.samanthaireland.stormstack.thunder.auth.provider.dto.UpdateRoleRequest;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.annotation.Scopes;
import ca.samanthaireland.stormstack.thunder.auth.service.RoleService;
import ca.samanthaireland.stormstack.thunder.auth.service.ScopeService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import static ca.samanthaireland.stormstack.thunder.auth.provider.http.MediaTypes.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST resource for role management.
 */
@Path("/api/roles")
@Produces({V1_JSON, JSON})
@Consumes({V1_JSON, JSON})
public class RoleResource {

    @Inject
    RoleService roleService;

    @Inject
    ScopeService scopeService;

    /**
     * List all roles.
     */
    @GET
    @Scopes("auth.role.read")
    public List<RoleResponse> listRoles() {
        return roleService.findAll().stream()
                .map(RoleResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Create a new role.
     */
    @POST
    @Scopes("auth.role.create")
    public Response createRole(@Valid CreateRoleRequest request) {
        Set<RoleId> includedRoleIds = request.includedRoleIds() != null
                ? request.includedRoleIds().stream().map(RoleId::fromString).collect(Collectors.toSet())
                : Set.of();

        Set<String> scopes = request.scopes() != null ? request.scopes() : Set.of();

        Role role = roleService.createRole(request.name(), request.description(), includedRoleIds, scopes);

        return Response.created(URI.create("/api/roles/" + role.id()))
                .entity(RoleResponse.from(role))
                .build();
    }

    /**
     * Get a role by ID.
     */
    @GET
    @Path("/{id}")
    @Scopes("auth.role.read")
    public RoleResponse getRole(@PathParam("id") String id) {
        RoleId roleId = RoleId.fromString(id);
        return roleService.findById(roleId)
                .map(RoleResponse::from)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));
    }

    /**
     * Update a role.
     */
    @PUT
    @Path("/{id}")
    @Scopes("auth.role.update")
    public RoleResponse updateRole(@PathParam("id") String id, @Valid UpdateRoleRequest request) {
        RoleId roleId = RoleId.fromString(id);
        Role role = roleService.findById(roleId)
                .orElseThrow(() -> AuthException.roleNotFound(roleId));

        if (request.description() != null) {
            role = roleService.updateDescription(roleId, request.description());
        }

        if (request.includedRoleIds() != null) {
            Set<RoleId> includedRoleIds = request.includedRoleIds().stream()
                    .map(RoleId::fromString)
                    .collect(Collectors.toSet());
            role = roleService.updateIncludedRoles(roleId, includedRoleIds);
        }

        if (request.scopes() != null) {
            role = scopeService.updateScopes(roleId, request.scopes());
        }

        return RoleResponse.from(role);
    }

    /**
     * Delete a role.
     */
    @DELETE
    @Path("/{id}")
    @Scopes("auth.role.delete")
    public Response deleteRole(@PathParam("id") String id) {
        RoleId roleId = RoleId.fromString(id);
        roleService.deleteRole(roleId);
        return Response.noContent().build();
    }

    // =========================================================================
    // Scope Management
    // =========================================================================

    /**
     * Replace all scopes for a role.
     */
    @PUT
    @Path("/{id}/scopes")
    @Scopes("auth.role.update")
    public RoleResponse updateScopes(@PathParam("id") String id, Set<String> scopes) {
        RoleId roleId = RoleId.fromString(id);
        Role role = scopeService.updateScopes(roleId, scopes != null ? scopes : Set.of());
        return RoleResponse.from(role);
    }

    /**
     * Add a scope to a role.
     */
    @POST
    @Path("/{id}/scopes/{scope}")
    @Scopes("auth.role.update")
    public RoleResponse addScope(@PathParam("id") String id, @PathParam("scope") String scope) {
        RoleId roleId = RoleId.fromString(id);
        Role role = scopeService.addScope(roleId, scope);
        return RoleResponse.from(role);
    }

    /**
     * Remove a scope from a role.
     */
    @DELETE
    @Path("/{id}/scopes/{scope}")
    @Scopes("auth.role.update")
    public RoleResponse removeScope(@PathParam("id") String id, @PathParam("scope") String scope) {
        RoleId roleId = RoleId.fromString(id);
        Role role = scopeService.removeScope(roleId, scope);
        return RoleResponse.from(role);
    }

    /**
     * Get all resolved scopes for a role (including inherited from included roles).
     */
    @GET
    @Path("/{id}/scopes/resolved")
    @Scopes("auth.role.read")
    public Set<String> getResolvedScopes(@PathParam("id") String id) {
        RoleId roleId = RoleId.fromString(id);
        return scopeService.resolveScopes(roleId);
    }
}
