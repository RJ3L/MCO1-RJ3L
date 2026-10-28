package com.fintech.walletapi.security;

import com.fintech.walletapi.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component("accountSecurity")
public class AccountSecurity {

    private final UserRepository userRepository;

    public AccountSecurity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
