package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/transfers}, e.g.
 * {@code { "fromId": 3, "toId": 4, "amount": 100.00 }}.
 *
 * <p>The amount is always expressed in the <b>source</b> wallet's currency.</p>
 */
public class TransferRequestDTO {

    /** Source account id. */
    @NotNull(message = "Source account ID is required")
    private Long fromId;

    /** Destination account id. */
    @NotNull(message = "Destination account ID is required")
    private Long toId;

    /** Amount to send, in the source currency. */
    @NotNull(message = "Transfer amount must be greater than zero")
    @Positive(message = "Transfer amount must be greater than zero")
    private BigDecimal amount;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public TransferRequestDTO() {
    }

    /**
     * Returns the source account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the source account id
     */
    public Long getFromId() {
        return fromId;
    }

    /**
     * Sets the source account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getFromId()} returns the value.</p>
     *
     * @param fromId the source account id
     */
    public void setFromId(Long fromId) {
        this.fromId = fromId;
    }

    /**
     * Returns the destination account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the destination account id
     */
    public Long getToId() {
        return toId;
    }

    /**
     * Sets the destination account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getToId()} returns the value.</p>
     *
     * @param toId the destination account id
     */
    public void setToId(Long toId) {
        this.toId = toId;
    }

    /**
     * Returns the amount to send, in the source currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the amount to send, in the source currency
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount to send, in the source currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getAmount()} returns the value.</p>
     *
     * @param amount the amount to send, in the source currency
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
