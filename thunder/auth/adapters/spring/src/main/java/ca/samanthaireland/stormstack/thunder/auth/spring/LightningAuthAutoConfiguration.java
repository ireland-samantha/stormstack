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

package ca.samanthaireland.stormstack.thunder.auth.spring;

import ca.samanthaireland.stormstack.thunder.auth.spring.cache.TokenCache;
import ca.samanthaireland.stormstack.thunder.auth.spring.client.AuthServiceClient;
import ca.samanthaireland.stormstack.thunder.auth.spring.filter.ApiTokenExchangeFilter;
import ca.samanthaireland.stormstack.thunder.auth.spring.filter.JwtAuthorizationFilter;
import ca.samanthaireland.stormstack.thunder.auth.spring.security.RequiredScopesAspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Spring Boot auto-configuration for Lightning Auth integration.
 *
 * <p>This configuration is automatically applied when the
 * {@code lightning.auth.enabled} property is true (which is the default).
 *
 * <p>It registers:
 * <ul>
 *   <li>{@link AuthServiceClient} - HTTP client for token exchange</li>
 *   <li>{@link TokenCache} - In-memory token cache with TTL</li>
 *   <li>{@link ApiTokenExchangeFilter} - Filter to exchange X-Api-Token for JWT</li>
 *   <li>{@link JwtAuthorizationFilter} - Filter to validate JWT and set SecurityContext</li>
 *   <li>{@link RequiredScopesAspect} - AOP aspect for @RequiredScopes enforcement</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(LightningAuthProperties.class)
@EnableAspectJAutoProxy
@ConditionalOnProperty(name = "lightning.auth.enabled", havingValue = "true", matchIfMissing = true)
public class LightningAuthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LightningAuthAutoConfiguration.class);

    /**
     * Creates the ObjectMapper for JSON serialization.
     * Only created if not already defined by the application.
     */
    @Bean
    @ConditionalOnMissingBean(name = "lightningAuthObjectMapper")
    public ObjectMapper lightningAuthObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Creates the auth service HTTP client.
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthServiceClient authServiceClient(
            LightningAuthProperties properties,
            ObjectMapper lightningAuthObjectMapper) {
        log.info("Creating AuthServiceClient for URL: {}", properties.getServiceUrl());
        return new AuthServiceClient(properties, lightningAuthObjectMapper);
    }

    /**
     * Creates the token cache.
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenCache tokenCache(LightningAuthProperties properties) {
        log.info("Creating TokenCache (enabled: {})", properties.getCache().isEnabled());
        return new TokenCache(properties.getCache());
    }

    /**
     * Creates the JWT decoder for token validation.
     * Uses HMAC-SHA256 with the configured secret.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "lightning.auth.jwt.secret")
    public JwtDecoder lightningJwtDecoder(LightningAuthProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("lightning.auth.jwt.secret must be configured");
        }

        log.info("Creating JwtDecoder with HMAC-SHA256");

        // Create secret key from the configured secret
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Registers the API token exchange filter.
     */
    @Bean
    public FilterRegistrationBean<ApiTokenExchangeFilter> apiTokenExchangeFilterRegistration(
            AuthServiceClient authServiceClient,
            TokenCache tokenCache) {
        log.info("Registering ApiTokenExchangeFilter");

        FilterRegistrationBean<ApiTokenExchangeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiTokenExchangeFilter(authServiceClient, tokenCache));
        registration.addUrlPatterns("/*");
        registration.setOrder(ApiTokenExchangeFilter.ORDER);
        registration.setName("apiTokenExchangeFilter");
        return registration;
    }

    /**
     * Registers the JWT authorization filter.
     */
    @Bean
    @ConditionalOnProperty(name = "lightning.auth.jwt.secret")
    public FilterRegistrationBean<JwtAuthorizationFilter> jwtAuthorizationFilterRegistration(
            JwtDecoder lightningJwtDecoder) {
        log.info("Registering JwtAuthorizationFilter");

        FilterRegistrationBean<JwtAuthorizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthorizationFilter(lightningJwtDecoder));
        registration.addUrlPatterns("/*");
        registration.setOrder(JwtAuthorizationFilter.ORDER);
        registration.setName("jwtAuthorizationFilter");
        return registration;
    }

    /**
     * Creates the @RequiredScopes enforcement aspect.
     */
    @Bean
    @ConditionalOnMissingBean
    public RequiredScopesAspect requiredScopesAspect() {
        log.info("Creating RequiredScopesAspect for scope enforcement");
        return new RequiredScopesAspect();
    }
}
