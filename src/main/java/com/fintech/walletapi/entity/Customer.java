package com.fintech.walletapi.entity;

import jakarta.persistence.*;

/**
 * The bank profile of a person (name and contact details).
 *
 * <p>A customer is linked one-to-one with a {@link User} (login credentials)
 * and owns zero or more {@link Account wallets}.</p>
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String email;
    private String phone;

    /** Inverse side of the one-to-one link; the foreign key lives in {@code users.customer_id}. */
    @OneToOne(mappedBy = "customer")
    private User user;

    /**
     * Empty constructor required by JPA.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public Customer() {
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
     * Returns the customer's full name.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the customer's full name.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getFullName()} returns the value.</p>
     *
     * @param fullName the full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns the e-mail address.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the e-mail address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the e-mail address.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getEmail()} returns the value.</p>
     *
     * @param email the e-mail address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the phone number.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone number.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getPhone()} returns the value.</p>
     *
     * @param phone the phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the login linked to this customer.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the linked user, or {@code null} for a profile without a login
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the back-reference to the linked login.
     *
     * <p><b>Pre-condition:</b> the user's {@code customer} should point back to this customer.
     * <b>Post-condition:</b> {@code getUser()} returns the value.</p>
     *
     * @param user the linked user
     */
    public void setUser(User user) {
        this.user = user;
    }
}
