package ca.samanthaireland.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Role")
class RoleTest {

    @Nested
    @DisplayName("Record construction")
    class Construction {

        @Test
        @DisplayName("should create role with all fields")
        void shouldCreateRoleWithAllFields() {
            Role role = new Role(1, "admin", "Full access", Set.of("command_manager", "view_only"));

            assertThat(role.id()).isEqualTo(1);
            assertThat(role.name()).isEqualTo("admin");
            assertThat(role.description()).isEqualTo("Full access");
            assertThat(role.includedRoles()).containsExactlyInAnyOrder("command_manager", "view_only");
        }

        @Test
        @DisplayName("should create role with empty included roles")
        void shouldCreateRoleWithEmptyIncludedRoles() {
            Role role = new Role(1, "view_only", "Read-only access", Set.of());

            assertThat(role.includedRoles()).isEmpty();
        }

        @Test
        @DisplayName("convenience constructor should set empty included roles")
        void convenienceConstructorShouldSetEmptyIncludedRoles() {
            Role role = new Role(1, "view_only", "Read-only access");

            assertThat(role.includedRoles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("includes method")
    class Includes {

        @Test
        @DisplayName("should include self")
        void shouldIncludeSelf() {
            Role role = new Role(1, "admin", "Admin", Set.of());

            assertThat(role.includes("admin")).isTrue();
        }

        @Test
        @DisplayName("should include direct included role")
        void shouldIncludeDirectIncludedRole() {
            Role role = new Role(1, "admin", "Admin", Set.of("command_manager"));

            assertThat(role.includes("command_manager")).isTrue();
        }

        @Test
        @DisplayName("should NOT include non-included role")
        void shouldNotIncludeNonIncludedRole() {
            Role role = new Role(1, "command_manager", "Command manager", Set.of("view_only"));

            assertThat(role.includes("admin")).isFalse();
        }

        @Test
        @DisplayName("should include all directly included roles")
        void shouldIncludeAllDirectlyIncludedRoles() {
            Role role = new Role(1, "admin", "Admin", Set.of("command_manager", "view_only"));

            assertThat(role.includes("command_manager")).isTrue();
            assertThat(role.includes("view_only")).isTrue();
        }

        @Test
        @DisplayName("role with no includes should only include self")
        void roleWithNoIncludesShouldOnlyIncludeSelf() {
            Role role = new Role(1, "view_only", "View only");

            assertThat(role.includes("view_only")).isTrue();
            assertThat(role.includes("admin")).isFalse();
            assertThat(role.includes("command_manager")).isFalse();
        }
    }

    @Nested
    @DisplayName("wither methods")
    class WitherMethods {

        @Test
        @DisplayName("withDescription should return new role with updated description")
        void withDescriptionShouldReturnUpdatedRole() {
            Role original = new Role(1, "admin", "Original description", Set.of("view_only"));

            Role updated = original.withDescription("New description");

            assertThat(updated.id()).isEqualTo(1);
            assertThat(updated.name()).isEqualTo("admin");
            assertThat(updated.description()).isEqualTo("New description");
            assertThat(updated.includedRoles()).containsExactly("view_only");
            // Original unchanged
            assertThat(original.description()).isEqualTo("Original description");
        }

        @Test
        @DisplayName("withIncludedRoles should return new role with updated included roles")
        void withIncludedRolesShouldReturnUpdatedRole() {
            Role original = new Role(1, "admin", "Admin", Set.of("view_only"));

            Role updated = original.withIncludedRoles(Set.of("command_manager", "view_only"));

            assertThat(updated.id()).isEqualTo(1);
            assertThat(updated.name()).isEqualTo("admin");
            assertThat(updated.description()).isEqualTo("Admin");
            assertThat(updated.includedRoles()).containsExactlyInAnyOrder("command_manager", "view_only");
            // Original unchanged
            assertThat(original.includedRoles()).containsExactly("view_only");
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("roles with same values should be equal")
        void rolesWithSameValuesShouldBeEqual() {
            Role role1 = new Role(1, "admin", "Admin", Set.of("view_only"));
            Role role2 = new Role(1, "admin", "Admin", Set.of("view_only"));

            assertThat(role1).isEqualTo(role2);
            assertThat(role1.hashCode()).isEqualTo(role2.hashCode());
        }

        @Test
        @DisplayName("roles with different IDs should not be equal")
        void rolesWithDifferentIdsShouldNotBeEqual() {
            Role role1 = new Role(1, "admin", "Admin", Set.of());
            Role role2 = new Role(2, "admin", "Admin", Set.of());

            assertThat(role1).isNotEqualTo(role2);
        }
    }
}
