package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtUtil} (no Spring context needed).
 */
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("GMoneyTestOnlySigningKeyWithAtLeast32Bytes");

    /**
     * Builds a user with only a username, as {@code UserDetails}.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param username login name
     * @return the user
     */
    private static User user(String username) {
        User u = new User();
        u.setUsername(username);
        return u;
    }

    /**
     * TC-JWT-01: tests {@code JwtUtil.generateToken}.
     *
     * <p><b>Pre-condition:</b> Username alice.</p>
     * <p><b>Post-condition:</b> Three-part signed JWT.</p>
     */
    @Test
    @DisplayName("TC-JWT-01 | JwtUtil.generateToken | username alice -> three-part signed JWT")
    void generateToken_format() {
        String token = jwtUtil.generateToken("alice");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    /**
     * TC-JWT-02: tests {@code JwtUtil.generateToken}.
     *
     * <p><b>Pre-condition:</b> Different users.</p>
     * <p><b>Post-condition:</b> Different tokens.</p>
     */
    @Test
    @DisplayName("TC-JWT-02 | JwtUtil.generateToken | different users -> different tokens")
    void generateToken_unique() {
        assertNotEquals(jwtUtil.generateToken("alice"), jwtUtil.generateToken("bob"));
    }

    /**
     * TC-JWT-03: tests {@code JwtUtil.generateToken}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Token subject round-trips to the same username.</p>
     */
    @Test
    @DisplayName("TC-JWT-03 | JwtUtil.generateToken | token subject round-trips to the same username")
    void generateToken_roundTrip() {
        assertEquals("carol", jwtUtil.extractUsername(jwtUtil.generateToken("carol")));
    }

    /**
     * TC-JWT-04: tests {@code JwtUtil.extractUsername}.
     *
     * <p><b>Pre-condition:</b> Valid token.</p>
     * <p><b>Post-condition:</b> Subject.</p>
     */
    @Test
    @DisplayName("TC-JWT-04 | JwtUtil.extractUsername | valid token -> subject")
    void extractUsername_valid() {
        assertEquals("alice", jwtUtil.extractUsername(jwtUtil.generateToken("alice")));
    }

    /**
     * TC-JWT-05: tests {@code JwtUtil.extractUsername}.
     *
     * <p><b>Pre-condition:</b> Tampered signature.</p>
     * <p><b>Post-condition:</b> JwtException.</p>
     */
    @Test
    @DisplayName("TC-JWT-05 | JwtUtil.extractUsername | tampered signature -> JwtException")
    void extractUsername_tampered() {
        String token = jwtUtil.generateToken("alice");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThrows(JwtException.class, () -> jwtUtil.extractUsername(tampered));
    }

    /**
     * TC-JWT-06: tests {@code JwtUtil.extractUsername}.
     *
     * <p><b>Pre-condition:</b> Garbage string.</p>
     * <p><b>Post-condition:</b> JwtException.</p>
     */
    @Test
    @DisplayName("TC-JWT-06 | JwtUtil.extractUsername | garbage string -> JwtException")
    void extractUsername_garbage() {
        assertThrows(JwtException.class, () -> jwtUtil.extractUsername("not-a-jwt"));
    }

    /**
     * TC-JWT-07: tests {@code JwtUtil.isTokenValid}.
     *
     * <p><b>Pre-condition:</b> Token for alice checked against alice.</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-JWT-07 | JwtUtil.isTokenValid | token for alice checked against alice -> true")
    void isTokenValid_matchingUser() {
        assertTrue(jwtUtil.isTokenValid(jwtUtil.generateToken("alice"), user("alice")));
    }

    /**
     * TC-JWT-08: tests {@code JwtUtil.isTokenValid}.
     *
     * <p><b>Pre-condition:</b> Token for alice checked against bob.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-JWT-08 | JwtUtil.isTokenValid | token for alice checked against bob -> false")
    void isTokenValid_otherUser() {
        assertFalse(jwtUtil.isTokenValid(jwtUtil.generateToken("alice"), user("bob")));
    }

    /**
     * TC-JWT-09: tests {@code JwtUtil.isTokenValid}.
     *
     * <p><b>Pre-condition:</b> Case differs (Alice vs alice).</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-JWT-09 | JwtUtil.isTokenValid | case differs (Alice vs alice) -> false")
    void isTokenValid_caseSensitive() {
        assertFalse(jwtUtil.isTokenValid(jwtUtil.generateToken("Alice"), user("alice")));
    }

    /**
     * TC-JWT-10: tests {@code JwtUtil constructor}.
     *
     * <p><b>Pre-condition:</b> Missing secret.</p>
     * <p><b>Post-condition:</b> Startup rejected.</p>
     */
    @Test
    @DisplayName("TC-JWT-10 | JwtUtil constructor | missing secret -> startup rejected")
    void missingSecretRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtUtil(null)); }
    /**
     * TC-JWT-11: tests {@code JwtUtil constructor}.
     *
     * <p><b>Pre-condition:</b> Short secret.</p>
     * <p><b>Post-condition:</b> Startup rejected.</p>
     */
    @Test
    @DisplayName("TC-JWT-11 | JwtUtil constructor | short secret -> startup rejected")
    void shortSecretRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short")); }
    /**
     * TC-JWT-12: tests {@code JwtUtil constructor}.
     *
     * <p><b>Pre-condition:</b> Different deployment secret.</p>
     * <p><b>Post-condition:</b> Existing token rejected.</p>
     */
    @Test
    @DisplayName("TC-JWT-12 | JwtUtil constructor | different deployment secret -> existing token rejected")
    void differentDeploymentRejectsToken() {
        JwtUtil other = new JwtUtil("DifferentDeploymentKeyAtLeast32BytesLong");
        assertThrows(JwtException.class, () -> other.extractUsername(jwtUtil.generateToken("alice")));
    }

}
