package com.fintech.walletapi.dto;

import java.math.BigDecimal;

/**
 * Response body of {@code GET /api/v1/forex/quote}: a preview of a conversion
 * without moving any money.
 */
public class ForexQuoteDTO {

    /** Currency converted from. */
    private String fromCurrency;

    /** Currency converted to. */
    private String toCurrency;

    /** Amount to convert, in the from-currency. */
    private BigDecimal amount;

    /** Rate applied. */
    private BigDecimal rate;

    /** Resulting amount, in the to-currency. */
    private BigDecimal convertedAmount;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public ForexQuoteDTO() {
    }

    /**
     * Creates a fully populated quote.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param fromCurrency    currency converted from
     * @param toCurrency      currency converted to
     * @param amount          amount to convert
     * @param rate            rate applied
     * @param convertedAmount resulting amount
     */
    public ForexQuoteDTO(String fromCurrency, String toCurrency, BigDecimal amount,
                         BigDecimal rate, BigDecimal convertedAmount) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.amount = amount;
        this.rate = rate;
        this.convertedAmount = convertedAmount;
    }

    /**
     * Returns the currency converted from.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the currency converted from
     */
    public String getFromCurrency() {
        return fromCurrency;
    }

    /**
     * Sets the currency converted from.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getFromCurrency()} returns the value.</p>
     *
     * @param fromCurrency the currency converted from
     */
    public void setFromCurrency(String fromCurrency) {
        this.fromCurrency = fromCurrency;
    }

    /**
     * Returns the currency converted to.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the currency converted to
     */
    public String getToCurrency() {
        return toCurrency;
    }

    /**
     * Sets the currency converted to.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getToCurrency()} returns the value.</p>
     *
     * @param toCurrency the currency converted to
     */
    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    /**
     * Returns the amount to convert, in the from-currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the amount to convert, in the from-currency
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount to convert, in the from-currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getAmount()} returns the value.</p>
     *
     * @param amount the amount to convert, in the from-currency
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns the rate applied.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the rate applied
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate applied.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getRate()} returns the value.</p>
     *
     * @param rate the rate applied
     */
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    /**
     * Returns the resulting amount, in the to-currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the resulting amount, in the to-currency
     */
    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    /**
     * Sets the resulting amount, in the to-currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getConvertedAmount()} returns the value.</p>
     *
     * @param convertedAmount the resulting amount, in the to-currency
     */
    public void setConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
    }
}
