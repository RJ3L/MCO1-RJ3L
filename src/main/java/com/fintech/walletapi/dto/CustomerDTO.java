package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;

/**
 * Request/response body for a customer profile.
 */
public class CustomerDTO {

    /** Customer id. */
    private Long id;

    /** Full name. */
    @NotBlank(message = "Customer name is required")
    private String fullName;

    /** E-mail address. */
    @Email(message = "Email must be a valid email address")
    private String email;

    /** Phone number. */
    @Pattern(regexp = "^(09|\\+639)\\d{9}$", message = "Phone must be a Philippine mobile number such as 09171234567 or +639171234567")
    private String phone;

    /** Username of the linked login. */
    private String username;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public CustomerDTO() {
    }

    /**
     * Creates a fully populated customer DTO.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param id       customer id
     * @param fullName full name
     * @param email    e-mail address
     * @param phone    phone number
     * @param username username of the linked login
     */
    public CustomerDTO(Long id, String fullName, String email, String phone, String username) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
    }

    /**
     * Creates a DTO with only the basic details.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new instance is initialized with the supplied values.</p>
     *
     * @param id       customer id
     * @param fullName full name
     */
    public CustomerDTO(Long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    /**
     * Returns the customer id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the customer id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the customer id.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getId()} returns the value.</p>
     *
     * @param id the customer id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the full name.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name.
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
     * Returns the username of the linked login.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the username of the linked login
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username of the linked login.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getUsername()} returns the value.</p>
     *
     * @param username the username of the linked login
     */
    public void setUsername(String username) {
        this.username = username;
    }
}
