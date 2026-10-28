package com.fintech.walletapi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One entry of the immutable transfer ledger.
 *
 * <p>A record is written once, when a transfer succeeds, and is never changed
 * afterwards. For a cross-currency (forex) transfer it also stores both
 * currencies, the exchange rate used and the amount credited to the
 * destination, so the conversion can always be audited.</p>
 */
@Entity
@Table(name = "transfer_records")
public class TransferRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sourceAccountId;

    @Column(nullable = false)
    private Long destinationAccountId;

    /** Amount debited from the source, in the source currency. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Set once on insert; {@code updatable = false} keeps it from ever changing. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime transferDate;

    @Column(length = 3)
    private String sourceCurrency;

    @Column(length = 3)
    private String destinationCurrency;

    /** Amount credited to the destination, in the destination currency. */
    @Column(precision = 19, scale = 2)
    private BigDecimal convertedAmount;

    /** Source-to-destination rate used (1 for same-currency transfers). */
    @Column(precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    /**
     * Empty constructor required by JPA.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public TransferRecord() {
    }

    /**
     * JPA hook that stamps the transfer time when the ledger row is inserted.
     *
     * <p><b>Pre-condition:</b> the record is new (never persisted).</p>
     * <p><b>Post-condition:</b> {@code transferDate} holds the current time.</p>
     */
    @PrePersist
    protected void onCreate() {
        this.transferDate = LocalDateTime.now();
    }

    /**
     * Returns the ledger entry id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the id, or {@code null} before saving
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the ledger entry id.
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
     * Returns the id of the account the money left.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the source account id
     */
    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    /**
     * Sets the id of the account the money left.
     *
     * <p><b>Pre-condition:</b> must reference an existing account.
     * <b>Post-condition:</b> {@code getSourceAccountId()} returns the value.</p>
     *
     * @param sourceAccountId the source account id
     */
    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    /**
     * Returns the id of the account that received the money.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the destination account id
     */
    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    /**
     * Sets the id of the account that received the money.
     *
     * <p><b>Pre-condition:</b> must reference an existing account.
     * <b>Post-condition:</b> {@code getDestinationAccountId()} returns the value.</p>
     *
     * @param destinationAccountId the destination account id
     */
    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    /**
     * Returns the amount debited, in the source currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the amount
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount debited, in the source currency.
     *
     * <p><b>Pre-condition:</b> {@code amount} &gt; 0.
     * <b>Post-condition:</b> {@code getAmount()} returns the value.</p>
     *
     * @param amount the amount
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Returns when the transfer happened.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the transfer time, or {@code null} before saving
     */
    public LocalDateTime getTransferDate() {
        return transferDate;
    }

    /**
     * Sets the transfer time (normally done by {@link #onCreate()}; used by tests).
     *
     * <p><b>Pre-condition:</b> the record is not yet saved — the column is not updatable.
     * <b>Post-condition:</b> {@code getTransferDate()} returns the value.</p>
     *
     * @param transferDate the transfer time
     */
    public void setTransferDate(LocalDateTime transferDate) {
        this.transferDate = transferDate;
    }

    /**
     * Returns the currency of the source wallet.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the source currency code
     */
    public String getSourceCurrency() {
        return sourceCurrency;
    }

    /**
     * Sets the currency of the source wallet.
     *
     * <p><b>Pre-condition:</b> a {@link SupportedCurrency} code.
     * <b>Post-condition:</b> {@code getSourceCurrency()} returns the value.</p>
     *
     * @param sourceCurrency the source currency code
     */
    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    /**
     * Returns the currency of the destination wallet.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the destination currency code
     */
    public String getDestinationCurrency() {
        return destinationCurrency;
    }

    /**
     * Sets the currency of the destination wallet.
     *
     * <p><b>Pre-condition:</b> a {@link SupportedCurrency} code.
     * <b>Post-condition:</b> {@code getDestinationCurrency()} returns the value.</p>
     *
     * @param destinationCurrency the destination currency code
     */
    public void setDestinationCurrency(String destinationCurrency) {
        this.destinationCurrency = destinationCurrency;
    }

    /**
     * Returns the amount credited, in the destination currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the credited amount
     */
    public BigDecimal getConvertedAmount() {
        return convertedAmount;
    }

    /**
     * Sets the amount credited, in the destination currency.
     *
     * <p><b>Pre-condition:</b> {@code convertedAmount} &gt;= 0.
     * <b>Post-condition:</b> {@code getConvertedAmount()} returns the value.</p>
     *
     * @param convertedAmount the credited amount
     */
    public void setConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    /**
     * Returns the exchange rate applied.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the rate (1 for same-currency transfers)
     */
    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    /**
     * Sets the exchange rate applied.
     *
     * <p><b>Pre-condition:</b> {@code exchangeRate} &gt; 0.
     * <b>Post-condition:</b> {@code getExchangeRate()} returns the value.</p>
     *
     * @param exchangeRate the rate
     */
    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }
}
