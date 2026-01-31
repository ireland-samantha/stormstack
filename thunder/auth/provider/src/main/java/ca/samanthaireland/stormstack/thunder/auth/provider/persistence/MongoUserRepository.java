/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.auth.provider.persistence;

import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.model.User;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.UserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * MongoDB implementation of UserRepository.
 */
@ApplicationScoped
@Startup
public class MongoUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoUserRepository.class);
    private static final String DATABASE = "lightning-auth";
    private static final String COLLECTION = "users";

    @Inject
    MongoClient mongoClient;

    @PostConstruct
    void init() {
        // Create unique index on username
        collection().createIndex(
                Indexes.ascending("username"),
                new IndexOptions().unique(true)
        );
        log.info("MongoUserRepository initialized with unique username index");
    }

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(DATABASE).getCollection(COLLECTION);
    }

    @Override
    public Optional<User> findById(UserId id) {
        Document doc = collection().find(Filters.eq("_id", id.toString())).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        Document doc = collection().find(
                Filters.regex("username", "^" + username + "$", "i")
        ).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public List<User> findAll() {
        return StreamSupport.stream(collection().find().spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public User save(User user) {
        Document doc = toDocument(user);
        collection().replaceOne(
                Filters.eq("_id", user.id().toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
        return user;
    }

    @Override
    public boolean deleteById(UserId id) {
        return collection().deleteOne(Filters.eq("_id", id.toString())).getDeletedCount() > 0;
    }

    @Override
    public boolean existsByUsername(String username) {
        return collection().countDocuments(
                Filters.regex("username", "^" + username + "$", "i")
        ) > 0;
    }

    @Override
    public long count() {
        return collection().countDocuments();
    }

    private Document toDocument(User user) {
        return new Document()
                .append("_id", user.id().toString())
                .append("username", user.username())
                .append("passwordHash", user.passwordHash())
                .append("roleIds", user.roleIds().stream()
                        .map(RoleId::toString)
                        .collect(Collectors.toList()))
                .append("scopes", new ArrayList<>(user.scopes()))
                .append("createdAt", user.createdAt().toString())
                .append("enabled", user.enabled());
    }

    private User fromDocument(Document doc) {
        @SuppressWarnings("unchecked")
        List<String> roleIdStrings = doc.get("roleIds", List.class);
        Set<RoleId> roleIds = roleIdStrings != null
                ? roleIdStrings.stream().map(RoleId::fromString).collect(Collectors.toSet())
                : Set.of();

        @SuppressWarnings("unchecked")
        List<String> scopeStrings = doc.get("scopes", List.class);
        Set<String> scopes = scopeStrings != null
                ? new HashSet<>(scopeStrings)
                : Set.of();

        return new User(
                UserId.fromString(doc.getString("_id")),
                doc.getString("username"),
                doc.getString("passwordHash"),
                roleIds,
                scopes,
                Instant.parse(doc.getString("createdAt")),
                doc.getBoolean("enabled", true)
        );
    }
}
