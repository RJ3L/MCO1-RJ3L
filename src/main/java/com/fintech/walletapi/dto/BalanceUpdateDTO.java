package com.fintech.walletapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request body for an administrator's balance adjustment. */
public class BalanceUpdateDTO {
    @NotNull(message = "Balance is required")
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Digits(integer = 17, fraction = 2, message = "Balance must have at most 17 integer digits and 2 decimal places")
    private BigDecimal balance;

    /**
     * Reads the requested balance.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     * @return the requested balance, or null before deserialization
     */
    public BigDecimal getBalance() { return balance; }

    /**
     * Sets the requested balance; validation happens at the API boundary.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> getBalance returns the supplied value.</p>
     * @param balance the requested amount
     */
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
