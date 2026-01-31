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

package ca.samanthaireland.lightning.controlplane.provider.dto;

import java.time.Instant;

/**
 * Response returned when a player successfully joins a match.
 *
 * @param matchId            the cluster-unique match ID
 * @param playerId           the player ID
 * @param playerName         the player's display name
 * @param matchToken         the JWT match token for authenticating to WebSocket connections
 * @param commandWebSocketUrl WebSocket URL for sending commands
 * @param snapshotWebSocketUrl WebSocket URL for receiving snapshots
 * @param tokenExpiresAt     when the match token expires
 */
public record JoinMatchResponse(
        String matchId,
        String playerId,
        String playerName,
        String matchToken,
        String commandWebSocketUrl,
        String snapshotWebSocketUrl,
        Instant tokenExpiresAt
) {
    /**
     * Builder for JoinMatchResponse.
     */
    public static class Builder {
        private String matchId;
        private String playerId;
        private String playerName;
        private String matchToken;
        private String commandWebSocketUrl;
        private String snapshotWebSocketUrl;
        private Instant tokenExpiresAt;

        public Builder matchId(String matchId) {
            this.matchId = matchId;
            return this;
        }

        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder matchToken(String matchToken) {
            this.matchToken = matchToken;
            return this;
        }

        public Builder commandWebSocketUrl(String commandWebSocketUrl) {
            this.commandWebSocketUrl = commandWebSocketUrl;
            return this;
        }

        public Builder snapshotWebSocketUrl(String snapshotWebSocketUrl) {
            this.snapshotWebSocketUrl = snapshotWebSocketUrl;
            return this;
        }

        public Builder tokenExpiresAt(Instant tokenExpiresAt) {
            this.tokenExpiresAt = tokenExpiresAt;
            return this;
        }

        public JoinMatchResponse build() {
            return new JoinMatchResponse(
                    matchId,
                    playerId,
                    playerName,
                    matchToken,
                    commandWebSocketUrl,
                    snapshotWebSocketUrl,
                    tokenExpiresAt
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
