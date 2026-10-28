package com.fintech.walletapi.integration;

import com.fintech.walletapi.entity.*;
import com.fintech.walletapi.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fintech.walletapi.security.JwtUtil;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired TransferRecordRepository ledger;
    @Autowired WebApplicationContext context;
    @Autowired JwtUtil jwtUtil;

    @BeforeEach
    void authenticatedRequests() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!username.equals("owner")) {
            User admin = new User(); admin.setUsername(username); admin.setPassword("unused"); admin.setRole("ROLE_ADMIN");
            users.saveAndFlush(admin);
        }
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + jwtUtil.generateToken(username))).build();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
    }

    private Customer profile(String username) {
        Customer c = new Customer(); c.setFullName("Customer"); c.setEmail("test@example.com");
        User u = new User(); u.setUsername(username); u.setPassword("unused"); u.setCustomer(c); c.setUser(u);
        return users.saveAndFlush(u).getCustomer();
    }
    private Account wallet(Customer c, String balance) {
        Account a = new Account(); a.setCustomer(c); a.setAccountType("SAVINGS"); a.setStatus("ACTIVE");
        a.setCurrency("PHP"); a.setBalance(new BigDecimal(balance)); return accounts.saveAndFlush(a);
    }
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-01 | CustomerService.deleteCustomer | eligible linked profile deleted -> profile and login removed")
    void adminDeletesLinkedProfile() throws Exception {
        Customer c = profile("delete-me"); Long uid = c.getUser().getId();
        mvc.perform(get("/api/v1/customers/" + c.getId())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/customers/" + c.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Updated\",\"email\":\"updated@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/customers/" + c.getId())).andExpect(status().isOk());
        customers.flush(); assertFalse(customers.existsById(c.getId())); assertFalse(users.existsById(uid));
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + jwtUtil.generateToken("delete-me"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customers/" + c.getId())).andExpect(status().isNotFound());
    }
    @Test @WithMockUser(username = "owner", roles = "CUSTOMER")
    @DisplayName("TC-CUSINT-02 | CustomerService.updateCustomer | owner can edit self but cannot edit another profile")
    void ownerBoundaries() throws Exception {
        Customer own = profile("owner"), other = profile("other");
        String body = "{\"fullName\":\"Updated\",\"email\":\"updated@example.com\"}";
        mvc.perform(put("/api/v1/customers/" + own.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customers/" + other.getId())).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/customers/" + other.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/customers/" + own.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers")).andExpect(status().isForbidden());
        assertEquals("Customer", customers.findById(other.getId()).orElseThrow().getFullName());
    }
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-03 | CustomerService.deleteCustomer | customer with wallets -> 409 and profile retained")
    void walletsProtectCustomer() throws Exception {
        Customer c = profile("has-wallet"); wallet(c, "0");
        mvc.perform(delete("/api/v1/customers/" + c.getId())).andExpect(status().isConflict()).andExpect(jsonPath("$.errorType").value("Conflict"));
        assertTrue(customers.existsById(c.getId()));
    }
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-04 | AccountService.closeAccount | funded or historical wallet -> 409 without destroying money or history")
    void protectedWalletDeletion() throws Exception {
        Account a = wallet(profile("funded"), "10");
        mvc.perform(delete("/api/v1/accounts/" + a.getId())).andExpect(status().isConflict());
        assertEquals(0, new BigDecimal("10").compareTo(accounts.findById(a.getId()).orElseThrow().getBalance()));
        a.setBalance(BigDecimal.ZERO); accounts.saveAndFlush(a);
        TransferRecord r = new TransferRecord(); r.setSourceAccountId(a.getId()); r.setDestinationAccountId(999L); r.setAmount(BigDecimal.TEN); ledger.saveAndFlush(r);
        mvc.perform(delete("/api/v1/accounts/" + a.getId())).andExpect(status().isConflict());
        assertTrue(accounts.existsById(a.getId())); assertTrue(ledger.existsById(r.getId()));
    }
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-05 | AccountController.updatedAccount | bad balance bodies -> 400 and original balance preserved")
    void invalidBalanceDoesNotPersist() throws Exception {
        Account a = wallet(profile("validation"), "10");
        for (String body : new String[]{"{}", "{\"balance\":-1}", "{\"balance\":1.001}", "{\"balance\":100000000000000000}"}) {
            mvc.perform(put("/api/v1/accounts/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            assertEquals(0, BigDecimal.TEN.compareTo(accounts.findById(a.getId()).orElseThrow().getBalance()));
        }
    }
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-06 | AccountController.closeAccount | empty unused wallet -> deletion and standard 404 body")
    void eligibleWalletCrud() throws Exception {
        Account a = wallet(profile("empty"), "0");
        mvc.perform(put("/api/v1/accounts/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"balance\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(25));
        mvc.perform(put("/api/v1/accounts/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"balance\":0}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/accounts/" + a.getId())).andExpect(status().isOk());
        accounts.flush();
        mvc.perform(delete("/api/v1/accounts/" + a.getId())).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorType").value("Not Found"));
        mvc.perform(put("/api/v1/accounts/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"balance\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorType").value("Not Found"));
    }
}
