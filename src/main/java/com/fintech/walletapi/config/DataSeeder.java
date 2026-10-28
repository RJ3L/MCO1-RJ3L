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

@Component
@ConditionalOnProperty(name = "gmoney.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

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

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            Customer profile = new Customer();
            profile.setFullName("GMoney Administrator");
            profile.setEmail("admin@gmoney.local");

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");
            admin.setCustomer(profile);
            profile.setUser(admin);
            userRepository.save(admin);
            log.info("Seeded default admin user '{}'", adminUsername);
        }

        for (ExchangeRate rate : DEFAULT_RATES) {
            if (!exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(
                    rate.getBaseCurrency(), rate.getQuoteCurrency())) {
                exchangeRateRepository.save(new ExchangeRate(
                        rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate()));
                log.info("Seeded exchange rate {} -> {} = {}",
                        rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate());
            }
        }
    }
}
