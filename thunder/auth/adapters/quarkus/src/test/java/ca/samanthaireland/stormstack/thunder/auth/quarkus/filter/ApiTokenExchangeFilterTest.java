package ca.samanthaireland.stormstack.thunder.auth.quarkus.filter;

import ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.CachedSession;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.cache.TokenCache;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.client.TokenExchangeResponse;
import ca.samanthaireland.stormstack.thunder.auth.quarkus.exception.LightningAuthException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiTokenExchangeFilterTest {

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private TokenCache tokenCache;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private MultivaluedMap<String, String> headers;

    private ApiTokenExchangeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiTokenExchangeFilter(true, authServiceClient, tokenCache);
        lenient().when(requestContext.getHeaders()).thenReturn(headers);
    }

    @Test
    void filter_whenDisabled_doesNothing() {
        filter = new ApiTokenExchangeFilter(false, authServiceClient, tokenCache);

        filter.filter(requestContext);

        verify(authServiceClient, never()).exchangeToken(any());
        verify(tokenCache, never()).get(any());
    }

    @Test
    void filter_withNoApiToken_doesNothing() {
        when(requestContext.getHeaderString("X-Api-Token")).thenReturn(null);

        filter.filter(requestContext);

        verify(authServiceClient, never()).exchangeToken(any());
    }

    @Test
    void filter_withBlankApiToken_doesNothing() {
        when(requestContext.getHeaderString("X-Api-Token")).thenReturn("   ");

        filter.filter(requestContext);

        verify(authServiceClient, never()).exchangeToken(any());
    }

    @Test
    void filter_withExistingAuthorizationHeader_doesNothing() {
        when(requestContext.getHeaderString("X-Api-Token")).thenReturn("lat_test_token");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer existing.jwt");

        filter.filter(requestContext);

        verify(authServiceClient, never()).exchangeToken(any());
    }

    @Test
    void filter_withCachedSession_usesCache() {
        String apiToken = "lat_test_token";
        CachedSession cachedSession = new CachedSession(
                "cached.session.jwt",
                Instant.now().plusSeconds(3600),
                Set.of("read")
        );

        when(requestContext.getHeaderString("X-Api-Token")).thenReturn(apiToken);
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);
        when(tokenCache.get(apiToken)).thenReturn(Optional.of(cachedSession));

        filter.filter(requestContext);

        verify(authServiceClient, never()).exchangeToken(any());
        verify(headers).putSingle("Authorization", "Bearer cached.session.jwt");
    }

    @Test
    void filter_withNoCache_exchangesToken() {
        String apiToken = "lat_test_token";
        TokenExchangeResponse response = new TokenExchangeResponse(
                "new.session.jwt",
                Instant.now().plusSeconds(3600),
                Set.of("read", "write")
        );

        when(requestContext.getHeaderString("X-Api-Token")).thenReturn(apiToken);
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);
        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(apiToken)).thenReturn(response);

        filter.filter(requestContext);

        verify(authServiceClient).exchangeToken(apiToken);
        verify(tokenCache).put(apiToken, response);
        verify(headers).putSingle("Authorization", "Bearer new.session.jwt");
    }

    @Test
    void filter_whenExchangeFails_abortsWithUnauthorized() {
        String apiToken = "lat_invalid_token";

        when(requestContext.getHeaderString("X-Api-Token")).thenReturn(apiToken);
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);
        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(apiToken))
                .thenThrow(new LightningAuthException("INVALID_API_TOKEN", "Invalid token"));

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void filter_whenServiceUnavailable_abortsWithError() {
        String apiToken = "lat_test_token";

        when(requestContext.getHeaderString("X-Api-Token")).thenReturn(apiToken);
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);
        when(tokenCache.get(apiToken)).thenReturn(Optional.empty());
        when(authServiceClient.exchangeToken(apiToken))
                .thenThrow(new RuntimeException("Connection refused"));

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(500);
    }
}
