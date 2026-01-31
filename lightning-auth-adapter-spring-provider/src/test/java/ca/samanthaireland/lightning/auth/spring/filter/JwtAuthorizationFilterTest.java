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

import ca.samanthaireland.lightning.auth.spring.security.LightningAuthentication;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthorizationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private FilterChain filterChain;

    private JwtAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthorizationFilter(jwtDecoder);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_withoutAuthHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_withNonBearerToken_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void doFilter_withValidJwt_setsAuthentication() throws Exception {
        String jwtToken = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = createJwt(
                "user-123",
                "testuser",
                List.of("view_snapshots", "submit_commands"),
                Instant.now().plusSeconds(3600)
        );
        when(jwtDecoder.decode(jwtToken)).thenReturn(jwt);

        // Capture authentication during filter chain
        doAnswer(invocation -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth).isInstanceOf(LightningAuthentication.class);

            LightningAuthentication lightningAuth = (LightningAuthentication) auth;
            assertThat(lightningAuth.getUserId()).isEqualTo("user-123");
            assertThat(lightningAuth.getUsername()).isEqualTo("testuser");
            assertThat(lightningAuth.getScopes())
                    .containsExactlyInAnyOrder("view_snapshots", "submit_commands");
            assertThat(lightningAuth.isAuthenticated()).isTrue();
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_afterCompletion_clearsSecurityContext() throws Exception {
        String jwtToken = "valid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = createJwt("user-123", "testuser", List.of(), Instant.now().plusSeconds(3600));
        when(jwtDecoder.decode(jwtToken)).thenReturn(jwt);

        filter.doFilterInternal(request, response, filterChain);

        // After filter completes, context should be cleared
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_withInvalidJwt_returns401() throws Exception {
        String jwtToken = "invalid.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode(jwtToken))
                .thenThrow(new JwtException("Invalid signature"));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("INVALID_JWT");
    }

    @Test
    void doFilter_withExpiredJwt_returns401() throws Exception {
        String jwtToken = "expired.jwt.token";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode(jwtToken))
                .thenThrow(new JwtException("JWT expired at..."));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_extractsScopesFromRolesClaim() throws Exception {
        String jwtToken = "jwt.with.roles";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // JWT with roles instead of scopes
        Jwt jwt = Jwt.withTokenValue(jwtToken)
                .header("alg", "HS256")
                .subject("user-456")
                .claim("username", "roleuser")
                .claim("roles", List.of("admin", "moderator"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuedAt(Instant.now())
                .build();
        when(jwtDecoder.decode(jwtToken)).thenReturn(jwt);

        doAnswer(invocation -> {
            var auth = (LightningAuthentication) SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getScopes()).containsExactlyInAnyOrder("admin", "moderator");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_extractsApiTokenId() throws Exception {
        String jwtToken = "jwt.with.api_token_id";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = Jwt.withTokenValue(jwtToken)
                .header("alg", "HS256")
                .subject("user-789")
                .claim("username", "apiuser")
                .claim("api_token_id", "token-abc-123")
                .claim("scopes", List.of("view_snapshots"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuedAt(Instant.now())
                .build();
        when(jwtDecoder.decode(jwtToken)).thenReturn(jwt);

        doAnswer(invocation -> {
            var auth = (LightningAuthentication) SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getApiTokenId()).isEqualTo("token-abc-123");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void getOrder_returnsCorrectValue() {
        assertThat(filter.getOrder()).isEqualTo(JwtAuthorizationFilter.ORDER);
    }

    private Jwt createJwt(String userId, String username, List<String> scopes, Instant expiresAt) {
        return Jwt.withTokenValue("test.jwt.token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("user_id", userId)
                .claim("username", username)
                .claim("scopes", scopes)
                .expiresAt(expiresAt)
                .issuedAt(Instant.now())
                .build();
    }
}
