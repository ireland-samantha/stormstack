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

import ca.samanthaireland.stormstack.thunder.auth.model.Role;
import ca.samanthaireland.stormstack.thunder.auth.model.RoleId;
import ca.samanthaireland.stormstack.thunder.auth.repository.RoleRepository;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * MongoDB implementation of RoleRepository.
 */
@ApplicationScoped
@Startup
public class MongoRoleRepository implements RoleRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoRoleRepository.class);
    private static final String DATABASE = "lightning-auth";
    private static final String COLLECTION = "roles";

    @Inject
    MongoClient mongoClient;

    @PostConstruct
    void init() {
        // Create unique index on name (case-insensitive)
        collection().createIndex(
                Indexes.ascending("name"),
                new IndexOptions().unique(true)
        );
        log.info("MongoRoleRepository initialized with unique name index");
    }

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(DATABASE).getCollection(COLLECTION);
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        Document doc = collection().find(Filters.eq("_id", id.toString())).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public Optional<Role> findByName(String name) {
        Document doc = collection().find(
                Filters.regex("name", "^" + name + "$", "i")
        ).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public List<Role> findAllById(Set<RoleId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> idStrings = ids.stream().map(RoleId::toString).collect(Collectors.toList());
        return StreamSupport.stream(
                collection().find(Filters.in("_id", idStrings)).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<Role> findAll() {
        return StreamSupport.stream(collection().find().spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public Role save(Role role) {
        Document doc = toDocument(role);
        collection().replaceOne(
                Filters.eq("_id", role.id().toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
        return role;
    }

    @Override
    public boolean deleteById(RoleId id) {
        return collection().deleteOne(Filters.eq("_id", id.toString())).getDeletedCount() > 0;
    }

    @Override
    public boolean existsByName(String name) {
        return collection().countDocuments(
                Filters.regex("name", "^" + name + "$", "i")
        ) > 0;
    }

    @Override
    public long count() {
        return collection().countDocuments();
    }

    private Document toDocument(Role role) {
        return new Document()
                .append("_id", role.id().toString())
                .append("name", role.name())
                .append("description", role.description())
                .append("includedRoleIds", role.includedRoleIds().stream()
                        .map(RoleId::toString)
                        .collect(Collectors.toList()))
                .append("scopes", new ArrayList<>(role.scopes()));
    }

    private Role fromDocument(Document doc) {
        @SuppressWarnings("unchecked")
        List<String> includedIdStrings = doc.get("includedRoleIds", List.class);
        Set<RoleId> includedRoleIds = includedIdStrings != null
                ? includedIdStrings.stream().map(RoleId::fromString).collect(Collectors.toSet())
                : Set.of();

        @SuppressWarnings("unchecked")
        List<String> scopesList = doc.get("scopes", List.class);
        Set<String> scopes = scopesList != null
                ? new HashSet<>(scopesList)
                : Set.of();

        return new Role(
                RoleId.fromString(doc.getString("_id")),
                doc.getString("name"),
                doc.getString("description"),
                includedRoleIds,
                scopes
        );
    }
}
