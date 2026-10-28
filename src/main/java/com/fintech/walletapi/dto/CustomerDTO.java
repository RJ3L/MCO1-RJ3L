package com.fintech.walletapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;

public class CustomerDTO {

    private Long id;

    @NotBlank(message = "Customer name is required")
    private String fullName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @Pattern(regexp = "^(09|\\+639)\\d{9}$", message = "Phone must be a Philippine mobile number such as 09171234567 or +639171234567")
    private String phone;

    private String username;

    public CustomerDTO() {
    }

    public CustomerDTO(Long id, String fullName, String email, String phone, String username) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
    }

    public CustomerDTO(Long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
