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

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Week5LabTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class WithMockUserSupport {
        @Bean
        MockMvcBuilderCustomizer testSecurityContextCustomizer() {
            return builder -> builder.apply(new MockMvcConfigurerAdapter() {
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

    private void ensureAliceSetup() throws Exception {
        if (aliceToken != null) return;

        register("alice", "password123", "Alice Johnson", "alice@example.com", "09171234567");

        aliceToken = login("alice", "password123");
        assertNotNull(aliceToken, "Login must return a JWT token in $.data");
    }

    private Long extractLong(String json, String field) {
        try {
            String pattern = "\"" + field + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    private boolean fieldHasAnnotation(Class<?> clazz, String fieldName, String simpleAnnotationName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            for (Annotation a : f.getAnnotations()) {
                if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
            }
        } catch (NoSuchFieldException ignored) {}
        return false;
    }

    private boolean classHasAnnotation(Class<?> cls, String simpleAnnotationName) {
        for (Annotation a : cls.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
        }
        return false;
    }

    private boolean methodHasAnnotation(Method m, String simpleAnnotationName) {
        for (Annotation a : m.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleAnnotationName)) return true;
        }
        return false;
    }

    @Nested
    @DisplayName("Step 2: User Entity & Repository")
    class Step2UserEntity {

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

        @Test
        @Order(40)
        @DisplayName("GET /api/v1/accounts with no token returns 401 Unauthorized (JSON body)")
        void noTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(41)
        @DisplayName("GET /api/v1/accounts with garbage token returns 401")
        void invalidTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/accounts")
                            .header("Authorization", "Bearer not.a.real.token"))
                    .andExpect(status().isUnauthorized());
        }

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

        @Test
        @Order(50)
        @DisplayName("GET /api/v1/customers/me with valid JWT returns 200")
        void meReturns200WithValidToken() throws Exception {
            ensureAliceSetup();
            mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk());
        }

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

        @Test
        @Order(62)
        @DisplayName("@PreAuthorize on getPaginatedAccounts blocks ROLE_CUSTOMER (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerGetsPaginationForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/page"))
                    .andExpect(status().isForbidden());
        }

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

        @Test
        @Order(65)
        @DisplayName("@PostAuthorize: customer can access their own account (200 OK)")
        void customerCanAccessOwnAccount() throws Exception {
            ensureAliceSetup();

            String meResponse = mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andReturn().getResponse().getContentAsString();

            Long customerId = extractLong(meResponse, "id");
            assumeNotNull(customerId, "Skipping: /me endpoint not implemented yet (Step 9 required)");

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

            mockMvc.perform(get("/api/v1/accounts/" + aliceAccountId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @Order(66)
        @DisplayName("@PostAuthorize: customer CANNOT access another customer's account (403)")
        void customerCannotAccessOthersAccount() throws Exception {
            assumeNotNull(aliceAccountId, "Skipping: requires order(65) to pass first");

            register("bob", "password123", "Bob Santos", "bob@example.com", "09181234567");
            bobToken = login("bob", "password123");
            assumeNotNull(bobToken, "Skipping: bob login failed");

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

            mockMvc.perform(get("/api/v1/accounts/" + bobAccountId)
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(67)
        @DisplayName("@PreAuthorize on createAccount: customer CANNOT open an account for another customer (403)")
        void customerCannotOpenAccountForAnotherCustomer() throws Exception {
            ensureAliceSetup();

            register("carol", "password123", "Carol Reyes", "carol@example.com", "09191234567");
            String carolToken = login("carol", "password123");
            assertNotNull(carolToken, "Login must return a JWT token in $.data");
            String meResponse = mockMvc.perform(get("/api/v1/customers/me")
                            .header("Authorization", "Bearer " + carolToken))
                    .andReturn().getResponse().getContentAsString();
            Long carolCustomerId = extractLong(meResponse, "id");
            assertNotNull(carolCustomerId,
                    "GET /api/v1/customers/me must return the customer id in $.data.id (Step 9)");

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + aliceToken)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"CHECKING\",\"balance\":50.00,\"status\":\"ACTIVE\"}",
                                carolCustomerId)))
                    .andExpect(status().isForbidden());
        }

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

        @Test
        @Order(69)
        @DisplayName("@PreAuthorize on createAccount: ROLE_ADMIN can open an account for any customer (201)")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminCanOpenAccountForAnyCustomer() throws Exception {
            String customerResponse = mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Walk In Customer\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long customerId = extractLong(customerResponse, "id");
            assertNotNull(customerId, "An ADMIN must be able to create a customer (POST /api/v1/customers)");

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                "{\"customerId\":%d,\"accountType\":\"SAVINGS\",\"balance\":200.00,\"status\":\"ACTIVE\"}",
                                customerId)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/accounts")
                            .with(SecurityMockMvcRequestPostProcessors.anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountType\":\"SAVINGS\",\"balance\":1.00,\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnauthorized());
        }

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

        @Test
        @Order(75)
        @DisplayName("@PreAuthorize on closeAccount blocks ROLE_CUSTOMER on DELETE (returns 403)")
        @WithMockUser(username = "alice", roles = {"CUSTOMER"})
        void customerCannotCloseAccount() throws Exception {
            mockMvc.perform(delete("/api/v1/accounts/999999"))
                    .andExpect(status().isForbidden());
        }

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

    private static void assumeNotNull(Object value, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(value != null, message);
    }
}
