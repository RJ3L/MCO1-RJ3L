package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class TransferRequestDTO {

    @NotNull(message = "Source account ID is required")
    private Long fromId;

    @NotNull(message = "Destination account ID is required")
    private Long toId;

    @NotNull(message = "Transfer amount must be greater than zero")
    @Positive(message = "Transfer amount must be greater than zero")
    private BigDecimal amount;

    public TransferRequestDTO() {
    }

    public Long getFromId() {
        return fromId;
    }

    public void setFromId(Long fromId) {
        this.fromId = fromId;
    }

    public Long getToId() {
        return toId;
    }

    public void setToId(Long toId) {
        this.toId = toId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
