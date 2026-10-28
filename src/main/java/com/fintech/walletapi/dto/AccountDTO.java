package com.fintech.walletapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Request/response body for a wallet (account).
 *
 * <p>{@code currency} is optional on create and defaults to PHP. Any other
 * supported currency (USD, EUR, GBP, SGD) creates a forex wallet.
 * {@code username} is filled in by the service and used by
 * {@code @PostAuthorize} to verify ownership.</p>
 */
public class AccountDTO {

    /** Account id. */
    private Long id;

    /** Id of the owning customer. */
    private Long customerId;

    /** Full name of the owning customer. */
    private String customerName;

    /** Balance in the wallet currency. */
    @NotNull(message = "Balance is required")
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    private BigDecimal balance;

    /** Account status. */
    private String status;

    /** Account type. */
    @NotBlank(message = "Account type is required")
    private String accountType;

    /** ISO currency code of the wallet. */
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code such as PHP or USD")
    private String currency;

    /** Username of the owner. */
    private String username;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public AccountDTO() {
    }

    /**
     * Returns the account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the account id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the account id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getId()} returns the value.</p>
     *
     * @param id the account id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the id of the owning customer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the id of the owning customer
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * Sets the id of the owning customer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getCustomerId()} returns the value.</p>
     *
     * @param customerId the id of the owning customer
     */
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    /**
     * Returns the full name of the owning customer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the full name of the owning customer
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * Sets the full name of the owning customer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getCustomerName()} returns the value.</p>
     *
     * @param customerName the full name of the owning customer
     */
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    /**
     * Returns the balance in the wallet currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the balance in the wallet currency
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Sets the balance in the wallet currency.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getBalance()} returns the value.</p>
     *
     * @param balance the balance in the wallet currency
     */
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /**
     * Returns the account status.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the account status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the account status.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getStatus()} returns the value.</p>
     *
     * @param status the account status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the account type.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the account type
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * Sets the account type.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getAccountType()} returns the value.</p>
     *
     * @param accountType the account type
     */
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    /**
     * Returns the ISO currency code of the wallet.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the ISO currency code of the wallet
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the ISO currency code of the wallet.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getCurrency()} returns the value.</p>
     *
     * @param currency the ISO currency code of the wallet
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Returns the username of the owner.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the username of the owner
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username of the owner.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getUsername()} returns the value.</p>
     *
     * @param username the username of the owner
     */
    public void setUsername(String username) {
        this.username = username;
    }
}
