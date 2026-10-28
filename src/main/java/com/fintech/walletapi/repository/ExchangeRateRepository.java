package com.fintech.walletapi.repository;

import com.fintech.walletapi.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    boolean existsByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);
}
