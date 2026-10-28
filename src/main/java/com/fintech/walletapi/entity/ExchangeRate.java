package com.fintech.walletapi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A conversion rate between two supported currencies, maintained by admins.
 *
 * <p>The row {@code USD -> PHP = 58.000000} means 1 USD buys 58 PHP. Only one
 * row may exist per (base, quote) pair. The reverse direction does not need
 * its own row: the exchange-rate service uses the inverse automatically.</p>
 */
@Entity
@Table(name = "exchange_rates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"base_currency", "quote_currency"}))
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Currency being converted from (e.g. USD). */
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    /** Currency being converted to (e.g. PHP). */
    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    /** Units of quote currency per 1 unit of base currency. */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    private LocalDateTime updatedAt;

    /**
     * Empty constructor required by JPA.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public ExchangeRate() {
    }

    /**
     * Convenience constructor used by the seeder and tests.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param baseCurrency  currency converted from
     * @param quoteCurrency currency converted to
     * @param rate          units of quote per 1 base
     */
    public ExchangeRate(String baseCurrency, String quoteCurrency, BigDecimal rate) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
    }

    /**
     * JPA hook that stamps {@code updatedAt} on insert and on every update.
     *
     * <p><b>Pre-condition:</b> the entity is about to be inserted or updated.</p>
     * <p><b>Post-condition:</b> {@code updatedAt} holds the current time.</p>
     */
    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns the database id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the id, or {@code null} before saving
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the database id.
     *
     * <p><b>Pre-condition:</b> normally only used by JPA and tests.
     * <b>Post-condition:</b> {@code getId()} returns {@code id}.</p>
     *
     * @param id the new id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the base (from) currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the base currency code
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Sets the base (from) currency.
     *
     * <p><b>Pre-condition:</b> a {@link SupportedCurrency} code.
     * <b>Post-condition:</b> {@code getBaseCurrency()} returns the value.</p>
     *
     * @param baseCurrency the base currency code
     */
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    /**
     * Returns the quote (to) currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the quote currency code
     */
    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    /**
     * Sets the quote (to) currency.
     *
     * <p><b>Pre-condition:</b> a {@link SupportedCurrency} code different from the base.
     * <b>Post-condition:</b> {@code getQuoteCurrency()} returns the value.</p>
     *
     * @param quoteCurrency the quote currency code
     */
    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    /**
     * Returns the rate (units of quote per 1 base).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the rate
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate.
     *
     * <p><b>Pre-condition:</b> {@code rate} &gt; 0.
     * <b>Post-condition:</b> {@code getRate()} returns the value.</p>
     *
     * @param rate the rate
     */
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    /**
     * Returns when the rate was last changed.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the last update time, or {@code null} before saving
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
