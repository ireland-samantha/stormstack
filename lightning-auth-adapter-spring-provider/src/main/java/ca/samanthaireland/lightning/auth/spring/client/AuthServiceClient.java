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

package ca.samanthaireland.lightning.auth.spring.client;

import ca.samanthaireland.lightning.auth.spring.LightningAuthProperties;
import ca.samanthaireland.lightning.auth.spring.client.dto.OAuth2TokenResponse;
import ca.samanthaireland.lightning.auth.spring.client.dto.TokenExchangeResponse;
import ca.samanthaireland.lightning.auth.spring.exception.LightningAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * HTTP client for communicating with the Lightning Auth service.
 *
 * <p>This client handles OAuth2 token exchange requests (RFC 8693), converting
 * API tokens into short-lived session JWTs.
 */
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);
    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE_API_TOKEN = "urn:ietf:params:oauth:token-type:api_token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration requestTimeout;

    /**
     * Creates a new auth service client.
     *
     * @param properties   the configuration properties
     * @param objectMapper the JSON object mapper
     */
    public AuthServiceClient(LightningAuthProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = normalizeUrl(properties.getServiceUrl());
        this.requestTimeout = Duration.ofMillis(properties.getRequestTimeoutMs());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();

        log.info("AuthServiceClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Exchanges an API token for a session JWT using OAuth2 token exchange (RFC 8693).
     *
     * @param apiToken  the plaintext API token
     * @param clientIp  the client IP address (for audit logging)
     * @return the token exchange response containing the session JWT
     * @throws LightningAuthException if the exchange fails
     */
    public TokenExchangeResponse exchangeToken(String apiToken, String clientIp) {
        try {
            String formBody = buildTokenExchangeForm(apiToken);

            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + TOKEN_ENDPOINT))
                    .header("Content-Type", CONTENT_TYPE)
                    .header("Accept", "application/json")
                    .header("X-Forwarded-For", clientIp != null ? clientIp : "unknown")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            log.debug("Sending OAuth2 token exchange request to {}", httpRequest.uri());

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            return handleResponse(response);

        } catch (HttpTimeoutException e) {
            log.error("Token exchange request timed out");
            throw LightningAuthException.timeout(e);
        } catch (IOException e) {
            log.error("Failed to connect to auth service: {}", e.getMessage());
            throw LightningAuthException.serviceUnavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw LightningAuthException.internalError("Request interrupted", e);
        }
    }

    private TokenExchangeResponse handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode == 200) {
            try {
                OAuth2TokenResponse oauthResponse = objectMapper.readValue(body, OAuth2TokenResponse.class);

                // Convert OAuth2 response to our internal format
                Instant expiresAt = Instant.now().plusSeconds(oauthResponse.expiresIn());

                return new TokenExchangeResponse(
                        oauthResponse.accessToken(),
                        expiresAt,
                        oauthResponse.scopeSet()
                );
            } catch (IOException e) {
                log.error("Failed to parse token exchange response: {}", e.getMessage());
                throw LightningAuthException.internalError("Failed to parse response", e);
            }
        }

        if (statusCode == 401 || statusCode == 400) {
            // OAuth2 uses 400 for invalid_grant errors
            log.warn("Token exchange failed: invalid or expired API token");
            throw LightningAuthException.invalidApiToken();
        }

        if (statusCode >= 500) {
            log.error("Auth service returned error: {} - {}", statusCode, body);
            throw LightningAuthException.serviceUnavailable(
                    new RuntimeException("Auth service returned " + statusCode));
        }

        log.error("Unexpected response from auth service: {} - {}", statusCode, body);
        throw LightningAuthException.internalError(
                "Unexpected response: " + statusCode, null);
    }

    private String buildTokenExchangeForm(String apiToken) {
        return "grant_type=" + urlEncode(GRANT_TYPE_TOKEN_EXCHANGE)
                + "&subject_token=" + urlEncode(apiToken)
                + "&subject_token_type=" + urlEncode(TOKEN_TYPE_API_TOKEN);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8082";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
