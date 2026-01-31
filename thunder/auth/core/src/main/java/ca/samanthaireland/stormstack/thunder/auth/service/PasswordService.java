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

package ca.samanthaireland.stormstack.thunder.auth.service;

/**
 * Service for password hashing and verification.
 *
 * <p>This interface abstracts password hashing operations to allow
 * different implementations (BCrypt, Argon2, etc.).
 */
public interface PasswordService {

    /**
     * Hashes a plain text password.
     *
     * @param password the plain text password
     * @return the hashed password
     * @throws IllegalArgumentException if password is null or empty
     */
    String hashPassword(String password);

    /**
     * Verifies a plain text password against a hash.
     *
     * @param password the plain text password
     * @param hash     the password hash
     * @return true if the password matches the hash
     */
    boolean verifyPassword(String password, String hash);

    /**
     * Checks if a password hash needs to be rehashed.
     *
     * <p>This is useful when the cost factor has been increased and
     * existing hashes need to be upgraded.
     *
     * @param hash the password hash
     * @return true if the hash should be regenerated
     */
    boolean needsRehash(String hash);

    /**
     * Creates a SHA-256 hash of a token string.
     *
     * <p>This is used for hashing refresh tokens and other cryptographically
     * secure tokens where BCrypt's length limit (72 bytes) is too restrictive.
     * JWTs are already signed, so a simple SHA-256 hash is sufficient.
     *
     * @param token the token to hash
     * @return the SHA-256 hash as a hex string
     * @throws IllegalArgumentException if token is null or empty
     */
    String hashToken(String token);

    /**
     * Verifies a token against a SHA-256 hash.
     *
     * @param token the token to verify
     * @param hash  the SHA-256 hash (hex string)
     * @return true if the token matches the hash
     */
    boolean verifyToken(String token, String hash);
}
