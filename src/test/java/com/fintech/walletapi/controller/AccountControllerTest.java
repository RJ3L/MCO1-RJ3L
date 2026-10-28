package com.fintech.walletapi.controller;

import com.fintech.walletapi.dto.AccountDTO;
import com.fintech.walletapi.exception.DuplicateWalletException;
import com.fintech.walletapi.exception.InvalidTransactionException;
import com.fintech.walletapi.security.CustomUserDetailsService;
import com.fintech.walletapi.security.JwtUtil;
import com.fintech.walletapi.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static AccountDTO account(Long id, String currency, String balance) {
        AccountDTO dto = new AccountDTO();
        dto.setId(id);
        dto.setAccountType("SAVINGS");
        dto.setCurrency(currency);
        dto.setBalance(new BigDecimal(balance));
        dto.setStatus("ACTIVE");
        return dto;
    }

    private static final String NEW_USD_WALLET =
            "{\"customerId\":1,\"accountType\":\"SAVINGS\",\"balance\":0.00,\"status\":\"ACTIVE\",\"currency\":\"USD\"}";

    @Test
    @DisplayName("TC-ACCCTL-01 | AccountController.getAccountById | existing id 5 -> 200 'Account found' with JSON body")
    public void testGetAccount_Returns200AndJson() throws Exception {
        AccountDTO fakeAccount = new AccountDTO();
        fakeAccount.setId(5L);
        fakeAccount.setCustomerName("Bob Builder");
        fakeAccount.setBalance(new BigDecimal("250.00"));
        when(accountService.getAccountById(5L)).thenReturn(fakeAccount);

        mockMvc.perform(get("/api/v1/accounts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account found"))
                .andExpect(jsonPath("$.data.customerName").value("Bob Builder"))
                .andExpect(jsonPath("$.data.balance").value(250.00));
    }

    @Test
    @DisplayName("TC-ACCCTL-02 | AccountController.getAccountById | unknown id 99 -> 404")
    public void testGetAccount_NotFound_Returns404() throws Exception {
        when(accountService.getAccountById(99L)).thenThrow(new IllegalArgumentException("Account not Found"));

        mockMvc.perform(get("/api/v1/accounts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-ACCCTL-03 | AccountController.getAccountById | someone else's wallet -> 403")
    void getAccount_forbidden() throws Exception {
        when(accountService.getAccountById(7L)).thenThrow(new AccessDeniedException("no"));
        mockMvc.perform(get("/api/v1/accounts/7"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-ACCCTL-04 | AccountController.createAccount | USD forex wallet -> 201 with currency USD")
    void create_usdWallet() throws Exception {
        when(accountService.createAccount(any())).thenReturn(account(10L, "USD", "0.00"));
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(NEW_USD_WALLET))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully"))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    @DisplayName("TC-ACCCTL-05 | AccountController.createAccount | negative balance -> 400 'Balance cannot be negative'")
    void create_negativeBalance() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"accountType\":\"SAVINGS\",\"balance\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Balance cannot be negative"));
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("TC-ACCCTL-06 | AccountController.createAccount | currency 'US' (not 3 letters) -> 400")
    void create_badCurrencyFormat() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"accountType\":\"SAVINGS\",\"balance\":1,\"currency\":\"US\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-ACCCTL-23 | AccountController.createAccount | second USD wallet for the customer -> 409 Conflict")
    void create_duplicateCurrency() throws Exception {
        when(accountService.createAccount(any()))
                .thenThrow(new DuplicateWalletException("Customer 1 already has a USD wallet"));
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(NEW_USD_WALLET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorType").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Customer 1 already has a USD wallet"));
    }

    @Test
    @DisplayName("TC-ACCCTL-07 | AccountController.createAccount | unsupported currency JPY -> 400 'Invalid Transaction'")
    void create_unsupportedCurrency() throws Exception {
        when(accountService.createAccount(any())).thenThrow(new InvalidTransactionException("Unsupported currency: JPY"));
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"accountType\":\"SAVINGS\",\"balance\":1,\"currency\":\"JPY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value("Invalid Transaction"));
    }

    @Test
    @DisplayName("TC-ACCCTL-08 | AccountController.getAllAccounts | no filter -> getAllAccounts used")
    void getAll_noFilter() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(List.of(account(1L, "PHP", "1.00")));
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(accountService, never()).getAccountByStatus(any());
    }

    @Test
    @DisplayName("TC-ACCCTL-09 | AccountController.getAllAccounts | ?status=ACTIVE -> filter used")
    void getAll_statusFilter() throws Exception {
        when(accountService.getAccountByStatus("ACTIVE")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/accounts").param("status", "ACTIVE"))
                .andExpect(status().isOk());
        verify(accountService).getAccountByStatus("ACTIVE");
        verify(accountService, never()).getAllAccounts();
    }

    @Test
    @DisplayName("TC-ACCCTL-10 | AccountController.getAllAccounts | customer (not admin) -> 403")
    void getAll_forbidden() throws Exception {
        when(accountService.getAllAccounts()).thenThrow(new AccessDeniedException("admin only"));
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-ACCCTL-11 | AccountController.getMyAccounts | alice -> her PHP and USD wallets")
    void mine_twoWallets() throws Exception {
        when(accountService.getMyAccounts("alice")).thenReturn(List.of(account(1L, "PHP", "900.00"), account(2L, "USD", "17.24")));
        mockMvc.perform(get("/api/v1/accounts/mine").principal(new UsernamePasswordAuthenticationToken("alice", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].currency").value("USD"));
    }

    @Test
    @DisplayName("TC-ACCCTL-12 | AccountController.getMyAccounts | user with no wallets -> empty list")
    void mine_none() throws Exception {
        when(accountService.getMyAccounts("carol")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/accounts/mine").principal(new UsernamePasswordAuthenticationToken("carol", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("TC-ACCCTL-13 | AccountController.getMyAccounts | username taken from the token, not the URL")
    void mine_usesToken() throws Exception {
        when(accountService.getMyAccounts("bob")).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/accounts/mine").principal(new UsernamePasswordAuthenticationToken("bob", null, List.of())))
                .andExpect(jsonPath("$.message").value("Successfully fetched your wallets"));
        verify(accountService).getMyAccounts("bob");
    }

    @Test
    @DisplayName("TC-ACCCTL-14 | AccountController.updatedAccount | existing id -> 200 'Account updated successfully'")
    void update_ok() throws Exception {
        when(accountService.updateAccountBalance(eq(1L), any())).thenReturn(account(1L, "PHP", "900.00"));
        mockMvc.perform(put("/api/v1/accounts/1").contentType(MediaType.APPLICATION_JSON).content("{\"balance\":900.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(900.00));
    }

    @Test
    @DisplayName("TC-ACCCTL-15 | AccountController.updatedAccount | unknown id -> 404")
    void update_missing() throws Exception {
        when(accountService.updateAccountBalance(eq(9L), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/accounts/9").contentType(MediaType.APPLICATION_JSON).content("{\"balance\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-ACCCTL-16 | AccountController.updatedAccount | malformed JSON -> 400")
    void update_badJson() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/1").contentType(MediaType.APPLICATION_JSON).content("{balance:"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-ACCCTL-17 | AccountController.closeAccount | existing id -> 200 'Account closed successfully'")
    void close_ok() throws Exception {
        when(accountService.closeAccount(1L)).thenReturn(true);
        mockMvc.perform(delete("/api/v1/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account closed successfully"));
    }

    @Test
    @DisplayName("TC-ACCCTL-18 | AccountController.closeAccount | unknown id -> 404")
    void close_missing() throws Exception {
        when(accountService.closeAccount(9L)).thenReturn(false);
        mockMvc.perform(delete("/api/v1/accounts/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-ACCCTL-19 | AccountController.closeAccount | non-admin -> 403")
    void close_forbidden() throws Exception {
        when(accountService.closeAccount(1L)).thenThrow(new AccessDeniedException("admin only"));
        mockMvc.perform(delete("/api/v1/accounts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-ACCCTL-20 | AccountController.getAccountsPaginated | defaults -> page 0 size 5 requested")
    void page_defaults() throws Exception {
        when(accountService.getPaginatedAccounts(0, 5)).thenReturn(new PageImpl<>(List.of(account(1L, "PHP", "1.00")), PageRequest.of(0, 5), 1));
        mockMvc.perform(get("/api/v1/accounts/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
        verify(accountService).getPaginatedAccounts(0, 5);
    }

    @Test
    @DisplayName("TC-ACCCTL-21 | AccountController.getAccountsPaginated | ?page=2&size=3 -> forwarded")
    void page_custom() throws Exception {
        when(accountService.getPaginatedAccounts(2, 3)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 3), 0));
        mockMvc.perform(get("/api/v1/accounts/page").param("page", "2").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Page fetched"));
        verify(accountService).getPaginatedAccounts(2, 3);
    }

    @Test
    @DisplayName("TC-ACCCTL-22 | AccountController.getAccountsPaginated | ?page=abc -> 400")
    void page_badParam() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/page").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-ACCCTL-24 | AccountController.updatedAccount | missing balance -> 400, service not called")
    void updateMissingBalance() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorType").value("Validation Failed"));
        verify(accountService, never()).updateAccountBalance(any(), any());
    }
    @Test
    @DisplayName("TC-ACCCTL-25 | AccountController.updatedAccount | negative balance -> 400, service not called")
    void updateNegativeBalance() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/1").contentType(MediaType.APPLICATION_JSON).content("{\"balance\":-1}"))
                .andExpect(status().isBadRequest());
        verify(accountService, never()).updateAccountBalance(any(), any());
    }
    @Test
    @DisplayName("TC-ACCCTL-26 | AccountController.updatedAccount | fractional cent -> 400, service not called")
    void updateFractionalCent() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/1").contentType(MediaType.APPLICATION_JSON).content("{\"balance\":1.001}"))
                .andExpect(status().isBadRequest());
        verify(accountService, never()).updateAccountBalance(any(), any());
    }

}
