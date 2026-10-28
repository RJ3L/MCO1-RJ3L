package com.fintech.walletapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only view of one ledger entry, returned by the transfer-history endpoint.
 *
 * <p>{@code direction} is {@code SENT} or {@code RECEIVED} relative to the
 * account whose history was requested.</p>
 */
public class TransferRecordDTO {

    /** Ledger entry id. */
    private Long id;

    /** Source account id. */
    private Long sourceAccountId;

    /** Destination account id. */
    private Long destinationAccountId;

    /** Amount debited in the source currency. */
    private BigDecimal amount;

    /** Time of the transfer. */
    private LocalDateTime transferDate;

    /** Source currency code. */
    private String sourceCurrency;

    /** Destination currency code. */
    private String destinationCurrency;

    /** Amount credited in the destination currency. */
    private BigDecimal convertedAmount;

    /** Exchange rate applied. */
    private BigDecimal exchangeRate;

    /** Direction relative to the requested account (SENT or RECEIVED). */
    private String direction;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public TransferRecordDTO() {
    }

    /**
     * Returns the ledger entry id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the ledger entry id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the ledger entry id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getId()} returns the value.</p>
     *
     * @param id the ledger entry id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the source account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the source account id
     */
    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    /**
     * Sets the source account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getSourceAccountId()} returns the value.</p>
     *
     * @param sourceAccountId the source account id
     */
    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    /**
     * Returns the destination account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the destination account id
     */
    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    /**
     * Sets the destination account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getDestinationAccountId()} returns the value.</p>
     *
     * @param destinationAccountId the destination account id
     */
    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    /**
     * Returns the amount debited in the source currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the amount debited in the source currency
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount debited in the source currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getAmount()} returns the value.</p>
     *
     * @param amount the amount debited in the source currency
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns the time of the transfer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the time of the transfer
     */
    public LocalDateTime getTransferDate() {
        return transferDate;
    }

    /**
     * Sets the time of the transfer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getTransferDate()} returns the value.</p>
     *
     * @param transferDate the time of the transfer
     */
    public void setTransferDate(LocalDateTime transferDate) {
        this.transferDate = transferDate;
    }

    /**
     * Returns the source currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the source currency code
     */
    public String getSourceCurrency() {
        return sourceCurrency;
    }

    /**
     * Sets the source currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getSourceCurrency()} returns the value.</p>
     *
     * @param sourceCurrency the source currency code
     */
    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    /**
     * Returns the destination currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the destination currency code
     */
    public String getDestinationCurrency() {
        return destinationCurrency;
    }

    /**
     * Sets the destination currency code.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getDestinationCurrency()} returns the value.</p>
     *
     * @param destinationCurrency the destination currency code
     */
    public void setDestinationCurrency(String destinationCurrency) {
        this.destinationCurrency = destinationCurrency;
    }

    /**
     * Returns the amount credited in the destination currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the amount credited in the destination currency
     */
    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    /**
     * Sets the amount credited in the destination currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getConvertedAmount()} returns the value.</p>
     *
     * @param convertedAmount the amount credited in the destination currency
     */
    public void setConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    /**
     * Returns the exchange rate applied.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the exchange rate applied
     */
    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    /**
     * Sets the exchange rate applied.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getExchangeRate()} returns the value.</p>
     *
     * @param exchangeRate the exchange rate applied
     */
    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    /**
     * Returns the direction relative to the requested account (SENT or RECEIVED).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the direction relative to the requested account (SENT or RECEIVED)
     */
    public String getDirection() {
        return direction;
    }

    /**
     * Sets the direction relative to the requested account (SENT or RECEIVED).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getDirection()} returns the value.</p>
     *
     * @param direction the direction relative to the requested account (SENT or RECEIVED)
     */
    public void setDirection(String direction) {
        this.direction = direction;
    }
}
