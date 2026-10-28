package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("GMoneyTestOnlySigningKeyWithAtLeast32Bytes");

    private static User user(String username) {
        User u = new User();
        u.setUsername(username);
        return u;
    }

    @Test
    @DisplayName("TC-JWT-01 | JwtUtil.generateToken | username alice -> three-part signed JWT")
    void generateToken_format() {
        String token = jwtUtil.generateToken("alice");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("TC-JWT-02 | JwtUtil.generateToken | different users -> different tokens")
    void generateToken_unique() {
        assertNotEquals(jwtUtil.generateToken("alice"), jwtUtil.generateToken("bob"));
    }

    @Test
    @DisplayName("TC-JWT-03 | JwtUtil.generateToken | token subject round-trips to the same username")
    void generateToken_roundTrip() {
        assertEquals("carol", jwtUtil.extractUsername(jwtUtil.generateToken("carol")));
    }

    @Test
    @DisplayName("TC-JWT-04 | JwtUtil.extractUsername | valid token -> subject")
    void extractUsername_valid() {
        assertEquals("alice", jwtUtil.extractUsername(jwtUtil.generateToken("alice")));
    }

    @Test
    @DisplayName("TC-JWT-05 | JwtUtil.extractUsername | tampered signature -> JwtException")
    void extractUsername_tampered() {
        String token = jwtUtil.generateToken("alice");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThrows(JwtException.class, () -> jwtUtil.extractUsername(tampered));
    }

    @Test
    @DisplayName("TC-JWT-06 | JwtUtil.extractUsername | garbage string -> JwtException")
    void extractUsername_garbage() {
        assertThrows(JwtException.class, () -> jwtUtil.extractUsername("not-a-jwt"));
    }

    @Test
    @DisplayName("TC-JWT-07 | JwtUtil.isTokenValid | token for alice checked against alice -> true")
    void isTokenValid_matchingUser() {
        assertTrue(jwtUtil.isTokenValid(jwtUtil.generateToken("alice"), user("alice")));
    }

    @Test
    @DisplayName("TC-JWT-08 | JwtUtil.isTokenValid | token for alice checked against bob -> false")
    void isTokenValid_otherUser() {
        assertFalse(jwtUtil.isTokenValid(jwtUtil.generateToken("alice"), user("bob")));
    }

    @Test
    @DisplayName("TC-JWT-09 | JwtUtil.isTokenValid | case differs (Alice vs alice) -> false")
    void isTokenValid_caseSensitive() {
        assertFalse(jwtUtil.isTokenValid(jwtUtil.generateToken("Alice"), user("alice")));
    }

    @Test
    @DisplayName("TC-JWT-10 | JwtUtil constructor | missing secret -> startup rejected")
    void missingSecretRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtUtil(null)); }
    @Test
    @DisplayName("TC-JWT-11 | JwtUtil constructor | short secret -> startup rejected")
    void shortSecretRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short")); }
    @Test
    @DisplayName("TC-JWT-12 | JwtUtil constructor | different deployment secret -> existing token rejected")
    void differentDeploymentRejectsToken() {
        JwtUtil other = new JwtUtil("DifferentDeploymentKeyAtLeast32BytesLong");
        assertThrows(JwtException.class, () -> other.extractUsername(jwtUtil.generateToken("alice")));
    }

}
