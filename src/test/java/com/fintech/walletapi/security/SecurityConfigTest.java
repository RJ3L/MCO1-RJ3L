package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private AuthenticationProvider authenticationProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private void saveUser(String username, String rawPassword, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userRepository.save(user);
    }

    @Test
    @DisplayName("TC-SEC-01 | SecurityConfig.securityFilterChain | /actuator/health without token -> 200 (public)")
    void chain_healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-02 | SecurityConfig.securityFilterChain | /api/v1/forex/rates without token -> 401 (protected)")
    void chain_protectedNeedsToken() throws Exception {
        mockMvc.perform(get("/api/v1/forex/rates")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-03 | SecurityConfig.securityFilterChain | customer token on admin-only POST /forex/rates -> 403")
    void chain_customerBlockedFromAdminEndpoint() throws Exception {
        saveUser("sec-customer", "pw", "ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/forex/rates")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("sec-customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"GBP\",\"quoteCurrency\":\"PHP\",\"rate\":74}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-04 | SecurityConfig.securityFilterChain | admin token on POST /forex/rates -> 201")
    void chain_adminAllowed() throws Exception {
        saveUser("sec-admin", "pw", "ROLE_ADMIN");
        mockMvc.perform(post("/api/v1/forex/rates")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("sec-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"GBP\",\"quoteCurrency\":\"USD\",\"rate\":1.27}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-SEC-05 | SecurityConfig.passwordEncoder | hash is not the plain text and matches it")
    void encoder_hashesAndMatches() {
        String hash = passwordEncoder.encode("password123");
        assertNotEquals("password123", hash);
        assertTrue(passwordEncoder.matches("password123", hash));
    }

    @Test
    @DisplayName("TC-SEC-06 | SecurityConfig.passwordEncoder | wrong password does not match")
    void encoder_wrongPassword() {
        assertFalse(passwordEncoder.matches("wrong", passwordEncoder.encode("password123")));
    }

    @Test
    @DisplayName("TC-SEC-07 | SecurityConfig.passwordEncoder | same password hashed twice -> different salts")
    void encoder_salted() {
        assertNotEquals(passwordEncoder.encode("same"), passwordEncoder.encode("same"));
    }

    @Test
    @DisplayName("TC-SEC-08 | SecurityConfig.authenticationManager | correct credentials -> authenticated")
    void manager_valid() {
        saveUser("sec-alice", "secret", "ROLE_CUSTOMER");
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("sec-alice", "secret"));
        assertTrue(auth.isAuthenticated());
        assertEquals("sec-alice", auth.getName());
    }

    @Test
    @DisplayName("TC-SEC-09 | SecurityConfig.authenticationManager | wrong password -> BadCredentialsException")
    void manager_wrongPassword() {
        saveUser("sec-bob", "secret", "ROLE_CUSTOMER");
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("sec-bob", "nope")));
    }

    @Test
    @DisplayName("TC-SEC-10 | SecurityConfig.authenticationManager | unknown user -> BadCredentialsException (no user enumeration)")
    void manager_unknownUser() {
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("nobody-here", "x")));
    }

    @Test
    @DisplayName("TC-SEC-11 | SecurityConfig.authenticationProvider | supports username/password tokens")
    void provider_supports() {
        assertTrue(authenticationProvider.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("TC-SEC-12 | SecurityConfig.authenticationProvider | valid admin credentials -> ROLE_ADMIN authority")
    void provider_roles() {
        saveUser("sec-root", "pw", "ROLE_ADMIN");
        Authentication auth = authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("sec-root", "pw"));
        assertEquals("ROLE_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("TC-SEC-13 | SecurityConfig.authenticationProvider | wrong password -> BadCredentialsException")
    void provider_wrongPassword() {
        saveUser("sec-carol", "pw", "ROLE_CUSTOMER");
        assertThrows(BadCredentialsException.class, () -> authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken("sec-carol", "bad")));
    }

    @Test
    @DisplayName("TC-SEC-14 | SecurityConfig.securityFilterChain | /v3/api-docs without token -> 200 with JWT bearer scheme")
    void chain_apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("TC-SEC-15 | SecurityConfig.securityFilterChain | /swagger-ui/index.html without token -> 200 (public)")
    void chain_swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-16 | SecurityConfig.securityFilterChain | customer token on admin-only POST /forex/rates/sync -> 403")
    void chain_customerBlockedFromRateSync() throws Exception {
        saveUser("sec-syncer", "pw", "ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/forex/rates/sync")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("sec-syncer")))
                .andExpect(status().isForbidden());
    }
}
