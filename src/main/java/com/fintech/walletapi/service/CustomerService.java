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

/**
 * Business logic for customer profiles.
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    /**
     * Creates the service.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param customerRepository customer repository
     * @param userRepository     user repository
     * @param accountRepository wallet repository (to protect customers with wallets)
     */
    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository,
                           AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the profile of the logged-in user.
     *
     * <p><b>Pre-condition:</b> {@code username} comes from the JWT.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param username the logged-in username
     * @return the user's customer profile
     * @throws ResponseStatusException 404 if the user or their profile does not exist
     */
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

    /**
     * Creates a customer profile without a login (admin back-office use).
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; {@code dto.fullName} is not blank.</p>
     * <p><b>Post-condition:</b> a new customer row exists.</p>
     *
     * @param dto full name, e-mail and phone
     * @return the saved profile with its generated id
     */
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

    /**
     * Lists customer profiles for administration.
     * <p><b>Pre-condition:</b> caller is ADMIN. <b>Post-condition:</b> no data changes.</p>
     * @return all customer profiles, including profiles without a login
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<CustomerDTO> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::convertToDTO).toList();
    }

    /**
     * Reads a profile by id.
     * <p><b>Pre-condition:</b> caller is ADMIN or owns the requested profile.</p>
     * <p><b>Post-condition:</b> no data changes.</p>
     * @param id customer id
     * @return the requested profile
     * @throws IllegalArgumentException if the profile is missing
     */
    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#id, authentication.name)")
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerById(Long id) {
        return convertToDTO(customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found")));
    }

    /**
     * Replaces profile contact details without changing the linked login.
     * <p><b>Pre-condition:</b> caller is ADMIN or owns the profile; request passed validation.</p>
     * <p><b>Post-condition:</b> name, email and phone match the request; identity and wallets are unchanged.</p>
     * @param id customer id
     * @param dto validated contact details
     * @return the updated profile
     * @throws IllegalArgumentException if the profile is missing
     */
    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwnCustomer(#id, authentication.name)")
    public CustomerDTO updateCustomer(Long id, CustomerDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        customer.setFullName(dto.getFullName());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());
        return convertToDTO(customerRepository.save(customer));
    }

    /**
     * Deletes a profile and its linked login only after all wallets have been removed.
     * <p><b>Pre-condition:</b> caller is ADMIN and the customer has no remaining wallets.</p>
     * <p><b>Post-condition:</b> profile and linked login are gone; otherwise nothing changes.</p>
     * @param id customer id
     * @throws IllegalArgumentException if the profile is missing
     * @throws ResponseStatusException 409 if wallets still belong to the customer
     */
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

    /**
     * Maps a customer without exposing credentials.
     * <p><b>Pre-condition:</b> customer is loaded within a transaction.</p>
     * <p><b>Post-condition:</b> no data changes.</p>
     * @param customer the profile
     * @return contact details and the optional linked username
     */
    private CustomerDTO convertToDTO(Customer customer) {
        return new CustomerDTO(customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone(),
                customer.getUser() == null ? null : customer.getUser().getUsername());
    }
}
