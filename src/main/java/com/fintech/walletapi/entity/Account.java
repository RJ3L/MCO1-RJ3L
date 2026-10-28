package com.fintech.walletapi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A wallet that holds money in exactly one currency.
 *
 * <p>A PHP account is a regular peso wallet; an account in any other
 * {@link SupportedCurrency} is a <b>forex wallet</b>. Every account belongs to
 * one {@link Customer}, and a customer may own several accounts but at most
 * one per currency (enforced by {@code AccountService} and a unique key on
 * {@code (customer_id, currency)}).</p>
 */
@Entity
@Table(name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_customer_currency",
                columnNames = {"customer_id", "currency"}))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: stale balance updates or deletes fail instead of losing money. */
    @Version
    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private long version;

    private String accountType;

    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    private String status;

    /** ISO 4217 code of the money held in this wallet (PHP, USD, ...). */
    @Column(length = 3)
    private String currency;

    /** The owner. LAZY: the customer is only loaded when it is actually used. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updateAt;

    /**
     * Empty constructor required by JPA.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public Account() {
    }

    /**
     * JPA hook that runs once, right before the row is first inserted.
     *
     * <p><b>Pre-condition:</b> the entity is new (not yet persisted).</p>
     * <p><b>Post-condition:</b> {@code createdAt} and {@code updateAt} hold the current time and,
     * if no currency was given, the wallet is a {@link SupportedCurrency#HOME} (PHP) wallet.</p>
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updateAt = LocalDateTime.now();
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = SupportedCurrency.HOME.name();
        }
    }

    /**
     * JPA hook that runs right before every update of an existing row.
     *
     * <p><b>Pre-condition:</b> the entity was already persisted.</p>
     * <p><b>Post-condition:</b> {@code updateAt} holds the current time; {@code createdAt} is untouched.</p>
     */
    @PreUpdate
    protected void onUpdate() {
        this.updateAt = LocalDateTime.now();
    }

    /**
     * Returns the database id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the id, or {@code null} before the account is saved
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
     * Returns the account type (e.g. SAVINGS, CHECKING).
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
     * @param accountType the account type, e.g. {@code "SAVINGS"}
     */
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    /**
     * Returns the current balance, expressed in {@link #getCurrency()}.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the balance
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Sets the balance.
     *
     * <p><b>Pre-condition:</b> {@code balance} should not be negative.
     * <b>Post-condition:</b> {@code getBalance()} returns the value.</p>
     *
     * @param balance the new balance
     */
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /**
     * Returns the account status (e.g. ACTIVE).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the account status.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getStatus()} returns the value.</p>
     *
     * @param status the new status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the ISO currency code of this wallet.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the currency code, e.g. {@code "PHP"} or {@code "USD"}
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the ISO currency code of this wallet.
     *
     * <p><b>Pre-condition:</b> {@code currency} should be a {@link SupportedCurrency} code.
     * <b>Post-condition:</b> {@code getCurrency()} returns the value.</p>
     *
     * @param currency the currency code
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * Returns the owning customer.
     *
     * <p><b>Pre-condition:</b> must be called inside a transaction (lazy relation).
     * <b>Post-condition:</b> no state change.</p>
     *
     * @return the owner, or {@code null} for an unlinked account
     */
    public Customer getCustomer() {
        return customer;
    }

    /**
     * Links the account to its owner.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getCustomer()} returns the value.</p>
     *
     * @param customer the owner
     */
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    /**
     * Returns when the account was created.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the creation time, or {@code null} before the first save
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the account was last changed.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the last update time, or {@code null} before the first save
     */
    public LocalDateTime getUpdateAt() {
        return updateAt;
    }
}
