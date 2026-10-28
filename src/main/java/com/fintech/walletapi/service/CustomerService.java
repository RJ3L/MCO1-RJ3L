package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.CustomerDTO;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.CustomerRepository;
import com.fintech.walletapi.repository.AccountRepository;
import java.util.List;
import com.fintech.walletapi.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository,
                           AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public CustomerDTO getCurrentCustomer(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Customer customer = user.getCustomer();
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return new CustomerDTO(customer.getId(), customer.getFullName(),
                customer.getEmail(), customer.getPhone(), user.getUsername());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public CustomerDTO createCustomer(CustomerDTO dto) {
        Customer customer = new Customer();
        customer.setFullName(dto.getFullName());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());

        Customer saved = customerRepository.save(customer);
        return new CustomerDTO(saved.getId(), saved.getFullName(),
                saved.getEmail(), saved.getPhone(), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<CustomerDTO> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::convertToDTO).toList();
    }

    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#id, authentication.name)")
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerById(Long id) {
        return convertToDTO(customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found")));
    }

    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#id, authentication.name)")
    public CustomerDTO updateCustomer(Long id, CustomerDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        customer.setFullName(dto.getFullName());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());
        return convertToDTO(customerRepository.save(customer));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        if (accountRepository.existsByCustomer_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove the customer's eligible wallets before deleting the profile");
        }
        if (customer.getUser() != null) {
            userRepository.delete(customer.getUser());
        } else {
            customerRepository.delete(customer);
        }
    }

    private CustomerDTO convertToDTO(Customer customer) {
        return new CustomerDTO(customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone(),
                customer.getUser() == null ? null : customer.getUser().getUsername());
    }
}
