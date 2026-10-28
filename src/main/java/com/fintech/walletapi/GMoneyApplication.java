package com.fintech.walletapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of <b>GMoney</b>, a monolithic e-wallet REST API.
 *
 * <p>GMoney lets customers register, log in with a JWT, open peso and forex
 * wallets (accounts in PHP, USD, EUR, GBP or SGD), send money between wallets
 * (with automatic currency conversion) and read an immutable transfer ledger.</p>
 */
@SpringBootApplication
public class GMoneyApplication {

    /**
     * Boots the Spring application context and the embedded web server.
     *
     * <p><b>Pre-condition:</b> a reachable MySQL server matching the settings in
     * {@code application.properties} (or the test H2 configuration).</p>
     * <p><b>Post-condition:</b> the GMoney API is listening for HTTP requests.</p>
     *
     * @param args optional Spring Boot command-line arguments (e.g. {@code --server.port=9090})
     */
    public static void main(String[] args) {
        SpringApplication.run(GMoneyApplication.class, args);
    }
}
