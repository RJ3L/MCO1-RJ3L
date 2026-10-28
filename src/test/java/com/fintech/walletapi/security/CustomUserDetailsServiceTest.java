package com.fintech.walletapi.security;

import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("TC-UDS-01 | CustomUserDetailsService.loadUserByUsername | existing user -> same user returned")
    void load_existing() {
        User alice = new User();
        alice.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        assertSame(alice, service.loadUserByUsername("alice"));
    }

    @Test
    @DisplayName("TC-UDS-02 | CustomUserDetailsService.loadUserByUsername | unknown user -> UsernameNotFoundException")
    void load_missing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));
        assertEquals("User not found: ghost", ex.getMessage());
    }

    @Test
    @DisplayName("TC-UDS-03 | CustomUserDetailsService.loadUserByUsername | admin -> authorities contain ROLE_ADMIN")
    void load_adminAuthorities() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole("ROLE_ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        UserDetails details = service.loadUserByUsername("admin");
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
    }
}
