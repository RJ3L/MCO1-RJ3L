package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for {@link ExchangeRate} rows.
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * Finds the rate for one direction of a currency pair.
     *
     * <p><b>Pre-condition:</b> codes should be upper-case ISO codes.
     * <b>Post-condition:</b> no data is changed.</p>
     *
     * @param baseCurrency  currency converted from
     * @param quoteCurrency currency converted to
     * @return the rate row, or {@link Optional#empty()} if that direction is not stored
     */
    Optional<ExchangeRate> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    /**
     * Checks whether a rate already exists for one direction of a currency pair.
     *
     * <p><b>Pre-condition:</b> codes should be upper-case ISO codes.
     * <b>Post-condition:</b> no data is changed.</p>
     *
     * @param baseCurrency  currency converted from
     * @param quoteCurrency currency converted to
     * @return {@code true} if a row exists
     */
    boolean existsByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);
}
