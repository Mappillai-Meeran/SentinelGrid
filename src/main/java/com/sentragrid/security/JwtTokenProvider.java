package com.sentragrid.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JWT token provider supporting strong 256-bit entropy validation and
 * zero-downtime backward-compatible verification key rotation.
 *
 * <p><strong>Key Rotation & Retirement Procedure:</strong></p>
 * <ol>
 *   <li>Generate a new cryptographically strong 256-bit secret.</li>
 *   <li>Promote the new secret to {@code app.jwt.secret} (active signing key).</li>
 *   <li>Move the previous signing secret into {@code app.jwt.verification-secrets} (comma-separated list).</li>
 *   <li>Deploy the application. New tokens will be issued using the new active key, while existing
 *       tokens issued under the previous key remain fully verifiable until their expiry (24 hours).</li>
 *   <li>After the maximum token lifetime has elapsed (e.g. 24 hours), remove the retired secret
 *       from {@code app.jwt.verification-secrets}.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.verification-secrets:}")
    private String verificationSecrets;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey activeSigningKey;
    private final List<SecretKey> verificationKeys = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        boolean isProduction = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production")
                        || p.equalsIgnoreCase("demo") || p.equalsIgnoreCase("cloud-demo"))
                || (Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("docker"))
                    && Arrays.stream(environment.getActiveProfiles()).noneMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("test")));

        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT signing secret (app.jwt.secret) must not be empty" +
                    (isProduction ? " in production environment" : ""));
        }

        byte[] activeBytes = decodeSecretBytes(jwtSecret.trim());
        if (activeBytes.length < 32) {
            throw new IllegalStateException("JWT signing secret (app.jwt.secret) must provide at least 256 bits (32 bytes) of entropy. Actual decoded length: " +
                    activeBytes.length + " bytes.");
        }

        if (isProduction && "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".equalsIgnoreCase(jwtSecret.trim())) {
            throw new IllegalStateException("CRITICAL SECURITY FAILURE: Production environment cannot use known development JWT secret placeholder.");
        }

        this.activeSigningKey = Keys.hmacShaKeyFor(activeBytes);
        this.verificationKeys.clear();
        this.verificationKeys.add(this.activeSigningKey);

        if (verificationSecrets != null && !verificationSecrets.trim().isEmpty()) {
            String[] vSecrets = verificationSecrets.split(",");
            for (String vSecret : vSecrets) {
                String trimmed = vSecret.trim();
                if (!trimmed.isEmpty()) {
                    byte[] vBytes = decodeSecretBytes(trimmed);
                    if (vBytes.length >= 32) {
                        if (isProduction && "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".equalsIgnoreCase(trimmed)) {
                            throw new IllegalStateException("CRITICAL SECURITY FAILURE: Production environment cannot use known development JWT secret placeholder in verification secrets.");
                        }
                        this.verificationKeys.add(Keys.hmacShaKeyFor(vBytes));
                    } else {
                        throw new IllegalStateException("Rotated verification secret must provide at least 256 bits (32 bytes) of entropy.");
                    }
                }
            }
        }
        log.info("JwtTokenProvider initialized with 1 active signing key and {} total verification key(s)", verificationKeys.size());
    }

    private byte[] decodeSecretBytes(String secret) {
        try {
            byte[] base64Decoded = Decoders.BASE64.decode(secret);
            if (base64Decoded.length >= 32) {
                return base64Decoded;
            }
        } catch (Exception ignored) {
            // Not valid Base64; fallback to raw UTF-8 bytes
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(activeSigningKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateTokenForUser(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(activeSigningKey, Jwts.SIG.HS256)
                .compact();
    }

    public String getUsernameFromJwt(String token) {
        // Try active key first
        try {
            return parseSubjectWithKey(token, activeSigningKey);
        } catch (SignatureException | SecurityException ex) {
            // Check secondary verification keys
            for (SecretKey key : verificationKeys) {
                if (key != activeSigningKey) {
                    try {
                        return parseSubjectWithKey(token, key);
                    } catch (Exception ignored) {
                    }
                }
            }
            throw ex;
        }
    }

    private String parseSubjectWithKey(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String authToken) {
        // Try active key first
        try {
            Jwts.parser().verifyWith(activeSigningKey).build().parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
            return false;
        } catch (MalformedJwtException ex) {
            log.error("Invalid/Malformed JWT token: {}", ex.getMessage());
            return false;
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
            return false;
        } catch (SignatureException | SecurityException ex) {
            // Signature mismatch with active key; try rotated verification keys
            for (SecretKey secondaryKey : verificationKeys) {
                if (secondaryKey != activeSigningKey) {
                    try {
                        Jwts.parser().verifyWith(secondaryKey).build().parseSignedClaims(authToken);
                        log.debug("JWT successfully validated with a rotated verification key.");
                        return true;
                    } catch (ExpiredJwtException expiredEx) {
                        log.error("Expired rotated JWT token: {}", expiredEx.getMessage());
                        return false;
                    } catch (Exception ignored) {
                        // Continue checking next verification key
                    }
                }
            }
            log.error("JWT signature validation failed across all {} configured keys.", verificationKeys.size());
            return false;
        }
    }

    /**
     * Helper for tests to register verification keys dynamically.
     */
    public void registerVerificationKey(SecretKey key) {
        if (!verificationKeys.contains(key)) {
            verificationKeys.add(key);
        }
    }
}
