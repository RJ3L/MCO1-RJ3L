package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountSecurity}.
 */
@ExtendWith(MockitoExtension.class)
class AccountSecurityTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountSecurity accountSecurity;

    /**
     * Builds a user, optionally linked to a customer with a given id.
     *
     * <p><b>Pre-condition:</b> None.</p>
     * <p><b>Post-condition:</b> No state is changed.</p>
     *
     * @param username login name
     * @param customerId profile id, or {@code null} for a user without a profile
     * @return the user
     */
    private static User userWithCustomer(String username, Long customerId) {
        User user = new User();
        user.setUsername(username);
        if (customerId != null) {
            Customer c = new Customer();
            c.setId(customerId);
            user.setCustomer(c);
        }
        return user;
    }

    /**
     * TC-ACCSEC-01: tests {@code AccountSecurity.isOwnCustomer}.
     *
     * <p><b>Pre-condition:</b> Alice uses her own customerId 5.</p>
     * <p><b>Post-condition:</b> True.</p>
     */
    @Test
    @DisplayName("TC-ACCSEC-01 | AccountSecurity.isOwnCustomer | alice uses her own customerId 5 -> true")
    void ownCustomer_true() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithCustomer("alice", 5L)));
        assertTrue(accountSecurity.isOwnCustomer(5L, "alice"));
    }

    /**
     * TC-ACCSEC-02: tests {@code AccountSecurity.isOwnCustomer}.
     *
     * <p><b>Pre-condition:</b> Alice uses Bob's customerId 6.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-ACCSEC-02 | AccountSecurity.isOwnCustomer | alice uses Bob's customerId 6 -> false")
    void otherCustomer_false() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithCustomer("alice", 5L)));
        assertFalse(accountSecurity.isOwnCustomer(6L, "alice"));
    }

    /**
     * TC-ACCSEC-03: tests {@code AccountSecurity.isOwnCustomer}.
     *
     * <p><b>Pre-condition:</b> Null customerId.</p>
     * <p><b>Post-condition:</b> False without DB lookup.</p>
     */
    @Test
    @DisplayName("TC-ACCSEC-03 | AccountSecurity.isOwnCustomer | null customerId -> false without DB lookup")
    void nullCustomer_false() {
        assertFalse(accountSecurity.isOwnCustomer(null, "alice"));
        verifyNoInteractions(userRepository);
    }

    /**
     * TC-ACCSEC-04: tests {@code AccountSecurity.isOwnCustomer}.
     *
     * <p><b>Pre-condition:</b> Unknown user.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-ACCSEC-04 | AccountSecurity.isOwnCustomer | unknown user -> false")
    void unknownUser_false() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertFalse(accountSecurity.isOwnCustomer(5L, "ghost"));
    }

    /**
     * TC-ACCSEC-05: tests {@code AccountSecurity.isOwnCustomer}.
     *
     * <p><b>Pre-condition:</b> User without customer profile.</p>
     * <p><b>Post-condition:</b> False.</p>
     */
    @Test
    @DisplayName("TC-ACCSEC-05 | AccountSecurity.isOwnCustomer | user without customer profile -> false")
    void userWithoutProfile_false() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(userWithCustomer("admin", null)));
        assertFalse(accountSecurity.isOwnCustomer(1L, "admin"));
    }
}
