package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.ApiResponse;
import com.fintech.walletapi.dto.LoginDTO;
import com.fintech.walletapi.dto.RegisterDTO;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.UserRepository;
import com.fintech.walletapi.security.JwtUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Registration and login endpoints ({@code /api/v1/auth}).
 */
@RestController
@Tag(name = "1. Auth", description = "Register, log in and get a JWT")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the controller.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param authenticationManager verifies login credentials
     * @param jwtUtil               issues tokens
     * @param userRepository        stores new users
     * @param passwordEncoder       hashes passwords
     */
    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * {@code POST /api/v1/auth/register} — public sign-up as a customer.
     *
     * <p><b>Pre-condition:</b> the body passes validation and the username is unused.</p>
     * <p><b>Post-condition:</b> a {@code ROLE_CUSTOMER} user and its customer profile exist.</p>
     *
     * @param request credentials and profile
     * @return 201 on success, 409 if the username is taken
     */
    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterDTO request) {
        return registerUser(request, "ROLE_CUSTOMER");
    }

    /**
     * {@code POST /api/v1/auth/register/admin} — an admin creates another admin.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; the body passes validation.</p>
     * <p><b>Post-condition:</b> a {@code ROLE_ADMIN} user and its profile exist.</p>
     *
     * @param request credentials and profile
     * @return 201 on success, 409 if the username is taken (403 for non-admins)
     */
    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> registerAdmin(@Valid @RequestBody RegisterDTO request) {
        return registerUser(request, "ROLE_ADMIN");
    }

    /**
     * {@code POST /api/v1/auth/login} — exchanges credentials for a JWT.
     *
     * <p><b>Pre-condition:</b> the body passes validation.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param request username and password
     * @return 200 with the token in {@code data}; 401 (via the exception handler) on bad credentials
     */
    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody LoginDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String token = jwtUtil.generateToken(request.getUsername());
        return ResponseEntity.ok(new ApiResponse<>(200, "Login successful", token));
    }

    /**
     * Shared registration logic for customers and admins.
     *
     * <p><b>Pre-condition:</b> {@code request} is valid; {@code role} is ROLE_CUSTOMER or ROLE_ADMIN.</p>
     * <p><b>Post-condition:</b> on success a user and a linked customer profile are saved together.</p>
     *
     * @param request credentials and profile
     * @param role    role to grant
     * @return 201 on success, 409 if the username is taken
     */
    private ResponseEntity<ApiResponse<String>> registerUser(RegisterDTO request, String role) {
        // Usernames are unique: a taken one returns 409 Conflict
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        // 1. Login credentials; the password is stored only as a BCrypt hash
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        // 2. Customer profile, linked to the login in both directions
        Customer customer = new Customer();
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setUser(user);
        user.setCustomer(customer);

        // CascadeType.ALL on User.customer saves both rows in one call
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "User registered successfully"));
    }
}
