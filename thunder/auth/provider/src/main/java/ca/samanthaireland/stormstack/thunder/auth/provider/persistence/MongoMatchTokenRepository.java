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

import ca.samanthaireland.stormstack.thunder.auth.model.MatchToken;
import ca.samanthaireland.stormstack.thunder.auth.model.MatchTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.MatchTokenRepository;
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
 * MongoDB implementation of MatchTokenRepository.
 */
@ApplicationScoped
@Startup
public class MongoMatchTokenRepository implements MatchTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoMatchTokenRepository.class);
    private static final String DATABASE = "lightning-auth";
    private static final String COLLECTION = "match_tokens";

    @Inject
    MongoClient mongoClient;

    @PostConstruct
    void init() {
        // Create indexes for efficient lookups
        collection().createIndex(Indexes.ascending("matchId"));
        collection().createIndex(Indexes.ascending("playerId"));
        collection().createIndex(Indexes.compoundIndex(
                Indexes.ascending("matchId"),
                Indexes.ascending("playerId")
        ));
        log.info("MongoMatchTokenRepository initialized with indexes");
    }

    private MongoCollection<Document> collection() {
        return mongoClient.getDatabase(DATABASE).getCollection(COLLECTION);
    }

    @Override
    public Optional<MatchToken> findById(MatchTokenId id) {
        Document doc = collection().find(Filters.eq("_id", id.toString())).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public List<MatchToken> findByMatchId(String matchId) {
        return StreamSupport.stream(
                collection().find(Filters.eq("matchId", matchId)).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<MatchToken> findByPlayerId(String playerId) {
        return StreamSupport.stream(
                collection().find(Filters.eq("playerId", playerId)).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MatchToken> findActiveByMatchAndPlayer(String matchId, String playerId) {
        Bson filter = Filters.and(
                Filters.eq("matchId", matchId),
                Filters.eq("playerId", playerId),
                Filters.eq("revokedAt", null),
                Filters.gt("expiresAt", Instant.now().toString())
        );

        Document doc = collection().find(filter).first();
        return Optional.ofNullable(doc).map(this::fromDocument);
    }

    @Override
    public List<MatchToken> findActiveByMatchId(String matchId) {
        Instant now = Instant.now();
        Bson filter = Filters.and(
                Filters.eq("matchId", matchId),
                Filters.eq("revokedAt", null),
                Filters.gt("expiresAt", now.toString())
        );

        return StreamSupport.stream(
                collection().find(filter).spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public List<MatchToken> findAll() {
        return StreamSupport.stream(collection().find().spliterator(), false)
                .map(this::fromDocument)
                .collect(Collectors.toList());
    }

    @Override
    public MatchToken save(MatchToken token) {
        Document doc = toDocument(token);
        collection().replaceOne(
                Filters.eq("_id", token.id().toString()),
                doc,
                new ReplaceOptions().upsert(true)
        );
        return token;
    }

    @Override
    public boolean deleteById(MatchTokenId id) {
        return collection().deleteOne(Filters.eq("_id", id.toString())).getDeletedCount() > 0;
    }

    @Override
    public long deleteByMatchId(String matchId) {
        return collection().deleteMany(Filters.eq("matchId", matchId)).getDeletedCount();
    }

    @Override
    public long count() {
        return collection().countDocuments();
    }

    @Override
    public long countActiveByMatchId(String matchId) {
        Instant now = Instant.now();
        Bson filter = Filters.and(
                Filters.eq("matchId", matchId),
                Filters.eq("revokedAt", null),
                Filters.gt("expiresAt", now.toString())
        );
        return collection().countDocuments(filter);
    }

    private Document toDocument(MatchToken token) {
        Document doc = new Document()
                .append("_id", token.id().toString())
                .append("matchId", token.matchId())
                .append("containerId", token.containerId())
                .append("playerId", token.playerId())
                .append("userId", token.userId() != null ? token.userId().toString() : null)
                .append("playerName", token.playerName())
                .append("scopes", new ArrayList<>(token.scopes()))
                .append("createdAt", token.createdAt().toString())
                .append("expiresAt", token.expiresAt().toString())
                .append("revokedAt", token.revokedAt() != null ? token.revokedAt().toString() : null);
        // Note: jwtToken is NOT stored in database for security
        return doc;
    }

    private MatchToken fromDocument(Document doc) {
        @SuppressWarnings("unchecked")
        List<String> scopesList = doc.get("scopes", List.class);
        Set<String> scopes = scopesList != null ? new HashSet<>(scopesList) : Set.of();

        String userIdStr = doc.getString("userId");
        UserId userId = userIdStr != null ? UserId.fromString(userIdStr) : null;

        return new MatchToken(
                MatchTokenId.fromString(doc.getString("_id")),
                doc.getString("matchId"),
                doc.getString("containerId"),
                doc.getString("playerId"),
                userId,
                doc.getString("playerName"),
                scopes,
                Instant.parse(doc.getString("createdAt")),
                Instant.parse(doc.getString("expiresAt")),
                parseInstant(doc.getString("revokedAt")),
                null // jwtToken is never stored
        );
    }

    private Instant parseInstant(String value) {
        return value != null ? Instant.parse(value) : null;
    }
}
