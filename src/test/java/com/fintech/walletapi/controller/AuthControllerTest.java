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

    @Test
    @DisplayName("TC-AUTHCTL-02 | AuthController.register | username taken -> 409 'Username already exists'")
    void register_duplicate() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-AUTHCTL-03 | AuthController.register | invalid e-mail -> 400 'Email must be a valid email address'")
    void register_badEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"p\",\"fullName\":\"X\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email must be a valid email address"));
    }

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

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-05 | AuthController.registerAdmin | username taken -> 409")
    void registerAdmin_duplicate() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User()));
        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON).content(ALICE_REGISTRATION))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-AUTHCTL-06 | AuthController.registerAdmin | missing password -> 400 'Password is required'")
    void registerAdmin_missingPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/admin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"fullName\":\"X\",\"email\":\"x@x.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is required"));
    }

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

    @Test
    @DisplayName("TC-AUTHCTL-09 | AuthController.login | blank username -> 400 'Username is required'")
    void login_blankUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is required"));
    }

    private static String registration(String username, String phoneJson) {
        return "{\"username\":\"" + username + "\",\"password\":\"password123\",\"fullName\":\"Phone Tester\","
                + "\"email\":\"" + username + "@example.com\"" + phoneJson + "}";
    }

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

    @Test
    @DisplayName("TC-AUTHCTL-11 | AuthController.register | no phone given -> 201 (phone is optional)")
    void register_withoutPhone() throws Exception {
        when(userRepository.findByUsername("nophone")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("nophone", "")))
                .andExpect(status().isCreated());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("TC-AUTHCTL-12 | AuthController.register | phone '12345' (too short) -> 400 with phone message, nothing saved")
    void register_shortPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("shortphone", ",\"phone\":\"12345\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-AUTHCTL-13 | AuthController.register | phone '0917-123-4567' (dashes) -> 400")
    void register_dashedPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("dashphone", ",\"phone\":\"0917-123-4567\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
    }

    @Test
    @DisplayName("TC-AUTHCTL-14 | AuthController.register | Swagger placeholder phone 'string' -> 400")
    void register_placeholderPhone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("placeholder", ",\"phone\":\"string\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone must be a Philippine mobile number such as 09171234567 or +639171234567"));
    }

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

