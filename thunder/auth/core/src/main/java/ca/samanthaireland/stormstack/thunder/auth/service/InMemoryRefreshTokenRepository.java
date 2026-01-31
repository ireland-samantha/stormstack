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

package ca.samanthaireland.stormstack.thunder.auth.service;

import ca.samanthaireland.stormstack.thunder.auth.model.RefreshToken;
import ca.samanthaireland.stormstack.thunder.auth.model.RefreshToken.RefreshTokenId;
import ca.samanthaireland.stormstack.thunder.auth.model.ServiceClientId;
import ca.samanthaireland.stormstack.thunder.auth.model.UserId;
import ca.samanthaireland.stormstack.thunder.auth.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of RefreshTokenRepository.
 *
 * <p>This implementation is suitable for development and testing.
 * For production, use a persistent implementation backed by MongoDB.
 */
public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<RefreshTokenId, RefreshToken> tokens = new ConcurrentHashMap<>();

    @Override
    public Optional<RefreshToken> findById(RefreshTokenId id) {
        return Optional.ofNullable(tokens.get(id));
    }

    @Override
    public List<RefreshToken> findByUserId(UserId userId) {
        return tokens.values().stream()
                .filter(t -> t.userId().equals(userId))
                .toList();
    }

    @Override
    public List<RefreshToken> findActiveByUserId(UserId userId) {
        return tokens.values().stream()
                .filter(t -> t.userId().equals(userId))
                .filter(RefreshToken::isValid)
                .toList();
    }

    @Override
    public List<RefreshToken> findByUserIdAndClientId(UserId userId, ServiceClientId clientId) {
        return tokens.values().stream()
                .filter(t -> t.userId().equals(userId))
                .filter(t -> t.clientId().equals(clientId))
                .toList();
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        tokens.put(token.id(), token);
        return token;
    }

    @Override
    public boolean deleteById(RefreshTokenId id) {
        return tokens.remove(id) != null;
    }

    @Override
    public int revokeAllByUserId(UserId userId) {
        int count = 0;
        for (Map.Entry<RefreshTokenId, RefreshToken> entry : tokens.entrySet()) {
            if (entry.getValue().userId().equals(userId) && !entry.getValue().isRevoked()) {
                tokens.put(entry.getKey(), entry.getValue().revoke());
                count++;
            }
        }
        return count;
    }

    @Override
    public int revokeAllByUserIdAndClientId(UserId userId, ServiceClientId clientId) {
        int count = 0;
        for (Map.Entry<RefreshTokenId, RefreshToken> entry : tokens.entrySet()) {
            RefreshToken token = entry.getValue();
            if (token.userId().equals(userId) &&
                token.clientId().equals(clientId) &&
                !token.isRevoked()) {
                tokens.put(entry.getKey(), token.revoke());
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteExpired() {
        int count = 0;
        Instant now = Instant.now();
        var iterator = tokens.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt().isBefore(now)) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }
}
