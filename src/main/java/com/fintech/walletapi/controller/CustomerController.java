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

@RestController
@Tag(name = "2. Customers", description = "Customer profiles")
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCurrentCustomer(Authentication authentication) {
        CustomerDTO customer = customerService.getCurrentCustomer(authentication.getName());
        return ResponseEntity.ok(new ApiResponse<>(200, "Successfully retrieved customer", customer));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerDTO>> createCustomer(@Valid @RequestBody CustomerDTO customerDTO) {
        CustomerDTO createdCustomer = customerService.createCustomer(customerDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Successfully created customer", createdCustomer));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDTO>>> getAllCustomers() {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customers retrieved", customerService.getAllCustomers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDTO>> getCustomerById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer found", customerService.getCustomerById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDTO>> updateCustomer(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody CustomerDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer updated", customerService.updateCustomer(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable("id") Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Customer deleted"));
    }
}
