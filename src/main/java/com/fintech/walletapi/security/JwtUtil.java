package com.fintech.walletapi.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;

import java.security.Key;
import java.util.Date;

/**
 * Creates and checks the JSON Web Tokens (HS256) that GMoney issues on login.
 */
@Component
public class JwtUtil {

    /** Deployment-specific signing key, supplied through configuration. */
    private final Key signingKey;

    /**
     * Loads the configured signing secret and rejects missing or weak secrets at startup.
     * <p><b>Pre-condition:</b> secret is a private, random value of at least 32 UTF-8 bytes.</p>
     * <p><b>Post-condition:</b> tokens use only this deployment's signing key.</p>
     * @param secret value of gmoney.jwt.secret (normally JWT_SECRET)
     * @throws IllegalArgumentException if the secret is missing or too short
     */
    public JwtUtil(@Value("${gmoney.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Tokens are valid for one hour. */
    private static final long EXPIRATION_TIME = 1000L * 60 * 60;

    /**
     * Builds the HMAC signing key from the secret.
     *
     * <p><b>Pre-condition:</b> the secret is at least 32 bytes.
     * <b>Post-condition:</b> no state change.</p>
     *
     * @return the signing key
     */
    private Key getSigningKey() {
        return signingKey;
    }

    /**
     * Issues a signed token for a user.
     *
     * <p><b>Pre-condition:</b> the user's credentials were already verified.</p>
     * <p><b>Post-condition:</b> a token whose subject is {@code username} and that expires in one hour is returned.</p>
     *
     * @param username the subject of the token
     * @return the compact JWT string
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Reads the username (subject) from a token.
     *
     * <p><b>Pre-condition:</b> {@code token} was signed with this application's key and is not expired.</p>
     * <p><b>Post-condition:</b> no state change.</p>
     *
     * @param token the compact JWT
     * @return the subject
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with or expired
     * @throws IllegalArgumentException     if the token is {@code null} or empty
     */
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * Checks that a token belongs to the given user and has not expired.
     *
     * <p><b>Pre-condition:</b> {@code token} is a well-formed token signed with this application's key.</p>
     * <p><b>Post-condition:</b> no state change.</p>
     *
     * @param token       the compact JWT
     * @param userDetails the user the token should belong to
     * @return {@code true} if the subject matches and the token is still valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Checks whether a token's expiry time has passed.
     *
     * <p><b>Pre-condition:</b> {@code token} is well-formed.
     * <b>Post-condition:</b> no state change.</p>
     *
     * @param token the compact JWT
     * @return {@code true} if the token is expired
     */
    private boolean isTokenExpired(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getExpiration().before(new Date());
    }
}
