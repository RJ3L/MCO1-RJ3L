package com.fintech.walletapi.config;

import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.User;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import com.fintech.walletapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the data GMoney needs on a fresh database: one administrator (so the
 * admin-only endpoints can be used) and a starter exchange-rate table.
 *
 * <p>Runs only when {@code gmoney.seed.enabled=true} and never overwrites
 * existing rows, so it is safe on every restart. The seeded rates are
 * illustrative values for the demo, not live market rates; admins can change
 * them through {@code PUT /api/v1/forex/rates/{id}}.</p>
 */
@Component
@ConditionalOnProperty(name = "gmoney.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Starter rates: 1 unit of each foreign currency in PHP. Other pairs are derived. */
    static final List<ExchangeRate> DEFAULT_RATES = List.of(
            new ExchangeRate("USD", "PHP", new BigDecimal("58.000000")),
            new ExchangeRate("EUR", "PHP", new BigDecimal("63.000000")),
            new ExchangeRate("GBP", "PHP", new BigDecimal("74.000000")),
            new ExchangeRate("SGD", "PHP", new BigDecimal("43.500000")));

    private final UserRepository userRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    /**
     * Creates the seeder.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param userRepository         where the admin is stored
     * @param exchangeRateRepository where the rates are stored
     * @param passwordEncoder        hashes the admin password
     * @param adminUsername          {@code gmoney.seed.admin.username}
     * @param adminPassword          {@code gmoney.seed.admin.password}
     */
    public DataSeeder(UserRepository userRepository,
                      ExchangeRateRepository exchangeRateRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${gmoney.seed.admin.username:admin}") String adminUsername,
                      @Value("${gmoney.seed.admin.password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    /**
     * Seeds the admin and the rates if they are missing.
     *
     * <p><b>Pre-condition:</b> the database schema exists.</p>
     * <p><b>Post-condition:</b> an admin user with {@code adminUsername} exists, and every
     * {@link #DEFAULT_RATES} pair exists (existing rows are left untouched).</p>
     *
     * @param args command-line arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        // Create the first administrator only once; public registration can never create an admin
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            Customer profile = new Customer();
            profile.setFullName("GMoney Administrator");
            profile.setEmail("admin@gmoney.local");

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            admin.setCustomer(profile);
            // Link both sides of the one-to-one; saving the user cascades to the profile
            profile.setUser(admin);
            userRepository.save(admin);
            log.info("Seeded default admin user '{}'", adminUsername);
        }

        // Seed the starter X -> PHP rates that are missing; existing (live or admin-edited) rates are kept
        for (ExchangeRate rate : DEFAULT_RATES) {
            if (!exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(
                    rate.getBaseCurrency(), rate.getQuoteCurrency())) {
                // Save a copy so the shared constant list never becomes managed JPA entities
                exchangeRateRepository.save(new ExchangeRate(
                        rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate()));
                log.info("Seeded exchange rate {} -> {} = {}",
                        rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate());
            }
        }
    }
}
