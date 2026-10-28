package com.fintech.walletapi.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for entity behaviour beyond plain getters/setters: JPA lifecycle hooks
 * and the {@code UserDetails} methods of {@link User}.
 */
class EntityLifecycleTest {

    /**
     * TC-ENT-01: tests {@code Account.onCreate}.
     *
     * <p><b>Pre-condition:</b> New account.</p>
     * <p><b>Post-condition:</b> CreatedAt and updateAt set.</p>
     */
    @Test
    @DisplayName("TC-ENT-01 | Account.onCreate | new account -> createdAt and updateAt set")
    void account_onCreate_timestamps() {
        Account a = new Account();
        a.onCreate();
        assertNotNull(a.getCreatedAt());
        assertNotNull(a.getUpdateAt());
    }

    /**
     * TC-ENT-02: tests {@code Account.onCreate}.
     *
     * <p><b>Pre-condition:</b> No currency.</p>
     * <p><b>Post-condition:</b> Defaults to PHP.</p>
     */
    @Test
    @DisplayName("TC-ENT-02 | Account.onCreate | no currency -> defaults to PHP")
    void account_onCreate_defaultCurrency() {
        Account a = new Account();
        a.onCreate();
        assertEquals("PHP", a.getCurrency());
    }

    /**
     * TC-ENT-03: tests {@code Account.onCreate}.
     *
     * <p><b>Pre-condition:</b> USD given.</p>
     * <p><b>Post-condition:</b> Kept as USD.</p>
     */
    @Test
    @DisplayName("TC-ENT-03 | Account.onCreate | USD given -> kept as USD")
    void account_onCreate_keepsCurrency() {
        Account a = new Account();
        a.setCurrency("USD");
        a.onCreate();
        assertEquals("USD", a.getCurrency());
    }

    /**
     * TC-ENT-04: tests {@code Account.onUpdate}.
     *
     * <p><b>Pre-condition:</b> Update.</p>
     * <p><b>Post-condition:</b> UpdateAt refreshed.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-ENT-04 | Account.onUpdate | update -> updateAt refreshed")
    void account_onUpdate_refreshes() throws Exception {
        Account a = new Account();
        a.onCreate();
        LocalDateTime first = a.getUpdateAt();
        Thread.sleep(5);
        a.onUpdate();
        assertTrue(a.getUpdateAt().isAfter(first));
    }

    /**
     * TC-ENT-05: tests {@code Account.onUpdate}.
     *
     * <p><b>Pre-condition:</b> Update.</p>
     * <p><b>Post-condition:</b> CreatedAt unchanged.</p>
     */
    @Test
    @DisplayName("TC-ENT-05 | Account.onUpdate | update -> createdAt unchanged")
    void account_onUpdate_keepsCreated() {
        Account a = new Account();
        a.onCreate();
        LocalDateTime created = a.getCreatedAt();
        a.onUpdate();
        assertEquals(created, a.getCreatedAt());
    }

    /**
     * TC-ENT-06: tests {@code Account.onUpdate}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Currency and balance untouched.</p>
     */
    @Test
    @DisplayName("TC-ENT-06 | Account.onUpdate | currency and balance untouched")
    void account_onUpdate_keepsData() {
        Account a = new Account();
        a.setCurrency("EUR");
        a.setBalance(BigDecimal.TEN);
        a.onUpdate();
        assertEquals("EUR", a.getCurrency());
        assertEquals(BigDecimal.TEN, a.getBalance());
    }

    /**
     * TC-ENT-07: tests {@code TransferRecord.onCreate}.
     *
     * <p><b>Pre-condition:</b> Insert.</p>
     * <p><b>Post-condition:</b> TransferDate set.</p>
     */
    @Test
    @DisplayName("TC-ENT-07 | TransferRecord.onCreate | insert -> transferDate set")
    void transfer_onCreate_sets() {
        TransferRecord r = new TransferRecord();
        r.onCreate();
        assertNotNull(r.getTransferDate());
    }

    /**
     * TC-ENT-08: tests {@code TransferRecord.onCreate}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> TransferDate is 'now' (within 5 seconds).</p>
     */
    @Test
    @DisplayName("TC-ENT-08 | TransferRecord.onCreate | transferDate is 'now' (within 5 seconds)")
    void transfer_onCreate_now() {
        TransferRecord r = new TransferRecord();
        r.onCreate();
        assertTrue(r.getTransferDate().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    /**
     * TC-ENT-09: tests {@code TransferRecord.onCreate}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Amount and ids untouched.</p>
     */
    @Test
    @DisplayName("TC-ENT-09 | TransferRecord.onCreate | amount and ids untouched")
    void transfer_onCreate_keepsData() {
        TransferRecord r = new TransferRecord();
        r.setAmount(BigDecimal.ONE);
        r.setSourceAccountId(1L);
        r.onCreate();
        assertEquals(BigDecimal.ONE, r.getAmount());
        assertEquals(1L, r.getSourceAccountId());
    }

    /**
     * TC-ENT-10: tests {@code ExchangeRate.touch}.
     *
     * <p><b>Pre-condition:</b> Insert/update.</p>
     * <p><b>Post-condition:</b> UpdatedAt set.</p>
     */
    @Test
    @DisplayName("TC-ENT-10 | ExchangeRate.touch | insert/update -> updatedAt set")
    void rate_touch_sets() {
        ExchangeRate r = new ExchangeRate("USD", "PHP", BigDecimal.TEN);
        r.touch();
        assertNotNull(r.getUpdatedAt());
    }

    /**
     * TC-ENT-11: tests {@code ExchangeRate.touch}.
     *
     * <p><b>Pre-condition:</b> Second touch.</p>
     * <p><b>Post-condition:</b> Later timestamp.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test
    @DisplayName("TC-ENT-11 | ExchangeRate.touch | second touch -> later timestamp")
    void rate_touch_refreshes() throws Exception {
        ExchangeRate r = new ExchangeRate("USD", "PHP", BigDecimal.TEN);
        r.touch();
        LocalDateTime first = r.getUpdatedAt();
        Thread.sleep(5);
        r.touch();
        assertTrue(r.getUpdatedAt().isAfter(first));
    }

    /**
     * TC-ENT-12: tests {@code ExchangeRate.touch}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Pair and rate untouched.</p>
     */
    @Test
    @DisplayName("TC-ENT-12 | ExchangeRate.touch | pair and rate untouched")
    void rate_touch_keepsData() {
        ExchangeRate r = new ExchangeRate("EUR", "PHP", new BigDecimal("63"));
        r.touch();
        assertEquals("EUR", r.getBaseCurrency());
        assertEquals("PHP", r.getQuoteCurrency());
        assertEquals(new BigDecimal("63"), r.getRate());
    }

    /**
     * TC-ENT-13: tests {@code User.getAuthorities}.
     *
     * <p><b>Pre-condition:</b> Default role.</p>
     * <p><b>Post-condition:</b> ROLE_CUSTOMER.</p>
     */
    @Test
    @DisplayName("TC-ENT-13 | User.getAuthorities | default role -> ROLE_CUSTOMER")
    void user_defaultRole() {
        GrantedAuthority a = new User().getAuthorities().iterator().next();
        assertEquals("ROLE_CUSTOMER", a.getAuthority());
    }

    /**
     * TC-ENT-14: tests {@code User.getAuthorities}.
     *
     * <p><b>Pre-condition:</b> ROLE_ADMIN.</p>
     * <p><b>Post-condition:</b> ROLE_ADMIN.</p>
     */
    @Test
    @DisplayName("TC-ENT-14 | User.getAuthorities | ROLE_ADMIN -> ROLE_ADMIN")
    void user_adminRole() {
        User u = new User();
        u.setRole("ROLE_ADMIN");
        assertEquals("ROLE_ADMIN", u.getAuthorities().iterator().next().getAuthority());
    }

    /**
     * TC-ENT-15: tests {@code User.getAuthorities}.
     *
     * <p><b>Pre-condition:</b> Blank or null role.</p>
     * <p><b>Post-condition:</b> Falls back to ROLE_CUSTOMER.</p>
     */
    @Test
    @DisplayName("TC-ENT-15 | User.getAuthorities | blank or null role -> falls back to ROLE_CUSTOMER")
    void user_blankRole() {
        User u = new User();
        u.setRole(" ");
        assertEquals("ROLE_CUSTOMER", u.getAuthorities().iterator().next().getAuthority());
        u.setRole(null);
        assertEquals("ROLE_CUSTOMER", u.getAuthorities().iterator().next().getAuthority());
    }

    /**
     * TC-ENT-16: tests {@code User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired /
     * isEnabled}.
     *
     * <p><b>Pre-condition:</b> New user.</p>
     * <p><b>Post-condition:</b> All true.</p>
     */
    @Test
    @DisplayName("TC-ENT-16 | User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired / isEnabled | new user -> all true")
    void user_flags_new() {
        User u = new User();
        assertTrue(u.isAccountNonExpired());
        assertTrue(u.isAccountNonLocked());
        assertTrue(u.isCredentialsNonExpired());
        assertTrue(u.isEnabled());
    }

    /**
     * TC-ENT-17: tests {@code User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired /
     * isEnabled}.
     *
     * <p><b>Pre-condition:</b> Admin.</p>
     * <p><b>Post-condition:</b> All true.</p>
     */
    @Test
    @DisplayName("TC-ENT-17 | User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired / isEnabled | admin -> all true")
    void user_flags_admin() {
        User u = new User();
        u.setRole("ROLE_ADMIN");
        assertTrue(u.isAccountNonExpired());
        assertTrue(u.isAccountNonLocked());
        assertTrue(u.isCredentialsNonExpired());
        assertTrue(u.isEnabled());
    }

    /**
     * TC-ENT-18: tests {@code User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired /
     * isEnabled}.
     *
     * <p><b>Pre-condition:</b> User with profile.</p>
     * <p><b>Post-condition:</b> All true.</p>
     */
    @Test
    @DisplayName("TC-ENT-18 | User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired / isEnabled | user with profile -> all true")
    void user_flags_withProfile() {
        User u = new User();
        u.setUsername("alice");
        u.setCustomer(new Customer());
        assertTrue(u.isAccountNonExpired());
        assertTrue(u.isAccountNonLocked());
        assertTrue(u.isCredentialsNonExpired());
        assertTrue(u.isEnabled());
    }


    /**
     * TC-ENT-19: tests {@code Account.getCreatedAt}.
     *
     * <p><b>Pre-condition:</b> Never persisted.</p>
     * <p><b>Post-condition:</b> Null until onCreate runs.</p>
     */
    @Test
    @DisplayName("TC-ENT-19 | Account.getCreatedAt | never persisted -> null until onCreate runs")
    void account_getCreatedAt_beforePersist() {
        Account a = new Account();
        assertNull(a.getCreatedAt());
        a.onCreate();
        assertNotNull(a.getCreatedAt());
    }

    /**
     * TC-ENT-20: tests {@code Account.getUpdateAt}.
     *
     * <p><b>Pre-condition:</b> Never persisted.</p>
     * <p><b>Post-condition:</b> Null until onCreate runs.</p>
     */
    @Test
    @DisplayName("TC-ENT-20 | Account.getUpdateAt | never persisted -> null until onCreate runs")
    void account_getUpdateAt_beforePersist() {
        Account a = new Account();
        assertNull(a.getUpdateAt());
        a.onCreate();
        assertNotNull(a.getUpdateAt());
    }
}
