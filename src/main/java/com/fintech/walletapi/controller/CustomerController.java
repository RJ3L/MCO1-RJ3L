package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.ApiResponse;
import com.fintech.walletapi.dto.CustomerDTO;
import com.fintech.walletapi.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Customer profile endpoints ({@code /api/v1/customers}).
 */
@RestController
@Tag(name = "2. Customers", description = "Customer profiles")
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Creates the controller.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param customerService customer business logic
     */
    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * {@code GET /api/v1/customers/me} — the logged-in user's profile.
     *
     * <p><b>Pre-condition:</b> a valid JWT. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param authentication the logged-in user, injected by Spring Security from the JWT
     * @return 200 with the profile (its {@code id} is the customerId used to open wallets)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCurrentCustomer(Authentication authentication) {
        CustomerDTO customer = customerService.getCurrentCustomer(authentication.getName());
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully retrieved customer", customer));
    }

    /**
     * {@code POST /api/v1/customers} — admin creates a profile without a login.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; body passes validation.</p>
     * <p><b>Post-condition:</b> a new customer exists.</p>
     *
     * @param customerDTO the profile
     * @return 201 with the saved profile
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDTO>> createCustomer(@Valid @RequestBody CustomerDTO customerDTO) {
        CustomerDTO createdCustomer = customerService.createCustomer(customerDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Successfully created customer", createdCustomer));
    }

    /**
     * Lists profiles for administrators.
     * <p><b>Pre-condition:</b> caller is ADMIN. <b>Post-condition:</b> no data changes.</p>
     * @return 200 and all profiles
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDTO>>> getAllCustomers() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customers retrieved", customerService.getAllCustomers()));
    }

    /**
     * Reads an individual profile.
     * <p><b>Pre-condition:</b> caller is ADMIN or the owner. <b>Post-condition:</b> no data changes.</p>
     * @param id customer id
     * @return 200 and the profile; 404 if missing; 403 if forbidden
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCustomerById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer found", customerService.getCustomerById(id)));
    }

    /**
     * Updates profile contact information.
     * <p><b>Pre-condition:</b> caller is ADMIN or the owner and the body is valid.</p>
     * <p><b>Post-condition:</b> contact details are saved; linked credentials remain unchanged.</p>
     * @param id customer id
     * @param dto new contact details
     * @return 200 and the updated profile; 400, 403 or 404 on failure
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDTO>> updateCustomer(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer updated", customerService.updateCustomer(id, dto)));
    }

    /**
     * Deletes a customer who has no remaining wallets, including their linked login.
     * <p><b>Pre-condition:</b> caller is ADMIN. <b>Post-condition:</b> the eligible customer is deleted.</p>
     * @param id customer id
     * @return 200 on deletion; 409 if wallets remain; 403 or 404 otherwise
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable("id") Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer deleted"));
    }
}
