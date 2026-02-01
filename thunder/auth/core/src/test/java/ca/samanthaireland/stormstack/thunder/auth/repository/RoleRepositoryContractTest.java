/*
 * Copyright (c) 2026 Samantha Ireland
 */

package ca.samanthaireland.stormstack.thunder.auth.repository;

import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for {@link RoleRepository} interface.
 *
 * <p>These tests verify the expected behavior of any RoleRepository implementation.
 * They use an in-memory implementation to test the interface contract without
 * requiring external dependencies like MongoDB.
 */
@DisplayName("RoleRepository Contract Tests")
class RoleRepositoryContractTest {

    private RoleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoleRepository();
    }

    /**
     * Helper to create a test role with the given name and scopes.
     */
    private Role createRole(String name, Set<String> scopes) {
        return Role.create(name, name + " role", Set.of(), scopes);
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return role when exists")
        void shouldReturnRoleWhenExists() {
            Role role = createRole("admin", Set.of("admin.full"));
            repository.save(role);

            Optional<Role> found = repository.findById(role.id());

            assertThat(found).isPresent();
            assertThat(found.get().name()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should return empty when not exists")
        void shouldReturnEmptyWhenNotExists() {
            Optional<Role> found = repository.findById(RoleId.generate());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByName()")
    class FindByName {

        @Test
        @DisplayName("should return role with exact match")
        void shouldReturnRoleWithExactMatch() {
            Role role = createRole("admin", Set.of("admin.full"));
            repository.save(role);

            Optional<Role> found = repository.findByName("admin");

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(role.id());
        }

        @Test
        @DisplayName("should return role case-insensitively")
        void shouldReturnRoleCaseInsensitively() {
            Role role = Role.create("Admin", "Admin role", Set.of(), Set.of("admin.full"));
            repository.save(role);

            Optional<Role> found = repository.findByName("admin");

            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("should return empty for non-existent name")
        void shouldReturnEmptyForNonExistent() {
            Optional<Role> found = repository.findByName("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllById()")
    class FindAllById {

        @Test
        @DisplayName("should return all existing roles")
        void shouldReturnAllExistingRoles() {
            Role role1 = createRole("admin", Set.of("admin.full"));
            Role role2 = createRole("user", Set.of("user.read"));
            repository.save(role1);
            repository.save(role2);

            List<Role> found = repository.findAllById(Set.of(role1.id(), role2.id()));

            assertThat(found).hasSize(2);
            assertThat(found).extracting(Role::name)
                    .containsExactlyInAnyOrder("admin", "user");
        }

        @Test
        @DisplayName("should return only existing roles when some don't exist")
        void shouldReturnOnlyExistingRoles() {
            Role role = createRole("admin", Set.of("admin.full"));
            repository.save(role);

            List<Role> found = repository.findAllById(Set.of(role.id(), RoleId.generate()));

            assertThat(found).hasSize(1);
            assertThat(found.get(0).name()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyForEmptyInput() {
            List<Role> found = repository.findAllById(Set.of());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all roles")
        void shouldReturnAllRoles() {
            repository.save(createRole("admin", Set.of("admin.full")));
            repository.save(createRole("user", Set.of("user.read")));
            repository.save(createRole("moderator", Set.of("mod.ban")));

            List<Role> roles = repository.findAll();

            assertThat(roles).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no roles")
        void shouldReturnEmptyWhenNoRoles() {
            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should persist new role")
        void shouldPersistNewRole() {
            Role role = createRole("newrole", Set.of("scope1"));

            Role saved = repository.save(role);

            assertThat(saved).isNotNull();
            assertThat(repository.findById(role.id())).isPresent();
        }

        @Test
        @DisplayName("should update existing role")
        void shouldUpdateExistingRole() {
            Role role = createRole("admin", Set.of("admin.read"));
            repository.save(role);

            Role updated = role.withScopes(Set.of("admin.full", "admin.write"));
            repository.save(updated);

            Optional<Role> found = repository.findById(role.id());
            assertThat(found).isPresent();
            assertThat(found.get().scopes()).containsExactlyInAnyOrder("admin.full", "admin.write");
        }
    }

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("should remove existing role")
        void shouldRemoveExistingRole() {
            Role role = createRole("admin", Set.of("admin.full"));
            repository.save(role);

            boolean deleted = repository.deleteById(role.id());

            assertThat(deleted).isTrue();
            assertThat(repository.findById(role.id())).isEmpty();
        }

        @Test
        @DisplayName("should return false for non-existent role")
        void shouldReturnFalseForNonExistent() {
            boolean deleted = repository.deleteById(RoleId.generate());

            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("existsByName()")
    class ExistsByName {

        @Test
        @DisplayName("should return true for existing name")
        void shouldReturnTrueForExisting() {
            repository.save(createRole("admin", Set.of()));

            assertThat(repository.existsByName("admin")).isTrue();
        }

        @Test
        @DisplayName("should return true case-insensitively")
        void shouldReturnTrueCaseInsensitively() {
            repository.save(Role.create("Admin", "Admin role", Set.of(), Set.of()));

            assertThat(repository.existsByName("admin")).isTrue();
            assertThat(repository.existsByName("ADMIN")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-existent name")
        void shouldReturnFalseForNonExistent() {
            assertThat(repository.existsByName("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("count()")
    class Count {

        @Test
        @DisplayName("should return zero for empty repository")
        void shouldReturnZeroForEmpty() {
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            repository.save(createRole("role1", Set.of()));
            repository.save(createRole("role2", Set.of()));
            repository.save(createRole("role3", Set.of()));

            assertThat(repository.count()).isEqualTo(3);
        }
    }

    /**
     * In-memory implementation for contract testing.
     */
    private static class InMemoryRoleRepository implements RoleRepository {
        private final Map<RoleId, Role> roles = new HashMap<>();

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public Optional<Role> findByName(String name) {
            return roles.values().stream()
                    .filter(r -> r.name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public List<Role> findAllById(Set<RoleId> ids) {
            return ids.stream()
                    .map(roles::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public List<Role> findAll() {
            return new ArrayList<>(roles.values());
        }

        @Override
        public Role save(Role role) {
            roles.put(role.id(), role);
            return role;
        }

        @Override
        public boolean deleteById(RoleId id) {
            return roles.remove(id) != null;
        }

        @Override
        public boolean existsByName(String name) {
            return roles.values().stream()
                    .anyMatch(r -> r.name().equalsIgnoreCase(name));
        }

        @Override
        public long count() {
            return roles.size();
        }
    }
}
