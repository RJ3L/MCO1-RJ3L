package com.fintech.walletapi.service;

import com.fintech.walletapi.dto.ExchangeRateDTO;
import com.fintech.walletapi.dto.ForexQuoteDTO;
import com.fintech.walletapi.entity.ExchangeRate;
import com.fintech.walletapi.entity.SupportedCurrency;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.repository.ExchangeRateRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ExchangeRateService {

    public static final int RATE_SCALE = 6;
    public static final int MONEY_SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 12;

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ExchangeRateDTO createRate(ExchangeRateDTO dto) {
        String base = SupportedCurrency.fromCode(dto.getBaseCurrency()).name();
        String quote = SupportedCurrency.fromCode(dto.getQuoteCurrency()).name();
        if (base.equals(quote)) {
            throw new InvalidTransactionException("Base and quote currency must be different");
        }
        if (exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(base, quote)) {
            throw new InvalidTransactionException("Exchange rate for " + base + " to " + quote
                    + " already exists; update it instead");
        }
        ExchangeRate rate = new ExchangeRate(base, quote, dto.getRate().setScale(RATE_SCALE, RoundingMode.HALF_EVEN));
        return convertToDTO(exchangeRateRepository.save(rate));
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateDTO> getAllRates() {
        return exchangeRateRepository.findAll().stream()
                .sorted(Comparator.comparing(ExchangeRate::getBaseCurrency)
                        .thenComparing(ExchangeRate::getQuoteCurrency))
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExchangeRateDTO getRateById(Long id) {
        return exchangeRateRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ExchangeRateDTO updateRate(Long id, ExchangeRateDTO dto) {
        ExchangeRate existing = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found"));
        existing.setRate(dto.getRate().setScale(RATE_SCALE, RoundingMode.HALF_EVEN));
        return convertToDTO(exchangeRateRepository.save(existing));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRate(Long id) {
        if (!exchangeRateRepository.existsById(id)) {
            throw new IllegalArgumentException("Exchange rate not found");
        }
        exchangeRateRepository.deleteById(id);
    }

    public BigDecimal getRate(String from, String to) {
        String base = SupportedCurrency.fromCode(from).name();
        String quote = SupportedCurrency.fromCode(to).name();

        if (base.equals(quote)) {
            return BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
        }
        Optional<BigDecimal> direct = findDirectOrInverse(base, quote);
        if (direct.isPresent()) {
            return direct.get().setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
        }
        String home = SupportedCurrency.HOME.name();
        if (!base.equals(home) && !quote.equals(home)) {
            Optional<BigDecimal> toHome = findDirectOrInverse(base, home);
            Optional<BigDecimal> fromHome = findDirectOrInverse(home, quote);
            if (toHome.isPresent() && fromHome.isPresent()) {
                return toHome.get().multiply(fromHome.get()).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
            }
        }
        throw new IllegalArgumentException("Exchange rate not found for " + base + " to " + quote);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        return amount.multiply(getRate(from, to)).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    @Transactional(readOnly = true)
    public ForexQuoteDTO getQuote(String from, String to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
        String base = SupportedCurrency.fromCode(from).name();
        String quote = SupportedCurrency.fromCode(to).name();
        BigDecimal rate = getRate(base, quote);
        BigDecimal converted = amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        return new ForexQuoteDTO(base, quote, amount, rate, converted);
    }

    private Optional<BigDecimal> findDirectOrInverse(String base, String quote) {
        Optional<ExchangeRate> direct = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote);
        if (direct.isPresent()) {
            return Optional.of(direct.get().getRate());
        }
        return exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(quote, base)
                .map(inverse -> BigDecimal.ONE.divide(inverse.getRate(), INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN));
    }

    private ExchangeRateDTO convertToDTO(ExchangeRate entity) {
        ExchangeRateDTO dto = new ExchangeRateDTO();
        dto.setId(entity.getId());
        dto.setBaseCurrency(entity.getBaseCurrency());
        dto.setQuoteCurrency(entity.getQuoteCurrency());
        dto.setRate(entity.getRate());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
