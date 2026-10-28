package com.fintech.walletapi.security;

import com.fintech.walletapi.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Ownership checks used inside {@code @PreAuthorize} expressions,
 * e.g. {@code @accountSecurity.isOwnCustomer(#dto.customerId, authentication.name)}.
 */
@Component("accountSecurity")
public class AccountSecurity {

    private final UserRepository userRepository;

    /**
     * Creates the checker.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param userRepository used to look up the logged-in user
     */
    public AccountSecurity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Checks whether the logged-in user's own customer profile has the given id.
     * Prevents a customer from opening a wallet under someone else's profile (BOLA).
     *
     * <p><b>Pre-condition:</b> none.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param customerId the customer id sent in the request
     * @param username   the logged-in username (from the JWT)
     * @return {@code true} only if the user exists, has a customer profile, and its id equals {@code customerId}
     */
    public boolean isOwnCustomer(Long customerId, String username) {
        if (customerId == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(user -> user.getCustomer() != null
                        && customerId.equals(user.getCustomer().getId()))
                .orElse(false);
    }
}
