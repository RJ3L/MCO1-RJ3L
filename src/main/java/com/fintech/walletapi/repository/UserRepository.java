package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for {@link User} logins.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by the unique username.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param username the login name
     * @return the user, or {@link Optional#empty()} if no such user exists
     */
    Optional<User> findByUsername(String username);
}
