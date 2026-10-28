package com.fintech.walletapi.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Login credentials of a GMoney user.
 *
 * <p>Implements Spring Security's {@link UserDetails} so it can be loaded
 * directly by {@code CustomUserDetailsService}. The role is either
 * {@code ROLE_CUSTOMER} or {@code ROLE_ADMIN}.</p>
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    /** BCrypt hash — never the plain-text password. */
    @Column(length = 255)
    private String password;

    private String role = "ROLE_CUSTOMER";

    /** Owning side of the one-to-one link (holds the {@code customer_id} foreign key). */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * Empty constructor required by JPA.
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> a new empty instance is ready for persistence or deserialization.</p>
     */
    public User() {
    }

    /**
     * Returns the authorities (roles) granted to this user.
     *
     * <p><b>Pre-condition:</b> none.</p>
     * <p><b>Post-condition:</b> no state change; a blank or {@code null} role is treated as {@code ROLE_CUSTOMER}.</p>
     *
     * @return a single-element list holding the user's role
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(this.role == null || this.role.isBlank()
                ? "ROLE_CUSTOMER" : this.role));
    }

    /**
     * Returns the hashed password.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the BCrypt hash
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * Returns the unique username.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the username
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * Reports whether the account is not expired (GMoney never expires accounts).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return always {@code true}
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Reports whether the account is not locked (GMoney never locks accounts).
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return always {@code true}
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Reports whether the credentials are not expired.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return always {@code true}
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Reports whether the user is enabled.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return always {@code true}
     */
    @Override
    public boolean isEnabled() {
        return true;
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
     * Sets the username.
     *
     * <p><b>Pre-condition:</b> the username must not already exist.
     * <b>Post-condition:</b> {@code getUsername()} returns the value.</p>
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Sets the password hash.
     *
     * <p><b>Pre-condition:</b> {@code password} must already be hashed with the {@code PasswordEncoder}.
     * <b>Post-condition:</b> {@code getPassword()} returns the value.</p>
     *
     * @param password the BCrypt hash
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the role.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return {@code ROLE_CUSTOMER} or {@code ROLE_ADMIN}
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role.
     *
     * <p><b>Pre-condition:</b> {@code role} should be {@code ROLE_CUSTOMER} or {@code ROLE_ADMIN}.
     * <b>Post-condition:</b> {@code getRole()} returns the value.</p>
     *
     * @param role the role
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns the linked bank profile.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> no state change.</p>
     *
     * @return the customer, or {@code null}
     */
    public Customer getCustomer() {
        return customer;
    }

    /**
     * Links this login to a bank profile.
     *
     * <p><b>Pre-condition:</b> none. <b>Post-condition:</b> {@code getCustomer()} returns the value.</p>
     *
     * @param customer the customer
     */
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}
