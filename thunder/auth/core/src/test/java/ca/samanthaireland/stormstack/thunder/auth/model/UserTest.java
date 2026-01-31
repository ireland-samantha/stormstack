/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void create_generatesIdAndTimestamp() {
        User user = User.create("testuser", "hash123", Set.of());

        assertThat(user.id()).isNotNull();
        assertThat(user.username()).isEqualTo("testuser");
        assertThat(user.passwordHash()).isEqualTo("hash123");
        assertThat(user.roleIds()).isEmpty();
        assertThat(user.createdAt()).isNotNull();
        assertThat(user.enabled()).isTrue();
    }

    @Test
    void constructor_rejectsNullId() {
        assertThatThrownBy(() -> new User(null, "user", "hash", Set.of(), Set.of(), Instant.now(), true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_rejectsBlankUsername() {
        assertThatThrownBy(() -> new User(UserId.generate(), "  ", "hash", Set.of(), Set.of(), Instant.now(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void constructor_rejectsBlankPasswordHash() {
        assertThatThrownBy(() -> new User(UserId.generate(), "user", "", Set.of(), Set.of(), Instant.now(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void hasRole_returnsTrueWhenHasRole() {
        RoleId roleId = RoleId.generate();
        User user = User.create("testuser", "hash", Set.of(roleId));

        assertThat(user.hasRole(roleId)).isTrue();
    }

    @Test
    void hasRole_returnsFalseWhenMissingRole() {
        User user = User.create("testuser", "hash", Set.of());

        assertThat(user.hasRole(RoleId.generate())).isFalse();
    }

    @Test
    void withPasswordHash_createsNewInstanceWithUpdatedHash() {
        User original = User.create("testuser", "oldhash", Set.of());
        User updated = original.withPasswordHash("newhash");

        assertThat(updated.passwordHash()).isEqualTo("newhash");
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.username()).isEqualTo(original.username());
        assertThat(original.passwordHash()).isEqualTo("oldhash"); // Original unchanged
    }

    @Test
    void withRoleIds_createsNewInstanceWithUpdatedRoles() {
        RoleId role1 = RoleId.generate();
        RoleId role2 = RoleId.generate();
        User original = User.create("testuser", "hash", Set.of(role1));
        User updated = original.withRoleIds(Set.of(role2));

        assertThat(updated.roleIds()).containsExactly(role2);
        assertThat(original.roleIds()).containsExactly(role1); // Original unchanged
    }

    @Test
    void withEnabled_createsNewInstanceWithUpdatedStatus() {
        User original = User.create("testuser", "hash", Set.of());
        User disabled = original.withEnabled(false);

        assertThat(disabled.enabled()).isFalse();
        assertThat(original.enabled()).isTrue(); // Original unchanged
    }

    @Test
    void roleIds_areImmutable() {
        User user = User.create("testuser", "hash", Set.of(RoleId.generate()));

        assertThatThrownBy(() -> user.roleIds().add(RoleId.generate()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
