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

package ca.samanthaireland.stormstack.thunder.auth.spring.filter;

import ca.samanthaireland.stormstack.thunder.auth.spring.cache.TokenCache;
import ca.samanthaireland.stormstack.thunder.auth.spring.client.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.spring.client.dto.TokenExchangeResponse;
import ca.samanthaireland.stormstack.thunder.auth.spring.exception.LightningAuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiTokenExchangeFilterTest {

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private TokenCache tokenCache;

    @Mock
    private FilterChain filterChain;

    private ApiTokenExchangeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiTokenExchangeFilter(authServiceClient, tokenCache);
    }

    @Test
    void doFilter_withoutApiToken_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authServiceClient, tokenCache);
    }

    @Test
    void doFilter_withExistingAuthorization_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, "lat_test_token");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer existing.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authServiceClient, tokenCache);
    }

    @Test
    void doFilter_withApiToken_exchangesAndSetsAuthHeader() throws Exception {
        String apiToken = "lat_test_token";
        String sessionJwt = "session.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, apiToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(eq(apiToken), anyString()))
                .thenReturn(new TokenExchangeResponse(
                        sessionJwt,
                        Instant.now().plusSeconds(3600),
                        Set.of("view_snapshots")
                ));

        ArgumentCaptor<jakarta.servlet.http.HttpServletRequest> requestCaptor =
                ArgumentCaptor.forClass(jakarta.servlet.http.HttpServletRequest.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any(HttpServletResponse.class));
        verify(tokenCache).put(eq(apiToken), any(TokenExchangeResponse.class));

        // Verify the wrapped request has the Authorization header
        jakarta.servlet.http.HttpServletRequest wrappedRequest = requestCaptor.getValue();
        assertThat(wrappedRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + sessionJwt);
    }

    @Test
    void doFilter_withCachedToken_usesCache() throws Exception {
        String apiToken = "lat_cached_token";
        String cachedJwt = "cached.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, apiToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenCache.get(apiToken)).thenReturn(Optional.of(
                new TokenCache.CachedSession(
                        cachedJwt,
                        Instant.now().plusSeconds(3600),
                        Set.of("admin")
                )
        ));

        ArgumentCaptor<jakarta.servlet.http.HttpServletRequest> requestCaptor =
                ArgumentCaptor.forClass(jakarta.servlet.http.HttpServletRequest.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(requestCaptor.capture(), any(HttpServletResponse.class));
        verifyNoInteractions(authServiceClient);

        // Verify cached JWT is used
        jakarta.servlet.http.HttpServletRequest wrappedRequest = requestCaptor.getValue();
        assertThat(wrappedRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + cachedJwt);
    }

    @Test
    void doFilter_withInvalidApiToken_returns401() throws Exception {
        String apiToken = "lat_invalid_token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, apiToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(eq(apiToken), anyString()))
                .thenThrow(LightningAuthException.invalidApiToken());

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("INVALID_API_TOKEN");
    }

    @Test
    void doFilter_withServiceUnavailable_returns503() throws Exception {
        String apiToken = "lat_service_down";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, apiToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(eq(apiToken), anyString()))
                .thenThrow(LightningAuthException.serviceUnavailable(new RuntimeException("Connection refused")));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void doFilter_extractsClientIpFromXForwardedFor() throws Exception {
        String apiToken = "lat_forwarded_ip";
        String clientIp = "192.168.1.100";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokenExchangeFilter.API_TOKEN_HEADER, apiToken);
        request.addHeader("X-Forwarded-For", clientIp + ", 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(anyString(), anyString()))
                .thenReturn(new TokenExchangeResponse(
                        "jwt",
                        Instant.now().plusSeconds(3600),
                        Set.of()
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(authServiceClient).exchangeToken(eq(apiToken), eq(clientIp));
    }

    @Test
    void getOrder_returnsCorrectValue() {
        assertThat(filter.getOrder()).isEqualTo(ApiTokenExchangeFilter.ORDER);
    }
}
