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

package ca.samanthaireland.stormstack.thunder.auth.quarkus.client;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.config.LightningAuthConfig;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.exception.LightningAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * HTTP client for communicating with the Lightning Auth service.
 *
 * <p>Handles OAuth2 token exchange operations (RFC 8693) for exchanging
 * API tokens for short-lived session JWTs.
 */
@ApplicationScoped
@IfBuildProperty(name = "lightning.auth.filters.enabled", stringValue = "true", enableIfMissing = false)
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String GRANT_TYPE_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE_API_TOKEN = "urn:ietf:params:oauth:token-type:api_token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serviceUrl;
    private final Duration requestTimeout;

    @Inject
    public AuthServiceClient(LightningAuthConfig config) {
        this.serviceUrl = config.serviceUrl();
        this.requestTimeout = Duration.ofMillis(config.requestTimeoutMs());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    AuthServiceClient(HttpClient httpClient, ObjectMapper objectMapper,
                      String serviceUrl, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.serviceUrl = serviceUrl;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Exchange an API token for a short-lived session JWT using OAuth2 token exchange (RFC 8693).
     *
     * @param apiToken the API token to exchange
     * @return the token exchange response containing session JWT and metadata
     * @throws LightningAuthException if the exchange fails
     */
    public TokenExchangeResponse exchangeToken(String apiToken) {
        try {
            // Build form-urlencoded request body per OAuth2 spec
            String formBody = buildTokenExchangeForm(apiToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + TOKEN_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(requestTimeout)
                    .build();

            log.debug("Sending OAuth2 token exchange request to {}", request.uri());

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                OAuth2TokenResponse oauthResponse = objectMapper.readValue(
                        response.body(), OAuth2TokenResponse.class);

                // Convert OAuth2 response to our internal response format
                Instant expiresAt = Instant.now().plusSeconds(oauthResponse.expiresIn());

                return new TokenExchangeResponse(
                        oauthResponse.accessToken(),
                        expiresAt,
                        oauthResponse.scopeSet()
                );
            } else if (response.statusCode() == 401 || response.statusCode() == 400) {
                // OAuth2 uses 400 for invalid_grant errors
                log.warn("Token exchange failed: {}", response.body());
                throw new LightningAuthException("INVALID_API_TOKEN",
                        "Invalid or expired API token");
            } else if (response.statusCode() == 403) {
                throw new LightningAuthException("TOKEN_REVOKED",
                        "API token has been revoked");
            } else {
                log.error("Auth service returned unexpected status: {} - {}",
                        response.statusCode(), response.body());
                throw new LightningAuthException("AUTH_SERVICE_ERROR",
                        "Auth service returned status " + response.statusCode());
            }
        } catch (LightningAuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to communicate with auth service", e);
            throw new LightningAuthException("AUTH_SERVICE_UNAVAILABLE",
                    "Failed to communicate with auth service: " + e.getMessage(), e);
        }
    }

    private String buildTokenExchangeForm(String apiToken) {
        return "grant_type=" + urlEncode(GRANT_TYPE_TOKEN_EXCHANGE)
                + "&subject_token=" + urlEncode(apiToken)
                + "&subject_token_type=" + urlEncode(TOKEN_TYPE_API_TOKEN);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
