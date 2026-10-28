package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.CustomerDTO;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.CustomerRepository;
import com.fintech.walletapi.repository.AccountRepository;
import java.util.List;
import com.fintech.walletapi.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CustomerService customerService;

    private static User userWithProfile(String username) {
        Customer customer = new Customer();
        customer.setId(3L);
        customer.setFullName("Alice Johnson");
        customer.setEmail("alice@example.com");
        customer.setPhone("09171234567");
        User user = new User();
        user.setUsername(username);
        user.setCustomer(customer);
        customer.setUser(user);
        return user;
    }

    @Test
    @DisplayName("TC-CUSSVC-01 | CustomerService.getCurrentCustomer | existing user with profile -> full DTO")
    void getCurrentCustomer_found() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithProfile("alice")));

        CustomerDTO dto = customerService.getCurrentCustomer("alice");

        assertEquals(3L, dto.getId());
        assertEquals("Alice Johnson", dto.getFullName());
        assertEquals("alice@example.com", dto.getEmail());
        assertEquals("alice", dto.getUsername());
    }

    @Test
    @DisplayName("TC-CUSSVC-02 | CustomerService.getCurrentCustomer | unknown user -> 404 'User not found'")
    void getCurrentCustomer_unknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> customerService.getCurrentCustomer("ghost"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    @Test
    @DisplayName("TC-CUSSVC-03 | CustomerService.getCurrentCustomer | user without profile -> 404 'Customer not found'")
    void getCurrentCustomer_noProfile() {
        User user = new User();
        user.setUsername("admin2");
        when(userRepository.findByUsername("admin2")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> customerService.getCurrentCustomer("admin2"));

        assertEquals("Customer not found", ex.getReason());
    }

    @Test
    @DisplayName("TC-CUSSVC-04 | CustomerService.createCustomer | valid DTO -> saved with generated id")
    void createCustomer_saves() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });
        CustomerDTO request = new CustomerDTO();
        request.setFullName("Carol Cruz");

        CustomerDTO result = customerService.createCustomer(request);

        assertEquals(11L, result.getId());
        assertEquals("Carol Cruz", result.getFullName());
    }

    @Test
    @DisplayName("TC-CUSSVC-05 | CustomerService.createCustomer | e-mail and phone are persisted")
    void createCustomer_copiesContactDetails() {
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        CustomerDTO request = new CustomerDTO(null, "Dan Reyes", "dan@example.com", "0918", null);

        customerService.createCustomer(request);

        assertEquals("dan@example.com", captor.getValue().getEmail());
        assertEquals("0918", captor.getValue().getPhone());
    }

    @Test
    @DisplayName("TC-CUSSVC-06 | CustomerService.createCustomer | profile created without login -> username null")
    void createCustomer_noLogin() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerDTO request = new CustomerDTO();
        request.setFullName("Eve Santos");
        request.setUsername("should-be-ignored");

        CustomerDTO result = customerService.createCustomer(request);

        assertNull(result.getUsername());
    }

    @Test
    @DisplayName("TC-CUSSVC-07 | CustomerService.getAllCustomers | linked customer -> contact details and username")
    void listLinkedCustomer() {
        when(customerRepository.findAll()).thenReturn(List.of(userWithProfile("alice").getCustomer()));
        assertEquals("alice", customerService.getAllCustomers().get(0).getUsername());
    }
    @Test
    @DisplayName("TC-CUSSVC-08 | CustomerService.getAllCustomers | no customers -> empty list")
    void listEmpty() { assertTrue(customerService.getAllCustomers().isEmpty()); }
    @Test
    @DisplayName("TC-CUSSVC-09 | CustomerService.getAllCustomers | profile without login -> null username")
    void listUnlinkedCustomer() {
        Customer c = new Customer(); c.setFullName("Guest");
        when(customerRepository.findAll()).thenReturn(List.of(c));
        assertNull(customerService.getAllCustomers().get(0).getUsername());
    }
    @Test
    @DisplayName("TC-CUSSVC-10 | CustomerService.getCustomerById | linked profile -> requested details")
    void readLinkedCustomer() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(userWithProfile("alice").getCustomer()));
        assertEquals("alice@example.com", customerService.getCustomerById(3L).getEmail());
    }
    @Test
    @DisplayName("TC-CUSSVC-11 | CustomerService.getCustomerById | missing id -> not found")
    void readMissingCustomer() { assertThrows(IllegalArgumentException.class, () -> customerService.getCustomerById(99L)); }
    @Test
    @DisplayName("TC-CUSSVC-12 | CustomerService.getCustomerById | unlinked profile -> null username")
    void readUnlinkedCustomer() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(new Customer()));
        assertNull(customerService.getCustomerById(3L).getUsername());
    }
    @Test
    @DisplayName("TC-CUSSVC-13 | CustomerService.updateCustomer | changed name and contacts -> saved")
    void updateContacts() {
        Customer c = userWithProfile("alice").getCustomer();
        when(customerRepository.findById(3L)).thenReturn(Optional.of(c));
        when(customerRepository.save(c)).thenReturn(c);
        CustomerDTO dto = new CustomerDTO(3L, "Updated", "new@example.com", "123", "ignored");
        CustomerDTO result = customerService.updateCustomer(3L, dto);
        assertEquals("Updated", result.getFullName()); assertEquals("new@example.com", result.getEmail());
        assertEquals("123", result.getPhone());
    }
    @Test
    @DisplayName("TC-CUSSVC-14 | CustomerService.updateCustomer | body identity fields -> stored identity unchanged")
    void updatePreservesIdentity() {
        Customer c = userWithProfile("alice").getCustomer();
        when(customerRepository.findById(3L)).thenReturn(Optional.of(c)); when(customerRepository.save(c)).thenReturn(c);
        CustomerDTO result = customerService.updateCustomer(3L, new CustomerDTO(999L, "Updated", null, null, "admin"));
        assertEquals(3L, result.getId()); assertEquals("alice", result.getUsername());
    }
    @Test
    @DisplayName("TC-CUSSVC-15 | CustomerService.updateCustomer | missing id -> not found and no save")
    void updateMissingCustomer() {
        assertThrows(IllegalArgumentException.class, () -> customerService.updateCustomer(99L, new CustomerDTO()));
        verify(customerRepository, never()).save(any());
    }
    @Test
    @DisplayName("TC-CUSSVC-16 | CustomerService.deleteCustomer | unlinked profile with no wallets -> removed")
    void deleteUnlinkedCustomer() {
        Customer c = new Customer(); when(customerRepository.findById(3L)).thenReturn(Optional.of(c));
        customerService.deleteCustomer(3L); verify(customerRepository).delete(c);
    }
    @Test
    @DisplayName("TC-CUSSVC-17 | CustomerService.deleteCustomer | linked profile without wallets -> delete login with cascade")
    void deleteLinkedCustomer() {
        User u = userWithProfile("alice"); when(customerRepository.findById(3L)).thenReturn(Optional.of(u.getCustomer()));
        customerService.deleteCustomer(3L); verify(userRepository).delete(u);
    }
    @Test
    @DisplayName("TC-CUSSVC-18 | CustomerService.deleteCustomer | remaining wallet -> 409 and no deletion")
    void deleteCustomerWithWalletRejected() {
        when(customerRepository.findById(3L)).thenReturn(Optional.of(new Customer()));
        when(accountRepository.existsByCustomer_Id(3L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> customerService.deleteCustomer(3L)).getStatusCode().value());
        verify(customerRepository, never()).delete(any()); verify(userRepository, never()).delete(any());
    }
    @Test
    @DisplayName("TC-CUSSVC-19 | CustomerService.deleteCustomer | unknown id -> not found")
    void deleteMissingCustomer() { assertThrows(IllegalArgumentException.class, () -> customerService.deleteCustomer(99L)); }

}
