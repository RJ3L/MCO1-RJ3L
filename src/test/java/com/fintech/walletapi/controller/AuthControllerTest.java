package com.fintech.walletapi.controller;

import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.UserRepository;
import com.fintech.walletapi.security.CustomUserDetailsService;
import com.fintech.walletapi.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link AuthController}.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static final String ALICE_REGISTRATION = "{\"username\":\"alice\",\"password\":\"password123\","
            + "\"fullName\":\"Alice Johnson\",\"email\":\"alice@example.com\",\"phone\":\"09171234567\"}";

    /**
     * TC-AUTHCTL-01: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> New username.</p>
     * <p><b>Post-condition:</b> 201, password hashed, ROLE_CUSTOMER with profile.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-01 | AuthController.register | new username -> 201, password hashed, ROLE_CUSTOMER with profile")
    void register_ok() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hash");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        verify(userRepository).save(saved.capture());
        assertEquals("$2a$hash", saved.getValue().getPassword());
        assertEquals("ROLE_CUSTOMER", saved.getValue().getRole());
        assertEquals("Alice Johnson", saved.getValue().getCustomer().getFullName());
    }

    /**
     * TC-AUTHCTL-02: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Username taken.</p>
     * <p><b>Post-condition:</b> 409 'Username already exists'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-02 | AuthController.register | username taken -> 409 'Username already exists'")
    void register_duplicate() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
        verify(userRepository, never()).save(any());
    }

    /**
     * TC-AUTHCTL-03: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Invalid e-mail.</p>
     * <p><b>Post-condition:</b> 400 'Email must be a valid email address'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-03 | AuthController.register | invalid e-mail -> 400 'Email must be a valid email address'")
    void register_badEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"p\",\"fullName\":\"X\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email must be a valid email address"));
    }

    /**
     * TC-AUTHCTL-04: tests {@code AuthController.registerAdmin}.
     *
     * <p><b>Pre-condition:</b> Admin creates admin.</p>
     * <p><b>Post-condition:</b> 201, ROLE_ADMIN saved.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-04 | AuthController.registerAdmin | admin creates admin -> 201, ROLE_ADMIN saved")
    void registerAdmin_ok() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isCreated());

        verify(userRepository).save(saved.capture());
        assertEquals("ROLE_ADMIN", saved.getValue().getRole());
    }

    /**
     * TC-AUTHCTL-05: tests {@code AuthController.registerAdmin}.
     *
     * <p><b>Pre-condition:</b> Username taken.</p>
     * <p><b>Post-condition:</b> 409.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-05 | AuthController.registerAdmin | username taken -> 409")
    void registerAdmin_duplicate() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isConflict());
    }

    /**
     * TC-AUTHCTL-06: tests {@code AuthController.registerAdmin}.
     *
     * <p><b>Pre-condition:</b> Missing password.</p>
     * <p><b>Post-condition:</b> 400 'Password is required'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-06 | AuthController.registerAdmin | missing password -> 400 'Password is required'")
    void registerAdmin_missingPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"fullName\":\"X\",\"email\":\"x@x.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is required"));
    }

    /**
     * TC-AUTHCTL-07: tests {@code AuthController.login}.
     *
     * <p><b>Pre-condition:</b> Correct credentials.</p>
     * <p><b>Post-condition:</b> 200 with token in data.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-07 | AuthController.login | correct credentials -> 200 with token in data")
    void login_ok() throws Exception {
        when(jwtUtil.generateToken("alice")).thenReturn("jwt-token");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data").value("jwt-token"));
    }

    /**
     * TC-AUTHCTL-08: tests {@code AuthController.login}.
     *
     * <p><b>Pre-condition:</b> Wrong password.</p>
     * <p><b>Post-condition:</b> 401 'Invalid username or password', no token.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-08 | AuthController.login | wrong password -> 401 'Invalid username or password', no token")
    void login_badCredentials() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        verify(jwtUtil, never()).generateToken(any());
    }

    /**
     * TC-AUTHCTL-09: tests {@code AuthController.login}.
     *
     * <p><b>Pre-condition:</b> Blank username.</p>
     * <p><b>Post-condition:</b> 400 'Username is required'.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-09 | AuthController.login | blank username -> 400 'Username is required'")
    void login_blankUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is required"));
    }


    /**
     * Builds a registration JSON body with an optional phone fragment.
     *
     * <p><b>Pre-condition:</b> {@code phoneJson} is empty or a JSON fragment starting with a comma.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param username login name; also used for the e-mail
     * @param phoneJson for example {@code ,"phone":"12345"}, or empty
     * @return the request body
     */
    private static String registration(String username, String phoneJson) {
        return "{\"username\":\"" + username + "\",\"password\":\"password123\",\"fullName\":\"Phone Tester\","
                + "\"email\":\"" + username + "@example.com\"" + phoneJson + "}";
    }

    /**
     * TC-AUTHCTL-10: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Phone +639171234567.</p>
     * <p><b>Post-condition:</b> 201, phone saved on the profile.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-10 | AuthController.register | phone +639171234567 -> 201, phone saved on the profile")
    void register_internationalPhone() throws Exception {
        when(userRepository.findByUsername("phoneplus")).thenReturn(Optional.empty());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("phoneplus", ",\"phone\":\"+639171234567\"")))
                .andExpect(status().isCreated());

        verify(userRepository).save(saved.capture());
        assertEquals("+639171234567", saved.getValue().getCustomer().getPhone());
    }

    /**
     * TC-AUTHCTL-11: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> No phone given.</p>
     * <p><b>Post-condition:</b> 201 (phone is optional).</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-11 | AuthController.register | no phone given -> 201 (phone is optional)")
    void register_withoutPhone() throws Exception {
        when(userRepository.findByUsername("nophone")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("nophone", "")))
                .andExpect(status().isCreated());
        verify(userRepository).save(any(User.class));
    }

    /**
     * TC-AUTHCTL-12: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Phone '12345' (too short).</p>
     * <p><b>Post-condition:</b> 400 with phone message, nothing saved.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-12 | AuthController.register | phone '12345' (too short) -> 400 with phone message, nothing saved")
    void register_shortPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("shortphone", ",\"phone\":\"12345\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
        verify(userRepository, never()).save(any());
    }

    /**
     * TC-AUTHCTL-13: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Phone '0917-123-4567' (dashes).</p>
     * <p><b>Post-condition:</b> 400.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-13 | AuthController.register | phone '0917-123-4567' (dashes) -> 400")
    void register_dashedPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("dashphone", ",\"phone\":\"0917-123-4567\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
    }

    /**
     * TC-AUTHCTL-14: tests {@code AuthController.register}.
     *
     * <p><b>Pre-condition:</b> Swagger placeholder phone 'string'.</p>
     * <p><b>Post-condition:</b> 400.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-AUTHCTL-14 | AuthController.register | Swagger placeholder phone 'string' -> 400")
    void register_placeholderPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("placeholder", ",\"phone\":\"string\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
    }

    /**
     * TC-AUTHCTL-15: tests {@code AuthController.registerAdmin}.
     *
     * <p><b>Pre-condition:</b> Phone '0817123456' (wrong prefix).</p>
     * <p><b>Post-condition:</b> 400, nothing saved.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-15 | AuthController.registerAdmin | phone '0817123456' (wrong prefix) -> 400, nothing saved")
    void registerAdmin_badPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("badadmin", ",\"phone\":\"0817123456\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
        verify(userRepository, never()).save(any());
    }
}
