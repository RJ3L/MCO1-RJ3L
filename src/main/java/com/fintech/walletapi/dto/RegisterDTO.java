package com.fintech.walletapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/auth/register}: credentials plus the customer profile.
 */
public class RegisterDTO {

    /** Username. */
    @NotBlank(message = "Username is required")
    private String username;

    /** Plain-text password. */
    @NotBlank(message = "Password is required")
    private String password;

    /** Full name. */
    @NotBlank(message = "Full name is required")
    private String fullName;

    /** E-mail address. */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /** Phone number. */
    @Pattern(regexp = "^(09|\\+639)\\d{9}$", message = "Phone must be a Philippine mobile number such as 09171234567 or +639171234567")
    private String phone;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public RegisterDTO() {
    }

    /**
     * Returns the username.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getUsername()} returns the value.</p>
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the plain-text password.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the plain-text password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the plain-text password.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getPassword()} returns the value.</p>
     *
     * @param password the plain-text password
     */
    public void setPassword(String password) {
        this.password = password;
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
}
