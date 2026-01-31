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

package ca.samanthaireland.lightning.auth.provider.http;

import ca.samanthaireland.lightning.auth.config.AuthConfiguration;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Web Key Set (JWKS) Endpoint (RFC 7517).
 *
 * <p>Publishes the public keys used for JWT signature verification,
 * allowing other services to validate tokens without calling back
 * to the auth service.
 */
@Path("/.well-known")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class JwksEndpoint {

    private static final Logger log = Logger.getLogger(JwksEndpoint.class);

    @Inject
    AuthConfiguration authConfig;

    private Map<String, Object> cachedJwks;

    void onStart(@Observes StartupEvent ev) {
        // Pre-load JWKS on startup
        try {
            cachedJwks = loadJwks();
            log.info("JWKS endpoint initialized with " +
                    ((List<?>) cachedJwks.get("keys")).size() + " key(s)");
        } catch (Exception e) {
            log.warn("Failed to initialize JWKS: " + e.getMessage() +
                    ". JWKS endpoint will return empty keyset.");
            cachedJwks = Map.of("keys", List.of());
        }
    }

    /**
     * Get the JSON Web Key Set.
     *
     * @return the JWKS document
     */
    @GET
    @Path("/jwks.json")
    public Map<String, Object> getJwks() {
        if (cachedJwks == null) {
            return Map.of("keys", List.of());
        }
        return cachedJwks;
    }

    private Map<String, Object> loadJwks() throws Exception {
        Map<String, Object> jwks = new LinkedHashMap<>();

        if (authConfig.publicKeyLocation().isPresent()) {
            // RSA key configured
            RSAPublicKey publicKey = loadPublicKey(authConfig.publicKeyLocation().get());
            Map<String, Object> rsaKey = buildRsaJwk(publicKey);
            jwks.put("keys", List.of(rsaKey));
        } else {
            // HMAC - no public keys to expose
            jwks.put("keys", List.of());
        }

        return jwks;
    }

    private Map<String, Object> buildRsaJwk(RSAPublicKey publicKey) throws Exception {
        Map<String, Object> jwk = new LinkedHashMap<>();

        // Key type
        jwk.put("kty", "RSA");

        // Public key use
        jwk.put("use", "sig");

        // Algorithm
        jwk.put("alg", "RS256");

        // Key ID - hash of the public key for identification
        String kid = computeKeyId(publicKey);
        jwk.put("kid", kid);

        // RSA modulus (n) - Base64url encoded
        BigInteger modulus = publicKey.getModulus();
        jwk.put("n", base64UrlEncode(modulus.toByteArray()));

        // RSA exponent (e) - Base64url encoded
        BigInteger exponent = publicKey.getPublicExponent();
        jwk.put("e", base64UrlEncode(exponent.toByteArray()));

        return jwk;
    }

    private String computeKeyId(RSAPublicKey publicKey) throws Exception {
        // Compute SHA-256 hash of the public key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        // Take first 8 bytes as key ID
        byte[] truncated = new byte[8];
        System.arraycopy(hash, 0, truncated, 0, 8);
        return base64UrlEncode(truncated);
    }

    private String base64UrlEncode(byte[] data) {
        // Remove leading zero bytes for positive BigIntegers
        int start = 0;
        while (start < data.length - 1 && data[start] == 0) {
            start++;
        }
        byte[] trimmed = new byte[data.length - start];
        System.arraycopy(data, start, trimmed, 0, trimmed.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }

    private RSAPublicKey loadPublicKey(String location) throws Exception {
        String pem = readKeyFile(location);
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private String readKeyFile(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String resource = location.substring("classpath:".length());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resource);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            return Files.readString(java.nio.file.Path.of(location));
        }
    }
}
