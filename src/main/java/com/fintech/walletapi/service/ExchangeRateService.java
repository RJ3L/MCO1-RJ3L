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

/**
 * The forex engine: maintains the exchange-rate table (admin CRUD) and
 * converts amounts between supported currencies.
 *
 * <p>Rate lookup order for {@code from -> to}:</p>
 * <ol>
 *   <li>same currency &rarr; 1</li>
 *   <li>a stored {@code from -> to} row</li>
 *   <li>the inverse of a stored {@code to -> from} row</li>
 *   <li>a cross rate through PHP: {@code (from -> PHP) x (PHP -> to)}</li>
 * </ol>
 * <p>Rates are kept to 6 decimal places and money to 2, both rounded HALF_EVEN
 * (banker's rounding).</p>
 */
@Service
@Transactional
public class ExchangeRateService {

    /** Decimal places kept for exchange rates. */
    public static final int RATE_SCALE = 6;
    /** Decimal places kept for money. */
    public static final int MONEY_SCALE = 2;
    /** Extra precision used for intermediate (inverse) rates before final rounding. */
    private static final int INTERMEDIATE_SCALE = 12;

    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Creates the service.
     *
     * <p><b>Pre-condition:</b> injected dependencies are initialized. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param exchangeRateRepository exchange-rate repository
     */
    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Adds a new rate.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; both currencies are supported and different;
     * no rate exists yet for this direction.</p>
     * <p><b>Post-condition:</b> a new exchange-rate row exists.</p>
     *
     * @param dto base currency, quote currency and rate
     * @return the saved rate
     * @throws InvalidTransactionException if a currency is unsupported, both are equal, or the pair already exists (400)
     */
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

    /**
     * Lists every stored rate, sorted by base then quote currency.
     *
     * <p><b>Pre-condition:</b> caller is logged in.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @return all rates (empty list if none)
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateDTO> getAllRates() {
        return exchangeRateRepository.findAll().stream()
                .sorted(Comparator.comparing(ExchangeRate::getBaseCurrency)
                        .thenComparing(ExchangeRate::getQuoteCurrency))
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Reads one stored rate.
     *
     * <p><b>Pre-condition:</b> caller is logged in.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param id the rate id
     * @return the rate
     * @throws IllegalArgumentException if no rate has this id (404)
     */
    @Transactional(readOnly = true)
    public ExchangeRateDTO getRateById(Long id) {
        return exchangeRateRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found"));
    }

    /**
     * Changes the value of a stored rate. The currency pair itself cannot change.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN; {@code dto.rate} &gt; 0.</p>
     * <p><b>Post-condition:</b> the stored rate equals the new value and {@code updatedAt} is refreshed.</p>
     *
     * @param id  the rate id
     * @param dto carries the new rate
     * @return the updated rate
     * @throws IllegalArgumentException if no rate has this id (404)
     */
    @PreAuthorize("hasRole('ADMIN')")
    public ExchangeRateDTO updateRate(Long id, ExchangeRateDTO dto) {
        ExchangeRate existing = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found"));
        existing.setRate(dto.getRate().setScale(RATE_SCALE, RoundingMode.HALF_EVEN));
        return convertToDTO(exchangeRateRepository.save(existing));
    }

    /**
     * Deletes a stored rate.
     *
     * <p><b>Pre-condition:</b> caller is an ADMIN.</p>
     * <p><b>Post-condition:</b> the rate no longer exists; transfers needing it fail until it is re-added.</p>
     *
     * @param id the rate id
     * @throws IllegalArgumentException if no rate has this id (404)
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRate(Long id) {
        if (!exchangeRateRepository.existsById(id)) {
            throw new IllegalArgumentException("Exchange rate not found");
        }
        exchangeRateRepository.deleteById(id);
    }

    /**
     * Finds the rate that converts 1 unit of {@code from} into {@code to}.
     *
     * <p><b>Pre-condition:</b> both codes are supported currencies.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param from source currency code
     * @param to   target currency code
     * @return the rate, scaled to {@value #RATE_SCALE} decimals
     * @throws InvalidTransactionException if a code is not supported (400)
     * @throws IllegalArgumentException    if no direct, inverse or PHP cross rate exists (404)
     */
    public BigDecimal getRate(String from, String to) {
        // Normalize both codes; an unsupported code is rejected with 400
        String base = SupportedCurrency.fromCode(from).name();
        String quote = SupportedCurrency.fromCode(to).name();

        // 1. Same currency: nothing to convert
        if (base.equals(quote)) {
            return BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
        }
        // 2 + 3. A stored direct rate, else the inverse of the stored reverse rate
        Optional<BigDecimal> direct = findDirectOrInverse(base, quote);
        if (direct.isPresent()) {
            return direct.get().setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
        }
        // 4. Cross rate through PHP, e.g. USD -> PHP -> EUR; both legs must exist
        String home = SupportedCurrency.HOME.name();
        if (!base.equals(home) && !quote.equals(home)) {
            Optional<BigDecimal> toHome = findDirectOrInverse(base, home);
            Optional<BigDecimal> fromHome = findDirectOrInverse(home, quote);
            if (toHome.isPresent() && fromHome.isPresent()) {
                return toHome.get().multiply(fromHome.get()).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
            }
        }
        // No route at all: fail (404) rather than invent a rate
        throw new IllegalArgumentException("Exchange rate not found for " + base + " to " + quote);
    }

    /**
     * Converts an amount between currencies.
     *
     * <p><b>Pre-condition:</b> {@code amount} is not null; both codes are supported.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param amount amount in the {@code from} currency
     * @param from   source currency code
     * @param to     target currency code
     * @return the converted amount, rounded HALF_EVEN to 2 decimals
     * @throws IllegalArgumentException if no rate exists (404)
     */
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        return amount.multiply(getRate(from, to)).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Previews a conversion without moving any money.
     *
     * <p><b>Pre-condition:</b> {@code amount} &gt; 0; both codes are supported.</p>
     * <p><b>Post-condition:</b> no data is changed.</p>
     *
     * @param from   source currency code
     * @param to     target currency code
     * @param amount amount in the {@code from} currency
     * @return the quote (rate and converted amount)
     * @throws InvalidTransactionException if the amount is missing or not positive, or a code is unsupported (400)
     * @throws IllegalArgumentException    if no rate exists (404)
     */
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

    /**
     * Looks up a stored rate in either direction.
     *
     * <p><b>Pre-condition:</b> codes are normalized upper-case ISO codes.
     * <b>Post-condition:</b> no data is changed.</p>
     *
     * @param base  currency converted from
     * @param quote currency converted to
     * @return the direct rate, else {@code 1 / inverse rate} (kept at 12 decimals so a later
     *         cross-rate multiplication does not lose precision), else empty
     */
    private Optional<BigDecimal> findDirectOrInverse(String base, String quote) {
        Optional<ExchangeRate> direct = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote);
        if (direct.isPresent()) {
            return Optional.of(direct.get().getRate());
        }
        return exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(quote, base)
                .map(inverse -> BigDecimal.ONE.divide(inverse.getRate(), INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN));
    }

    /**
     * Maps an entity to its DTO.
     *
     * <p><b>Pre-condition:</b> {@code entity} is not null. <b>Post-condition:</b> no data is changed.</p>
     *
     * @param entity the rate entity
     * @return the DTO
     */
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
