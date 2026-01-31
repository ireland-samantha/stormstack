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

package ca.samanthaireland.stormstack.thunder.auth.provider.config;

import ca.samanthaireland.stormstack.thunder.auth.config.AuthConfiguration;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Quarkus configuration mapping for auth settings.
 *
 * <p>This class bridges Quarkus configuration to the core AuthConfiguration interface.
 */
@ConfigMapping(prefix = "auth")
public interface QuarkusAuthConfig extends AuthConfiguration {

    /**
     * JWT issuer claim value.
     */
    @WithDefault("https://lightningfirefly.com")
    @Override
    String jwtIssuer();

    /**
     * Path to the RSA private key for JWT signing.
     * Can be a classpath resource (classpath:privateKey.pem) or file path.
     */
    @Override
    Optional<String> privateKeyLocation();

    /**
     * Path to the RSA public key for JWT verification.
     * Can be a classpath resource (classpath:publicKey.pem) or file path.
     */
    @Override
    Optional<String> publicKeyLocation();

    /**
     * Secret key for HMAC JWT signing (fallback if RSA keys not configured).
     */
    @Override
    Optional<String> jwtSecret();

    /**
     * Session token expiry in hours.
     */
    @WithDefault("24")
    @Override
    int sessionExpiryHours();

    /**
     * BCrypt cost factor.
     */
    @WithDefault("12")
    @Override
    int bcryptCost();

    /**
     * Initial admin password for bootstrap.
     */
    @Override
    Optional<String> initialAdminPassword();

    /**
     * API token length in bytes.
     */
    @WithDefault("32")
    @Override
    int apiTokenLengthBytes();
}
