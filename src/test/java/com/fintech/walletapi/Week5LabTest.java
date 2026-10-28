package com.fintech.walletapi;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcConfigurerAdapter;
import org.springframework.web.context.WebApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Week 5 Auto-Grading Test Suite — Spring Security + JWT
 *
 * Architecture:
 *  - @TestInstance(PER_CLASS)  → single instance, instance fields shared across @Test methods
 *  - @TestMethodOrder(OrderAnnotation) → deterministic run order
 *  - H2 state persists across tests within the class (no @DirtiesContext per method)
 *
 * Test progression (@Order applies within each @Nested group; groups that need
 * alice's JWT register/login her themselves, so group run order does not matter):
 *  Orders 1-7   → Step 2: User entity & UserRepository (reflection)
 *  Orders 10-26 → Steps 3-7: security classes, SecurityConfig, auth DTOs (reflection)
 *  Orders 30-33 → Step 8: register (201 / 409) and login (JWT / 401)
 *  Orders 40-43 → security enforcement (401 JSON without a valid token, /auth/** public)
 *  Orders 50-54 → Step 9: /me endpoint
 *  Orders 55-57 → Step 10: GlobalExceptionHandler security handlers
 *  Order  58    → Step 11: AccountDTO.username
 *  Orders 60-66 → Step 12: Method Security (@Pre/@PostAuthorize)
 *  Orders 67-69 → Step 12.4: createAccount ownership rule (@accountSecurity.isOwnCustomer)
 *  Orders 73-77 → Step 12.3: remaining Admin-only operations (list, PUT, DELETE, create customer)
 *                 (67-69 and 73-77 live in the same Step 12 group as 60-66)
 *  Orders 70-72 → Challenge: /auth/register/admin endpoint
 *                 (optional — tagged "bonus", scored separately, never lowers the core score)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week5LabTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class WithMockUserSupport {
        /**
         * Registers a MockMvc customizer so every request carries the test security context.
         *
         * <p><b>Pre-condition:</b> The nested test configuration is being loaded by Spring.</p>
         * <p><b>Post-condition:</b> MockMvc applies {@code testSecurityContext()} to each request, so {@code
         * @WithMockUser} reaches the filter chain.</p>
         *
         * @return the customizer bean
         */
        @Bean
        MockMvcBuilderCustomizer testSecurityContextCustomizer() {
            return builder -> builder.apply(new MockMvcConfigurerAdapter() {
                /**
                 * Supplies the request post-processor that copies the test security context into each
                 * request.
                 *
                 * <p><b>Pre-condition:</b> MockMvc is being built for the lab suite.</p>
                 * <p><b>Post-condition:</b> Every request is processed with the current test security
                 * context.</p>
                 *
                 * @param mockMvcBuilder the builder being configured
                 * @param context the web application context
                 * @return the post-processor applying {@code testSecurityContext()}
                 */
                @Override
                public RequestPostProcessor beforeMockMvcCreated(
                        ConfigurableMockMvcBuilder<?> mockMvcBuilder, WebApplicationContext context) {
                    return SecurityMockMvcRequestPostProcessors.testSecurityContext();
                }
            });
        }
    }

    private String aliceToken;
    private String bobToken;
    private Long   aliceCustomerId;
    private Long   aliceAccountId;

    /**
     * Registers a customer through {@code POST /api/v1/auth/register}.
     *
     * <p><b>Pre-condition:</b> MockMvc is ready; the username may or may not already exist.</p>
     * <p><b>Post-condition:</b> The user exists unless the username was already taken; nothing is asserted
     * here.</p>
     *
     * @param username login name
     * @param password raw password
     * @param fullName profile name
     * @param email profile e-mail
     * @param phone profile phone number
     * @return the raw JSON response body
     * @throws Exception if the MockMvc request fails
     */
    private String register(String username, String password, String fullName,
                            String email, String phone) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                            "{\"username\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\"," +
                            "\"email\":\"%s\",\"phone\":\"%s\"}",
                            username, password, fullName, email, phone)))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Logs in through {@code POST /api/v1/auth/login} and extracts the JWT.
     *
     * <p><b>Pre-condition:</b> MockMvc is ready.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param username login name
     * @param password raw password
     * @return the token from {@code data}, or {@code null} if the login failed
     * @throws Exception if the MockMvc request fails
     */
    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                            "{\"username\":\"%s\",\"password\":\"%s\"}",
                            username, password)))
                .andReturn().getResponse().getContentAsString();
        if (response.contains("\"data\"")) {
            String raw = response.replaceAll(".*\"data\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            return raw.startsWith("{") ? null : raw;
        }
        return null;
    }

    /**
     * Registers and logs in alice once, caching her token for later checks.
     *
     * <p><b>Pre-condition:</b> The shared test context is loaded.</p>
     * <p><b>Post-condition:</b> {@code aliceToken} holds a valid JWT for alice.</p>
     *
     * @throws Exception if the MockMvc request fails
     */
    private void ensureAliceSetup() throws Exception {
        if (aliceToken != null) return;

        register("alice", "password123", "Alice Johnson", "alice@example.com", "09171234567");

        aliceToken = login("alice", "password123");
        assertNotNull(aliceToken, "Login must return a JWT token in $.data");
    }

    /**
     * Reads a numeric field from a JSON string.
     *
     * <p><b>Pre-condition:</b> {@code json} is a JSON document or {@code null}-free text.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param json the JSON text
     * @param field the field name to read
     * @return the first value of that field, or {@code null} if it is missing or not numeric
     */
    private Long extractLong(String json, String field) {
        try {
            String pattern = "\"" + field + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Checks by reflection whether a field carries an annotation.
     *
     * <p><b>Pre-condition:</b> {@code clazz} is loaded.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param clazz the class declaring the field
     * @param fieldName the field name
     * @param simpleAnnotationName the annotation's simple name, e.g. {@code Column}
     * @return {@code true} if the field exists and has that annotation
     */
    private boolean fieldHasAnnotation(Class<?> clazz, String fieldName, String simpleAnnotationName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            for (Annotation a : f.getAnnotations()) {
                if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
            }
        } catch (NoSuchFieldException ignored) {}
        return false;
    }

    /**
     * Checks by reflection whether a class carries an annotation.
     *
     * <p><b>Pre-condition:</b> {@code cls} is loaded.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param cls the class to inspect
     * @param simpleAnnotationName the annotation's simple name
     * @return {@code true} if the class has that annotation
     */
    private boolean classHasAnnotation(Class<?> cls, String simpleAnnotationName) {
        for (Annotation a : cls.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
        }
        return false;
    }

    /**
     * Checks by reflection whether a method carries an annotation.
     *
     * <p><b>Pre-condition:</b> {@code m} belongs to a loaded class.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param m the method to inspect
     * @param simpleAnnotationName the annotation's simple name
     * @return {@code true} if the method has that annotation
     */
    private boolean methodHasAnnotation(Method m, String simpleAnnotationName) {
        for (Annotation a : m.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
        }
        return false;
    }

    @Nested
    @DisplayName("Step 2: User Entity & Repository")
    class Step2UserEntity {

        /**
         * Course Week 5 lab check: User entity exists and is annotated with @Entity.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> User entity exists and is annotated with @Entity holds.</p>
         */
        @Test
        @Order(1)
        @DisplayName("User entity exists and is annotated with @Entity")
        void userEntityExists() {
            try {
                Class<?> user = Class.forName("com.fintech.walletapi.entity.User");
                assertNotNull(user.getAnnotation(jakarta.persistence.Entity.class),
                        "User must be annotated with @Entity");
            } catch (ClassNotFoundException e) {
                fail("User entity not found — create it at com.fintech.walletapi.entity.User");
            }
        }

        /**
         * Course Week 5 lab check: User is mapped to table named 'users'.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> User is mapped to table named 'users' holds.</p>
         */
        @Test
        @Order(2)
        @DisplayName("User is mapped to table named 'users'")
        void userMappedToUsersTable() {
            try {
                Class<?> user = Class.forName("com.fintech.walletapi.entity.User");
                jakarta.persistence.Table table = user.getAnnotation(jakarta.persistence.Table.class);
                assertNotNull(table, "User must have @Table annotation");
                assertEquals("users", table.name(), "@Table(name) must be \"users\"");
            } catch (ClassNotFoundException e) {
                fail("User entity not found");
            }
        }

        /**
         * Course Week 5 lab check: User implements Spring Security's UserDetails interface.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> User implements Spring Security's UserDetails interface holds.</p>
         */
        @Test
        @Order(3)
        @DisplayName("User implements Spring Security's UserDetails interface")
        void userImplementsUserDetails() {
            try {
                Class<?> user = Class.forName("com.fintech.walletapi.entity.User");
                boolean implementsUserDetails = java.util.Arrays.stream(user.getInterfaces())
                        .anyMatch(i -> i.getSimpleName().equals("UserDetails"));
                assertTrue(implementsUserDetails,
                        "User must implement org.springframework.security.core.userdetails.UserDetails");
            } catch (ClassNotFoundException e) {
                fail("User entity not found");
            }
        }

        /**
         * Course Week 5 lab check: User has a 'role' field (String).
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> User has a 'role' field (String) holds.</p>
         */
        @Test
        @Order(4)
        @DisplayName("User has a 'role' field (String)")
        void userHasRoleField() {
            try {
                Class<?> user = Class.forName("com.fintech.walletapi.entity.User");
                Field f = user.getDeclaredField("role");
                assertEquals(String.class, f.getType(), "User.role must be of type String");
            } catch (ClassNotFoundException e) {
                fail("User entity not found");
            } catch (NoSuchFieldException e) {
                fail("User must have a 'role' field (e.g. \"ROLE_CUSTOMER\" or \"ROLE_ADMIN\")");
            }
        }

        /**
         * Course Week 5 lab check: User has @OneToOne relationship to Customer.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> User has @OneToOne relationship to Customer holds.</p>
         */
        @Test
        @Order(5)
        @DisplayName("User has @OneToOne relationship to Customer")
        void userHasOneToOneCustomer() {
            try {
                Class<?> user = Class.forName("com.fintech.walletapi.entity.User");
                boolean hasOneToOne = java.util.Arrays.stream(user.getDeclaredFields())
                        .anyMatch(f -> f.isAnnotationPresent(jakarta.persistence.OneToOne.class));
                assertTrue(hasOneToOne, "User must have a @OneToOne field linking to Customer");
            } catch (ClassNotFoundException e) {
                fail("User entity not found");
            }
        }

        /**
         * Course Week 5 lab check: Customer entity has @OneToOne(mappedBy) back-reference to User.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> Customer entity has @OneToOne(mappedBy) back-reference to User holds.</p>
         */
        @Test
        @Order(6)
        @DisplayName("Customer entity has @OneToOne(mappedBy) back-reference to User")
        void customerHasMappedByUser() {
            try {
                Class<?> customer = Class.forName("com.fintech.walletapi.entity.Customer");
                boolean hasMappedBy = java.util.Arrays.stream(customer.getDeclaredFields())
                        .anyMatch(f -> {
                            jakarta.persistence.OneToOne oto =
                                f.getAnnotation(jakarta.persistence.OneToOne.class);
                            return oto != null && oto.mappedBy() != null && !oto.mappedBy().isEmpty();
                        });
                assertTrue(hasMappedBy,
                        "Customer must have @OneToOne(mappedBy = \"customer\") back-reference to User");
            } catch (ClassNotFoundException e) {
                fail("Customer entity not found");
            }
        }

        /**
         * Course Week 5 lab check: UserRepository interface exists with findByUsername derived query.
         *
         * <p><b>Pre-condition:</b> Step 2: User Entity &amp; Repository; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> UserRepository interface exists with findByUsername derived query
         * holds.</p>
         */
        @Test
        @Order(7)
        @DisplayName("UserRepository interface exists with findByUsername derived query")
        void userRepositoryExists() {
            try {
                Class<?> repo = Class.forName("com.fintech.walletapi.repository.UserRepository");
                assertTrue(repo.isInterface(), "UserRepository must be an interface");
                repo.getMethod("findByUsername", String.class);
            } catch (ClassNotFoundException e) {
                fail("UserRepository not found at com.fintech.walletapi.repository.UserRepository");
            } catch (NoSuchMethodException e) {
                fail("UserRepository must have: Optional<User> findByUsername(String username)");
            }
        }
    }

    @Nested
    @DisplayName("Step 3: CustomUserDetailsService")
    class Step3UserDetailsService {

        /**
         * Course Week 5 lab check: CustomUserDetailsService exists in security package.
         *
         * <p><b>Pre-condition:</b> Step 3: CustomUserDetailsService; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> CustomUserDetailsService exists in security package holds.</p>
         */
        @Test
        @Order(10)
        @DisplayName("CustomUserDetailsService exists in security package")
        void customUserDetailsServiceExists() {
            try {
                Class.forName("com.fintech.walletapi.security.CustomUserDetailsService");
            } catch (ClassNotFoundException e) {
                fail("CustomUserDetailsService not found — create it in com.fintech.walletapi.security");
            }
        }

        /**
         * Course Week 5 lab check: CustomUserDetailsService implements UserDetailsService.
         *
         * <p><b>Pre-condition:</b> Step 3: CustomUserDetailsService; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> CustomUserDetailsService implements UserDetailsService holds.</p>
         */
        @Test
        @Order(11)
        @DisplayName("CustomUserDetailsService implements UserDetailsService")
        void customUserDetailsServiceImplementsInterface() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.CustomUserDetailsService");
                boolean implements_ = java.util.Arrays.stream(cls.getInterfaces())
                        .anyMatch(i -> i.getSimpleName().equals("UserDetailsService"));
                assertTrue(implements_,
                        "CustomUserDetailsService must implement org.springframework.security.core.userdetails.UserDetailsService");
            } catch (ClassNotFoundException e) {
                fail("CustomUserDetailsService not found");
            }
        }
    }

    @Nested
    @DisplayName("Step 4: JWT Utilities")
    class Step4JwtUtils {

        /**
         * Course Week 5 lab check: JwtUtil exists in security package and has generateToken method.
         *
         * <p><b>Pre-condition:</b> Step 4: JWT Utilities; the shared test context is loaded and earlier
         * checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> JwtUtil exists in security package and has generateToken method
         * holds.</p>
         */
        @Test
        @Order(15)
        @DisplayName("JwtUtil exists in security package and has generateToken method")
        void jwtUtilExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.JwtUtil");
                cls.getMethod("generateToken", String.class);
            } catch (ClassNotFoundException e) {
                fail("JwtUtil not found — create it at com.fintech.walletapi.security.JwtUtil");
            } catch (NoSuchMethodException e) {
                fail("JwtUtil must have: public String generateToken(String username)");
            }
        }

        /**
         * Course Week 5 lab check: JwtAuthenticationFilter exists in security package.
         *
         * <p><b>Pre-condition:</b> Step 4: JWT Utilities; the shared test context is loaded and earlier
         * checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> JwtAuthenticationFilter exists in security package holds.</p>
         */
        @Test
        @Order(16)
        @DisplayName("JwtAuthenticationFilter exists in security package")
        void jwtAuthFilterExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.JwtAuthenticationFilter");
                assertNotNull(cls);
            } catch (ClassNotFoundException e) {
                fail("JwtAuthenticationFilter not found — create it at com.fintech.walletapi.security.JwtAuthenticationFilter");
            }
        }
    }

    @Nested
    @DisplayName("Step 5: Custom Security Error Handlers")
    class Step5ErrorHandlers {

        /**
         * Course Week 5 lab check: CustomAuthenticationEntryPoint exists and implements
         * AuthenticationEntryPoint.
         *
         * <p><b>Pre-condition:</b> Step 5: Custom Security Error Handlers; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> CustomAuthenticationEntryPoint exists and implements
         * AuthenticationEntryPoint holds.</p>
         */
        @Test
        @Order(18)
        @DisplayName("CustomAuthenticationEntryPoint exists and implements AuthenticationEntryPoint")
        void authEntryPointExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.CustomAuthenticationEntryPoint");
                boolean impl = java.util.Arrays.stream(cls.getInterfaces())
                        .anyMatch(i -> i.getSimpleName().equals("AuthenticationEntryPoint"));
                assertTrue(impl, "CustomAuthenticationEntryPoint must implement AuthenticationEntryPoint");
            } catch (ClassNotFoundException e) {
                fail("CustomAuthenticationEntryPoint not found in com.fintech.walletapi.security");
            }
        }

        /**
         * Course Week 5 lab check: CustomAccessDeniedHandler exists and implements AccessDeniedHandler.
         *
         * <p><b>Pre-condition:</b> Step 5: Custom Security Error Handlers; the shared test context is loaded
         * and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> CustomAccessDeniedHandler exists and implements AccessDeniedHandler
         * holds.</p>
         */
        @Test
        @Order(19)
        @DisplayName("CustomAccessDeniedHandler exists and implements AccessDeniedHandler")
        void accessDeniedHandlerExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.CustomAccessDeniedHandler");
                boolean impl = java.util.Arrays.stream(cls.getInterfaces())
                        .anyMatch(i -> i.getSimpleName().equals("AccessDeniedHandler"));
                assertTrue(impl, "CustomAccessDeniedHandler must implement AccessDeniedHandler");
            } catch (ClassNotFoundException e) {
                fail("CustomAccessDeniedHandler not found in com.fintech.walletapi.security");
            }
        }
    }

    @Nested
    @DisplayName("Step 6: SecurityConfig")
    class Step6SecurityConfig {

        /**
         * Course Week 5 lab check: SecurityConfig exists with @EnableWebSecurity.
         *
         * <p><b>Pre-condition:</b> Step 6: SecurityConfig; the shared test context is loaded and earlier
         * checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> SecurityConfig exists with @EnableWebSecurity holds.</p>
         */
        @Test
        @Order(20)
        @DisplayName("SecurityConfig exists with @EnableWebSecurity")
        void securityConfigHasEnableWebSecurity() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.SecurityConfig");
                assertTrue(classHasAnnotation(cls, "EnableWebSecurity"),
                    "SecurityConfig must be annotated with @EnableWebSecurity");
            } catch (ClassNotFoundException e) {
                fail("SecurityConfig not found at com.fintech.walletapi.security.SecurityConfig");
            }
        }

        /**
         * Course Week 5 lab check: SecurityConfig has @EnableMethodSecurity (enables @PreAuthorize /
         * @PostAuthorize).
         *
         * <p><b>Pre-condition:</b> Step 6: SecurityConfig; the shared test context is loaded and earlier
         * checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> SecurityConfig has @EnableMethodSecurity (enables @PreAuthorize /
         * @PostAuthorize) holds.</p>
         */
        @Test
        @Order(21)
        @DisplayName("SecurityConfig has @EnableMethodSecurity (enables @PreAuthorize / @PostAuthorize)")
        void securityConfigHasEnableMethodSecurity() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.security.SecurityConfig");
                assertTrue(classHasAnnotation(cls, "EnableMethodSecurity"),
                    "SecurityConfig must be annotated with @EnableMethodSecurity");
            } catch (ClassNotFoundException e) {
                fail("SecurityConfig not found");
            }
        }
    }

    @Nested
    @DisplayName("Step 7: Auth DTOs")
    class Step7AuthDtos {

        /**
         * Course Week 5 lab check: LoginDTO exists with username and password fields.
         *
         * <p><b>Pre-condition:</b> Step 7: Auth DTOs; the shared test context is loaded and earlier checks
         * have run in their declared order.</p>
         * <p><b>Post-condition:</b> LoginDTO exists with username and password fields holds.</p>
         */
        @Test
        @Order(25)
        @DisplayName("LoginDTO exists with username and password fields")
        void loginDtoExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.dto.LoginDTO");
                cls.getDeclaredField("username");
                cls.getDeclaredField("password");
            } catch (ClassNotFoundException e) {
                fail("LoginDTO not found at com.fintech.walletapi.dto.LoginDTO");
            } catch (NoSuchFieldException e) {
                fail("LoginDTO must have both 'username' and 'password' fields");
            }
        }

        /**
         * Course Week 5 lab check: RegisterDTO exists with username, password, fullName, email, phone fields.
         *
         * <p><b>Pre-condition:</b> Step 7: Auth DTOs; the shared test context is loaded and earlier checks
         * have run in their declared order.</p>
         * <p><b>Post-condition:</b> RegisterDTO exists with username, password, fullName, email, phone fields
         * holds.</p>
         */
        @Test
        @Order(26)
        @DisplayName("RegisterDTO exists with username, password, fullName, email, phone fields")
        void registerDtoExists() {
            try {
                Class<?> cls = Class.forName("com.fintech.walletapi.dto.RegisterDTO");
                for (String f : new String[]{"username", "password", "fullName", "email", "phone"}) {
                    try {
                        cls.getDeclaredField(f);
                    } catch (NoSuchFieldException e) {
                        fail("RegisterDTO is missing required field: '" + f + "'");
                    }
                }
            } catch (ClassNotFoundException e) {
                fail("RegisterDTO not found at com.fintech.walletapi.dto.RegisterDTO");
            }
        }
    }

    @Nested
    @DisplayName("Step 8: AuthController — Register & Login")
    class Step8AuthController {

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register returns 201 Created.
         *
         * <p><b>Pre-condition:</b> Step 8: AuthController — Register &amp; Login; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register returns 201 Created holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(30)
        @DisplayName("POST /api/v1/auth/register returns 201 Created")
        void registerReturns201() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice_reg\",\"password\":\"Password123!\"," +
                                     "\"fullName\":\"Alice Register\",\"email\":\"alice_reg@test.com\",\"phone\":\"09170000030\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201));
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register with duplicate username returns 409 Conflict.
         *
         * <p><b>Pre-condition:</b> Step 8: AuthController — Register &amp; Login; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register with duplicate username returns 409 Conflict
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(31)
        @DisplayName("POST /api/v1/auth/register with duplicate username returns 409 Conflict")
        void duplicateUsernameReturns409() throws Exception {
            register("alice2dup", "Password123!", "Alice", "a2@test.com", "09171234568");
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice2dup\",\"password\":\"Password123!\"," +
                                     "\"fullName\":\"Alice Again\",\"email\":\"a3@test.com\",\"phone\":\"09171234569\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/login with correct credentials returns 200 and a JWT
         * token.
         *
         * <p><b>Pre-condition:</b> Step 8: AuthController — Register &amp; Login; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/login with correct credentials returns 200 and a JWT
         * token holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(32)
        @DisplayName("POST /api/v1/auth/login with correct credentials returns 200 and a JWT token")
        void loginReturnsToken() throws Exception {
            register("logintest", "Password123!", "Login Test", "lt@test.com", "09171234560");

            String response = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"logintest\",\"password\":\"Password123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").isString())
                    .andReturn().getResponse().getContentAsString();

            String token = response.replaceAll(".*\"data\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            assertTrue(token.split("\\.").length == 3,
                    "JWT token must have 3 dot-separated segments (header.payload.signature). Got: " + token);
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/login with wrong password returns 401.
         *
         * <p><b>Pre-condition:</b> Step 8: AuthController — Register &amp; Login; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/login with wrong password returns 401 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(33)
        @DisplayName("POST /api/v1/auth/login with wrong password returns 401")
        void loginWrongPasswordReturns401() throws Exception {
            register("wrongpwuser", "Password123!", "Wrong PW", "wpw@test.com", "09171111111");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"wrongpwuser\",\"password\":\"WrongPassword!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Security Enforcement: JWT Required")
    class SecurityEnforcement {

        /**
         * Course Week 5 lab check: GET /api/v1/accounts with no token returns 401 Unauthorized (JSON body).
         *
         * <p><b>Pre-condition:</b> Security Enforcement: JWT Required; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/accounts with no token returns 401 Unauthorized (JSON body)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(40)
        @DisplayName("GET /api/v1/accounts with no token returns 401 Unauthorized (JSON body)")
        void noTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Course Week 5 lab check: GET /api/v1/accounts with garbage token returns 401.
         *
         * <p><b>Pre-condition:</b> Security Enforcement: JWT Required; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/accounts with garbage token returns 401 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(41)
        @DisplayName("GET /api/v1/accounts with garbage token returns 401")
        void invalidTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/accounts")
                            .header("Authorization", "Bearer not.a.real.token"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Course Week 5 lab check: 401 response is JSON (from CustomAuthenticationEntryPoint), not HTML.
         *
         * <p><b>Pre-condition:</b> Security Enforcement: JWT Required; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> 401 response is JSON (from CustomAuthenticationEntryPoint), not HTML
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(42)
        @DisplayName("401 response is JSON (from CustomAuthenticationEntryPoint), not HTML")
        void unauthorizedResponseIsJson() throws Exception {
            String contentType = mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentType();
            assertNotNull(contentType, "Response must have a Content-Type header");
            assertTrue(contentType.contains("application/json"),
                    "401 response must be JSON (CustomAuthenticationEntryPoint), not HTML. Got: " + contentType);
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register and /login are public (no token needed);
         * unknown user gets 401.
         *
         * <p><b>Pre-condition:</b> Security Enforcement: JWT Required; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register and /login are public (no token needed);
         * unknown user gets 401 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(43)
        @DisplayName("POST /api/v1/auth/register and /login are public (no token needed); unknown user gets 401")
        void authEndpointsArePublic() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"publiccheck\",\"password\":\"Password123!\"," +
                                     "\"fullName\":\"Public Check\",\"email\":\"pc@test.com\",\"phone\":\"09170000000\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"publiccheck\",\"password\":\"Password123!\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"ghost\",\"password\":\"ghost\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Step 9: GET /api/v1/customers/me (Identity from JWT)")
    class Step9MeEndpoint {

        /**
         * Course Week 5 lab check: GET /api/v1/customers/me with valid JWT returns 200.
         *
         * <p><b>Pre-condition:</b> Step 9: GET /api/v1/customers/me (Identity from JWT); the shared test
         * context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/customers/me with valid JWT returns 200 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(50)
        @DisplayName("GET /api/v1/customers/me with valid JWT returns 200")
        void meReturns200WithValidToken() throws Exception {
            ensureAliceSetup();
            mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk());
        }

        /**
         * Course Week 5 lab check: GET /api/v1/customers/me returns ApiResponse with customer data.
         *
         * <p><b>Pre-condition:</b> Step 9: GET /api/v1/customers/me (Identity from JWT); the shared test
         * context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/customers/me returns ApiResponse with customer data
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(51)
        @DisplayName("GET /api/v1/customers/me returns ApiResponse with customer data")
        void meReturnsCustomerData() throws Exception {
            ensureAliceSetup();
            mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data").exists());
        }

        /**
         * Course Week 5 lab check: GET /api/v1/customers/me response includes alice's fullName.
         *
         * <p><b>Pre-condition:</b> Step 9: GET /api/v1/customers/me (Identity from JWT); the shared test
         * context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/customers/me response includes alice's fullName holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(52)
        @DisplayName("GET /api/v1/customers/me response includes alice's fullName")
        void meResponseIncludesFullName() throws Exception {
            ensureAliceSetup();
            mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Alice Johnson"));
        }

        /**
         * Course Week 5 lab check: GET /api/v1/customers/me response includes alice's email, phone and
         * username.
         *
         * <p><b>Pre-condition:</b> Step 9: GET /api/v1/customers/me (Identity from JWT); the shared test
         * context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/customers/me response includes alice's email, phone and
         * username holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(53)
        @DisplayName("GET /api/v1/customers/me response includes alice's email, phone and username")
        void meResponseIncludesContactAndUsername() throws Exception {
            ensureAliceSetup();
            mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.data.phone").value("09171234567"))
                    .andExpect(jsonPath("$.data.username").value("alice"));
        }

        /**
         * Course Week 5 lab check: GET /api/v1/customers/me with no token returns 401.
         *
         * <p><b>Pre-condition:</b> Step 9: GET /api/v1/customers/me (Identity from JWT); the shared test
         * context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GET /api/v1/customers/me with no token returns 401 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(54)
        @DisplayName("GET /api/v1/customers/me with no token returns 401")
        void meWithNoTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Step 10: GlobalExceptionHandler — Security Exception Handlers")
    class Step10SecurityExceptions {

        /**
         * Course Week 5 lab check: GlobalExceptionHandler handles BadCredentialsException.
         *
         * <p><b>Pre-condition:</b> Step 10: GlobalExceptionHandler — Security Exception Handlers; the shared
         * test context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GlobalExceptionHandler handles BadCredentialsException holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(55)
        @DisplayName("GlobalExceptionHandler handles BadCredentialsException")
        void handlesBadCredentials() throws ClassNotFoundException {
            Class<?> cls = Class.forName("com.fintech.walletapi.exception.GlobalExceptionHandler");
            boolean handles = java.util.Arrays.stream(cls.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(
                            org.springframework.web.bind.annotation.ExceptionHandler.class))
                    .anyMatch(m -> {
                        org.springframework.web.bind.annotation.ExceptionHandler ann =
                            m.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
                        return java.util.Arrays.stream(ann.value())
                                .anyMatch(v -> v.getSimpleName().equals("BadCredentialsException"))
                            || java.util.Arrays.stream(m.getParameterTypes())
                                .anyMatch(p -> p.getSimpleName().equals("BadCredentialsException"));
                    });
            assertTrue(handles, "GlobalExceptionHandler must handle BadCredentialsException (Step 10)");
        }

        /**
         * Course Week 5 lab check: GlobalExceptionHandler handles AccessDeniedException.
         *
         * <p><b>Pre-condition:</b> Step 10: GlobalExceptionHandler — Security Exception Handlers; the shared
         * test context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> GlobalExceptionHandler handles AccessDeniedException holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(56)
        @DisplayName("GlobalExceptionHandler handles AccessDeniedException")
        void handlesAccessDenied() throws ClassNotFoundException {
            Class<?> cls = Class.forName("com.fintech.walletapi.exception.GlobalExceptionHandler");
            boolean handles = java.util.Arrays.stream(cls.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(
                            org.springframework.web.bind.annotation.ExceptionHandler.class))
                    .anyMatch(m -> {
                        org.springframework.web.bind.annotation.ExceptionHandler ann =
                            m.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
                        return java.util.Arrays.stream(ann.value())
                                .anyMatch(v -> v.getSimpleName().equals("AccessDeniedException"))
                            || java.util.Arrays.stream(m.getParameterTypes())
                                .anyMatch(p -> p.getSimpleName().equals("AccessDeniedException"));
                    });
            assertTrue(handles, "GlobalExceptionHandler must handle AccessDeniedException (Step 10)");
        }

        /**
         * Course Week 5 lab check: Wrong password response body is JSON with status 401 (not HTML 500).
         *
         * <p><b>Pre-condition:</b> Step 10: GlobalExceptionHandler — Security Exception Handlers; the shared
         * test context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> Wrong password response body is JSON with status 401 (not HTML 500)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(57)
        @DisplayName("Wrong password response body is JSON with status 401 (not HTML 500)")
        void badCredentialResponseIsCleanJson() throws Exception {
            register("badcreduser", "Password123!", "Bad Cred", "bc@test.com", "09172222222");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"badcreduser\",\"password\":\"WrongPassword\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @Nested
    @DisplayName("Step 11: AccountDTO has 'username' field for @PostAuthorize")
    class Step11AccountDtoUsername {

        /**
         * Course Week 5 lab check: AccountDTO has a 'username' String field.
         *
         * <p><b>Pre-condition:</b> Step 11: AccountDTO has 'username' field for @PostAuthorize; the shared
         * test context is loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> AccountDTO has a 'username' String field holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(58)
        @DisplayName("AccountDTO has a 'username' String field")
        void accountDtoHasUsernameField() throws ClassNotFoundException {
            Class<?> dto = Class.forName("com.fintech.walletapi.dto.AccountDTO");
            try {
                Field f = dto.getDeclaredField("username");
                assertEquals(String.class, f.getType(),
                        "AccountDTO.username must be of type String");
            } catch (NoSuchFieldException e) {
                fail("AccountDTO must have a 'username' field — needed by @PostAuthorize (Step 11)");
            }
        }
    }

    @Nested
    @DisplayName("Step 12: Method Security — RBAC")
    class Step12MethodSecurity {

        /**
         * Course Week 5 lab check: AccountService.getPaginatedAccounts has @PreAuthorize annotation.
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> AccountService.getPaginatedAccounts has @PreAuthorize annotation
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(60)
        @DisplayName("AccountService.getPaginatedAccounts has @PreAuthorize annotation")
        void getPaginatedAccountsHasPreAuthorize() throws ClassNotFoundException {
            Class<?> svc = Class.forName("com.fintech.walletapi.service.AccountService");
            boolean found = java.util.Arrays.stream(svc.getDeclaredMethods())
                    .filter(m -> m.getName().equals("getPaginatedAccounts"))
                    .anyMatch(m -> methodHasAnnotation(m, "PreAuthorize"));
            assertTrue(found,
                    "AccountService.getPaginatedAccounts must be annotated with @PreAuthorize(\"hasRole('ADMIN')\")");
        }

        /**
         * Course Week 5 lab check: AccountService.getAccountById has @PostAuthorize annotation.
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> AccountService.getAccountById has @PostAuthorize annotation holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(61)
        @DisplayName("AccountService.getAccountById has @PostAuthorize annotation")
        void getAccountByIdHasPostAuthorize() throws ClassNotFoundException {
            Class<?> svc = Class.forName("com.fintech.walletapi.service.AccountService");
            boolean found = java.util.Arrays.stream(svc.getDeclaredMethods())
                    .filter(m -> m.getName().equals("getAccountById"))
                    .anyMatch(m -> methodHasAnnotation(m, "PostAuthorize"));
            assertTrue(found,
                    "AccountService.getAccountById must be annotated with @PostAuthorize(\"hasRole('ADMIN') or returnObject.username == authentication.name\")");
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on getPaginatedAccounts blocks ROLE_CUSTOMER (returns 403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on getPaginatedAccounts blocks ROLE_CUSTOMER (returns 403)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(62)
        @DisplayName("@PreAuthorize on getPaginatedAccounts blocks ROLE_CUSTOMER (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerGetsPaginationForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/page"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on getPaginatedAccounts allows ROLE_ADMIN (returns 200).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on getPaginatedAccounts allows ROLE_ADMIN (returns 200)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(63)
        @DisplayName("@PreAuthorize on getPaginatedAccounts allows ROLE_ADMIN (returns 200)")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminGetsPaginationOk() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/page"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/accounts/page")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize 403 response is JSON (from GlobalExceptionHandler), not
         * HTML.
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize 403 response is JSON (from GlobalExceptionHandler), not
         * HTML holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(64)
        @DisplayName("@PreAuthorize 403 response is JSON (from GlobalExceptionHandler), not HTML")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void forbiddenResponseIsJson() throws Exception {
            String contentType = mockMvc.perform(get("/api/v1/accounts/page"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andReturn().getResponse().getContentType();
            assertNotNull(contentType, "403 response must have a Content-Type header");
            assertTrue(contentType.contains("application/json"),
                    "403 response must be JSON (GlobalExceptionHandler AccessDeniedException handler, Step 10). Got: " + contentType);
        }

        /**
         * Course Week 5 lab check: @PostAuthorize: customer can access their own account (200 OK).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PostAuthorize: customer can access their own account (200 OK) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(65)
        @DisplayName("@PostAuthorize: customer can access their own account (200 OK)")
        void customerCanAccessOwnAccount() throws Exception {
            ensureAliceSetup();

            // Find alice's customer id from her own token
            String meResponse = mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andReturn().getResponse().getContentAsString();

            Long customerId = extractLong(meResponse, "id");
            assumeNotNull(customerId, "Skipping: /me endpoint not implemented yet (Step 9 required)");

            // Alice opens a wallet under her own customer id (allowed by @PreAuthorize)
            String createResponse = mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + aliceToken)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"SAVINGS\",\"balance\":1000.00,\"status\":\"ACTIVE\"}",
                                customerId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            aliceAccountId = extractLong(createResponse, "id");
            assumeNotNull(aliceAccountId, "Skipping: account creation failed");

            // Reading her own wallet passes the @PostAuthorize ownership check
            mockMvc.perform(get("/api/v1/accounts/" + aliceAccountId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        /**
         * Course Week 5 lab check: @PostAuthorize: customer CANNOT access another customer's account (403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PostAuthorize: customer CANNOT access another customer's account (403)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(66)
        @DisplayName("@PostAuthorize: customer CANNOT access another customer's account (403)")
        void customerCannotAccessOthersAccount() throws Exception {
            assumeNotNull(aliceAccountId, "Skipping: requires order(65) to pass first");

            // Create bob and log him in
            register("bob", "password123", "Bob Santos", "bob@example.com", "09181234567");
            bobToken = login("bob", "password123");
            assumeNotNull(bobToken, "Skipping: bob login failed");

            // Bob opens a wallet of his own
            String meResponse = mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + bobToken))
                    .andReturn().getResponse().getContentAsString();
            Long bobCustomerId = extractLong(meResponse, "id");
            assumeNotNull(bobCustomerId, "Skipping: /me endpoint not implemented yet (Step 9 required)");

            String createResponse = mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + bobToken)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"SAVINGS\",\"balance\":500.00,\"status\":\"ACTIVE\"}",
                                bobCustomerId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long bobAccountId = extractLong(createResponse, "id");
            assumeNotNull(bobAccountId, "Skipping: account creation failed");

            // Alice's token on bob's wallet must be refused by the ownership check
            mockMvc.perform(get("/api/v1/accounts/" + bobAccountId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on createAccount: customer CANNOT open an account for
         * another customer (403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on createAccount: customer CANNOT open an account for
         * another customer (403) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(67)
        @DisplayName("@PreAuthorize on createAccount: customer CANNOT open an account for another customer (403)")
        void customerCannotOpenAccountForAnotherCustomer() throws Exception {
            ensureAliceSetup();

            // Create carol and look up her customer id
            register("carol", "password123", "Carol Reyes", "carol@example.com", "09191234567");
            String carolToken = login("carol", "password123");
            assertNotNull(carolToken, "Login must return a JWT token in $.data");
            String meResponse = mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + carolToken))
                    .andReturn().getResponse().getContentAsString();
            Long carolCustomerId = extractLong(meResponse, "id");
            assertNotNull(carolCustomerId,
                    "GET /api/v1/customers/me must return the customer id in $.data.id (Step 9)");

            // Alice tries to open a wallet under carol's customer id: @PreAuthorize must refuse it
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + aliceToken)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"CHECKING\",\"balance\":50.00,\"status\":\"ACTIVE\"}",
                                carolCustomerId)))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on createAccount: customer who sends no customerId gets 403.
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on createAccount: customer who sends no customerId gets 403
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(68)
        @DisplayName("@PreAuthorize on createAccount: customer who sends no customerId gets 403")
        void customerCannotOpenAccountWithoutCustomerId() throws Exception {
            ensureAliceSetup();

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + aliceToken)
                            .content("{\"accountType\":\"CHECKING\",\"balance\":50.00,\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on createAccount: ROLE_ADMIN can open an account for any
         * customer (201).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on createAccount: ROLE_ADMIN can open an account for any
         * customer (201) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(69)
        @DisplayName("@PreAuthorize on createAccount: ROLE_ADMIN can open an account for any customer (201)")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminCanOpenAccountForAnyCustomer() throws Exception {
            // As admin, create a walk-in customer profile
            String customerResponse = mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Walk In Customer\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long customerId = extractLong(customerResponse, "id");
            assertNotNull(customerId, "An ADMIN must be able to create a customer (POST /api/v1/customers)");

            // The admin may open a wallet for any customer
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"SAVINGS\",\"balance\":200.00,\"status\":\"ACTIVE\"}",
                                customerId)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/accounts")
                            // The same call without any identity is rejected with 401
                            .with(SecurityMockMvcRequestPostProcessors.anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountType\":\"SAVINGS\",\"balance\":1.00,\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on getAllAccounts / getAccountsByStatus blocks ROLE_CUSTOMER
         * (returns 403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on getAllAccounts / getAccountsByStatus blocks
         * ROLE_CUSTOMER (returns 403) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(73)
        @DisplayName("@PreAuthorize on getAllAccounts / getAccountsByStatus blocks ROLE_CUSTOMER (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotListAllAccounts() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/accounts").param("status", "ACTIVE"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on updateAccountBalance blocks ROLE_CUSTOMER on PUT (returns
         * 403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on updateAccountBalance blocks ROLE_CUSTOMER on PUT
         * (returns 403) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(74)
        @DisplayName("@PreAuthorize on updateAccountBalance blocks ROLE_CUSTOMER on PUT (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotUpdateBalance() throws Exception {
            mockMvc.perform(put("/api/v1/accounts/999999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"balance\":999999.00}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on closeAccount blocks ROLE_CUSTOMER on DELETE (returns
         * 403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on closeAccount blocks ROLE_CUSTOMER on DELETE (returns
         * 403) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(75)
        @DisplayName("@PreAuthorize on closeAccount blocks ROLE_CUSTOMER on DELETE (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotCloseAccount() throws Exception {
            mockMvc.perform(delete("/api/v1/accounts/999999"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: @PreAuthorize on CustomerService.createCustomer blocks ROLE_CUSTOMER
         * (returns 403).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> @PreAuthorize on CustomerService.createCustomer blocks ROLE_CUSTOMER
         * (returns 403) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(76)
        @DisplayName("@PreAuthorize on CustomerService.createCustomer blocks ROLE_CUSTOMER (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotCreateCustomer() throws Exception {
            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Fake Person\"}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: Admin-only operations still allow ROLE_ADMIN (list accounts 200, create
         * customer 201).
         *
         * <p><b>Pre-condition:</b> Step 12: Method Security — RBAC; the shared test context is loaded and
         * earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> Admin-only operations still allow ROLE_ADMIN (list accounts 200, create
         * customer 201) holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(77)
        @DisplayName("Admin-only operations still allow ROLE_ADMIN (list accounts 200, create customer 201)")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminCanUseAdminOnlyOperations() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/accounts").param("status", "ACTIVE"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Walk In Customer Two\"}"))
                    .andExpect(status().isCreated());
            mockMvc.perform(get("/api/v1/accounts")
                            .with(SecurityMockMvcRequestPostProcessors.anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @Tag("bonus")
    @DisplayName("Challenge (bonus): Admin Registration Endpoint")
    class ChallengeAdminEndpoint {

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register/admin with no token returns 401 (not 404).
         *
         * <p><b>Pre-condition:</b> Challenge (bonus): Admin Registration Endpoint; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register/admin with no token returns 401 (not 404)
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(70)
        @DisplayName("POST /api/v1/auth/register/admin with no token returns 401 (not 404)")
        void adminRegisterRequiresAuth() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newadmin\",\"password\":\"Admin123!\"," +
                                     "\"fullName\":\"New Admin\",\"email\":\"na@test.com\",\"phone\":\"09173333333\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertNotEquals(404, status,
                                "POST /api/v1/auth/register/admin must exist (not 404). Create it in AuthController.");
                        assertEquals(401, status,
                                "POST /api/v1/auth/register/admin must require authentication (401 without token)");
                    });
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register/admin with CUSTOMER token returns 403.
         *
         * <p><b>Pre-condition:</b> Challenge (bonus): Admin Registration Endpoint; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register/admin with CUSTOMER token returns 403
         * holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(71)
        @DisplayName("POST /api/v1/auth/register/admin with CUSTOMER token returns 403")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotRegisterAdmin() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newadmin\",\"password\":\"Admin123!\"," +
                                     "\"fullName\":\"New Admin\",\"email\":\"na@test.com\",\"phone\":\"09173333333\"}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Course Week 5 lab check: POST /api/v1/auth/register/admin with ADMIN token returns 201.
         *
         * <p><b>Pre-condition:</b> Challenge (bonus): Admin Registration Endpoint; the shared test context is
         * loaded and earlier checks have run in their declared order.</p>
         * <p><b>Post-condition:</b> POST /api/v1/auth/register/admin with ADMIN token returns 201 holds.</p>
         *
         * @throws Exception propagated from the code under test; any exception fails this test
         */
        @Test
        @Order(72)
        @DisplayName("POST /api/v1/auth/register/admin with ADMIN token returns 201")
        @WithMockUser(username = "superadmin", roles = {"ADMIN"})
        void adminCanRegisterNewAdmin() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newadmin\",\"password\":\"Admin123!\"," +
                                     "\"fullName\":\"New Admin\",\"email\":\"na@test.com\",\"phone\":\"09173333333\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201));
        }
    }

    /**
     * Skips the current check (instead of failing it) when a prerequisite value is missing.
     *
     * <p><b>Pre-condition:</b> Called inside a running test.</p>
     * <p><b>Post-condition:</b> The test continues if {@code value} is not null; otherwise it is reported as
     * skipped.</p>
     *
     * @param value the prerequisite value
     * @param message the reason shown when the check is skipped
     */
    private static void assumeNotNull(Object value, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(value != null, message);
    }
}
