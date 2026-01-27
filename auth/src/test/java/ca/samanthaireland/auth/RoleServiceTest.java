package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository);
    }

    @Nested
    @DisplayName("createRole")
    class CreateRole {

        @Test
        @DisplayName("should create role without included roles")
        void shouldCreateRoleWithoutIncludedRoles() {
            when(roleRepository.existsByName("viewer")).thenReturn(false);
            Role savedRole = new Role(1, "viewer", "Viewer role", Set.of());
            when(roleRepository.save(any(Role.class))).thenReturn(savedRole);

            Role result = roleService.createRole("viewer", "Viewer role");

            assertThat(result.name()).isEqualTo("viewer");
            assertThat(result.description()).isEqualTo("Viewer role");
            assertThat(result.includedRoles()).isEmpty();
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("should create role with included roles")
        void shouldCreateRoleWithIncludedRoles() {
            when(roleRepository.existsByName("admin")).thenReturn(false);
            when(roleRepository.existsByName("viewer")).thenReturn(true);
            when(roleRepository.existsByName("editor")).thenReturn(true);
            Role savedRole = new Role(1, "admin", "Admin role", Set.of("viewer", "editor"));
            when(roleRepository.save(any(Role.class))).thenReturn(savedRole);

            Role result = roleService.createRole("admin", "Admin role", Set.of("viewer", "editor"));

            assertThat(result.includedRoles()).containsExactlyInAnyOrder("viewer", "editor");
        }

        @Test
        @DisplayName("should throw when role name already exists")
        void shouldThrowWhenRoleNameAlreadyExists() {
            when(roleRepository.existsByName("admin")).thenReturn(true);

            assertThatThrownBy(() -> roleService.createRole("admin", "Admin role"))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("admin");

            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when included role does not exist")
        void shouldThrowWhenIncludedRoleDoesNotExist() {
            when(roleRepository.existsByName("admin")).thenReturn(false);
            when(roleRepository.existsByName("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> roleService.createRole("admin", "Admin", Set.of("nonexistent")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("nonexistent");

            verify(roleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return role when found")
        void shouldReturnRoleWhenFound() {
            Role role = new Role(1, "admin", "Admin role", Set.of());
            when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

            Optional<Role> result = roleService.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Role> result = roleService.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @DisplayName("should return role when found")
        void shouldReturnRoleWhenFound() {
            Role role = new Role(1, "admin", "Admin role", Set.of());
            when(roleRepository.findByName("admin")).thenReturn(Optional.of(role));

            Optional<Role> result = roleService.findByName("admin");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(roleRepository.findByName("nonexistent")).thenReturn(Optional.empty());

            Optional<Role> result = roleService.findByName("nonexistent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all roles")
        void shouldReturnAllRoles() {
            List<Role> roles = List.of(
                    new Role(1, "admin", "Admin", Set.of()),
                    new Role(2, "viewer", "Viewer", Set.of())
            );
            when(roleRepository.findAll()).thenReturn(roles);

            List<Role> result = roleService.findAll();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Role::name).containsExactlyInAnyOrder("admin", "viewer");
        }
    }

    @Nested
    @DisplayName("updateDescription")
    class UpdateDescription {

        @Test
        @DisplayName("should update description")
        void shouldUpdateDescription() {
            Role existingRole = new Role(1, "admin", "Old description", Set.of());
            Role updatedRole = existingRole.withDescription("New description");
            when(roleRepository.findById(1L)).thenReturn(Optional.of(existingRole));
            when(roleRepository.save(any(Role.class))).thenReturn(updatedRole);

            Role result = roleService.updateDescription(1L, "New description");

            assertThat(result.description()).isEqualTo("New description");
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("should throw when role not found")
        void shouldThrowWhenRoleNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.updateDescription(999L, "New desc"))
                    .isInstanceOf(AuthException.class);

            verify(roleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateIncludedRoles")
    class UpdateIncludedRoles {

        @Test
        @DisplayName("should update included roles")
        void shouldUpdateIncludedRoles() {
            Role existingRole = new Role(1, "admin", "Admin", Set.of());
            Role updatedRole = existingRole.withIncludedRoles(Set.of("viewer"));
            when(roleRepository.findById(1L)).thenReturn(Optional.of(existingRole));
            when(roleRepository.existsByName("viewer")).thenReturn(true);
            when(roleRepository.save(any(Role.class))).thenReturn(updatedRole);

            Role result = roleService.updateIncludedRoles(1L, Set.of("viewer"));

            assertThat(result.includedRoles()).contains("viewer");
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("should throw when role not found")
        void shouldThrowWhenRoleNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.updateIncludedRoles(999L, Set.of("viewer")))
                    .isInstanceOf(AuthException.class);

            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when included role does not exist")
        void shouldThrowWhenIncludedRoleDoesNotExist() {
            Role existingRole = new Role(1, "admin", "Admin", Set.of());
            when(roleRepository.findById(1L)).thenReturn(Optional.of(existingRole));
            when(roleRepository.existsByName("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> roleService.updateIncludedRoles(1L, Set.of("nonexistent")))
                    .isInstanceOf(AuthException.class);

            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when role tries to include itself")
        void shouldThrowWhenRoleTriesToIncludeItself() {
            Role existingRole = new Role(1, "admin", "Admin", Set.of());
            when(roleRepository.findById(1L)).thenReturn(Optional.of(existingRole));

            assertThatThrownBy(() -> roleService.updateIncludedRoles(1L, Set.of("admin")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("cannot include itself");

            verify(roleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteRole")
    class DeleteRole {

        @Test
        @DisplayName("should delete role and return true when found")
        void shouldDeleteRoleAndReturnTrueWhenFound() {
            Role role = new Role(1, "admin", "Admin", Set.of());
            when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

            boolean result = roleService.deleteRole(1L);

            assertThat(result).isTrue();
            verify(roleRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should return false when role not found")
        void shouldReturnFalseWhenRoleNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = roleService.deleteRole(999L);

            assertThat(result).isFalse();
            verify(roleRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("roleExists")
    class RoleExists {

        @Test
        @DisplayName("should return true when role exists")
        void shouldReturnTrueWhenRoleExists() {
            when(roleRepository.existsByName("admin")).thenReturn(true);

            assertThat(roleService.roleExists("admin")).isTrue();
        }

        @Test
        @DisplayName("should return false when role does not exist")
        void shouldReturnFalseWhenRoleDoesNotExist() {
            when(roleRepository.existsByName("nonexistent")).thenReturn(false);

            assertThat(roleService.roleExists("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return count from repository")
        void shouldReturnCountFromRepository() {
            when(roleRepository.count()).thenReturn(5L);

            assertThat(roleService.count()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("roleIncludes")
    class RoleIncludes {

        @Test
        @DisplayName("should return true when role is same as target")
        void shouldReturnTrueWhenRoleIsSameAsTarget() {
            assertThat(roleService.roleIncludes("admin", "admin")).isTrue();
        }

        @Test
        @DisplayName("should return true for direct inclusion")
        void shouldReturnTrueForDirectInclusion() {
            Role admin = new Role(1, "admin", "Admin", Set.of("viewer"));
            when(roleRepository.findByName("admin")).thenReturn(Optional.of(admin));

            assertThat(roleService.roleIncludes("admin", "viewer")).isTrue();
        }

        @Test
        @DisplayName("should return true for transitive inclusion")
        void shouldReturnTrueForTransitiveInclusion() {
            Role admin = new Role(1, "admin", "Admin", Set.of("editor"));
            Role editor = new Role(2, "editor", "Editor", Set.of("viewer"));
            when(roleRepository.findByName("admin")).thenReturn(Optional.of(admin));
            when(roleRepository.findByName("editor")).thenReturn(Optional.of(editor));

            assertThat(roleService.roleIncludes("admin", "viewer")).isTrue();
        }

        @Test
        @DisplayName("should return false when role does not include target")
        void shouldReturnFalseWhenRoleDoesNotIncludeTarget() {
            Role viewer = new Role(1, "viewer", "Viewer", Set.of());
            when(roleRepository.findByName("viewer")).thenReturn(Optional.of(viewer));

            assertThat(roleService.roleIncludes("viewer", "admin")).isFalse();
        }

        @Test
        @DisplayName("should return false when role not found")
        void shouldReturnFalseWhenRoleNotFound() {
            when(roleRepository.findByName("nonexistent")).thenReturn(Optional.empty());

            assertThat(roleService.roleIncludes("nonexistent", "admin")).isFalse();
        }
    }
}
