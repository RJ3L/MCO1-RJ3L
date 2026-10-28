package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 */
public class LoginDTO {

    /** Username. */
    @NotBlank(message = "Username is required")
    private String username;

    /** Plain-text password. */
    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Empty constructor required for JSON deserialization.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public LoginDTO() {
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
}
