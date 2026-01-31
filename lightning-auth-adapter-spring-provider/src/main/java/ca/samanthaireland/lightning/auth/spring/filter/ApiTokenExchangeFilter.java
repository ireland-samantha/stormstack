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

package ca.samanthaireland.lightning.auth.spring.filter;

import ca.samanthaireland.lightning.auth.spring.cache.TokenCache;
import ca.samanthaireland.lightning.auth.spring.client.AuthServiceClient;
import ca.samanthaireland.lightning.auth.spring.client.dto.TokenExchangeResponse;
import ca.samanthaireland.lightning.auth.spring.exception.LightningAuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Filter that exchanges API tokens for session JWTs.
 *
 * <p>This filter intercepts requests with an {@code X-Api-Token} header,
 * exchanges the API token for a short-lived session JWT via the Lightning
 * Auth service, and sets the {@code Authorization} header with the JWT
 * for downstream processing.
 *
 * <p>The filter maintains an in-memory cache of exchanged tokens to avoid
 * redundant calls to the auth service.
 *
 * <p>Filter order: {@code Ordered.HIGHEST_PRECEDENCE + 100} (runs before
 * JWT authorization filter).
 */
public class ApiTokenExchangeFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenExchangeFilter.class);

    /** Header containing the API token. */
    public static final String API_TOKEN_HEADER = "X-Api-Token";

    /** Header prefix for forwarded IP address. */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Filter order - runs before JWT filter. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final AuthServiceClient authServiceClient;
    private final TokenCache tokenCache;

    /**
     * Creates a new API token exchange filter.
     *
     * @param authServiceClient the auth service client
     * @param tokenCache        the token cache
     */
    public ApiTokenExchangeFilter(AuthServiceClient authServiceClient, TokenCache tokenCache) {
        this.authServiceClient = authServiceClient;
        this.tokenCache = tokenCache;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiToken = request.getHeader(API_TOKEN_HEADER);

        // If no API token header, pass through
        if (apiToken == null || apiToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // If Authorization header already present, don't override
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            log.debug("Authorization header already present, skipping token exchange");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String sessionJwt = exchangeOrGetCached(apiToken, getClientIp(request));

            // Wrap request to add Authorization header
            HttpServletRequest wrappedRequest = new AuthorizationHeaderWrapper(request, sessionJwt);
            filterChain.doFilter(wrappedRequest, response);

        } catch (LightningAuthException e) {
            handleAuthException(e, response);
        }
    }

    /**
     * Gets the session JWT from cache or exchanges the API token.
     */
    private String exchangeOrGetCached(String apiToken, String clientIp) {
        // Check cache first
        var cached = tokenCache.get(apiToken);
        if (cached.isPresent()) {
            log.debug("Using cached session token");
            return cached.get().sessionToken();
        }

        // Exchange token
        log.debug("Exchanging API token for session JWT");
        TokenExchangeResponse exchangeResponse = authServiceClient.exchangeToken(apiToken, clientIp);

        // Cache the result
        tokenCache.put(apiToken, exchangeResponse);

        return exchangeResponse.sessionToken();
    }

    /**
     * Extracts the client IP address from the request.
     */
    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Handles authentication exceptions by setting appropriate error response.
     */
    private void handleAuthException(LightningAuthException e, HttpServletResponse response)
            throws IOException {
        HttpStatus status = switch (e.getErrorCode()) {
            case INVALID_API_TOKEN -> HttpStatus.UNAUTHORIZED;
            case SERVICE_UNAVAILABLE, TIMEOUT -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.warn("API token exchange failed: {} - {}", e.getErrorCode(), e.getMessage());

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"message\":\"%s\"}",
                e.getErrorCode(),
                e.getMessage().replace("\"", "\\\"")
        ));
    }

    /**
     * Request wrapper that adds the Authorization header with the session JWT.
     */
    private static class AuthorizationHeaderWrapper extends HttpServletRequestWrapper {

        private final String sessionJwt;

        public AuthorizationHeaderWrapper(HttpServletRequest request, String sessionJwt) {
            super(request);
            this.sessionJwt = sessionJwt;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return "Bearer " + sessionJwt;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of("Bearer " + sessionJwt));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                names.add(originalNames.nextElement());
            }
            if (!names.stream().anyMatch(n -> n.equalsIgnoreCase(HttpHeaders.AUTHORIZATION))) {
                names.add(HttpHeaders.AUTHORIZATION);
            }
            return Collections.enumeration(names);
        }
    }
}
