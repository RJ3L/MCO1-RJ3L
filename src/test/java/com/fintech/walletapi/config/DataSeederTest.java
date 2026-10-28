package com.fintech.walletapi.config;

import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import com.fintech.walletapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataSeeder}.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataSeeder seeder;

    /**
     * Creates the seeder with mocked repositories and the default admin credentials.
     *
     * <p><b>Pre-condition:</b> The Mockito mocks are initialised.</p>
     * <p><b>Post-condition:</b> {@code seeder} is ready to run against the mocks.</p>
     */
    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(userRepository, exchangeRateRepository, passwordEncoder, "admin", "admin123");
    }

    /**
     * TC-SEED-01: tests {@code DataSeeder.run}.
     *
     * <p><b>Pre-condition:</b> Empty database.</p>
     * <p><b>Post-condition:</b> Admin (ROLE_ADMIN, hashed password) and 4 rates created.</p>
     */
    @Test
    @DisplayName("TC-SEED-01 | DataSeeder.run | empty database -> admin (ROLE_ADMIN, hashed password) and 4 rates created")
    void run_emptyDatabase() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("hashed");
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(anyString(), anyString())).thenReturn(false);
        ArgumentCaptor<User> admin = ArgumentCaptor.forClass(User.class);

        seeder.run();

        verify(userRepository).save(admin.capture());
        assertEquals("ROLE_ADMIN", admin.getValue().getRole());
        assertEquals("hashed", admin.getValue().getPassword());
        assertNotNull(admin.getValue().getCustomer());
        verify(exchangeRateRepository, times(DataSeeder.DEFAULT_RATES.size())).save(any(ExchangeRate.class));
    }

    /**
     * TC-SEED-02: tests {@code DataSeeder.run}.
     *
     * <p><b>Pre-condition:</b> Admin and rates already exist.</p>
     * <p><b>Post-condition:</b> Nothing saved (idempotent restart).</p>
     */
    @Test
    @DisplayName("TC-SEED-02 | DataSeeder.run | admin and rates already exist -> nothing saved (idempotent restart)")
    void run_alreadySeeded() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(anyString(), anyString())).thenReturn(true);

        seeder.run();

        verify(userRepository, never()).save(any());
        verify(exchangeRateRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    /**
     * TC-SEED-03: tests {@code DataSeeder.run}.
     *
     * <p><b>Pre-condition:</b> Only USD-&gt;PHP missing.</p>
     * <p><b>Post-condition:</b> Only that rate is added, admin kept.</p>
     */
    @Test
    @DisplayName("TC-SEED-03 | DataSeeder.run | only USD->PHP missing -> only that rate is added, admin kept")
    void run_partialRates() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(anyString(), anyString())).thenReturn(true);
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("USD", "PHP")).thenReturn(false);
        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);

        seeder.run();

        verify(exchangeRateRepository, times(1)).save(saved.capture());
        assertEquals("USD", saved.getValue().getBaseCurrency());
        verify(userRepository, never()).save(any());
    }
}
