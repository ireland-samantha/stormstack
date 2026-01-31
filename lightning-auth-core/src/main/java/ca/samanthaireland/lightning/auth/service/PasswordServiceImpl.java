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

package ca.samanthaireland.lightning.auth.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * BCrypt-based implementation of PasswordService.
 *
 * <p>Uses BCrypt for secure password hashing with a configurable cost factor.
 * Thread-safe and suitable for use as a singleton.
 */
public class PasswordServiceImpl implements PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordServiceImpl.class);

    private static final int DEFAULT_COST = 12;
    private static final int MIN_COST = 4;
    private static final int MAX_COST = 31;

    private final int cost;

    /**
     * Creates a PasswordService with the default cost factor (12).
     */
    public PasswordServiceImpl() {
        this(DEFAULT_COST);
    }

    /**
     * Creates a PasswordService with a custom cost factor.
     *
     * @param cost the BCrypt cost factor (4-31)
     * @throws IllegalArgumentException if cost is out of range
     */
    public PasswordServiceImpl(int cost) {
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new IllegalArgumentException(
                    String.format("BCrypt cost must be between %d and %d, got %d", MIN_COST, MAX_COST, cost));
        }
        this.cost = cost;
        log.info("PasswordService initialized with BCrypt cost factor {}", cost);
    }

    @Override
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray());
    }

    @Override
    public boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }

    @Override
    public boolean needsRehash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return true;
        }
        // BCrypt hash format: $2a$12$...
        // The cost is the number after the second '$'
        try {
            if (hash.length() < 7 || !hash.startsWith("$2")) {
                return true;
            }
            int hashCost = Integer.parseInt(hash.substring(4, 6));
            return hashCost < cost;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Gets the configured cost factor.
     *
     * @return the BCrypt cost factor
     */
    public int getCost() {
        return cost;
    }

    @Override
    public String hashToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean verifyToken(String token, String hash) {
        if (token == null || hash == null) {
            return false;
        }
        String computedHash = hashToken(token);
        return computedHash.equalsIgnoreCase(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
