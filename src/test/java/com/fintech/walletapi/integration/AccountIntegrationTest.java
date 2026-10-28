package com.fintech.walletapi.integration;

import com.fintech.walletapi.entity.Account;
import com.fintech.walletapi.entity.Customer;
import com.fintech.walletapi.repository.AccountRepository;
import com.fintech.walletapi.repository.CustomerRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
public class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-ACCINT-01 | AccountController.getAccountById (end to end) | admin reads a saved account -> 200 with DB values")
    public void testGetAccount_EndToEnd_Success() throws Exception {
        Account realAccount = new Account();
        realAccount.setAccountType("CHECKING");
        realAccount.setBalance(new BigDecimal("9999.00"));
        Account savedAccount = accountRepository.save(realAccount);

        mockMvc.perform(get("/api/v1/accounts/" + savedAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.data.balance").value(9999.00))
                .andExpect(jsonPath("$.data.currency").value("PHP"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("TC-ACCINT-02 | AccountController.getAccountById (end to end) | customer reads an account she does not own -> 403")
    public void testGetAccount_AsCustomer_WithNoOwnership_Returns403() throws Exception {
        Account account = new Account();
        account.setAccountType("SAVINGS");
        account.setBalance(new BigDecimal("1.00"));
        Account saved = accountRepository.save(account);

        mockMvc.perform(get("/api/v1/accounts/" + saved.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-ACCINT-03 | AccountController.getAccountById (end to end) | admin reads a missing id -> 404")
    public void testGetAccount_Missing_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/987654321"))
                .andExpect(status().isNotFound());
    }

    private Long newCustomerId() {
        Customer customer = new Customer();
        customer.setFullName("Wallet Rule Tester");
        customer.setEmail("wallet-rule@example.com");
        return customerRepository.save(customer).getId();
    }

    private static String walletJson(Long customerId, String currency) {
        return "{\"customerId\":" + customerId + ",\"accountType\":\"SAVINGS\",\"balance\":0,"
                + "\"status\":\"ACTIVE\",\"currency\":\"" + currency + "\"}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-ACCINT-04 | AccountController.createAccount (end to end) | second USD wallet for one customer -> 201 then 409, one row kept")
    public void testCreateAccount_SecondWalletSameCurrency_Returns409() throws Exception {
        Long customerId = newCustomerId();

        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(walletJson(customerId, "USD")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(walletJson(customerId, "usd")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Customer " + customerId + " already has a USD wallet"));

        assertTrue(accountRepository.existsByCustomer_IdAndCurrency(customerId, "USD"));
        assertEquals(1, accountRepository.findAll().stream()
                .filter(a -> a.getCustomer() != null && a.getCustomer().getId().equals(customerId)).count());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-ACCINT-05 | AccountController.createAccount (end to end) | one customer opens PHP, USD and EUR wallets -> all 201")
    public void testCreateAccount_DifferentCurrencies_AllAllowed() throws Exception {
        Long customerId = newCustomerId();
        for (String currency : new String[]{"PHP", "USD", "EUR"}) {
            mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(walletJson(customerId, currency)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.currency").value(currency));
        }
    }
}
