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

package ca.samanthaireland.lightning.auth.spring;

import ca.samanthaireland.lightning.auth.spring.cache.TokenCache;
import ca.samanthaireland.lightning.auth.spring.client.AuthServiceClient;
import ca.samanthaireland.lightning.auth.spring.filter.ApiTokenExchangeFilter;
import ca.samanthaireland.lightning.auth.spring.filter.JwtAuthorizationFilter;
import ca.samanthaireland.lightning.auth.spring.security.RequiredScopesAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class LightningAuthAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LightningAuthAutoConfiguration.class));

    @Test
    void autoConfiguration_createsBeansWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.enabled=true",
                        "lightning.auth.service-url=http://localhost:8082",
                        "lightning.auth.jwt.secret=test-secret-key-for-hmac-validation"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthServiceClient.class);
                    assertThat(context).hasSingleBean(TokenCache.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(RequiredScopesAspect.class);
                    assertThat(context).hasBean("apiTokenExchangeFilterRegistration");
                    assertThat(context).hasBean("jwtAuthorizationFilterRegistration");
                });
    }

    @Test
    void autoConfiguration_disabledWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("lightning.auth.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuthServiceClient.class);
                    assertThat(context).doesNotHaveBean(TokenCache.class);
                    assertThat(context).doesNotHaveBean(RequiredScopesAspect.class);
                });
    }

    @Test
    void autoConfiguration_enabledByDefault() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.jwt.secret=test-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthServiceClient.class);
                    assertThat(context).hasSingleBean(TokenCache.class);
                });
    }

    @Test
    void autoConfiguration_usesConfiguredServiceUrl() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.service-url=http://custom-auth:9000",
                        "lightning.auth.jwt.secret=test-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthServiceClient.class);
                });
    }

    @Test
    void autoConfiguration_skipJwtDecoderWithoutSecret() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.enabled=true"
                        // No jwt.secret configured
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthServiceClient.class);
                    assertThat(context).hasSingleBean(TokenCache.class);
                    // JwtDecoder should not be created without secret
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                    assertThat(context).doesNotHaveBean("jwtAuthorizationFilterRegistration");
                });
    }

    @Test
    void filterRegistration_hasCorrectOrder() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.jwt.secret=test-secret"
                )
                .run(context -> {
                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<ApiTokenExchangeFilter> apiTokenFilter =
                            (FilterRegistrationBean<ApiTokenExchangeFilter>)
                                    context.getBean("apiTokenExchangeFilterRegistration");

                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<JwtAuthorizationFilter> jwtFilter =
                            (FilterRegistrationBean<JwtAuthorizationFilter>)
                                    context.getBean("jwtAuthorizationFilterRegistration");

                    assertThat(apiTokenFilter.getOrder()).isEqualTo(ApiTokenExchangeFilter.ORDER);
                    assertThat(jwtFilter.getOrder()).isEqualTo(JwtAuthorizationFilter.ORDER);
                    // API token filter should run before JWT filter
                    assertThat(apiTokenFilter.getOrder()).isLessThan(jwtFilter.getOrder());
                });
    }

    @Test
    void properties_bindCorrectly() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.enabled=true",
                        "lightning.auth.service-url=http://auth.example.com:8082",
                        "lightning.auth.connect-timeout-ms=3000",
                        "lightning.auth.request-timeout-ms=8000",
                        "lightning.auth.cache.enabled=true",
                        "lightning.auth.cache.ttl-buffer-seconds=120",
                        "lightning.auth.cache.cleanup-interval-seconds=600",
                        "lightning.auth.jwt.issuer=https://issuer.example.com",
                        "lightning.auth.jwt.secret=my-secret-key"
                )
                .run(context -> {
                    LightningAuthProperties properties = context.getBean(LightningAuthProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getServiceUrl()).isEqualTo("http://auth.example.com:8082");
                    assertThat(properties.getConnectTimeoutMs()).isEqualTo(3000);
                    assertThat(properties.getRequestTimeoutMs()).isEqualTo(8000);

                    assertThat(properties.getCache().isEnabled()).isTrue();
                    assertThat(properties.getCache().getTtlBufferSeconds()).isEqualTo(120);
                    assertThat(properties.getCache().getCleanupIntervalSeconds()).isEqualTo(600);

                    assertThat(properties.getJwt().getIssuer()).isEqualTo("https://issuer.example.com");
                    assertThat(properties.getJwt().getSecret()).isEqualTo("my-secret-key");
                });
    }

    @Test
    void properties_haveDefaultValues() {
        contextRunner
                .withPropertyValues(
                        "lightning.auth.jwt.secret=test-secret"
                )
                .run(context -> {
                    LightningAuthProperties properties = context.getBean(LightningAuthProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getServiceUrl()).isEqualTo("http://localhost:8082");
                    assertThat(properties.getConnectTimeoutMs()).isEqualTo(5000);
                    assertThat(properties.getRequestTimeoutMs()).isEqualTo(10000);

                    assertThat(properties.getCache().isEnabled()).isTrue();
                    assertThat(properties.getCache().getTtlBufferSeconds()).isEqualTo(60);
                    assertThat(properties.getCache().getCleanupIntervalSeconds()).isEqualTo(300);
                });
    }
}
