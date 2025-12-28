package com.lightningfirefly.engine.api.resource.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdapterConfig}.
 */
@DisplayName("AdapterConfig")
class AdapterConfigTest {

    @Nested
    @DisplayName("defaults()")
    class Defaults {

        @Test
        @DisplayName("should provide default connect timeout of 30 seconds")
        void shouldProvideDefaultConnectTimeout() {
            AdapterConfig config = AdapterConfig.defaults();

            assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("should provide default request timeout of 60 seconds")
        void shouldProvideDefaultRequestTimeout() {
            AdapterConfig config = AdapterConfig.defaults();

            assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("withConnectTimeout()")
    class WithConnectTimeout {

        @Test
        @DisplayName("should use custom connect timeout")
        void shouldUseCustomConnectTimeout() {
            AdapterConfig config = AdapterConfig.withConnectTimeout(Duration.ofSeconds(10));

            assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("should use default request timeout")
        void shouldUseDefaultRequestTimeout() {
            AdapterConfig config = AdapterConfig.withConnectTimeout(Duration.ofSeconds(10));

            assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("should use custom connect timeout")
        void shouldUseCustomConnectTimeout() {
            AdapterConfig config = AdapterConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(120));

            assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("should use custom request timeout")
        void shouldUseCustomRequestTimeout() {
            AdapterConfig config = AdapterConfig.of(Duration.ofSeconds(5), Duration.ofSeconds(120));

            assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(120));
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        @DisplayName("DEFAULT_CONNECT_TIMEOUT should be 30 seconds")
        void defaultConnectTimeoutShouldBe30Seconds() {
            assertThat(AdapterConfig.DEFAULT_CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("DEFAULT_REQUEST_TIMEOUT should be 60 seconds")
        void defaultRequestTimeoutShouldBe60Seconds() {
            assertThat(AdapterConfig.DEFAULT_REQUEST_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
        }
    }
}
