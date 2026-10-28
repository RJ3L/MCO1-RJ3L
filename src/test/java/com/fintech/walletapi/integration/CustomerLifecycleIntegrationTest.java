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

    /**
     * Builds a MockMvc that sends a real JWT for the current mock user on every request.
     *
     * <p><b>Pre-condition:</b> A {@code @WithMockUser} identity is set for the test.</p>
     * <p><b>Post-condition:</b> That user exists (non-owner users as admins), {@code mvc} carries its Bearer
     * token, and the test context is cleared so only the token authenticates.</p>
     */
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

    /**
     * Saves a user with a linked customer profile.
     *
     * <p><b>Pre-condition:</b> The username is unused.</p>
     * <p><b>Post-condition:</b> A user and a customer row exist.</p>
     *
     * @param username login name
     * @return the saved customer profile
     */
    private Customer profile(String username) {
        Customer c = new Customer(); c.setFullName("Customer"); c.setEmail("test@example.com");
        User u = new User(); u.setUsername(username); u.setPassword("unused"); u.setCustomer(c); c.setUser(u);
        return users.saveAndFlush(u).getCustomer();
    }
    /**
     * Saves an active PHP savings wallet for a customer.
     *
     * <p><b>Pre-condition:</b> {@code c} is saved; {@code balance} is a valid decimal string.</p>
     * <p><b>Post-condition:</b> A wallet row exists.</p>
     *
     * @param c the owner
     * @param balance balance as text
     * @return the saved wallet
     */
    private Account wallet(Customer c, String balance) {
        Account a = new Account(); a.setCustomer(c); a.setAccountType("SAVINGS"); a.setStatus("ACTIVE");
        a.setCurrency("PHP"); a.setBalance(new BigDecimal(balance)); return accounts.saveAndFlush(a);
    }
    /**
     * TC-CUSINT-01: tests {@code CustomerService.deleteCustomer}.
     *
     * <p><b>Pre-condition:</b> Eligible linked profile deleted.</p>
     * <p><b>Post-condition:</b> Profile and login removed.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
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
    /**
     * TC-CUSINT-02: tests {@code CustomerService.updateCustomer}.
     *
     * <p><b>Pre-condition:</b> A standard call to the method under test.</p>
     * <p><b>Post-condition:</b> Owner can edit self but cannot edit another profile.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
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
    /**
     * TC-CUSINT-03: tests {@code CustomerService.deleteCustomer}.
     *
     * <p><b>Pre-condition:</b> Customer with wallets.</p>
     * <p><b>Post-condition:</b> 409 and profile retained.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-03 | CustomerService.deleteCustomer | customer with wallets -> 409 and profile retained")
    void walletsProtectCustomer() throws Exception {
        Customer c = profile("has-wallet"); wallet(c, "0");
        mvc.perform(delete("/api/v1/customers/" + c.getId())).andExpect(status().isConflict()).andExpect(jsonPath("$.errorType").value("Conflict"));
        assertTrue(customers.existsById(c.getId()));
    }
    /**
     * TC-CUSINT-04: tests {@code AccountService.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Funded or historical wallet.</p>
     * <p><b>Post-condition:</b> 409 without destroying money or history.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
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
    /**
     * TC-CUSINT-05: tests {@code AccountController.updatedAccount}.
     *
     * <p><b>Pre-condition:</b> Bad balance bodies.</p>
     * <p><b>Post-condition:</b> 400 and original balance preserved.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
    @Test @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-CUSINT-05 | AccountController.updatedAccount | bad balance bodies -> 400 and original balance preserved")
    void invalidBalanceDoesNotPersist() throws Exception {
        Account a = wallet(profile("validation"), "10");
        for (String body : new String[]{"{}", "{\"balance\":-1}", "{\"balance\":1.001}", "{\"balance\":100000000000000000}"}) {
            mvc.perform(put("/api/v1/accounts/" + a.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            assertEquals(0, BigDecimal.TEN.compareTo(accounts.findById(a.getId()).orElseThrow().getBalance()));
        }
    }
    /**
     * TC-CUSINT-06: tests {@code AccountController.closeAccount}.
     *
     * <p><b>Pre-condition:</b> Empty unused wallet.</p>
     * <p><b>Post-condition:</b> Deletion and standard 404 body.</p>
     *
     * @throws Exception propagated from the code under test; any exception fails this test
     */
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
