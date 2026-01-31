/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.lightning.auth.service;

import ca.samanthaireland.lightning.auth.exception.AuthException;
import ca.samanthaireland.lightning.auth.model.Role;
import ca.samanthaireland.lightning.auth.model.RoleId;
import ca.samanthaireland.lightning.auth.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleRepository);
    }

    @Test
    void createRole_savesNewRole() {
        when(roleRepository.existsByName("admin")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role role = roleService.createRole("admin", "Administrator role");

        assertThat(role.name()).isEqualTo("admin");
        assertThat(role.description()).isEqualTo("Administrator role");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRole_throwsWhenNameTaken() {
        when(roleRepository.existsByName("admin")).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole("admin", "description"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void createRole_withIncludedRoles_validatesExistence() {
        RoleId includedRoleId = RoleId.generate();
        Role includedRole = Role.create("user", "User role");

        when(roleRepository.existsByName("admin")).thenReturn(false);
        when(roleRepository.findAllById(Set.of(includedRoleId))).thenReturn(List.of(includedRole));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role role = roleService.createRole("admin", "Admin", Set.of(includedRoleId));

        assertThat(role.includedRoleIds()).contains(includedRoleId);
    }

    @Test
    void roleIncludes_returnsTrueForDirectMatch() {
        RoleId roleId = RoleId.generate();

        assertThat(roleService.roleIncludes(roleId, roleId)).isTrue();
    }

    @Test
    void roleIncludes_returnsTrueForDirectInclusion() {
        RoleId adminId = RoleId.generate();
        RoleId userId = RoleId.generate();
        Role admin = new Role(adminId, "admin", "Admin", Set.of(userId));

        when(roleRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThat(roleService.roleIncludes(adminId, userId)).isTrue();
    }

    @Test
    void roleIncludes_returnsTrueForTransitiveInclusion() {
        RoleId adminId = RoleId.generate();
        RoleId managerId = RoleId.generate();
        RoleId userId = RoleId.generate();

        Role admin = new Role(adminId, "admin", "Admin", Set.of(managerId));
        Role manager = new Role(managerId, "manager", "Manager", Set.of(userId));

        when(roleRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(roleRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThat(roleService.roleIncludes(adminId, userId)).isTrue();
    }

    @Test
    void roleIncludes_returnsFalseForUnrelatedRoles() {
        RoleId adminId = RoleId.generate();
        RoleId unrelatedId = RoleId.generate();
        Role admin = new Role(adminId, "admin", "Admin", Set.of());

        when(roleRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThat(roleService.roleIncludes(adminId, unrelatedId)).isFalse();
    }

    @Test
    void deleteRole_throwsWhenNotFound() {
        RoleId roleId = RoleId.generate();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole(roleId))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateIncludedRoles_preventsSelfInclusion() {
        RoleId roleId = RoleId.generate();
        Role role = new Role(roleId, "admin", "Admin", Set.of());

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        // Mock findAllById to pass validation - the role includes itself
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(role));

        assertThatThrownBy(() -> roleService.updateIncludedRoles(roleId, Set.of(roleId)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("cannot include itself");
    }
}
