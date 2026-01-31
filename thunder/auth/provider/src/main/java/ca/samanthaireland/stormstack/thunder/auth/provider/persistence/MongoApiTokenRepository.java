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

import ca.samanthaireland.stormstack.thunder.auth.model.ApiToken;
import ca.samanthaireland.stormstack.thunder.auth.model.ApiTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.ApiTokenRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * MongoDB implementation of ApiTokenRepository.
 */
@ApplicationScoped
@Startup
public class MongoApiTokenRepository implements ApiTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoApiTokenRepository.class);
    private static final String DATABASE = "lightning-auth";
    private static final String COLLECTION = "api_tokens";

    @Inject
    MongoClient mongoClient;

    @PostConstruct
    void init() {
        // Create index on userId for efficient user token lookups
        collection().createIndex(Indexes.ascending("userId"));
        log.info("MongoApiTokenRepository initialized with userId index");
    }

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(DATABASE).getCollection(COLLECTION);
    }

    @Override
    public Optional<ApiToken> findById(ApiTokenId id) {
        Document doc = collection().find(Filters.eq("_id", id.toString())).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public List<ApiToken> findByUserId(UserId userId) {
        return StreamSupport.stream(
                collection().find(Filters.eq("userId", userId.toString())).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<ApiToken> findAll() {
        return StreamSupport.stream(collection().find().spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<ApiToken> findAllActive() {
        // Active = not revoked AND (no expiry OR expiry in future)
        Instant now = Instant.now();
        Bson notRevoked = Filters.eq("revokedAt", null);
        Bson noExpiry = Filters.eq("expiresAt", null);
        Bson notExpired = Filters.gt("expiresAt", now.toString());

        Bson activeFilter = Filters.and(
                notRevoked,
                Filters.or(noExpiry, notExpired)
        );

        return StreamSupport.stream(
                collection().find(activeFilter).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public ApiToken save(ApiToken token) {
        Document doc = toDocument(token);
        collection().replaceOne(
                Filters.eq("_id", token.id().toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
        return token;
    }

    @Override
    public boolean deleteById(ApiTokenId id) {
        return collection().deleteOne(Filters.eq("_id", id.toString())).getDeletedCount() > 0;
    }

    @Override
    public long count() {
        return collection().countDocuments();
    }

    @Override
    public long countActiveByUserId(UserId userId) {
        Instant now = Instant.now();
        Bson userFilter = Filters.eq("userId", userId.toString());
        Bson notRevoked = Filters.eq("revokedAt", null);
        Bson noExpiry = Filters.eq("expiresAt", null);
        Bson notExpired = Filters.gt("expiresAt", now.toString());

        Bson activeFilter = Filters.and(
                userFilter,
                notRevoked,
                Filters.or(noExpiry, notExpired)
        );

        return collection().countDocuments(activeFilter);
    }

    private Document toDocument(ApiToken token) {
        Document doc = new Document()
                .append("_id", token.id().toString())
                .append("userId", token.userId().toString())
                .append("name", token.name())
                .append("tokenHash", token.tokenHash())
                .append("scopes", new ArrayList<>(token.scopes()))
                .append("createdAt", token.createdAt().toString())
                .append("expiresAt", token.expiresAt() != null ? token.expiresAt().toString() : null)
                .append("revokedAt", token.revokedAt() != null ? token.revokedAt().toString() : null)
                .append("lastUsedAt", token.lastUsedAt() != null ? token.lastUsedAt().toString() : null)
                .append("lastUsedIp", token.lastUsedIp());
        return doc;
    }

    private ApiToken fromDocument(Document doc) {
        @SuppressWarnings("unchecked")
        List<String> scopesList = doc.get("scopes", List.class);
        Set<String> scopes = scopesList != null ? new HashSet<>(scopesList) : Set.of();

        return new ApiToken(
                ApiTokenId.fromString(doc.getString("_id")),
                UserId.fromString(doc.getString("userId")),
                doc.getString("name"),
                doc.getString("tokenHash"),
                scopes,
                Instant.parse(doc.getString("createdAt")),
                parseInstant(doc.getString("expiresAt")),
                parseInstant(doc.getString("revokedAt")),
                parseInstant(doc.getString("lastUsedAt")),
                doc.getString("lastUsedIp")
        );
    }

    private Instant parseInstant(String value) {
        return value != null ? Instant.parse(value) : null;
    }
}
