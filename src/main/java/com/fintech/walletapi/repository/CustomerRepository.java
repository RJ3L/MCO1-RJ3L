package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for {@link Customer} profiles.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Searches customers whose full name contains the keyword (case-sensitive).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param keyword part of a name, e.g. {@code "Ali"}
     * @return matching customers (empty list if none)
     */
    List<Customer> findByFullNameContaining(String keyword);
}
