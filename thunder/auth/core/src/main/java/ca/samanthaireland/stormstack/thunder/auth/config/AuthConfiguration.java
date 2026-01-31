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

package ca.samanthaireland.stormstack.thunder.auth.config;

import java.util.Optional;

/**
 * Configuration for the authentication service.
 *
 * <p>This interface defines the configuration required by the auth service.
 * Implementations can be backed by various configuration sources (environment
 * variables, config files, etc.).
 *
 * <p>JWT signing supports two modes:
 * <ul>
 *   <li>RSA (RS256) - preferred for production, uses public/private key pair</li>
 *   <li>HMAC (HS256) - fallback mode, uses shared secret</li>
 * </ul>
 */
public interface AuthConfiguration {

    /**
     * Gets the JWT issuer claim value.
     *
     * @return the issuer (e.g., "https://lightningfirefly.com")
     */
    String jwtIssuer();

    /**
     * Gets the path to the RSA private key for JWT signing.
     *
     * <p>When this is set (along with publicKeyLocation), RSA signing is used.
     * The key should be in PEM format (PKCS#8).
     *
     * @return the private key path, or empty to use HMAC
     */
    default Optional<String> privateKeyLocation() {
        return Optional.empty();
    }

    /**
     * Gets the path to the RSA public key for JWT verification.
     *
     * <p>When this is set (along with privateKeyLocation), RSA verification is used.
     * The key should be in PEM format (X.509).
     *
     * @return the public key path, or empty to use HMAC
     */
    default Optional<String> publicKeyLocation() {
        return Optional.empty();
    }

    /**
     * Gets the secret key for HMAC-based JWT signing.
     *
     * <p>This is used only when RSA keys are not configured.
     *
     * @return the secret key, or empty if RSA keys are used
     */
    Optional<String> jwtSecret();

    /**
     * Gets the session token expiry time in hours.
     *
     * @return the expiry time in hours (default: 24)
     */
    int sessionExpiryHours();

    /**
     * Gets the BCrypt cost factor for password hashing.
     *
     * @return the cost factor (default: 12, range: 4-31)
     */
    int bcryptCost();

    /**
     * Gets the initial admin password for bootstrap.
     *
     * <p>This password is used to create the initial admin user if no
     * users exist in the database. Should be changed after first login.
     *
     * @return the initial admin password, or empty if not set
     */
    Optional<String> initialAdminPassword();

    /**
     * Gets the API token length in bytes.
     *
     * <p>The actual token will be Base64-encoded, so the string length
     * will be approximately 4/3 of this value.
     *
     * @return the token length in bytes (default: 32)
     */
    default int apiTokenLengthBytes() {
        return 32;
    }
}
