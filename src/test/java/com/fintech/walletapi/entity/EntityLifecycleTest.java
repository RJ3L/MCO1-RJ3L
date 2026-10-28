package com.fintech.walletapi.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EntityLifecycleTest {

    @Test
    @DisplayName("TC-ENT-01 | Account.onCreate | new account -> createdAt and updateAt set")
    void account_onCreate_timestamps() {
        Account a = new Account();
        a.onCreate();
        assertNotNull(a.getCreatedAt());
        assertNotNull(a.getUpdateAt());
    }

    @Test
    @DisplayName("TC-ENT-02 | Account.onCreate | no currency -> defaults to PHP")
    void account_onCreate_defaultCurrency() {
        Account a = new Account();
        a.onCreate();
        assertEquals("PHP", a.getCurrency());
    }

    @Test
    @DisplayName("TC-ENT-03 | Account.onCreate | USD given -> kept as USD")
    void account_onCreate_keepsCurrency() {
        Account a = new Account();
        a.setCurrency("USD");
        a.onCreate();
        assertEquals("USD", a.getCurrency());
    }

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

    @Test
    @DisplayName("TC-ENT-05 | Account.onUpdate | update -> createdAt unchanged")
    void account_onUpdate_keepsCreated() {
        Account a = new Account();
        a.onCreate();
        LocalDateTime created = a.getCreatedAt();
        a.onUpdate();
        assertEquals(created, a.getCreatedAt());
    }

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

    @Test
    @DisplayName("TC-ENT-07 | TransferRecord.onCreate | insert -> transferDate set")
    void transfer_onCreate_sets() {
        TransferRecord r = new TransferRecord();
        r.onCreate();
        assertNotNull(r.getTransferDate());
    }

    @Test
    @DisplayName("TC-ENT-08 | TransferRecord.onCreate | transferDate is 'now' (within 5 seconds)")
    void transfer_onCreate_now() {
        TransferRecord r = new TransferRecord();
        r.onCreate();
        assertTrue(r.getTransferDate().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

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

    @Test
    @DisplayName("TC-ENT-10 | ExchangeRate.touch | insert/update -> updatedAt set")
    void rate_touch_sets() {
        ExchangeRate r = new ExchangeRate("USD", "PHP", BigDecimal.TEN);
        r.touch();
        assertNotNull(r.getUpdatedAt());
    }

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

    @Test
    @DisplayName("TC-ENT-12 | ExchangeRate.touch | pair and rate untouched")
    void rate_touch_keepsData() {
        ExchangeRate r = new ExchangeRate("EUR", "PHP", new BigDecimal("63"));
        r.touch();
        assertEquals("EUR", r.getBaseCurrency());
        assertEquals("PHP", r.getQuoteCurrency());
        assertEquals(new BigDecimal("63"), r.getRate());
    }

    @Test
    @DisplayName("TC-ENT-13 | User.getAuthorities | default role -> ROLE_CUSTOMER")
    void user_defaultRole() {
        GrantedAuthority a = new User().getAuthorities().iterator().next();
        assertEquals("ROLE_CUSTOMER", a.getAuthority());
    }

    @Test
    @DisplayName("TC-ENT-14 | User.getAuthorities | ROLE_ADMIN -> ROLE_ADMIN")
    void user_adminRole() {
        User u = new User();
        u.setRole("ROLE_ADMIN");
        assertEquals("ROLE_ADMIN", u.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("TC-ENT-15 | User.getAuthorities | blank or null role -> falls back to ROLE_CUSTOMER")
    void user_blankRole() {
        User u = new User();
        u.setRole(" ");
        assertEquals("ROLE_CUSTOMER", u.getAuthorities().iterator().next().getAuthority());
        u.setRole(null);
        assertEquals("ROLE_CUSTOMER", u.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("TC-ENT-16 | User.isAccountNonExpired / isAccountNonLocked / isCredentialsNonExpired / isEnabled | new user -> all true")
    void user_flags_new() {
        User u = new User();
        assertTrue(u.isAccountNonExpired());
        assertTrue(u.isAccountNonLocked());
        assertTrue(u.isCredentialsNonExpired());
        assertTrue(u.isEnabled());
    }

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

    @Test
    @DisplayName("TC-ENT-19 | Account.getCreatedAt | never persisted -> null until onCreate runs")
    void account_getCreatedAt_beforePersist() {
        Account a = new Account();
        assertNull(a.getCreatedAt());
        a.onCreate();
        assertNotNull(a.getCreatedAt());
    }

    @Test
    @DisplayName("TC-ENT-20 | Account.getUpdateAt | never persisted -> null until onCreate runs")
    void account_getUpdateAt_beforePersist() {
        Account a = new Account();
        assertNull(a.getUpdateAt());
        a.onCreate();
        assertNotNull(a.getUpdateAt());
    }
}
