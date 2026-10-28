package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request/response body for an exchange rate, e.g.
 * {@code { "baseCurrency": "USD", "quoteCurrency": "PHP", "rate": 58.25 }}
 * (1 USD = 58.25 PHP).
 */
public class ExchangeRateDTO {

    /** Rate id. */
    private Long id;

    /** Base (from) currency code. */
    @NotBlank(message = "Base currency is required")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code such as PHP or USD")
    private String baseCurrency;

    /** Quote (to) currency code. */
    @NotBlank(message = "Quote currency is required")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code such as PHP or USD")
    private String quoteCurrency;

    /** Units of quote currency per 1 base currency. */
    @NotNull(message = "Rate is required")
    @Positive(message = "Rate must be greater than zero")
    private BigDecimal rate;

    /** Time of the last change. */
    private LocalDateTime updatedAt;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public ExchangeRateDTO() {
    }

    /**
     * Returns the rate id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the rate id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the rate id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getId()} returns the value.</p>
     *
     * @param id the rate id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the base (from) currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the base (from) currency code
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Sets the base (from) currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getBaseCurrency()} returns the value.</p>
     *
     * @param baseCurrency the base (from) currency code
     */
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    /**
     * Returns the quote (to) currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the quote (to) currency code
     */
    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    /**
     * Sets the quote (to) currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getQuoteCurrency()} returns the value.</p>
     *
     * @param quoteCurrency the quote (to) currency code
     */
    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    /**
     * Returns the units of quote currency per 1 base currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the units of quote currency per 1 base currency
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the units of quote currency per 1 base currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getRate()} returns the value.</p>
     *
     * @param rate the units of quote currency per 1 base currency
     */
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    /**
     * Returns the time of the last change.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the time of the last change
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the time of the last change.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getUpdatedAt()} returns the value.</p>
     *
     * @param updatedAt the time of the last change
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
