/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScopeServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    private ScopeServiceImpl scopeService;

    @BeforeEach
    void setUp() {
        scopeService = new ScopeServiceImpl(roleRepository);
    }

    @Test
    void constructor_withNullRepository_throwsNullPointer() {
        assertThatThrownBy(() -> new ScopeServiceImpl(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("RoleRepository");
    }

    // ============= updateScopes Tests =============

    @Test
    void updateScopes_withValidRole_updatesAndReturnsRole() {
        RoleId roleId = RoleId.generate();
        Role existingRole = Role.create("admin", "Admin role", Set.of(), Set.of("old.scope"));
        Role updatedRole = existingRole.withScopes(Set.of("new.scope1", "new.scope2"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(any(Role.class))).thenReturn(updatedRole);

        Role result = scopeService.updateScopes(roleId, Set.of("new.scope1", "new.scope2"));

        assertThat(result.scopes()).containsExactlyInAnyOrder("new.scope1", "new.scope2");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void updateScopes_withNonExistentRole_throwsRoleNotFound() {
        RoleId roleId = RoleId.generate();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopeService.updateScopes(roleId, Set.of("scope")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.ROLE_NOT_FOUND);
                });
    }

    @Test
    void updateScopes_withNullRoleId_throwsNullPointer() {
        assertThatThrownBy(() -> scopeService.updateScopes(null, Set.of("scope")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("RoleId");
    }

    @Test
    void updateScopes_withNullScopes_throwsNullPointer() {
        RoleId roleId = RoleId.generate();
        assertThatThrownBy(() -> scopeService.updateScopes(roleId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Scopes");
    }

    // ============= addScope Tests =============

    @Test
    void addScope_withValidRole_addsNewScope() {
        RoleId roleId = RoleId.generate();
        Role existingRole = Role.create("admin", "Admin role", Set.of(), Set.of("existing.scope"));
        Role updatedRole = existingRole.withScopes(Set.of("existing.scope", "new.scope"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(any(Role.class))).thenReturn(updatedRole);

        Role result = scopeService.addScope(roleId, "new.scope");

        assertThat(result.scopes()).containsExactlyInAnyOrder("existing.scope", "new.scope");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void addScope_withExistingScope_returnsUnchangedRole() {
        RoleId roleId = RoleId.generate();
        Role existingRole = Role.create("admin", "Admin role", Set.of(), Set.of("existing.scope"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));

        Role result = scopeService.addScope(roleId, "existing.scope");

        assertThat(result).isEqualTo(existingRole);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void addScope_withNonExistentRole_throwsRoleNotFound() {
        RoleId roleId = RoleId.generate();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopeService.addScope(roleId, "scope"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthException.ErrorCode.ROLE_NOT_FOUND);
                });
    }

    @Test
    void addScope_withNullRoleId_throwsNullPointer() {
        assertThatThrownBy(() -> scopeService.addScope(null, "scope"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addScope_withNullScope_throwsNullPointer() {
        RoleId roleId = RoleId.generate();
        assertThatThrownBy(() -> scopeService.addScope(roleId, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ============= removeScope Tests =============

    @Test
    void removeScope_withExistingScope_removesScope() {
        RoleId roleId = RoleId.generate();
        Role existingRole = Role.create("admin", "Admin role", Set.of(), Set.of("scope1", "scope2"));
        Role updatedRole = existingRole.withScopes(Set.of("scope2"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(any(Role.class))).thenReturn(updatedRole);

        Role result = scopeService.removeScope(roleId, "scope1");

        assertThat(result.scopes()).containsExactly("scope2");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void removeScope_withNonExistingScope_returnsUnchangedRole() {
        RoleId roleId = RoleId.generate();
        Role existingRole = Role.create("admin", "Admin role", Set.of(), Set.of("existing.scope"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));

        Role result = scopeService.removeScope(roleId, "nonexistent.scope");

        assertThat(result).isEqualTo(existingRole);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void removeScope_withNonExistentRole_throwsRoleNotFound() {
        RoleId roleId = RoleId.generate();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopeService.removeScope(roleId, "scope"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void removeScope_withNullRoleId_throwsNullPointer() {
        assertThatThrownBy(() -> scopeService.removeScope(null, "scope"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void removeScope_withNullScope_throwsNullPointer() {
        RoleId roleId = RoleId.generate();
        assertThatThrownBy(() -> scopeService.removeScope(roleId, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ============= resolveScopes(RoleId) Tests =============

    @Test
    void resolveScopes_withSingleRole_returnsDirectScopes() {
        RoleId roleId = RoleId.generate();
        Role role = Role.create("admin", "Admin role", Set.of(), Set.of("scope1", "scope2"));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        Set<String> scopes = scopeService.resolveScopes(roleId);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2");
    }

    @Test
    void resolveScopes_withRoleHierarchy_returnsTransitiveScopes() {
        RoleId parentRoleId = RoleId.generate();
        RoleId childRoleId = RoleId.generate();

        Role childRole = Role.create("user", "User role", Set.of(), Set.of("user.read"));
        Role parentRole = Role.create("admin", "Admin role", Set.of(childRoleId), Set.of("admin.full"));

        when(roleRepository.findById(parentRoleId)).thenReturn(Optional.of(parentRole));
        when(roleRepository.findById(childRoleId)).thenReturn(Optional.of(childRole));

        Set<String> scopes = scopeService.resolveScopes(parentRoleId);

        assertThat(scopes).containsExactlyInAnyOrder("admin.full", "user.read");
    }

    @Test
    void resolveScopes_withCircularInclusion_preventsInfiniteLoop() {
        RoleId roleAId = RoleId.generate();
        RoleId roleBId = RoleId.generate();

        // Create circular reference: A includes B, B includes A
        Role roleA = Role.create("roleA", "Role A", Set.of(roleBId), Set.of("scope.a"));
        Role roleB = Role.create("roleB", "Role B", Set.of(roleAId), Set.of("scope.b"));

        when(roleRepository.findById(roleAId)).thenReturn(Optional.of(roleA));
        when(roleRepository.findById(roleBId)).thenReturn(Optional.of(roleB));

        Set<String> scopes = scopeService.resolveScopes(roleAId);

        assertThat(scopes).containsExactlyInAnyOrder("scope.a", "scope.b");
    }

    @Test
    void resolveScopes_withNonExistentRole_throwsRoleNotFound() {
        RoleId roleId = RoleId.generate();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scopeService.resolveScopes(roleId))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void resolveScopes_withNullRoleId_throwsNullPointer() {
        assertThatThrownBy(() -> scopeService.resolveScopes((RoleId) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ============= resolveScopes(Set<RoleId>) Tests =============

    @Test
    void resolveScopes_withMultipleRoles_returnsCombinedScopes() {
        RoleId roleId1 = RoleId.generate();
        RoleId roleId2 = RoleId.generate();

        Role role1 = Role.create("role1", "Role 1", Set.of(), Set.of("scope1", "scope2"));
        Role role2 = Role.create("role2", "Role 2", Set.of(), Set.of("scope2", "scope3"));

        when(roleRepository.findById(roleId1)).thenReturn(Optional.of(role1));
        when(roleRepository.findById(roleId2)).thenReturn(Optional.of(role2));

        Set<String> scopes = scopeService.resolveScopes(Set.of(roleId1, roleId2));

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3");
    }

    @Test
    void resolveScopes_withEmptyRoleSet_returnsEmptyScopes() {
        Set<String> scopes = scopeService.resolveScopes(Set.of());

        assertThat(scopes).isEmpty();
    }

    @Test
    void resolveScopes_withNonExistentRoleInSet_skipsNonExistent() {
        RoleId validRoleId = RoleId.generate();
        RoleId invalidRoleId = RoleId.generate();

        Role validRole = Role.create("valid", "Valid role", Set.of(), Set.of("scope1"));

        when(roleRepository.findById(validRoleId)).thenReturn(Optional.of(validRole));
        when(roleRepository.findById(invalidRoleId)).thenReturn(Optional.empty());

        Set<String> scopes = scopeService.resolveScopes(Set.of(validRoleId, invalidRoleId));

        assertThat(scopes).containsExactly("scope1");
    }

    @Test
    void resolveScopes_withNullRoleIdSet_throwsNullPointer() {
        assertThatThrownBy(() -> scopeService.resolveScopes((Set<RoleId>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveScopes_withDeepHierarchy_resolvesAllLevels() {
        RoleId grandparentId = RoleId.generate();
        RoleId parentId = RoleId.generate();
        RoleId childId = RoleId.generate();

        Role child = Role.create("child", "Child role", Set.of(), Set.of("child.scope"));
        Role parent = Role.create("parent", "Parent role", Set.of(childId), Set.of("parent.scope"));
        Role grandparent = Role.create("grandparent", "Grandparent role", Set.of(parentId), Set.of("grandparent.scope"));

        when(roleRepository.findById(grandparentId)).thenReturn(Optional.of(grandparent));
        when(roleRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(roleRepository.findById(childId)).thenReturn(Optional.of(child));

        Set<String> scopes = scopeService.resolveScopes(grandparentId);

        assertThat(scopes).containsExactlyInAnyOrder("grandparent.scope", "parent.scope", "child.scope");
    }

    @Test
    void resolveScopes_withMissingIncludedRole_handleGracefully() {
        RoleId parentId = RoleId.generate();
        RoleId missingChildId = RoleId.generate();

        Role parent = Role.create("parent", "Parent role", Set.of(missingChildId), Set.of("parent.scope"));

        when(roleRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(roleRepository.findById(missingChildId)).thenReturn(Optional.empty());

        Set<String> scopes = scopeService.resolveScopes(parentId);

        // Should still return parent scopes even if child is missing
        assertThat(scopes).containsExactly("parent.scope");
    }
}
