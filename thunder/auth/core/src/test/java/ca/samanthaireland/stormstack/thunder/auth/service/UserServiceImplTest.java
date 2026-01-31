/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.exception.AuthException;
import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordService passwordService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, roleService, passwordService);
    }

    @Test
    void createUser_hashesPasswordAndSavesUser() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordService.hashPassword("password123")).thenReturn("hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.createUser("testuser", "password123", Set.of());

        assertThat(user.username()).isEqualTo("testuser");
        assertThat(user.passwordHash()).isEqualTo("hashedpassword");
        verify(passwordService).hashPassword("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_validatesRolesExist() {
        RoleId roleId = RoleId.generate();
        Role role = Role.create("user", "User role");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(roleService.findAllById(Set.of(roleId))).thenReturn(List.of(role));
        when(passwordService.hashPassword(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.createUser("testuser", "password", Set.of(roleId));

        assertThat(user.roleIds()).contains(roleId);
    }

    @Test
    void createUser_throwsWhenUsernameTaken() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("testuser", "password", Set.of()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void createUser_throwsWhenRoleDoesNotExist() {
        RoleId roleId = RoleId.generate();

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(roleService.findAllById(Set.of(roleId))).thenReturn(List.of()); // Role not found

        assertThatThrownBy(() -> userService.createUser("testuser", "password", Set.of(roleId)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("roles do not exist");
    }

    @Test
    void updatePassword_hashesNewPassword() {
        UserId userId = UserId.generate();
        User existingUser = User.create("testuser", "oldhash", Set.of());

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordService.hashPassword("newpassword")).thenReturn("newhash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updatePassword(userId, "newpassword");

        assertThat(updated.passwordHash()).isEqualTo("newhash");
    }

    @Test
    void updatePassword_throwsWhenUserNotFound() {
        UserId userId = UserId.generate();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updatePassword(userId, "newpassword"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void setEnabled_updatesEnabledStatus() {
        UserId userId = UserId.generate();
        User existingUser = User.create("testuser", "hash", Set.of());

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User disabled = userService.setEnabled(userId, false);

        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void addRole_addsRoleToUser() {
        UserId userId = UserId.generate();
        RoleId roleId = RoleId.generate();
        User existingUser = User.create("testuser", "hash", Set.of());
        Role role = Role.create("newrole", "New role");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(roleService.findById(roleId)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.addRole(userId, roleId);

        assertThat(updated.roleIds()).contains(roleId);
    }

    @Test
    void removeRole_removesRoleFromUser() {
        UserId userId = UserId.generate();
        RoleId roleId = RoleId.generate();
        User existingUser = new User(userId, "testuser", "hash", Set.of(roleId), Set.of(),
                java.time.Instant.now(), true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.removeRole(userId, roleId);

        assertThat(updated.roleIds()).doesNotContain(roleId);
    }

    @Test
    void deleteUser_throwsWhenUserNotFound() {
        UserId userId = UserId.generate();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void isUsernameAvailable_returnsTrueWhenNotExists() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        assertThat(userService.isUsernameAvailable("newuser")).isTrue();
    }

    @Test
    void isUsernameAvailable_returnsFalseWhenExists() {
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThat(userService.isUsernameAvailable("existinguser")).isFalse();
    }
}
