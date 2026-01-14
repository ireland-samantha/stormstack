package ca.samanthaireland.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link UserRepository}.
 *
 * <p>This implementation stores users in a concurrent map and is suitable
 * for development and testing. For production, consider using a database
 * or external identity provider.
 *
 * <p>Thread-safe for concurrent access.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * Create an empty repository.
     */
    public InMemoryUserRepository() {
    }

    /**
     * Create a repository with an initial admin user.
     *
     * @param adminPasswordHash the BCrypt hash of the admin password
     * @return a repository with the admin user
     */
    public static InMemoryUserRepository withAdminUser(String adminPasswordHash) {
        InMemoryUserRepository repo = new InMemoryUserRepository();
        User admin = new User(
                repo.idGenerator.getAndIncrement(),
                "admin",
                adminPasswordHash,
                Set.of("admin"),
                Instant.now(),
                true
        );
        repo.usersById.put(admin.id(), admin);
        repo.usersByUsername.put(admin.username(), admin);
        return repo;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(usersById.values());
    }

    @Override
    public User save(User user) {
        User toSave;
        if (user.id() <= 0) {
            // New user - generate ID
            toSave = new User(
                    idGenerator.getAndIncrement(),
                    user.username(),
                    user.passwordHash(),
                    user.roles(),
                    user.createdAt() != null ? user.createdAt() : Instant.now(),
                    user.enabled()
            );
        } else {
            // Existing user - check for username conflicts
            User existing = usersById.get(user.id());
            if (existing != null && !existing.username().equals(user.username())) {
                // Username changed - remove old mapping
                usersByUsername.remove(existing.username());
            }
            toSave = user;
        }

        usersById.put(toSave.id(), toSave);
        usersByUsername.put(toSave.username(), toSave);
        return toSave;
    }

    @Override
    public boolean deleteById(long id) {
        User removed = usersById.remove(id);
        if (removed != null) {
            usersByUsername.remove(removed.username());
            return true;
        }
        return false;
    }

    @Override
    public boolean existsByUsername(String username) {
        return usersByUsername.containsKey(username);
    }

    @Override
    public long count() {
        return usersById.size();
    }
}
