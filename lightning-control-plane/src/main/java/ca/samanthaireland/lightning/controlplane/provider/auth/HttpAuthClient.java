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

package ca.samanthaireland.lightning.controlplane.provider.auth;

import ca.samanthaireland.lightning.controlplane.auth.AuthClient;
import ca.samanthaireland.lightning.controlplane.auth.AuthClientException;
import ca.samanthaireland.lightning.controlplane.auth.IssueMatchTokenRequest;
import ca.samanthaireland.lightning.controlplane.auth.MatchTokenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * HTTP-based implementation of AuthClient that delegates to AuthServiceClient.
 */
@ApplicationScoped
public class HttpAuthClient implements AuthClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAuthClient.class);

    private final AuthServiceClient authServiceClient;

    @Inject
    public HttpAuthClient(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    public MatchTokenResponse issueMatchToken(IssueMatchTokenRequest request) {
        log.debug("Issuing match token for match={}, player={}",
                request.matchId(), request.playerId());

        long containerId = parseContainerId(request.containerId());

        AuthServiceClient.MatchTokenResult result = authServiceClient.issueMatchToken(
                request.matchId(),
                containerId,
                request.playerId(),
                request.playerName(),
                request.scopes() != null ? request.scopes() : Set.of("match.command.send", "match.snapshot.read")
        );

        return switch (result) {
            case AuthServiceClient.MatchTokenResult.Success success -> {
                log.info("Match token issued: tokenId={}, match={}, player={}",
                        success.tokenId(), success.matchId(), success.playerId());
                yield new MatchTokenResponse(
                        success.tokenId(),
                        success.matchId(),
                        success.playerId(),
                        request.playerName(),
                        request.scopes(),
                        success.token(),
                        success.expiresAt()
                );
            }
            case AuthServiceClient.MatchTokenResult.Failure failure -> {
                log.error("Failed to issue match token: status={}, message={}",
                        failure.statusCode(), failure.message());
                throw AuthClientException.httpError(
                        failure.statusCode(),
                        "MATCH_TOKEN_ISSUANCE_FAILED",
                        failure.message()
                );
            }
        };
    }

    private long parseContainerId(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(containerId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
