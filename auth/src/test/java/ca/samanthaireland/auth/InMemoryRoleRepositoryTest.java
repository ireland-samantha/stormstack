package ca.samanthaireland.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryRoleRepository")
class InMemoryRoleRepositoryTest {

    private InMemoryRoleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoleRepository();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("should generate ID for new role")
        void shouldGenerateIdForNewRole() {
            Role role = new Role(0, "testrole", "Test description", Set.of());

            Role saved = repository.save(role);

            assertThat(saved.id()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should preserve provided data")
        void shouldPreserveProvidedData() {
            Role role = new Role(0, "admin", "Administrator role", Set.of("view_only", "edit"));

            Role saved = repository.save(role);

            assertThat(saved.name()).isEqualTo("admin");
            assertThat(saved.description()).isEqualTo("Administrator role");
            assertThat(saved.includedRoles()).containsExactlyInAnyOrder("view_only", "edit");
        }

        @Test
        @DisplayName("should update existing role")
        void shouldUpdateExistingRole() {
            Role role = repository.save(new Role(0, "myrole", "Original desc", Set.of()));
            Role updated = role.withDescription("Updated desc");

            repository.save(updated);

            Optional<Role> found = repository.findById(role.id());
            assertThat(found).isPresent();
            assertThat(found.get().description()).isEqualTo("Updated desc");
        }

        @Test
        @DisplayName("should handle name change and remove old name mapping")
        void shouldHandleNameChangeAndRemoveOldNameMapping() {
            Role role = repository.save(new Role(0, "oldname", "Description", Set.of()));
            Role renamed = new Role(role.id(), "newname", role.description(), role.includedRoles());

            repository.save(renamed);

            assertThat(repository.findByName("oldname")).isEmpty();
            assertThat(repository.findByName("newname")).isPresent();
        }

        @Test
        @DisplayName("should generate incrementing IDs")
        void shouldGenerateIncrementingIds() {
            Role role1 = repository.save(new Role(0, "role1", "desc", Set.of()));
            Role role2 = repository.save(new Role(0, "role2", "desc", Set.of()));
            Role role3 = repository.save(new Role(0, "role3", "desc", Set.of()));

            assertThat(role2.id()).isGreaterThan(role1.id());
            assertThat(role3.id()).isGreaterThan(role2.id());
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @DisplayName("should return role when found")
        void shouldReturnRoleWhenFound() {
            repository.save(new Role(0, "testrole", "Test", Set.of()));

            Optional<Role> result = repository.findByName("testrole");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("testrole");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<Role> result = repository.findByName("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should be case insensitive")
        void shouldBeCaseInsensitive() {
            repository.save(new Role(0, "AdminRole", "Admin", Set.of()));

            assertThat(repository.findByName("adminrole")).isPresent();
            assertThat(repository.findByName("ADMINROLE")).isPresent();
            assertThat(repository.findByName("AdminRole")).isPresent();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return role when found")
        void shouldReturnRoleWhenFound() {
            Role saved = repository.save(new Role(0, "role", "desc", Set.of()));

            Optional<Role> result = repository.findById(saved.id());

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(saved.id());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<Role> result = repository.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return empty list when no roles")
        void shouldReturnEmptyListWhenNoRoles() {
            List<Role> result = repository.findAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return all roles")
        void shouldReturnAllRoles() {
            repository.save(new Role(0, "role1", "desc", Set.of()));
            repository.save(new Role(0, "role2", "desc", Set.of()));

            List<Role> result = repository.findAll();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Role::name).containsExactlyInAnyOrder("role1", "role2");
        }

        @Test
        @DisplayName("should return copy to prevent external modification")
        void shouldReturnCopyToPreventExternalModification() {
            repository.save(new Role(0, "role1", "desc", Set.of()));

            List<Role> result1 = repository.findAll();
            List<Role> result2 = repository.findAll();

            assertThat(result1).isNotSameAs(result2);
        }
    }

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("should return true and delete role when found")
        void shouldReturnTrueAndDeleteRoleWhenFound() {
            Role saved = repository.save(new Role(0, "role", "desc", Set.of()));

            boolean result = repository.deleteById(saved.id());

            assertThat(result).isTrue();
            assertThat(repository.findById(saved.id())).isEmpty();
            assertThat(repository.findByName("role")).isEmpty();
        }

        @Test
        @DisplayName("should return false when not found")
        void shouldReturnFalseWhenNotFound() {
            boolean result = repository.deleteById(999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByName")
    class ExistsByName {

        @Test
        @DisplayName("should return true when name exists")
        void shouldReturnTrueWhenNameExists() {
            repository.save(new Role(0, "existing", "desc", Set.of()));

            assertThat(repository.existsByName("existing")).isTrue();
        }

        @Test
        @DisplayName("should return false when name does not exist")
        void shouldReturnFalseWhenNameDoesNotExist() {
            assertThat(repository.existsByName("nonexistent")).isFalse();
        }

        @Test
        @DisplayName("should be case insensitive")
        void shouldBeCaseInsensitive() {
            repository.save(new Role(0, "MyRole", "desc", Set.of()));

            assertThat(repository.existsByName("myrole")).isTrue();
            assertThat(repository.existsByName("MYROLE")).isTrue();
        }
    }

    @Nested
    @DisplayName("count")
    class Count {

        @Test
        @DisplayName("should return 0 when empty")
        void shouldReturnZeroWhenEmpty() {
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            repository.save(new Role(0, "role1", "desc", Set.of()));
            repository.save(new Role(0, "role2", "desc", Set.of()));
            repository.save(new Role(0, "role3", "desc", Set.of()));

            assertThat(repository.count()).isEqualTo(3);
        }
    }
}
