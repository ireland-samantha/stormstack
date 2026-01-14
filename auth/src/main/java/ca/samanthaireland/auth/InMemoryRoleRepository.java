package ca.samanthaireland.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of RoleRepository.
 *
 * <p>Thread-safe using ConcurrentHashMap.
 */
public class InMemoryRoleRepository implements RoleRepository {

    private final Map<Long, Role> rolesById = new ConcurrentHashMap<>();
    private final Map<String, Role> rolesByName = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Role> findByName(String name) {
        return Optional.ofNullable(rolesByName.get(name.toLowerCase()));
    }

    @Override
    public Optional<Role> findById(long id) {
        return Optional.ofNullable(rolesById.get(id));
    }

    @Override
    public List<Role> findAll() {
        return List.copyOf(rolesById.values());
    }

    @Override
    public Role save(Role role) {
        Role toSave;
        if (role.id() == 0) {
            toSave = new Role(
                    idGenerator.getAndIncrement(),
                    role.name(),
                    role.description(),
                    role.includedRoles()
            );
        } else {
            // Remove old name mapping if name changed
            Role existing = rolesById.get(role.id());
            if (existing != null && !existing.name().equalsIgnoreCase(role.name())) {
                rolesByName.remove(existing.name().toLowerCase());
            }
            toSave = role;
        }

        rolesById.put(toSave.id(), toSave);
        rolesByName.put(toSave.name().toLowerCase(), toSave);
        return toSave;
    }

    @Override
    public boolean deleteById(long id) {
        Role removed = rolesById.remove(id);
        if (removed != null) {
            rolesByName.remove(removed.name().toLowerCase());
            return true;
        }
        return false;
    }

    @Override
    public boolean existsByName(String name) {
        return rolesByName.containsKey(name.toLowerCase());
    }

    @Override
    public long count() {
        return rolesById.size();
    }
}
