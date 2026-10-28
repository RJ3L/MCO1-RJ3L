package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for {@link Account} wallets. CRUD methods are inherited from
 * {@link JpaRepository}; the methods below are Spring Data derived queries.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Finds every account with the given status.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param status the status to match, e.g. {@code "ACTIVE"}
     * @return matching accounts (empty list if none)
     */
    List<Account> findByStatus(String status);

    /**
     * Finds every wallet owned by the user with this username
     * (walks Account &rarr; Customer &rarr; User).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param username the owner's login name
     * @return the user's wallets (empty list if none)
     */
    List<Account> findByCustomer_User_Username(String username);

    /**
     * Checks whether a customer already holds a wallet in this currency.
     *
     * <p><b>Pre-condition:</b> {@code currency} is a normalized upper-case ISO code.
     * <b>Post-condition:</b> no data is changed.</p>
     *
     * @param customerId the owner's customer id
     * @param currency   the wallet currency, e.g. {@code "USD"}
     * @return {@code true} if such a wallet exists
     */
    boolean existsByCustomer_IdAndCurrency(Long customerId, String currency);

    /**
     * Checks whether a customer still owns any wallets before profile deletion.
     * <p><b>Pre-condition:</b> customerId identifies a profile. <b>Post-condition:</b> no data changes.</p>
     * @param customerId the profile id
     * @return true if at least one wallet references the customer
     */
    boolean existsByCustomer_Id(Long customerId);
}
